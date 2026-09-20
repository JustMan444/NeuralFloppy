package tool;

import java.util.Map;

/**
 * Результат векторного поиска.
 */
public class VecMatch {
    public int id;
    public double distance;
    public Map<String, Object> metadata;

    public VecMatch(int id, double distance, Map<String, Object> metadata) {
        this.id = id;
        this.distance = distance;
        this.metadata = metadata;
    }
}