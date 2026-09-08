package tool;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

public class MainModuleSystem implements ModuleContext {
    private final NeuralFloppyCore core;

    public MainModuleSystem(NeuralFloppyCore core) {
        this.core = core;
    }

    @Override
    public List<String> findContext(String query) {
        return core.findContext(query);
    }

    @Override
    public void saveMessage(String role, String content) {
        core.saveMessage(role, content);
    }

    @Override
    public String callLLM(String prompt, String mode) {
        return core.callLLM(prompt, mode);
    }

    @Override
    public void log(String text) {
        System.out.println(text);
    }
}