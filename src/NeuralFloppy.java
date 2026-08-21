import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class NeuralFloppy {

    private static final String PERSONA_FILE = "perso1na.txt";
    private static final String NDJSON_FILE = "data/chat.ndjson";
    private static final String OUTPUT_PROMPT_FILE = "last_prompt.txt";
    private static final Gson GSON = new Gson();

    private static List<Message> messages = new ArrayList<>();
    private static Map<String, List<Integer>> wordIndex = new HashMap<>();

    public static void main(String[] args) throws Exception {
        buildIndex();
        System.out.println("NeuralFloppy v1.2 CLI готов. " + messages.size() + " сообщений в памяти.");
        System.out.println("Введи вопрос — получишь промт. Копируй его в DeepSeek веб-чат.");
        System.out.println("Команды: :save (сохранить промт в файл), :exit");

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("\nТы: ");
            String q = reader.readLine();
            if (q == null || q.isBlank()) continue;
            if (q.equalsIgnoreCase(":exit")) break;
            if (q.equalsIgnoreCase(":save")) {
                System.out.println("Промт сохранён в " + OUTPUT_PROMPT_FILE);
                continue;
            }
            String prompt = buildPrompt(q);
            System.out.println("\n===== СКОПИРУЙ ЭТО В DEEPSEEK =====");
            System.out.println(prompt);
            System.out.println("====================================");
            if (q.equalsIgnoreCase(":save")) {
                Files.writeString(Path.of(OUTPUT_PROMPT_FILE), prompt);
            }
        }
    }

    // ================== ИНДЕКСАЦИЯ ==================
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

    // ================== ПОИСК ==================
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

    // ================== СБОРКА ПРОМТА ==================
    static String buildPrompt(String question) throws IOException {
        String persona = Files.readString(Path.of(PERSONA_FILE));
        List<String> ctx = searchContext(question, 10);
        String context = String.join("\n---\n", ctx);
        return String.format("""
            %s

            Вот история твоего общения с учеником (используй для контекста):
            %s

            Ученик спросил: %s
            Ответь как тот самый наставник:""", persona, context, question);
    }

    // ================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==================
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