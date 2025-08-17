// app.js — robust wiring that (1) enables Send, (2) works with/without <form>

document.addEventListener('DOMContentLoaded', () => {
  // ----- DOM lookups (with fallbacks) ---------------------------------------
  const form =
    document.querySelector('#chat-form') ||
    document.querySelector('form[data-chat]') ||
    document.querySelector('form');

  // support either <input> or <textarea>
  const input =
    document.querySelector('#message') ||
    (form && (form.querySelector('#message') ||
              form.querySelector('textarea[name="message"]') ||
              form.querySelector('input[name="message"]'))) ||
    document.querySelector('textarea, input[type="text"]');

  const sendBtn =
    document.querySelector('#send') ||
    (form && form.querySelector('[type="submit"], button:not([type]), button[type="button"]')) ||
    document.querySelector('[type="submit"], button#send, button');

  let log =
    document.querySelector('#chat-log') ||
    document.querySelector('[data-chat-log]');

  if (!log) {
    log = document.createElement('div');
    log.id = 'chat-log';
    log.style.maxHeight = '60vh';
    log.style.overflowY = 'auto';
    log.style.padding = '12px';
    log.style.border = '1px solid #333';
    log.style.borderRadius = '8px';
    log.style.margin = '12px 0';
    (document.querySelector('#app') || document.body).prepend(log);
  }

  // ----- utilities ----------------------------------------------------------
  const enableSend = () => {
    if (!sendBtn) return;
    sendBtn.disabled = false;
    sendBtn.removeAttribute('aria-disabled');
    sendBtn.style.pointerEvents = 'auto';
    sendBtn.style.opacity = '';
  };

  const setSendEnabledFromInput = () => {
    if (!sendBtn || !input) return;
    const hasText = input.value && input.value.trim().length > 0;
    sendBtn.disabled = !hasText;
    sendBtn.setAttribute('aria-disabled', String(!hasText));
    sendBtn.style.pointerEvents = hasText ? 'auto' : 'none';
    sendBtn.style.opacity = hasText ? '' : '0.6';
  };

  const escapeHtml = (s) =>
    String(s).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');

  function addBubble(role, text) {
    const el = document.createElement('div');
    el.className = `bubble ${role}`;
    el.style.margin = '8px 0';
    el.style.padding = '10px 12px';
    el.style.borderRadius = '10px';
    el.style.whiteSpace = 'pre-wrap';
    el.style.lineHeight = '1.35';
    if (role === 'user') {
      el.style.background = '#1f3a5c'; el.style.color = 'white';
    } else {
      el.style.background = '#0f172a'; el.style.color = '#e5e7eb';
    }
    el.innerHTML = `<div>${escapeHtml(text)}</div>`;
    log.appendChild(el);
    log.scrollTop = log.scrollHeight;
  }

  async function sendMessage(message) {
    if (!message) return;
    addBubble('user', message);
    setSendEnabledFromInput(); // may disable while input empty

    try {
      const res = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message })
      });
      const data = await res.json().catch(() => ({}));
      const reply = data.reply || data.response || 'Sorry, I did not understand that.';
      addBubble('assistant', reply);
    } catch (err) {
      console.error('POST /api/chat failed', err);
      addBubble('assistant', 'Something went wrong. Please try again.');
    } finally {
      setSendEnabledFromInput(); // re-evaluate after response
    }
  }

  // ----- wiring (works with or without <form>) ------------------------------
  // Always enable the button on load so you can type immediately
  enableSend();
  setSendEnabledFromInput();

  if (input) {
    input.addEventListener('input', () => setSendEnabledFromInput());
    // Enter to send (works for <input> only; for <textarea>, use Ctrl+Enter)
    input.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && !(input.tagName === 'TEXTAREA' && !e.ctrlKey)) {
        e.preventDefault();
        const msg = (input.value || '').trim();
        if (msg) {
          input.value = '';
          setSendEnabledFromInput();
          sendMessage(msg);
        }
      } else if (input.tagName === 'TEXTAREA' && e.key === 'Enter' && e.ctrlKey) {
        e.preventDefault();
        const msg = (input.value || '').trim();
        if (msg) {
          input.value = '';
          setSendEnabledFromInput();
          sendMessage(msg);
        }
      }
    });
  }

  if (form) {
    form.addEventListener('submit', (e) => {
      e.preventDefault();
      const msg = (input && input.value || '').trim();
      if (!msg) return;
      if (input) input.value = '';
      setSendEnabledFromInput();
      sendMessage(msg);
    });
  } else if (sendBtn) {
    // No <form>: wire button click directly
    sendBtn.addEventListener('click', (e) => {
      e.preventDefault();
      const msg = (input && input.value || '').trim();
      if (!msg) return;
      if (input) input.value = '';
      setSendEnabledFromInput();
      sendMessage(msg);
    });
  }

  // Greeting
  addBubble(
    'assistant',
    "Hi! I can answer:\n- list customers\n- customer {id}\n- accounts for customer {id}\n- balance for account {id}"
  );
});
