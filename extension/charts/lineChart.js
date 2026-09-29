/* Price-history line chart (plain SVG, no library). Only real observations are ever plotted. */

export const EMPTY_CHART_TEXT = 'Price history will appear after more price checks.';
export const EMPTY_TREND_TEXT = 'More price checks are required to display the trend.';

function niceTicks(min, max, count) {
  const span = max - min;
  const rough = span / Math.max(1, count);
  const pow = 10 ** Math.floor(Math.log10(rough));
  const step = [1, 2, 2.5, 5, 10].map((m) => m * pow).find((s) => s >= rough) || rough;
  const ticks = [];
  for (let v = Math.ceil(min / step) * step; v <= max + step * 1e-9; v += step) ticks.push(Number(v.toFixed(6)));
  return ticks;
}

/** Pure data step (unit-tested). points: [{observedAt|t, price}] */
export function buildSeries(points) {
  const data = points
    .map((p) => ({ t: typeof p.t === 'number' ? p.t : Date.parse(p.observedAt), v: Number(p.price), source: p.source }))
    .filter((p) => Number.isFinite(p.t) && Number.isFinite(p.v) && p.v > 0)
    .sort((a, b) => a.t - b.t);
  if (data.length === 0) return { data, min: null, max: null, yMin: null, yMax: null, ticks: [], trend: 'NONE' };
  const min = Math.min(...data.map((d) => d.v));
  const max = Math.max(...data.map((d) => d.v));
  const pad = max === min ? Math.max(max * 0.02, 1) : (max - min) * 0.12;
  const yMin = Math.max(0, min - pad);
  const yMax = max + pad;
  const last = data[data.length - 1].v;
  const prev = data.length > 1 ? data[data.length - 2].v : null;
  let trend = 'NONE';
  if (prev !== null) trend = last < prev ? 'DECREASING' : last > prev ? 'INCREASING' : 'STABLE';
  return { data, min, max, yMin, yMax, ticks: niceTicks(yMin, yMax, 4), trend };
}

const NS = 'http://www.w3.org/2000/svg';
const svg = (tag, attrs = {}, text) => {
  const el = document.createElementNS(NS, tag);
  for (const [k, v] of Object.entries(attrs)) el.setAttribute(k, v);
  if (text != null) el.textContent = text;
  return el;
};

/**
 * Renders into `container`. Options: currency, compact (popup sparkline size), formatMoney(value, currency).
 * Y axis is not forced to start at zero so small real changes stay visible; ticks are labelled.
 */
export function renderLineChart(container, points, { currency = 'USD', compact = false, formatMoney = (v) => String(v) } = {}) {
  container.replaceChildren();
  const s = buildSeries(points);
  if (s.data.length < 2) {
    const p = document.createElement('p');
    p.className = 'chart-empty';
    p.textContent = EMPTY_CHART_TEXT;
    container.append(p);
    return s;
  }
  const W = compact ? 320 : 640, H = compact ? 120 : 260;
  const m = compact ? { l: 8, r: 8, t: 10, b: 10 } : { l: 64, r: 16, t: 14, b: 30 };
  const iw = W - m.l - m.r, ih = H - m.t - m.b;
  const t0 = s.data[0].t, t1 = s.data[s.data.length - 1].t;
  const x = (d, i) => m.l + (t1 === t0 ? (i / (s.data.length - 1)) * iw : ((d.t - t0) / (t1 - t0)) * iw);
  const y = (v) => m.t + ih - ((v - s.yMin) / (s.yMax - s.yMin)) * ih;

  const root = svg('svg', { viewBox: `0 0 ${W} ${H}`, class: 'chart', role: 'img',
    'aria-label': `Price history from ${formatMoney(s.data[0].v, currency)} to ${formatMoney(s.data[s.data.length - 1].v, currency)}` });

  if (!compact) {
    for (const tv of s.ticks) {
      root.append(svg('line', { x1: m.l, x2: W - m.r, y1: y(tv), y2: y(tv), class: 'chart-grid' }));
      root.append(svg('text', { x: m.l - 8, y: y(tv) + 4, class: 'chart-label', 'text-anchor': 'end' }, formatMoney(tv, currency)));
    }
    const xt = 4;
    for (let i = 0; i <= xt; i++) {
      const tt = t0 + ((t1 - t0) * i) / xt;
      const label = new Date(tt).toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
      root.append(svg('text', { x: m.l + (iw * i) / xt, y: H - 8, class: 'chart-label', 'text-anchor': i === 0 ? 'start' : i === xt ? 'end' : 'middle' }, label));
    }
  }

  const path = s.data.map((d, i) => `${i ? 'L' : 'M'}${x(d, i).toFixed(1)},${y(d.v).toFixed(1)}`).join(' ');
  root.append(svg('path', { d: path, class: 'chart-line', fill: 'none' }));

  s.data.forEach((d, i) => {
    const isLast = i === s.data.length - 1;
    const cls = isLast ? 'chart-dot chart-dot-current' : d.v === s.min ? 'chart-dot chart-dot-low' : d.v === s.max ? 'chart-dot chart-dot-high' : 'chart-dot';
    if (compact && !isLast && cls === 'chart-dot') return;
    const dot = svg('circle', { cx: x(d, i), cy: y(d.v), r: isLast ? 4.5 : 3.5, class: cls });
    dot.append(svg('title', {}, `${formatMoney(d.v, currency)} · ${new Date(d.t).toLocaleString()}`));
    root.append(dot);
  });

  container.append(root);
  return s;
}
