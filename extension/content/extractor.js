/*
 * PricePulse product extractor. Injected on demand by the popup (activeTab + scripting), so the
 * extension needs no always-on content script and no broad host permissions.
 * Priority: 1) structured data (JSON-LD, meta, itemprop)  2) DOM heuristics  3) site adapters.
 * If nothing trustworthy is found it says so; it never invents a price.
 */
(function (root) {
  'use strict';
  var Core = root.PricePulseCore;

  // Level 3: adapters are best-effort, only for sites where generic detection is weak.
  var ADAPTERS = [
    { host: /(^|\.)amazon\.[a-z.]{2,6}$/, title: '#productTitle',
      price: ['.a-price:not(.a-text-price) .a-offscreen', '#corePrice_feature_div .a-offscreen', '#priceblock_ourprice', '#priceblock_dealprice'] },
    { host: /(^|\.)ebay\.[a-z.]{2,6}$/, title: 'h1.x-item-title__mainTitle',
      price: ['.x-price-primary .ux-textspans', '[itemprop=price]'] }
  ];

  var PRICE_SELECTORS = ['[itemprop="price"]', '[data-price]', '[class*="price" i]', '[id*="price" i]'];
  var STALE_HINT = /(^|[\s_-])(old|was|mrp|strike|original|list|compare|regular|crossed|previous|savings?|discount|emi|shipping)([\s_-]|$)/i;

  function text(el) { return (el && (el.getAttribute('content') || el.textContent) || '').replace(/\s+/g, ' ').trim(); }

  function isHidden(el) {
    for (var n = el; n && n.nodeType === 1; n = n.parentElement) {
      if (n.hasAttribute('hidden') || n.getAttribute('aria-hidden') === 'true') return true;
      var st = root.getComputedStyle ? root.getComputedStyle(n) : null;
      if (st && (st.display === 'none' || st.visibility === 'hidden')) return true;
    }
    return false;
  }

  function isStale(el) {
    for (var n = el, depth = 0; n && n.nodeType === 1 && depth < 4; n = n.parentElement, depth++) {
      var tag = n.tagName.toLowerCase();
      if (tag === 's' || tag === 'del' || tag === 'strike') return true;
      if (STALE_HINT.test((n.className && n.className.baseVal === undefined ? n.className : '') + ' ' + (n.id || ''))) return true;
    }
    return false;
  }

  // ---- Level 1: structured data ----
  function jsonLdProducts(doc) {
    var out = [];
    var scripts = doc.querySelectorAll('script[type="application/ld+json"]');
    function walk(node, depth) {
      if (!node || depth > 6) return;
      if (Array.isArray(node)) { node.forEach(function (n) { walk(n, depth + 1); }); return; }
      if (typeof node !== 'object') return;
      if (node['@graph']) walk(node['@graph'], depth + 1);
      var type = node['@type'];
      var isProduct = Array.isArray(type) ? type.indexOf('Product') !== -1 : type === 'Product';
      if (isProduct) out.push(node);
      if (node.mainEntity) walk(node.mainEntity, depth + 1);
    }
    scripts.forEach(function (s) { try { walk(JSON.parse(s.textContent), 0); } catch (e) { /* malformed JSON-LD is common */ } });
    return out;
  }

  function offerPrice(offers) {
    if (!offers) return null;
    if (Array.isArray(offers)) {
      for (var i = 0; i < offers.length; i++) { var r = offerPrice(offers[i]); if (r) return r; }
      return null;
    }
    var raw = offers.price != null ? offers.price : offers.lowPrice;
    if (raw == null && offers.priceSpecification) raw = offers.priceSpecification.price;
    var price = Core.parseMachineDecimal(raw);
    var cur = offers.priceCurrency || (offers.priceSpecification && offers.priceSpecification.priceCurrency) ||
      Core.detectCurrency(String(raw));
    if (!price || !cur) return null;
    return { price: price, currency: String(cur).toUpperCase() };
  }

  function fromStructured(doc) {
    var products = jsonLdProducts(doc);
    for (var i = 0; i < products.length; i++) {
      var o = offerPrice(products[i].offers);
      if (o) return { name: products[i].name, price: o.price, currency: o.currency, method: 'json-ld', confidence: 'high' };
    }
    var amountEl = doc.querySelector('meta[property="product:price:amount"], meta[property="og:price:amount"], meta[itemprop="price"]');
    var curEl = doc.querySelector('meta[property="product:price:currency"], meta[property="og:price:currency"], meta[itemprop="priceCurrency"]');
    if (amountEl && curEl) {
      var p = Core.parseMachineDecimal(amountEl.getAttribute('content'));
      var c = String(curEl.getAttribute('content') || '').toUpperCase();
      if (p && c) return { name: null, price: p, currency: c, method: 'meta', confidence: 'high' };
    }
    return null;
  }

  // ---- Level 2 & 3: DOM ----
  function candidateFromElements(els, method) {
    var best = null;
    els.forEach(function (el, index) {
      if (isHidden(el) || isStale(el)) return;
      var t = text(el);
      if (!t || t.length > 40) return; // long text is a description, not a price
      var price = Core.parseAmount(t);
      var cur = Core.detectCurrency(t);
      if (!price || !cur) return;
      var size = 0;
      try { size = parseFloat(root.getComputedStyle(el).fontSize) || 0; } catch (e) { /* ignore */ }
      var score = size * 2 - index; // prefer larger text, then earlier in the document
      if (!best || score > best.score) best = { price: price, currency: cur, score: score, method: method };
    });
    return best;
  }

  function fromAdapter(doc, host) {
    for (var i = 0; i < ADAPTERS.length; i++) {
      var a = ADAPTERS[i];
      if (!a.host.test(host)) continue;
      for (var j = 0; j < a.price.length; j++) {
        var c = candidateFromElements(Array.prototype.slice.call(doc.querySelectorAll(a.price[j])), 'adapter');
        if (c) {
          var titleEl = doc.querySelector(a.title);
          return { name: titleEl ? text(titleEl) : null, price: c.price, currency: c.currency, method: 'adapter', confidence: 'medium' };
        }
      }
    }
    return null;
  }

  function fromDom(doc) {
    var itemprop = candidateFromElements(Array.prototype.slice.call(doc.querySelectorAll('[itemprop="price"]')), 'itemprop');
    if (itemprop) return { name: null, price: itemprop.price, currency: itemprop.currency, method: 'itemprop', confidence: 'medium' };
    var els = [];
    PRICE_SELECTORS.slice(1).forEach(function (sel) {
      doc.querySelectorAll(sel).forEach(function (el) { if (els.indexOf(el) === -1) els.push(el); });
    });
    var c = candidateFromElements(els, 'dom');
    return c ? { name: null, price: c.price, currency: c.currency, method: 'dom', confidence: 'medium' } : null;
  }

  function pageTitle(doc) {
    var og = doc.querySelector('meta[property="og:title"]');
    var h1 = doc.querySelector('h1');
    return (og && og.getAttribute('content')) || text(h1) || doc.title || '';
  }

  function run() {
    var doc = root.document;
    var loc = root.location;
    var base = { found: false, url: loc.href.split('#')[0], domain: loc.hostname.replace(/^www\./, '') };
    var hit = fromStructured(doc) || fromAdapter(doc, loc.hostname) || fromDom(doc);
    if (!hit) {
      base.name = pageTitle(doc).slice(0, 300);
      base.reason = 'Unable to detect a valid product price.';
      return base;
    }
    var check = Core.validateDetected(hit.price, hit.currency);
    if (!check.ok) {
      base.name = pageTitle(doc).slice(0, 300);
      base.reason = 'Unable to detect a valid product price.';
      return base;
    }
    base.found = true;
    base.name = String(hit.name || pageTitle(doc)).replace(/\s+/g, ' ').trim().slice(0, 300);
    base.price = check.value;
    base.currency = hit.currency;
    base.method = hit.method;
    base.confidence = hit.confidence;
    return base;
  }

  root.PricePulseExtractor = { run: run };
})(typeof globalThis !== 'undefined' ? globalThis : this);
