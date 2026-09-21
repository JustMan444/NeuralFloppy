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
                    if (!enabled || ctx == null) return "{\"error\":\"module disabled\"}";
                    if (args.length < 2) {
                        return "{\"usage\":\":hello search|save|ask|status|test\"}";
                    }
                    String sub = args[1];
                    switch (sub) {
                        case "status" -> {
                            return "{\"status\":\"active\",\"name\":\"hello\"}";
                        }
                        case "search" -> {
                            if (args.length < 3) return "{\"error\":\"no query\"}";
                            String q = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                            java.util.List<String> found = ctx.findContext(q);
                            return "{\"found\":" + found.size() + "}";
                        }
                        case "save" -> {
                            if (args.length < 3) return "{\"error\":\"no text\"}";
                            String text = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                            ctx.saveMessage("SYSTEM", text);
                            return "{\"saved\":true}";
                        }
                        case "ask" -> {
                            if (args.length < 3) return "{\"error\":\"no prompt\"}";
                            String prompt = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                            String answer = ctx.callLLM(prompt, "local");
                            return "{\"answer\":\"" + answer.replace("\"", "\\\"") + "\"}";
                        }
                        default -> {
                            return "{\"error\":\"unknown subcommand\"}";
                        }
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