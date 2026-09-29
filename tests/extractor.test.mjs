import test from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { read } from './helpers.mjs';

function extract(html, url = 'https://shop.example.com/item/1') {
  const dom = new JSDOM(html, { url, runScripts: 'outside-only' });
  dom.window.eval(read('shared/pricecore.js'));
  dom.window.eval(read('content/extractor.js'));
  return dom.window.PricePulseExtractor.run();
}

test('level 1: JSON-LD product with offer', () => {
  const r = extract(`<html><head><title>x</title><script type="application/ld+json">
    {"@context":"https://schema.org","@type":"Product","name":"Trail Shoe",
     "offers":{"@type":"Offer","price":"3199.00","priceCurrency":"INR"}}</script></head><body></body></html>`);
  assert.equal(r.found, true);
  assert.equal(r.name, 'Trail Shoe');
  assert.equal(r.price, '3199');
  assert.equal(r.currency, 'INR');
  assert.equal(r.method, 'json-ld');
});

test('level 1: JSON-LD inside @graph with offers array and numeric price', () => {
  const r = extract(`<script type="application/ld+json">{"@graph":[{"@type":"WebPage"},
    {"@type":["Product"],"name":"Kettle","offers":[{"@type":"Offer","price":49.99,"priceCurrency":"EUR"}]}]}</script>`);
  assert.equal(r.found, true);
  assert.equal(r.price, '49.99');
  assert.equal(r.currency, 'EUR');
});

test('malformed JSON-LD is skipped, meta tags used', () => {
  const r = extract(`<head><script type="application/ld+json">{ not json</script>
    <meta property="og:title" content="Desk Lamp"><meta property="product:price:amount" content="29.99">
    <meta property="product:price:currency" content="USD"></head>`);
  assert.equal(r.found, true);
  assert.equal(r.method, 'meta');
  assert.equal(r.price, '29.99');
});

test('level 2: DOM heuristics ignore struck-through and hidden prices', () => {
  const r = extract(`<body><h1>Wireless Mouse</h1>
    <span class="price-old">₹1,999</span><del>₹1,799</del>
    <span style="display:none" class="price">₹1</span>
    <span class="price current">₹1,299.00</span></body>`);
  assert.equal(r.found, true);
  assert.equal(r.name, 'Wireless Mouse');
  assert.equal(r.price, '1299');
  assert.equal(r.currency, 'INR');
  assert.equal(r.confidence, 'medium');
});

test('level 3: Amazon adapter', () => {
  const r = extract(`<span id="productTitle"> Echo Dot </span>
    <span class="a-price"><span class="a-offscreen">₹4,499.00</span></span>`, 'https://www.amazon.in/dp/B0ABCDEFGH');
  assert.equal(r.found, true);
  assert.equal(r.method, 'adapter');
  assert.equal(r.name, 'Echo Dot');
  assert.equal(r.price, '4499');
});

test('never fabricates: page without a price', () => {
  const r = extract('<body><h1>About us</h1><p>We sell things. Founded in 1999.</p></body>');
  assert.equal(r.found, false);
  assert.equal(r.reason, 'Unable to detect a valid product price.');
  assert.equal(r.price, undefined);
});

test('never fabricates: price without a currency', () => {
  const r = extract('<body><h1>Thing</h1><span class="price">1299</span></body>');
  assert.equal(r.found, false);
});

test('rejects invalid structured price', () => {
  const r = extract(`<script type="application/ld+json">{"@type":"Product","name":"Free","offers":{"price":"0","priceCurrency":"USD"}}</script>`);
  assert.equal(r.found, false);
});
