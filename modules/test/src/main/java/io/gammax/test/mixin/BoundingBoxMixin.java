package io.gammax.test.mixin;

import io.gammax.api.Inject;
import io.gammax.api.Mixin;
import io.gammax.api.Shadow;
import io.gammax.api.Unique;
import io.gammax.api.enums.InjectAt;
import io.gammax.api.enums.Mode;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;

@Mixin(target = BoundingBox.class)
public abstract class BoundingBoxMixin {

    @Shadow
    private double minX;

    @Shadow
    private double minY;

    @Shadow
    private double minZ;

    @Shadow
    private double maxX;

    @Shadow
    private double maxY;

    @Shadow
    private double maxZ;

    @Shadow
    public abstract @NotNull BoundingBox resize(double x1, double y1, double z1, double x2, double y2, double z2);

    @Unique
    private double modifier;

    @Inject(method = "<init>", at = @Inject.At(value = InjectAt.RETURN, mode = Mode.CANSEL))
    private void init() {
        System.out.println("Создан экземпляр значение modifier на: 2");
        modifier = 2.0;
    }

    @Unique
    private void testResize() {
        System.out.println("Сумма всех значений: " + minX + minZ + minZ + maxX + maxY + maxZ);
        System.out.println("Умножаю modifier самого на себя");
        modifier = modifier * modifier;
        System.out.println("Умножаю все значения на модификатор");
        minX *= modifier;
        minY *= modifier;
        minZ *= modifier;
        maxX *= modifier;
        maxY *= modifier;
        maxZ *= modifier;
        System.out.println("Выполняю resize");
        BoundingBox box = resize(minX, minY, minZ, maxX, maxY, maxZ);
        System.out.println("Новый хитбокс: " + box);
    }
}
