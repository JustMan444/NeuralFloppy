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

public class NeuralFloppyAPI_CLI {
    private static final String API_KEY = "ТВОЙ_OPENROUTER_КЛЮЧ"; // <-- ВСТАВЬ СЮДА
    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String MODEL = "openrouter/free"; // Или deepseek/deepseek-r1:free
    private static final String PERSONA_FILE = "persona.txt";
    private static final String NDJSON_FILE = "data/chat.ndjson";
    private static final Gson GSON = new Gson();

    private static List<Message> messages = new ArrayList<>();
    private static Map<String, List<Integer>> wordIndex = new HashMap<>();

    public static void main(String[] args) throws Exception {
        buildIndex();
        System.out.println("NeuralFloppy API готов. " + messages.size() + " сообщений в памяти.");
        System.out.println("Модель: " + MODEL);
        System.out.println("Вводи вопрос (или :выход)\n");

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        while (true) {
            System.out.print("Ты: ");
            String q = reader.readLine();
            if (q == null || q.isBlank()) continue;
            if (q.equals(":выход")) break;

            // Сохраняем вопрос
            Message userMsg = new Message("USER", q, Instant.now().getEpochSecond());
            messages.add(userMsg);
            appendToNdjson(userMsg);
            addToIndex(userMsg, messages.size() - 1);

            // Получаем ответ от API
            String answer = askTeacher(q);
            System.out.println("\nУчитель: " + answer);

            // Сохраняем ответ
            Message assistantMsg = new Message("ASSISTANT", answer, Instant.now().getEpochSecond());
            messages.add(assistantMsg);
            appendToNdjson(assistantMsg);
            addToIndex(assistantMsg, messages.size() - 1);
            System.out.println();
        }
    }

    static String askTeacher(String question) throws Exception {
        String persona = Files.readString(Path.of(PERSONA_FILE));
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
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + API_KEY)
                .header("HTTP-Referer", "http://localhost")
                .header("X-Title", "NeuralFloppy")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(Map.of(
                        "model", MODEL,
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

// --- Вспомогательные методы (индекс, сохранение, поиск) ---

    static void buildIndex() throws IOException {
        messages.clear();
        wordIndex.clear();
        Path filePath = Path.of(NDJSON_FILE);
        if (!Files.exists(filePath)) {
            System.out.println("Файл " + NDJSON_FILE + " не найден.");
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
                } catch (Exception e) {
                    System.err.println("Ошибка чтения строки NDJSON: " + e.getMessage());
                }
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
            if (ids != null) {
                for (int id : ids) {
                    scores.merge(id, 1, Integer::sum);
                }
            }
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