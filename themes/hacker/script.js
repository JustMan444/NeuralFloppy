function send() {
    let q = document.getElementById('input').value;
    if (!q) return;
    let chat = document.getElementById('chat');
    chat.innerHTML += "<p><b>You:</b> " + q + "</p>";
    document.getElementById('input').value = '';
    if (q.startsWith(':')) {
        fetch('/command?cmd=' + encodeURIComponent(q))
            .then(r => r.text())
            .then(a => { chat.innerHTML += "<p><b>Tool:</b> " + a + "</p>"; });
        return;
    }
    let mode = document.getElementById('mode').value;
    let model = document.getElementById('model').value;
    let temp = document.getElementById('temp').value;
    let stream = document.getElementById('stream').checked ? 'on' : 'off';
    let params = 'q=' + encodeURIComponent(q) + '&mode=' + mode + '&model=' + encodeURIComponent(model) + '&temp=' + temp + '&stream=' + stream;
    fetch('/ask?' + params)
        .then(r => r.text())
        .then(a => { chat.innerHTML += "<p><b>Assistant:</b> " + a + "</p>"; });
}