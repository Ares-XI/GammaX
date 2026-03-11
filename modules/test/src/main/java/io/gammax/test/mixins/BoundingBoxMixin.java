package io.gammax.test.mixins;

import io.gammax.api.*;
import io.gammax.api.util.At;
import io.gammax.api.util.Mode;
import io.gammax.api.util.Signature;
import io.gammax.test.access.BoundingBoxAccess;
import org.bukkit.util.BoundingBox;

@Mixin(BoundingBox.class)
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
    public abstract double getVolume();

    @Unique
    private int checkCount;

    @Unique
    public static final double EXPAND_FACTOR = 1.1;

//    @Override
    @Unique
    public void expandSymmetrical(double amount) {
        minX -= amount;
        minY -= amount;
        minZ -= amount;
        maxX += amount;
        maxY += amount;
        maxZ += amount;
        System.out.println("[BoundingBox] Expanded by " + amount);
    }

//    @Override
    @Unique
    public String getDimensions() {
        return String.format("BoundingBox{min=(%.2f,%.2f,%.2f), max=(%.2f,%.2f,%.2f)}", minX, minY, minZ, maxX, maxY, maxZ);
    }

//    @Override
    @Unique
    public int getCheckCount() {
        return checkCount;
    }

    @Inject(
            method = "<init>",
            at = At.RETURN
    )
    private void onInit() {
        checkCount = 0;
        System.out.println("[Inject CONSTRUCTOR] New bounding box: " + getDimensions());
    }

    @Inject(
            method = "shift",
            at = At.HEAD,
            signature = @Signature(
                    parameters = {
                            double.class,
                            double.class,
                            double.class
                    },
                    result = BoundingBox.class
            )
    )
    private void onShift(@Arg(0) double x, @Arg(1) double y, @Arg(2) double z) {
        System.out.println("[Inject HEAD] Shifting by (" + x + ", " + y + ", " + z + ")");
        System.out.println("[Inject HEAD] Current: " + getDimensions());
    }

    @Inject(
            method = "getVolume",
            at = At.RETURN,
            mode = Mode.CANSEL
    )
    private double onGetVolume() {
        return (maxX - minX) * (maxY - minY) * (maxZ - minZ) * EXPAND_FACTOR;
    }

    @Inject(
            method = "contains",
            at = At.HEAD,
            signature = @Signature(
                    parameters = {
                            double.class,
                            double.class,
                            double.class
                    },
                    result = boolean.class
            )
    )
    private void onContains(@Arg(0) double x, @Arg(1) double y, @Arg(2) double z) {
        checkCount++;
        System.out.println("[Inject HEAD] Contains check #" + checkCount + " for (" + x + ", " + y + ", " + z + ")");
    }
}