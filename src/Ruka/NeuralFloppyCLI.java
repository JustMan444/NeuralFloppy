import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.*;

public class NeuralFloppyCLI {
    private static final String PERSONA_FILE = "persona.txt";
    private static final String NDJSON_FILE = "data/chat.ndjson";
    private static final String ARCHIVE_DIR = "archive";
    private static final Gson GSON = new Gson();

    private static List<Message> messages = new ArrayList<>();
    private static Map<String, List<Integer>> wordIndex = new HashMap<>();
    private static String currentPersona = "";

    public static void main(String[] args) throws Exception {
        // Загружаем персону и историю
        currentPersona = Files.readString(Path.of(PERSONA_FILE));
        buildIndex();

        System.out.println("NeuralFloppy v1.1 CLI (с автосохранением) готов. " + messages.size() + " сообщений в индексе.");
        System.out.println("Вводи вопрос или команду (:help, :архив, :персона, :выход)\n");

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        while (true) {
            System.out.print("Ты: ");
            String q = reader.readLine();
            if (q == null || q.isBlank()) continue;

            // Обработка команд
            if (q.startsWith(":")) {
                if (q.equals(":выход")) break;
                else if (q.equals(":help")) showHelp();
                else if (q.equals(":персона")) showPersona();
                else if (q.equals(":архив")) saveArchive();
                else System.out.println("Команда не найдена. Введи :help");
                continue;
            }

            // 1. Сохраняем вопрос пользователя
            Message userMsg = new Message("USER", q, Instant.now().getEpochSecond());
            messages.add(userMsg);
            appendToNdjson(userMsg);
            addToIndex(userMsg, messages.size() - 1);

            // 2. Генерируем промт
            String prompt = buildPrompt(q);
            System.out.println("\n===== СКОПИРУЙ ЭТО В DEEPSEEK ИЛИ OLLAMA =====");
            System.out.println(prompt);
            System.out.println("================================================");

            // 3. Ждём ответ от модели (ручной ввод)
            System.out.print("Вставь ответ модели (или 'пропустить'): ");
            String answer = reader.readLine();
            if (answer != null && !answer.equalsIgnoreCase("пропустить") && !answer.isBlank()) {
                Message assistantMsg = new Message("ASSISTANT", answer, Instant.now().getEpochSecond());
                messages.add(assistantMsg);
                appendToNdjson(assistantMsg);
                addToIndex(assistantMsg, messages.size() - 1);
                System.out.println("Ответ сохранён.");
            } else {
                System.out.println("Ответ пропущен.");
            }
            System.out.println();
        }
    }

    static void showHelp() {
        System.out.println("""
            Доступные команды:
            :help       - показать это сообщение
            :персона    - показать текущую персону
            :архив      - сохранить всю историю в отдельный файл (archive/сессия_дата.json)
            :выход      - выйти
            """);
    }

    static void showPersona() {
        System.out.println("=== ТЕКУЩАЯ ПЕРСОНА ===");
        System.out.println(currentPersona);
        System.out.println("========================");
    }

    static void saveArchive() throws IOException {
        Files.createDirectories(Path.of(ARCHIVE_DIR));
        String filename = "archive/session_" + Instant.now().toString().replace(":", "-") + ".json";
        // Сохраняем все сообщения в JSON-файл (массив)
        String json = GSON.toJson(messages);
        Files.writeString(Path.of(filename), json);
        System.out.println("Архив сохранён: " + filename);
    }

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
        String persona = currentPersona;
        List<String> ctx = searchContext(question, 10);
        String context = String.join("\n---\n", ctx);
        return String.format("""
            %s

            Вот история твоего общения с учеником (используй для контекста):
            %s

            Ученик спросил: %s
            Ответь как тот самый наставник:""", persona, context, question);
    }

    static Set<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase().split("[^а-яa-z0-9]+"))
                .filter(w -> w.length() > 1)
                .collect(Collectors.toSet());
    }

    static class Message {
        String role;
        String content;
        long ts;
        Message(String r, String c, long t) { role = r; content = c; ts = t; }
    }
}