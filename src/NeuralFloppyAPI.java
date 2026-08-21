import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class NeuralFloppyAPI {

    private static final String API_KEY = "sk-or-v1-...";
    private static final String PERSONA_FILE = "perso1na.txt";
    private static final String NDJSON_FILE = "data/c2hat.ndjson";
    private static final String PROVIDERS_FILE = "providers.json";
    private static final Gson GSON = new Gson();

    private static List<Message> messages = new ArrayList<>();
    private static Map<String, List<Integer>> wordIndex = new HashMap<>();
    private static ProviderConfig providerConfig;

    public static void main(String[] args) throws Exception {
        buildIndex();
        loadProviderConfig();
        System.out.println("NeuralFloppy API готов. " + messages.size() + " сообщений.");
        System.out.println("Активная модель: " + providerConfig.active);

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("\nТы: ");
            String q = reader.readLine();
            if (q == null || q.isBlank()) continue;
            if (q.equalsIgnoreCase("выход")) break;
            System.out.println("\nУчитель: " + askTeacher(q));
        }
    }

    static void loadProviderConfig() throws IOException {
        String json = Files.readString(Path.of(PROVIDERS_FILE));
        providerConfig = GSON.fromJson(json, ProviderConfig.class);
    }

    static class ProviderConfig {
        String active;
        List<String> catalog;
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
                .uri(URI.create("https://openrouter.ai/api/v1/chat/completions"))
                .header("Authorization", "Bearer " + API_KEY)
                .header("HTTP-Referer", "http://localhost")
                .header("X-Title", "NeuralFloppy")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(Map.of(
                        "model", providerConfig.active,
                        "messages", List.of(Map.of("role", "user", "content", prompt)),
                        "temperature", 0.7,
                        "max_tokens", 2000
                ))))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();
        System.out.println("[DEBUG] " + responseBody);

        JsonObject respJson = GSON.fromJson(responseBody, JsonObject.class);
        if (respJson.has("error")) {
            String msg = respJson.getAsJsonObject("error").get("message").getAsString();
            return "Ошибка: " + msg;
        }
        if (respJson.has("choices") && respJson.getAsJsonArray("choices").size() > 0) {
            return respJson.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();
        }
        return "Пустой ответ. Сырое: " + responseBody;
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