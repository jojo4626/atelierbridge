
(() => {
  'use strict';
  if (window.ArtCraftFileProbe) return;
  window.ArtCraftFileProbe = true;
  document.addEventListener('change', event => {
    const element = event.target;
    if (!(element instanceof HTMLInputElement) || element.type !== 'file') return;
    const files = element.files;
    if (!files || files.length === 0) {
      console.info('[ArtCraftFile] Champ fichier vide après sélection');
      return;
    }
    const first = files[0];
    console.info('[ArtCraftFile] Événement change reçu : ' + files.length + ' fichier(s)');
    first.slice(0, 16).arrayBuffer().then(bytes => {
      console.info('[ArtCraftFile] Lecture WebView : OK (' + bytes.byteLength + ' octets de test)');
    }).catch(err => {
      
      console.info('[ArtCraftFile] Lecture WebView : ÉCHEC (' + (err && err.name || 'inconnue') + ')');
    });
  }, true);
})();
