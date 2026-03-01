package io.gammax.test.mixins;

import io.gammax.api.*;
import io.gammax.api.util.At;
import io.gammax.api.util.Mode;
import io.gammax.api.util.Signature;
import org.bukkit.util.Vector;

@Mixin(Vector.class)
public abstract class VectorMixin {

    @Shadow
    protected double x;

    @Shadow
    protected double y;

    @Shadow
    protected double z;

    @Shadow
    public abstract Vector multiply(double m);

    @Shadow
    public abstract Vector add(Vector other);

    @Shadow
    public abstract double length();

    @Shadow
    public abstract Vector clone();

    @Unique
    private int operationCount;

    @Unique
    public static final double EPSILON = 0.0001;

    @Unique
    private String lastOperation;

//    @Override
    @Unique
    public boolean isZeroS() {
        return Math.abs(x) < EPSILON && Math.abs(y) < EPSILON && Math.abs(z) < EPSILON;
    }

//    @Override
    @Unique
    public String getStats() {
        return String.format("Vector{ops=%d, last='%s', pos=(%.2f,%.2f,%.2f)}", operationCount, lastOperation, x, y, z);
    }

//    @Override
    @Unique
    public int getOperationCount() {
        return operationCount;
    }

//    @Override
    @Unique
    public String getLastOperation() {
        return lastOperation;
    }

    @Unique
    private void incrementCount(String operation) {
        operationCount++;
        lastOperation = operation;
        System.out.println("[Vector] Operation #" + operationCount + ": " + operation);
    }

    @Inject(
            method = "multiply",
            at = At.HEAD,
            signature = @Signature(
                    parameters = double.class,
                    result = Vector.class
            )
    )
    private void onMultiply(@Arg double m) {
        incrementCount("multiply(" + m + ")");
        System.out.println("[Inject HEAD] Multiplying by " + m);
    }

//    @Inject(
//            method = "length",
//            at = At.HEAD,
//            mode = Mode.CANSEL,
//            signature = @Signature(result = double.class)
//    )
//    private double onLength() {
//        double len = Math.sqrt(x*x + y*y + z*z);
//        if (len < EPSILON) {
//            System.out.println("[Inject RETURN] Zero-length detected, returning 0");
//            return 0.0;
//        }
//        System.out.println("[Inject RETURN] Length = " + len);
//        return len;
//    }

    @Inject(
            method = "clone",
            at = At.HEAD,
            signature = @Signature(result = Vector.class)
    )
    private void onClone() {
        System.out.println("[Inject HEAD] Cloning vector");
    }

    @Inject(
            method = "add",
            at = At.HEAD,
            signature = @Signature(
                    parameters = Vector.class,
                    result = Vector.class
            )
    )
    private void onAdd(@Arg Vector other) {
        incrementCount("add(" + other + ")");
        System.out.println("[Inject HEAD] Adding " + other);
    }

    @Inject(
            method = "multiply",
            at = At.RETURN,
            mode = Mode.AFTER,
            signature = @Signature(
                    parameters = Vector.class,
                    result = Vector.class
            )
    )
    private void onMultiplyAfter() {
        System.out.println("[Inject AFTER] Result: (" + x + ", " + y + ", " + z + ")");
    }
}