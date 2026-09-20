package tool;

import java.nio.ByteBuffer;
import java.sql.*;
import java.util.*;

public class VecEngine {
    private static boolean available = false;
    private static String lastError = null;
    private static final String DB = "jdbc:sqlite:data/neuralfloppy.db";
    private static Connection connection = null;

    // Одно общее соединение с уже загруженным расширением
    private static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            org.sqlite.SQLiteConfig config = new org.sqlite.SQLiteConfig();
            config.enableLoadExtension(true);
            connection = DriverManager.getConnection(DB, config.toProperties());
            try (Statement stmt = connection.createStatement()) {
                String dllPath = new java.io.File("lib/sqlite-vec.dll").getAbsolutePath().replace("\\", "/");
                stmt.execute("SELECT load_extension('" + dllPath + "', 'sqlite3_vec_init')");
            }
        }
        return connection;
    }

    public static boolean init() {
        try {
            Connection c = getConnection();
            try (Statement stmt = c.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT vec_version()");
                if (rs.next()) {
                    lastError = null;
                    available = true;
                    System.out.println("[VEC] sqlite-vec загружен, версия: " + rs.getString(1));
                    return true;
                }
            }
        } catch (Exception e) {
            lastError = e.getMessage();
            available = false;
            System.out.println("[VEC] Не удалось загрузить sqlite-vec: " + lastError);
        }
        return false;
    }

    public static boolean isAvailable() { return available; }
    public static String getLastError() { return lastError; }

    public static void rebuildTable(int dimension) {
        if (!available) return;
        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS vec_items");
            stmt.execute("CREATE VIRTUAL TABLE vec_items USING vec0(" +
                    "content_id INTEGER PRIMARY KEY, " +
                    "embedding FLOAT[" + dimension + "])");
            System.out.println("[VEC] Таблица vec_items пересоздана (dim=" + dimension + ")");
        } catch (Exception e) {
            System.out.println("[VEC] Ошибка пересоздания: " + e.getMessage());
        }
    }

    public static void insert(int contentId, double[] vec) {
        if (!available) return;
        try (PreparedStatement ps = getConnection().prepareStatement(
                "INSERT OR REPLACE INTO vec_items (content_id, embedding) VALUES (?, ?)")) {
            ByteBuffer buf = ByteBuffer.allocate(vec.length * 4);
            for (double d : vec) buf.putFloat((float) d);
            ps.setInt(1, contentId);
            ps.setBytes(2, buf.array());
            ps.executeUpdate();
        } catch (Exception e) {
            System.out.println("[VEC] Ошибка вставки: " + e.getMessage());
        }
    }

    public static List<Integer> search(double[] queryVec, int topK) {
        List<Integer> result = new ArrayList<>();
        if (!available) return result;
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT content_id FROM vec_items WHERE embedding MATCH ? ORDER BY distance LIMIT ?")) {
            ByteBuffer buf = ByteBuffer.allocate(queryVec.length * 4);
            for (double d : queryVec) buf.putFloat((float) d);
            ps.setBytes(1, buf.array());
            ps.setInt(2, topK);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.add(rs.getInt(1));
        } catch (Exception e) {
            System.out.println("[VEC] Ошибка поиска: " + e.getMessage());
        }
        return result;
    }
    public static void createTableIfNeeded(int dimension) {
        if (!available) return;
        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute("CREATE VIRTUAL TABLE IF NOT EXISTS vec_items USING vec0(" +
                    "content_id INTEGER PRIMARY KEY, " +
                    "embedding FLOAT[" + dimension + "])");
        } catch (Exception e) {
            System.out.println("[VEC] Ошибка создания таблицы: " + e.getMessage());
        }
    }
}