package tool;

import java.util.List;
import java.util.Map;

public class HelloModule implements Module {
    private ModuleContext ctx;
    private boolean enabled = false;

    @Override
    public String getName() { return "hello"; }

    @Override
    public void onStartup(ModuleContext context) {
        context.log("[HELLO] Модуль загружен при старте NeuralFloppy.");
    }

    @Override
    public void enable(ModuleContext context) {
        this.ctx = context;
        enabled = true;
        ctx.logInfo("Модуль включён.");

        // Registry
        ctx.register("hello", "greeting", "Hello, World!");
        ctx.logInfo("Registered greeting: " + ctx.get("hello", "greeting"));

        // Config
        ctx.setConfig("hello.mode", "test");
        ctx.logInfo("Config: " + ctx.getConfig("hello.mode", "default"));

        // Модули
        ctx.logInfo("Модулей: " + ctx.listModules());
    }

    @Override
    public void disable() {
        if (ctx != null) {
            ctx.log("[HELLO] Модуль выключен.");
        }
        enabled = false;
    }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public Map<String, CommandHandler> getCommands() {
        return Map.of(
                ":hello", args -> {
                    if (!enabled || ctx == null) return;
                    if (args.length < 2) {
                        ctx.log("[HELLO] Используй: :hello search <текст> | :hello save <текст> | :hello ask <промпт> | :hello status");
                        return;
                    }
                    String sub = args[1];
                    switch (sub) {
                        case "status" -> ctx.log("[HELLO] Статус: " + (enabled ? "активен" : "выключен"));
                        case "search" -> {
                            if (args.length < 3) { ctx.log("[HELLO] Укажи текст поиска."); return; }
                            String q = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                            List<String> found = ctx.findContext(q);
                            ctx.log("[HELLO] Найдено по '" + q + "': " + found.size() + " фрагментов.");
                        }
                        case "save" -> {
                            if (args.length < 3) { ctx.log("[HELLO] Укажи текст для сохранения."); return; }
                            String text = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                            ctx.saveMessage("SYSTEM", text);
                            ctx.log("[HELLO] Сохранено: " + text);
                        }
                        case "ask" -> {
                            if (args.length < 3) { ctx.log("[HELLO] Укажи промпт."); return; }
                            String prompt = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                            String answer = ctx.callLLM(prompt, "local");
                            ctx.log("[HELLO] Ответ LLM: " + answer);
                        }
                        case "test" -> {
                            ctx.log("Колонки: " + ctx.listColumns());
                            ctx.createColumn("test_module_col");
                            ctx.saveEpisode("test_module_col",
                                    new Episode("{\"hp\":30}", "attack", 1.0, "{\"hp\":25}", System.currentTimeMillis()));
                            List<Episode> eps = ctx.getEpisodes("test_module_col", 10);
                            ctx.log("Эпизодов: " + eps.size());
                            double q = ctx.getQValue("test_module_col", "{\"hp\":30}", "attack");
                            ctx.log("Q-значение: " + q);
                        }
                        default -> ctx.log("[HELLO] Неизвестная подкоманда: " + sub);
                    }
                }
        );
    }

    @Override
    public void onShutdown(ModuleContext context) {
        if (enabled) {
            context.log("[HELLO] Модуль выгружен при завершении NeuralFloppy.");
        }
        enabled = false;
    }

}