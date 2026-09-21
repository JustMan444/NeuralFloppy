package tool;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigManager {
    private static final String CONFIG_FILE = "config.json";
    private static final Gson GSON = new Gson();

    // Значения по умолчанию (если файла нет)
    public static String apiKey = "";
    public static String apiUrl = "https://openrouter.ai/api/v1/chat/completions";
    public static String defaultModel = "openrouter/free";

    public static void load() {
        Path path = Path.of(CONFIG_FILE);
        if (!Files.exists(path)) {
            System.out.println("[CONFIG] Файл config.json не найден. Создаю с настройками по умолчанию.");
            save();
            return;
        }
        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject json = GSON.fromJson(raw, JsonObject.class);
            if (json.has("api_key")) apiKey = json.get("api_key").getAsString();
            if (json.has("api_url")) apiUrl = json.get("api_url").getAsString();
            if (json.has("default_model")) defaultModel = json.get("default_model").getAsString();
            System.out.println("[CONFIG] Загружен config.json. API URL: " + apiUrl);
        } catch (Exception e) {
            System.out.println("[CONFIG] Ошибка загрузки: " + e.getMessage());
        }
    }
    private static final Map<String, String> extraConfig = new ConcurrentHashMap<>();

    public static String get(String key) {
        if (key.equals("api_key")) return apiKey;
        if (key.equals("api_url")) return apiUrl;
        if (key.equals("default_model")) return defaultModel;
        return extraConfig.get(key);
    }

    public static void set(String key, String value) {
        if (key.equals("api_key")) apiKey = value;
        else if (key.equals("api_url")) apiUrl = value;
        else if (key.equals("default_model")) defaultModel = value;
        else extraConfig.put(key, value);
        save();
    }

    public static void save() {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("api_key", apiKey);
            json.addProperty("api_url", apiUrl);
            json.addProperty("default_model", defaultModel);
            Files.writeString(Path.of(CONFIG_FILE), GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("[CONFIG] Ошибка сохранения: " + e.getMessage());
        }
    }
}