import { api, ApiError } from '../shared/api.js';
import { getClientId, getSettings, setSyncStatus } from '../shared/storage.js';

/*
 * MV3 service worker: no long-lived state. Everything is re-derived from chrome.storage and the
 * backend on each wake-up. While Chrome is running, the alarm wakes this worker to pick up
 * notifications the backend queued. While Chrome is closed only the backend scheduler runs.
 */
const ALARM = 'pricepulse-sync';
const ICON = chrome.runtime.getURL('assets/icon128.png');

async function ensureAlarm(force = false) {
  const { syncMinutes } = await getSettings();
  const existing = await chrome.alarms.get(ALARM);
  if (force || !existing || existing.periodInMinutes !== syncMinutes) {
    await chrome.alarms.create(ALARM, { delayInMinutes: 1, periodInMinutes: syncMinutes });
  }
}

function fmt(value, currency) {
  try { return new Intl.NumberFormat(currency === 'INR' ? 'en-IN' : undefined, { style: 'currency', currency, maximumFractionDigits: 2 }).format(Number(value)); }
  catch { return `${currency} ${value}`; }
}

function toNotification(n) {
  const price = fmt(n.price, n.currency);
  if (n.type === 'TARGET_REACHED') {
    return { title: 'Target price reached', message: `${n.name}\nNow ${price} (your target: ${fmt(n.targetPrice, n.currency)})` };
  }
  if (n.type === 'DROPPED') {
    return { title: 'Price dropped', message: `${n.name}\n${fmt(n.previousPrice, n.currency)} → ${price}` };
  }
  return { title: 'Price increased', message: `${n.name}\n${fmt(n.previousPrice, n.currency)} → ${price}` };
}

async function syncAndNotify() {
  const settings = await getSettings();
  try {
    const pending = await api.pending();
    const shown = [];
    for (const n of pending) {
      const allowed = settings.notifications &&
        (n.type === 'TARGET_REACHED' || (n.type === 'DROPPED' && settings.notifyDrops) || (n.type === 'INCREASED' && settings.notifyIncreases));
      if (allowed) {
        const { title, message } = toNotification(n);
        // Same id for the same event: Chrome replaces instead of stacking duplicates.
        await chrome.notifications.create(`pp|${n.productId}|${n.type}|${n.price}`, {
          type: 'basic', iconUrl: ICON, title, message, priority: n.type === 'TARGET_REACHED' ? 2 : 1
        });
        await chrome.storage.local.set({ [`url:${n.productId}`]: n.url });
      }
      shown.push(n.productId);
    }
    // Acknowledge everything handled (shown or filtered by settings) so it is never re-sent.
    if (shown.length) await api.ack(shown);
    await setSyncStatus({ ok: true, delivered: pending.length });
    return { ok: true, count: pending.length };
  } catch (e) {
    const offline = e instanceof ApiError && e.offline;
    await setSyncStatus({ ok: false, error: offline ? 'PricePulse server is currently unavailable.' : (e.message || 'Sync failed.') });
    return { ok: false, offline };
  }
}

chrome.runtime.onInstalled.addListener(async () => {
  await getClientId();
  await ensureAlarm(true);
});

chrome.runtime.onStartup.addListener(async () => {
  await ensureAlarm();       // alarms may be cleared on browser restart, so re-create if missing
  await syncAndNotify();     // resume immediately when Chrome starts
});

chrome.alarms.onAlarm.addListener((alarm) => { if (alarm.name === ALARM) syncAndNotify(); });

chrome.notifications.onClicked.addListener(async (id) => {
  const productId = id.split('|')[1];
  const key = `url:${productId}`;
  const stored = await chrome.storage.local.get(key);
  if (stored[key]) chrome.tabs.create({ url: stored[key] });
  chrome.notifications.clear(id);
});

chrome.storage.onChanged.addListener((changes, area) => {
  if (area === 'local' && changes.settings) ensureAlarm();
});

chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
  if (msg && msg.type === 'sync-now') {
    syncAndNotify().then(sendResponse);
    return true; // keep the channel open for the async response
  }
  return false;
});

// Runs every time the worker wakes (cheap): make sure the alarm exists.
ensureAlarm();
