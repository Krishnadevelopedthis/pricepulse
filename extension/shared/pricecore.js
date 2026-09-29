/*
 * PricePulse core: price parsing, validation, URL checks and formatting.
 * Classic script (no imports) so it can be loaded by the popup, injected into pages next to the
 * extractor, and evaluated in Node tests. Mirrors backend util/PriceParser + PriceValidator.
 */
(function (root) {
  'use strict';

  var SYMBOLS = [['₹', 'INR'], ['$', 'USD'], ['€', 'EUR'], ['£', 'GBP'], ['¥', 'JPY']];
  var SUPPORTED = ['INR', 'USD', 'EUR', 'GBP', 'JPY', 'AUD', 'CAD', 'CHF', 'SGD', 'AED'];
  var ISO_RE = /\b(INR|USD|EUR|GBP|JPY|AUD|CAD|CHF|SGD|AED)\b/;
  var RS_RE = /\b(?:Rs\.?|INR)\s*(?=\d)/i;
  var TOKEN_RE = /\d[\d.,\u00A0\u202F' ]*\d|\d/;
  var MACHINE_RE = /^\d{1,12}(\.\d{1,4})?$/;
  var USER_PRICE_RE = /^\d{1,10}(\.\d{1,2})?$/;

  function detectCurrency(text) {
    if (typeof text !== 'string') return null;
    for (var i = 0; i < SYMBOLS.length; i++) if (text.indexOf(SYMBOLS[i][0]) !== -1) return SYMBOLS[i][1];
    var m = ISO_RE.exec(text.toUpperCase());
    if (m) return m[1];
    if (RS_RE.test(text)) return 'INR';
    return null;
  }

  function count(str, ch) { return str.split(ch).length - 1; }

  /** Parses a human-formatted amount; returns a plain decimal string or null. */
  function parseAmount(text) {
    if (typeof text !== 'string') return null;
    var m = TOKEN_RE.exec(text);
    if (!m) return null;
    var token = m[0].replace(/[\u00A0\u202F' ]/g, '');
    var lastDot = token.lastIndexOf('.');
    var lastComma = token.lastIndexOf(',');
    var normalized;
    if (lastDot >= 0 && lastComma >= 0) {
      var dec = lastDot > lastComma ? '.' : ',';
      var grp = dec === '.' ? ',' : '.';
      normalized = token.split(grp).join('').replace(dec, '.');
    } else if (lastDot >= 0 || lastComma >= 0) {
      var sep = lastDot >= 0 ? '.' : ',';
      var digitsAfter = token.length - token.lastIndexOf(sep) - 1;
      if (count(token, sep) > 1 || digitsAfter === 3) normalized = token.split(sep).join('');
      else normalized = token.replace(sep, '.');
    } else {
      normalized = token;
    }
    return /^\d+(\.\d+)?$/.test(normalized) ? normalized : null;
  }

  /** For JSON-LD / meta values where "." is always the decimal separator. */
  function parseMachineDecimal(text) {
    if (text === null || text === undefined) return null;
    var t = String(text).trim();
    if (MACHINE_RE.test(t)) return t;
    return parseAmount(t);
  }

  function maxScale(currency) { return currency === 'JPY' ? 0 : 2; }

  /** Validates a price string typed by the user. Returns {ok, value?, error?}. */
  function parseUserPrice(raw, currency) {
    if (raw === null || raw === undefined || String(raw).trim() === '') return { ok: false, error: 'Enter a price.' };
    var t = String(raw).trim();
    if (!USER_PRICE_RE.test(t)) return { ok: false, error: 'Enter a number such as 4999 or 4999.50.' };
    var n = Number(t);
    if (!isFinite(n) || n <= 0) return { ok: false, error: 'Price must be greater than zero.' };
    var decimals = t.indexOf('.') === -1 ? 0 : t.split('.')[1].replace(/0+$/, '').length;
    if (decimals > maxScale(currency || 'USD')) return { ok: false, error: 'Too many decimal places for ' + currency + '.' };
    return { ok: true, value: t };
  }

  /** Validates a detected price (string or number). Never fabricates a value. */
  function validateDetected(price, currency) {
    if (SUPPORTED.indexOf(currency) === -1) return { ok: false, error: 'Unsupported currency.' };
    var s = typeof price === 'number' ? String(price) : price;
    if (typeof s !== 'string') return { ok: false, error: 'Missing price.' };
    if (s.indexOf('.') !== -1) s = s.replace(/(\.\d*?)0+$/, '$1').replace(/\.$/, '');
    return parseUserPrice(s, currency);
  }

  function validateUrl(raw) {
    if (!raw || !String(raw).trim()) return { ok: false, error: 'URL is required.' };
    var u;
    try { u = new URL(String(raw).trim()); } catch (e) { return { ok: false, error: 'URL is not valid.' }; }
    if (u.protocol !== 'http:' && u.protocol !== 'https:') return { ok: false, error: 'Only http and https pages can be tracked.' };
    if (u.username || u.password) return { ok: false, error: 'URLs with credentials are not supported.' };
    if (String(raw).length > 2048) return { ok: false, error: 'URL is too long.' };
    u.hash = '';
    return { ok: true, value: u.toString() };
  }

  /** Signed-amount formatting: formatMoney("3199.00","INR") -> "₹3,199". */
  function formatMoney(value, currency) {
    var n = Number(value);
    if (!isFinite(n)) return '—';
    try {
      var digits = maxScale(currency);
      var hasFraction = Math.abs(n - Math.round(n)) > 1e-9;
      return new Intl.NumberFormat(currency === 'INR' ? 'en-IN' : undefined, {
        style: 'currency', currency: currency,
        minimumFractionDigits: hasFraction ? digits : 0, maximumFractionDigits: digits
      }).format(n);
    } catch (e) {
      return currency + ' ' + n;
    }
  }

  root.PricePulseCore = {
    SUPPORTED: SUPPORTED, detectCurrency: detectCurrency, parseAmount: parseAmount,
    parseMachineDecimal: parseMachineDecimal, parseUserPrice: parseUserPrice,
    validateDetected: validateDetected, validateUrl: validateUrl, formatMoney: formatMoney
  };
})(typeof globalThis !== 'undefined' ? globalThis : this);
