from pathlib import Path
r = Path(__file__).resolve().parent.parent
build=(r/'app/build.gradle').read_text()
java=(r/'app/src/main/java/com/artcraftmobile/bridge/MainActivity.java').read_text()
workflow=(r/'.github/workflows/play-bundle.yml').read_text()
assert "applicationId 'io.github.jojo4626.atelierbridge.play'" in build
assert 'targetSdk 36' in build and 'compileSdk playStore ? 36 : 35' in build
assert 'bundleRelease' in workflow and 'release.p12' not in workflow
assert 'ATELIERBRIDGE_KEYSTORE_B64' in workflow
assert 'prepareBundledModules()' in java
assert 'getAssets().open("play/" + slug + ".zip")' in java
assert 'if (isPlayEdition()) return;' in java
assert 'if (isPlayEdition()) prepareBundledModules();' in java
assert 'PLAY_APPS' in java and 'availableApps()' in java
assert (r/'scripts/fetch-play-modules.py').is_file()
assert 'sha256:' in (r/'scripts/fetch-play-modules.py').read_text()
print('OK: Play variant API 36, separate app ID, bundled modules, upload signing and gated downloads')
