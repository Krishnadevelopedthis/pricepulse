import { api, ApiError } from '../shared/api.js';
import { describeChange, h, money, timeAgo } from '../shared/format.js';
import { renderLineChart, EMPTY_TREND_TEXT } from '../charts/lineChart.js';
import { getSettings, saveSettings, getSyncStatus, getClientId, DEFAULTS } from '../shared/storage.js';

const Core = globalThis.PricePulseCore;
const $ = (id) => document.getElementById(id);
const VIEWS = ['products', 'history', 'settings'];
let products = [];

const SOURCE_LABEL = { BROWSER_INITIAL: 'Browser (start)', BROWSER: 'Browser', SERVER: 'Server check' };

function banner(text, kind = '') {
  const b = $('pageBanner');
  b.hidden = !text;
  b.className = `banner ${kind}`.trim();
  b.textContent = text || '';
}

function errorText(e) {
  return e instanceof ApiError ? e.message : 'Something went wrong. Try again.';
}

// ---------- routing ----------
function parseHash() {
  const [view, rest] = location.hash.replace(/^#/, '').split(/[=]/);
  return { view: VIEWS.includes(view) ? view : 'products', id: view === 'history' ? rest : null };
}

function select(view) {
  for (const v of VIEWS) {
    $(`view-${v}`).hidden = v !== view;
    $(`tab-${v}`).setAttribute('aria-selected', String(v === view));
    $(`tab-${v}`).tabIndex = v === view ? 0 : -1;
  }
}

async function route() {
  const { view, id } = parseHash();
  select(view);
  if (view === 'products') await loadProducts();
  if (view === 'history') await loadHistoryView(id);
  if (view === 'settings') await loadSettings();
}

for (const tab of document.querySelectorAll('.tab')) {
  tab.addEventListener('click', () => { location.hash = tab.dataset.view; });
  tab.addEventListener('keydown', (e) => {
    const i = VIEWS.indexOf(tab.dataset.view);
    const next = e.key === 'ArrowRight' ? VIEWS[(i + 1) % 3] : e.key === 'ArrowLeft' ? VIEWS[(i + 2) % 3] : null;
    if (next) { location.hash = next; $(`tab-${next}`).focus(); }
  });
}
window.addEventListener('hashchange', route);

// ---------- server status ----------
async function pingServer() {
  const tag = $('serverStatus');
  try { await api.health(); tag.textContent = 'Server connected'; tag.className = 'tag down'; return true; }
  catch { tag.textContent = 'Server unavailable'; tag.className = 'tag warn'; return false; }
}

// ---------- products ----------
async function loadProducts() {
  banner('');
  try {
    products = await api.listProducts();
  } catch (e) {
    banner(e instanceof ApiError && e.offline ? 'PricePulse server is currently unavailable.' : errorText(e), 'error');
    products = [];
  }
  $('productsEmpty').hidden = products.length > 0;
  $('productList').replaceChildren(...products.map(productCard));
}

function productCard(p) {
  const change = describeChange(p);
  const statusTag = p.status === 'PAUSED' ? h('span', { class: 'tag muted' }, 'Paused')
    : p.targetReached ? h('span', { class: 'tag down' }, 'Target reached')
    : p.extractionStatus === 'FAILED' ? h('span', { class: 'tag warn', title: p.lastError || '' }, 'Server check failing')
    : h('span', { class: 'tag' }, 'Tracking');

  const card = h('article', { class: 'card product' },
    h('div', {},
      h('h3', {}, h('a', { href: p.url, target: '_blank', rel: 'noopener noreferrer' }, p.name)),
      h('p', { class: 'sub' }, p.domain)),
    h('div', { class: 'price-col' },
      h('div', { class: 'price' }, money(p.currentPrice, p.currency)),
      h('div', { class: `change ${change.kind}` }, change.text)),
    h('div', { class: 'facts' }, statusTag,
      p.targetPrice ? h('span', { class: 'tag muted' }, `Target ${money(p.targetPrice, p.currency)}`) : null,
      h('span', { class: 'sub' }, `Last checked ${timeAgo(p.lastCheckedAt)}`)),
    h('div', { class: 'buttons' }));

  const buttons = card.querySelector('.buttons');
  const wrap = (label, cls, fn) => {
    const b = h('button', { class: `btn small ${cls}`, type: 'button' }, label);
    b.addEventListener('click', async () => {
      b.classList.add('loading'); b.disabled = true;
      try { await fn(b); } finally { b.classList.remove('loading'); b.disabled = false; }
    });
    return b;
  };
  buttons.append(
    h('button', { class: 'btn small secondary', type: 'button', onclick: () => { location.hash = `history=${p.id}`; } }, 'View History'),
    wrap('Check now', 'secondary', async () => {
      try { await api.checkNow(p.id); banner('Price updated from the server check.', 'ok'); }
      catch (e) { banner(e.code === 'EXTRACTION_FAILED' ? `Server could not read a price: ${e.message} Open the product page and the extension will record its price.` : errorText(e), 'error'); }
      await loadProducts();
    }),
    wrap(p.status === 'PAUSED' ? 'Resume' : 'Pause', 'secondary', async () => {
      try { await api.update(p.id, { status: p.status === 'PAUSED' ? 'ACTIVE' : 'PAUSED' }); } catch (e) { banner(errorText(e), 'error'); }
      await loadProducts();
    }),
    wrap('Remove Tracking', 'danger', async (b) => {
      if (!confirm(`Stop tracking "${p.name}" and delete its price history?`)) return;
      try { await api.remove(p.id); banner('Tracking removed.', 'ok'); } catch (e) { banner(errorText(e), 'error'); }
      await loadProducts();
    }));
  return card;
}

// ---------- history ----------
async function loadHistoryView(id) {
  banner('');
  try { products = await api.listProducts(); }
  catch (e) { banner(e instanceof ApiError && e.offline ? 'PricePulse server is currently unavailable.' : errorText(e), 'error'); products = []; }
  const sel = $('historyProduct');
  sel.replaceChildren(...products.map((p) => h('option', { value: p.id }, p.name.length > 70 ? `${p.name.slice(0, 67)}…` : p.name)));
  $('historyEmpty').hidden = products.length > 0;
  $('historyBody').hidden = products.length === 0;
  $('historyProduct').closest('.history-head').hidden = products.length === 0;
  if (!products.length) return;
  sel.value = products.some((p) => p.id === id) ? id : products[0].id;
  await renderHistory(sel.value);
}

async function renderHistory(id) {
  const p = products.find((x) => x.id === id);
  if (!p) return;
  const chart = $('historyChart');
  chart.replaceChildren(h('p', { class: 'chart-empty' }, 'Loading price history…'));
  try {
    const hist = await api.history(id, 500);
    const series = renderLineChart(chart, hist.points, { currency: p.currency, formatMoney: money });
    const s = hist.stats;
    $('historyTrend').textContent = series.data.length < 2 ? EMPTY_TREND_TEXT
      : series.trend === 'DECREASING' ? 'Trend: the latest check is lower than the one before.'
      : series.trend === 'INCREASING' ? 'Trend: the latest check is higher than the one before.'
      : `Trend: stable since ${new Date(s.stableSince).toLocaleDateString()}.`;
    const change = describeChange(p);
    const stat = (label, value, sub) => h('div', { class: 'stat' }, h('div', { class: 'label' }, label), h('div', { class: 'value' }, value), sub ? h('div', { class: 'sub' }, sub) : null);
    $('statGrid').replaceChildren(
      stat('Current', s.current != null ? money(s.current, p.currency) : '—', change.text),
      stat('Previous', s.previous != null ? money(s.previous, p.currency) : '—', 'Previous recorded price'),
      stat('Lowest', s.lowest != null ? money(s.lowest, p.currency) : '—'),
      stat('Highest', s.highest != null ? money(s.highest, p.currency) : '—'),
      stat('Checks recorded', String(s.count)),
      stat('Target', p.targetPrice ? money(p.targetPrice, p.currency) : 'Not set'));
    const body = $('historyTable').tBodies[0];
    body.replaceChildren(...[...hist.points].reverse().slice(0, 50).map((pt) =>
      h('tr', {}, h('td', {}, new Date(pt.observedAt).toLocaleString()), h('td', { class: 'num' }, money(pt.price, p.currency)), h('td', {}, SOURCE_LABEL[pt.source] || pt.source))));
  } catch (e) {
    chart.replaceChildren(h('p', { class: 'chart-empty' }, e instanceof ApiError && e.offline ? 'PricePulse server is currently unavailable.' : errorText(e)));
  }
}
$('historyProduct').addEventListener('change', (e) => { history.replaceState(null, '', `#history=${e.target.value}`); renderHistory(e.target.value); });

// ---------- settings ----------
async function loadSettings() {
  banner('');
  const s = await getSettings();
  $('setNotifications').checked = s.notifications;
  $('setDrops').checked = s.notifyDrops;
  $('setIncreases').checked = s.notifyIncreases;
  $('setSync').value = String(s.syncMinutes);
  $('setBackend').value = s.backendUrl;
  $('backendError').textContent = '';
  // Store builds ship without optional host permissions, so the server address is fixed and the field is hidden.
  $('serverGroup').hidden = !chrome.runtime.getManifest().optional_host_permissions;
  $('installId').textContent = await getClientId();
  const st = await getSyncStatus();
  $('syncStatus').textContent = st ? (st.ok ? `Last sync ${timeAgo(st.at)}.` : `Last sync failed ${timeAgo(st.at)}: ${st.error}`) : 'No sync has run yet.';
}

function validateBackend(raw) {
  const v = Core.validateUrl(raw);
  if (!v.ok) return v;
  const u = new URL(v.value);
  if (u.search || (u.pathname !== '/' && u.pathname !== '')) return { ok: false, error: 'Enter only the server address, for example http://localhost:8080.' };
  return { ok: true, value: u.origin };
}

$('saveSettings').addEventListener('click', async () => {
  const customServer = !$('serverGroup').hidden;
  const backend = customServer ? validateBackend($('setBackend').value) : { ok: true, value: null };
  $('backendError').textContent = backend.ok ? '' : backend.error;
  $('setBackend').setAttribute('aria-invalid', String(!backend.ok));
  if (!backend.ok) return;
  const btn = $('saveSettings');
  btn.classList.add('loading'); btn.disabled = true;
  try {
    // Only the default local backend is pre-approved; any other server needs an explicit permission grant.
    if (customServer) {
      const granted = await chrome.permissions.contains({ origins: [`${backend.value}/*`] })
        || await chrome.permissions.request({ origins: [`${backend.value}/*`] });
      if (!granted) { $('backendError').textContent = 'Chrome needs permission to contact this server.'; return; }
    }
    await saveSettings({
      ...(customServer ? { backendUrl: backend.value } : {}), notifications: $('setNotifications').checked, notifyDrops: $('setDrops').checked,
      notifyIncreases: $('setIncreases').checked, syncMinutes: Number($('setSync').value) || DEFAULTS.syncMinutes
    });
    banner('Settings saved.', 'ok');
    pingServer();
  } finally { btn.classList.remove('loading'); btn.disabled = false; }
});

$('testNotif').addEventListener('click', async () => {
  const level = await chrome.notifications.getPermissionLevel();
  if (level !== 'granted') { banner('Chrome notifications are blocked for PricePulse. Allow them in your system or Chrome settings.', 'error'); return; }
  await chrome.notifications.create(`pp-test-${Date.now()}`, {
    type: 'basic', iconUrl: chrome.runtime.getURL('assets/icon128.png'), title: 'PricePulse test', message: 'Notifications are working.'
  });
});

document.addEventListener('DOMContentLoaded', () => {});
pingServer();
route();
