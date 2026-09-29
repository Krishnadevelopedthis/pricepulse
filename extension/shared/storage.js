export const DEFAULTS = Object.freeze({
  backendUrl: 'http://localhost:8080',
  notifications: true,
  notifyDrops: true,
  notifyIncreases: false,
  syncMinutes: 15
});

export async function getSettings() {
  const { settings } = await chrome.storage.local.get('settings');
  return { ...DEFAULTS, ...(settings || {}) };
}

export async function saveSettings(patch) {
  const next = { ...(await getSettings()), ...patch };
  await chrome.storage.local.set({ settings: next });
  return next;
}

/** Random per-install id. Guarded by a Web Lock so the popup and service worker never race. */
export async function getClientId() {
  return navigator.locks.request('pricepulse-client-id', async () => {
    const { clientId } = await chrome.storage.local.get('clientId');
    if (clientId) return clientId;
    const created = crypto.randomUUID();
    await chrome.storage.local.set({ clientId: created });
    return created;
  });
}

export async function getSyncStatus() {
  const { syncStatus } = await chrome.storage.local.get('syncStatus');
  return syncStatus || null;
}

export async function setSyncStatus(status) {
  await chrome.storage.local.set({ syncStatus: { ...status, at: new Date().toISOString() } });
}
