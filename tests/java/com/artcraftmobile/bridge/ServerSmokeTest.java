package com.artcraftmobile.bridge;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.Socket;
import java.io.OutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;

public class ServerSmokeTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static HttpURLConnection request(int port, String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL("http://127.0.0.1:" + port + path).openConnection();
        c.setConnectTimeout(3000);
        c.setReadTimeout(3000);
        return c;
    }
    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("artcraft-server-test").toFile();
        File docRoot = new File(root, "vectorcraft/dist/web");
        try {
            check(docRoot.mkdirs(), "mkdirs failed");
            Files.write(new File(docRoot, "index.html").toPath(), "INDEX OK".getBytes(StandardCharsets.UTF_8));
            Files.write(new File(docRoot, "app.wasm").toPath(), new byte[]{0,97,115,109});
            Files.write(new File(root, "vectorcraft/entry.txt").toPath(), "dist/web/index.html".getBytes(StandardCharsets.UTF_8));
            try (AutoCloseableServer svr = new AutoCloseableServer(root)) {
                LocalHttpServer server = svr.server;
                check(request(server.getPort(), "/").getResponseCode() == 404, "non actif doit retourner 404");
                server.activate("vectorcraft", "dist/web/index.html");
                HttpURLConnection index = request(server.getPort(), "/");
                check(index.getResponseCode() == 200, "index non servi à la racine");
                check(new String(index.getInputStream().readAllBytes(), StandardCharsets.UTF_8).equals("INDEX OK"), "index incorrect");
                HttpURLConnection wasm = request(server.getPort(), "/app.wasm");
                check(wasm.getResponseCode() == 200, "wasm inaccessible");
                check("application/wasm".equals(wasm.getContentType()), "MIME WASM incorrect");
                HttpURLConnection range = request(server.getPort(), "/app.wasm");
                range.setRequestProperty("Range", "bytes=1-2");
                check(range.getResponseCode() == 206, "pas de réponse partielle 206");
                check(range.getHeaderField("Content-Range").equals("bytes 1-2/4"), "Content-Range invalide");
                check(range.getInputStream().readAllBytes().length == 2, "corps range incorrect");
                HttpURLConnection head = request(server.getPort(), "/app.wasm");
                head.setRequestMethod("HEAD");
                check(head.getResponseCode() == 200, "HEAD refusé");
                check(head.getHeaderField("Content-Length").equals("4"), "HEAD Content-Length incorrect");
                HttpURLConnection invalidRange = request(server.getPort(), "/app.wasm");
                invalidRange.setRequestProperty("Range", "bytes=999-");
                check(invalidRange.getResponseCode() == 416, "range non satisfiable non refusé");
                try (Socket badHost = new Socket("127.0.0.1", server.getPort())) {
                    badHost.setSoTimeout(3000);
                    OutputStream o = badHost.getOutputStream();
                    o.write("GET / HTTP/1.1\r\nHost: example.org\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                    o.flush();
                    InputStream i = badHost.getInputStream();
                    String status = new String(i.readNBytes(30), StandardCharsets.US_ASCII);
                    check(status.contains("403"), "Host inattendu accepté");
                }
                check(request(server.getPort(), "/%2e%2e/%2e%2e/entry.txt").getResponseCode() == 403, "traversal accepté");
                check(request(server.getPort(), "/entry.txt").getResponseCode() == 404, "metadata exposée");
                server.deactivate();
                check(request(server.getPort(), "/").getResponseCode() == 404, "serveur reste actif");
                System.out.println("OK: localhost, Host, HTML, WASM, MIME, HEAD, Range 206/416, traversal, deactivation");
            }
        } finally {
            try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(root.toPath())) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }
    private static class AutoCloseableServer implements AutoCloseable {
        LocalHttpServer server;
        AutoCloseableServer(File root) throws Exception { server = new LocalHttpServer(root, 0); }
        public void close() { server.close(); }
    }
}
