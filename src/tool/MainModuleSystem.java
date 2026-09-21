package tool;

import java.util.List;
import java.util.Map;

public class MainModuleSystem implements ModuleContext {
    private final NeuralFloppyCore core;
    private final String moduleName;

    public MainModuleSystem(NeuralFloppyCore core, String moduleName) {
        this.core = core;
        this.moduleName = moduleName;
    }

    // ==================== Старые методы ====================

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
    public List<Episode> getEpisodes(String column, int limit) {
        return core.getEpisodes(column, limit);
    }

    @Override
    public void saveEpisode(String column, Episode episode) {
        core.saveEpisode(column, episode);
    }

    @Override
    public double[] getEmbedding(String text) {
        return core.getEmbedding(text);
    }

    @Override
    public List<VecMatch> searchByVector(double[] queryVec, String column, int topK) {
        return core.searchByVector(queryVec, column, topK);
    }

    @Override
    public void saveVector(String column, double[] vec, Map<String, Object> metadata) {
        core.saveVector(column, vec, metadata);
    }

    @Override
    public double getQValue(String column, String stateKey, String action) {
        return core.getQValue(column, stateKey, action);
    }

    @Override
    public void setQValue(String column, String stateKey, String action, double value) {
        core.setQValue(column, stateKey, action, value);
    }

    @Override
    public Map<String, Double> getQValues(String column, String stateKey) {
        return core.getQValues(column, stateKey);
    }

    @Override
    public List<String> listColumns() {
        return core.listColumns();
    }

    @Override
    public void createColumn(String name) {
        core.createColumn(name);
    }

    @Override
    public void clearColumn(String name) {
        core.clearColumn(name);
    }

    // ==================== Новые методы: Registry ====================

    @Override
    public void register(String namespace, String name, Object thing) {
        core.register(namespace, name, thing);
    }

    @Override
    public Object get(String namespace, String name) {
        return core.get(namespace, name);
    }

    @Override
    public boolean exists(String namespace, String name) {
        return core.exists(namespace, name);
    }

    // ==================== Новые методы: Config ====================

    @Override
    public String getConfig(String key, String defaultValue) {
        return core.getConfig(key, defaultValue);
    }

    @Override
    public void setConfig(String key, String value) {
        core.setConfig(key, value);
    }

    @Override
    public boolean hasConfig(String key) {
        return core.hasConfig(key);
    }

    // ==================== Логи с префиксом ====================

    @Override
    public void log(String text) {
        logInfo(text);
    }

    @Override
    public void logTrace(String msg) {
        System.out.println("[TRACE][" + moduleName + "] " + msg);
    }

    @Override
    public void logDebug(String msg) {
        System.out.println("[DEBUG][" + moduleName + "] " + msg);
    }

    @Override
    public void logInfo(String msg) {
        System.out.println("[INFO][" + moduleName + "] " + msg);
    }

    @Override
    public void logWarn(String msg) {
        System.out.println("[WARN][" + moduleName + "] " + msg);
    }

    @Override
    public void logError(String msg, Throwable t) {
        System.err.println("[ERROR][" + moduleName + "] " + msg +
                (t != null ? " | " + t.getMessage() : ""));
    }
    @Override
    public Module getModule(String name) {
        return core.getModule(name);
    }

    @Override
    public boolean isModuleEnabled(String name) {
        return core.isModuleEnabled(name);
    }

    @Override
    public List<String> listModules() {
        return core.listModules();
    }
}