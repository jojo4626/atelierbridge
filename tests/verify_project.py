#!/usr/bin/env python3
"""Zero-dependency CI checks for the packaged offline WASM diagnostic."""
from pathlib import Path
from zipfile import ZipFile
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent
manifest = root/'app/src/main/AndroidManifest.xml'
ET.parse(manifest)
ET.parse(root/'app/src/main/res/xml/network_security_config.xml')
ET.parse(root/'app/src/main/res/drawable/ic_launcher.xml')
assert 'versionCode 101' in (root/'app/build.gradle').read_text()
assets = root/'app/src/main/assets/test-wasm-local.zip'
example = root/'examples/test-wasm-local.zip'
assert assets.read_bytes() == example.read_bytes(), 'diagnostic asset and downloadable test differ'
with ZipFile(assets) as z:
    wanted = {'dist/web/index.html', 'dist/web/app.js', 'dist/web/test.wasm'}
    assert wanted.issubset(set(z.namelist()))
    assert z.read('dist/web/test.wasm') == b'\x00asm\x01\x00\x00\x00'
    html = z.read('dist/web/index.html').decode('utf-8')
    script = z.read('dist/web/app.js').decode('utf-8')
    assert 'app.js' in html and 'test.wasm' in script and 'pointerdown' in script
source = (root/'app/src/main/java/com/artcraftmobile/bridge/MainActivity.java').read_text()
installer = (root/'app/src/main/java/com/artcraftmobile/bridge/ReleaseInstaller.java').read_text()
assert 'installSelected' in source and 'chooseModules' in source
assert 'api.github.com/repos/storytold/' in installer
assert 'ReleaseAssetSelector.trustedReleaseUrl' in installer
print('OK: Android XML, v1, diagnostic WASM and official-release installer wiring')

files = root/'app/src/main/java/com/artcraftmobile/bridge'
assert (files/'NativeSaveBridge.java').is_file()
assert 'ACTION_CREATE_DOCUMENT' in (files/'NativeSaveBridge.java').read_text()
assert 'onShowFileChooser' in (files/'MainActivity.java').read_text()
assert 'NativeSaveBridge.REQUEST_SAVE' in (files/'MainActivity.java').read_text()

assert 'setAllowContentAccess(true)' in source
assert 'setAllowFileAccess(false)' in source
assert 'FLAG_GRANT_READ_URI_PERMISSION' in source
assert 'onReceiveValue(permitted)' in source
assert 'Diagnostic fichiers et menus' in source
print('OK: photo picker URI access, file:// disabled, interaction diagnostic')

assert "applicationId 'io.github.jojo4626.atelierbridge'" in (root/'app/build.gradle').read_text()

assert 'wrapper.addView(browser, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1))' in source
assert 'FrameLayout frame' not in source
assert 'boolean tablet =' not in source
assert 'downloadClickHook()' not in source
assert 'SCREEN_ORIENTATION_SENSOR_LANDSCAPE' in source  # user-selected Pivoter still exists
print('OK: classic touch layout; no forced rotation or document-wide click capture')
assert 'installPhotoCraftTouchMenuCompat()' in source
assert 'photocraft-touch-menu.js' in source
assert (root/'app/src/main/assets/photocraft-touch-menu.js').is_file()
assert 'window.ArtCraftTouchCompat.setEnabled' in source
print('OK: PhotoCraft-only menu touch compatibility wiring')

assert 'approvedDocumentUris.contains(u.toString())' in source
assert 'approvedDocumentUris.clear()' in source
assert 'uriArrayFromIntent(data)' in source
assert 'photocraft-file-diagnostic.js' in source
assert (root/'app/src/main/assets/photocraft-file-diagnostic.js').is_file()
print('OK: selected content:// is passed through; only approved document URIs allowed')

assert 'RecentExports.record' in (files/'NativeSaveBridge.java').read_text()
assert (files/'RecentExports.java').exists()
assert (root/'app/src/main/assets/artcraft-export-hook.js').exists()
assert 'HTMLAnchorElement.prototype.click' in (root/'app/src/main/assets/artcraft-export-hook.js').read_text()
assert "document.addEventListener('click'" not in (root/'app/src/main/assets/artcraft-export-hook.js').read_text()
assert 'installExportHook()' in source
assert 'showRecentExports()' in source and 'checkUpdates(false)' in source
assert 'ATELIERBRIDGE_KEYSTORE_FILE' in (root/'app/build.gradle').read_text()
print('OK: v1 export bridge, recent exports, update checks, signing configuration')

assert (root/'scripts/setup-signing-termux.sh').exists()
assert 'app-release.apk' in (root/'.github/workflows/release.yml').read_text()
assert 'ATELIERBRIDGE_KEYSTORE_B64' in (root/'.github/workflows/release.yml').read_text()
print('OK: stable signing and release workflows')
