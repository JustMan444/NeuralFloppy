import com.google.gson.JsonElement;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class NeuralFloppyLocal {

    private static final String OLLAMA_URL = "http://localhost:11434/v1/chat/completions";
    private static final String MODEL = "deepseek-r1-32k"; // или mistral, gemma2:2b, qwen2.5:0.5b
    private static final String PERSONA_FILE = "perso1na.txt";
    private static final String NDJSON_FILE = "data/c2hat.ndjson";
    private static final Gson GSON = new Gson();

    private static List<Message> messages = new ArrayList<>();
    private static Map<String, List<Integer>> wordIndex = new HashMap<>();

    public static void main(String[] args) throws Exception {
        buildIndex();
        System.out.println("NeuralFloppy Local (Ollama) готов. " + messages.size() + " сообщений.");
        System.out.println("Модель: " + MODEL);

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("\nТы: ");
            String q = reader.readLine();
            if (q == null || q.isBlank()) continue;
            if (q.equalsIgnoreCase("выход")) break;
            System.out.println("\nУчитель: " + askTeacher(q));
        }
    }

    static void buildIndex() throws IOException {
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
                if ("ASSISTANT".equals(msg.role)) {
                    for (String word : tokenize(msg.content)) {
                        wordIndex.computeIfAbsent(word, k -> new ArrayList<>()).add(idx);
                    }
                }
            });
        }
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
                .uri(URI.create(OLLAMA_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(Map.of(
                        "model", MODEL,
                        "messages", List.of(Map.of("role", "user", "content", prompt)),
                        "temperature", 0.7,
                        "max_tokens", 8192
                ))))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();
        System.out.println("[DEBUG] Ollama response: " + responseBody);

        JsonObject respJson = GSON.fromJson(responseBody, JsonObject.class);
        if (respJson.has("error")) {
            JsonElement err = respJson.get("error");
            if (err.isJsonPrimitive()) {
                return "Ошибка Ollama: " + err.getAsString();
            } else {
                return "Ошибка Ollama: " + err.toString();
            }
        }
        if (respJson.has("choices") && respJson.getAsJsonArray("choices").size() > 0) {
            JsonObject msg = respJson.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message");

            String raw = msg.get("content").getAsString();
            // Убираем блоки если они вдруг попали в контент
            raw = raw.replaceAll("(?s)сюда.*?", "").trim();
            // Если ответ всё ещё содержит мусор, вытаскиваем только то, что после последнего ответа
            if (raw.contains("ответ:")) {
                raw = raw.substring(raw.lastIndexOf("ответ:") + 6).trim();
            }
            return raw.isEmpty() ? raw : raw;
        }
        return "Пустой ответ от Ollama. Сырое: " + responseBody;
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