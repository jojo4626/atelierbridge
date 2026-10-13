package com.artcraftmobile.bridge;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

final class NativeSaveBridge {
    static final int REQUEST_SAVE = 904;
    private final Activity activity;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private final WebView web;
    private final int localPort;
    private final String appName;
    private boolean waitingForPicker;
    private String sourceUrl;
    private String fileName;
    private String mime;
    private String token;
    private OutputStream output;
    private Uri destination;
    private long written;

    NativeSaveBridge(Activity activity, WebView web, int port, String appName) {
        this.activity = activity;
        this.web = web;
        this.localPort = port;
        this.appName = appName;
    }

    
    @JavascriptInterface public void request(String name, String url) {
        requestInternal(name, url, "application/octet-stream");
    }

    void requestDownload(String name, String url, String contentType) {
        requestInternal(name, url, contentType);
    }

    private void requestInternal(String name, String url, String contentType) {
        if (!FileExportPolicy.allowedUrl(url, localPort)) {
            showToast("Export bloqué : source non locale");
            return;
        }
        synchronized (lock) {
            if (waitingForPicker || output != null) {
                showToast("Termine d'abord l'export en cours");
                return;
            }
            waitingForPicker = true;
            sourceUrl = url;
            mime = FileExportPolicy.preferredMime(name, contentType);
            fileName = FileExportPolicy.safeFilename(name, mime);
        }
        ui.post(() -> {
            try {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(mime);
                intent.putExtra(Intent.EXTRA_TITLE, fileName);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                activity.startActivityForResult(intent, REQUEST_SAVE);
            } catch (Exception e) {
                reset();
                showToast("Sélecteur de sauvegarde indisponible");
            }
        });
    }

    void savePickerResult(int resultCode, Intent data) {
        synchronized (lock) {
            if (!waitingForPicker) return;
            waitingForPicker = false;
            if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
                resetLocked();
                return;
            }
            try {
                destination = data.getData();
                
                try {
                    int grants = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    if (grants != 0)
                        activity.getContentResolver().takePersistableUriPermission(destination, grants);
                } catch (SecurityException | IllegalArgumentException ignored) { }
                output = activity.getContentResolver().openOutputStream(destination, "w");
                if (output == null) throw new IOException("Impossible d'écrire ce fichier");
            } catch (IOException e) {
                resetLocked();
                showToast("Enregistrement impossible : " + e.getMessage());
                return;
            }
            written = 0;
            token = UUID.randomUUID().toString();
        }


        String script = "(async function(){var t=" + JSONObject.quote(token) + ",u=" + JSONObject.quote(sourceUrl) + ";" +
            "try{let b;if(u.startsWith('export:')){let k=u.slice(7);" +
            "let q=window.__artcraftPendingExports?.[k];if(!q)throw Error('Export expiré');" +
            "b=await q;delete window.__artcraftPendingExports[k];}" +
            "else{const r=await fetch(u);if(!r.ok)throw Error('HTTP '+r.status);b=await r.blob();}" +
            "if(b.size>" + FileExportPolicy.MAX_EXPORT_BYTES + ")throw Error('Fichier trop volumineux');" +
            "for(let p=0;p<b.size;p+=49152){let a=new Uint8Array(await b.slice(p,p+49152).arrayBuffer());" +
            "let s='';for(let i=0;i<a.length;i++)s+=String.fromCharCode(a[i]);" +
            "if(!ArtCraftSave.writeChunk(t,btoa(s)))throw Error('Écriture refusée');}" +
            "ArtCraftSave.complete(t);" +
            "}catch(e){ArtCraftSave.fail(t,String(e));}})();";
        ui.post(() -> web.evaluateJavascript(script, null));
    }

    @JavascriptInterface public boolean writeChunk(String id, String base64) {
        synchronized (lock) {
            if (output == null || token == null || !token.equals(id)) return false;
            try {
                if (base64 == null || base64.length() > 131072) throw new IOException("Bloc trop grand");
                byte[] data = Base64.decode(base64, Base64.DEFAULT);
                if (written + data.length > FileExportPolicy.MAX_EXPORT_BYTES) throw new IOException("Export >150 Mo");
                output.write(data);
                written += data.length;
                return true;
            } catch (Exception e) {
                resetLocked();
                showToast("Échec d'export : " + e.getMessage());
                return false;
            }
        }
    }

    @JavascriptInterface public void complete(String id) {
        synchronized (lock) {
            if (output == null || token == null || !token.equals(id)) return;
            long count = written;
            try {
                output.flush();
                output.close();
                output = null; 
                RecentExports.record(activity, destination, fileName, mime, appName);
                showToast("Fichier enregistré (" + (count / 1024) + " Ko)");
            } catch (IOException e) {
                showToast("Erreur de sauvegarde : " + e.getMessage());
            } finally { resetLocked(); }
        }
    }

    @JavascriptInterface public void fail(String id, String reason) {
        synchronized (lock) {
            if (token == null || !token.equals(id)) return;
            resetLocked();
        }
        showToast("Export impossible : " + (reason == null ? "erreur Web" : reason));
    }

    void close() { reset(); }
    private void reset() { synchronized (lock) { resetLocked(); } }
    private void resetLocked() {
        boolean partial = output != null;
        if (output != null) try { output.close(); } catch (IOException ignored) { }
        
        if (partial && destination != null) {
            try { activity.getContentResolver().delete(destination, null, null); }
            catch (Exception ignored) { }
        }
        destination = null;
        output = null;
        token = null;
        sourceUrl = null;
        fileName = null;
        mime = null;
        written = 0;
        waitingForPicker = false;
    }

    
    static String downloadClickHook() {
        return "(function(){if(window.__artcraftSaveHook)return;window.__artcraftSaveHook=true;" +
            "window.__artcraftPendingExports=Object.create(null);window.__artcraftExportCounter=0;" +
            "document.addEventListener('click',function(e){" +
            "const a=e.target&&e.target.closest?e.target.closest('a[download]'):null;" +
            "if(!a)return;const u=a.href||'';" +
            "if(u.startsWith('blob:')||u.startsWith('data:')){" +
            "e.preventDefault();e.stopImmediatePropagation();" +
            "const id=String(++window.__artcraftExportCounter);" +
            "window.__artcraftPendingExports[id]=fetch(u).then(r=>r.blob());" +
            "ArtCraftSave.request(a.getAttribute('download')||'creation','export:'+id);" +
            "}},true);})();";
    }

    private void showToast(String message) {
        ui.post(() -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
    }
}
