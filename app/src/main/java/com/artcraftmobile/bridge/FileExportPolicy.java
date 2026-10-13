package com.artcraftmobile.bridge;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

final class FileExportPolicy {
    static final long MAX_EXPORT_BYTES = 150L * 1024 * 1024;
    private FileExportPolicy() { }

    static boolean allowedUrl(String value, int port) {
        if (value == null || value.length() > 4096) return false;
        if (value.matches("export:[0-9]{1,12}")) return true; 
        if (value.startsWith("data:")) return value.length() <= MAX_EXPORT_BYTES * 2;
        if (value.startsWith("blob:")) return allowedLocalHttp(value.substring(5), port);
        return allowedLocalHttp(value, port);
    }

    private static boolean allowedLocalHttp(String value, int port) {
        try {
            URI u = new URI(value);
            return "http".equalsIgnoreCase(u.getScheme())
                && "127.0.0.1".equals(u.getHost()) && u.getPort() == port
                && u.getRawUserInfo() == null && u.getRawFragment() == null;
        } catch (URISyntaxException e) { return false; }
    }

    
    static String preferredMime(String filename, String declared) {
        String normalized = safeMime(declared);
        if (!"application/octet-stream".equals(normalized)) return normalized;
        if (filename == null) return normalized;
        String n = filename.toLowerCase(Locale.ROOT);
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".zip")) return "application/zip";
        return normalized;
    }

    static String safeFilename(String filename, String mime) {
        if (filename == null) filename = "";
        
        filename = filename.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1).trim();
        filename = filename.replaceAll("[\\p{Cntrl}<>:\"|?*]", "_").replaceAll("^\\.+", "");
        if (filename.isEmpty()) filename = "creation";
        if (filename.length() > 100) filename = filename.substring(0, 100);
        if (!filename.contains(".")) filename += extension(mime);
        return filename;
    }

    static String safeMime(String mime) {
        if (mime == null || !mime.matches("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+"))
            return "application/octet-stream";
        return mime.toLowerCase(Locale.ROOT);
    }

    private static String extension(String mime) {
        switch (safeMime(mime)) {
            case "image/svg+xml": return ".svg";
            case "image/png": return ".png";
            case "image/jpeg": return ".jpg";
            case "application/pdf": return ".pdf";
            case "image/webp": return ".webp";
            case "text/plain": return ".txt";
            case "application/zip": return ".zip";
            default: return ".bin";
        }
    }
}
