package com.artcraftmobile.bridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class MainActivity extends Activity {
    private static final int PICK_ZIP = 902;
    private static final int PICK_WEB_FILE = 903;
    
    private final Set<String> approvedDocumentUris = ConcurrentHashMap.newKeySet();
    private boolean landscape = false;
    private boolean photoTouchMenuEnabled = true;
    private final AtomicBoolean updateCheckActive = new AtomicBoolean(false);
    private static final String UPDATE_PREFS = "module_update_checks";
    private static final int BG = 0xff10131b;
    private static final int CARD = 0xff1c2330;
    private static final int WHITE = 0xfff0f4fa;
    private static final int SOFT = 0xffa8b5c8;
    private static final int ACCENT = 0xff92c7fc;
    private static final int MAX_FILES = 6000;
    private static final long MAX_BYTES = 450L * 1024 * 1024;

    private static final String[][] APPS = new String[][] {
        {"PhotoCraft", "photocraft", "Retouche photo et calques"},
        {"VectorCraft", "vectorcraft", "Vecteurs, SVG et laser"},
        {"FilmCraft", "filmcraft", "Montage vidéo • Web expérimental"},
        {"LightCraft", "lightcraft", "Photothèque et RAW"},
        {"PdfCraft", "pdfcraft", "Lecture et édition de PDF"},
        {"EffectCraft", "effectcraft", "Effets et animation"},
        {"DesignCraft", "designcraft", "Mise en page"},
        {"FigCraft", "figcraft", "Prototypage d'interfaces"},
        {"WordCraft", "wordcraft", "Traitement de texte"},
        {"SoundCraft", "soundcraft", "Audio multipiste"},
        {"DeckCraft", "deckcraft", "Présentations"},
        {"CADCraft", "cadcraft", "Dessin technique"},
        {"GridCraft", "gridcraft", "Tableurs"},
    };

    private final ExecutorService background = Executors.newSingleThreadExecutor();
    private LocalHttpServer server;
    private LinearLayout main;
    private WebView browser;
    private NativeSaveBridge nativeSave;
    private String incomingSlug;
    private ValueCallback<Uri[]> webFileCallback;
    
    private final java.util.ArrayDeque<String> fileEvents = new java.util.ArrayDeque<>();
    private synchronized void note(String message) {
        if (fileEvents.size() >= 24) fileEvents.removeFirst();
        fileEvents.addLast(message);
    }
    private synchronized String fileReport() {
        if (fileEvents.isEmpty()) return "Aucun évènement. Essaie une photo dans PhotoCraft.";
        return android.text.TextUtils.join("\n", fileEvents);
    }
    private void showFileReport() {
        new AlertDialog.Builder(this)
            .setTitle("Diagnostic fichiers et menus")
            .setMessage(fileReport() + "\n\nAppui long sur ↻ pour ouvrir ce journal. Teste aussi le bouton Chrome si les menus ne réagissent pas.")
            .setPositiveButton("OK", null)
            .show();
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showHome();
        checkUpdates(false);
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    private TextView text(String value, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(sp);
        return t;
    }
    private Button button(String value, int color, Runnable action) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setTextColor(color == ACCENT ? BG : WHITE);
        b.setBackgroundTintList(ColorStateList.valueOf(color));
        b.setOnClickListener(v -> action.run());
        return b;
    }
    private LinearLayout.LayoutParams margins(int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(bottom);
        return p;
    }
    private File bundle(String slug) { return new File(new File(getFilesDir(), "bundles"), slug); }
    private boolean installed(String slug) { return new File(bundle(slug), "entry.txt").isFile(); }

    private void showAbout() {
        new AlertDialog.Builder(this)
            .setTitle("AtelierBridge · 1.0 RC1")
            .setMessage("Lanceur indépendant, sans affiliation avec l’équipe ArtCraft. "
                + "Les modules téléchargés appartiennent à leurs auteurs respectifs. "
                + "Les documents restent sur l’appareil ou dans le dossier choisi. "
                + "Les installations et vérifications de versions interrogent GitHub. "
                + "Aucune télémétrie n’est intégrée au lanceur. "
                + "Code, licences et politique de confidentialité : dépôt GitHub.")
            .setPositiveButton("Fermer", null)
            .setNeutralButton("GitHub", (dialog, which) ->
                openExternal("https://github.com/jojo4626/atelierbridge"))
            .show();
    }

    private void showHome() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        landscape = false;
        approvedDocumentUris.clear();
        if (nativeSave != null) { nativeSave.close(); nativeSave = null; }
        if (server != null) { server.close(); server = null; }
        if (browser != null) {
            browser.stopLoading();
            browser.destroy();
            browser = null;
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        main = vertical();
        main.setPadding(dp(18), dp(25), dp(18), dp(20));
        scroll.addView(main);
        setContentView(scroll);

        TextView kicker = text("ÉDITION INDÉPENDANTE • 1.0 RC1", 11, ACCENT);
        kicker.setLetterSpacing(.15f);
        main.addView(kicker, margins(8));
        TextView heading = text("AtelierBridge", 29, WHITE);
        heading.setTypeface(null, 1);
        main.addView(heading, margins(4));
        main.addView(text("Installe les ateliers de ton choix en un clic, ouvre les fichiers depuis Android et enregistre les exports directement sur ta tablette.", 14, SOFT), margins(14));
        TextView note = text("Téléchargement Internet nécessaire à l'installation uniquement ; les modules déjà installés fonctionnent en local. Disponibilité et compatibilité Android selon chaque éditeur.", 12, SOFT);
        note.setPadding(dp(13), dp(12), dp(13), dp(12));
        note.setBackgroundColor(CARD);
        main.addView(note, margins(22));

        Button about = button("À propos, confidentialité et licences", 0xff34475e, this::showAbout);
        about.setMinHeight(dp(48));
        main.addView(about, margins(10));

        Button diagnosis = button("Tester ma tablette (WASM / WebGL)", ACCENT, this::runDiagnostics);
        diagnosis.setMinHeight(dp(50));
        main.addView(diagnosis, margins(8));

        Button multiInstall = button("Choisir plusieurs ateliers à installer", 0xff34475e, this::chooseModules);
        multiInstall.setMinHeight(dp(48));
        main.addView(multiInstall, margins(8));

        Button recents = button("Mes exports récents", 0xff34475e, this::showRecentExports);
        recents.setMinHeight(dp(48));
        main.addView(recents, margins(8));
        Button updates = button("Rechercher les mises à jour", 0xff34475e, () -> checkUpdates(true));
        updates.setMinHeight(dp(48));
        main.addView(updates, margins(18));

        final int columns = getResources().getConfiguration().smallestScreenWidthDp >= 600 ? 2 : 1;
        LinearLayout gridRow = null;
        for (int i = 0; i < APPS.length; i++) {
            String[] app = APPS[i];
            if (i % columns == 0) {
                gridRow = new LinearLayout(this);
                gridRow.setOrientation(LinearLayout.HORIZONTAL);
                main.addView(gridRow, margins(6));
            }
            String name = app[0], slug = app[1], purpose = app[2];
            boolean ready = installed(slug);
            LinearLayout card = vertical();
            card.setPadding(dp(14), dp(14), dp(14), dp(11));
            card.setBackgroundColor(CARD);
            String installedVersion = readInstalledVersion(slug);
            TextView title = text(name + (ready ? "  •  Installé" + (installedVersion.isEmpty() ? "" : " (" + installedVersion + ")") : "  •  À installer"), 19, WHITE);
            title.setTypeface(null, 1);
            card.addView(title, margins(4));
            card.addView(text(purpose, 13, SOFT), margins(9));

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            Button open = button("Ouvrir", ACCENT, () -> openApp(slug));
            open.setEnabled(ready);
            actions.addView(open, new LinearLayout.LayoutParams(0, dp(45), 1));
            Button installer = button(ready ? "Mettre à jour" : "Installer ↓", 0xff34475e,
                () -> installSelected(new String[] {slug}));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(45), 1);
            p.leftMargin = dp(7);
            actions.addView(installer, p);
            card.addView(actions, margins(4));
            LinearLayout otherActions = new LinearLayout(this);
            otherActions.setOrientation(LinearLayout.HORIZONTAL);
            Button importZip = button("Importer ZIP", 0xff253242, () -> importZip(slug));
            otherActions.addView(importZip, new LinearLayout.LayoutParams(0, dp(42), 1));
            Button releases = button("Site officiel ↗", 0xff253242,
                () -> openExternal("https://github.com/storytold/" + slug + "/releases/latest"));
            LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, dp(42), 1);
            p2.leftMargin = dp(7);
            otherActions.addView(releases, p2);
            card.addView(otherActions, margins(0));
            LinearLayout.LayoutParams cardParams = columns == 1
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            cardParams.rightMargin = dp(columns == 1 ? 0 : 8);
            cardParams.bottomMargin = dp(8);
            gridRow.addView(card, cardParams);
        }
        TextView footer = text("Catalogue indépendant non officiel. ZIP Web uniquement, récupérés depuis GitHub / storytold. L'installation est locale ; certains modules peuvent ne pas être proposés ou compatibles.", 12, SOFT);
        footer.setPadding(0, dp(8), 0, dp(15));
        main.addView(footer);
    }

    private String readInstalledVersion(String slug) {
        try {
            File file = new File(bundle(slug), "release.txt");
            if (!file.isFile() || file.length() > 120) return "";
            byte[] bytes = new byte[(int) file.length()];
            try (FileInputStream in = new FileInputStream(file)) {
                if (in.read(bytes) != bytes.length) return "";
            }
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) { return ""; }
    }

    
    private void showRecentExports() {
        final List<RecentExports.Entry> entries = RecentExports.list(this);
        if (entries.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Exports récents")
                .setMessage("Aucun fichier exporté via AtelierBridge pour le moment. Les projets internes des éditeurs ne sont pas accessibles à cette liste.")
                .setPositiveButton("OK", null).show();
            return;
        }
        String[] items = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            RecentExports.Entry e = entries.get(i);
            items[i] = e.name + "  •  " + e.appName + "\n" +
                android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", e.timestamp);
        }
        new AlertDialog.Builder(this).setTitle("Exports récents")
            .setItems(items, (dialog, which) -> openRecentExport(entries.get(which)))
            .setNeutralButton("Effacer la liste", (dialog, which) ->
                new AlertDialog.Builder(this).setTitle("Effacer l'historique ?")
                    .setMessage("La liste sera vidée mais tes fichiers enregistrés ne seront pas supprimés.")
                    .setNegativeButton("Annuler", null)
                    .setPositiveButton("Effacer", (d, w) -> RecentExports.clear(this)).show())
            .setPositiveButton("Fermer", null).show();
    }

    private void openRecentExport(RecentExports.Entry e) {
        try {
            Uri uri = Uri.parse(e.uri);
            if (!"content".equals(uri.getScheme())) throw new IOException("URI non autorisée");
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, e.mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Ouvrir ce fichier avec…"));
        } catch (Exception error) {
            new AlertDialog.Builder(this).setTitle("Ouverture impossible")
                .setMessage("Le document a pu être déplacé ou l'autorisation Android a expiré. Réexporte-le depuis l'éditeur.")
                .setPositiveButton("OK", null).show();
        }
    }

    
    private void checkUpdates(boolean manual) {
        if (!updateCheckActive.compareAndSet(false, true)) return;
        List<String> installedSlugs = new ArrayList<>();
        for (String[] item : APPS) if (installed(item[1])) installedSlugs.add(item[1]);
        if (installedSlugs.isEmpty()) {
            updateCheckActive.set(false);
            if (manual) Toast.makeText(this, "Installe un atelier avant de chercher des mises à jour", Toast.LENGTH_LONG).show();
            return;
        }
        SharedPreferences preferences = getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (!manual && now - preferences.getLong("lastCheck", 0) < 24L * 60L * 60L * 1000L) {
            updateCheckActive.set(false);
            return;
        }
        preferences.edit().putLong("lastCheck", now).apply();
        if (manual) Toast.makeText(this, "Vérification des versions installées…", Toast.LENGTH_SHORT).show();
        background.execute(() -> {
            List<String> changed = new ArrayList<>();
            List<String> names = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            ReleaseInstaller.Progress progress = new ReleaseInstaller.Progress() {
                @Override public void transferred(long done, long total) { }
                @Override public boolean cancelled() { return false; }
            };
            for (String slug : installedSlugs) {
                try {
                    ReleaseInstaller.Asset asset = ReleaseInstaller.latest(slug, progress);
                    String current = readInstalledVersion(slug);
                    
                    if (!current.isEmpty() && !current.equals(asset.version)) {
                        changed.add(slug);
                        names.add(slug + "  " + current + " → " + asset.version);
                    }
                } catch (Exception e) {
                    errors.add(slug + " : " + (e.getMessage() == null ? "indisponible" : e.getMessage()));
                }
            }
            runOnUiThread(() -> {
                updateCheckActive.set(false);
                if (!changed.isEmpty()) {
                    new AlertDialog.Builder(this).setTitle("Mises à jour disponibles")
                        .setMessage(String.join("\n", names) + "\n\nLes anciennes versions sont conservées si une installation échoue.")
                        .setNegativeButton("Plus tard", null)
                        .setPositiveButton("Mettre à jour", (dialog, which) -> installSelected(changed.toArray(new String[0]))).show();
                } else if (manual) {
                    new AlertDialog.Builder(this).setTitle("Vérification terminée")
                        .setMessage(errors.isEmpty() ? "Tes modules officiels connus sont à jour. Les ZIP importés manuellement ne sont pas vérifiés." :
                            "Aucune mise à jour confirmée. Certains modules n'ont pas pu être vérifiés :\n" + String.join("\n", errors))
                        .setPositiveButton("OK", null).show();
                }
            });
        });
    }

    private void chooseModules() {
        String[] names = new String[APPS.length];
        boolean[] checked = new boolean[APPS.length];
        for (int i = 0; i < APPS.length; i++) names[i] = APPS[i][0] + (installed(APPS[i][1]) ? " (installé)" : "");
        new AlertDialog.Builder(this)
            .setTitle("Choisir les ateliers")
            .setMultiChoiceItems(names, checked, (dialog, which, value) -> checked[which] = value)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Installer la sélection", (dialog, which) -> {
                List<String> selected = new ArrayList<>();
                for (int i = 0; i < APPS.length; i++) if (checked[i]) selected.add(APPS[i][1]);
                if (selected.isEmpty()) Toast.makeText(this, "Sélectionne au moins une application", Toast.LENGTH_SHORT).show();
                else installSelected(selected.toArray(new String[0]));
            }).show();
    }

    private void installSelected(String[] slugs) {
        
        AtomicBoolean cancelled = new AtomicBoolean(false);
        LinearLayout panel = vertical();
        panel.setPadding(dp(24), dp(10), dp(24), dp(12));
        TextView message = text("Recherche des versions Web officielles…", 14, SOFT);
        panel.addView(message, margins(14));
        ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setMax(100);
        panel.addView(progressBar, margins(8));
        TextView remaining = text("Installation 1 / " + slugs.length, 12, SOFT);
        panel.addView(remaining);
        AlertDialog progressDialog = new AlertDialog.Builder(this)
            .setTitle("Installation des modules")
            .setView(panel)
            .setNegativeButton("Annuler", (d, w) -> cancelled.set(true))
            .create();
        progressDialog.setCancelable(false);
        progressDialog.show();

        background.execute(() -> {
            List<String> successes = new ArrayList<>();
            List<String> failures = new ArrayList<>();
            for (int i = 0; i < slugs.length; i++) {
                if (cancelled.get()) break;
                final String slug = slugs[i];
                final int index = i + 1;
                runOnUiThread(() -> {
                    message.setText("Recherche de " + slug + "…");
                    remaining.setText("Installation " + index + " / " + slugs.length);
                    progressBar.setIndeterminate(true);
                });
                File archive = null;
                try {
                    ReleaseInstaller.Progress progress = new ReleaseInstaller.Progress() {
                        @Override public boolean cancelled() { return cancelled.get(); }
                        @Override public void transferred(long done, long total) {
                            int percent = total > 0 ? (int) Math.min(100, done * 100L / total) : 0;
                            runOnUiThread(() -> {
                                progressBar.setIndeterminate(total <= 0);
                                if (total > 0) progressBar.setProgress(percent);
                                message.setText("Téléchargement " + slug + " : " + (total > 0 ? percent + "%" : (done / 1024 / 1024) + " Mo"));
                            });
                        }
                    };
                    ReleaseInstaller.Asset asset = ReleaseInstaller.latest(slug, progress);
                    String current = readInstalledVersion(slug);
                    if (installed(slug) && !current.isEmpty() && current.equals(asset.version)) {
                        successes.add(slug + " : déjà à jour (" + current + ")");
                        continue;
                    }
                    archive = ReleaseInstaller.download(getCacheDir(), asset, progress);
                    if (cancelled.get()) throw new CancellationException();
                    runOnUiThread(() -> {
                        progressBar.setIndeterminate(true);
                        message.setText("Installation de " + slug + "…");
                    });
                    try (InputStream input = new FileInputStream(archive)) {
                        installZip(input, slug);
                    }
                    
                    try (FileOutputStream out = new FileOutputStream(new File(bundle(slug), "release.txt"))) {
                        out.write(asset.version.getBytes(StandardCharsets.UTF_8));
                    }
                    successes.add(slug + " : " + asset.version);
                } catch (CancellationException e) {
                    cancelled.set(true);
                } catch (Exception e) {
                    failures.add(slug + " : " + (e.getMessage() == null ? e.toString() : e.getMessage()));
                } finally {
                    if (archive != null && archive.exists() && !archive.delete()) archive.deleteOnExit();
                }
            }
            boolean wasCancelled = cancelled.get();
            runOnUiThread(() -> {
                progressDialog.dismiss();
                showHome();
                StringBuilder result = new StringBuilder();
                if (!successes.isEmpty()) result.append("Installé(s) :\n").append(String.join("\n", successes));
                if (!failures.isEmpty()) result.append("\n\nÉchec(s) :\n").append(String.join("\n", failures));
                if (wasCancelled) result.append("\n\nOpération annulée.");
                if (result.length() == 0) result.append("Aucun module installé.");
                new AlertDialog.Builder(this).setTitle("Résultat de l'installation")
                    .setMessage(result.toString()).setPositiveButton("OK", null).show();
            });
        });
    }

    private void runDiagnostics() {
        Toast.makeText(this, "Préparation du test hors ligne…", Toast.LENGTH_SHORT).show();
        background.execute(() -> {
            String error = null;
            try (InputStream input = getAssets().open("test-wasm-local.zip")) {
                
                installZip(input, "testcraft");
            } catch (Exception e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final String failure = error;
            runOnUiThread(() -> {
                if (failure == null) openApp("testcraft");
                else new AlertDialog.Builder(this).setTitle("Diagnostic indisponible")
                    .setMessage(failure).setPositiveButton("OK", null).show();
            });
        });
    }

    private void importZip(String slug) {
        incomingSlug = slug;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, PICK_ZIP);
    }

    private static Uri[] uriArrayFromIntent(Intent data) {
        if (data == null) return null;
        ClipData clips = data.getClipData();
        if (clips != null && clips.getItemCount() > 0) {
            ArrayList<Uri> found = new ArrayList<>();
            for (int i = 0; i < clips.getItemCount() && i < 30; i++) {
                Uri u = clips.getItemAt(i).getUri();
                if (u != null) found.add(u);
            }
            if (!found.isEmpty()) return found.toArray(new Uri[0]);
        }
        Uri one = data.getData();
        return one == null ? null : new Uri[] {one};
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == NativeSaveBridge.REQUEST_SAVE) {
            if (nativeSave != null) nativeSave.savePickerResult(resultCode, data);
            return;
        }
        if (requestCode == PICK_WEB_FILE) {
            ValueCallback<Uri[]> callback = webFileCallback;
            webFileCallback = null;
            Uri[] selected = resultCode == RESULT_OK
                ? WebChromeClient.FileChooserParams.parseResult(resultCode, data) : null;
            
            
            if ((selected == null || selected.length == 0) && resultCode == RESULT_OK && data != null) {
                selected = uriArrayFromIntent(data);
            }
            
            
            approvedDocumentUris.clear();
            ArrayList<Uri> valid = new ArrayList<>();
            if (selected != null) for (Uri picked : selected) {
                if (picked != null && "content".equals(picked.getScheme()) && valid.size() < 30) {
                    valid.add(picked);
                    approvedDocumentUris.add(picked.toString());
                }
            }
            Uri[] permitted = valid.isEmpty() ? null : valid.toArray(new Uri[0]);
            if (callback != null) callback.onReceiveValue(permitted);
            if (permitted != null) {
                note("Photo/document : " + permitted.length + " fichier(s) transmis à la WebView");
                Uri sample = permitted[0];
                background.execute(() -> {
                    String status;
                    try (InputStream probe = getContentResolver().openInputStream(sample)) {
                        if (probe == null) throw new IOException("flux absent");
                        if (probe.read() == -1) throw new IOException("fichier vide");
                        status = "Lecture Android : OK";
                    } catch (Exception e) {
                        status = "Lecture Android : ÉCHEC (" + e.getClass().getSimpleName() + ")";
                    }
                    note(status);
                    if (status.contains("ÉCHEC")) runOnUiThread(() ->
                        Toast.makeText(this, "Lecture impossible. Maintiens ↻ pour le diagnostic.", Toast.LENGTH_LONG).show());
                });
            } else {
                note("Aucun fichier compatible reçu du sélecteur Android");
            }
            return;
        }
        if (requestCode != PICK_ZIP) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) { incomingSlug = null; return; }
        String slug = incomingSlug;
        Uri uri = data.getData();
        incomingSlug = null;
        if (slug == null) return;
        Toast.makeText(this, "Importation du module…", Toast.LENGTH_LONG).show();
        background.execute(() -> {
            String error = null;
            try { installZip(uri, slug); }
            catch (Exception e) { error = e.getMessage() == null ? e.toString() : e.getMessage(); }
            final String lastError = error;
            runOnUiThread(() -> {
                if (lastError != null) new AlertDialog.Builder(this).setTitle("Import impossible")
                    .setMessage(lastError).setPositiveButton("OK", null).show();
                else Toast.makeText(this, "Module importé !", Toast.LENGTH_SHORT).show();
                showHome();
            });
        });
    }

    private void installZip(Uri uri, String slug) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Fichier ZIP illisible");
            installZip(in, slug);
        }
    }

    
    private void installZip(InputStream input, String slug) throws IOException {
        if (!slug.matches("[a-z]+craft")) throw new IOException("Identifiant de module invalide");
        File root = new File(getFilesDir(), "bundles");
        if (!root.exists() && !root.mkdirs()) throw new IOException("Dossier privé indisponible");
        File staging = new File(root, slug + ".staging");
        deleteTree(staging);
        if (!staging.mkdirs()) throw new IOException("Impossible de créer le dossier temporaire");
        int count = 0;
        long extracted = 0L;
        try {
            {
                try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(input))) {
                    ZipEntry entry;
                    byte[] buffer = new byte[32768];
                    String prefix = staging.getCanonicalPath() + File.separator;
                    while ((entry = zip.getNextEntry()) != null) {
                        if (++count > MAX_FILES) throw new IOException("Archive trop volumineuse (fichiers)");
                        if (entry.getName().length() > 240 || entry.getName().split("/", -1).length > 25)
                            throw new IOException("Chemin ZIP trop profond ou trop long");
                        File out = new File(staging, entry.getName());
                        String canonical = out.getCanonicalPath();
                        if (!canonical.startsWith(prefix)) throw new IOException("Chemin interdit dans le ZIP");
                        if (entry.isDirectory()) {
                            if (!out.exists() && !out.mkdirs()) throw new IOException("Dossier ZIP illisible");
                        } else {
                            File parent = out.getParentFile();
                            if (!parent.exists() && !parent.mkdirs()) throw new IOException("Dossier ZIP illisible");
                            try (BufferedOutputStream dst = new BufferedOutputStream(new FileOutputStream(out))) {
                                int n;
                                while ((n = zip.read(buffer)) != -1) {
                                    extracted += n;
                                    if (extracted > MAX_BYTES) throw new IOException("Archive trop volumineuse (>450 Mo)");
                                    dst.write(buffer, 0, n);
                                }
                            }
                        }
                        zip.closeEntry();
                    }
                }
            }
            List<File> indexes = new ArrayList<>();
            scanIndexes(staging, indexes, 0);
            if (indexes.isEmpty()) throw new IOException("Ce ZIP ne contient pas d'index.html. Télécharge la version *-web-*.zip, pas l'installateur PC.");
            indexes.sort((a, b) -> Integer.compare(a.getAbsolutePath().length(), b.getAbsolutePath().length()));
            String relative = staging.toPath().relativize(indexes.get(0).toPath()).toString().replace(File.separatorChar, '/');
            try (FileOutputStream fout = new FileOutputStream(new File(staging, "entry.txt"))) {
                fout.write(relative.getBytes(StandardCharsets.UTF_8));
            }
            
            File old = bundle(slug);
            File backup = new File(root, slug + ".backup");
            deleteTree(backup);
            if (old.exists() && !old.renameTo(backup)) throw new IOException("Impossible de préparer la mise à jour");
            if (!staging.renameTo(old)) {
                if (backup.exists()) backup.renameTo(old);
                throw new IOException("Impossible d'activer le nouveau module");
            }
            deleteTree(backup);
        } finally {
            if (staging.exists()) deleteTree(staging);
        }
    }

    private static void scanIndexes(File dir, List<File> found, int depth) {
        if (depth > 8 || found.size() > 20) return;
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File item : list) {
            if (item.isDirectory()) scanIndexes(item, found, depth + 1);
            else if (item.getName().equalsIgnoreCase("index.html")) found.add(item);
        }
    }
    private static void deleteTree(File file) throws IOException {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        if (!file.delete()) throw new IOException("Impossible de nettoyer : " + file.getName());
    }

    private static int stablePort(String slug) {
        for (int i = 0; i < APPS.length; i++) if (APPS[i][1].equals(slug)) return 46910 + i;
        return 46950;
    }

    private void openApp(String slug) {
        File entryFile = new File(bundle(slug), "entry.txt");
        String entry;
        try {
            byte[] bytes = new byte[(int) entryFile.length()];
            try (java.io.FileInputStream is = new java.io.FileInputStream(entryFile)) {
                int n = is.read(bytes);
                if (n != bytes.length) throw new IOException("Fichier incomplet");
            }
            entry = new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            Toast.makeText(this, "Réimporte le module", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            if (server != null) server.close();
            server = new LocalHttpServer(new File(getFilesDir(), "bundles"), stablePort(slug));
            server.activate(slug, entry);
        } catch (IOException e) {
            if (server != null) { server.close(); server = null; }
            Toast.makeText(this, "Module invalide ou serveur indisponible : " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        note("Ouverture : " + slug);
        String localUrl = "http://127.0.0.1:" + server.getPort() + "/";
        LinearLayout wrapper = vertical();
        wrapper.setBackgroundColor(BG);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(4), dp(8), dp(4));
        top.setBackgroundColor(CARD);
        top.addView(button("← Accueil", 0xff34475e, this::showHome));
        TextView name = text(slug, 17, WHITE);
        name.setTypeface(null, 1);
        name.setPadding(dp(10), 0, 0, 0);
        top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(button("Pivoter", 0xff34475e, () -> {
            landscape = !landscape;
            setRequestedOrientation(landscape ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }));
        if ("photocraft".equals(slug)) {
            final Button touchMenu = button("Doigt ✓", 0xff34475e, () -> {});
            touchMenu.setText(photoTouchMenuEnabled ? "Doigt ✓" : "Doigt OFF");
            touchMenu.setOnClickListener(v -> {
                photoTouchMenuEnabled = !photoTouchMenuEnabled;
                touchMenu.setText(photoTouchMenuEnabled ? "Doigt ✓" : "Doigt OFF");
                if (browser != null) browser.evaluateJavascript(
                    "window.ArtCraftTouchCompat && window.ArtCraftTouchCompat.setEnabled(" +
                    photoTouchMenuEnabled + ");", null);
                Toast.makeText(this, photoTouchMenuEnabled
                    ? "Compatibilité des menus au doigt activée"
                    : "Compatibilité des menus au doigt désactivée", Toast.LENGTH_SHORT).show();
            });
            top.addView(touchMenu);
        }
        top.addView(button("Chrome", 0xff34475e, () -> {
            Toast.makeText(this, "Garde AtelierBridge ouvert : le module fonctionne sur son serveur local.", Toast.LENGTH_LONG).show();
            openExternal(localUrl);
        }));
        Button reload = button("↻", 0xff34475e, () -> { if (browser != null) browser.reload(); });
        
        reload.setOnLongClickListener(v -> { showFileReport(); return true; });
        top.addView(reload);
        wrapper.addView(top);
        approvedDocumentUris.clear();
        browser = new WebView(this);
        browser.setBackgroundColor(BG);
        nativeSave = new NativeSaveBridge(this, browser, server.getPort(), slug);
        browser.addJavascriptInterface(nativeSave, "ArtCraftSave");
        browser.getSettings().setJavaScriptEnabled(true);
        browser.getSettings().setDomStorageEnabled(true);
        browser.getSettings().setAllowFileAccess(false);
        
        browser.getSettings().setAllowContentAccess(true);
        browser.getSettings().setMediaPlaybackRequiresUserGesture(true);
        browser.getSettings().setBuiltInZoomControls(true);
        browser.getSettings().setDisplayZoomControls(false);
        browser.getSettings().setSupportZoom(true);
        browser.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage message) {
                String msg = message.message();
                if (msg.startsWith("[ArtCraftFile] ")) {
                    note(msg.length() > 180 ? msg.substring(0, 180) : msg);
                } else if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    if (msg.length() > 180) msg = msg.substring(0, 180) + "…";
                    note("Erreur JS ligne " + message.lineNumber() + ": " + msg);
                }
                return super.onConsoleMessage(message);
            }
            @Override public boolean onShowFileChooser(WebView webView,
                            ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (webFileCallback != null) webFileCallback.onReceiveValue(null);
                webFileCallback = callback;
                note("Sélecteur demandé par l'éditeur");
                try {
                    Intent request = params.createIntent();
                    request.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    note("Sélecteur WebView : mode=" + params.getMode());
                    startActivityForResult(request, PICK_WEB_FILE);
                    return true;
                } catch (Exception e) {
                    webFileCallback = null;
                    callback.onReceiveValue(null);
                    Toast.makeText(MainActivity.this, "Sélecteur de fichiers indisponible", Toast.LENGTH_LONG).show();
                    return true;
                }
            }
        });
        browser.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                if (url != null && url.startsWith(localUrl)) {
                    note("Page chargée");
                    installExportHook();
                }
                if ("photocraft".equals(slug) && url != null && url.startsWith(localUrl)) {
                    installPhotoCraftTouchMenuCompat();
                    installPhotoCraftFileDiagnostic();
                }
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                
                Uri u = request.getUrl();
                if ("http".equals(u.getScheme()) && "127.0.0.1".equals(u.getHost()) && u.getPort() == server.getPort())
                    return super.shouldInterceptRequest(view, request);


                if ("content".equals(u.getScheme()) && approvedDocumentUris.contains(u.toString())) {
                    note("Accès WebView au document autorisé");
                    return super.shouldInterceptRequest(view, request);
                }
                return new WebResourceResponse("text/plain", "UTF-8", new java.io.ByteArrayInputStream(new byte[0]));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("http".equals(u.getScheme()) && "127.0.0.1".equals(u.getHost()) && u.getPort() == server.getPort()) return false;
                if ("https".equals(u.getScheme()) || "http".equals(u.getScheme())) openExternal(u.toString());
                return true;
            }
        });
        browser.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (nativeSave == null) return;
            String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
            nativeSave.requestDownload(filename, url, mimeType);
        });
        wrapper.addView(browser, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(wrapper);
        browser.loadUrl(localUrl);
    }

    
    private void installExportHook() {
        if (browser == null) return;
        try (InputStream in = getAssets().open("artcraft-export-hook.js");
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[4096];
            int n;
            while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
            browser.evaluateJavascript(out.toString("UTF-8"), null);
        } catch (IOException e) {
            note("Pont export indisponible : " + e.getClass().getSimpleName());
        }
    }

    
    private void installPhotoCraftTouchMenuCompat() {
        if (browser == null) return;
        try (InputStream input = getAssets().open("photocraft-touch-menu.js");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[4096];
            int count;
            while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
            String script = output.toString("UTF-8") + "\n;window.ArtCraftTouchCompat && " +
                "window.ArtCraftTouchCompat.setEnabled(" + photoTouchMenuEnabled + ");";
            browser.evaluateJavascript(script, result ->
                note("Compatibilité tactile PhotoCraft initialisée (activée=" + photoTouchMenuEnabled + ")"));
        } catch (IOException error) {
            note("Compatibilité tactile non chargée : " + error.getClass().getSimpleName());
        }
    }

    
    private void installPhotoCraftFileDiagnostic() {
        if (browser == null) return;
        try (InputStream input = getAssets().open("photocraft-file-diagnostic.js");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[4096];
            int count;
            while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
            browser.evaluateJavascript(output.toString("UTF-8"), null);
            note("Diagnostic d'import PhotoCraft actif");
        } catch (IOException error) {
            note("Diagnostic d'import indisponible : " + error.getClass().getSimpleName());
        }
    }

    private void openExternal(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { Toast.makeText(this, "Navigateur externe indisponible", Toast.LENGTH_SHORT).show(); }
    }

    @Override public void onBackPressed() {
        if (browser != null) showHome();
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if (nativeSave != null) { nativeSave.close(); nativeSave = null; }
        if (browser != null) { browser.destroy(); browser = null; }
        if (server != null) server.close();
        background.shutdownNow();
        super.onDestroy();
    }
}
