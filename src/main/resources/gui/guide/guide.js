// URSC feature guide: walkthrough stepper, feature filter, section index and screenshot zoom.
(function () {
  'use strict';
  var panes = Array.prototype.slice.call(document.querySelectorAll('.step-pane'));
  var stepBtns = Array.prototype.slice.call(document.querySelectorAll('#steps button'));
  var prev = document.getElementById('prev');
  var next = document.getElementById('next');
  var count = document.getElementById('count');
  var cur = 0;

  function show(i) {
    cur = Math.max(0, Math.min(panes.length - 1, i));
    panes.forEach(function (p, k) { p.classList.toggle('hidden', k !== cur); });
    stepBtns.forEach(function (b, k) {
      if (k === cur) { b.setAttribute('aria-current', 'step'); } else { b.removeAttribute('aria-current'); }
    });
    prev.disabled = cur === 0;
    next.disabled = cur === panes.length - 1;
    count.textContent = 'Step ' + (cur + 1) + ' of ' + panes.length;
  }
  stepBtns.forEach(function (b) { b.addEventListener('click', function () { show(+b.getAttribute('data-step')); }); });
  prev.addEventListener('click', function () { show(cur - 1); });
  next.addEventListener('click', function () { show(cur + 1); });
  document.addEventListener('keydown', function (e) {
    if (e.target && (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA')) return;
    if (document.getElementById('zoom').open) return;
    if (e.key === 'ArrowRight') show(cur + 1);
    if (e.key === 'ArrowLeft') show(cur - 1);
  });
  show(0);

  // feature filter
  var q = document.getElementById('q');
  var shown = document.getElementById('shown');
  var none = document.getElementById('none');
  var features = Array.prototype.slice.call(document.querySelectorAll('.feature'));
  var groups = Array.prototype.slice.call(document.querySelectorAll('.group'));
  function filter() {
    var terms = q.value.toLowerCase().split(/\s+/).filter(Boolean);
    var n = 0;
    features.forEach(function (f) {
      var t = f.textContent.toLowerCase();
      var ok = terms.every(function (w) { return t.indexOf(w) >= 0; });
      f.classList.toggle('hidden', !ok);
      if (ok) n++;
    });
    groups.forEach(function (g) { g.classList.toggle('hidden', !g.querySelector('.feature:not(.hidden)')); });
    none.classList.toggle('hidden', n > 0);
    shown.textContent = terms.length ? n + ' of ' + features.length + ' features' : features.length + ' features';
  }
  q.addEventListener('input', filter);
  filter();

  // highlight the section in view
  var links = Array.prototype.slice.call(document.querySelectorAll('#index a'));
  if ('IntersectionObserver' in window) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) {
        if (en.isIntersecting) {
          links.forEach(function (a) { a.classList.toggle('on', a.getAttribute('href') === '#' + en.target.id); });
        }
      });
    }, { rootMargin: '-20% 0px -70% 0px' });
    groups.forEach(function (g) { io.observe(g); });
  }

  // screenshot zoom
  var dlg = document.getElementById('zoom');
  var big = document.getElementById('zoom-img');
  Array.prototype.slice.call(document.querySelectorAll('figure[data-zoom]')).forEach(function (f) {
    f.setAttribute('tabindex', '0');
    f.setAttribute('role', 'button');
    var open = function () {
      var img = f.querySelector('img');
      big.src = img.src;
      big.alt = img.alt;
      if (dlg.showModal) { dlg.showModal(); } else { dlg.setAttribute('open', ''); }
    };
    f.addEventListener('click', open);
    f.addEventListener('keydown', function (e) { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); open(); } });
  });
  document.getElementById('zoom-close').addEventListener('click', function () { dlg.close(); });
  dlg.addEventListener('click', function (e) { if (e.target === dlg) dlg.close(); });
}());
