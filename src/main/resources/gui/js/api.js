// Session token: passed once in the URL fragment (never sent to the server), kept per browser tab.
export const token = (() => {
  const m = location.hash.match(/t=([0-9a-f]+)/);
  if (m) {
    sessionStorage.setItem('ursc-token', m[1]);
    history.replaceState(null, '', location.pathname);
  }
  return sessionStorage.getItem('ursc-token') || '';
})();

export async function api(path, { method = 'GET', body } = {}) {
  const r = await fetch('/api/' + path, {
    method,
    headers: { 'X-URSC-Token': token, 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await r.text();
  let data;
  try { data = text ? JSON.parse(text) : {}; } catch { data = { raw: text }; }
  if (!r.ok) {
    const e = new Error(data.error || (data.errors && data.errors.join('; ')) || r.statusText);
    e.status = r.status;
    e.data = data;
    throw e;
  }
  return data;
}

/** URL for links/downloads (token as query parameter). */
export const apiUrl = (p) => '/api/' + p + (p.includes('?') ? '&' : '?') + 't=' + token;

export function events(handler) {
  let es;
  const open = () => {
    es = new EventSource(apiUrl('events'));
    es.onmessage = (e) => { try { handler(JSON.parse(e.data)); } catch (err) { console.warn(err); } };
    es.onerror = () => { es.close(); setTimeout(open, 3000); };
  };
  open();
}
