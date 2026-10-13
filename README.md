# AtelierBridge

Lanceur Android indépendant pour les versions WebAssembly de logiciels de création, notamment PhotoCraft et VectorCraft. Le catalogue télécharge les archives Web officielles à la demande, sans intégrer leurs binaires dans l'APK.

La version `1.0.0-rc.1` est un candidat de diffusion publique. Elle conserve la compatibilité tactile de PhotoCraft, la sélection de photos Android, les exports locaux, l'historique des fichiers exportés et la mise à jour manuelle des modules.

## Compatibilité et limites

- Android 8.0 et versions ultérieures ; fonctionnement vérifié auparavant sur une tablette Android 13 pour la branche 0.5.x.
- WebAssembly, WebGL2 et parfois WebGPU sont nécessaires selon les modules.
- Les fonctions des modules dépendent de leurs propres versions Web. Les exports particuliers doivent être testés sur appareil.
- Les modifications du package Android créent **une nouvelle application**. Les données d'une ancienne version ArtCraft Mobile ne sont pas migrées automatiquement.
- Publication GitHub APK directe ; cette version n'est pas déclarée conforme à Google Play (API cible 33).

## Publier depuis Android

1. Décompresse cette archive dans un **nouveau** dossier Termux.
2. Crée un dépôt GitHub public nommé `atelierbridge` sous le compte `jojo4626`.
3. Publie les sources :

```bash
pkg install -y git gh openjdk-17 openssl coreutils unzip
cd ~/atelierbridge
git init -b main
git add -A
git commit -m "AtelierBridge 1.0.0-rc.1"
gh auth login
gh repo create jojo4626/atelierbridge --public --source=. --remote=origin --push
bash scripts/setup-signing-termux.sh
```

4. Dans GitHub > **Settings > Environments**, crée `production` et active si possible les approbations requises.
5. Dans **Actions > Publier APK signée**, sélectionne la branche `main`, entre `v1.0.0-rc.1` et lance le workflow.
6. Après compilation, trouve `AtelierBridge.apk` et `AtelierBridge.sha256` dans **Releases**. Chaque APK publique est signée et contrôlée par rapport au certificat enregistré.

N'active pas la publication de Releases signées depuis des demandes de fusion. Les clés ne doivent être accessibles qu'aux mainteneurs autorisés. Le workflow limite la publication au dépôt et à la branche indiqués.

## Signatures

Le script de configuration **réutilise la clé existante** de la série 0.6, si elle existe dans `~/.artcraftmobile-signing`, sinon en crée une. Il transfère sa copie encodée aux **Secrets** GitHub, pas au dépôt de code. Le certificat SHA-256 est mémorisé dans une variable du dépôt : une signature différente bloque la publication. Les secrets ne sont pas présents dans le ZIP.

L'identifiant définitif de cette édition est `io.github.jojo4626.atelierbridge`. Pour toute mise à jour, garde cet identifiant, augmente `versionCode` et signe avec **la même clé**. La prochaine version pourra alors remplacer celle-ci sans désinstallation.

Copie les fichiers `~/.artcraftmobile-signing/artcraft-release.p12` et `~/.artcraftmobile-signing/password` vers un stockage privé fiable. Leur perte empêcherait de publier une mise à jour avec la même identité de signature.

## Tests

```bash
python3 tests/verify_project.py
bash tests/smoke_server.sh
bash tests/test_release_selector.sh
bash tests/test_file_export_policy.sh
node tests/test_photocraft_touch.cjs
node tests/test_photocraft_file_bridge.cjs
node tests/test_v06_export_hook.cjs
```

Le code source du lanceur est sous MIT. Les modules, leurs marques et leurs contenus conservent leurs licences respectives : lire [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) et [PRIVACY.md](PRIVACY.md). AtelierBridge n'est pas affilié à l'équipe ArtCraft.
