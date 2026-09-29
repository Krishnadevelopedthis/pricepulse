import { api, ApiError } from '../shared/api.js';
import { describeChange, money, timeAgo } from '../shared/format.js';
import { renderLineChart } from '../charts/lineChart.js';

const Core = globalThis.PricePulseCore;
const $ = (id) => document.getElementById(id);

const state = { tab: null, detection: null, tracked: null, manual: false, offline: false, confirmTimer: null };

const METHOD_LABEL = { 'json-ld': 'structured data', meta: 'page metadata', itemprop: 'page markup', dom: 'page text', adapter: 'site rules', manual: 'manual entry' };

// ---------- small UI helpers ----------
function showSection(name) {
  $('stateLoading').hidden = name !== 'loading';
  $('stateMessage').hidden = name !== 'message';
  $('stateProduct').hidden = name !== 'product';
}

function setBanner(text, kind = '') {
  const b = $('banner');
  b.hidden = !text;
  b.className = `banner ${kind}`.trim();
  b.textContent = text || '';
}

function showMessage(text, help = '', { manual = false } = {}) {
  $('messageText').textContent = text;
  $('messageHelp').textContent = help;
  $('manualBtn').hidden = !manual;
  showSection('message');
}

function setLoading(btn, on) {
  btn.classList.toggle('loading', on);
  btn.disabled = on;
}

function currencySymbol(code) {
  try {
    return new Intl.NumberFormat(undefined, { style: 'currency', currency: code }).formatToParts(0).find((p) => p.type === 'currency').value;
  } catch { return code; }
}

function fieldError(inputId, errId, message) {
  $(errId).textContent = message || '';
  const input = $(inputId);
  input.setAttribute('aria-invalid', message ? 'true' : 'false');
  const affix = input.closest('.affix');
  if (affix) affix.dataset.invalid = message ? 'true' : 'false';
}

// ---------- detection ----------
async function detect(tabId) {
  try {
    await chrome.scripting.executeScript({ target: { tabId }, files: ['shared/pricecore.js', 'content/extractor.js'] });
    const [res] = await chrome.scripting.executeScript({ target: { tabId }, func: () => globalThis.PricePulseExtractor.run() });
    return res && res.result ? res.result : { found: false, reason: 'Unable to detect a valid product price.' };
  } catch {
    return { found: false, reason: 'PricePulse cannot read this page.' };
  }
}

// ---------- rendering ----------
function renderCard({ name, domain, priceText, tag, tagClass = '', change = null, meta = '' }) {
  $('domain').textContent = domain;
  $('productName').textContent = name || 'Untitled product';
  $('price').textContent = priceText;
  const t = $('statusTag');
  t.textContent = tag;
  t.className = `tag ${tagClass}`.trim();
  const c = $('changeLine');
  c.hidden = !change;
  if (change) { c.textContent = change.text; c.className = `change ${change.kind}`; }
  $('metaLine').textContent = meta;
}

function renderNew() {
  const d = state.detection;
  renderCard({ name: d.name, domain: d.domain, priceText: money(d.price, d.currency), tag: 'Product detected',
    meta: `Price read from ${METHOD_LABEL[d.method] || d.method}. Confirm it matches the page.` });
  $('manualBox').hidden = true;
  $('targetCur').textContent = currencySymbol(d.currency);
  $('target').value = '';
  $('trackBtn').textContent = 'Track Price';
  $('trackBtn').disabled = state.offline;
  $('historyBtn').disabled = true;
  $('historyBtn').title = 'Track this product to start building history';
  $('removeBtn').hidden = true;
  $('sparkline').replaceChildren();
  $('trendNote').hidden = true;
  updateTargetHint();
  showSection('product');
}

function renderManual() {
  const host = new URL(state.tab.url).hostname.replace(/^www\./, '');
  state.manual = true;
  state.detection = { found: false, name: state.tab.title || host, domain: host, url: state.tab.url };
  renderCard({ name: state.detection.name, domain: host, priceText: '—', tag: 'Manual entry', tagClass: 'muted',
    meta: 'Enter the price shown on the page. PricePulse could not read it automatically.' });
  const sel = $('manualCurrency');
  if (!sel.options.length) {
    for (const c of Core.SUPPORTED) sel.add(new Option(c, c));
    sel.value = host.endsWith('.in') ? 'INR' : 'USD';
  }
  $('manualBox').hidden = false;
  $('targetCur').textContent = currencySymbol(sel.value);
  $('trackBtn').textContent = 'Track Price';
  $('trackBtn').disabled = state.offline;
  $('historyBtn').disabled = true;
  $('removeBtn').hidden = true;
  $('sparkline').replaceChildren();
  $('trendNote').hidden = true;
  showSection('product');
}

async function renderTracked() {
  const p = state.tracked;
  const tag = p.status === 'PAUSED' ? ['Paused', 'muted']
    : p.targetReached ? ['Target reached', 'down']
    : p.extractionStatus === 'FAILED' ? ['Server check failing', 'warn'] : ['Tracking', ''];
  const meta = p.extractionStatus === 'FAILED'
    ? `Last server check failed: ${p.lastError || 'price not readable'}. Open this page to update it.`
    : `Last checked ${timeAgo(p.lastCheckedAt)}`;
  renderCard({ name: p.name, domain: p.domain, priceText: money(p.currentPrice, p.currency), tag: tag[0], tagClass: tag[1],
    change: describeChange(p), meta });
  $('manualBox').hidden = true;
  $('targetCur').textContent = currencySymbol(p.currency);
  $('target').value = p.targetPrice ?? '';
  $('trackBtn').textContent = 'Update Target';
  $('trackBtn').disabled = false;
  $('historyBtn').disabled = false;
  $('historyBtn').title = '';
  $('removeBtn').hidden = false;
  $('removeBtn').textContent = 'Remove Tracking';
  updateTargetHint();
  showSection('product');

  try {
    const h = await api.history(p.id, 40);
    const series = renderLineChart($('sparkline'), h.points, { currency: p.currency, compact: true, formatMoney: money });
    const note = $('trendNote');
    note.hidden = series.data.length < 2;
    if (!note.hidden) note.textContent = `Lowest ${money(series.min, p.currency)} · Highest ${money(series.max, p.currency)}`;
  } catch { /* history is optional in the popup; errors surface in the dashboard */ }
}

function currentPriceNumber() {
  if (state.tracked) return Number(state.tracked.currentPrice);
  if (state.detection && state.detection.found) return Number(state.detection.price);
  return NaN;
}

function updateTargetHint() {
  const hint = $('targetHint');
  const v = Number($('target').value);
  const cur = currentPriceNumber();
  hint.textContent = Number.isFinite(v) && v > 0 && Number.isFinite(cur) && v >= cur
    ? 'Set a target below the current price to get an alert.'
    : 'You will be notified when the price reaches or drops below this.';
}

// ---------- actions ----------
function activeCurrency() {
  if (state.tracked) return state.tracked.currency;
  return state.manual ? $('manualCurrency').value : state.detection.currency;
}

async function onTrackOrUpdate() {
  const currency = activeCurrency();
  const target = Core.parseUserPrice($('target').value, currency);
  fieldError('target', 'targetError', target.ok ? '' : target.error);
  if (!target.ok) { $('target').focus(); return; }
  const btn = $('trackBtn');

  if (state.tracked) {
    setLoading(btn, true);
    try {
      state.tracked = await api.update(state.tracked.id, { targetPrice: Number(target.value) });
      setBanner('Target price updated.', 'ok');
      await renderTracked();
    } catch (e) { handleApiError(e); } finally { setLoading(btn, false); }
    return;
  }

  let price = state.detection.price;
  if (state.manual) {
    const p = Core.parseUserPrice($('manualPrice').value, currency);
    $('manualError').textContent = p.ok ? '' : p.error;
    $('manualPrice').setAttribute('aria-invalid', p.ok ? 'false' : 'true');
    if (!p.ok) { $('manualPrice').focus(); return; }
    price = p.value;
  } else {
    const check = Core.validateDetected(price, currency);
    if (!check.ok) { showMessage('Unable to detect a valid product price.', '', { manual: true }); return; }
  }

  setLoading(btn, true);
  try {
    state.tracked = await api.track({
      url: state.tab.url, name: state.detection.name || state.tab.title || state.detection.domain,
      price: Number(price), currency, targetPrice: Number(target.value)
    });
    setBanner('Tracking started. PricePulse will keep checking in the background.', 'ok');
    await renderTracked();
  } catch (e) {
    if (e instanceof ApiError && e.code === 'DUPLICATE_TRACKING') {
      state.tracked = await api.lookup(state.tab.url).catch(() => null);
      if (state.tracked) { setBanner('You are already tracking this product.'); await renderTracked(); return; }
    }
    handleApiError(e);
  } finally { setLoading(btn, false); }
}

function handleApiError(e) {
  if (e instanceof ApiError && e.offline) { setBanner('PricePulse server is currently unavailable.', 'error'); return; }
  if (e instanceof ApiError && e.fieldErrors && e.fieldErrors.targetPrice) { fieldError('target', 'targetError', e.fieldErrors.targetPrice); return; }
  setBanner(e instanceof ApiError ? e.message : 'Something went wrong. Try again.', 'error');
}

async function onRemove() {
  const btn = $('removeBtn');
  if (!btn.dataset.confirm) {
    btn.dataset.confirm = '1';
    btn.textContent = 'Confirm removal';
    clearTimeout(state.confirmTimer);
    state.confirmTimer = setTimeout(() => { delete btn.dataset.confirm; btn.textContent = 'Remove Tracking'; }, 4000);
    return;
  }
  clearTimeout(state.confirmTimer);
  delete btn.dataset.confirm;
  setLoading(btn, true);
  try {
    await api.remove(state.tracked.id);
    state.tracked = null;
    setBanner('Tracking removed.', 'ok');
    if (state.detection && state.detection.found) renderNew(); else showMessage('No longer tracking this product.', '', { manual: true });
  } catch (e) { handleApiError(e); } finally { setLoading(btn, false); btn.textContent = 'Remove Tracking'; }
}

function onHistory() {
  if (!state.tracked) return;
  chrome.tabs.create({ url: chrome.runtime.getURL(`options/options.html#history=${encodeURIComponent(state.tracked.id)}`) });
  window.close();
}

// ---------- startup ----------
async function load() {
  showSection('loading');
  setBanner('');
  Object.assign(state, { detection: null, tracked: null, manual: false, offline: false });

  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  const url = Core.validateUrl(tab && tab.url);
  if (!tab || !url.ok) {
    showMessage('Open a product page to start tracking.', 'PricePulse works on regular http and https pages.');
    $('retryBtn').hidden = true;
    return;
  }
  $('retryBtn').hidden = false;
  state.tab = { id: tab.id, url: url.value, title: (tab.title || '').slice(0, 300) };
  state.detection = await detect(tab.id);

  try {
    state.tracked = await api.lookup(state.tab.url);
  } catch (e) {
    if (e instanceof ApiError && e.offline) { state.offline = true; setBanner('PricePulse server is currently unavailable.', 'error'); }
    else setBanner(e.message, 'error');
  }

  if (state.tracked) {
    // Report what this browser sees; it works even when the server cannot read the page.
    const d = state.detection;
    if (d.found && d.currency === state.tracked.currency && Number(d.price) !== Number(state.tracked.currentPrice)) {
      try { state.tracked = await api.observe(state.tracked.id, Number(d.price), d.currency); } catch { /* keep last known state */ }
    }
    await renderTracked();
  } else if (state.detection.found) {
    renderNew();
  } else {
    showMessage(state.detection.reason || 'Unable to detect a valid product price.',
      'PricePulse never guesses a price. You can enter the price shown on the page instead.', { manual: true });
  }
}

$('trackBtn').addEventListener('click', onTrackOrUpdate);
$('historyBtn').addEventListener('click', onHistory);
$('removeBtn').addEventListener('click', onRemove);
$('retryBtn').addEventListener('click', load);
$('manualBtn').addEventListener('click', renderManual);
$('openOptions').addEventListener('click', () => chrome.runtime.openOptionsPage());
$('target').addEventListener('input', () => { fieldError('target', 'targetError', ''); updateTargetHint(); });
$('target').addEventListener('keydown', (e) => { if (e.key === 'Enter') onTrackOrUpdate(); });
$('manualPrice').addEventListener('input', () => {
  $('manualError').textContent = '';
  $('manualPrice').setAttribute('aria-invalid', 'false');
  const p = Core.parseUserPrice($('manualPrice').value, $('manualCurrency').value);
  $('price').textContent = p.ok ? money(p.value, $('manualCurrency').value) : '—';
});
$('manualCurrency').addEventListener('change', () => { $('targetCur').textContent = currencySymbol($('manualCurrency').value); $('manualPrice').dispatchEvent(new Event('input')); });

load();
