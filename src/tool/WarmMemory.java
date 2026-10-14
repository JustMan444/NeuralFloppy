package tool;


import java.io.IOException;
import java.util.*;

public class WarmMemory {

    /** Возвращает последние сообщения, укладывающиеся в maxTokens. */
    public static List<NeuralFloppyTool2_0_DT_snapchot2.Message> getWarm(int maxTokens)  {
        List<NeuralFloppyTool2_0_DT_snapchot2.Message> all =
                NeuralFloppyTool2_0_DT_snapchot2.getMessages();

        List<NeuralFloppyTool2_0_DT_snapchot2.Message> warm = new ArrayList<>();
        int total = 0;

        for (int i = all.size() - 1; i >= 0; i--) {
            var m = all.get(i);
            int tokens = TokenCounter.count(m.content) + 5;
            if (total + tokens > maxTokens && !warm.isEmpty()) break;
            warm.add(0, m);
            total += tokens;
        }
        return warm;
    }

    /** Собирает массив messages для API. */
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
        Set<String> warmTexts = new HashSet<>();
        for (var m : warm) warmTexts.add(m.content);

        for (var m : warm) {
            msgs.add(Map.of(
                    "role", m.role.toLowerCase(),
                    "content", m.content
            ));
        }

        // 3. COLD + вопрос — в конец
        List<String> coldFiltered = new ArrayList<>();
        for (String c : cold) {
            if (!warmTexts.contains(c)) coldFiltered.add(c);
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