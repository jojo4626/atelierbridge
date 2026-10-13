# Audit avant commercialisation — logiciels tiers

**Statut : NON VALIDÉ pour une mise en vente.** Cet audit est préparatoire, pas un avis juridique.

| Module | Version figée | Licence du code documentée par l'auteur | Points bloquants à vérifier avant vente |
| --- | --- | --- | --- |
| PhotoCraft | v0.8.2 | MIT ou Apache 2.0 | `NOTICE`, `ATTRIBUTION.md`, fichiers embarqués, polices/icônes, marque et logo |
| VectorCraft | v0.10.0 | MIT ou Apache 2.0 | `NOTICE`, `ASSETS.md`, polices embarquées, dessin du logo, marque et logo |
| FilmCraft et les 10 autres ateliers | Non inclus | Non audité | Ne pas intégrer dans l'édition payante avant un examen séparé |

Sources officielles :
- https://github.com/storytold/photocraft (README et licences)
- https://github.com/storytold/vectorcraft (README et licences)

Exigences avant commercialisation :
1. Vérifier la licence exacte de **l'archive Web binaire redistribuée** (et de toutes ses dépendances) : le code source MIT/Apache ne suffit pas pour les polices, images ou logos embarqués.
2. Conserver toutes les notices nécessaires dans l'application et dans l'archive distribuée. Demander une autorisation ou remplacer tout élément de marque non redistribuable.
3. Vérifier la permission contractuelle de distribuer ces éditeurs en les **incluant dans une application payante** ; ne pas affirmer qu'ils sont développés par AtelierBridge.
4. Examiner les droits des œuvres et des ressources incluses dans les fichiers ZIP officiels.
5. Contrôler que les fichiers ZIP achetés par les utilisateurs sont identiques aux archives vérifiées dans le workflow, et ne chargent pas de code additionnel non approuvé à l'exécution.

Les licences de code MIT/Apache permettent généralement l'usage commercial sous conditions, mais les éléments de marque ont leurs propres limites.
