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
    static void buildIndex() throws IOException { /* ... код без изменений ... */ }
    static void addToIndex(Message msg, int idx) { /* ... */ }
    static void appendToNdjson(Message msg) throws IOException { /* ... */ }
    static List<String> searchContext(String query, int topN) { /* ... */ }
    static Set<String> tokenize(String text) { /* ... */ }

    static class Message {
        String role, content;
        long ts;
        Message(String r, String c, long t) { role = r; content = c; ts = t; }
    }
}