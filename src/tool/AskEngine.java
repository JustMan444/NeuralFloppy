package tool;

import com.google.gson.*;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * AskEngine — единый вход для всех LLM-запросов в NeuralFloppy.
 * Заменяет зоопарк методов askAPI/askLocal/askAPIStreaming/askLocalStreaming.
 */
public class AskEngine {

    private static final Gson GSON = new Gson();

    // Ссылка на ядро (для saveMessage / saveToColumn — они instance-методы)
    private static NeuralFloppyTool2_0_DT_snapchot2 core;

    public static void setCore(NeuralFloppyTool2_0_DT_snapchot2 c) {
        core = c;
    }

    // ==================== ПАРАМЕТРЫ ЗАПРОСА ====================
    public static class AskRequest {
        public String question;
        public String column = null;
        public JsonObject state = null;
        public Mode mode = Mode.AUTO;
        public boolean useThinking = false;
        public boolean useHotMemory = true;
        public int contextSize = 10;
        public OutputStream streamTo = null;
        public boolean silent = false;
    }

    public enum Mode { AUTO, API, LOCAL, MANUAL }

    // ==================== ПАРАМЕТРЫ ОТВЕТА ====================
    public static class AskResponse {
        public String answer;
        public long promptTokens;
        public long cacheHitTokens;
        public long cacheMissTokens;
        public long completionTokens;
        public long elapsedMs;
        public String model;
        public String mode;
        public boolean fromCache;

        @Override
        public String toString() {
            return String.format(
                    "answer=%d chars | hit=%d miss=%d completion=%d | %dms | %s",
                    answer.length(), cacheHitTokens, cacheMissTokens,
                    completionTokens, elapsedMs, mode
            );
        }
    }

    // ==================== ГЛАВНЫЙ МЕТОД ====================
    public static AskResponse ask(AskRequest req) throws Exception {
        long t0 = System.currentTimeMillis();
        AskResponse resp = new AskResponse();
        resp.model = NeuralFloppyTool2_0_DT_snapchot2.getCurrentModel();

        // 1. Режим
        Mode mode = resolveMode(req.mode);
        resp.mode = mode.name();

        // 2. Контекст
        List<String> ctx = collectContext(req);
        resp.fromCache = !ctx.isEmpty();

        // 3. Промпт
        String prompt = buildPrompt(req, ctx);

        // 4. Размышления
        if (req.useThinking) {
            String thoughts = runThinking(req.question, mode);
            if (!thoughts.isBlank()) {
                prompt = injectThoughts(prompt, thoughts);
            }
        }

        // 5. Вызов LLM
        String answer;
        switch (mode) {
            case API    -> answer = callApi(prompt, req, resp);
            case LOCAL  -> answer = callLocal(prompt, req, resp);
            case MANUAL -> answer = callManual(prompt);
            default -> throw new IllegalStateException("Unknown mode: " + mode);
        }

        resp.answer = answer;
        resp.elapsedMs = System.currentTimeMillis() - t0;

        // 6. Сохранение
        if (!req.silent && answer != null && !answer.isBlank()) {
            saveToMemory(req, answer);
        }

        // 7. Лог
        logUsage(req, resp);

        return resp;
    }

    // ==================== РЕЖИМ ====================
    private static Mode resolveMode(Mode requested) {
        if (requested == Mode.AUTO) {
            return NeuralFloppyTool2_0_DT_snapchot2.getCurrentMode()
                    == NeuralFloppyTool2_0_DT_snapchot2.Mode.API ? Mode.API : Mode.LOCAL;
        }
        return requested;
    }

    // ==================== КОНТЕКСТ ====================
    private static List<String> collectContext(AskRequest req) {
        if (req.silent) return List.of();
        try {
            if (req.column != null) {
                return NeuralFloppyTool2_0_DT_snapchot2.searchContext(
                        req.question, req.contextSize, req.column);
            } else {
                return NeuralFloppyTool2_0_DT_snapchot2.searchContext(
                        req.question, req.contextSize);
            }
        } catch (Exception e) {
            System.err.println("[ASK] Ошибка поиска контекста: " + e.getMessage());
            return List.of();
        }
    }

    // ==================== ПРОМТ ====================
    private static String buildPrompt(AskRequest req, List<String> ctx) throws Exception {
        StringBuilder sb = new StringBuilder();

        // Слой 1: HotMemory (кэшируемый)
        if (req.useHotMemory) {
            sb.append(HotMemoryManager.getPrefix()).append("\n\n");
        } else {
            sb.append(NeuralFloppyTool2_0_DT_snapchot2.getCurrentPersona()).append("\n\n");
        }

        // Слой 2: Вопрос
        sb.append("Вопрос: ").append(req.question).append("\n\n");

        // Слой 3: Контекст
        if (!ctx.isEmpty()) {
            sb.append("Контекст из памяти:\n");
            sb.append(String.join("\n---\n", ctx));
            sb.append("\n\n");
        }

        // Слой 4: Инструкция
        sb.append("Ответь как наставник. Кратко, по делу.");

        return sb.toString();
    }

    // ==================== РАЗМЫШЛЕНИЯ ====================
    private static String runThinking(String question, Mode mode) {
        try {
            String prompt = "Проанализируй вопрос. Дай цепочку рассуждений:\n" + question;
            AskRequest req = new AskRequest();
            req.question = prompt;
            req.mode = mode == Mode.API ? Mode.API : Mode.LOCAL;
            req.useHotMemory = false;
            req.silent = true;
            return ask(req).answer;
        } catch (Exception e) {
            System.err.println("[THINK] Ошибка: " + e.getMessage());
            return "";
        }
    }

    private static String injectThoughts(String prompt, String thoughts) {
        return "Размышления:\n" + thoughts + "\n\nТеперь ответь:\n" + prompt;
    }

    // ==================== API ====================
    private static String callApi(String prompt, AskRequest req, AskResponse resp) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", NeuralFloppyTool2_0_DT_snapchot2.getCurrentModel());
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        body.put("temperature", NeuralFloppyTool2_0_DT_snapchot2.getTemperature());
        body.put("max_tokens", 2000);
        if (req.streamTo != null) body.put("stream", true);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ConfigManager.apiUrl))
                .header("Authorization", "Bearer " + ConfigManager.apiKey)
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(
                        GSON.toJson(body), StandardCharsets.UTF_8))
                .build();

        if (req.streamTo != null) {
            return streamApiResponse(client, request, req.streamTo, resp);
        } else {
            return plainApiResponse(client, request, resp);
        }
    }

    private static String plainApiResponse(HttpClient client, HttpRequest request, AskResponse resp) throws Exception {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);

        if (json.has("error")) {
            return "Ошибка API: " + json.get("error").toString();
        }

        parseUsage(json, resp);

        if (json.has("choices") && json.getAsJsonArray("choices").size() > 0) {
            JsonElement content = json.getAsJsonArray("choices").get(0)
                    .getAsJsonObject().getAsJsonObject("message").get("content");
            return content != null && !content.isJsonNull() ? content.getAsString() : "";
        }
        return "Пустой ответ API: " + response.body();
    }

    private static String streamApiResponse(HttpClient client, HttpRequest request,
                                            OutputStream os, AskResponse resp) throws Exception {
        HttpResponse<java.io.InputStream> response = client.send(request,
                HttpResponse.BodyHandlers.ofInputStream());
        java.io.BufferedReader in = new java.io.BufferedReader(
                new java.io.InputStreamReader(response.body(), StandardCharsets.UTF_8));
        StringBuilder full = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            if (!line.startsWith("data: ")) continue;
            String data = line.substring(6).trim();
            if (data.equals("[DONE]")) continue;
            try {
                JsonObject json = GSON.fromJson(data, JsonObject.class);
                if (json.has("choices") && json.getAsJsonArray("choices").size() > 0) {
                    JsonObject delta = json.getAsJsonArray("choices").get(0).getAsJsonObject();
                    String chunk = null;
                    if (delta.has("delta") && delta.getAsJsonObject("delta").has("content"))
                        chunk = delta.getAsJsonObject("delta").get("content").getAsString();
                    if (chunk != null) {
                        os.write(chunk.getBytes(StandardCharsets.UTF_8));
                        os.flush();
                        full.append(chunk);
                    }
                }
                if (json.has("usage")) parseUsage(json, resp);
            } catch (Exception ignore) {}
        }
        return full.toString();
    }

    // ==================== LOCAL ====================
    private static String callLocal(String prompt, AskRequest req, AskResponse resp) throws Exception {
        NeuralFloppyTool2_0_DT_snapchot2.ensureOllamaRunning();
        HttpClient client = HttpClient.newHttpClient();

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("num_ctx", 32768);
        options.put("temperature", NeuralFloppyTool2_0_DT_snapchot2.getTemperature());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", NeuralFloppyTool2_0_DT_snapchot2.getCurrentModel());
        body.put("prompt", prompt);
        body.put("stream", req.streamTo != null);
        body.put("options", options);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/generate"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(
                        GSON.toJson(body), StandardCharsets.UTF_8))
                .build();

        if (req.streamTo != null) {
            HttpResponse<java.io.InputStream> response = client.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());
            java.io.BufferedReader in = new java.io.BufferedReader(
                    new java.io.InputStreamReader(response.body(), StandardCharsets.UTF_8));
            StringBuilder full = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                try {
                    JsonObject json = GSON.fromJson(line, JsonObject.class);
                    if (json.has("response")) {
                        String chunk = json.get("response").getAsString();
                        req.streamTo.write(chunk.getBytes(StandardCharsets.UTF_8));
                        req.streamTo.flush();
                        full.append(chunk);
                    }
                    if (json.has("done") && json.get("done").getAsBoolean()) break;
                } catch (Exception ignore) {}
            }
            return full.toString();
        } else {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
            if (json.has("response")) return json.get("response").getAsString();
            return "Ошибка Ollama: " + response.body();
        }
    }

    // ==================== MANUAL ====================
    private static String callManual(String prompt) {
        System.out.println("===== СКОПИРУЙ ЭТО В МОДЕЛЬ =====");
        System.out.println(prompt);
        System.out.println("=================================");
        System.out.print("Вставь ответ: ");
        try {
            return new java.io.BufferedReader(new java.io.InputStreamReader(
                    System.in, StandardCharsets.UTF_8)).readLine();
        } catch (Exception e) {
            return "";
        }
    }

    // ==================== СОХРАНЕНИЕ ====================
    private static void saveToMemory(AskRequest req, String answer) {
        if (core == null) {
            System.err.println("[ASK] core не установлен — сохранение пропущено");
            return;
        }
        try {
            if (req.column != null) {
                core.saveToColumn(req.column, answer, "chat");
            } else {
                core.saveMessage("ASSISTANT", answer);
            }
        } catch (Exception e) {
            System.err.println("[ASK] Ошибка сохранения: " + e.getMessage());
        }
    }

    // ==================== ЛОГ ====================
    private static void logUsage(AskRequest req, AskResponse resp) {
        StringBuilder sb = new StringBuilder("[CACHE] ");
        if (resp.cacheHitTokens > 0 || resp.cacheMissTokens > 0) {
            sb.append("hit=").append(resp.cacheHitTokens)
                    .append(" miss=").append(resp.cacheMissTokens)
                    .append(" completion=").append(resp.completionTokens);
        } else {
            sb.append("usage не получен (провайдер не вернул)");
        }
        sb.append(" | ").append(resp.elapsedMs).append("ms");
        sb.append(" | mode=").append(resp.mode);
        if (req.column != null) sb.append(" | column=").append(req.column);
        if (req.useThinking) sb.append(" | thinking");

        System.out.println(sb);
    }

    private static void parseUsage(JsonObject json, AskResponse resp) {
        if (!json.has("usage")) return;
        JsonObject usage = json.getAsJsonObject("usage");
        if (usage.has("prompt_tokens"))            resp.promptTokens     = usage.get("prompt_tokens").getAsLong();
        if (usage.has("completion_tokens"))        resp.completionTokens = usage.get("completion_tokens").getAsLong();
        if (usage.has("prompt_cache_hit_tokens"))  resp.cacheHitTokens   = usage.get("prompt_cache_hit_tokens").getAsLong();
        if (usage.has("prompt_cache_miss_tokens")) resp.cacheMissTokens  = usage.get("prompt_cache_miss_tokens").getAsLong();
    }

    // ==================== ОБЁРТКИ ====================
    public static String askApiLegacy(String question) throws Exception {
        AskRequest req = new AskRequest();
        req.question = question;
        req.mode = Mode.API;
        return ask(req).answer;
    }

    public static String askLocalLegacy(String question) throws Exception {
        AskRequest req = new AskRequest();
        req.question = question;
        req.mode = Mode.LOCAL;
        return ask(req).answer;
    }
}