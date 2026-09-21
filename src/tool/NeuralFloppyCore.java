package tool;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

public interface NeuralFloppyCore {
    boolean isGameModeEnabled();
    String getDefaultColumn();
    boolean executeCommand(String cmd);
    void saveToColumn(String column, String data, String format);
    String askLLM(String query, JsonObject state);
    String analyzeWithLLM(String query, JsonObject state);
    List<String> findContext(String query);
    void saveMessage(String role, String content);
    String callLLM(String prompt, String mode);
    String askLLM(String query, JsonObject state, String column);

    List<Episode> getEpisodes(String column, int limit);
    void saveEpisode(String column, Episode episode);
    double[] getEmbedding(String text);
    List<VecMatch> searchByVector(double[] queryVec, String column, int topK);
    void saveVector(String column, double[] vec, Map<String, Object> metadata);
    double getQValue(String column, String stateKey, String action);
    void setQValue(String column, String stateKey, String action, double value);
    Map<String, Double> getQValues(String column, String stateKey);
    List<String> listColumns();
    void createColumn(String name);
    void clearColumn(String name);
    void register(String namespace, String name, Object thing);
    Object get(String namespace, String name);
    boolean exists(String namespace, String name);
    String getConfig(String key, String defaultValue);
    void setConfig(String key, String value);
    boolean hasConfig(String key);
    Module getModule(String name);
    boolean isModuleEnabled(String name);
    List<String> listModules();
    String executeCommandWithResult(String cmd);

}