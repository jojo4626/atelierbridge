const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const script = fs.readFileSync('app/src/main/assets/photocraft-touch-menu.js', 'utf8');
const listeners = {document: {}, canvas: {}};
const received = [];
function node(name) {
  return {
    addEventListener(type, fn, opts) {
      (listeners[name][type] ??= []).push(fn);
    },
    dispatchEvent(e) {
      if (name === 'canvas') {
        for (const fn of listeners.document[e.type] || []) fn(e);
      }
      for (const fn of listeners[name][e.type] || []) fn(e);
      if (name === 'canvas') received.push(e);
      return !e.defaultPrevented;
    },
    getBoundingClientRect() { return {left: 0, top: 0, right: 1000, width: 1000, height: 700}; },
    focus() {},
  };
}
class FakeEvent {
  constructor(type, opts={}) {
    this.type = type;
    Object.assign(this, opts);
    this.cancelable = opts.cancelable ?? true;
    this.defaultPrevented = false;
    this.stopped = false;
  }
  preventDefault() { this.defaultPrevented = true; }
  stopImmediatePropagation() { this.stopped = true; }
}
const canvas = node('canvas');
const document = node('document');
document.getElementById = id => id === 'photocraft_canvas' ? canvas : null;
const window = {};
vm.runInNewContext(script, {window, document, PointerEvent: FakeEvent, MouseEvent: FakeEvent});
assert.equal(window.ArtCraftTouchCompat.isEnabled(), true);
function finger(type,x,y,id=4) {
  const e = new FakeEvent(type,{pointerType:'touch',isPrimary:true,pointerId:id,clientX:x,clientY:y,screenX:x,screenY:y});
  document.dispatchEvent(e);
  return e;
}
let original = finger('pointerdown',120,20);
assert.equal(original.defaultPrevented, true);
assert.equal(original.stopped, true);
assert.equal(received.at(-1).type, 'pointerdown');
assert.equal(received.at(-1).pointerType, 'mouse');
assert.equal(received.at(-1).buttons, 1);
original = finger('pointerup',120,20);
assert.equal(original.defaultPrevented,true);
assert.equal(received.at(-1).type,'pointerup');
assert.equal(received.at(-1).buttons,0);
const touchEnd = new FakeEvent('touchend',{touches:[]});
document.dispatchEvent(touchEnd);
assert.equal(touchEnd.defaultPrevented,true);
const length = received.length;
assert.equal(finger('pointerdown',100,250).defaultPrevented, false);
assert.equal(finger('pointerup',100,250).defaultPrevented, false);
assert.equal(finger('pointerdown',100,80).defaultPrevented, false);
assert.equal(received.length, length);
const pen = new FakeEvent('pointerdown',{pointerType:'pen',clientX:50,clientY:15,isPrimary:true,pointerId:9});
document.dispatchEvent(pen);
assert.equal(pen.defaultPrevented,false);
window.ArtCraftTouchCompat.setEnabled(false);
assert.equal(window.ArtCraftTouchCompat.isEnabled(), false);
const length2=received.length;
assert.equal(finger('pointerdown',120,20).defaultPrevented, false);
assert.equal(received.length,length2);
window.ArtCraftTouchCompat.setEnabled(true);
assert.equal(finger('pointerdown',120,20).defaultPrevented,true);
assert.equal(finger('pointerup',120,20).defaultPrevented,true);
console.log('OK: PhotoCraft menu touch -> mouse, pen untouched, editor untouched, toggle');
