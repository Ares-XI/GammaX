package io.gammax.test.mixins;

import io.gammax.api.*;
import io.gammax.api.util.At;
import io.gammax.api.util.Signature;
import org.bukkit.util.NumberConversions;

@Mixin(NumberConversions.class)
public abstract class NumberConversionsMixin {

    @Shadow
    public static native int floor(double num);

    @Unique
    private static int floorCallCount = 0;

    @Inject(
            method = "floor",
            at = At.HEAD,
            signature = @Signature(
                    parameters = double.class,
                    result = int.class
            )
    )
    private static void onFloor(@Arg(0) double num) {
        floorCallCount++;
        System.out.println("[Inject HEAD] floor() called with " + num + " (total calls: " + floorCallCount + ")");
    }

    @Unique
    public static void resetCount() {
        floorCallCount = 0;
    }

    @Unique
    public static int getFloorCalls() {
        return floorCallCount;
    }
}