package org.hwyl.sexytopo.control.sketch;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import androidx.core.content.ContextCompat;
import org.hwyl.sexytopo.R;
import org.hwyl.sexytopo.control.util.Space2DUtils;
import org.hwyl.sexytopo.model.geometry.Coord2D;
import org.hwyl.sexytopo.model.geometry.Line;
import org.hwyl.sexytopo.model.geometry.Projection2D;
import org.hwyl.sexytopo.model.scan.SectionScan;
import org.hwyl.sexytopo.model.sketch.CrossSection;
import org.hwyl.sexytopo.model.survey.Leg;
import org.hwyl.sexytopo.model.survey.LegFilter;

/**
 * Overrides parent provide a slightly more minimal editing experience for the cross-section sketch
 * view.
 */
public class CrossSectionView extends SketchView {

    /** Fraction of the smaller screen dimension that the longest splay should occupy. */
    private static final float AUTO_FIT_SCREEN_FRACTION = 0.4f;

    /** Fallback zoom when there are no splays to measure. */
    private static final float DEFAULT_FALLBACK_ZOOM = 60.0f;

    private static final float SCAN_POINT_RADIUS_DP = 1f;

    // zoomed out, a point closer than this to the last one drawn is skipped, so dots stay apart
    private static final float SCAN_POINT_SPACING_DP = 4f;

    private boolean autoFitted = false;

    private SectionScan scan = null;
    private final Paint scanPointPaint = new Paint();

    private CrossSection.Orientation orientation = CrossSection.Orientation.VERTICAL;

    // Decides which legs count as being in the plane of the section (and so are drawn solid). A
    // vertical section is judged by the direction of its splays across the passage; a horizontal
    // one by how level they are.
    private Projection2D legPlaneProjection = Projection2D.CROSS_SECTION;

    public CrossSectionView(Context context, AttributeSet attrs) {
        super(context, attrs);
        scanPointPaint.setStyle(Paint.Style.FILL);
        scanPointPaint.setColor(ContextCompat.getColor(context, R.color.scanPoint));
    }

    /** The scanner's scan at this station, drawn behind the section, or null for none. */
    public void setScan(SectionScan scan) {
        this.scan = scan;
        invalidate();
    }

    /** Tells the view which cross-section is being edited, so it can show it appropriately. */
    public void setCrossSection(CrossSection crossSection) {
        this.orientation = crossSection.getOrientation();
        this.legPlaneProjection = crossSection.getProjectionType();
        invalidate();
    }

    /** A horizontal section is drawn like the plan, with north at the top, so it has a compass. */
    @Override
    protected boolean isCompassShown() {
        return orientation == CrossSection.Orientation.HORIZONTAL;
    }

    @Override
    protected boolean isLegInPlane(Leg leg) {
        return legPlaneProjection.isLegInPlane(leg);
    }

    @Override
    public void checkForChangedSurvey() {
        if (getWidth() > 0 && getHeight() > 0) {
            if (!autoFitted) {
                autoFitZoom();
                autoFitted = true;
            }
            centreViewOnSurveyPoint(Coord2D.ORIGIN);
        }
    }

    /**
     * Pick a zoom level so the longest splay in the projection occupies AUTO_FIT_SCREEN_FRACTION of
     * the smaller screen dimension.
     */
    private void autoFitZoom() {
        if (state.getProjection() == null) {
            return;
        }
        float longestSplay = 0f;
        for (Line<Coord2D> line : state.getProjection().getLegLines(LegFilter.SPLAYS)) {
            float length = Space2DUtils.getDistance(line.getStart(), line.getEnd());
            if (length > longestSplay) {
                longestSplay = length;
            }
        }
        if (longestSplay <= 0f && scan != null) {
            for (Coord2D point : scan.getPointsInSection()) {
                longestSplay =
                        Math.max(longestSplay, Space2DUtils.getDistance(Coord2D.ORIGIN, point));
            }
        }
        if (longestSplay <= 0f) {
            setZoom(DEFAULT_FALLBACK_ZOOM);
            return;
        }
        float smallerScreenDim = Math.min(getWidth(), getHeight());
        float targetPixels = smallerScreenDim * AUTO_FIT_SCREEN_FRACTION;
        setZoom(targetPixels / longestSplay);
    }

    /** A cross-section is drawn on its own, without the surveys linked to its survey. */
    @Override
    protected boolean areConnectedSurveysShown() {
        return false;
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(Canvas canvas) {
        updateViewBounds();

        drawGrid(canvas);
        drawScan(canvas);
        drawSurvey(canvas);
        drawToolFeedback(canvas);
        drawLegend(canvas);
        drawCompass(canvas);
    }

    private void drawScan(Canvas canvas) {
        if (scan == null) {
            return;
        }
        SketchPaints.applyAntiAliasPreference(scanPointPaint);
        float offsetX = viewport.getOffsetX();
        float offsetY = viewport.getOffsetY();
        float scale = viewport.getScale();
        float radius = ScreenUnits.dpToPixels(SCAN_POINT_RADIUS_DP);
        float spacing = ScreenUnits.dpToPixels(SCAN_POINT_SPACING_DP);
        float minSquared = spacing * spacing;
        float lastX = Float.NaN;
        float lastY = Float.NaN;
        // the points are in angle order, so neighbours on screen are neighbours in the list
        for (Coord2D point : scan.getPointsInSection()) {
            float x = (point.x - offsetX) * scale;
            float y = (point.y - offsetY) * scale;
            float dx = x - lastX;
            float dy = y - lastY;
            if (dx * dx + dy * dy < minSquared) {
                continue;
            }
            canvas.drawCircle(x, y, radius, scanPointPaint);
            lastX = x;
            lastY = y;
        }
    }

    protected ViewContext getViewContext() {
        return ViewContext.CROSS_SECTION;
    }
}
