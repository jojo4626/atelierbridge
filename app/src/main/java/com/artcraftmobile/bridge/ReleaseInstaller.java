package com.artcraftmobile.bridge;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.CancellationException;

final class ReleaseInstaller {
    private static final long MAX_ARCHIVE = 300L * 1024 * 1024;
    private static final int MAX_METADATA = 2 * 1024 * 1024;

    interface Progress {
        void transferred(long done, long total);
        boolean cancelled();
    }

    static final class Asset {
        final String name;
        final String version;
        final String url;
        final long size;
        final String digest;
        Asset(String name, String version, String url, long size, String digest) {
            this.name = name;
            this.version = version;
            this.url = url;
            this.size = size;
            this.digest = digest;
        }
    }

    private ReleaseInstaller() { }

    static Asset latest(String slug, Progress progress) throws Exception {
        if (!ReleaseAssetSelector.knownSlug(slug)) throw new IOException("Application inconnue");
        if (progress.cancelled()) throw new CancellationException();
        URL api = new URL("https://api.github.com/repos/storytold/" + slug + "/releases/latest");
        HttpURLConnection conn = (HttpURLConnection) api.openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(35000);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        conn.setRequestProperty("User-Agent", "AtelierBridge/1.0");
        try {
            int code = conn.getResponseCode();
            if (code == 404) throw new IOException("Aucune version officielle publiée pour " + slug);
            if (code == 403 || code == 429) throw new IOException("Limite GitHub atteinte. Réessaie plus tard ou utilise Importer ZIP.");
            if (code != 200) throw new IOException("GitHub ne répond pas (HTTP " + code + ")");
            byte[] bytes;
            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    if (progress.cancelled()) throw new CancellationException();
                    if (out.size() + n > MAX_METADATA) throw new IOException("Réponse GitHub trop volumineuse");
                    out.write(buffer, 0, n);
                }
                bytes = out.toByteArray();
            }
            JSONObject release = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            String version = release.optString("tag_name", "version inconnue");
            JSONArray assets = release.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject candidate = assets.getJSONObject(i);
                    String name = candidate.optString("name", "");
                    String url = candidate.optString("browser_download_url", "");
                    long size = candidate.optLong("size", -1);
                    if (!ReleaseAssetSelector.matchingWebArchive(slug, name)
                        || !ReleaseAssetSelector.trustedReleaseUrl(slug, url)) continue;
                    if (size < 0 || size > MAX_ARCHIVE) throw new IOException("Archive Web trop volumineuse");
                    return new Asset(name, version, url, size, candidate.optString("digest", ""));
                }
            }
            throw new IOException("Pas de ZIP WebAssembly pour la dernière version de " + slug + ". Tu peux toujours utiliser Importer ZIP.");
        } finally { conn.disconnect(); }
    }

    static File download(File cacheDir, Asset asset, Progress progress) throws Exception {
        if (progress.cancelled()) throw new CancellationException();
        if (!cacheDir.exists() && !cacheDir.mkdirs()) throw new IOException("Cache indisponible");
        File temp = File.createTempFile("artcraft-", ".zip.part", cacheDir);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(asset.url).openConnection();
            conn.setConnectTimeout(25000);
            conn.setReadTimeout(45000);
            conn.setRequestProperty("User-Agent", "AtelierBridge/1.0");
            int code = conn.getResponseCode();
            if (code != 200) throw new IOException("Téléchargement impossible (HTTP " + code + ")");
            long reportedSize = conn.getContentLengthLong();
            if (reportedSize > MAX_ARCHIVE) throw new IOException("Téléchargement trop volumineux");
            long total = reportedSize > 0 ? reportedSize : asset.size;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            long done = 0;
            progress.transferred(done, total);
            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(temp))) {
                byte[] buffer = new byte[32768];
                int n;
                long lastReport = 0;
                while ((n = in.read(buffer)) != -1) {
                    if (progress.cancelled()) throw new CancellationException();
                    done += n;
                    if (done > MAX_ARCHIVE) throw new IOException("Téléchargement >300 Mo");
                    out.write(buffer, 0, n);
                    md.update(buffer, 0, n);
                    if (done - lastReport >= 256 * 1024 || done == total) {
                        progress.transferred(done, total);
                        lastReport = done;
                    }
                }
            }
            if (done != asset.size) throw new IOException("Téléchargement incomplet : taille inattendue");
            if (asset.digest.startsWith("sha256:")) {
                StringBuilder hex = new StringBuilder();
                for (byte b : md.digest()) hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
                if (!asset.digest.substring(7).equalsIgnoreCase(hex.toString()))
                    throw new IOException("Échec de la vérification SHA-256 du ZIP");
            }
            if (progress.cancelled()) throw new CancellationException();
            return temp;
        } catch (Exception e) {
            if (!temp.delete()) temp.deleteOnExit();
            throw e;
        } finally { if (conn != null) conn.disconnect(); }
    }
}
