package tool;

import java.util.*;

public class WarmMemory {

    /** Возвращает последние сообщения, укладывающиеся в maxTokens. */
    /**
     * Возвращает последние сообщения с пакетным вытеснением.
     * Терпит переполнение до EVICT_TRIGGER, затем сносит EVICT_PERCENT старых.
     */
    public static List<NeuralFloppyTool2_0_DT_snapchot2.Message> getWarm(int maxTokens) {
        List<NeuralFloppyTool2_0_DT_snapchot2.Message> all =
                NeuralFloppyTool2_0_DT_snapchot2.getMessages();

        if (all.isEmpty()) return new ArrayList<>();

        // 1. Считаем общий объем всех сообщений
        int totalTokens = 0;
        int[] msgTokens = new int[all.size()];
        for (int i = 0; i < all.size(); i++) {
            msgTokens[i] = TokenCounter.count(all.get(i).content) + 5;
            totalTokens += msgTokens[i];
        }

        // 2. Если влезаем в лимит — отдаем всё (кэш стабилен)
        if (totalTokens <= maxTokens) {
            return new ArrayList<>(all);
        }

        // 3. Если переполнение небольшое (до +20%), тоже терпим
        int triggerLimit = (int)(maxTokens * 1.2);
        if (totalTokens <= triggerLimit) {
            // Даем [UAZ] разобраться с этим через API ошибку,
            // либо просто отдаем всё (надеемся, модель проглотит)
            return new ArrayList<>(all);
        }

        // 4. ПЕРЕПОЛНЕНИЕ! Сносим 70% старых, оставляем 30% новых.
        System.err.println("[WARM] Переполнение (" + totalTokens + " > " + triggerLimit + "). Сносим 70% старых.");

        int targetTokens = (int)(maxTokens * 0.3); // Оставляем 30% от лимита
        List<NeuralFloppyTool2_0_DT_snapchot2.Message> warm = new ArrayList<>();
        int currentTokens = 0;

        // Идем с конца, набираем новые сообщения
        for (int i = all.size() - 1; i >= 0; i--) {
            if (currentTokens + msgTokens[i] > targetTokens && !warm.isEmpty()) {
                break;
            }
            warm.add(0, all.get(i));
            currentTokens += msgTokens[i];
        }

        return warm;
    }
    /**
     * Собирает массив messages для API (OpenRouter и Ollama /api/chat).
     */
    public static List<Map<String, String>> buildMessages(
            String question, List<String> cold, int maxTokens) {

        List<Map<String, String>> msgs = new ArrayList<>();

        // 1. SYSTEM — стабильный префикс
        msgs.add(Map.of(
                "role", "system",
                "content", HotMemoryManager.getPrefix()
        ));

        // 2. WARM — последние N токенов
        List<NeuralFloppyTool2_0_DT_snapchot2.Message> warm = getWarm(maxTokens);

        // Дедупликация: собираем тексты warm, чтобы не дублировать их в cold
        Set<String> warmTexts = new HashSet<>();
        for (var m : warm) {
            warmTexts.add(m.content);
        }

        for (var m : warm) {
            msgs.add(Map.of(
                    "role", m.role.toLowerCase(),
                    "content", m.content
            ));
        }

        // 3. COLD + вопрос — в конец
        List<String> coldFiltered = new ArrayList<>();
        for (String c : cold) {
            if (!warmTexts.contains(c)) {
                coldFiltered.add(c);
            }
        }

        String userContent = question;
        if (!coldFiltered.isEmpty()) {
            userContent = "[Контекст из прошлого]:\n"
                    + String.join("\n---\n", coldFiltered)
                    + "\n\n" + question;
        }

        msgs.add(Map.of("role", "user", "content", userContent));

        return msgs;
    }
}