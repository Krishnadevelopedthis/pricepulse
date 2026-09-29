/* Presentation helpers shared by popup and options (ES module; relies on PricePulseCore global for money). */
const money = (v, c) => globalThis.PricePulseCore.formatMoney(v, c);

export { money };

/** "₹300 decrease (8.57%)", "Price increased by ₹500 (16.67%)" or "No change since the last check". */
export function describeChange(p) {
  const amount = Number(p.changeAmount);
  if (p.direction === 'DECREASED' && amount) {
    return { kind: 'down', text: `${money(Math.abs(amount), p.currency)} decrease (${Math.abs(Number(p.changePercent)).toFixed(2)}%)` };
  }
  if (p.direction === 'INCREASED' && amount) {
    return { kind: 'up', text: `Price increased by ${money(Math.abs(amount), p.currency)} (${Math.abs(Number(p.changePercent)).toFixed(2)}%)` };
  }
  return { kind: 'flat', text: 'No change since the last check' };
}

export function timeAgo(iso, now = Date.now()) {
  if (!iso) return 'never';
  const s = Math.max(0, Math.round((now - Date.parse(iso)) / 1000));
  if (s < 60) return 'just now';
  const m = Math.round(s / 60);
  if (m < 60) return `${m} min ago`;
  const h = Math.round(m / 60);
  if (h < 48) return `${h} h ago`;
  return `${Math.round(h / 24)} days ago`;
}

/** Tiny DOM builder. Text is always set via textContent, so page-supplied names cannot inject markup. */
export function h(tag, attrs = {}, ...children) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v === false || v == null) continue;
    if (k === 'class') el.className = v;
    else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2), v);
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const c of children.flat()) if (c != null && c !== false) el.append(c.nodeType ? c : document.createTextNode(String(c)));
  return el;
}
