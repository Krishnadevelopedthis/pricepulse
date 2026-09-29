import test from 'node:test';
import assert from 'node:assert/strict';
import { loadCore } from './helpers.mjs';

const Core = loadCore();

// Same table as backend CoreLogicSelfTest.
const AMOUNTS = [
  ['₹1,299', '1299'], ['₹1,299.00', '1299.00'], ['$29.99', '29.99'], ['€49,99', '49.99'],
  ['1.299,50 €', '1299.50'], ['₹1,29,999', '129999'], ['1 299,00', '1299.00'],
  ['Price: £39.99 incl. VAT', '39.99'], ['Out of stock', null]
];
for (const [input, expected] of AMOUNTS) {
  test(`parseAmount(${input})`, () => assert.equal(Core.parseAmount(input), expected));
}

test('machine decimals keep "." as decimal', () => {
  assert.equal(Core.parseMachineDecimal('1299.5'), '1299.5');
  assert.equal(Core.parseMachineDecimal('1.299'), '1.299');
});

test('currency detection', () => {
  assert.equal(Core.detectCurrency('₹1,299'), 'INR');
  assert.equal(Core.detectCurrency('Rs. 1,299'), 'INR');
  assert.equal(Core.detectCurrency('EUR 20'), 'EUR');
  assert.equal(Core.detectCurrency('1299'), null);
});

test('user price validation accepts valid values', () => {
  const r = Core.parseUserPrice(' 4999 ', 'INR');
  assert.equal(r.ok, true);
  assert.equal(r.value, '4999');
  assert.equal(Core.parseUserPrice('4999.50', 'INR').ok, true);
});

for (const bad of ['', 'NaN', 'Infinity', '-5', '1e5', 'abc', '12.345', '0', '1,299', null, undefined]) {
  test(`user price rejects ${JSON.stringify(bad)}`, () => assert.equal(Core.parseUserPrice(bad, 'INR').ok, false));
}
test('JPY rejects decimals', () => assert.equal(Core.parseUserPrice('10.5', 'JPY').ok, false));

test('detected price validation', () => {
  assert.equal(Core.validateDetected('1299.0000', 'INR').value, '1299');
  assert.equal(Core.validateDetected('12.3400', 'USD').value, '12.34');
  assert.equal(Core.validateDetected('0', 'USD').ok, false);
  assert.equal(Core.validateDetected('10', 'XXX').ok, false);
  assert.equal(Core.validateDetected(undefined, 'USD').ok, false);
});

test('URL validation', () => {
  assert.equal(Core.validateUrl('https://shop.example.com/p/1#reviews').value, 'https://shop.example.com/p/1');
  for (const bad of ['', 'not a url', 'ftp://example.com', 'javascript:alert(1)', 'https://user:pw@example.com/']) {
    assert.equal(Core.validateUrl(bad).ok, false, bad);
  }
});

test('formatMoney', () => {
  assert.match(Core.formatMoney('3199', 'INR'), /₹\s?3,199/);
  assert.match(Core.formatMoney('29.99', 'USD'), /\$29\.99/);
  assert.equal(Core.formatMoney('abc', 'USD'), '—');
});
