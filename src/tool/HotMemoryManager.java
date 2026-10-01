package tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

/**
 * HotMemoryManager — управляет стабильным префиксом промта для cache hit.
 * Префикс = persona + системные инструкции + опционально замороженный контекст.
 * Не меняется между запросами → кэшируется провайдером.
 */
public class HotMemoryManager {

    private static String frozenPrefix = null;
    private static int maxPrefixTokens = 4000; // настраивается
    private static boolean frozen = false;

    /**
     * Собирает стабильный префикс. Если он уже заморожен — возвращает его же.
     * Иначе пересобирает из persona.txt и системных инструкций.
     */
    public static String getPrefix() throws IOException {
        if (frozen && frozenPrefix != null) {
            return frozenPrefix;
        }

        StringBuilder sb = new StringBuilder();

        // 1. Персона — стабильна, если не менять в сессии
        String persona = Files.readString(Path.of("persona.txt"), StandardCharsets.UTF_8);
        sb.append(persona).append("\n\n");

        // 2. Системные инструкции — фиксированные, не зависят от запроса
        sb.append("Ты — наставник. Отвечай по делу, используй метафоры, ");
        sb.append("заканчивай вопросом. Не выдумывай ссылки.\n\n");

        // Обрезаем до maxPrefixTokens (грубо: 4 символа ≈ 1 токен)
        String prefix = sb.toString();
        int maxChars = maxPrefixTokens * 4;
        if (prefix.length() > maxChars) {
            prefix = prefix.substring(0, maxChars);
        }

        frozenPrefix = prefix;
        return prefix;
    }

    /**
     * Заморозить текущий префикс. После этого он не меняется до :hot refresh.
     */
    public static void freeze() throws IOException {
        frozenPrefix = null;
        frozen = false;
        getPrefix(); // строим заново
        frozen = true;
        System.out.println("[HOT] Префикс заморожен. Размер: ~" +
                (frozenPrefix.length() / 4) + " токенов");
    }

    /**
     * Разморозить и пересобрать префикс (после изменения persona.txt).
     */
    public static void refresh() throws IOException {
        frozenPrefix = null;
        frozen = false;
        String prefix = getPrefix();
        frozen = true;
        System.out.println("[HOT] Префикс обновлён. Размер: ~" +
                (prefix.length() / 4) + " токенов");
    }

    public static String getStatus() {
        if (frozenPrefix == null) return "не собран";
        return "заморожен=" + frozen + ", ~" + (frozenPrefix.length() / 4) + " токенов";
    }

    public static void setMaxPrefixTokens(int tokens) {
        maxPrefixTokens = tokens;
    }

    public static int getMaxPrefixTokens() {
        return maxPrefixTokens;
    }

    public static boolean isFrozen() {
        return frozen;
    }
}