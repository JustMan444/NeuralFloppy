package tool;

import com.google.gson.JsonObject;

import java.util.List;

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
}