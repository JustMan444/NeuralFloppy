package tool;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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
}