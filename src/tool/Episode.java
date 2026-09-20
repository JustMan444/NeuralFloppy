package tool;

/**
 * Один эпизод q-agent. Хранится в колонке как NDJSON-строка.
 */
public class Episode {
    public String state;      // JSON-строка состояния
    public String action;
    public double reward;
    public String nextState;  // JSON-строка следующего состояния
    public long ts;

    public Episode(String state, String action, double reward, String nextState, long ts) {
        this.state = state;
        this.action = action;
        this.reward = reward;
        this.nextState = nextState;
        this.ts = ts;
    }
}