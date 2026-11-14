package com.example.neuralfloppy.controller;

import com.example.neuralfloppy.tool.NeuralFloppyTool2_0_DT_snapchot2;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.example.neuralfloppy.tool.NeuralFloppyTool2_0_DT_snapchot2.handleCommand;

public class WebServer {
    public static void startWebServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
            server.createContext("/", new ChatHandler());
            server.createContext("/command", new WebCommandHandler());
            server.createContext("/ask", new NeuralFloppyTool2_0_DT_snapchot2.AskHandler());
            server.createContext("/ask-stream", new NeuralFloppyTool2_0_DT_snapchot2.AskStreamHandler());

            // === 1.9.2: Game API Endpoints ===
            server.createContext("/api/game", new GameAPI.GameHandler());
            server.createContext("/api/full-control", new GameAPI.FullControlHandler());

            server.setExecutor(null);
            server.start();
            System.out.println("Сервер запущен на http://localhost:8080");
            System.out.println("Игровое API доступно на /api/game и /api/full-control");
        } catch (IOException e) {
            System.out.println("Ошибка запуска сервера: " + e.getMessage());
        }
    }
    public static class WebCommandHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery().split("=")[1];
            query = java.net.URLDecoder.decode(query, StandardCharsets.UTF_8);
            String result = "";
            try {
                // Перенаправляем команду в handleCommand, но перехватываем вывод
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintStream oldOut = System.out;
                System.setOut(new PrintStream(baos, true, StandardCharsets.UTF_8));
                handleCommand(query);
                System.setOut(oldOut);
                result = baos.toString(StandardCharsets.UTF_8).trim();
            } catch (Exception e) {
                result = "Ошибка команды: " + e.getMessage();
            }
            byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }

    // Отдаёт HTML-страницу
    public static class ChatHandler implements HttpHandler {
        public static String currentTheme = null;
        public static int themeLevel = 1;

        public static String currentCssTheme = null;   // имя темы для CSS
        public static String currentHtmlTheme = null;  // имя темы для HTML
        public static String currentJsTheme = null;    // имя темы для JS

        public void handle(HttpExchange exchange) throws IOException {
            String css = "";
            String customHtml = "";
            String customJs = "";

            // Загружаем CSS темы, если задан
            if (currentCssTheme != null) {
                Path cssPath = Path.of("themes/" + currentCssTheme + "/style.css");
                if (Files.exists(cssPath)) {
                    css = "<style>" + Files.readString(cssPath) + "</style>";
                }
            }

            // Загружаем HTML темы, если задан
            if (currentHtmlTheme != null) {
                Path htmlPath = Path.of("themes/" + currentHtmlTheme + "/template.html");
                if (Files.exists(htmlPath)) {
                    customHtml = Files.readString(htmlPath);
                }
            }

            // Загружаем JS темы, если задан
            if (currentJsTheme != null) {
                Path jsPath = Path.of("themes/" + currentJsTheme + "/script.js");
                if (Files.exists(jsPath)) {
                    customJs = "<script>" + Files.readString(jsPath) + "</script>";
                }
            }

            // Если CSS не задан — используем встроенный
            if (css.isEmpty()) {
                css = """
            <style>
                body { font-family: monospace; background: #111; color: #0f0; padding: 20px; }
                #chat { border: 1px solid #0f0; padding: 10px; height: 300px; overflow-y: auto; margin-bottom: 10px; }
                .panel { margin-bottom: 10px; }
                .panel select, .panel input, .panel button { background: #222; color: #0f0; border: 1px solid #0f0; padding: 4px; margin-right: 5px; }
                .panel button { cursor: pointer; }
                #input { width: 70%; background: #222; color: #0f0; border: 1px solid #0f0; padding: 5px; }
                button.send { background: #0f0; color: #111; border: none; padding: 6px 12px; font-weight: bold; }
            </style>
            """;
            }

            // Если HTML не задан — используем стандартную структуру
            if (customHtml.isEmpty()) {
                customHtml = """
            <h1>NeuralFloppy v2.0 Web Console</h1>
            <div id="chat"></div>
            <div class="panel">
                <label>Режим:</label>
                <select id="mode">
                    <option value="api">API</option>
                    <option value="local">LOCAL</option>
                </select>
                <label>Модель:</label>
                <input type="text" id="model" value="openrouter/free" size="20">
                <label>Temperature:</label>
                <input type="number" id="temp" value="0.7" min="0" max="2" step="0.1" style="width:60px">
                <label>Stream:</label>
                <input type="checkbox" id="stream" checked>
                <button onclick="sendCommand(':status')">Статус</button>
            </div>
            <input type="text" id="input" placeholder="Введи вопрос или команду (например, :status)">
            <button class="send" onclick="send()">Отправить</button>
            """;
            }

            // Если JS не задан — используем стандартный скрипт
            if (customJs.isEmpty()) {
                customJs = """
            <script>
                function send() {
    let q = document.getElementById('input').value;
    if (!q) return;
    let chat = document.getElementById('chat');
    chat.innerHTML += "<p><b>Ты:</b> " + q + "</p>";
    document.getElementById('input').value = '';

    if (q.startsWith(':')) {
        fetch('/command?cmd=' + encodeURIComponent(q))
            .then(r => r.text())
            .then(a => {
                chat.innerHTML += "<p><b>Tool:</b> " + a + "</p>";
                chat.scrollTop = chat.scrollHeight;
            });
        return;
    }

    let mode = document.getElementById('mode').value;
    let model = document.getElementById('model').value;
    let temp = document.getElementById('temp').value;
    let stream = document.getElementById('stream').checked ? 'on' : 'off';
    let params = 'q=' + encodeURIComponent(q) + '&mode=' + mode + '&model=' + encodeURIComponent(model) + '&temp=' + temp + '&stream=' + stream;

    // Создаём контейнер для ответа
    let answerP = document.createElement('p');
    answerP.innerHTML = '<b>Учитель:</b> ';
    chat.appendChild(answerP);

    let eventSource = new EventSource('/ask-stream?' + params);
    eventSource.onmessage = function(event) {
        if (event.data === '[DONE]') {
            eventSource.close();
        } else {
            answerP.innerHTML += event.data;
            chat.scrollTop = chat.scrollHeight;
        }
    };
    eventSource.onerror = function() {
        answerP.innerHTML += ' [Ошибка соединения]';
        eventSource.close();
    };
}
            </script>
            """;
            }

            String html = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <title>NeuralFloppy Web UI</title>
            %s
        </head>
        <body>
            %s
            %s
        </body>
        </html>
        """.formatted(css, customHtml, customJs);

            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }
}
