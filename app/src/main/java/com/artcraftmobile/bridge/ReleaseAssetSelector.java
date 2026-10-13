package com.artcraftmobile.bridge;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

final class ReleaseAssetSelector {
    private ReleaseAssetSelector() { }

    static boolean knownSlug(String slug) {
        if (slug == null) return false;
        switch (slug) {
            case "photocraft": case "vectorcraft": case "filmcraft": case "lightcraft":
            case "pdfcraft": case "effectcraft": case "designcraft": case "figcraft":
            case "wordcraft": case "soundcraft": case "deckcraft": case "cadcraft":
            case "gridcraft": return true;
            default: return false;
        }
    }

    static boolean matchingWebArchive(String slug, String filename) {
        if (!knownSlug(slug) || filename == null) return false;
        String lower = filename.toLowerCase(Locale.ROOT);
        String prefix = slug + "-web-";
        if (!lower.startsWith(prefix) || !lower.endsWith(".zip")) return false;
        String version = lower.substring(prefix.length(), lower.length() - 4);
        return version.matches("[a-z0-9][a-z0-9._+\\-]*") && version.length() <= 80;
    }

    static boolean trustedReleaseUrl(String slug, String value) {
        if (!knownSlug(slug) || value == null) return false;
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                && "github.com".equalsIgnoreCase(uri.getHost())
                && uri.getRawUserInfo() == null && uri.getPort() == -1
                && uri.getRawQuery() == null && uri.getRawFragment() == null
                && uri.getRawPath() != null
                && uri.getRawPath().startsWith("/storytold/" + slug + "/releases/download/")
                && !uri.getRawPath().contains("..") && !uri.getRawPath().contains("%2f");
        } catch (URISyntaxException e) { return false; }
    }
}
