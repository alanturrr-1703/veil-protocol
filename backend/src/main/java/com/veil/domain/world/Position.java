package com.veil.domain.world;

/**
 * A point on the neon-city grid. Used for movement and Shadow radius (hunt) queries.
 */
public record Position(double x, double y) {

    public double distanceTo(Position other) {
        double dx = x - other.x;
        double dy = y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
