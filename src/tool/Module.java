package tool;

public interface Module {
    String getName();
    void enable(ModuleContext context);
    void disable();
    boolean isEnabled();
}