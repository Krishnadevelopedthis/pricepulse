import test from 'node:test';
import assert from 'node:assert/strict';
import { buildSeries } from '../extension/charts/lineChart.js';

const pt = (d, price) => ({ observedAt: `2026-09-${String(d).padStart(2, '0')}T10:00:00Z`, price });

test('series statistics come only from supplied data', () => {
  const s = buildSeries([pt(3, '3199'), pt(1, '3499'), pt(2, '3499')]);
  assert.deepEqual(s.data.map((d) => d.v), [3499, 3499, 3199]); // sorted by time
  assert.equal(s.min, 3199);
  assert.equal(s.max, 3499);
  assert.equal(s.trend, 'DECREASING');
  assert.ok(s.yMin < 3199 && s.yMax > 3499);
});

test('increasing / stable trends', () => {
  assert.equal(buildSeries([pt(1, '10'), pt(2, '12')]).trend, 'INCREASING');
  assert.equal(buildSeries([pt(1, '10'), pt(2, '10')]).trend, 'STABLE');
});

test('flat series still gets a non-degenerate axis', () => {
  const s = buildSeries([pt(1, '100'), pt(2, '100')]);
  assert.ok(s.yMax > s.yMin);
});

test('invalid points are dropped, never invented', () => {
  const s = buildSeries([pt(1, 'abc'), pt(2, '-5'), pt(3, '0'), { observedAt: 'nope', price: '5' }]);
  assert.equal(s.data.length, 0);
  assert.equal(s.trend, 'NONE');
});
