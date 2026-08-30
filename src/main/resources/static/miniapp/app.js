(function () {
  'use strict';

  const tg = window.Telegram && window.Telegram.WebApp;
  if (tg) { tg.ready(); tg.expand(); }
  const initData = tg ? tg.initData : '';

  function haptic(style) {
    try { if (tg && tg.HapticFeedback) tg.HapticFeedback.impactOccurred(style || 'light'); } catch (e) { /* ignore */ }
  }

  const I18N = {
    uz: {
      projects: "Loyihalar", sprints: "Sprintlar",
      responsible: "Mas'ul", deadline: "Dedlayn", status: "Holat",
      overdue: "Muddati o'tgan", executor: "Ijrochi",
      overdueOnly: "faqat muddati o'tganlar", noData: "Ma'lumot yo'q", loading: "Yuklanmoqda…",
      comments: "Izohlar", history: "Holatlar tarixi", attachments: "Fayllar",
      noComments: "Hozircha izohlar yo'q", noHistory: "Hali o'zgarishlar bo'lmagan", noAttachments: "Fayl yo'q",
      start: "▶️ Boshlash", complete: "✅ Yakunlash", cancel: "❌ Bekor qilish", addComment: "Yuborish",
      commentPlaceholder: "Izohingizni yozing…", cancelReasonPlaceholder: "Bekor qilish sababi",
      postpone: "⏳ Muddat ko'chirish", newDeadlinePlaceholder: "KK.OO.YYYY yoki 'ertaga'",
      send: "Yuborish", errorGeneric: "Xatolik yuz berdi",
      needStart: "Iltimos, avval botga /start yuboring.", postponeSent: "So'rov yuborildi, rahbar javobini kuting",
      commentAdded: "Izoh qo'shildi", statusUpdated: "Holat yangilandi",
      cancelTitle: "Vazifani bekor qilish", postponeTitle: "Muddatni ko'chirish", dismiss: "Yopish"
    },
    ru: {
      projects: "Проекты", sprints: "Спринты",
      responsible: "Ответственный", deadline: "Дедлайн", status: "Статус",
      overdue: "Просрочено", executor: "Исполнитель",
      overdueOnly: "только просроченные", noData: "Нет данных", loading: "Загрузка…",
      comments: "Комментарии", history: "История статусов", attachments: "Файлы",
      noComments: "Комментариев пока нет", noHistory: "Изменений пока не было", noAttachments: "Файлов нет",
      start: "▶️ Начать", complete: "✅ Завершить", cancel: "❌ Отменить", addComment: "Отправить",
      commentPlaceholder: "Напишите комментарий…", cancelReasonPlaceholder: "Причина отмены",
      postpone: "⏳ Перенести срок", newDeadlinePlaceholder: "ДД.ММ.ГГГГ или «завтра»",
      send: "Отправить", errorGeneric: "Произошла ошибка",
      needStart: "Сначала напишите боту /start.", postponeSent: "Запрос отправлен, ожидайте решения руководителя",
      commentAdded: "Комментарий добавлен", statusUpdated: "Статус обновлён",
      cancelTitle: "Отмена задачи", postponeTitle: "Перенос срока", dismiss: "Закрыть"
    },
    en: {
      projects: "Projects", sprints: "Sprints",
      responsible: "Responsible", deadline: "Deadline", status: "Status",
      overdue: "Overdue", executor: "Executor",
      overdueOnly: "overdue only", noData: "No data", loading: "Loading…",
      comments: "Comments", history: "Status history", attachments: "Files",
      noComments: "No comments yet", noHistory: "No changes yet", noAttachments: "No files",
      start: "▶️ Start", complete: "✅ Complete", cancel: "❌ Cancel", addComment: "Send",
      commentPlaceholder: "Write a comment…", cancelReasonPlaceholder: "Cancellation reason",
      postpone: "⏳ Request postpone", newDeadlinePlaceholder: "DD.MM.YYYY or 'tomorrow'",
      send: "Send", errorGeneric: "An error occurred",
      needStart: "Please send /start to the bot first.", postponeSent: "Request sent, awaiting manager's decision",
      commentAdded: "Comment added", statusUpdated: "Status updated",
      cancelTitle: "Cancel task", postponeTitle: "Postpone deadline", dismiss: "Close"
    }
  };

  function detectDefaultLang() {
    const stored = localStorage.getItem('miniapp_lang');
    if (stored && I18N[stored]) return stored;
    const tgLang = tg && tg.initDataUnsafe && tg.initDataUnsafe.user && tg.initDataUnsafe.user.language_code;
    if (tgLang && tgLang.startsWith('uz')) return 'uz';
    if (tgLang && tgLang.startsWith('en')) return 'en';
    return 'ru';
  }

  let lang = detectDefaultLang();
  function t(key) { return (I18N[lang] && I18N[lang][key]) || key; }

  async function api(path, options) {
    options = options || {};
    options.headers = Object.assign({ 'X-Telegram-Init-Data': initData, 'Content-Type': 'application/json' }, options.headers || {});
    const res = await fetch('/api/miniapp' + path, options);
    if (!res.ok) {
      let msg = t('errorGeneric');
      if (res.status === 401) {
        msg = t('needStart');
      } else {
        try { const body = await res.json(); msg = body.error || msg; } catch (e) { /* ignore */ }
      }
      throw new Error(msg);
    }
    return res.status === 204 ? null : res.json();
  }

  const stack = [{ view: 'projects', params: {} }];
  function pushView(view, params) { haptic('light'); stack.push({ view, params }); render(); }
  function popView() { haptic('light'); stack.pop(); render(); }

  const content = document.getElementById('content');
  const pageTitle = document.getElementById('pageTitle');
  const backBtn = document.getElementById('backBtn');

  backBtn.addEventListener('click', () => { if (stack.length > 1) popView(); });
  if (tg && tg.BackButton) {
    tg.BackButton.onClick(() => { if (stack.length > 1) popView(); });
  }

  document.querySelectorAll('.lang-switch button').forEach(btn => {
    btn.addEventListener('click', async () => {
      haptic('light');
      lang = btn.dataset.lang;
      localStorage.setItem('miniapp_lang', lang);
      updateLangButtons();
      try {
        await api('/language', { method: 'POST', body: JSON.stringify({ language: lang.toUpperCase() }) });
      } catch (e) { /* not registered yet - ignore, still switch client-side chrome */ }
      render();
    });
  });
  function updateLangButtons() {
    document.querySelectorAll('.lang-switch button').forEach(b => b.classList.toggle('active', b.dataset.lang === lang));
  }
  updateLangButtons();

  function showToast(msg) {
    const el = document.createElement('div');
    el.className = 'toast';
    el.textContent = msg;
    document.body.appendChild(el);
    setTimeout(() => el.remove(), 2600);
  }

  function esc(s) {
    const d = document.createElement('div');
    d.textContent = s === null || s === undefined ? '' : String(s);
    return d.innerHTML;
  }

  function dot(cls) { return '<span class="dot ' + esc(cls) + '"></span>'; }

  // ------------------------------------------------------------------ //
  // Bottom sheet (mirrors the native-app "sheet" pattern for actions
  // that need a bit more room than an inline button, e.g. reason/date entry)
  // ------------------------------------------------------------------ //
  function openSheet(titleText, bodyHtml) {
    closeSheet();
    const overlay = document.createElement('div');
    overlay.className = 'sheet-overlay';
    overlay.id = 'activeSheet';
    overlay.innerHTML =
        '<div class="sheet">' +
        '<div class="sheet-handle"></div>' +
        '<h3>' + esc(titleText) + '</h3>' +
        '<div class="sheet-body">' + bodyHtml + '</div>' +
        '</div>';
    overlay.addEventListener('click', (e) => { if (e.target === overlay) closeSheet(); });
    document.body.appendChild(overlay);
    return overlay;
  }
  function closeSheet() {
    const el = document.getElementById('activeSheet');
    if (el) el.remove();
  }

  function skeletonList(count) {
    let html = '<div class="skeleton-list">';
    for (let i = 0; i < (count || 4); i++) html += '<div class="skeleton-card"></div>';
    return html + '</div>';
  }

  async function render() {
    const current = stack[stack.length - 1];
    backBtn.hidden = stack.length <= 1;
    if (tg && tg.BackButton) {
      if (stack.length > 1) tg.BackButton.show(); else tg.BackButton.hide();
    }
    content.innerHTML = skeletonList(current.view === 'task' ? 3 : 4);
    try {
      if (current.view === 'projects') await renderProjects();
      else if (current.view === 'project') await renderProject(current.params.id);
      else if (current.view === 'sprint') await renderSprint(current);
      else if (current.view === 'task') await renderTask(current.params.id);
    } catch (e) {
      content.innerHTML = '<div class="empty">' + esc(e.message) + '</div>';
    }
  }

  async function renderProjects() {
    pageTitle.textContent = t('projects');
    const projects = await api('/projects');
    if (!projects.length) { content.innerHTML = '<div class="empty">' + t('noData') + '</div>'; return; }
    content.innerHTML = '<div class="list">' + projects.map(p => `
      <div class="row-card" data-id="${p.id}">
        <div class="row-title">${esc(p.name)}</div>
        <div class="row-meta">
          <span class="badge">${esc(p.statusLabel)}</span>
          <span class="progress"><div style="width:${p.percentDone}%"></div></span>
          <span>${p.percentDone}%</span>
        </div>
        <div class="row-meta">${dot(p.colorClass)} ${esc(p.deadlineLabel)}
          ${p.overdueCount > 0 ? '<span class="badge">' + esc(t('overdue')) + ': ' + p.overdueCount + '</span>' : ''}
        </div>
      </div>`).join('') + '</div>';
    content.querySelectorAll('.row-card').forEach(el => {
      el.addEventListener('click', () => pushView('project', { id: el.dataset.id }));
    });
  }

  async function renderProject(id) {
    const data = await api('/projects/' + id);
    pageTitle.textContent = data.name;
    let html = '<div class="card">' +
        (data.description ? '<p class="muted">' + esc(data.description) + '</p>' : '') +
        '<p class="muted">' + esc(t('responsible')) + ': ' + esc(data.responsibleName) + '<br/>' +
        esc(t('deadline')) + ': ' + esc(data.deadlineLabel) + ' · ' + esc(t('status')) +
        ': <span class="badge">' + esc(data.statusLabel) + '</span></p></div>' +
        '<h2 style="font-size:0.95em;">' + esc(t('sprints')) + '</h2>';
    if (!data.sprints.length) {
      html += '<div class="empty">' + t('noData') + '</div>';
    } else {
      html += '<div class="list">' + data.sprints.map(s => `
        <div class="row-card" data-id="${s.id}">
          <div class="row-title">${esc(s.name)}</div>
          <div class="row-meta">
            <span class="badge">${esc(s.statusLabel)}</span>
            <span class="progress"><div style="width:${s.percentDone}%"></div></span>
            <span>${s.percentDone}%</span>
          </div>
          <div class="row-meta">${dot(s.colorClass)} ${esc(s.deadlineLabel)}</div>
        </div>`).join('') + '</div>';
    }
    content.innerHTML = html;
    content.querySelectorAll('.row-card').forEach(el => {
      el.addEventListener('click', () => pushView('sprint', { projectId: id, sprintId: el.dataset.id, overdueOnly: false }));
    });
  }

  async function renderSprint(current) {
    const { projectId, sprintId } = current.params;
    const overdueOnly = !!current.params.overdueOnly;
    const qs = overdueOnly ? '?overdueOnly=true' : '';
    const data = await api('/projects/' + projectId + '/sprints/' + sprintId + qs);
    pageTitle.textContent = data.name;
    let html = '<div class="card"><p class="muted">' + esc(t('responsible')) + ': ' + esc(data.responsibleName) + '<br/>' +
        esc(t('deadline')) + ': ' + esc(data.deadlineLabel) + ' · ' + esc(t('status')) +
        ': <span class="badge">' + esc(data.statusLabel) + '</span></p></div>';
    html += '<div class="filters"><span class="chip' + (overdueOnly ? ' active' : '') + '" id="overdueChip">' +
        esc(t('overdueOnly')) + '</span></div>';
    if (!data.tasks.length) {
      html += '<div class="empty">' + t('noData') + '</div>';
    } else {
      html += '<div class="list">' + data.tasks.map(task => `
        <div class="row-card" data-id="${task.id}">
          <div class="row-title">${esc(task.name)}</div>
          <div class="row-meta"><span class="badge">${esc(task.statusLabel)}</span> ${esc(task.executorName)}</div>
          <div class="row-meta">${dot(task.colorClass)} ${esc(task.deadlineLabel)}</div>
        </div>`).join('') + '</div>';
    }
    content.innerHTML = html;
    content.querySelectorAll('.row-card').forEach(el => {
      el.addEventListener('click', () => pushView('task', { id: el.dataset.id }));
    });
    document.getElementById('overdueChip').addEventListener('click', () => {
      haptic('light');
      current.params.overdueOnly = !overdueOnly;
      render();
    });
  }

  async function renderTask(id) {
    const data = await api('/tasks/' + id);
    pageTitle.textContent = data.name;

    let html = '<div class="card">' +
        (data.description ? '<p class="muted">' + esc(data.description) + '</p>' : '') +
        '<p class="muted">' + esc(t('executor')) + ': ' + esc(data.executorName) + '<br/>' +
        esc(t('status')) + ': <span class="badge">' + esc(data.statusLabel) + '</span> · ' +
        esc(t('deadline')) + ': ' + esc(data.deadlineLabel) + '</p>' +
        '<div class="actions" id="taskActions"></div>' +
        '</div>';

    html += '<div class="card"><h2>' + esc(t('attachments')) + '</h2>';
    if (!data.attachments.length) {
      html += '<div class="muted">' + t('noAttachments') + '</div>';
    } else {
      html += data.attachments.map(a =>
          '<a class="attachment-link" target="_blank" rel="noopener" href="/attachments/' + a.id +
          '/open?initData=' + encodeURIComponent(initData) + '">' + esc(a.originalFileName) + '</a>'
      ).join('');
    }
    html += '</div>';

    html += '<div class="card"><h2>' + esc(t('history')) + '</h2>';
    if (!data.history.length) {
      html += '<div class="muted">' + t('noHistory') + '</div>';
    } else {
      html += data.history.map(h =>
          '<div class="history-item">' + esc(h.transition) +
          '<div class="meta">' + esc(h.changedByName) + ' · ' + esc(h.changedAtLabel) + '</div>' +
          (h.comment ? '<div>' + esc(h.comment) + '</div>' : '') + '</div>'
      ).join('');
    }
    html += '</div>';

    html += '<div class="card"><h2>' + esc(t('comments')) + '</h2><div id="commentsList">';
    if (!data.comments.length) {
      html += '<div class="muted">' + t('noComments') + '</div>';
    } else {
      html += data.comments.map(c =>
          '<div class="comment-item">' + esc(c.text) +
          '<div class="meta">' + esc(c.authorName) + ' · ' + esc(c.createdAtLabel) + '</div></div>'
      ).join('');
    }
    html += '</div>';
    if (data.actions.canComment) {
      html += '<div class="field" style="margin-top:10px;">' +
          '<textarea id="commentText" rows="2" placeholder="' + esc(t('commentPlaceholder')) + '"></textarea></div>' +
          '<button class="btn" id="sendCommentBtn">' + esc(t('addComment')) + '</button>';
    }
    html += '</div>';

    content.innerHTML = html;

    const actionsEl = document.getElementById('taskActions');
    if (data.actions.canStart) actionsEl.innerHTML += '<button class="btn" id="startBtn">' + esc(t('start')) + '</button>';
    if (data.actions.canComplete) actionsEl.innerHTML += '<button class="btn" id="completeBtn">' + esc(t('complete')) + '</button>';
    if (data.actions.canCancel) actionsEl.innerHTML += '<button class="btn danger" id="cancelBtn">' + esc(t('cancel')) + '</button>';
    if (data.actions.canRequestPostpone) actionsEl.innerHTML += '<button class="btn secondary" id="postponeBtn">' + esc(t('postpone')) + '</button>';

    const startBtn = document.getElementById('startBtn');
    if (startBtn) startBtn.addEventListener('click', () => doStatusChange(id, 'IN_PROGRESS'));

    const completeBtn = document.getElementById('completeBtn');
    if (completeBtn) completeBtn.addEventListener('click', () => doStatusChange(id, 'DONE'));

    const cancelBtn = document.getElementById('cancelBtn');
    if (cancelBtn) cancelBtn.addEventListener('click', () => {
      haptic('light');
      const sheet = openSheet(t('cancelTitle'),
          '<div class="field"><label>' + esc(t('cancelReasonPlaceholder')) + '</label>' +
          '<textarea id="cancelReasonInput" rows="3" autofocus></textarea></div>' +
          '<div class="sheet-actions">' +
          '<button class="btn secondary" id="sheetCancelBtn">' + esc(t('dismiss')) + '</button>' +
          '<button class="btn danger" id="confirmCancelBtn">' + esc(t('cancel')) + '</button>' +
          '</div>');
      sheet.querySelector('#sheetCancelBtn').addEventListener('click', closeSheet);
      sheet.querySelector('#confirmCancelBtn').addEventListener('click', async () => {
        const reason = sheet.querySelector('#cancelReasonInput').value.trim();
        if (!reason) return;
        try {
          await api('/tasks/' + id + '/status', { method: 'POST', body: JSON.stringify({ status: 'CANCELLED', comment: reason }) });
          closeSheet();
          showToast(t('statusUpdated'));
          render();
        } catch (e) { showToast(e.message); }
      });
    });

    const postponeBtn = document.getElementById('postponeBtn');
    if (postponeBtn) postponeBtn.addEventListener('click', () => {
      haptic('light');
      const sheet = openSheet(t('postponeTitle'),
          '<div class="field"><label>' + esc(t('newDeadlinePlaceholder')) + '</label>' +
          '<input type="text" id="postponeInput" placeholder="' + esc(t('newDeadlinePlaceholder')) + '"/></div>' +
          '<div class="sheet-actions">' +
          '<button class="btn" id="confirmPostponeBtn" style="width:100%;">' + esc(t('send')) + '</button>' +
          '</div>');
      sheet.querySelector('#confirmPostponeBtn').addEventListener('click', async () => {
        const value = sheet.querySelector('#postponeInput').value.trim();
        if (!value) return;
        try {
          await api('/tasks/' + id + '/postpone', { method: 'POST', body: JSON.stringify({ newDeadline: value }) });
          closeSheet();
          showToast(t('postponeSent'));
        } catch (e) { showToast(e.message); }
      });
    });

    const sendCommentBtn = document.getElementById('sendCommentBtn');
    if (sendCommentBtn) sendCommentBtn.addEventListener('click', async () => {
      const text = document.getElementById('commentText').value.trim();
      if (!text) return;
      try {
        await api('/tasks/' + id + '/comment', { method: 'POST', body: JSON.stringify({ text }) });
        showToast(t('commentAdded'));
        render();
      } catch (e) { showToast(e.message); }
    });
  }

  async function doStatusChange(id, status) {
    haptic('medium');
    try {
      await api('/tasks/' + id + '/status', { method: 'POST', body: JSON.stringify({ status }) });
      showToast(t('statusUpdated'));
      render();
    } catch (e) {
      showToast(e.message);
    }
  }

  render();
})();
