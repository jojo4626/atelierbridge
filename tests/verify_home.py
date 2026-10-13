from pathlib import Path

root = Path(__file__).resolve().parent.parent
main = (root / 'app/src/main/java/com/artcraftmobile/bridge/MainActivity.java').read_text()
app = (root / 'app/build.gradle').read_text()

assert 'private void showSection(int section)' in main
for label in ['Accueil', 'Catalogue', 'Fichiers', 'Réglages', 'Mes ateliers', 'À découvrir', 'Rechercher un atelier']:
    assert label in main, label
for method in ['openApp(resume)', 'installSelected(new String[]{slug})', 'this::chooseModules', 'this::showRecentExports', 'this::runDiagnostics', 'checkUpdates(true)']:
    assert method in main, method
assert 'getString("last_opened", "")' in main
assert 'putString("last_opened", slug)' in main
assert 'else if (currentSection != 0) showSection(0)' in main
assert 'onReceiveValue(permitted)' in main
assert 'installPhotoCraftTouchMenuCompat()' in main
assert "versionCode 101" in app
assert "versionName '1.0.0-rc.2'" in app
assert "applicationId 'io.github.jojo4626.atelierbridge'" in app
print('OK: accueil natif, 4 espaces, navigation, catalogue, fonctions existantes et identité inchangée')
