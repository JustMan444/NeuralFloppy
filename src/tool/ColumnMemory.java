package tool;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * ColumnMemory — изолированная память для колонки GameAPI.
 * Хранит NDJSON-файл колонки, строит для неё эмбеддинги и ищет.
 * Не трогает глобальную память ядра.
 */
public class ColumnMemory {

    private static final Gson GSON = new Gson();

    private final String columnName;
    private final String ndjsonFile;
    private final String vecTable;

    public ColumnMemory(String columnName) {
        this.columnName = columnName;
        this.ndjsonFile = "chat_" + columnName + ".ndjson";
        this.vecTable = "vec_items_" + columnName;
    }

    public String getColumnName() { return columnName; }
    public String getNdjsonFile() { return ndjsonFile; }

    // ================== ЗАПИСЬ ==================

    public void append(NeuralFloppyTool2_0_DT_snapchot2.Message msg) {
        try {
            String line = GSON.toJson(msg) + "\n";
            Files.writeString(Path.of(ndjsonFile), line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (Exception e) {
            System.out.println("[COLUMN] Ошибка записи в " + ndjsonFile + ": " + e.getMessage());
        }
    }

    // ================== ЧТЕНИЕ ==================

    public List<NeuralFloppyTool2_0_DT_snapchot2.Message> loadAll() {
        List<NeuralFloppyTool2_0_DT_snapchot2.Message> list = new ArrayList<>();
        Path path = Path.of(ndjsonFile);
        if (!Files.exists(path)) return list;
        try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            lines.forEach(line -> {
                if (line.isBlank()) return;
                try {
                    JsonObject obj = GSON.fromJson(line, JsonObject.class);
                    list.add(new NeuralFloppyTool2_0_DT_snapchot2.Message(
                            obj.get("role").getAsString(),
                            obj.get("content").getAsString(),
                            obj.has("ts") ? obj.get("ts").getAsLong() : 0
                    ));
                } catch (Exception e) { /* skip битую строку */ }
            });
        } catch (Exception e) {
            System.out.println("[COLUMN] Ошибка чтения " + ndjsonFile + ": " + e.getMessage());
        }
        return list;
    }


    // ================== ПОИСК ==================

    /**
     * Ищет в колонке: сначала через sqlite-vec, потом fallback на слова.
     */
    public List<String> search(String query, int topN, String searchEngine) {
        // 1. Векторный поиск
        if (("vec".equals(searchEngine) || "hybrid".equals(searchEngine)) && VecEngine.isAvailable()) {
            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                double[] queryVec = NeuralFloppyTool2_0_DT_snapchot2.EmbeddingEngine.getEmbedding(client, query);
                List<Integer> ids = VecEngine.search(vecTable, queryVec, topN);
                if (!ids.isEmpty()) {
                    List<NeuralFloppyTool2_0_DT_snapchot2.Message> all = loadAll();
                    List<String> results = new ArrayList<>();
                    for (int id : ids) {
                        if (id >= 0 && id < all.size()) {
                            results.add(all.get(id).content);
                        }
                    }
                    if (!results.isEmpty()) return results;
                }
            } catch (Exception e) {
                System.out.println("[COLUMN] vec-поиск по '" + columnName + "' упал: " + e.getMessage());
                // fallback ниже
            }
        }

        // 2. Fallback: поиск по словам в NDJSON
        List<NeuralFloppyTool2_0_DT_snapchot2.Message> all = loadAll();
        if (all.isEmpty()) return List.of();

        Set<String> tokens = tokenize(query);
        Map<Integer, Integer> scores = new HashMap<>();
        for (int i = 0; i < all.size(); i++) {
            String content = all.get(i).content.toLowerCase();
            for (String token : tokens) {
                if (content.contains(token)) {
                    scores.merge(i, 1, Integer::sum);
                }
            }
        }
        return scores.entrySet().stream()
                .sorted((e1, e2) -> {
                    int cmp = Integer.compare(e2.getValue(), e1.getValue());
                    if (cmp == 0) {
                        // Свежие — выше
                        cmp = Long.compare(all.get(e2.getKey()).ts, all.get(e1.getKey()).ts);
                    }
                    return cmp;
                })
                .limit(topN)
                .map(e -> all.get(e.getKey()).content)
                .collect(Collectors.toList());
    }

    private Set<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase().split("[^а-яa-z0-9]+"))
                .filter(w -> w.length() > 1)
                .collect(Collectors.toSet());
    }

    // ================== ЭМБЕДДИНГИ ==================

    /**
     * Строит эмбеддинги для всех сообщений колонки и пишет в vec-таблицу.
     */
    public void buildEmbeddings(int dimension) {
        List<NeuralFloppyTool2_0_DT_snapchot2.Message> all = loadAll();
        if (all.isEmpty()) {
            System.out.println("[COLUMN] Колонка '" + columnName + "' пуста.");
            return;
        }

        if (VecEngine.isAvailable()) {
            VecEngine.rebuildTable(vecTable, dimension);
        }

        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();

        int i = 0, skipped = 0;
        for (NeuralFloppyTool2_0_DT_snapchot2.Message msg : all) {
            if (msg.content.isBlank()) continue;
            String text = msg.content.length() > 500 ? msg.content.substring(0, 500) : msg.content;
            try {
                double[] vec = NeuralFloppyTool2_0_DT_snapchot2.EmbeddingEngine.getEmbedding(client, text);
                if (VecEngine.isAvailable()) {
                    VecEngine.insert(vecTable, i, vec);
                }
                i++;
            } catch (Exception e) {
                skipped++;
                System.err.println("[COLUMN] Ошибка эмбеддинга: " + e.getMessage());
            }
        }
        System.out.println("[COLUMN] Построено " + i + " эмбеддингов для '" + columnName + "'. Пропущено: " + skipped);
    }
    // ================== ЭПИЗОДЫ ==================

public static List<Episode> loadEpisodes(String column, int limit) throws Exception {
    List<Episode> list = new ArrayList<>();
    Path path = Path.of("chat_" + column + ".ndjson");
    if (!Files.exists(path)) return list;

    List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
    int start = Math.max(0, lines.size() - limit);
    for (int i = start; i < lines.size(); i++) {
        String line = lines.get(i);
        if (line.isBlank()) continue;
        try {
            JsonObject obj = GSON.fromJson(line, JsonObject.class);

            // Эпизод = должен содержать state И action (минимум)
            if (!obj.has("state") || !obj.has("action")) {
                continue; // не эпизод, пропускаем
            }

            String state = obj.get("state").toString();
            String action = obj.get("action").getAsString();
            double reward = obj.has("reward") ? obj.get("reward").getAsDouble() : 0.0;
            String nextState = obj.has("next_state") ? obj.get("next_state").toString() : "{}";
            long ts = obj.has("ts") ? obj.get("ts").getAsLong() : 0;
            list.add(new Episode(state, action, reward, nextState, ts));
        } catch (Exception e) { /* skip битую строку */ }
    }
    return list;
}

    public static void saveEpisode(String column, Episode episode) throws Exception {
        Path path = Path.of("chat_" + column + ".ndjson");
        JsonObject obj = new JsonObject();
        obj.add("state", GSON.fromJson(episode.state(), JsonObject.class));
        obj.addProperty("action", episode.action());
        obj.addProperty("reward", episode.reward());
        if (episode.nextState() != null) {
            obj.add("next_state", GSON.fromJson(episode.nextState(), JsonObject.class));
        }
        obj.addProperty("ts", episode.ts());
        String line = GSON.toJson(obj) + "\n";
        Files.writeString(path, line, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}