package tool;

import java.util.List;
import java.util.Map;

public interface ModuleContext {
    List<String> findContext(String query);
    void saveMessage(String role, String content);
    String callLLM(String prompt, String mode);
    void log(String text);

    // === Эпизоды ===
    List<Episode> getEpisodes(String column, int limit);
    void saveEpisode(String column, Episode episode);

    // === Векторы ===
    double[] getEmbedding(String text);
    List<VecMatch> searchByVector(double[] queryVec, String column, int topK);
    void saveVector(String column, double[] vec, Map<String, Object> metadata);

    // === Q-таблица в RAM ===
    double getQValue(String column, String stateKey, String action);
    void setQValue(String column, String stateKey, String action, double value);
    Map<String, Double> getQValues(String column, String stateKey);

    // === Колонки ===
    List<String> listColumns();
    void createColumn(String name);
    void clearColumn(String name);

    // === Registry ===
    void register(String namespace, String name, Object thing);
    Object get(String namespace, String name);
    boolean exists(String namespace, String name);

    // === Доступ к модулям ===
    Module getModule(String name);
    boolean isModuleEnabled(String name);
    List<String> listModules();

    // === Config ===
    String getConfig(String key, String defaultValue);
    void setConfig(String key, String value);
    boolean hasConfig(String key);

    // === Логи ===
    void logTrace(String msg);
    void logDebug(String msg);
    void logInfo(String msg);
    void logWarn(String msg);
    void logError(String msg, Throwable t);
}