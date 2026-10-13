# Déclaration Data Safety — feuille de vérification

Ne cochez **pas** « Aucune donnée collectée » sans contrôler le bundle compilé et les communications réelles des éditeurs intégrés.

Comportement prévu dans le code source :
- Aucun compte AtelierBridge, aucune publicité, aucun SDK d'analyse ajouté par le lanceur.
- Accès aux documents sélectionnés par l'utilisateur via le sélecteur Android.
- Enregistrement local des préférences, archives d'éditeurs décompressées, références d'exports.
- Liens externes optionnels vers GitHub.
- Paiement de l'application traité par Google Play, sans accès direct du lanceur aux moyens de paiement.

Contrôles avant réponse dans Play Console :
- [ ] Observer avec un proxy réseau les connexions lors du premier lancement, de l'ouverture/édition/export, et hors ligne.
- [ ] Examiner scripts et polices des archives WebAssembly intégrées pour télémétrie, services et dépendances externes.
- [ ] Vérifier la version réellement compilée et les permissions du manifeste final.
- [ ] Tester suppression des données, historique, documents enregistrés, désinstallation.
- [ ] Fournir une politique publique et un contact développeur réel.
- [ ] Déclarer exactement les données collectées, partagées, éventuellement chiffrées et les pratiques de suppression.
