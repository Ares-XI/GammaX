package io.gammax.test.commads;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class TestVector implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        Vector vector = new Vector(2, 2, 2);
        try {
            Field field = Vector.class.getField("str");
            Method method = Vector.class.getMethod("testMultiply");
            commandSender.sendMessage((String) field.get(vector));
            method.invoke(vector);
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        return true;
    }
}
