package io.gammax.test.commads;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class TestBoundingBox implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        BoundingBox box = new BoundingBox();
        try {
            Field field = BoundingBox.class.getDeclaredField("modifier");
            field.setAccessible(true);
            double value = (double) field.get(box);
            Method method = BoundingBox.class.getDeclaredMethod("testResize");
            method.setAccessible(true);
            commandSender.sendMessage(Double.toString(value));
            method.invoke(box);
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        return true;
    }
}
