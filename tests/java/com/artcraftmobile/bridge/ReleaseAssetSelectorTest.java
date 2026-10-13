package com.artcraftmobile.bridge;

public final class ReleaseAssetSelectorTest {
    private static void require(boolean cond, String info) {
        if (!cond) throw new AssertionError(info);
    }
    public static void main(String[] args) {
        require(ReleaseAssetSelector.matchingWebArchive("vectorcraft", "vectorcraft-web-0.10.0.zip"), "vector expected");
        require(ReleaseAssetSelector.matchingWebArchive("photocraft", "photocraft-web-1.2.3.zip"), "photo expected");
        require(!ReleaseAssetSelector.matchingWebArchive("vectorcraft", "vectorcraft-0.10.0-windows-x64-portable.zip"), "reject Windows ZIP");
        require(!ReleaseAssetSelector.matchingWebArchive("vectorcraft", "evil-web-0.10.0.zip"), "reject other project");
        require(!ReleaseAssetSelector.matchingWebArchive("unknowncraft", "unknowncraft-web-0.3.zip"), "reject unknown craft");
        require(!ReleaseAssetSelector.matchingWebArchive("vectorcraft", "vectorcraft-web-../../payload.zip"), "reject malformed name");
        String url = "https://github.com/storytold/vectorcraft/releases/download/v0.10.0/vectorcraft-web-0.10.0.zip";
        require(ReleaseAssetSelector.trustedReleaseUrl("vectorcraft", url), "trusted GitHub URL");
        require(!ReleaseAssetSelector.trustedReleaseUrl("photocraft", url), "reject different project URL");
        require(!ReleaseAssetSelector.trustedReleaseUrl("vectorcraft", "https://github.com.evil.test/storytold/vectorcraft/releases/download/v0.10.0/b.zip"), "reject spoofed host");
        require(!ReleaseAssetSelector.trustedReleaseUrl("vectorcraft", "http://github.com/storytold/vectorcraft/releases/download/v0.10.0/b.zip"), "require HTTPS");
        require(!ReleaseAssetSelector.trustedReleaseUrl("vectorcraft", "https://github.com/storytold/vectorcraft/releases/download/../download/b.zip"), "reject traversal");
        System.out.println("OK: official Web archive selectors and GitHub release URL validation");
    }
}
