package tool;

import java.util.List;

public interface ModuleContext {
    List<String> findContext(String query);
    void saveMessage(String role, String content);
    String callLLM(String prompt, String mode);
    void log(String text);
}