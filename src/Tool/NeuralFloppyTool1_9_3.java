package Tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.*;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.net.InetSocketAddress;
import java.net.URLEncoder;

public class NeuralFloppyTool1_9_3 implements NeuralFloppyCore {
    private static final String PERSONA_FILE = "persona.txt";
    private static final String NDJSON_FILE = "chat.ndjson";
    private static final String ARCHIVE_DIR = "archive";
    private static final String API_KEY = "sk-or-v1-....."; // API ключ
    private static double temperature = 0.7;
    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final Gson GSON = new Gson();
    private static BufferedReader reader;

    private static List<Message> messages = new ArrayList<>();
    private static Map<String, List<Integer>> wordIndex = new HashMap<>();
    private static String currentPersona = "";

    enum Mode { API, LOCAL, MANUAL }
    private static Mode currentMode = Mode.API;
    private static String currentModel = "openrouter/free";
    private static boolean autoSave = true;
    private static boolean streaming = true;
    // Режимы NeuralFloppy 1.9.2
    private static boolean gameModeEnabled = false;    // Активация игрового API
    private static String defaultColumn = null;        // Колонка памяти по умолчанию
    private static boolean silentMode = false;         // Режим без генерации ответа
    private static boolean compressEnabled = true;     // Автосжатие в долгую память
    private static boolean dynamicContext = true;      // Динамический контекст

    private static int contextSize = 10; // по умолчанию
    private static boolean thinkingEnabled = false;
    private static boolean webSearchEnabled = false;

    // Эмбеддинги
    private static boolean embedEnabled = false;
    private static int embedAutoThreshold = 0; // 0 = выключено
    private static int embedNewCount = 0;
    private static boolean embedBuildInProgress = false;

    private static int personaAutoThreshold = 0; // 0 = выключено
    private static int personaNewCount = 0;

    private static int memoryAutoThreshold = 0;
    private static int memoryNewCount = 0;

    public static void main(String[] args) throws Exception {
        ensureDirectoriesAndFiles();
        DatabaseManager.initDatabase();

        try {
            List<String[]> dbMessages = DatabaseManager.loadMessagesFromDb();
            if (!dbMessages.isEmpty()) {
                messages.clear();
                wordIndex.clear();
                for (String[] m : dbMessages) {
                    Message msg = new Message(m[0], m[1], Long.parseLong(m[2]));
                    messages.add(msg);
                    addToIndex(msg, messages.size() - 1);
                }
                System.out.println("Загружено из базы: " + messages.size() + " сообщений.");
            } else {
                buildIndex(); // загрузка из NDJSON
                for (Message m : messages) {
                    DatabaseManager.saveMessageToDb(m.role, m.content, m.ts);
                }
                System.out.println("Загружено из NDJSON и мигрировано в базу: " + messages.size());
            }
        } catch (SQLException e) {
            System.out.println("Ошибка базы: " + e.getMessage());
            buildIndex(); // fallback на NDJSON
        }
        try {
            EmbeddingEngine.load();
            System.out.println("Эмбеддинги загружены из SQLite: " + EmbeddingEngine.vectors.size() + " векторов.");
        } catch (Exception e) {
            System.out.println("Ошибка загрузки эмбеддингов из базы: " + e.getMessage());
            // fallback: пробуем JSON, если есть
            if (Files.exists(Path.of("data/embeddings.json"))) {
                System.out.println("Пробуем загрузить из embeddings.json...");
                try {
                    // Здесь вызов старого метода load, если он ещё есть
                } catch (Exception ex) { /* игнорируем */ }
            }
        }
        ensureOllamaInstalled();
        ensureOllamaRunning();
        currentPersona = Files.readString(Path.of(PERSONA_FILE));
        ensureEmbeddingModel();
        if (Files.exists(Path.of("data/embeddings.json"))) {
            EmbeddingEngine.load();
            System.out.println("Эмбеддинги загружены: " + EmbeddingEngine.vectors.size() + " векторов.");
        }
        NeuralFloppyTool1_9_3 app = new NeuralFloppyTool1_9_3();
        GameAPI.setCore(app);

        System.out.println("NeuralFloppy TOOL V1.9.3/*. " + messages.size() + " сообщений в индексе.");
        System.out.println("Режим: " + currentMode + " | Модель: " + currentModel + " | Автосохранение: " + (autoSave ? "вкл" : "выкл") + " | Стриминг: " + (streaming ? "вкл" : "выкл"));
        System.out.println("Введи :help для списка команд.\n");

        reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        while (true) {
            System.out.print("Ты: ");
            String q = reader.readLine();
            if (q == null || q.isBlank()) continue;
            if (!isValidInput(q)) {
                System.out.println("ИИ:Кодировка сломана");
                continue;
            }

            if (q.startsWith(":")) {
                handleCommand(q);
                continue;
            }

            Message userMsg = new Message("USER", q, Instant.now().getEpochSecond());
            messages.add(userMsg);
            if (autoSave) appendToNdjson(userMsg);
            try {
                DatabaseManager.saveMessageToDb(userMsg.role, userMsg.content, userMsg.ts);
            } catch (Exception e) {
                System.out.println("Ошибка записи в БД: " + e.getMessage());
            }
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
                try {
                    DatabaseManager.saveMessageToDb(assistantMsg.role, assistantMsg.content, assistantMsg.ts);
                } catch (Exception e) {
                    System.out.println("Ошибка записи в БД: " + e.getMessage());
                }
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
            // Проверяем авто-обновление персоны
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
            // Авто-сжатие в долгую память
            if (memoryAutoThreshold > 0) {
                memoryNewCount++;
                if (memoryNewCount >= memoryAutoThreshold) {
                    System.out.println("[Авто-память] Сжимаю...");
                    if (messages.size() >= 2) {
                        Thread thread = new Thread(() -> {
                        int count = Math.min(10, messages.size());
                        List<Message> recent = messages.subList(messages.size() - count, messages.size());
                        StringBuilder history = new StringBuilder();
                        for (Message m : recent) {
                            history.append(m.role).append(": ").append(m.content).append("\n");
                        }
                        String compressPrompt = "Сожми следующий диалог в 3-5 предложений, сохранив суть, ключевые решения и код:\n" + history.toString();
                        try {
                            String compressed = "";
                            if (currentMode == Mode.API) compressed = askAPI(compressPrompt);
                            else if (currentMode == Mode.LOCAL) compressed = askLocal(compressPrompt);
                            if (!compressed.isBlank() && !compressed.contains("Ошибка")) {
                                HttpClient client = HttpClient.newHttpClient();
                                double[] vec = EmbeddingEngine.getEmbedding(client, compressed);
                                MemoryManager.addCompressed(compressed, vec);
                                System.out.println("Сжатый фрагмент сохранён.");
                            }

                        } catch (Exception e) { System.out.println("Ошибка сжатия: " + e.getMessage()); }
                    });
                        thread.setDaemon(true);
                        thread.start();
                    }
                    memoryNewCount = 0;
                }
            }
            System.out.println();
        }
    }
    static void ensureEmbeddingModel() throws IOException, InterruptedException {
        String model = EmbeddingEngine.EMBED_MODEL;
        // Проверяем, есть ли модель в Ollama
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/tags"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject json = GSON.fromJson(resp.body(), JsonObject.class);
        boolean hasModel = false;
        if (json.has("models")) {
            JsonArray models = json.getAsJsonArray("models");
            for (JsonElement m : models) {
                String name = m.getAsJsonObject().get("name").getAsString();
                if (name.startsWith(model)) {
                    hasModel = true;
                    break;
                }
            }
        }
        if (!hasModel) {
            System.out.println("[OLLAMA] Модель " + model + " не найдена. Скачиваю...");
            ProcessBuilder pb = new ProcessBuilder("ollama", "pull", model);
            pb.inheritIO();
            Process p = pb.start();
            p.waitFor();
        }
    }
    public static List<Message> loadMessagesFromDb() throws SQLException {
        List<Message> list = new ArrayList<>();
        String sql = "SELECT role, content, ts FROM messages ORDER BY id";
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:neuralfloppy.db");
             Statement stmt = c.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Message(rs.getString("role"), rs.getString("content"), rs.getLong("ts")));
            }
        }
        return list;
    }
    static void ensureDirectoriesAndFiles() throws IOException {
        // Создаём все нужные папки
        Files.createDirectories(Path.of("data"));
        Files.createDirectories(Path.of("archive"));
        Files.createDirectories(Path.of("personas"));
        Files.createDirectories(Path.of("themes"));
        Files.createDirectories(Path.of("presets"));
        Files.createDirectories(Path.of("out"));

        // Создаём пустой chat.ndjson, если его нет
        Path ndjsonPath = Path.of("chat.ndjson");
        if (!Files.exists(ndjsonPath)) {
            Files.createFile(ndjsonPath);
        }

        // Создаём пустой persona.txt, если его нет
        Path personaPath = Path.of("persona.txt");
        if (!Files.exists(personaPath)) {
            Files.writeString(personaPath, "[СИСТЕМА]\nТы — ИИ-наставник внутри NeuralFloppy.\n");
        }

        // Создаём пустые файлы тем и пресетов, если их нет (можно заменить на создание пустых JSON)
        // Пресеты уже создаются через команды, но на всякий случай создадим папку
    }

    // ================== API ==================
    static String askAPI(String question) throws Exception {
        String persona = currentPersona;
        List<String> ctx = searchContext(question, contextSize);
        String context = String.join("\n---\n", ctx);
        String prompt = String.format("""
            %s

            Вот история твоего общения с учеником:
            %s

            Ученик спросил: %s
            Ответь как тот самый наставник:""", persona, context, question);

        if (webSearchEnabled) {

            if (ctx.isEmpty() || ctx.size() < 3) {
                System.out.println("[Авто-поиск] Ищу в интернете: " + question);
                try {
                    String webResult = WebSearchEngine.search(question);
                    if (!webResult.isBlank()) {
                        context += "\n[Из интернета]: " + webResult;
                    }
                } catch (Exception e) { /* игнорируем */ }
            }
        }


        if (thinkingEnabled) {
            prompt = "Думай шаг за шагом и рассуждай вслух перед ответом.\n" + prompt;
        }

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
        if (body.contains("exceed_context_size") && contextSize > 50) {
            contextSize -= 50;
            System.out.println("[UAZ] Уменьшаю контекст до " + contextSize);
            return askAPI(question);
        }
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
    static String askAPIStreamingThinking(String question) throws Exception {
        String persona = currentPersona;
        List<String> ctx = searchContext(question, contextSize);
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
                        "model", "deepseek/deepseek-r1:free",
                        "messages", List.of(Map.of("role", "user", "content", prompt)),
                        "temperature", 0.7,
                        "max_tokens", 2048,
                        "stream", true,
                        "include_reasoning", true
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
                        if (delta.has("delta") && delta.getAsJsonObject("delta").has("reasoning")) {
                            String reasoning = delta.getAsJsonObject("delta").get("reasoning").getAsString();
                            System.out.print("[Мысль]: " + reasoning);
                        }
                        if (delta.has("delta") && delta.getAsJsonObject("delta").has("content")) {
                            String chunk = delta.getAsJsonObject("delta").get("content").getAsString();
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
    static boolean ensureOllamaInstalled() {
        // Проверяем, есть ли ollama в PATH
        try {
            ProcessBuilder pb = new ProcessBuilder("ollama", "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[OLLAMA] " + line);
                return true;
            }
        } catch (IOException e) {
            System.out.println("[OLLAMA] Ollama не найдена в PATH!");
            System.out.println("[OLLAMA] Пожалуйста, установите её с https://ollama.com/download");
            System.out.println("[OLLAMA] После установки перезапустите NeuralFloppy.");
            return false;
        }
        return false;
    }

    static String askAPIStreaming(String question) throws Exception {
        String persona = currentPersona;
        List<String> ctx = searchContext(question, contextSize);
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
        ensureOllamaRunning();
        String persona = currentPersona;
        List<String> ctx = searchContext(question, contextSize);
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
        if (body.contains("exceed_context_size") && contextSize > 50) {
            contextSize -= 50;
            System.out.println("[UAZ] Уменьшаю контекст до " + contextSize);
            return askLocal(question);
        }
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
    static String askAPIStreamingToWeb(String question, OutputStream os) throws Exception {
        String persona = currentPersona;
        List<String> ctx = searchContext(question, contextSize);
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
        String line;
        StringBuilder fullAnswer = new StringBuilder();
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
                            os.write(("data: " + chunk + "\n\n").getBytes(StandardCharsets.UTF_8));
                            os.flush();
                        }
                    }
                } catch (Exception e) {}
            }
        }
        return fullAnswer.toString();
    }

    static String askLocalStreamingToWeb(String question, OutputStream os) throws Exception {
        ensureOllamaRunning();
        String persona = currentPersona;
        List<String> ctx = searchContext(question, contextSize);
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
                        "options", Map.of("num_ctx", 32768)
                )), StandardCharsets.UTF_8))
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        BufferedReader in = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));
        String line;
        StringBuilder fullAnswer = new StringBuilder();
        while ((line = in.readLine()) != null) {
            try {
                JsonObject json = GSON.fromJson(line, JsonObject.class);
                if (json.has("response")) {
                    String chunk = json.get("response").getAsString();
                    os.write(("data: " + chunk + "\n\n").getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
                if (json.has("done") && json.get("done").getAsBoolean()) break;
            } catch (Exception e) {}
        }
        return fullAnswer.toString();
    }
    static class AskHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery().split("=")[1];
            query = java.net.URLDecoder.decode(query, StandardCharsets.UTF_8);
            String answer = "";
            try {
                // Сохраняем вопрос пользователя
                Message userMsg = new Message("USER", query, Instant.now().getEpochSecond());
                messages.add(userMsg);
                if (autoSave) appendToNdjson(userMsg);
                try {
                    DatabaseManager.saveMessageToDb(userMsg.role, userMsg.content, userMsg.ts);
                } catch (Exception e) {
                    System.out.println("Ошибка записи в БД: " + e.getMessage());
                }
                addToIndex(userMsg, messages.size() - 1);

                switch (currentMode) {
                    case API -> answer = askAPI(query);
                    case LOCAL -> answer = askLocal(query);
                    case MANUAL -> answer = "Ручной режим недоступен в веб-интерфейсе.";
                }

                // Сохраняем ответ
                Message assistantMsg = new Message("ASSISTANT", answer, Instant.now().getEpochSecond());
                messages.add(assistantMsg);
                if (autoSave) appendToNdjson(assistantMsg);
                try {
                    DatabaseManager.saveMessageToDb(assistantMsg.role, assistantMsg.content, assistantMsg.ts);
                } catch (Exception e) {
                    System.out.println("Ошибка записи в БД: " + e.getMessage());
                }
                addToIndex(assistantMsg, messages.size() - 1);
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
        ensureOllamaRunning();
        String persona = currentPersona;
        List<String> ctx = searchContext(question, contextSize);
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
    static class AskStreamHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            String q = java.net.URLDecoder.decode(query.split("=")[1], StandardCharsets.UTF_8);

            // Сохраняем вопрос пользователя
            Message userMsg = new Message("USER", q, Instant.now().getEpochSecond());
            messages.add(userMsg);
            if (autoSave) appendToNdjson(userMsg);
            addToIndex(userMsg, messages.size() - 1);

            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=UTF-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("Connection", "keep-alive");
            exchange.sendResponseHeaders(200, 0);

            OutputStream os = exchange.getResponseBody();
            String answer = "";
            try {
                if (currentMode == Mode.API) {
                    answer = askAPIStreamingToWeb(q, os);
                } else if (currentMode == Mode.LOCAL) {
                    answer = askLocalStreamingToWeb(q, os);
                } else {
                    os.write("data: Ручной режим не поддерживает стриминг.\n\n".getBytes(StandardCharsets.UTF_8));
                }

                // Сохраняем ответ (полный текст, собранный из стрима)
                if (!answer.isBlank()) {
                    Message assistantMsg = new Message("ASSISTANT", answer, Instant.now().getEpochSecond());
                    messages.add(assistantMsg);
                    if (autoSave) appendToNdjson(assistantMsg);
                    addToIndex(assistantMsg, messages.size() - 1);
                }

                // Сигнал завершения
                os.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                os.write(("data: Ошибка: " + e.getMessage() + "\n\n").getBytes(StandardCharsets.UTF_8));
            } finally {
                os.close();
            }
        }
    }

    // ================== КОМАНДЫ ==================
    static void handleCommand(String cmd) throws IOException {
        String[] parts = cmd.split("\\s+");
        switch (parts[0]) {
            case ":help" -> {
                if (parts.length > 1 && parts[1].equalsIgnoreCase("all")) {
                    System.out.println("""
                    :mode api|local|manual|programmers  - переключить режим
                    :model <имя>            - сменить модель
                    :think on|off           - включить/выключить режим размышлений
                    :websearch on|off       - включить/выключить авто-поиск в интернете
                    :persona                - показать текущую персону
                    :persona save <имя>     - сохранить персону в профиль
                    :persona load <имя>     - загрузить персону из профиля
                    :persona new <имя>      - создать новый профиль
                    :autosave on|off        - вкл/выкл автосохранение
                    :stream on|off          - вкл/выкл потоковый вывод
                    :status                 - показать состояние Tool
                    :models                 - список локальных моделей
                    :save                   - сохранить сессию в архив
                    :exit                   - выход
                    :web                    - открыть чат в браузере
                    :embed build            - построить эмбеддинги
                    :embed on|off           - вкл/выкл семантический поиск
                    :embed auto <N>         - авто-перестроение каждые N сообщений
                    :embed status           - состояние движка
                    :persona auto           - обновить персону через ИИ
                    :persona auto <N>       - авто-обновление каждые N сообщений
                    :persona auto off       - отключить авто-обновление
                    :remember               - сжать последние 10 сообщений
                    :memory migrate         - миграция JSON в SQLite
                    :memory status          - состояние долгой памяти
                    :memory search <текст>  - текстовый поиск по памяти
                    :memory auto <N>        - авто-сжатие каждые N сообщений
                    :memory purge <дни>     - удалить старые записи
                    :memorн default <имя>   - установить дефолтную колонку памяти
                    :summarize              - сводка последних 20 сообщений
                    :import <файл>          - импорт JSON-диалогов
                    :think on|off           - размышление
                    :websearch on|off       - поиск в интернете
                    :theme <имя> <уровень>  - применить тему (1=CSS, 2=CSS+HTML, 3=CSS+HTML+JS)
                    :theme off              - сбросить тему
                    :preset                 - пресеты!
                    :themes                 - показать список доступных тем
                    :presets                - показать список доступных пресетов
                    :game off               - отключить api
                    :game status            - получить статус mode programmers
                    :game list              - получить все колонки памяти (for easy mode in api)
                    :game memory <колонка>  -  создать колонку
                    :game clear <колонка>   - удалить данные из колонки
                    :game export <колонка>  - экспортировать колонку 
                    :storage default|short|long|archive - выбрать тип архитектуру не рекомендуется для личного использования
                    :silent on|off          - только запись 
                    :compress on|off        - авто-сжатие
                    :context dynamic|static -заморозить контекст
                    :test                   - тестирование всех систем
                    :clear                  - очистить консоль
                    :clear session          - сбрасывание активного контекста 
                   
            """); // :NeuralFloppy           -узнать новости проекта
                } else {
                    System.out.println("""
            ===== БАЗОВЫЕ КОМАНДЫ =====
            :mode api|local|manual|programmers
            :model <имя>            - сменить модель
            :models                 - список локальных моделей
            :status                 - состояние Tool
            :embed on|off           - вкл/выкл эмбеддинги
            :embed build            - построить эмбеддинги
            :web                    - открыть веб-интерфейс
            :exit                   - выход

            Для расширенного списка введи :help all
            """);
                }
            }
            // === NeuralFloppy 1.9.2 Новые Команды ===

            case ":mode" -> {
                if (parts.length < 2) {
                    System.out.println("Укажи режим: api, local, manual, programmers");
                    return;
                }
                switch (parts[1].toLowerCase()) {
                    case "api" -> currentMode = Mode.API;
                    case "local" -> currentMode = Mode.LOCAL;
                    case "manual" -> currentMode = Mode.MANUAL;
                    case "programmers" -> {
                        gameModeEnabled = true;
                        System.out.println("Игровой API активирован. Принимаю запросы на /api/game и /api/full-control");
                        System.out.println("Используй :game off для отключения.");
                    }
                    default -> System.out.println("Неизвестный режим.");
                }
            }
            case ":clear" -> {
                if (parts.length > 1 && parts[1].equalsIgnoreCase("session")) {
                    messages.clear();
                    wordIndex.clear();
                    System.out.println("Сессия сброшена. Память на диске не тронута.");
                } else {
                    // Очистка консоли (Windows / Unix)
                    try {
                        if (System.getProperty("os.name").toLowerCase().contains("win")) {
                            new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
                        } else {
                            System.out.print("\033[H\033[2J");
                            System.out.flush();
                        }
                    } catch (Exception e) {
                        System.out.println("Не удалось очистить экран: " + e.getMessage());
                    }
                }
            }
            case ":test" -> {
                System.out.println("=== Диагностика NeuralFloppy ===");
                // 1. Папки
                boolean dirsOk = Files.isDirectory(Path.of("data")) &&
                        Files.isDirectory(Path.of("archive")) &&
                        Files.isDirectory(Path.of("personas")) &&
                        Files.isDirectory(Path.of("themes")) &&
                        Files.isDirectory(Path.of("presets"));
                System.out.println("Папки: " + (dirsOk ? "OK" : "ОШИБКА"));

                // 2. Ollama
                boolean ollamaOk = false;
                try {
                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:11434/api/tags"))
                            .timeout(Duration.ofSeconds(3))
                            .GET()
                            .build();
                    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                    ollamaOk = resp.statusCode() == 200;
                } catch (Exception e) {
                    ollamaOk = false;
                }
                System.out.println("Ollama: " + (ollamaOk ? "OK" : "НЕДОСТУПНА"));

                // 3. Эмбеддинг-модель
                boolean embOk = !EmbeddingEngine.vectors.isEmpty() || Files.exists(Path.of("data/embeddings.json"));
                System.out.println("Эмбеддинги: " + (embOk ? "OK (" + EmbeddingEngine.vectors.size() + " векторов)" : "НЕТ ДАННЫХ"));

                // 4. Память
                System.out.println("Активных сообщений: " + messages.size());
                System.out.println("Размер chat.ndjson: " + Files.size(Path.of("chat.ndjson")) + " байт");

                // 5. Веб-сервер
                boolean webOk = false;
                try {
                    Socket socket = new Socket();
                    socket.connect(new InetSocketAddress("localhost", 8080), 2000);
                    webOk = true;
                    socket.close();
                } catch (Exception e) {
                    webOk = false;
                }
                System.out.println("Веб-сервер (порт 8080): " + (webOk ? "OK" : "НЕ ЗАПУЩЕН"));
                System.out.println("=== Диагностика завершена ===");
            }

            case ":game" -> {
                if (parts.length < 2) {
                    System.out.println("Используй: :game off | :game status | :game list | :game memory <колонка> | :game clear <колонка> | :game export <колонка>");
                    return;
                }
                switch (parts[1].toLowerCase()) {
                    case "off" -> {
                        gameModeEnabled = false;
                        System.out.println("Игровой API деактивирован.");
                    }
                    case "status" -> {
                        System.out.println("Игровой API: " + (gameModeEnabled ? "активен" : "неактивен"));
                        System.out.println("Колонка по умолчанию: " + (defaultColumn != null ? defaultColumn : "не задана"));
                    }
                    // Остальные команды :game будут обрабатываться в GameAPI
                    default -> {
                        System.out.println("Эта команда пока не реализована в консоли. Используйте API.");
                    }
                }
            }

            case ":storage" -> {
                if (parts.length < 2) {
                    System.out.println("Используй: :storage default|short|long|archive");
                    return;
                }
                switch (parts[1].toLowerCase()) {
                    case "default" -> {
                        StorageManager.setMode("default");
                        System.out.println("Режим хранения: классический (NDJSON + SQLite).");
                    }
                    case "short" -> {
                        StorageManager.setMode("short");
                        System.out.println("Режим хранения: короткий (только NDJSON).");
                    }
                    case "long" -> {
                        StorageManager.setMode("long");
                        System.out.println("Режим хранения: долгий (прямая запись в SQLite).");
                    }
                    case "archive" -> {
                        StorageManager.setMode("archive");
                        System.out.println("Режим хранения: архивный (сразу в холодную память).");
                    }
                    default -> System.out.println("Неизвестный режим хранения.");
                }
            }

            case ":silent" -> {
                if (parts.length < 2) {
                    System.out.println("Используй: :silent on|off");
                    return;
                }
                silentMode = parts[1].equalsIgnoreCase("on");
                System.out.println("Тихий режим (без генерации ответа): " + (silentMode ? "включён" : "выключен"));
            }

            case ":compress" -> {
                if (parts.length < 2) {
                    System.out.println("Используй: :compress on|off");
                    return;
                }
                compressEnabled = parts[1].equalsIgnoreCase("on");
                System.out.println("Автосжатие в долгую память: " + (compressEnabled ? "включено" : "выключено"));
            }

            case ":context" -> {
                if (parts.length < 2) {
                    System.out.println("Используй: :context dynamic|static");
                    return;
                }
                dynamicContext = parts[1].equalsIgnoreCase("dynamic");
                System.out.println("Режим контекста: " + (dynamicContext ? "динамический" : "статический"));
            }

            case ":memory" -> {
                if (parts.length < 2) {
                    System.out.println("Используй: :memory new <имя> | use <имя> | list | delete <имя> | default <имя> | status | search <текст> | auto <N> | purge <дни>");
                    return;
                }
                switch (parts[1].toLowerCase()) {
                    case "default" -> {
                        if (parts.length < 3) { System.out.println("Укажи имя колонки."); return; }
                        defaultColumn = parts[2];
                        System.out.println("Колонка по умолчанию установлена: " + defaultColumn);
                    }
                    default -> {
                    }
                }
            }
            case ":presets" -> {
                Path presetsDir = Path.of("presets");
                if (!Files.exists(presetsDir)) {
                    System.out.println("Папка presets/ не найдена.");
                    return;
                }
                System.out.println("Доступные пресеты:");
                try (var files = Files.list(presetsDir)) {
                    files.filter(p -> p.toString().endsWith(".json"))
                            .map(p -> "  - " + p.getFileName().toString().replace(".json", ""))
                            .forEach(System.out::println);
                } catch (Exception e) {
                    System.out.println("Ошибка при чтении папки presets: " + e.getMessage());
                }
            }
            case ":themes" -> {
                Path themesDir = Path.of("themes");
                if (!Files.exists(themesDir)) {
                    System.out.println("Папка themes/ не найдена.");
                    return;
                }
                System.out.println("Доступные темы:");
                try (var dirs = Files.list(themesDir)) {
                    dirs.filter(Files::isDirectory)
                            .map(p -> "  - " + p.getFileName().toString())
                            .forEach(System.out::println);
                } catch (Exception e) {
                    System.out.println("Ошибка при чтении папки themes: " + e.getMessage());
                }
            }
            case ":theme" -> {
                if (parts.length < 2) {
                    System.out.println("Используй: :theme <имя> [css|html|js|all|уровень]");
                    System.out.println("Примеры:");
                    System.out.println("  :theme deepseek 3       - применить всё (CSS+HTML+JS)");
                    System.out.println("  :theme deepseek css     - только CSS");
                    System.out.println("  :theme deepseek html    - только HTML");
                    System.out.println("  :theme deepseek js      - только JS");
                    System.out.println("  :theme deepseek css+html - CSS и HTML");
                    System.out.println("  :theme off              - сбросить всё");
                    return;
                }

                String themeName = parts[1];
                if (themeName.equalsIgnoreCase("off")) {
                    ChatHandler.currentCssTheme = null;
                    ChatHandler.currentHtmlTheme = null;
                    ChatHandler.currentJsTheme = null;
                    ChatHandler.themeLevel = 0;
                    System.out.println("Тема сброшена. Стандартный стиль применён.");
                    return;
                }

                Path themeDir = Path.of("themes/" + themeName);
                if (!Files.exists(themeDir)) {
                    System.out.println("Тема не найдена: " + themeDir);
                    return;
                }

                // Определяем, что применять
                boolean applyCss = false;
                boolean applyHtml = false;
                boolean applyJs = false;

                if (parts.length >= 3) {
                    String mode = parts[2].toLowerCase();
                    switch (mode) {
                        case "css":
                            applyCss = true;
                            break;
                        case "html":
                            applyHtml = true;
                            break;
                        case "js":
                            applyJs = true;
                            break;
                        case "all":
                            applyCss = applyHtml = applyJs = true;
                            break;
                        default:
                            // пытаемся распарсить как число (старый уровень)
                            try {
                                int level = Integer.parseInt(mode);
                                if (level < 1 || level > 3) throw new NumberFormatException();
                                applyCss = true;
                                applyHtml = (level >= 2);
                                applyJs = (level >= 3);
                            } catch (NumberFormatException e) {
                                // может, это комбинация типа css+html
                                if (mode.contains("css")) applyCss = true;
                                if (mode.contains("html")) applyHtml = true;
                                if (mode.contains("js")) applyJs = true;
                                if (!applyCss && !applyHtml && !applyJs) {
                                    System.out.println("Неверный режим. Используй: css, html, js, all, или уровень 1-3.");
                                    return;
                                }
                            }
                    }
                } else {
                    // без аргументов: спрашиваем
                    System.out.println("Тема '" + themeName + "' найдена. Что применить?");
                    System.out.println("  css   - Только цвета и анимации");
                    System.out.println("  html  - Только структуру");
                    System.out.println("  js    - Только скрипты");
                    System.out.println("  all   - Всё вместе");
                    System.out.print("Введи (css/html/js/all): ");
                    String choice = reader.readLine().trim().toLowerCase();
                    switch (choice) {
                        case "css": applyCss = true; break;
                        case "html": applyHtml = true; break;
                        case "js": applyJs = true; break;
                        case "all": applyCss = applyHtml = applyJs = true; break;
                        default:
                            System.out.println("Неверный ввод. Применяю только CSS.");
                            applyCss = true;
                    }
                }

                // Применяем выбранные части
                if (applyCss) ChatHandler.currentCssTheme = themeName;
                if (applyHtml) ChatHandler.currentHtmlTheme = themeName;
                if (applyJs) ChatHandler.currentJsTheme = themeName;

                // Определяем итоговый уровень для отображения
                if (applyCss && applyHtml && applyJs) ChatHandler.themeLevel = 3;
                else if (applyCss && applyHtml) ChatHandler.themeLevel = 2;
                else if (applyCss) ChatHandler.themeLevel = 1;
                else ChatHandler.themeLevel = 0; // например, только js

                System.out.println("Применены части темы '" + themeName + "': " +
                        (applyCss ? "CSS " : "") +
                        (applyHtml ? "HTML " : "") +
                        (applyJs ? "JS " : "") +
                        ". Открой http://localhost:8080");
            }
            case ":preset" -> {
                if (parts.length < 2) {
                    System.out.println("Используй: :preset eco|balanced|sport|uaz-200");
                    return;
                }
                String presetName = parts[1];
                Path presetPath = Path.of("presets/" + presetName + ".json");
                if (!Files.exists(presetPath)) {
                    System.out.println("Преcет не найден: " + presetPath);
                    return;
                }
                try {
                    String raw = Files.readString(presetPath, StandardCharsets.UTF_8);
                    JsonObject preset = GSON.fromJson(raw, JsonObject.class);

                    if (preset.has("temperature")) temperature = preset.get("temperature").getAsDouble();
                    if (preset.has("stream")) streaming = preset.get("stream").getAsBoolean();
                    if (preset.has("embed_enabled")) embedEnabled = preset.get("embed_enabled").getAsBoolean();
                    if (preset.has("compress")) memoryAutoThreshold = preset.get("compress").getAsBoolean() ? 10 : 0;
                    if (preset.has("context_messages")) {
                        int ctxSize = preset.get("context_messages").getAsInt();
                        // Сохраняем в глобальную переменную (добавь её в класс: private static int contextSize = 10;)
                        contextSize = ctxSize;
                    }
                    // Меняем эмбеддинг-модель, если нужно
                    if (preset.has("embed_model")) {
                        String embedModel = preset.get("embed_model").getAsString();
                        if (!EmbeddingEngine.EMBED_MODEL.equals(embedModel)) {
                            EmbeddingEngine.EMBED_MODEL = EmbeddingEngine.getFullEmbedModelName(embedModel);
                            System.out.println("Эмбеддинг-модель сменена на " + embedModel + ". Перестрой индекс: :embed build");
                        }
                    }
                    System.out.println("Преcет '" + presetName + "' загружен.");
                } catch (Exception e) {
                    System.out.println("Ошибка загрузки пресета: " + e.getMessage());
                }
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
            case ":think" -> {
                if (parts.length < 2) { System.out.println("Используй: :think on|off"); return; }
                thinkingEnabled = parts[1].equalsIgnoreCase("on");
                System.out.println("Режим размышлений " + (thinkingEnabled ? "включён" : "выключен"));
            }
            case ":websearch" -> {
                if (parts.length < 2) { System.out.println("Используй: :websearch on|off"); return; }
                webSearchEnabled = parts[1].equalsIgnoreCase("on");
                System.out.println("Поиск в интернете " + (webSearchEnabled ? "включён" : "выключен"));
            }
            case ":embed" -> {
                if (parts.length < 2) {
                    System.out.println("Используй: :embed build|on|off|auto <N>|status");
                    return;
                }
                switch (parts[1]) {
                    case "build" -> {
                        if (embedBuildInProgress) {
                            System.out.println("Эмбеддинги уже строятся. Ожидайте.");
                            return;
                        }
                        embedBuildInProgress = true;
                        Thread bg = new Thread(() -> {
                            try {
                                System.out.println("[EMBED] Строю эмбеддинги... Это может занять минуту.");
                                EmbeddingEngine.build();
                                System.out.println("[EMBED] Готово.");
                            } catch (Exception e) {
                                System.out.println("[EMBED] Ошибка: " + e.getMessage());
                            } finally {
                                embedBuildInProgress = false;
                            }
                        });
                        bg.setDaemon(true);
                        bg.start();
                        System.out.println("Эмбеддинги строятся в фоне. Можно писать дальше.");
                    }
                    case "model" -> {
                        if (parts.length < 3) {
                            System.out.println("Укажи модель. Например: :embed model nomic-embed-text");
                            return;
                        }
                        String newModel = parts[2];
                        boolean hasModel = false;
                        try {
                            HttpClient client = HttpClient.newHttpClient();
                            HttpRequest req = HttpRequest.newBuilder()
                                    .uri(URI.create("http://localhost:11434/api/tags"))
                                    .timeout(Duration.ofSeconds(5))
                                    .GET()
                                    .build();
                            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                            JsonObject json = GSON.fromJson(resp.body(), JsonObject.class);
                            if (json.has("models")) {
                                JsonArray models = json.getAsJsonArray("models");
                                for (JsonElement m : models) {
                                    String name = m.getAsJsonObject().get("name").getAsString();
                                    if (name.startsWith(newModel)) { hasModel = true; break; }
                                }
                            }
                        } catch (Exception e) {
                            System.out.println("Не удалось проверить модель: " + e.getMessage());
                            return;
                        }

                        if (!hasModel) {
                            System.out.println("Модель " + newModel + " не скачана. Скачиваю...");
                            try {
                                ProcessBuilder pb = new ProcessBuilder("ollama", "pull", newModel);
                                pb.inheritIO();
                                Process p = pb.start();
                                p.waitFor();
                            } catch (Exception e) {
                                System.out.println("Ошибка скачивания: " + e.getMessage());
                                return;
                            }
                        }

                        EmbeddingEngine.EMBED_MODEL = newModel;
                        System.out.println("Эмбеддинг-модель сменена на " + newModel + ". Перестрой индекс: :embed build");
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
            case ":import" -> {
                if (parts.length < 2) { System.out.println("Укажи путь к JSON-файлу."); return; }
                String importPath = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
                Path filePath = Path.of(importPath);
                if (!Files.exists(filePath)) { System.out.println("Файл не найден."); return; }
                System.out.println("Импортирую " + filePath.getFileName() + "...");
                try {
                    String raw = Files.readString(filePath, StandardCharsets.UTF_8);
                    JsonElement root = GSON.fromJson(raw, JsonElement.class);
                    List<Map<String, String>> imported = new ArrayList<>();
                    if (root.isJsonArray()) {
                        for (JsonElement el : root.getAsJsonArray()) {
                            JsonObject obj = el.getAsJsonObject();
                            if (obj.has("role") && obj.has("content"))
                                imported.add(Map.of("role", obj.get("role").getAsString(), "content", obj.get("content").getAsString()));
                        }
                    } else if (root.isJsonObject()) {
                        JsonObject obj = root.getAsJsonObject();
                        if (obj.has("messages")) {
                            for (JsonElement el : obj.getAsJsonArray("messages")) {
                                JsonObject msg = el.getAsJsonObject();
                                if (msg.has("role") && msg.has("content"))
                                    imported.add(Map.of("role", msg.get("role").getAsString(), "content", msg.get("content").getAsString()));
                            }
                        } else if (obj.has("role") && obj.has("content")) {
                            imported.add(Map.of("role", obj.get("role").getAsString(), "content", obj.get("content").getAsString()));
                        }
                    }
                    int added = 0;
                    for (Map<String, String> m : imported) {
                        String role = m.get("role"), content = m.get("content");
                        if (role == null || content == null) continue;
                        Message msg = new Message(role, content, Instant.now().getEpochSecond());
                        messages.add(msg);
                        addToIndex(msg, messages.size() - 1);
                        if (autoSave) appendToNdjson(msg);
                        added++;
                    }
                    System.out.println("Импортировано " + added + " сообщений.");
                    if (added > 0 && embedEnabled) { System.out.println("Перестраиваю эмбеддинги..."); EmbeddingEngine.build(); }
                } catch (Exception e) { System.out.println("Ошибка импорта: " + e.getMessage()); }
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
            case ":remember" -> {
                if (messages.size() < 2) {
                    System.out.println("Недостаточно сообщений для сжатия.");
                    return;
                }
                System.out.println("Сжимаю последние 10 сообщений в долгую память...");
                int count = Math.min(10, messages.size());
                List<Message> recent = messages.subList(messages.size() - count, messages.size());
                StringBuilder history = new StringBuilder();
                for (Message m : recent) {
                    history.append(m.role).append(": ").append(m.content).append("\n");
                }
                String compressPrompt = "Сожми следующий диалог в 1-2 предложения, сохранив суть, ключевые решения и код:\n" + history.toString();
                try {
                    String compressed = "";
                    if (currentMode == Mode.API) {
                        compressed = askAPI(compressPrompt);
                    } else if (currentMode == Mode.LOCAL) {
                        compressed = askLocal(compressPrompt);
                    }
                    System.out.println("[DEBUG] Сжатый ответ: " + compressed);
                    if (!compressed.isBlank() && !compressed.contains("Ошибка")) {
                        // Обрежем длинный сжатый текст, чтобы nomic-embed-text не подавился
                        String shortText = compressed.length() > 500 ? compressed.substring(0, 500) : compressed;
                        try {
                            HttpClient client = HttpClient.newHttpClient();
                            double[] vec = EmbeddingEngine.getEmbedding(client, shortText);
                            MemoryManager.addCompressed(shortText, vec);
                            System.out.println("Сжатый фрагмент сохранён в долгую память.");
                        } catch (Exception inner) {
                            System.out.println("Ошибка при построении эмбеддинга: " + inner.getClass().getSimpleName() + " - " + inner.getMessage());
                            inner.printStackTrace();
                        }
                    } else {
                        System.out.println("Не удалось сжать: пустой ответ или ошибка API.");
                    }
                } catch (Exception e) {
                    System.out.println("Ошибка сжатия: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    e.printStackTrace();
                }
            }
            case ":stream" -> {
                if (parts.length < 2) { System.out.println("Укажи on или off"); return; }
                streaming = parts[1].equalsIgnoreCase("on");
                System.out.println("Потоковый вывод " + (streaming ? "включен" : "выключен"));
            }
            case ":summarize" -> {
                if (messages.size() < 2) { System.out.println("Мало сообщений для сводки."); return; }
                System.out.println("Генерирую сводку последних 20 сообщений...");
                int cnt = Math.min(20, messages.size());
                List<Message> recent = messages.subList(messages.size() - cnt, messages.size());
                StringBuilder hist = new StringBuilder();
                for (Message m : recent) hist.append(m.role).append(": ").append(m.content).append("\n");
                String prompt = "Сделай краткий дайджест следующего диалога (3-5 предложений):\n" + hist.toString();
                try {
                    String summary = "";
                    if (currentMode == Mode.API) summary = askAPI(prompt);
                    else if (currentMode == Mode.LOCAL) summary = askLocal(prompt);
                    System.out.println("Сводка:\n" + summary);
                } catch (Exception e) {
                    System.out.println("Ошибка сводки: " + e.getMessage());
                }
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
    static boolean ensureOllamaRunning() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:11434/api/tags"))
                    .GET()
                    .build();
            client.send(request, HttpResponse.BodyHandlers.ofString());
            return true; // уже работает
        } catch (Exception e) {
            System.out.println("[Ollama] Не отвечает, пытаюсь запустить...");
        }

        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                // Windows: запускаем в отдельном окне, чтобы не висеть
                pb = new ProcessBuilder("cmd", "/c", "start", "ollama", "serve");
            } else {
                pb = new ProcessBuilder("ollama", "serve");
                pb.redirectErrorStream(true);
            }
            pb.start();
            // Ждём до 30 секунд, пока Ollama проснётся
            for (int i = 0; i < 30; i++) {
                Thread.sleep(1000);
                try {
                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:11434/api/tags"))
                            .GET()
                            .build();
                    client.send(request, HttpResponse.BodyHandlers.ofString());
                    System.out.println("[Ollama] Запущена и отвечает.");
                    return true;
                } catch (Exception ignore) {
                    System.out.print(".");
                }
            }
            System.out.println("\n[Ollama] Не удалось дождаться запуска.");
            return false;
        } catch (Exception ex) {
            System.out.println("[Ollama] Ошибка запуска: " + ex.getMessage());
            return false;
        }
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
            server.createContext("/ask-stream", new AskStreamHandler());

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
        static String currentTheme = null;
        static int themeLevel = 1;

        static String currentCssTheme = null;   // имя темы для CSS
        static String currentHtmlTheme = null;  // имя темы для HTML
        static String currentJsTheme = null;    // имя темы для JS

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
        List<String> activeResults = new ArrayList<>();

        // 1. Поиск в активном индексе (эмбеддинги или wordIndex)
        if (embedEnabled && !EmbeddingEngine.vectors.isEmpty()) {
            try {
                activeResults = EmbeddingEngine.search(query, topN);
            } catch (Exception e) {
                System.out.println("[Embed] Ошибка: " + e.getMessage());
            }
        }
        if (activeResults.isEmpty()) {
            // Fallback на wordIndex
            Set<String> tokens = tokenize(query);
            Map<Integer, Integer> scores = new HashMap<>();
            for (String token : tokens) {
                List<Integer> ids = wordIndex.get(token);
                if (ids != null) ids.forEach(id -> scores.merge(id, 1, Integer::sum));
            }
            activeResults = scores.entrySet().stream()
                    .sorted((e1, e2) -> {
                        int cmp = Integer.compare(e2.getValue(), e1.getValue());
                        if (cmp == 0) cmp = Long.compare(messages.get(e2.getKey()).ts, messages.get(e1.getKey()).ts);
                        return cmp;
                    })
                    .limit(topN)
                    .map(e -> messages.get(e.getKey()).content)
                    .collect(Collectors.toList());
        }

        // 2. Поиск в долгой (холодной) памяти
        List<String> coldResults = new ArrayList<>();
        try {
            if (embedEnabled) {
                HttpClient client = HttpClient.newHttpClient();
                double[] queryVec = EmbeddingEngine.getEmbedding(client, query);
                List<MemoryManager.MemoryEntry> cold = MemoryManager.search(queryVec, 3);
                for (MemoryManager.MemoryEntry e : cold) {
                    coldResults.add("[Из долгой памяти]: " + e.text);
                }
            }
        } catch (Exception e) {
            // Игнорируем ошибки холодного поиска
        }

        // 3. Объединяем: сначала активные, потом холодные
        List<String> combined = new ArrayList<>(activeResults);
        combined.addAll(coldResults);
        return combined.stream().distinct().limit(topN).collect(Collectors.toList());
    }

    static String buildPrompt(String question) throws IOException {
        List<String> ctx = searchContext(question, contextSize);
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
        static String EMBED_MODEL = "nomic-embed-text"; // по умолчанию

        // Метод для получения имени модели с учётом суффикса -16k
        static String getFullEmbedModelName(String baseModel) {
            return baseModel + "-16k";
        }
        static final Path EMBED_FILE = Path.of("data/embeddings.json");

        static int getEmbeddingDimension() {
            if (EMBED_MODEL.contains("mxbai") || EMBED_MODEL.contains("bge")) {
                return 1024;
            }
            return 768; // nomic и все остальные
        }

        static void build() throws Exception {
            vectors.clear();
            texts.clear();
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            int skipped = 0;
            for (Message msg : messages) {
                if (msg.content.isBlank()) continue;
                // Обрезаем до 500 символов, чтобы точно влезало в 2048 токенов
                String text = msg.content.length() > 500 ? msg.content.substring(0, 500) : msg.content;
                try {
                    double[] vec = getEmbedding(client, text);
                    vectors.add(vec);
                    texts.add(text);
                } catch (Exception e) {
                    skipped++;
                    System.err.println("[EMBED] Ошибка для фрагмента: " + e.getMessage());
                }
            }
            save();
            System.out.println("[EMBED] Построено " + vectors.size() + " эмбеддингов. Пропущено: " + skipped);
        }

        static double[] getEmbedding(HttpClient client, String text) throws Exception {
            ensureOllamaRunning();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:11434/api/embeddings"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(Map.of(
                            "model", EMBED_MODEL,
                            "prompt", text
                    )), StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = resp.body();
            JsonObject json = GSON.fromJson(body, JsonObject.class);

            if (json.has("error")) {
                throw new RuntimeException("Embedding error: " + json.get("error").toString());
            }

            JsonArray embeddingArray = json.getAsJsonArray("embedding");
            if (embeddingArray == null) {
                throw new RuntimeException("No embedding in response");
            }
            double[] vec = new double[embeddingArray.size()]; // динамический размер
            int i = 0;
            for (JsonElement e : embeddingArray) {
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

        static void save() throws Exception {
            DatabaseManager.clearEmbeddings();
            for (int i = 0; i < texts.size(); i++) {
                DatabaseManager.saveEmbedding(texts.get(i), vectors.get(i));
            }
        }

        static void load() throws Exception {
            vectors.clear();
            texts.clear();
            List<DatabaseManager.EmbeddingEntry> entries = DatabaseManager.loadEmbeddings();
            for (DatabaseManager.EmbeddingEntry entry : entries) {
                vectors.add(entry.vec);
                texts.add(entry.content);
            }
        }
    }
    static class WebSearchEngine {
        static String search(String query) throws Exception {
            HttpClient client = HttpClient.newHttpClient();
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.duckduckgo.com/?q=" + encoded + "&format=json&no_html=1"))
                    .header("User-Agent", "NeuralFloppy/1.9")
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject json = GSON.fromJson(resp.body(), JsonObject.class);
            if (json.has("AbstractText") && !json.get("AbstractText").isJsonNull()) {
                return json.get("AbstractText").getAsString();
            } else if (json.has("RelatedTopics") && json.getAsJsonArray("RelatedTopics").size() > 0) {
                return json.getAsJsonArray("RelatedTopics").get(0).getAsJsonObject().get("Text").getAsString();
            }
            return "Ничего не найдено.";
        }
    }
    static class StorageManager {
        private static String currentMode = "default";

        public static void setMode(String mode) {
            currentMode = mode;
        }

        public static String getMode() {
            return currentMode;
        }
    }
    static class Message {
        String role, content;
        long ts;
        Message(String r, String c, long t) { role = r; content = c; ts = t; }
    }
    // ================== МЕТОДЫ ДЛЯ GameAPI ==================

    public boolean isGameModeEnabled() {
        return gameModeEnabled;
    }

    public String getDefaultColumn() {
        return defaultColumn;
    }

    public boolean executeCommand(String cmd) {
        // Безопасное выполнение команд из API
        // Запрещаем опасные команды
        if (cmd.startsWith(":exit") || cmd.startsWith(":memory delete all")) {
            return false;
        }
        try {
            handleCommand(cmd);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void saveToColumn(String column, String data, String format) {
        try {
            String storageMode = StorageManager.getMode();
            String fileName = "chat_" + column + ".ndjson";
            Message msg = new Message("SYSTEM", data, Instant.now().getEpochSecond());
            String line = GSON.toJson(msg) + "\n";
            Files.writeString(Path.of(fileName), line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            if (storageMode.equals("long") || storageMode.equals("archive")) {
                MemoryManager.addCompressed(data, null);
            }
        } catch (Exception e) {
            System.err.println("[GameAPI] Ошибка сохранения в колонку " + column + ": " + e.getMessage());
        }
    }

    public String askLLM(String query, JsonObject state) {
        try {
            if (currentMode == Mode.API) {
                return askAPI(query);
            } else if (currentMode == Mode.LOCAL) {
                return askLocal(query);
            }
        } catch (Exception e) {
            System.err.println("[GameAPI] Ошибка вызова LLM: " + e.getMessage());
        }
        return "LLM not available";
    }

    public String analyzeWithLLM(String query, JsonObject state) {
        try {
            String prompt = "Проанализируй следующие данные и опиши их в стиле NeuralFloppy:\n" + state.toString();
            if (currentMode == Mode.API) {
                return askAPI(prompt);
            } else if (currentMode == Mode.LOCAL) {
                return askLocal(prompt);
            }
        } catch (Exception e) {
            System.err.println("[GameAPI] Ошибка анализа LLM: " + e.getMessage());
        }
        return "LLM analysis not available";
    }
}