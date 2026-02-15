package io.gammax.test.mixin;

import io.gammax.api.Mixin;
import io.gammax.api.Shadow;
import io.gammax.api.Unique;
import org.bukkit.util.Vector;

@Mixin(target = Vector.class)
public abstract class VectorMixin {
    @Shadow
    protected double x;

    @Unique
    public static String string = "Строка!";

    @Unique
    public static final String str = "Hello from mixin!";

    @Shadow
    public abstract Vector multiply(double m);

    @Unique
    public void testMultiply() {
        System.out.println(str);
        System.out.println("Умножаю вектор на скаляр: " + x);
        System.out.println("Устанавливаю значение поля str на: \"test\"");
        Vector vec = multiply(x);
        string = "test";
        System.out.println("новый вектор: " + vec.toString());
    }
}
