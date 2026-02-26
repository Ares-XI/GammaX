package io.gammax.test.access;

public interface BoundingBoxAccess {
    void expandSymmetrical(double amount);
    String getDimensions();
    int getCheckCount();
}