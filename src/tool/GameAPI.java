package tool;

import com.google.gson.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * GameAPI v2.0 — Игровой мозжечок NeuralFloppy.
 * Полностью переработан для быстрых, детерминированных ответов на основе траекторий.
 * LLM используется только для формата 'chat'.
 */
public class GameAPI {

    private static final Gson GSON = new Gson();

    private static NeuralFloppyCore core;

    public static void setCore(NeuralFloppyCore c) {
        core = c;
    }

    // -------------------------- ОБРАБОТЧИКИ --------------------------

    public static class GameHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            handleRequest(exchange, false);
        }
    }

    public static class FullControlHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            handleRequest(exchange, true);
        }
    }

    // -------------------------- ОСНОВНАЯ ЛОГИКА --------------------------

    private static void handleRequest(HttpExchange exchange, boolean fullControl) throws IOException {
        // 1. Проверка активации
        if (!core.isGameModeEnabled()) {
            sendError(exchange, 403, "Game API is not active. Use :mode programmers to enable.");
            return;
        }

        // 2. Чтение тела запроса
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonObject requestJson;
        try {
            requestJson = JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            sendError(exchange, 400, "Invalid JSON format.");
            return;
        }

        // 3. Извлечение полей
        String column = requestJson.has("column") ? requestJson.get("column").getAsString() : core.getDefaultColumn();
        String format = requestJson.has("format") ? requestJson.get("format").getAsString() : null;


        // 4. Валидация
        if (column == null || column.isBlank()) {
            sendError(exchange, 400, "Column is required.");
            return;
        }
        if (format == null || format.isBlank()) {
            sendError(exchange, 400, "Format is required.");
            return;
        }

        // 5. Обработка команд (только для full-control)
// 5. Обработка команд (только для full-control)
        if (fullControl && requestJson.has("commands")) {
            try {
                JsonArray commands = requestJson.getAsJsonArray("commands");
                for (JsonElement cmdElement : commands) {
                    String cmd = cmdElement.getAsString();
                    if (cmd == null || cmd.isBlank()) continue;

                    if (!core.executeCommand(cmd)) {
                        sendError(exchange, 403, "Command is not allowed or failed: " + cmd);
                        return;
                    }
                    System.out.println("[API] Выполнена команда: " + cmd);
                }
            } catch (Exception e) {
                sendError(exchange, 400, "Invalid commands format: " + e.getMessage());
                return;
            }
        }

        // 6. Обработка формата
        JsonObject responseJson = processFormat(column, format, requestJson);

        // 7. Отправка ответа
        byte[] responseBytes = responseJson.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    // -------------------------- ОБРАБОТКА ФОРМАТОВ --------------------------

    private static JsonObject processFormat(String column, String format, JsonObject requestJson) {
        JsonObject response = new JsonObject();
        String columnType = requestJson.has("type") ? requestJson.get("type").getAsString() : "classic";

        try {
            if ("q-agent".equals(columnType)) {
                // Режим быстрого, детерминированного поиска без LLM
                switch (format) {
                    case "observer":
                        SuperFastEngine.observe(column, requestJson);
                        response.addProperty("status", "recorded");
                        break;
                    case "action-only":
                    case "detailed":
                    case "smart-action":
                    case "custom":
                        response = SuperFastEngine.query(column, requestJson);
                        break;
                    case "chat":
                        String llmAnswer = core.askLLM(requestJson.get("query").getAsString(), requestJson, column);
                        response.addProperty("message", llmAnswer);
                        break;
                    default:
                        sendError(null, 400, "Unknown format: " + format);
                        break;
                }
            } else {
                // Классический режим с LLM (твоя старая добрая логика)
                switch (format) {
                    case "observer":
                        core.saveToColumn(column, requestJson.toString(), "observer");
                        response.addProperty("status", "recorded");
                        break;
                    case "looker":
                        String llmAnalysis = core.analyzeWithLLM(requestJson.get("query").getAsString(), requestJson);
                        core.saveToColumn(column, llmAnalysis, "looker");
                        response.addProperty("status", "recorded");
                        break;
                    default:
                        String llmAnswer = core.askLLM(requestJson.get("query").getAsString(), requestJson, column);
                        core.saveToColumn(column, llmAnswer, format);
                        response.addProperty("message", llmAnswer);
                        break;
                }
            }
        } catch (Exception e) {
            response = new JsonObject();
            response.addProperty("error", "Internal server error: " + e.getMessage());
        }

        return response;
    }

    // -------------------------- SUPER-FAST ENGINE --------------------------

    /**
     * SuperFastEngine — детерминированный движок хранения и поиска траекторий.
     * Не использует LLM. Работает на HashMap'ах.
     */
    public static class SuperFastEngine {
        // Основная Q-таблица: State -> Action -> Средняя награда
        private static final Map<String, Map<String, Double>> qTable = new ConcurrentHashMap<>();
        // Хранилище всех траекторий для поиска: State -> List of TrajectoryEvent
        private static final Map<String, List<TrajectoryEvent>> trajectories = new ConcurrentHashMap<>();
        // Индекс действий
        private static final Map<String, Integer> actionIndex = new ConcurrentHashMap<>();

        /**
         * Сохраняет новое наблюдение и обновляет Q-таблицу.
         */
        public static void observe(String column, JsonObject requestJson) {
            String stateKey = stateToKey(requestJson.getAsJsonObject("state"));
            String action = requestJson.get("action").getAsString();
            double reward = requestJson.has("reward") ? requestJson.get("reward").getAsDouble() : 0.0;
            JsonObject nextState = requestJson.has("next_state") ? requestJson.getAsJsonObject("next_state") : null;

            // 1. Обновляем Q-таблицу (простое экспоненциальное сглаживание)
            Map<String, Double> actions = qTable.computeIfAbsent(stateKey, k -> new ConcurrentHashMap<>());
            double oldQ = actions.getOrDefault(action, 0.0);
            double lr = 0.1; // Скорость обучения
            actions.put(action, oldQ + lr * (reward - oldQ));

            // 2. Сохраняем траекторию
            List<TrajectoryEvent> traj = trajectories.computeIfAbsent(stateKey, k -> new ArrayList<>());
            traj.add(new TrajectoryEvent(action, reward, nextState, Instant.now().getEpochSecond()));

            // 3. Индексируем действие
            actionIndex.put(action, actionIndex.getOrDefault(action, 0) + 1);
        }

        /**
         * Ищет похожие состояния и возвращает топ-5 траекторий.
         */
        public static JsonObject query(String column, JsonObject requestJson) {
            JsonObject state = requestJson.getAsJsonObject("state");
            String stateKey = stateToKey(state);

            // 1. Ищем точное совпадение
            if (qTable.containsKey(stateKey)) {
                return buildResponse(stateKey);
            }
            List<String> neighbors = nearestNeighbors(stateKey, 5);
            JsonArray neighborsArray = new JsonArray();
            for (String neighborKey : neighbors) {
                JsonObject neighborJson = new JsonObject();
                neighborJson.addProperty("state", neighborKey);
                neighborJson.add("actions", GSON.toJsonTree(qTable.get(neighborKey)));
                neighborJson.addProperty("similarity", calculateSimilarity(stateKey, neighborKey));
                neighborsArray.add(neighborJson);
            }

            JsonObject response = new JsonObject();
            response.add("neighbors", neighborsArray);
            response.addProperty("message", "Exact match not found. Returning nearest neighbors.");
            return response;
        }

        private static JsonObject buildResponse(String stateKey) {
            Map<String, Double> actions = qTable.get(stateKey);
            String bestAction = actions.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .get().getKey();

            JsonObject response = new JsonObject();
            response.addProperty("action", bestAction);
            response.addProperty("confidence", actions.get(bestAction));
            response.add("all_actions", GSON.toJsonTree(actions));
            return response;
        }

        private static List<String> nearestNeighbors(String stateKey, int k) {
            // Простой поиск по частичному совпадению ключей
            // В будущем коде здесь будет сравнение векторов состояний
            return qTable.keySet().stream()
                    .filter(key -> !key.equals(stateKey))
                    .sorted(Comparator.comparingDouble(key -> -calculateSimilarity(stateKey, key)))
                    .limit(k)
                    .collect(Collectors.toList());
        }

        private static double calculateSimilarity(String key1, String key2) {
            String[] parts1 = key1.split("\\|");
            String[] parts2 = key2.split("\\|");
            Set<String> set1 = new HashSet<>(Arrays.asList(parts1));
            Set<String> set2 = new HashSet<>(Arrays.asList(parts2));
            set1.retainAll(set2);
            return (double) set1.size() / Math.max(parts1.length, parts2.length);
        }

        private static String stateToKey(JsonObject state) {
            if (state == null) return "empty";
            return state.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getKey() + "=" + e.getValue().toString())
                    .collect(Collectors.joining("|"));
        }

        static class TrajectoryEvent {
            String action;
            double reward;
            JsonObject nextState;
            long timestamp;

            TrajectoryEvent(String action, double reward, JsonObject nextState, long timestamp) {
                this.action = action;
                this.reward = reward;
                this.nextState = nextState;
                this.timestamp = timestamp;
            }
        }
    }

    // -------------------------- ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ --------------------------

    private static void sendError(HttpExchange exchange, int code, String message) throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        byte[] bytes = error.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}