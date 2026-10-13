

(function () {
  if (window.__artcraftProgrammaticDownloadHook) return;
  window.__artcraftProgrammaticDownloadHook = true;
  window.__artcraftPendingExports = window.__artcraftPendingExports || Object.create(null);
  window.__artcraftExportCounter = window.__artcraftExportCounter || 0;
  const originalClick = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function () {
    try {
      if (this.hasAttribute('download') && window.ArtCraftSave) {
        const url = this.href || '';
        if (url.startsWith('blob:http://127.0.0.1:') || url.startsWith('data:')) {
          const id = String(++window.__artcraftExportCounter);
          
          window.__artcraftPendingExports[id] = fetch(url).then(function (response) {
            if (!response.ok) throw new Error('Export inaccessible');
            return response.blob();
          });
          window.ArtCraftSave.request(this.getAttribute('download') || 'creation', 'export:' + id);
          return;
        }
      }
    } catch (e) { console.warn('[ArtCraftExport] ', e); }
    return originalClick.call(this);
  };
})();
