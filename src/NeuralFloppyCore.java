import com.google.gson.JsonObject;
import java.io.OutputStream;

public interface NeuralFloppyCore {
    boolean isGameModeEnabled();
    String getDefaultColumn();
    boolean executeCommand(String cmd);
    void saveToColumn(String column, String data, String format);
    String askLLM(String query, JsonObject state);
    String analyzeWithLLM(String query, JsonObject state);
}