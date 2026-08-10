package Tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.*;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.net.InetSocketAddress;

public class NeuralFloppyTool1_6 {
    private static final String PERSONA_FILE = "persona.txt";
    private static final String NDJSON_FILE = "chat.ndjson";
    private static final String ARCHIVE_DIR = "archive";
    private static final String API_KEY = "sk-or-v1-...."; // твой ключ
    private static double temperature = 0.7;
    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final Gson GSON = new Gson();


    private static List<Message> messages = new ArrayList<>();
    private static Map<String, List<Integer>> wordIndex = new HashMap<>();
    private static String currentPersona = "";

    enum Mode { API, LOCAL, MANUAL }
    private static Mode currentMode = Mode.API;
    private static String currentModel = "openrouter/free";
    private static boolean autoSave = true;
    private static boolean streaming = true;

    // Эмбеддинги
    private static boolean embedEnabled = false;
    private static int embedAutoThreshold = 0; // 0 = выключено
    private static int embedNewCount = 0;

    private static int personaAutoThreshold = 0; // 0 = выключено
    private static int personaNewCount = 0;

    public static void main(String[] args) throws Exception {
        currentPersona = Files.readString(Path.of(PERSONA_FILE));
        buildIndex();
        if (Files.exists(Path.of("data/embeddings.json"))) {
            EmbeddingEngine.load();
            System.out.println("Эмбеддинги загружены: " + EmbeddingEngine.vectors.size() + " векторов.");
        }

        System.out.println("NeuralFloppy TOOL V1.6/*. " + messages.size() + " сообщений в индексе.");
        System.out.println("Режим: " + currentMode + " | Модель: " + currentModel + " | Автосохранение: " + (autoSave ? "вкл" : "выкл") + " | Стриминг: " + (streaming ? "вкл" : "выкл"));
        System.out.println("Введи :help для списка команд.\n");

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        while (true) {
            System.out.print("Ты: ");
            String q = reader.readLine();
            if (q == null || q.isBlank()) continue;
            if (!isValidInput(q)) {
                System.out.println("ИИ: Бро, кодировка сломалась. Повтори вопрос.");
                continue;
            }

            if (q.startsWith(":")) {
                handleCommand(q);
                continue;
            }

            Message userMsg = new Message("USER", q, Instant.now().getEpochSecond());
            messages.add(userMsg);
            if (autoSave) appendToNdjson(userMsg);
            addToIndex(userMsg, messages.size() - 1);

            String answer = "";
            System.out.print("\nИИ: ");
            switch (currentMode) {
                case API -> answer = streaming ? askAPIStreaming(q) : askAPI(q);
                case LOCAL -> answer = streaming ? askLocalStreaming(q) : askLocal(q);
                case MANUAL -> {
                    String prompt = buildPrompt(q);
                    System.out.println("\n===== СКОПИРУЙ ЭТО В МОДЕЛЬ =====");
                    System.out.println(prompt);
                    System.out.println("=================================");
                    System.out.print("Вставь ответ модели: ");
                    answer = reader.readLine();
                }
            }

            if (!answer.isBlank()) {
                Message assistantMsg = new Message("ASSISTANT", answer, Instant.now().getEpochSecond());
                messages.add(assistantMsg);
                if (autoSave) appendToNdjson(assistantMsg);
                addToIndex(assistantMsg, messages.size() - 1);
            }
            if (autoSave && embedAutoThreshold > 0) {
                embedNewCount++;
                if (embedNewCount >= embedAutoThreshold) {
                    System.out.println("[Авто-embed] Перестраиваю эмбеддинги...");
                    EmbeddingEngine.build();
                    embedNewCount = 0;
                }
            }
            if (personaAutoThreshold > 0) {
                personaNewCount++;
                if (personaNewCount >= personaAutoThreshold) {
                    System.out.println("[Авто-persona] Генерирую новую персону...");
                    String newPersona = autoPersona();
                    if (!newPersona.isBlank()) {
                        Files.createDirectories(Path.of(ARCHIVE_DIR));
                        Files.writeString(Path.of(ARCHIVE_DIR, "persona_backup_" + Instant.now().toString().replace(":", "-") + ".txt"), currentPersona);
                        Files.writeString(Path.of(PERSONA_FILE), newPersona);
                        currentPersona = newPersona;
                        System.out.println("Персона обновлена автоматически.");
                    }
                    personaNewCount = 0;
                }
            }
            System.out.println();
        }
    }

    // ================== API ==================
    static String askAPI(String question) throws Exception {
        String persona = currentPersona;
        List<String> ctx = searchContext(question, 10);
        String context = String.join("\n---\n", ctx);
        String prompt = String.format("""
            %s

            Вот история твоего общения с учеником:
            %s

            Ученик спросил: %s
            Ответь как тот самый наставник:""", persona, context, question);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://openrouter.ai/api/v1/chat/completions"))
                .header("Authorization", "Bearer " + API_KEY)
                .header("HTTP-Referer", "http://localhost")
                .header("X-Title", "NeuralFloppy")
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(Map.of(
                        "model", currentModel,
                        "messages", List.of(Map.of("role", "user", "content", prompt)),
                        "temperature", temperature,
                        "max_tokens", 500
                )), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.body();
        JsonObject json = GSON.fromJson(body, JsonObject.class);
        if (json.has("choices") && json.getAsJsonArray("choices").size() > 0) {
            JsonObject message = json.getAsJsonArray("choices").get(0)
                    .getAsJsonObject().getAsJsonObject("message");
            JsonElement content = message.get("content");
            if (content != null && !content.isJsonNull()) {
                return content.getAsString();
            } else {
                return "Модель не ответила. Возможно, сработал фильтр безопасности. Попробуй другую модель.";
            }
        }
        return "Ошибка API: " + body;
    }

    static String askAPIStreaming(String question) throws Exception {
        String persona = currentPersona;
        List<String> ctx = searchContext(question, 10);
        String context = String.join("\n---\n", ctx);
        String prompt = String.format("""
            %s

            Вот история твоего общения с учеником:
            %s

            Ученик спросил: %s
            Ответь как тот самый наставник:""", persona, context, question);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://openrouter.ai/api/v1/chat/completions"))
                .header("Authorization", "Bearer " + API_KEY)
                .header("HTTP-Referer", "http://localhost")
                .header("X-Title", "NeuralFloppy")
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(Map.of(
                        "model", currentModel,
                        "messages", List.of(Map.of("role", "user", "content", prompt)),
                        "temperature", temperature,
                        "max_tokens", 500,
                        "stream", true
                )), StandardCharsets.UTF_8))
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        BufferedReader in = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));
        StringBuilder fullAnswer = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            if (line.startsWith("data: ")) {
                String jsonStr = line.substring(6).trim();
                if (jsonStr.equals("[DONE]")) continue;
                try {
                    JsonObject json = GSON.fromJson(jsonStr, JsonObject.class);
                    if (json.has("choices") && json.getAsJsonArray("choices").size() > 0) {
                        JsonObject delta = json.getAsJsonArray("choices").get(0).getAsJsonObject();
                        String chunk = null;
                        if (delta.has("delta") && delta.getAsJsonObject("delta").has("content")) {
                            chunk = delta.getAsJsonObject("delta").get("content").getAsString();
                        } else if (delta.has("message") && delta.getAsJsonObject("message").has("content")) {
                            chunk = delta.getAsJsonObject("message").get("content").getAsString();
                        }
                        if (chunk != null) {
                            System.out.print(chunk);
                            fullAnswer.append(chunk);
                        }
                    }
                } catch (Exception e) {}
            }
        }
        System.out.println();
        return fullAnswer.toString();
    }

    // ================== LOCAL ==================
    static String askLocal(String question) throws Exception {
        String persona = currentPersona;
        List<String> ctx = searchContext(question, 10);
        String context = String.join("\n---\n", ctx);
        String prompt = String.format("""
            %s

            Вот история твоего общения с учеником:
            %s

            Ученик спросил: %s
            Ответь как тот самый наставник:""", persona, context, question);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/generate"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(Map.of(
                        "model", currentModel,
                        "prompt", prompt,
                        "stream", false,
                        "options", Map.of("num_ctx", 32768),
                        "temperature",temperature
                )), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.body();
        JsonObject json = GSON.fromJson(body, JsonObject.class);
        if (json.has("response")) {
            JsonElement resp = json.get("response");
            if (resp != null && !resp.isJsonNull()) {
                return resp.getAsString();
            } else {
                return "Локальная модель вернула пустой ответ.";
            }
        }
        return "Ошибка Ollama: " + body;
    }
    static class AskHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery().split("=")[1];
            query = java.net.URLDecoder.decode(query, StandardCharsets.UTF_8);
            String answer = "";
            try {
                switch (currentMode) {
                    case API:
                        answer = askAPI(query);
                        break;
                    case LOCAL:
                        answer = askLocal(query);
                        break;
                    case MANUAL:
                        answer = "Ручной режим недоступен в веб-интерфейсе. Переключись на :mode api или :mode local.";
                        break;
                }
            } catch (Exception e) {
                answer = "Ошибка: " + e.getMessage();
            }
            byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }

    static String askLocalStreaming(String question) throws Exception {
        String persona = currentPersona;
        List<String> ctx = searchContext(question, 10);
        String context = String.join("\n---\n", ctx);
        String prompt = String.format("""
            %s

            Вот история твоего общения с учеником:
            %s

            Ученик спросил: %s
            Ответь как тот самый наставник:""", persona, context, question);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/generate"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(Map.of(
                        "model", currentModel,
                        "prompt", prompt,
                        "stream", true,
                        "options", Map.of("num_ctx", 32768),
                        "temperature",temperature
                )), StandardCharsets.UTF_8))
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        BufferedReader in = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));
        StringBuilder fullAnswer = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            try {
                JsonObject json = GSON.fromJson(line, JsonObject.class);
                if (json.has("response")) {
                    String chunk = json.get("response").getAsString();
                    System.out.print(chunk);
                    fullAnswer.append(chunk);
                }
                if (json.has("done") && json.get("done").getAsBoolean()) break;
            } catch (Exception e) {}
        }
        System.out.println();
        return fullAnswer.toString();
    }

    // ================== КОМАНДЫ ==================
    static void handleCommand(String cmd) throws IOException {
        String[] parts = cmd.split("\\s+");
        switch (parts[0]) {
            case ":help" -> System.out.println("""
                    Команды:
                    :mode api|local|manual - переключить режим
                    :model <имя>           - сменить модель
                    :persona               - показать текущую персону
                    :persona save <имя>    - сохранить текущую персону в профиль
                    :persona load <имя>    - загрузить персону из профиля
                    :persona new <имя>     - создать новый пустой профиль
                    :autosave on|off       - вкл/выкл автосохранение
                    :stream on|off         - вкл/выкл потоковый вывод
                    :status                - показать состояние Tool
                    :models                - показать список локальных моделей
                    :save                  - сохранить сессию в архив
                    :exit                  - выход
                    :web                   - открытие чата в браузере
                    :embed build           - построить эмбеддинги для всей базы
                    :embed on|off          - вкл/выкл семантический поиск
                    :embed auto <N>        - авто-перестроение каждые N сообщений
                    :embed status          - состояние движка
                    :persona auto          - ИИ анализирует диалоги и создаёт новую персону
                    :persona auto          - ручное обновление персоны
                    :persona auto <N>      - авто-обновление каждые N сообщений
                    :persona auto off      - отключить авто-обновление
                    """);
            case ":mode" -> {
                if (parts.length < 2) {
                    System.out.println("Укажи режим: api, local, manual");
                    return;
                }
                switch (parts[1].toLowerCase()) {
                    case "api" -> currentMode = Mode.API;
                    case "local" -> currentMode = Mode.LOCAL;
                    case "manual" -> currentMode = Mode.MANUAL;
                    default -> System.out.println("Неизвестный режим.");
                }
                System.out.println("Режим переключён на " + currentMode);
            }
            case ":temperature" -> {
                if (parts.length < 2) {
                    System.out.println("Укажи значение от 0.0 до 2.0. Например: :temperature 0.3");
                    return;
                }
                try {
                    double temp = Double.parseDouble(parts[1]);
                    if (temp < 0.0 || temp > 2.0) throw new NumberFormatException();
                    temperature = temp;
                    System.out.println("Temperature установлена на " + temperature);
                } catch (NumberFormatException e) {
                    System.out.println("Некорректное значение. Укажи число от 0.0 до 2.0");
                }
            }
            case ":embed" -> {
                if (parts.length < 2) {
                    System.out.println("Используй: :embed build|on|off|auto <N>|status");
                    return;
                }
                switch (parts[1]) {
                    case "build" -> {
                        System.out.println("Строю эмбеддинги... Это может занять минуту.");
                        try {
                            EmbeddingEngine.build();
                        } catch (Exception e) {
                            System.out.println("Ошибка: " + e.getMessage());
                        }
                    }
                    case "on" -> {
                        embedEnabled = true;
                        System.out.println("Эмбеддинги включены.");
                    }
                    case "off" -> {
                        embedEnabled = false;
                        System.out.println("Эмбеддинги выключены.");
                    }
                    case "auto" -> {
                        if (parts.length < 3) {
                            System.out.println("Укажи число сообщений. Например: :embed auto 10");
                            return;
                        }
                        embedAutoThreshold = Integer.parseInt(parts[2]);
                        embedNewCount = 0;
                        System.out.println("Авто-перестроение каждые " + embedAutoThreshold + " новых сообщений.");
                    }
                    case "status" -> {
                        System.out.println("Эмбеддинги: " + (embedEnabled ? "вкл" : "выкл"));
                        System.out.println("Векторов в памяти: " + EmbeddingEngine.vectors.size());
                        System.out.println("Авто-перестроение: " + (embedAutoThreshold > 0 ? "каждые " + embedAutoThreshold + " сообщ." : "выкл"));
                    }
                    default -> System.out.println("Неизвестная подкоманда :embed");
                }
            }

            case ":model" -> {
                if (parts.length < 2) {
                    System.out.println("Укажи имя модели.");
                    return;
                }
                currentModel = parts[1];
                System.out.println("Модель сменена на " + currentModel);
            }
            case ":web" -> {
                System.out.println("Запускаю веб-интерфейс на http://localhost:8080");
                startWebServer();
            }
            case ":persona" -> {
                if (parts.length < 2) {
                    System.out.println("Текущая персона:\n" + currentPersona);
                    return;
                }
                if (parts[1].equals("save")) {
                    if (parts.length < 3) { System.out.println("Укажи имя профиля. Например: :persona save злой"); return; }
                    String profileName = parts[2];
                    Files.createDirectories(Path.of("personas"));
                    Files.writeString(Path.of("personas/" + profileName + ".txt"), currentPersona);
                    System.out.println("Персона сохранена как " + profileName);
                } else if (parts[1].equals("load")) {
                    if (parts.length < 3) { System.out.println("Укажи имя профиля. Например: :persona load философ"); return; }
                    String profileName = parts[2];
                    Path profilePath = Path.of("personas/" + profileName + ".txt");
                    if (!Files.exists(profilePath)) {
                        System.out.println("Профиль не найден: " + profilePath);
                        return;
                    }
                    currentPersona = Files.readString(profilePath);
                    Files.writeString(Path.of(PERSONA_FILE), currentPersona);
                    System.out.println("Персона загружена из профиля " + profileName);
                } else if (parts[1].equals("new")) {
                    if (parts.length < 3) { System.out.println("Укажи имя новой персоны. Например: :persona new хакер"); return; }
                    String profileName = parts[2];
                    Path profilePath = Path.of("personas/" + profileName + ".txt");
                    if (Files.exists(profilePath)) {
                        System.out.println("Профиль '" + profileName + "' уже существует. Используй :persona load или save.");
                        return;
                    }
                    String template = """
            [СИСТЕМА]
            Ты — ИИ-наставник внутри NeuralFloppy. Ты помогаешь пользователю.

            [ТВОЯ РОЛЬ]
            Опиши здесь свою роль и стиль общения.

            [ПРАВИЛА ОТВЕТА]
            1. ...
            2. ...
            """;
                    Files.createDirectories(Path.of("personas"));
                    Files.writeString(profilePath, template);
                    System.out.println("Новая персона создана: personas/" + profileName + ".txt");
                    System.out.println("Отредактируй этот файл, а затем загрузи его командой :persona load " + profileName);
                } else if (parts[1].equals("auto")) {
                    if (parts.length >= 3 && parts[2].equalsIgnoreCase("off")) {
                        personaAutoThreshold = 0;
                        personaNewCount = 0;
                        System.out.println("Авто-обновление персоны выключено.");
                        return;
                    }
                    if (parts.length >= 3 && parts[2].matches("\\d+")) {
                        personaAutoThreshold = Integer.parseInt(parts[2]);
                        personaNewCount = 0;
                        System.out.println("Авто-обновление персоны каждые " + personaAutoThreshold + " сообщ.");
                        return;
                    }
                    // Ручной запуск
                    System.out.println("Анализирую последние диалоги и генерирую новую персону...");
                    String newPersona = autoPersona();
                    if (!newPersona.isBlank()) {
                        Files.writeString(Path.of(ARCHIVE_DIR, "persona_backup_" + Instant.now().toString().replace(":", "-") + ".txt"), currentPersona);
                        Files.writeString(Path.of(PERSONA_FILE), newPersona);
                        currentPersona = newPersona;
                        System.out.println("Персона обновлена!");
                    }
                }
            }

            case ":autosave" -> {
                if (parts.length < 2) { System.out.println("Укажи on или off"); return; }
                autoSave = parts[1].equalsIgnoreCase("on");
                System.out.println("Автосохранение " + (autoSave ? "включено" : "выключено"));
            }
            case ":status" -> {
                System.out.println("=== Статус NeuralFloppy ===");
                System.out.println("Режим: " + currentMode);
                System.out.println("Модель: " + currentModel);
                System.out.println("Автосохранение: " + (autoSave ? "вкл" : "выкл"));
                System.out.println("Потоковый вывод: " + (streaming ? "вкл" : "выкл"));
                System.out.println("Сообщений в индексе: " + messages.size());
                System.out.println("Размер базы: " + Files.size(Path.of(NDJSON_FILE)) + " байт");
                System.out.println("=========================");
            }
            case ":models" -> {
                System.out.println("Локальные модели (через Ollama API):");
                try {
                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:11434/api/tags"))
                            .GET()
                            .build();
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                    if (json.has("models")) {
                        json.getAsJsonArray("models").forEach(m -> {
                            String name = m.getAsJsonObject().get("name").getAsString();
                            System.out.println("  - " + name);
                        });
                    } else {
                        System.out.println("Пустой ответ от Ollama.");
                    }
                } catch (Exception e) {
                    System.out.println("Ошибка: " + e.getMessage() + ". Ollama точно запущена?");
                }
            }
            case ":stream" -> {
                if (parts.length < 2) { System.out.println("Укажи on или off"); return; }
                streaming = parts[1].equalsIgnoreCase("on");
                System.out.println("Потоковый вывод " + (streaming ? "включен" : "выключен"));
            }
            case ":save" -> saveArchive();
            case ":exit" -> System.exit(0);
            default -> System.out.println("Неизвестная команда. :help для списка.");
        }
    }

    // ================== ИНДЕКС, ПОИСК, СОХРАНЕНИЕ ==================
    static boolean isValidInput(String text) {
        if (text == null || text.isBlank()) return false;
        String cleaned = text.replace("?", "").replace("�", "").trim();
        if (cleaned.isEmpty()) return false;
        long readable = text.chars().filter(c -> Character.isLetterOrDigit(c) || Character.isWhitespace(c)).count();
        return (double) readable / text.length() > 0.2;
    }
    static String autoPersona() {
        int count = Math.min(50, messages.size());
        if (count == 0) return "";
        List<Message> recent = messages.subList(messages.size() - count, messages.size());
        StringBuilder history = new StringBuilder();
        for (Message m : recent) {
            history.append(m.role).append(": ").append(m.content).append("\n");
        }
        String analysisPrompt = """
        Ты — эксперт по психологии пользователей. Проанализируй следующие диалоги и создай новый системный промт для ИИ-наставника.
        Опиши стиль общения, интересы, слабости пользователя. Предложи такой стиль, который будет максимально полезен и приятен пользователю.
        Верни ТОЛЬКО текст нового системного промта, без пояснений.
        
        ДИАЛОГИ:
        """ + history.toString();

        try {
            String answer = "";
            if (currentMode == Mode.API) {
                answer = askAPI(analysisPrompt);
            } else if (currentMode == Mode.LOCAL) {
                answer = askLocal(analysisPrompt);
            } else {
                System.out.println("Ручной режим. Скопируй промт и вставь ответ:");
                System.out.println(analysisPrompt);
                System.out.print("Новая персона: ");
                answer = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
            }
            return answer != null ? answer.trim() : "";
        } catch (Exception e) {
            System.out.println("Ошибка генерации персоны: " + e.getMessage());
            return "";
        }
    }

    static void startWebServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
            server.createContext("/", new ChatHandler());
            server.createContext("/command", new CommandHandler());
            server.createContext("/ask", new AskHandler());
            server.setExecutor(null);
            server.start();
            System.out.println("Сервер запущен. Не закрывай это окно.");
        } catch (IOException e) {
            System.out.println("Ошибка запуска сервера: " + e.getMessage());
        }
    }
    static class CommandHandler implements HttpHandler {
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
    static class ChatHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>NeuralFloppy Web UI</title>
                <style>
                    body { font-family: monospace; background: #111; color: #0f0; padding: 20px; }
                    #chat { border: 1px solid #0f0; padding: 10px; height: 300px; overflow-y: auto; margin-bottom: 10px; }
                    .panel { margin-bottom: 10px; }
                    .panel select, .panel input, .panel button { background: #222; color: #0f0; border: 1px solid #0f0; padding: 4px; margin-right: 5px; }
                    .panel button { cursor: pointer; }
                    #input { width: 70%; background: #222; color: #0f0; border: 1px solid #0f0; padding: 5px; }
                    button.send { background: #0f0; color: #111; border: none; padding: 6px 12px; font-weight: bold; }
                </style>
            </head>
            <body>
                <h1>NeuralFloppy v1.6 Web Console</h1>
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
                <script>
                    function send() {
                        let q = document.getElementById('input').value;
                        if (!q) return;
                        let chat = document.getElementById('chat');
                        chat.innerHTML += "<p><b>Ты:</b> " + q + "</p>";
                        document.getElementById('input').value = '';
                        
                        // Если это команда, отправляем её
                        if (q.startsWith(':')) {
                            fetch('/command?cmd=' + encodeURIComponent(q))
                                .then(r => r.text())
                                .then(a => {
                                    chat.innerHTML += "<p><b>Tool:</b> " + a + "</p>";
                                    chat.scrollTop = chat.scrollHeight;
                                });
                            return;
                        }
                        
                        // Иначе собираем настройки
                        let mode = document.getElementById('mode').value;
                        let model = document.getElementById('model').value;
                        let temp = document.getElementById('temp').value;
                        let stream = document.getElementById('stream').checked ? 'on' : 'off';
                        let params = 'q=' + encodeURIComponent(q) + '&mode=' + mode + '&model=' + encodeURIComponent(model) + '&temp=' + temp + '&stream=' + stream;
                        fetch('/ask?' + params)
                            .then(r => r.text())
                            .then(a => {
                                chat.innerHTML += "<p><b>Учитель:</b> " + a + "</p>";
                                chat.scrollTop = chat.scrollHeight;
                            });
                    }
                </script>
            </body>
            </html>
            """;
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }

    static void buildIndex() throws IOException {
        messages.clear();
        wordIndex.clear();
        Path filePath = Path.of(NDJSON_FILE);
        if (!Files.exists(filePath)) {
            System.out.println("[DEBUG] Файл не найден: " + filePath.toAbsolutePath());
            return;
        }
        try (Stream<String> lines = Files.lines(filePath, StandardCharsets.UTF_8)) {
            lines.forEach(line -> {
                if (line.isBlank()) return;
                try {
                    JsonObject obj = GSON.fromJson(line, JsonObject.class);
                    Message msg = new Message(
                            obj.get("role").getAsString(),
                            obj.get("content").getAsString(),
                            obj.has("ts") ? obj.get("ts").getAsLong() : 0
                    );
                    messages.add(msg);
                    addToIndex(msg, messages.size() - 1);
                } catch (Exception e) {}
            });
        }
    }

    static void addToIndex(Message msg, int idx) {
        if ("ASSISTANT".equals(msg.role) || "USER".equals(msg.role)) {
            for (String word : tokenize(msg.content)) {
                wordIndex.computeIfAbsent(word, k -> new ArrayList<>()).add(idx);
            }
        }
    }

    static void appendToNdjson(Message msg) throws IOException {
        String line = GSON.toJson(msg) + "\n";
        Files.writeString(Path.of(NDJSON_FILE), line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    static List<String> searchContext(String query, int topN) {
        if (embedEnabled && !EmbeddingEngine.vectors.isEmpty()) {
            try {
                return EmbeddingEngine.search(query, topN);
            } catch (Exception e) {
                System.out.println("[Embed] Ошибка поиска: " + e.getMessage());
            }
        }
        // Fallback на старый wordIndex
        Set<String> queryTokens = tokenize(query);
        Map<Integer, Integer> scores = new HashMap<>();
        for (String token : queryTokens) {
            List<Integer> ids = wordIndex.get(token);
            if (ids != null) ids.forEach(id -> scores.merge(id, 1, Integer::sum));
        }
        return scores.entrySet().stream()
                .sorted((e1, e2) -> {
                    int cmp = Integer.compare(e2.getValue(), e1.getValue());
                    if (cmp == 0) cmp = Long.compare(messages.get(e2.getKey()).ts, messages.get(e1.getKey()).ts);
                    return cmp;
                })
                .limit(topN)
                .map(e -> messages.get(e.getKey()).content)
                .collect(Collectors.toList());
    }

    static String buildPrompt(String question) throws IOException {
        List<String> ctx = searchContext(question, 10);
        String context = String.join("\n---\n", ctx);
        return String.format("""
            %s

            Вот история твоего общения с учеником:
            %s

            Ученик спросил: %s
            Ответь как тот самый наставник:""", currentPersona, context, question);
    }

    static void saveArchive() throws IOException {
        Files.createDirectories(Path.of(ARCHIVE_DIR));
        String filename = "archive/session_" + Instant.now().toString().replace(":", "-") + ".json";
        String json = GSON.toJson(messages);
        Files.writeString(Path.of(filename), json);
        System.out.println("Сессия сохранена в " + filename);
    }

    static Set<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase().split("[^а-яa-z0-9]+"))
                .filter(w -> w.length() > 1)
                .collect(Collectors.toSet());
    }
    static class EmbeddingEngine {
        static List<double[]> vectors = new ArrayList<>();
        static List<String> texts = new ArrayList<>();
        static final String EMBED_MODEL = "nomic-embed-text";
        static final Path EMBED_FILE = Path.of("data/embeddings.json");

        static void build() throws Exception {
            vectors.clear();
            texts.clear();
            HttpClient client = HttpClient.newHttpClient();
            int skipped = 0;
            for (Message msg : messages) {
                if (msg.content.isBlank()) continue;
                // Обрезаем длинные сообщения (модель не любит больше ~2000 символов)
                String text = msg.content.length() > 2000 ? msg.content.substring(0, 2000) : msg.content;
                try {
                    double[] vec = getEmbedding(client, text);
                    vectors.add(vec);
                    texts.add(text);
                } catch (Exception e) {
                    skipped++;
                    // Просто пропускаем проблемные сообщения
                }
            }
            save();
            System.out.println("Построено " + vectors.size() + " эмбеддингов. Пропущено: " + skipped);
        }

        static double[] getEmbedding(HttpClient client, String text) throws Exception {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:11434/api/embeddings"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(Map.of(
                            "model", EMBED_MODEL,
                            "prompt", text
                    )), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = resp.body();
            JsonObject json = GSON.fromJson(body, JsonObject.class);

            // Проверяем, нет ли ошибки
            if (json.has("error")) {
                throw new RuntimeException("Embedding error: " + json.get("error").toString());
            }

            double[] vec = new double[768];
            int i = 0;
            for (JsonElement e : json.getAsJsonArray("embedding")) {
                vec[i++] = e.getAsDouble();
            }
            return vec;
        }

        static List<String> search(String query, int topN) throws Exception {
            if (vectors.isEmpty()) return List.of();
            HttpClient client = HttpClient.newHttpClient();
            double[] queryVec = getEmbedding(client, query);
            record Pair(int idx, double sim) {}
            List<Pair> scores = new ArrayList<>();
            for (int i = 0; i < vectors.size(); i++) {
                double sim = cosineSimilarity(queryVec, vectors.get(i));
                scores.add(new Pair(i, sim));
            }
            scores.sort((a, b) -> Double.compare(b.sim, a.sim));
            return scores.stream()
                    .limit(topN)
                    .map(p -> texts.get(p.idx))
                    .collect(Collectors.toList());
        }

        static double cosineSimilarity(double[] a, double[] b) {
            double dot = 0, normA = 0, normB = 0;
            for (int i = 0; i < a.length; i++) {
                dot += a[i] * b[i];
                normA += a[i] * a[i];
                normB += b[i] * b[i];
            }
            return dot / (Math.sqrt(normA) * Math.sqrt(normB));
        }

        static void save() throws IOException {
            Files.writeString(EMBED_FILE, GSON.toJson(Map.of(
                    "vectors", vectors,
                    "texts", texts
            )));
        }

        static void load() throws IOException {
            if (!Files.exists(EMBED_FILE)) return;
            JsonObject json = GSON.fromJson(Files.readString(EMBED_FILE), JsonObject.class);
            vectors.clear();
            texts.clear();
            JsonArray vArr = json.getAsJsonArray("vectors");
            JsonArray tArr = json.getAsJsonArray("texts");
            for (int i = 0; i < vArr.size(); i++) {
                double[] vec = new double[768];
                JsonArray vecArr = vArr.get(i).getAsJsonArray();
                for (int j = 0; j < 768; j++) {
                    vec[j] = vecArr.get(j).getAsDouble();
                }
                vectors.add(vec);
                texts.add(tArr.get(i).getAsString());
            }
        }
    }

    static class Message {
        String role, content;
        long ts;
        Message(String r, String c, long t) { role = r; content = c; ts = t; }
    }
}