package io.gammax.test;

import io.gammax.test.commads.TestBoundingBox;
import io.gammax.test.commads.TestVector;
import org.bukkit.plugin.java.JavaPlugin;

public final class TestPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getCommand("test-vector").setExecutor(new TestVector());
        getCommand("test-box").setExecutor(new TestBoundingBox());
    }

}
