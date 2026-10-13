

(() => {
  'use strict';
  if (window.ArtCraftTouchCompat) return;

  const canvas = document.getElementById('photocraft_canvas');
  if (!canvas || typeof PointerEvent !== 'function') return;

  let enabled = true;
  let activePointerId = null;
  let swallowTouchEnd = false;
  let startX = 0;
  let startY = 0;


  const MENU_HEIGHT_CSS = 44;

  function inMenu(x, y) {
    const rect = canvas.getBoundingClientRect();
    return x >= rect.left && x <= rect.right &&
           y >= rect.top && y < rect.top + Math.min(MENU_HEIGHT_CSS, rect.height);
  }

  function simulated(type, source, buttons) {
    const params = {
      bubbles: true, cancelable: true, composed: true,
      clientX: source.clientX, clientY: source.clientY,
      screenX: source.screenX, screenY: source.screenY,
      pointerId: 918273, pointerType: 'mouse', isPrimary: true,
      button: 0, buttons, detail: 1,
    };
    
    if (type === 'pointerdown') {
      document.dispatchEvent(new MouseEvent('mousemove', params));
    }
    canvas.dispatchEvent(new PointerEvent(type, params));
  }

  function suppress(event) {
    if (event.cancelable) event.preventDefault();
    event.stopImmediatePropagation();
  }

  document.addEventListener('pointerdown', function(event) {
    if (!enabled || event.pointerType !== 'touch' || !event.isPrimary ||
        activePointerId !== null || !inMenu(event.clientX, event.clientY)) return;
    activePointerId = event.pointerId;
    swallowTouchEnd = true;
    startX = event.clientX;
    startY = event.clientY;
    suppress(event);
    canvas.focus({preventScroll: true});
    simulated('pointerdown', event, 1);
  }, {capture: true, passive: false});

  document.addEventListener('pointermove', function(event) {
    if (event.pointerType !== 'touch' || activePointerId !== event.pointerId) return;
    suppress(event);
    
    
  }, {capture: true, passive: false});

  function release(event) {
    if (event.pointerType !== 'touch' || activePointerId !== event.pointerId) return;
    suppress(event);
    simulated('pointerup', {
      clientX: startX, clientY: startY,
      screenX: event.screenX, screenY: event.screenY,
    }, 0);
    activePointerId = null;
  }
  document.addEventListener('pointerup', release, {capture: true, passive: false});
  document.addEventListener('pointercancel', release, {capture: true, passive: false});


  ['touchstart', 'touchmove', 'touchend', 'touchcancel'].forEach(type => {
    document.addEventListener(type, function(event) {
      if (!swallowTouchEnd) return;
      suppress(event);
      if (type === 'touchend' || type === 'touchcancel') {
        if (event.touches.length === 0) swallowTouchEnd = false;
      }
    }, {capture: true, passive: false});
  });

  window.ArtCraftTouchCompat = {
    setEnabled(value) {
      enabled = Boolean(value);
      if (!enabled) {
        
        if (activePointerId !== null) {
          simulated('pointerup', {clientX: startX, clientY: startY, screenX: 0, screenY: 0}, 0);
          activePointerId = null;
        }
        swallowTouchEnd = false;
      }
      return enabled;
    },
    isEnabled() { return enabled; },
  };
})();
