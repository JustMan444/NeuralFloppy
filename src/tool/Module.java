package tool;

import java.util.List;
import java.util.Map;

public interface Module {
    String getName();
    void onStartup(ModuleContext context);
    void enable(ModuleContext context);
    void disable();
    boolean isEnabled();
    Map<String, CommandHandler> getCommands();
    void onShutdown(ModuleContext context);
    default List<String> getDependencies() { return List.of(); }
    default List<String> getOptionalDependencies() { return List.of(); }
    default String getVersion() { return "1.0"; }
    default String getApiVersion() { return "2.1"; }
}