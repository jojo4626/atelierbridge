const assert = require('node:assert/strict');
const fs = require('node:fs');
const src = fs.readFileSync('app/src/main/java/com/artcraftmobile/bridge/MainActivity.java','utf8');
const probe = fs.readFileSync('app/src/main/assets/photocraft-file-diagnostic.js','utf8');
assert.match(src, /if \("content"\.equals\(u\.getScheme\(\)\) && approvedDocumentUris\.contains\(u\.toString\(\)\)\)/);
assert.match(src, /Uri\[\] permitted = valid\.isEmpty\(\) \? null : valid\.toArray/);
assert.match(src, /uriArrayFromIntent\(data\)/);
assert.match(src, /approvedDocumentUris\.clear\(\)/);
assert.match(probe, /\.slice\(0, 16\)\.arrayBuffer\(\)/);
assert.doesNotMatch(probe, /\.name\s*[),;]/); // no filenames in diagnostic
console.log('OK: content:// selection whitelisted, passive file-import diagnostics');
