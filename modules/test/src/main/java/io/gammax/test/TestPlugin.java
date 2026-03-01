package io.gammax.test;

import io.gammax.test.access.VectorAccess;
import io.gammax.test.commads.TestMixinCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class TestPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getCommand("test-mixin").setExecutor(new TestMixinCommand());
        this.getClassLoader();
    }

}
