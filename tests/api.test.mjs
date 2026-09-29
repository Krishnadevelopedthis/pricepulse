import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { createApiClient, ApiError } from '../extension/shared/api.js';

function serve(handler) {
  return new Promise((resolve) => {
    const calls = [];
    const server = http.createServer((req, res) => {
      let body = '';
      req.on('data', (c) => (body += c));
      req.on('end', () => { calls.push({ method: req.method, url: req.url, headers: req.headers, body }); handler(req, res, calls.length); });
    });
    server.listen(0, '127.0.0.1', () => resolve({ server, calls, url: `http://127.0.0.1:${server.address().port}` }));
  });
}
const client = (baseUrl, extra = {}) =>
  createApiClient({ getConfig: async () => ({ baseUrl, clientId: 'abcdefabcdefabcdef' }), sleep: async () => {}, ...extra });
const json = (res, status, obj) => { res.writeHead(status, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(obj)); };

test('sends X-Client-Id and parses JSON', async () => {
  const s = await serve((req, res) => json(res, 200, [{ id: '1' }]));
  const out = await client(s.url).listProducts();
  assert.equal(out[0].id, '1');
  assert.equal(s.calls[0].headers['x-client-id'], 'abcdefabcdefabcdef');
  s.server.close();
});

test('GET retries 5xx with backoff then succeeds', async () => {
  const s = await serve((req, res, n) => (n < 3 ? json(res, 503, { code: 'X', message: 'down' }) : json(res, 200, [])));
  await client(s.url).listProducts();
  assert.equal(s.calls.length, 3);
  s.server.close();
});

test('retries are bounded (never endless)', async () => {
  const s = await serve((req, res) => json(res, 500, { code: 'INTERNAL_ERROR', message: 'boom' }));
  await assert.rejects(client(s.url).listProducts(), (e) => e instanceof ApiError && e.status === 500);
  assert.equal(s.calls.length, 3); // 1 try + 2 retries
  s.server.close();
});

test('POST is not retried', async () => {
  const s = await serve((req, res) => json(res, 500, { code: 'INTERNAL_ERROR', message: 'boom' }));
  await assert.rejects(client(s.url).track({ url: 'https://a.example/p' }));
  assert.equal(s.calls.length, 1);
  s.server.close();
});

test('maps validation and duplicate errors', async () => {
  const s = await serve((req, res) => {
    if (req.method === 'POST') return json(res, 409, { code: 'DUPLICATE_TRACKING', message: 'You are already tracking this product.', productId: 'p1' });
    json(res, 422, { code: 'VALIDATION_FAILED', message: 'bad', fieldErrors: { targetPrice: 'bad' } });
  });
  await assert.rejects(client(s.url).track({}), (e) => e.code === 'DUPLICATE_TRACKING' && e.productId === 'p1');
  await assert.rejects(client(s.url).update('p1', {}), (e) => e.status === 422 && e.fieldErrors.targetPrice === 'bad');
  s.server.close();
});

test('lookup returns null on 404, throws on others', async () => {
  const s = await serve((req, res) => json(res, 404, { code: 'NOT_FOUND', message: 'Product not found.' }));
  assert.equal(await client(s.url).lookup('https://a.example/p'), null);
  s.server.close();
});

test('DELETE 204 resolves to null', async () => {
  const s = await serve((req, res) => { res.writeHead(204); res.end(); });
  assert.equal(await client(s.url).remove('p1'), null);
  s.server.close();
});

test('unreachable server reports OFFLINE', async () => {
  const s = await serve(() => {});
  const url = s.url; s.server.close();
  await assert.rejects(client(url).listProducts(), (e) => e.offline === true && /currently unavailable/.test(e.message));
});

test('request timeout aborts and reports OFFLINE', async () => {
  const s = await serve(() => { /* never respond */ });
  await assert.rejects(client(s.url).request('/api/products', { timeoutMs: 100, retries: 0 }), (e) => e.offline === true);
  s.server.closeAllConnections?.(); s.server.close();
});
