package tool;

import java.util.Map;

public interface Module {
    String getName();
    void onStartup(ModuleContext context);
    void enable(ModuleContext context);
    void disable();
    boolean isEnabled();
    Map<String, CommandHandler> getCommands();
    void onShutdown(ModuleContext context);
}