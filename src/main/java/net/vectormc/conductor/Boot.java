package net.vectormc.conductor;

public abstract class Boot {
    Boot() { }

    public abstract void onEnable();
    public abstract void onDisable();
}
