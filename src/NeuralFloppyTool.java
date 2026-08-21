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

public class NeuralFloppyTool {
    private static final String PERSONA_FILE = "persona.txt";
    private static final String NDJSON_FILE = "data/chat.ndjson";
    private static final String ARCHIVE_DIR = "archive";
    private static final String API_KEY = "sk-or-v1-..."; // <-- ВСТАВЬ СВОЙ КЛЮЧ
    private static final String OLLAMA_URL = "http://localhost:11434/v1/chat/completions";
    private static final Gson GSON = new Gson();

    private static List<Message> messages = new ArrayList<>();
    private static Map<String, List<Integer>> wordIndex = new HashMap<>();
    private static String currentPersona = "";

    enum Mode { API, LOCAL, MANUAL }
    private static Mode currentMode = Mode.API;
    private static String currentModel = "openrouter/free";
    private static boolean autoSave = true;

    public static void main(String[] args) throws Exception {
        currentPersona = Files.readString(Path.of(PERSONA_FILE));
        buildIndex();

        System.out.println("NeuralFloppy TOOL v1.1 готов. " + messages.size() + " сообщений в индексе.");
        System.out.println("Режим: " + currentMode + " | Модель: " + currentModel + " | Автосохранение: " + (autoSave ? "вкл" : "выкл"));
        System.out.println("Введи :help для списка команд.\n");

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        while (true) {
            System.out.print("Ты: ");
            String q = reader.readLine();
            if (q == null || q.isBlank()) continue;

            // Проверка на кракозябры
            if (!isValidInput(q)) {
                System.out.println("Учитель: Бро, кодировка сломалась. Повтори вопрос.");
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
            switch (currentMode) {
                case API -> answer = askAPI(q);
                case LOCAL -> answer = askLocal(q);
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
                System.out.println("\nУчитель: " + answer);
                Message assistantMsg = new Message("ASSISTANT", answer, Instant.now().getEpochSecond());
                messages.add(assistantMsg);
                if (autoSave) appendToNdjson(assistantMsg);
                addToIndex(assistantMsg, messages.size() - 1);
            }
            System.out.println();
        }
    }

    // Проверка на битые символы
    static boolean isValidInput(String text) {
        if (text == null || text.isBlank()) return false;
        String cleaned = text.replace("?", "").replace("�", "").trim();
        if (cleaned.isEmpty()) return false;
        long readable = text.chars().filter(c -> Character.isLetterOrDigit(c) || Character.isWhitespace(c)).count();
        return (double) readable / text.length() > 0.2;
    }

    // Обработчик команд
    static void handleCommand(String cmd) throws IOException {
        String[] parts = cmd.split("\\s+");
        switch (parts[0]) {
            case ":help" -> System.out.println("""
                Команды:
                :mode api|local|manual - переключить режим
                :model <имя>           - сменить модель (для api/local)
                :persona               - показать текущую персону
                :autosave on|off       - вкл/выкл автосохранение
                :save                  - сохранить текущую сессию в архив
                :exit                  - выход
                """);
            case ":mode" -> {
                if (parts.length < 2) { System.out.println("Укажи режим: api, local, manual"); return; }
                switch (parts[1].toLowerCase()) {
                    case "api" -> currentMode = Mode.API;
                    case "local" -> currentMode = Mode.LOCAL;
                    case "manual" -> currentMode = Mode.MANUAL;
                    default -> System.out.println("Неизвестный режим.");
                }
                System.out.println("Режим переключён на " + currentMode);
            }
            case ":model" -> {
                if (parts.length < 2) { System.out.println("Укажи имя модели."); return; }
                currentModel = parts[1];
                System.out.println("Модель сменена на " + currentModel);
            }
            case ":persona" -> System.out.println("Текущая персона:\n" + currentPersona);
            case ":autosave" -> {
                if (parts.length < 2) { System.out.println("Укажи on или off"); return; }
                autoSave = parts[1].equalsIgnoreCase("on");
                System.out.println("Автосохранение " + (autoSave ? "включено" : "выключено"));
            }
            case ":save" -> saveArchive();
            case ":exit" -> System.exit(0);
            default -> System.out.println("Неизвестная команда. :help для списка.");
        }
    }

    // API-режим (OpenRouter)
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
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(Map.of(
                        "model", currentModel,
                        "messages", List.of(Map.of("role", "user", "content", prompt)),
                        "temperature", 0.7,
                        "max_tokens", 8192
                ))))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.body();
        JsonObject json = GSON.fromJson(body, JsonObject.class);
        if (json.has("choices") && json.getAsJsonArray("choices").size() > 0) {
            return json.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();
        }
        return "Ошибка API: " + body;
    }

    // Локальный режим (Ollama)
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
                .uri(URI.create(OLLAMA_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(Map.of(
                        "model", currentModel,
                        "messages", List.of(Map.of("role", "user", "content", prompt)),
                        "temperature", 0.7,
                        "max_tokens", 8192,
                        "num_ctx", 32768
                ))))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.body();
        JsonObject json = GSON.fromJson(body, JsonObject.class);
        if (json.has("choices") && json.getAsJsonArray("choices").size() > 0) {
            return json.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();
        }
        return "Ошибка Ollama: " + body;
    }

    // Индексация, сохранение, поиск
    static void buildIndex() throws IOException {
        messages.clear();
        wordIndex.clear();
        if (!Files.exists(Path.of(NDJSON_FILE))) return;
        try (Stream<String> lines = Files.lines(Path.of(NDJSON_FILE))) {
            lines.forEach(line -> {
                JsonObject obj = GSON.fromJson(line, JsonObject.class);
                Message msg = new Message(
                        obj.get("role").getAsString(),
                        obj.get("content").getAsString(),
                        obj.has("ts") ? obj.get("ts").getAsLong() : 0
                );
                messages.add(msg);
                int idx = messages.size() - 1;
                addToIndex(msg, idx);
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

    static class Message {
        String role, content;
        long ts;
        Message(String r, String c, long t) { role = r; content = c; ts = t; }
    }
}