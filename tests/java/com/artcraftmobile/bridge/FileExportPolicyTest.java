package com.artcraftmobile.bridge;

final class FileExportPolicyTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        int port = 46911;
        check(FileExportPolicy.allowedUrl("blob:http://127.0.0.1:46911/aaa-bbb", port), "local blob");
        check(FileExportPolicy.allowedUrl("http://127.0.0.1:46911/export.svg", port), "local export");
        check(FileExportPolicy.allowedUrl("export:12", port), "preloaded browser blob");
        check(!FileExportPolicy.allowedUrl("export:../token", port), "invalid blob identifier");
        check(FileExportPolicy.allowedUrl("data:image/svg+xml;base64,PHN2Zz4=", port), "data URL");
        check(!FileExportPolicy.allowedUrl("blob:https://evil.example/abc", port), "remote blob");
        check(!FileExportPolicy.allowedUrl("http://127.0.0.1:9999/a.svg", port), "wrong local port");
        check(!FileExportPolicy.allowedUrl("https://github.com/storytold/a", port), "remote URL");
        check(!FileExportPolicy.allowedUrl("file:///data/a", port), "file URI");
        check(!FileExportPolicy.allowedUrl("http://user@127.0.0.1:46911/", port), "userinfo");
        check(!FileExportPolicy.allowedUrl("http://127.0.0.1:46911/#frag", port), "fragment");
        check("dessin.svg".equals(FileExportPolicy.safeFilename("../../dessin", "image/svg+xml")), "clean filename and ext");
        check("image.jpg".equals(FileExportPolicy.safeFilename("C:\\Users\\image.jpg", "image/jpeg")), "windows path");
        check("creation.bin".equals(FileExportPolicy.safeFilename("", "text/invalid;chars")), "invalid mime");
        check("application/octet-stream".equals(FileExportPolicy.safeMime("x/y;script")), "safe mime");
        check("image/png".equals(FileExportPolicy.safeMime("IMAGE/PNG")), "normalized mime");
        check("image/svg+xml".equals(FileExportPolicy.preferredMime("dessin.svg", "application/octet-stream")), "infer SVG MIME");
        check("image/png".equals(FileExportPolicy.preferredMime("photo.PNG", "application/octet-stream")), "infer PNG MIME");
        check("image/jpeg".equals(FileExportPolicy.preferredMime("photo.jpeg", "application/octet-stream")), "infer JPEG MIME");
        System.out.println("OK: Android export URL, filename and MIME safety policy");
    }
}
