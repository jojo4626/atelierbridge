package com.artcraftmobile.bridge;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class LocalHttpServer {
    private final File root;
    private final ServerSocket listener;
    private final ExecutorService workers = Executors.newFixedThreadPool(3);
    private final Thread acceptThread;
    private volatile boolean running = true;
    private volatile File activeDocumentRoot;

    LocalHttpServer(File root, int preferredPort) throws IOException {
        this.root = root;
        ServerSocket temp;
        try {
            temp = new ServerSocket(preferredPort, 16, InetAddress.getByName("127.0.0.1"));
        } catch (java.net.BindException occupied) {
            temp = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"));
        }
        listener = temp;
        acceptThread = new Thread(() -> {
            while (running) {
                try {
                    Socket client = listener.accept();
                    workers.submit(() -> serve(client));
                } catch (IOException e) {
                    if (running) android.util.Log.w("ArtCraftLocal", "Serveur HTTP indisponible", e);
                } catch (java.util.concurrent.RejectedExecutionException e) {
                    
                    if (running) android.util.Log.w("ArtCraftLocal", "Serveur saturé", e);
                }
            }
        }, "ArtCraftLocalHTTP");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    int getPort() { return listener.getLocalPort(); }

    synchronized void activate(String slug, String relativeIndex) throws IOException {
        if (!slug.matches("[a-z]+craft") || relativeIndex == null || !relativeIndex.endsWith("index.html")) {
            throw new IOException("Module invalide");
        }
        File appDir = new File(root, slug).getCanonicalFile();
        File entry = new File(appDir, relativeIndex).getCanonicalFile();
        if (!entry.isFile() || !entry.toPath().startsWith(appDir.toPath())) {
            throw new IOException("index.html manquant ou hors du module");
        }
        File docRoot = entry.getParentFile();
        if (!docRoot.toPath().startsWith(appDir.toPath())) throw new IOException("Racine invalide");
        activeDocumentRoot = docRoot;
    }

    void deactivate() { activeDocumentRoot = null; }

    private void serve(Socket socket) {
        try (Socket client = socket) {
            client.setSoTimeout(15000);
            BufferedInputStream in = new BufferedInputStream(client.getInputStream());
            BufferedOutputStream out = new BufferedOutputStream(client.getOutputStream());
            String request = readLine(in);
            if (request == null) return;
            String[] parts = request.split(" ");
            if (parts.length < 2 || !("GET".equals(parts[0]) || "HEAD".equals(parts[0]))) {
                error(out, 405, "Method Not Allowed"); return;
            }
            String path = parts[1].split("\\?", 2)[0];
            if (path.length() > 4096) { error(out, 414, "URI Too Long"); return; }
            try { path = URLDecoder.decode(path.replace("+", "%2B"), "UTF-8"); }
            catch (Exception e) { error(out, 400, "Bad Request"); return; }
            int n = 0;
            String rangeHeader = null;
            String hostHeader = null;
            while (n++ < 70) {
                String line = readLine(in);
                if (line == null || line.isEmpty()) break;
                if (line.regionMatches(true, 0, "Range:", 0, 6))
                    rangeHeader = line.substring(6).trim();
                if (line.regionMatches(true, 0, "Host:", 0, 5))
                    hostHeader = line.substring(5).trim();
            }
            
            String expected = "127.0.0.1:" + listener.getLocalPort();
            if (!expected.equals(hostHeader)) { error(out, 403, "Forbidden"); return; }
            if (path.indexOf('\0') >= 0 || !path.startsWith("/")) {
                error(out, 403, "Forbidden"); return;
            }
            File docRoot = activeDocumentRoot;
            if (docRoot == null || !docRoot.isDirectory()) {
                error(out, 404, "Not Found"); return;
            }
            File resource = new File(docRoot, path.equals("/") ? "index.html" : path.substring(1));
            String base = docRoot.getCanonicalPath() + File.separator;
            if (!resource.getCanonicalPath().startsWith(base)) {
                error(out, 403, "Forbidden"); return;
            }
            if (!resource.isFile() || "entry.txt".equals(resource.getName())) {
                error(out, 404, "Not Found"); return;
            }
            String type = mime(resource.getName());
            long size = resource.length(), start = 0, end = size - 1;
            boolean partial = rangeHeader != null;
            if (partial) {
                try {
                    if (!rangeHeader.matches("bytes=[0-9]*-[0-9]*")) throw new NumberFormatException();
                    String[] pair = rangeHeader.substring(6).split("-", -1);
                    if (pair[0].isEmpty()) {
                        long suffix = Long.parseLong(pair[1]);
                        if (suffix <= 0) throw new NumberFormatException();
                        start = Math.max(0, size - suffix);
                    } else {
                        start = Long.parseLong(pair[0]);
                    }
                    if (!pair[0].isEmpty() && !pair[1].isEmpty())
                        end = Math.min(size - 1, Long.parseLong(pair[1]));
                    if (start < 0 || start > end || start >= size) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    byte[] h = ("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */" + size +
                        "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
                    out.write(h); out.flush(); return;
                }
            }
            long contentSize = partial ? end - start + 1 : size;
            String headers = "HTTP/1.1 " + (partial ? "206 Partial Content" : "200 OK") + "\r\n" +
                "Content-Type: " + type + "\r\n" +
                "Content-Length: " + contentSize + "\r\n" +
                "Accept-Ranges: bytes\r\n" +
                (partial ? "Content-Range: bytes " + start + "-" + end + "/" + size + "\r\n" : "") +
                "Cross-Origin-Opener-Policy: same-origin\r\n" +
                "Cross-Origin-Embedder-Policy: require-corp\r\n" +
                "Cross-Origin-Resource-Policy: same-origin\r\n" +
                "X-Content-Type-Options: nosniff\r\n" +
                "Cache-Control: no-cache\r\n" +
                "Connection: close\r\n\r\n";
            out.write(headers.getBytes(StandardCharsets.US_ASCII));
            if (!"HEAD".equals(parts[0])) {
                try (FileInputStream file = new FileInputStream(resource)) {
                    file.getChannel().position(start);
                    byte[] b = new byte[65536];
                    long remaining = contentSize;
                    int k;
                    while (remaining > 0 && (k = file.read(b, 0, (int)Math.min(b.length, remaining))) != -1) {
                        out.write(b, 0, k);
                        remaining -= k;
                    }
                }
            }
            out.flush();
        } catch (IOException e) {
            android.util.Log.d("ArtCraftLocal", "Connexion terminée: " + e.getMessage());
        }
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 16384; i++) {
            int x = in.read();
            if (x == -1) return b.length() == 0 ? null : b.toString();
            if (x == '\n') return b.toString();
            if (x != '\r') b.append((char) x);
        }
        throw new IOException("Ligne HTTP trop longue");
    }
    private static void error(OutputStream out, int status, String message) throws IOException {
        byte[] content = message.getBytes(StandardCharsets.UTF_8);
        String h = "HTTP/1.1 " + status + " " + message + "\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: " + content.length + "\r\nConnection: close\r\n\r\n";
        out.write(h.getBytes(StandardCharsets.US_ASCII));
        out.write(content);
        out.flush();
    }
    private static String mime(String name) {
        String s = name.toLowerCase(java.util.Locale.ROOT);
        if (s.endsWith(".html") || s.endsWith(".htm")) return "text/html; charset=utf-8";
        if (s.endsWith(".js") || s.endsWith(".mjs")) return "text/javascript; charset=utf-8";
        if (s.endsWith(".css")) return "text/css; charset=utf-8";
        if (s.endsWith(".wasm")) return "application/wasm";
        if (s.endsWith(".json") || s.endsWith(".map")) return "application/json";
        if (s.endsWith(".svg")) return "image/svg+xml";
        if (s.endsWith(".png")) return "image/png";
        if (s.endsWith(".jpg") || s.endsWith(".jpeg")) return "image/jpeg";
        if (s.endsWith(".webp")) return "image/webp";
        if (s.endsWith(".gif")) return "image/gif";
        if (s.endsWith(".ico")) return "image/x-icon";
        if (s.endsWith(".woff2")) return "font/woff2";
        if (s.endsWith(".woff")) return "font/woff";
        if (s.endsWith(".ttf")) return "font/ttf";
        if (s.endsWith(".mp4")) return "video/mp4";
        if (s.endsWith(".mp3")) return "audio/mpeg";
        if (s.endsWith(".wav")) return "audio/wav";
        if (s.endsWith(".bin") || s.endsWith(".data")) return "application/octet-stream";
        return "application/octet-stream";
    }
    void close() {
        running = false;
        try { listener.close(); } catch (IOException ignored) { }
        workers.shutdownNow();
    }
}
