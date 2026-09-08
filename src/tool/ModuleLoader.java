package tool;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ModuleLoader {
    private static final Map<String, Module> modules = new LinkedHashMap<>();

    public static void loadAll(Path dir) {
        if (!Files.isDirectory(dir)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.jar")) {
            for (Path jar : stream) {
                // В 2.0-DT просто показываем, что нашли jar. Загрузку из jar сделаем позже.
                System.out.println("[MODULES] Найден модуль: " + jar.getFileName());
            }
        } catch (IOException e) {
            System.out.println("[MODULES] Ошибка сканирования папки модулей: " + e.getMessage());
        }
    }

    public static void register(Module module) {
        modules.put(module.getName(), module);
    }

    public static void enable(String name, ModuleContext context) {
        Module module = modules.get(name);
        if (module != null) {
            module.enable(context);
            System.out.println("[MODULES] Модуль '" + name + "' включён.");
        } else {
            System.out.println("[MODULES] Модуль '" + name + "' не найден.");
        }
    }

    public static void disable(String name) {
        Module module = modules.get(name);
        if (module != null) {
            module.disable();
            System.out.println("[MODULES] Модуль '" + name + "' выключен.");
        } else {
            System.out.println("[MODULES] Модуль '" + name + "' не найден.");
        }
    }

    public static List<String> listNames() {
        return new ArrayList<>(modules.keySet());
    }
}