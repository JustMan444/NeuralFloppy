// В 2.0-DT просто показываем, что нашли jar. Загрузку из jar сделаем позже.
package tool;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ModuleLoader {
    private static final Map<String, Module> modules = new LinkedHashMap<>();
    private static final Map<String, CommandHandler> commandRegistry = new LinkedHashMap<>();

    public static void register(Module module) {
        modules.put(module.getName(), module);
        // Команды регистрируем только при включении модуля
    }
    public static Module getModule(String name) {
        return modules.get(name);
    }

    public static boolean isEnabled(String name) {
        Module m = modules.get(name);
        return m != null && m.isEnabled();
    }
    public static void loadAll(Path dir) {
        if (!Files.isDirectory(dir)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.jar")) {
            for (Path jar : stream) {
                System.out.println("[MODULES] Найден jar: " + jar.getFileName());
            }
        } catch (IOException e) {
            System.out.println("[MODULES] Ошибка сканирования: " + e.getMessage());
        }
    }
    public static void shutdownAll(ModuleContext ctx) {
        for (Module module : modules.values()) {
            try {
                module.disable();
                module.onShutdown(ctx);
            } catch (Exception e) { /* лог */ }
        }
    }
    public static void enable(String name, ModuleContext baseContext, NeuralFloppyCore core) {
        Module module = modules.get(name);
        if (module == null) return;
        try {
            ModuleContext personalCtx = new MainModuleSystem(core, name);
            module.enable(personalCtx);
            module.getCommands().forEach((cmd, handler) -> commandRegistry.put(cmd, handler));
            System.out.println("[MODULES] Модуль '" + name + "' включён.");
        } catch (Exception e) {
            System.err.println("[MODULES] Ошибка включения '" + name + "': " + e.getMessage());
        }
    }

    public static void disable(String name) {
        Module module = modules.get(name);
        if (module == null) return;
        module.disable();
        // Убираем команды модуля
        module.getCommands().keySet().forEach(commandRegistry::remove);
        System.out.println("[MODULES] Модуль '" + name + "' выключен.");
    }

    public static List<String> listNames() {
        return new ArrayList<>(modules.keySet());
    }

    public static Map<String, CommandHandler> getCommandRegistry() {
        return commandRegistry;
    }

    public static String executeCommand(String cmd) {
        String[] parts = cmd.split("\\s+");
        CommandHandler handler = commandRegistry.get(parts[0]);
        if (handler == null) return null; // команда не от модуля
        try {
            return handler.execute(parts);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}";
        }
    }
}