from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent
app = (root / "app/build.gradle").read_text()
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml")
release = (root / ".github/workflows/release.yml").read_text()
ci = (root / ".github/workflows/ci.yml").read_text()
signing = (root / "scripts/setup-signing-termux.sh").read_text()

assert "applicationId 'io.github.jojo4626.atelierbridge'" in app
assert "versionName '1.0.0-rc.2'" in app
assert "versionCode 101" in app
assert 'android:label="AtelierBridge"' in (root / "app/src/main/AndroidManifest.xml").read_text()
assert "ATELIERBRIDGE_KEYSTORE_FILE" in app
assert "github.ref == 'refs/heads/main'" in release
assert "workflow_dispatch:" in release
assert "ATELIERBRIDGE_CERT_SHA256" in release
assert '"${ACTUAL^^}" == "${EXPECTED_CERT_SHA256^^}"' in release
assert "apksigner" in release
assert "assembleRelease" in release
assert "AtelierBridge.apk" in release
assert "pull_request:" in ci
assert "ATELIERBRIDGE_KEYSTORE_B64" not in ci
assert "gh secret set ATELIERBRIDGE_KEYSTORE_B64" in signing
assert "existing" in signing and "cert" in signing
assert "ArtCraft Mobile" not in (root / "app/src/main/AndroidManifest.xml").read_text()
for name in ("LICENSE", "PRIVACY.md", "THIRD_PARTY_NOTICES.md", "CHANGELOG.md"):
    assert (root / name).is_file(), name
for path in root.rglob("*"):
    if path.is_file():
        assert path.suffix.lower() not in {".p12", ".jks", ".keystore", ".apk", ".aab", ".pem", ".key"}, path
print("OK: marque indépendante, certificat verrouillé, release signée, notices et secrets absents")
