<div align="center">

# 🎨 AtelierBridge

### Votre studio créatif, sur Android.

**Photo, dessin vectoriel et outils de création réunis dans une seule application.**  
Installez les éditeurs qui vous intéressent en un clic, créez sur votre tablette et retrouvez vos exports dans Android.

<sub>Open-source creative app launcher for Android · PhotoCraft & VectorCraft · One-tap installation</sub>

[![Télécharger l'APK](https://img.shields.io/badge/TÉLÉCHARGER-L'APK_ANDROID-80c7ff?style=for-the-badge&logo=android&logoColor=101827)](https://github.com/jojo4626/atelierbridge/releases)
[![Code source](https://img.shields.io/badge/VOIR-LE_CODE_SOURCE-252b39?style=for-the-badge&logo=github&logoColor=white)](https://github.com/jojo4626/atelierbridge)

[![Version](https://img.shields.io/github/v/release/jojo4626/atelierbridge?include_prereleases&label=version)](https://github.com/jojo4626/atelierbridge/releases)
[![Tests Android](https://github.com/jojo4626/atelierbridge/actions/workflows/ci.yml/badge.svg)](https://github.com/jojo4626/atelierbridge/actions/workflows/ci.yml)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3ddc84?logo=android&logoColor=white)
[![Licence MIT](https://img.shields.io/badge/licence-MIT-blue.svg)](LICENSE)

**Gratuit · Open source · Sans compte · Modules utilisables hors ligne après installation**

<sub>Application communautaire indépendante, non affiliée aux développeurs de la suite ArtCraft.</sub>

</div>

---

## ✨ Créez sans multiplier les applications

AtelierBridge transforme votre tablette Android en **atelier créatif polyvalent**. Au lieu d'installer manuellement plusieurs applications ou de manipuler des fichiers ZIP, choisissez un éditeur depuis le catalogue : AtelierBridge récupère sa version Web compatible lorsqu'elle est disponible et l'exécute localement.

| 🖼️ Photo | ✏️ Illustration | 📁 Fichiers | 📦 Catalogue |
|:---|:---|:---|:---|
| Retoucher des photos et travailler avec des calques | Dessiner, modifier et exporter des SVG | Ouvrir des documents Android et retrouver ses exports | Installer et mettre à jour les éditeurs disponibles |

### Ce que vous pouvez faire

- **Installer en un clic** les modules WebAssembly proposés par les projets compatibles, ou importer une archive ZIP manuellement.
- **Travailler au doigt ou au stylet**, avec une adaptation tactile des menus de PhotoCraft.
- **Ouvrir des photos et des documents** à l'aide du sélecteur de fichiers Android.
- **Exporter vos créations** via les mécanismes pris en charge par l'éditeur et le sélecteur Android.
- **Reprendre votre travail rapidement** depuis l'accueil, consulter les exports récents et chercher des mises à jour.
- **Utiliser les modules déjà installés hors connexion**, sous réserve des besoins propres à chaque éditeur.

## 🧰 Les éditeurs

| Éditeur | Utilisation | Projet |
|:---|:---|:---|
| **PhotoCraft** | Retouche photo, pinceaux, calques et images | [Code officiel](https://github.com/storytold/photocraft) |
| **VectorCraft** | Illustration vectorielle, dessin et SVG | [Code officiel](https://github.com/storytold/vectorcraft) |
| **Autres ateliers** | Image, média, PDF et création : selon les versions Web publiées | Depuis le catalogue intégré |

> **À savoir :** AtelierBridge est un **lanceur**, pas un portage Android natif de chaque éditeur. La compatibilité, les performances et les fonctions d'import/export dépendent du module WebAssembly et de l'appareil. Certains ateliers peuvent ne pas proposer de version Web utilisable.

## 📲 Installer sur votre tablette

**1.** Ouvrez la page **[GitHub Releases](https://github.com/jojo4626/atelierbridge/releases)**.  
**2.** Téléchargez le fichier **`AtelierBridge.apk`** de la version souhaitée.  
**3.** Ouvrez l'APK avec Android et autorisez son installation depuis votre navigateur ou votre gestionnaire de fichiers si Android le demande.  
**4.** Lancez AtelierBridge, ouvrez le **Catalogue**, puis touchez **Installer** sur PhotoCraft, VectorCraft ou un autre atelier disponible.

> Les versions marquées **Pre-release** sont des versions de test : elles sont publiées pour validation et peuvent encore comporter des limitations. L'APK est distribuée sur GitHub et non via le Play Store.

### Navigation

| 🏠 Accueil | 🔎 Catalogue | 📂 Fichiers | ⚙️ Réglages |
|:---|:---|:---|:---|
| Ateliers installés, reprise rapide, suggestions | Recherche, filtres, installation | Exports récents accessibles à Android | Mises à jour, diagnostic, gestion |

## 🔐 Confidentialité et sécurité

AtelierBridge ne nécessite **aucun compte** et n'intègre **ni publicité ni outil de suivi analytique**. Les modules téléchargés et l'historique des exports sont stockés sur l'appareil.

Le téléchargement initial et la recherche de mises à jour passent par GitHub. Les documents sont sélectionnés par l'utilisateur à l'aide des outils Android. Les logiciels téléchargés restent des **applications tierces**, avec leurs propres licences et comportements.

- [Politique de confidentialité](PRIVACY.md)
- [Politique de sécurité](SECURITY.md)
- [Notices des composants tiers](THIRD_PARTY_NOTICES.md)
- Chaque publication contient une empreinte **SHA-256** permettant de contrôler le téléchargement.

## 🛠️ Pour les développeurs

Le projet est un lanceur Android utilisant **Java**, **Android WebView**, un serveur HTTP local pour les modules installés et **GitHub Actions** pour la compilation et les APK signées.

```bash
git clone https://github.com/jojo4626/atelierbridge.git
cd atelierbridge
```

Pour vérifier le projet et compiler, consultez les workflows :

- [`Tests Android`](.github/workflows/ci.yml) — compilation de contrôle sans clé de publication.
- [`Publier APK signée`](.github/workflows/release.yml) — publication manuelle des APK signées.
- [Guide de contribution](CONTRIBUTING.md) — proposer un correctif ou une amélioration.

Les **clés de signature privées ne sont pas présentes dans ce dépôt**. Elles sont conservées séparément et utilisées uniquement par la chaîne de publication autorisée.

## 🤝 Contribuer

Vous avez trouvé un bug ou un éditeur qui ne fonctionne pas sur votre appareil ? Ouvrez une [issue](https://github.com/jojo4626/atelierbridge/issues) en indiquant votre version d'Android, le module concerné et les étapes permettant de reproduire le problème. Les contributions visant l'accessibilité tactile, la compatibilité des fichiers et l'amélioration du catalogue sont les bienvenues.

---

<div align="center">

### La création, partout avec vous.

[**⬇ Télécharger AtelierBridge**](https://github.com/jojo4626/atelierbridge/releases) · [**Signaler un problème**](https://github.com/jojo4626/atelierbridge/issues) · [**Contribuer**](CONTRIBUTING.md)

<sub>AtelierBridge est un projet indépendant. PhotoCraft, VectorCraft, ArtCraft et leurs identités visuelles appartiennent à leurs détenteurs respectifs. Ce dépôt ne constitue ni un produit officiel ni une approbation par leurs équipes.</sub>

</div>
