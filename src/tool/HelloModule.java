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
        ctx.log("[HELLO] Модуль включён. Готов к работе.");
        // Пример использования фич сразу при включении
        List<String> ctxs = ctx.findContext("привет");
        ctx.log("[HELLO] Найдено контекстов по 'привет': " + ctxs.size());
        ctx.saveMessage("SYSTEM", "HelloModule успешно включён.");
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