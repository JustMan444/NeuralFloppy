// === NeuralFloppy AI – EventSource-стриминг (классический бэкенд) ===

const chat = document.getElementById('chat');
const input = document.getElementById('input');
const sendBtn = document.getElementById('sendBtn');
const newChatBtn = document.getElementById('newChatBtn');
const statusBtn = document.getElementById('statusBtn');
const thinkingIndicator = document.getElementById('thinking-indicator');

// Тултипы
newChatBtn.setAttribute('data-tooltip', 'Новый чат');
statusBtn.setAttribute('data-tooltip', 'Статус сервера');

// Отправка по Enter
input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendMessage();
    }
});

sendBtn.addEventListener('click', sendMessage);
newChatBtn.addEventListener('click', () => { chat.innerHTML = ''; });
statusBtn.addEventListener('click', () => sendCommand(':status'));

// Авто-рост поля ввода
input.addEventListener('input', () => {
    input.style.height = 'auto';
    input.style.height = input.scrollHeight + 'px';
});

// ─── Главная логика отправки ──────────────────────────────────
function sendMessage() {
    const q = input.value.trim();
    if (!q) return;

    // Показываем сообщение пользователя
    appendMessage('user', q);
    input.value = '';
    input.style.height = 'auto';

    // Команды (начинаются с ":")
    if (q.startsWith(':')) {
        fetch('/command?cmd=' + encodeURIComponent(q))
            .then(r => r.text())
            .then(txt => appendMessage('tool', txt))
            .catch(() => appendMessage('tool', '⚠️ Ошибка выполнения команды'));
        return;
    }

    const mode = document.getElementById('mode').value;
    const model = document.getElementById('model').value;
    const temp = document.getElementById('temp').value;
    const useStream = document.getElementById('stream').checked;
    const baseParams = `q=${encodeURIComponent(q)}&mode=${mode}&model=${encodeURIComponent(model)}&temp=${temp}`;

    if (useStream) {
        streamViaEventSource(baseParams);
    } else {
        regularFetch(baseParams);
    }
}

// ─── Обычный запрос (без стрима) ──────────────────────────────
async function regularFetch(params) {
    showThinking(true);
    try {
        const response = await fetch('/ask?' + params).then(r => r.text());
        showThinking(false);
        appendMessage('assistant', response);
    } catch (err) {
        showThinking(false);
        appendMessage('assistant', '⚠️ Ошибка сети.');
    }
}

// ─── Стриминг через EventSource ──────────────────────────────
function streamViaEventSource(params) {
    showThinking(false);                     // убираем точки
    const msgDiv = document.createElement('div');
    msgDiv.classList.add('message', 'assistant-message');
    chat.appendChild(msgDiv);

    let fullText = '';
    const evtSource = new EventSource('/ask-stream?' + params);

    evtSource.onmessage = function(event) {
        if (event.data === '[DONE]') {
            evtSource.close();
            // Финальный рендер Markdown (на случай, если остались сырые маркеры)
            msgDiv.innerHTML = renderMarkdown(fullText);
        } else {
            fullText += event.data;
            // Постепенное обновление с Markdown (можно и без рендера до [DONE])
            msgDiv.innerHTML = renderMarkdown(fullText);
            chat.scrollTop = chat.scrollHeight;
        }
    };

    evtSource.onerror = function() {
        evtSource.close();
        if (!fullText) {
            msgDiv.innerHTML = '⚠️ [Ошибка соединения]';
        } else {
            msgDiv.innerHTML = renderMarkdown(fullText) + ' ⚠️ [Соединение прервано]';
        }
    };
}

// ─── Простой Markdown‑рендерер ────────────────────────────────
function renderMarkdown(text) {
    // Заголовки
    text = text.replace(/^### (.*$)/gim, '<h3>$1</h3>');
    text = text.replace(/^## (.*$)/gim, '<h2>$1</h2>');
    text = text.replace(/^# (.*$)/gim, '<h1>$1</h1>');
    // Жирный и курсив
    text = text.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
    text = text.replace(/\*(.*?)\*/g, '<em>$1</em>');
    // Код (строчный)
    text = text.replace(/`(.*?)`/g, '<code>$1</code>');
    // Переносы строк
    text = text.replace(/\n/g, '<br>');
    return text;
}

// ─── Вспомогательные функции ──────────────────────────────────
function sendCommand(cmd) {
    input.value = cmd;
    sendMessage();
}

function appendMessage(role, text) {
    const msgDiv = document.createElement('div');
    msgDiv.classList.add('message', `${role}-message`);
    if (role === 'assistant' || role === 'tool') {
        msgDiv.innerHTML = renderMarkdown(text);
    } else {
        msgDiv.textContent = text;
    }
    chat.appendChild(msgDiv);
    chat.scrollTop = chat.scrollHeight;
}

function showThinking(show) {
    if (show) {
        thinkingIndicator.style.display = 'flex';
        chat.appendChild(thinkingIndicator);
        sendBtn.classList.add('sending');
    } else {
        thinkingIndicator.style.display = 'none';
        if (thinkingIndicator.parentNode) {
            thinkingIndicator.parentNode.removeChild(thinkingIndicator);
        }
        sendBtn.classList.remove('sending');
    }
}