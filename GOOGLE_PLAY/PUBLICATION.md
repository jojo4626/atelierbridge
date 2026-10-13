# AtelierBridge — édition Google Play : mode d'emploi

## Ce que fait cette variante

- Crée un AAB avec `applicationId` distinct `io.github.jojo4626.atelierbridge.play` pour ne pas compromettre la version gratuite GitHub.
- Vise Android API 36, avec une version `1.0.0-play.1` (`versionCode 201`).
- Intègre des versions **figées** PhotoCraft v0.8.2 et VectorCraft v0.10.0 dans le paquet via GitHub Actions ; leur SHA-256 est vérifié sur les métadonnées des releases officielles.
- N'affiche pas les options de téléchargement automatique et d'import ZIP dans l'édition Play. Les mises à jour des moteurs nécessitent un nouveau AAB.
- Réutilise les secrets de signature actuels comme clé d'**upload** (Google Play App Signing gère la signature finale installée sur l'appareil).
- Laisse inchangée la construction APK GitHub sans la propriété `playStore`.

## Installer le correctif sur le dépôt

Depuis Termux, après avoir téléchargé `AtelierBridge-GooglePlay-mise-a-jour.zip` dans Téléchargements :

```sh
cd ~/atelierbridge
git pull --rebase origin main
unzip -o ~/storage/downloads/AtelierBridge-GooglePlay-mise-a-jour.zip
git add -A
git commit -m "Préparer l'édition Google Play"
git push origin main
```

## Construire le .aab pour un test interne

Dans GitHub Actions, lancer `Construire AAB Google Play (test)` sur `main`. Le workflow utilise l'environnement `production` déjà configuré, télécharge les ZIP officiels, vérifie leurs empreintes SHA-256 et produit l'artefact **AtelierBridge-Play-AAB-TEST-PRIVATE** si toutes les vérifications réussissent.

**Il n'est pas publié automatiquement** en Release publique. Ne partagez ni `.p12`, ni mots de passe de signature, ni secrets.

Les binaires et leurs avis de licence **doivent être audités juridiquement avant la mise en vente**. Si un ZIP tiers n'a pas de SHA-256 dans l'API GitHub ou qu'un élément de marque est interdit, la construction ou la publication doit rester bloquée.

## Création de l'application dans Play Console

1. Créer son compte développeur et renseigner profil de paiement, identité, coordonnées.
2. Créer une nouvelle application au prix initial souhaité (par exemple 2,99 €). Préférer le statut payant **dès la création**, car passer ensuite de gratuit à payant sur le même identifiant n'est pas autorisé.
3. Fournir la fiche de `FICHE_PLAY_STORE_FR.md`, des **captures réelles** de l'édition Play et une icône conforme.
4. Publier une URL de confidentialité accessible, renseigner le formulaire Data Safety à partir d'une vérification de l'AAB.
5. Importer le `.aab` dans un canal de tests internes, configurer Play App Signing en utilisant la clé du bundle comme clé d'upload.
6. Tester sur plusieurs appareils, particulièrement Android 13, 15 et 16, puis suivre les tests fermés et l'examen de Play Console.
7. Après validation des licences, de la marque et de la politique Google Play sur le contenu embarqué, demander l'accès production puis publier.

## Blocages avant vente

- [ ] Compiler réellement l'AAB sur GitHub et confirmer que les deux éditeurs embarqués se chargent entièrement hors ligne.
- [ ] Vérifier licences, polices, icônes et marques des archives redistribuées.
- [ ] Effectuer un audit réseau et de confidentialité sur la version signée.
- [ ] Préparer de vraies captures, e-mail de contact et déclaration Data Safety.
- [ ] Valider le bon fonctionnement avec Android API 36, toutes fonctions d'import/export et gestes tactiles.
- [ ] Vérifier les politiques Google Play sur WebView, logiciels tiers et code interprété avec le paquet exact.

Cette édition est un **candidat technique pour test interne**, pas une publication Play Store déjà approuvée.
