const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync('app/src/main/assets/artcraft-export-hook.js', 'utf8');
let originallyClicked = 0, requests = [], fetches = [];
function HTMLAnchorElement() { this.href = ''; this.name = ''; this.hasDownload = false; }
HTMLAnchorElement.prototype.click = function() { originallyClicked++; };
HTMLAnchorElement.prototype.hasAttribute = function(k) { return k === 'download' && this.hasDownload; };
HTMLAnchorElement.prototype.getAttribute = function(k) { return k === 'download' ? this.name : null; };
const context = {
  window: {ArtCraftSave: {request: (...v) => requests.push(v)}},
  HTMLAnchorElement,
  fetch: u => {fetches.push(u); return Promise.resolve({ok:true, blob: async() => Buffer.from('data')});},
  console
};
vm.runInNewContext(source, context);
vm.runInNewContext(source, context); // injection idempotent
let menu = new HTMLAnchorElement(); menu.href = 'http://127.0.0.1:1234/'; menu.click();
assert.equal(originallyClicked, 1);
let download = new HTMLAnchorElement(); download.href = 'blob:http://127.0.0.1:1234/test';
download.hasDownload = true; download.name='test.svg'; download.click();
assert.equal(originallyClicked, 1);
assert.equal(requests.length, 1);
assert.equal(requests[0][0], 'test.svg');
assert.match(requests[0][1], /^export:\d+$/);
assert.equal(fetches.length, 1);
let external = new HTMLAnchorElement(); external.href = 'blob:https://example.com/test'; external.hasDownload=true; external.click();
assert.equal(originallyClicked, 2);
console.log('OK: programmatic local downloads only; other clicks untouched');
