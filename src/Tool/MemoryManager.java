package Tool;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class MemoryManager {
    private static final String MEMORY_FILE = "data/long_term_memory.json";
    private static final Gson GSON = new Gson();

    public static List<MemoryEntry> load() throws IOException {
        if (!Files.exists(Path.of(MEMORY_FILE))) return new ArrayList<>();
        String raw = Files.readString(Path.of(MEMORY_FILE));
        return GSON.fromJson(raw, new TypeToken<List<MemoryEntry>>(){}.getType());
    }

    public static void save(List<MemoryEntry> entries) throws IOException {
        Files.writeString(Path.of(MEMORY_FILE), GSON.toJson(entries));
    }

    public static void addCompressed(String text, double[] embedding) throws IOException {
        List<MemoryEntry> entries = load();
        entries.add(new MemoryEntry(
                UUID.randomUUID().toString(),
                text,
                embedding,
                System.currentTimeMillis(),
                0
        ));
        save(entries);
    }
    // Текстовый поиск (без эмбеддингов)
    public static List<MemoryEntry> searchByText(String query, int topN) throws IOException {
        List<MemoryEntry> result = new ArrayList<>();
        List<MemoryEntry> all = load();
        for (MemoryEntry e : all) {
            if (e.text.toLowerCase().contains(query.toLowerCase())) {
                result.add(e);
            }
        }
        // Сортируем по времени (новые первыми)
        result.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        if (result.size() > topN) {
            result = result.subList(0, topN);
        }
        return result;
    }

    // Миграция из JSON в SQLite (если используется SQLite, иначе просто заглушка)
    public static void migrateFromJson() throws IOException {
        // Здесь будет код миграции в SQLite, если нужно. а пока что :D
        // Пока оставим заглушку, чтобы не падало.
        System.out.println("Миграция в SQLite не настроена. Используй JSON-память.");
    }

    // Статус памяти
    public static void printStatus() throws IOException {
        List<MemoryEntry> all = load();
        System.out.println("Записей в долгой памяти: " + all.size());
        if (!all.isEmpty()) {
            MemoryEntry last = all.get(all.size() - 1);
            System.out.println("Последняя запись: " + new java.util.Date(last.timestamp));
        }
    }

    // Удаление старых записей
    public static void purgeOld(int days) throws IOException {
        List<MemoryEntry> all = load();
        long cutoff = System.currentTimeMillis() - (long)days * 24 * 60 * 60 * 1000;
        int before = all.size();
        all.removeIf(e -> e.timestamp < cutoff);
        save(all);
        System.out.println("Удалено " + (before - all.size()) + " старых записей.");
    }

    public static List<MemoryEntry> search(double[] queryVec, int topN) throws IOException {
        List<MemoryEntry> entries = load();
        if (entries.isEmpty()) return List.of();
        List<Map.Entry<MemoryEntry, Double>> scored = new ArrayList<>();
        for (MemoryEntry e : entries) {
            double sim = cosineSimilarity(queryVec, e.embedding);
            scored.add(Map.entry(e, sim));
        }
        scored.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<MemoryEntry> result = new ArrayList<>();
        for (int i = 0; i < Math.min(topN, scored.size()); i++) {
            MemoryEntry e = scored.get(i).getKey();
            e.accessCount++;
            result.add(e);
        }
        save(entries);
        return result;
    }
    private static double cosineSimilarity(double[] a, double[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // Запись в памяти
    public static class MemoryEntry {
        public String id;
        public String text;
        public double[] embedding;
        public long timestamp;
        public int accessCount;

        public MemoryEntry(String id, String text, double[] embedding, long timestamp, int accessCount) {
            this.id = id;
            this.text = text;
            this.embedding = embedding;
            this.timestamp = timestamp;
            this.accessCount = accessCount;
        }
    }
}