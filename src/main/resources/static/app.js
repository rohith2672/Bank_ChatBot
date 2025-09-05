// Renders backend reply + structured data (transactions, loans, balance, etc.)

document.addEventListener('DOMContentLoaded', () => {
  // ---- config ---------------------------------------------------------------
  const CHAT_URL = document.querySelector('meta[name="chat-endpoint"]')?.content?.trim() || '/chat';
  const epEl = document.getElementById('ep'); if (epEl) epEl.textContent = CHAT_URL;

  // persist API key across reloads
  const apiKeyInput = document.getElementById('apikey');
  try { apiKeyInput.value = localStorage.apiKey || ''; } catch {}
  apiKeyInput?.addEventListener('change', () => { localStorage.apiKey = apiKeyInput.value.trim(); });

  const newCid = () => 'cid-' + Math.random().toString(36).slice(2) + Date.now().toString(36);

  // ---- DOM ------------------------------------------------------------------
  const form = document.getElementById('chat-form');
  const input = document.getElementById('message');
  const sendBtn = document.getElementById('send');
  const log = document.getElementById('chat-log');

  const lastPanel = document.getElementById('last-response');
  const replyText = document.getElementById('reply-text');
  const dataView = document.getElementById('data-view');
  const rawJson = document.getElementById('raw-json');
  const latencyEl = document.getElementById('latency');

  // ---- helpers --------------------------------------------------------------
  const esc = (s) => String(s ?? '').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;');
  const money = (v) => (v===null || v===undefined || v==='') ? '' : `$${Number(v).toFixed(2)}`;
  const fmtDate = (s) => {
    if (!s) return '';
    // Accept ISO or yyyy-MM-dd
    const d = new Date(s);
    if (isNaN(d.getTime())) return s;
    return d.toLocaleString();
  };
  const setSendEnabledFromInput = () => {
    const ok = input.value.trim().length > 0;
    sendBtn.disabled = !ok;
    sendBtn.style.opacity = ok ? '' : '.6';
  };
  const bubble = (role, text) => {
    const el = document.createElement('div');
    el.className = `bubble ${role}`;
    el.textContent = text;
    log.appendChild(el);
    log.scrollTop = log.scrollHeight;
  };

  function clearDataView() {
    replyText.innerHTML = '';
    dataView.innerHTML = '';
    rawJson.textContent = '';
  }

  function renderKeyValues(obj, title) {
    const box = document.createElement('div');
    if (title) {
      const h = document.createElement('h3');
      h.textContent = title;
      h.style.margin = '12px 0 6px';
      h.style.fontSize = '16px';
      h.style.color = '#cbd5e1';
      box.appendChild(h);
    }
    const kv = document.createElement('div');
    kv.className = 'kv';
    Object.entries(obj).forEach(([k,v]) => {
      const b = document.createElement('b'); b.textContent = k;
      const t = document.createElement('div'); t.textContent = (v === null || v === undefined) ? '' : String(v);
      kv.append(b, t);
    });
    box.appendChild(kv);
    return box;
  }

  function tableFrom(items, columns, caption) {
    if (!Array.isArray(items) || items.length === 0) return null;
    const wrap = document.createElement('div');
    if (caption) {
      const h = document.createElement('h3');
      h.textContent = caption; h.style.margin = '12px 0 6px'; h.style.fontSize = '16px'; h.style.color='#cbd5e1';
      wrap.appendChild(h);
    }
    const table = document.createElement('table');
    const thead = document.createElement('thead');
    const trh = document.createElement('tr');
    columns.forEach(c => {
      const th = document.createElement('th'); th.textContent = c.header;
      trh.appendChild(th);
    });
    thead.appendChild(trh);
    table.appendChild(thead);

    const tbody = document.createElement('tbody');
    items.forEach(row => {
      const tr = document.createElement('tr');
      columns.forEach(c => {
        const td = document.createElement('td');
        let val = row?.[c.key];
        if (c.format === 'money') val = money(val);
        else if (c.format === 'date') val = fmtDate(val);
        td.textContent = (val === null || val === undefined) ? '' : String(val);
        tr.appendChild(td);
      });
      tbody.appendChild(tr);
    });
    table.appendChild(tbody);
    wrap.appendChild(table);
    return wrap;
  }

  function renderResponse(res, ms) {
    clearDataView();
    lastPanel.style.display = 'block';
    latencyEl.textContent = `${ms} ms`;

    // Friendly text
    const reply = res.reply || res.follow_up || res.response || res.message || 'No reply.';
    replyText.textContent = reply;

    // Pretty data rendering (best-effort).
    const d = res.data || {};
    // Common shapes we agreed on:
    // - { amount, currency?, status }
    // - { customer, accounts, transactions[], loans[] }
    // - arbitrary extras; everything still visible in Raw JSON

    if (d.amount !== undefined || d.status !== undefined || d.currency !== undefined) {
      const kv = {};
      if (d.amount !== undefined) kv.Amount = money(d.amount);
      if (d.currency !== undefined) kv.Currency = d.currency;
      if (d.status !== undefined) kv.Status = d.status;
      dataView.appendChild(renderKeyValues(kv, 'Summary'));
    }

    if (d.customer) {
      dataView.appendChild(renderKeyValues(d.customer, 'Customer'));
    }
    if (Array.isArray(d.accounts) && d.accounts.length) {
      const cols = [
        { header:'Account ID', key:'id' },
        { header:'Type', key:'type' },
        { header:'Balance', key:'balance', format:'money' }
      ];
      dataView.appendChild(tableFrom(d.accounts, cols, 'Accounts'));
    }
    if (Array.isArray(d.transactions) && d.transactions.length) {
      const cols = [
        { header:'Tx ID', key:'id' },
        { header:'Date', key:'transactionDate', format:'date' },
        { header:'Type', key:'type' },
        { header:'Amount', key:'amount', format:'money' }
      ];
      dataView.appendChild(tableFrom(d.transactions, cols, 'Transactions'));
    }
    if (Array.isArray(d.loans) && d.loans.length) {
      const cols = [
        { header:'Loan ID', key:'id' },
        { header:'Start Date', key:'startDate', format:'date' },
        { header:'Status', key:'status' },
        { header:'Outstanding', key:'outstanding', format:'money' }
      ];
      dataView.appendChild(tableFrom(d.loans, cols, 'Loans'));
    }

    // Raw JSON fallback (always available)
    rawJson.textContent = JSON.stringify(res, null, 2);
  }

  async function parseBody(res) {
    const text = await res.text();
    try { return JSON.parse(text); } catch { return { _raw: text }; }
  }

  async function sendMessage(message) {
    if (!message) return;
    bubble('user', message);
    setSendEnabledFromInput();

    const headers = { 'Content-Type': 'application/json', 'X-Correlation-Id': newCid() };
    const k = apiKeyInput.value.trim(); if (k) headers['X-API-Key'] = k;

    const t0 = performance.now();
    try {
      const res = await fetch(CHAT_URL, { method:'POST', headers, body: JSON.stringify({ message }) });
      const body = await parseBody(res);
      const ms = Math.max(1, Math.round(performance.now() - t0));

      if (!res.ok) {
        // Keep consistent with backend error shape
        const reply = body.reply || body.error || body.message || `Error ${res.status}`;
        bubble('assistant', String(reply));
        renderResponse(body, ms);
        return;
      }

      const reply = body.reply || body.follow_up || body.response || body.message || (body._raw ?? 'Sorry, I did not understand that.');
      bubble('assistant', reply);
      renderResponse(body, ms);

    } catch (e) {
      bubble('assistant', 'Network error. Please try again.');
      console.error(e);
    } finally {
      setSendEnabledFromInput();
    }
  }

  // ---- wire up --------------------------------------------------------------
  sendBtn.disabled = true;
  bubble('assistant', "Hi! Try:\n- balance for id 101\n- balance for John Doe\n- last 5 transactions for id 101\n- loan status for id 101");

  input.addEventListener('input', setSendEnabledFromInput);
  setSendEnabledFromInput();

  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !(input.tagName === 'TEXTAREA' && !e.ctrlKey)) {
      e.preventDefault();
      const msg = input.value.trim();
      if (!msg) return;
      input.value = ''; setSendEnabledFromInput();
      sendMessage(msg);
    }
  });

  form.addEventListener('submit', (e) => {
    e.preventDefault();
    const msg = input.value.trim();
    if (!msg) return;
    input.value = ''; setSendEnabledFromInput();
    sendMessage(msg);
  });
});
