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
}