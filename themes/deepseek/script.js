function send() {
    let q = document.getElementById('input').value;
    if (!q) return;
    let chat = document.getElementById('chat');
    chat.innerHTML += "<p><b>Ты:</b> " + q + "</p>";
    document.getElementById('input').value = '';
    if (q.startsWith(':')) {
        fetch('/command?cmd=' + encodeURIComponent(q))
            .then(r => r.text())
            .then(a => { chat.innerHTML += "<p><b>Tool:</b> " + a + "</p>"; });
        return;
    }
    let params = 'q=' + encodeURIComponent(q);
    fetch('/ask?' + params)
        .then(r => r.text())
        .then(a => { chat.innerHTML += "<p><b>Учитель:</b> " + a + "</p>"; });
}