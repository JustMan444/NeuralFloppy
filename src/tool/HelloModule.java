package tool;

public class HelloModule implements Module {
    private ModuleContext ctx;

    @Override
    public String getName() { return "hello"; }

    @Override
    public void enable(ModuleContext context) {
        this.ctx = context;
        ctx.log("[HELLO] Модуль включён!");
    }

    @Override
    public void disable() {
        ctx.log("[HELLO] Модуль выключен!");
    }

    @Override
    public boolean isEnabled() { return ctx != null; }
}