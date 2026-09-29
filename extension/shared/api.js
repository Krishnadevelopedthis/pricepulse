import { getClientId, getSettings } from './storage.js';

export class ApiError extends Error {
  constructor(status, code, message, extra = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = extra.fieldErrors || null;
    this.productId = extra.productId || null;
  }
  get offline() { return this.code === 'OFFLINE'; }
}

const backoff = (attempt) => 400 * 2 ** attempt;

async function toApiError(res) {
  try {
    const b = await res.json();
    return new ApiError(res.status, b.code || 'ERROR', b.message || 'Request failed.', { fieldErrors: b.fieldErrors, productId: b.productId });
  } catch {
    return new ApiError(res.status, 'ERROR', 'Request failed.');
  }
}

/**
 * Small API client: request timeout, exponential backoff for idempotent requests only
 * (GET/PATCH/DELETE), and never an endless retry loop. POST is never retried automatically.
 */
export function createApiClient({ getConfig, fetchImpl = (...a) => fetch(...a), sleep = (ms) => new Promise((r) => setTimeout(r, ms)) }) {
  async function request(path, { method = 'GET', body, timeoutMs = 8000, retries } = {}) {
    const { baseUrl, clientId } = await getConfig();
    const maxRetries = retries ?? (method === 'POST' ? 0 : 2);
    for (let attempt = 0; ; attempt++) {
      const ctrl = new AbortController();
      const timer = setTimeout(() => ctrl.abort(), timeoutMs);
      try {
        const res = await fetchImpl(baseUrl + path, {
          method,
          headers: { 'X-Client-Id': clientId, ...(body ? { 'Content-Type': 'application/json' } : {}) },
          body: body ? JSON.stringify(body) : undefined,
          signal: ctrl.signal
        });
        clearTimeout(timer);
        if (res.ok) return res.status === 204 ? null : await res.json();
        const err = await toApiError(res);
        if ((res.status >= 500 || res.status === 429) && attempt < maxRetries) { await sleep(backoff(attempt)); continue; }
        throw err;
      } catch (e) {
        clearTimeout(timer);
        if (e instanceof ApiError) throw e;
        if (attempt < maxRetries) { await sleep(backoff(attempt)); continue; }
        throw new ApiError(0, 'OFFLINE', 'PricePulse server is currently unavailable.');
      }
    }
  }

  return {
    request,
    health: () => request('/api/health', { retries: 0, timeoutMs: 4000 }),
    listProducts: () => request('/api/products'),
    getProduct: (id) => request(`/api/products/${encodeURIComponent(id)}`),
    /** Returns the tracked product for a page URL, or null when it is not tracked. */
    async lookup(url) {
      try { return await request(`/api/products/lookup?url=${encodeURIComponent(url)}`); }
      catch (e) { if (e instanceof ApiError && e.status === 404) return null; throw e; }
    },
    track: (payload) => request('/api/products', { method: 'POST', body: payload }),
    update: (id, patch) => request(`/api/products/${encodeURIComponent(id)}`, { method: 'PATCH', body: patch }),
    remove: (id) => request(`/api/products/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    history: (id, limit = 200) => request(`/api/products/${encodeURIComponent(id)}/history?limit=${limit}`),
    observe: (id, price, currency) => request(`/api/products/${encodeURIComponent(id)}/observations`, { method: 'POST', body: { price, currency } }),
    checkNow: (id) => request(`/api/products/${encodeURIComponent(id)}/check`, { method: 'POST', timeoutMs: 30000 }),
    pending: () => request('/api/notifications/pending'),
    ack: (productIds) => request('/api/notifications/ack', { method: 'POST', body: { productIds } })
  };
}

export const api = createApiClient({
  async getConfig() {
    const [settings, clientId] = await Promise.all([getSettings(), getClientId()]);
    return { baseUrl: settings.backendUrl.replace(/\/+$/, ''), clientId };
  }
});
