package tool;

/**
 * Один эпизод q-agent. Хранится в колонке как NDJSON-строка.
 */

public record Episode(
        String state,
        String action,
        double reward,
        String nextState,
        long ts
) {}

//    public Episode(String state, String action, double reward, String nextState, long ts) {
//        this.state = state;
//        this.action = action;
//        this.reward = reward;
//        this.nextState = nextState;
//        this.ts = ts;
//    }
