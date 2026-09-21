package tool;

import java.nio.ByteBuffer;

import java.sql.*;
import java.util.*;

public class DatabaseManager {

    public static void initDatabase() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:data/neuralfloppy.db");
             Statement stmt = c.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS messages (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "role TEXT NOT NULL, " +
                    "content TEXT NOT NULL, " +
                    "ts INTEGER NOT NULL)");
            stmt.execute("CREATE TABLE IF NOT EXISTS embeddings (" +
                    "content TEXT PRIMARY KEY, " +
                    "vec BLOB NOT NULL, " +
                    "ts INTEGER NOT NULL)");
        }
    }

    public static void saveMessageToDb(String role, String content, long ts) throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:data/neuralfloppy.db");
             PreparedStatement ps = c.prepareStatement("INSERT INTO messages (role, content, ts) VALUES (?, ?, ?)")) {
            ps.setString(1, role);
            ps.setString(2, content);
            ps.setLong(3, ts);
            ps.executeUpdate();
        }
    }
    public static List<String[]> loadMessagesFromDb() throws SQLException {
        List<String[]> list = new ArrayList<>();
        String sql = "SELECT role, content, ts FROM messages ORDER BY id";
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:data/neuralfloppy.db");
             Statement stmt = c.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new String[]{
                        rs.getString("role"),
                        rs.getString("content"),
                        String.valueOf(rs.getLong("ts"))
                });
            }
        }
        return list;
    }
    public static void saveEmbedding(String content, double[] vec) throws SQLException {
        byte[] blob = new byte[vec.length * 8];
        ByteBuffer buffer = ByteBuffer.wrap(blob);
        for (double d : vec) {
            buffer.putDouble(d);
        }

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:data/neuralfloppy.db");
             PreparedStatement ps = c.prepareStatement("INSERT OR REPLACE INTO embeddings (content, vec, ts) VALUES (?, ?, ?)")) {
            ps.setString(1, content);
            ps.setBytes(2, blob);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public static List<EmbeddingEntry> loadEmbeddings() throws SQLException {
        List<EmbeddingEntry> list = new ArrayList<>();
        String sql = "SELECT content, vec, ts FROM embeddings ORDER BY ts";

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:data/neuralfloppy.db");
             Statement stmt = c.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                byte[] bytes = rs.getBytes("vec");
                double[] vec = new double[bytes.length / 8];
                ByteBuffer.wrap(bytes).asDoubleBuffer().get(vec);
                list.add(new EmbeddingEntry(rs.getString("content"), vec, rs.getLong("ts")));
            }
        }
        return list;
    }

    public static void clearEmbeddings() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:data/neuralfloppy.db");
             Statement stmt = c.createStatement()) {
            stmt.execute("DELETE FROM embeddings");
        }
    }

    public static class EmbeddingEntry {
        public String content;
        public double[] vec;
        public long ts;

        public EmbeddingEntry(String content, double[] vec, long ts) {
            this.content = content;
            this.vec = vec;
            this.ts = ts;
        }
    }
    public static void initFts5() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:data/neuralfloppy.db");
             Statement stmt = c.createStatement()) {

            // Таблица FTS5, привязанная к messages
            stmt.execute("""
            CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING fts5(
                content,
                role UNINDEXED,
                ts UNINDEXED,
                content='messages',
                content_rowid='id'
            )
        """);

            // Триггер на INSERT
            stmt.execute("""
            CREATE TRIGGER IF NOT EXISTS messages_ai AFTER INSERT ON messages BEGIN
                INSERT INTO messages_fts(rowid, content, role, ts)
                VALUES (new.id, new.content, new.role, new.ts);
            END
        """);

            // Триггер на DELETE
            stmt.execute("""
            CREATE TRIGGER IF NOT EXISTS messages_ad AFTER DELETE ON messages BEGIN
                INSERT INTO messages_fts(messages_fts, rowid, content, role, ts)
                VALUES('delete', old.id, old.content, old.role, old.ts);
            END
        """);

            // Триггер на UPDATE
            stmt.execute("""
            CREATE TRIGGER IF NOT EXISTS messages_au AFTER UPDATE ON messages BEGIN
                INSERT INTO messages_fts(messages_fts, rowid, content, role, ts)
                VALUES('delete', old.id, old.content, old.role, old.ts);
                INSERT INTO messages_fts(rowid, content, role, ts)
                VALUES (new.id, new.content, new.role, new.ts);
            END
        """);

            System.out.println("[FTS5] Таблица messages_fts готова.");
        }
    }

    public static void backfillFts5() throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:data/neuralfloppy.db");
             Statement stmt = c.createStatement()) {
            // Заливаем всё, что ещё не проиндексировано
            stmt.execute("""
            INSERT INTO messages_fts(rowid, content, role, ts)
            SELECT id, content, role, ts FROM messages
            WHERE id NOT IN (SELECT rowid FROM messages_fts)
        """);
            System.out.println("[FTS5] Индекс заполнен из messages.");
        }
    }

    public static List<String> searchFts(String query, int limit) throws SQLException {
        List<String> results = new ArrayList<>();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:data/neuralfloppy.db");
             PreparedStatement ps = c.prepareStatement(
                     "SELECT content FROM messages_fts WHERE content MATCH ? ORDER BY rank LIMIT ?")) {
            ps.setString(1, query);
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) results.add(rs.getString("content"));
        }
        return results;
    }
}