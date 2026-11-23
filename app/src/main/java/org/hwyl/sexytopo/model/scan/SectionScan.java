package org.hwyl.sexytopo.model.scan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hwyl.sexytopo.model.geometry.Coord2D;

/**
 * A lidar scan taken at a station by a cross-section scanner, drawn behind the station's
 * cross-section. Only the lidar's returns are kept. The line it was read from is kept too, so
 * writing it back loses nothing SexyTopo doesn't read.
 */
public class SectionScan {

    /** Which way the scanner looked along the survey when the scan was taken. */
    public enum Facing {
        ON,
        BACK
    }

    /** One return: the lidar's angle (degrees, clockwise seen from its top) and distance. */
    public static class Point {
        public final float degrees;
        public final float millimetres;
        public final int quality;

        public Point(float degrees, float millimetres, int quality) {
            this.degrees = degrees;
            this.millimetres = millimetres;
            this.quality = quality;
        }
    }

    private final String scanName;
    private final Facing facing;
    private final List<Point> points;
    private final List<Coord2D> pointsInSection;
    private final String source;

    public SectionScan(String scanName, Facing facing, List<Point> points, String source) {
        this.scanName = scanName;
        this.facing = facing;
        this.source = source;
        this.points = Collections.unmodifiableList(new ArrayList<>(points));
        this.pointsInSection = Collections.unmodifiableList(toSection(points, facing));
    }

    public String getScanName() {
        return scanName;
    }

    /** The line of the scanner file this scan was read from. */
    public String getSource() {
        return source;
    }

    public Facing getFacing() {
        return facing;
    }

    public List<Point> getPoints() {
        return points;
    }

    /** The points in the cross-section's frame: metres from the station, x right, y down. */
    public List<Coord2D> getPointsInSection() {
        return pointsInSection;
    }

    /**
     * The scanner is held with the lidar's top away from the user, so seen from behind it the
     * angles run anticlockwise from straight up. Facing back turns the section round.
     */
    static List<Coord2D> toSection(List<Point> points, Facing facing) {
        float mirror = facing == Facing.BACK ? -1 : 1;
        List<Coord2D> coords = new ArrayList<>(points.size());
        for (Point point : points) {
            double radians = Math.toRadians(point.degrees);
            float metres = point.millimetres / 1000;
            float x = (float) (-metres * Math.sin(radians)) * mirror;
            float y = (float) (-metres * Math.cos(radians));
            coords.add(new Coord2D(x, y));
        }
        return coords;
    }
}
