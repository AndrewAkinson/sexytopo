package org.hwyl.sexytopo.comms.scanner;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hwyl.sexytopo.control.io.basic.ScanJsonlTranslater;
import org.hwyl.sexytopo.control.io.basic.SketchJsonTranslater;
import org.hwyl.sexytopo.control.util.CrossSectioner;
import org.hwyl.sexytopo.model.geometry.Coord2D;
import org.hwyl.sexytopo.model.geometry.Projection2D;
import org.hwyl.sexytopo.model.scan.SectionScan;
import org.hwyl.sexytopo.model.scan.SurveyScans;
import org.hwyl.sexytopo.model.sketch.CrossSection;
import org.hwyl.sexytopo.model.sketch.CrossSectionDetail;
import org.hwyl.sexytopo.model.sketch.Sketch;
import org.hwyl.sexytopo.model.survey.Station;
import org.hwyl.sexytopo.model.survey.Survey;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Decides what to do with each section the cross-section scanner sends, and makes the change to the
 * survey: its scan goes on the station, and its drawing into the station's cross-section unless
 * that has been edited in SexyTopo since the revision the scanner's drawing was based on. Also
 * finds the drawings edited here that the scanner doesn't have yet.
 *
 * <p>Drawing revisions made here are even and the scanner's odd, so the two never share a number.
 */
public class ScannerSession {

    public static final String OK = "ok";
    public static final String ERROR = "error";
    public static final String CONFLICT = "conflict";

    public static final String BAD_CHECKSUM = "bad_checksum";
    public static final String UNKNOWN_FORMAT = "unknown_format";
    public static final String NO_STATION = "no_station";
    public static final String BAD_POINTS = "bad_points";
    public static final String BAD_SKETCH = "bad_sketch";

    private static final String ORIENTATION_TAG = "orientation";
    private static final String HORIZONTAL = "horizontal";
    private static final String FACING_TAG = "facing";
    private static final String FACING_BACK = "back";

    // where a new cross-section goes, from its station, as to_sexytopo.py does
    private static final Coord2D NEW_SECTION_OFFSET = new Coord2D(3, 0);

    /**
     * The reply to send, and for a conflict, the change to make if the user chooses the scanner's
     * drawing, or what to note if they keep SexyTopo's.
     */
    public static class Outcome {
        public final JSONObject reply;
        private final Change change;
        private final Change keep;

        private Outcome(JSONObject reply, Change change) {
            this(reply, change, null);
        }

        private Outcome(JSONObject reply, Change change, Change keep) {
            this.reply = reply;
            this.change = change;
            this.keep = keep;
        }

        public boolean isConflict() {
            return CONFLICT.equals(reply.optString(ScannerMessages.RESULT_TAG));
        }

        /** Takes the scanner's drawing over SexyTopo's, returning the reply to send then. */
        public JSONObject takeScannersDrawing() throws JSONException {
            change.apply();
            return change.okReply();
        }

        /**
         * Keeps SexyTopo's drawing, noting the scanner's clashing revision as the one agreed, so
         * SexyTopo's drawing is then sent to the scanner based on it.
         */
        public void keepSexyToposDrawing() throws JSONException {
            if (keep != null) {
                keep.apply();
            }
        }
    }

    /** A drawing edited here that the scanner hasn't got, with the revision to send it as. */
    public static class Outgoing {
        public final String scanName;
        public final int revision;
        public final int basedOn;
        public final String hash;
        public final Sketch drawing;

        private Outgoing(String scanName, int revision, int basedOn, String hash, Sketch drawing) {
            this.scanName = scanName;
            this.revision = revision;
            this.basedOn = basedOn;
            this.hash = hash;
            this.drawing = drawing;
        }
    }

    private interface Change {
        void apply() throws JSONException;

        JSONObject okReply() throws JSONException;
    }

    private final Survey survey;
    private final boolean verticalOnElevation;

    /**
     * New vertical sections go on the elevation if verticalOnElevation, otherwise the plan.
     * Horizontal ones always go on the elevation.
     */
    public ScannerSession(Survey survey, boolean verticalOnElevation) {
        this.survey = survey;
        this.verticalOnElevation = verticalOnElevation;
    }

    /** The reply to a frame from the scanner that was dropped unread. */
    public static JSONObject onDropped(String reason) throws JSONException {
        String replyReason =
                ScannerFrames.DROPPED_UNKNOWN_FORMAT.equals(reason) ? UNKNOWN_FORMAT : BAD_CHECKSUM;
        return ScannerMessages.reply(null, 0, ERROR, replyReason);
    }

    public Outcome onSection(JSONObject message) throws JSONException {
        JSONObject scanJson = message.optJSONObject(ScannerMessages.SCAN_TAG);
        JSONObject sketchDoc = message.optJSONObject(ScannerMessages.SKETCH_TAG);
        int revision = sketchDoc == null ? 0 : sketchDoc.optInt(ScannerMessages.REVISION_TAG, 0);
        int basedOn = message.optInt(ScannerMessages.BASED_ON_TAG, 0);

        String scanName = scanJson == null ? null : scanJson.optString(ScannerMessages.SCAN_TAG);
        if (scanName == null
                || scanName.isEmpty()
                || !ScanJsonlTranslater.FORMAT.equals(
                        scanJson.optString(ScannerMessages.FORMAT_TAG))) {
            return error(scanName, revision, UNKNOWN_FORMAT);
        }

        Station station = survey.getStationByName(scanJson.optString(ScannerMessages.STATION_TAG));
        if (station == null) {
            return error(scanName, revision, NO_STATION);
        }

        try {
            ScanJsonlTranslater.toScan(scanJson);
        } catch (JSONException | IllegalArgumentException e) {
            return error(scanName, revision, BAD_POINTS);
        }

        Sketch drawing = null;
        if (sketchDoc != null) {
            JSONObject sketchJson = sketchDoc.optJSONObject(ScannerMessages.SKETCH_TAG);
            if (!ScannerMessages.SKETCH_FORMAT.equals(
                            sketchDoc.optString(ScannerMessages.FORMAT_TAG))
                    || sketchJson == null) {
                return error(scanName, revision, BAD_SKETCH);
            }
            drawing = SketchJsonTranslater.toSubSketch(sketchJson);
        }

        SurveyScans scans = survey.getScans();
        SectionScan held = scans.get(station);
        JSONObject ok = ScannerMessages.reply(scanName, revision, OK, null);
        if (held != null
                && held.getScanName().equals(scanName)
                && held.getReceived() != null
                && held.getReceived().revision == revision) {
            return new Outcome(ok, null);
        }

        CrossSection.Orientation orientation =
                HORIZONTAL.equals(scanJson.optString(ORIENTATION_TAG))
                        ? CrossSection.Orientation.HORIZONTAL
                        : CrossSection.Orientation.VERTICAL;
        boolean facingBack = FACING_BACK.equals(scanJson.optString(FACING_TAG));
        CrossSectionDetail detail = findSection(station);
        final Sketch newDrawing = drawing;

        Change change =
                new Change() {
                    public void apply() throws JSONException {
                        Sketch shown = putDrawing(station, orientation, facingBack, newDrawing);
                        Station previous = scans.findStation(scanName);
                        if (previous != null) {
                            SectionScan moved = scans.remove(previous);
                            if (previous != station) {
                                removeIfUnedited(previous, moved);
                            }
                        }
                        scans.put(
                                station,
                                ScanJsonlTranslater.withReceived(scanJson, revision, hash(shown)));
                        survey.setSaved(false);
                    }

                    public JSONObject okReply() {
                        return ok;
                    }
                };

        if (detail != null && drawing != null && isEditedSince(detail, held, basedOn)) {
            Change keep = null;
            if (held != null && held.getScanName().equals(scanName)) {
                keep =
                        new Change() {
                            public void apply() throws JSONException {
                                agreed(scanName, revision, hash(newDrawing));
                            }

                            public JSONObject okReply() {
                                return null;
                            }
                        };
            }
            return new Outcome(
                    ScannerMessages.reply(scanName, revision, CONFLICT, null), change, keep);
        }
        change.apply();
        return new Outcome(ok, null);
    }

    /**
     * The drawings of scans on their stations that differ from the revision agreed with the
     * scanner, each numbered as the next even revision.
     */
    public List<Outgoing> changedDrawings() throws JSONException {
        List<Outgoing> changed = new ArrayList<>();
        for (Map.Entry<String, Station> entry : ScannerMessages.scanStations(survey).entrySet()) {
            Sketch drawing = findSection(entry.getValue()).getSketch();
            SectionScan.Received received = survey.getScans().get(entry.getValue()).getReceived();
            String drawingHash = hash(drawing);
            boolean differs =
                    received == null
                            ? !SketchJsonTranslater.isSketchEmpty(drawing)
                            : !drawingHash.equals(received.sketchHash);
            if (differs) {
                int agreed = received == null ? 0 : received.revision;
                changed.add(
                        new Outgoing(
                                entry.getKey(), nextEven(agreed), agreed, drawingHash, drawing));
            }
        }
        return changed;
    }

    /** The smallest even revision above this one. */
    static int nextEven(int revision) {
        return (revision + 2) & ~1;
    }

    /** Notes a drawing revision as agreed with the scanner, with a hash of the drawing then. */
    public void agreed(String scanName, int revision, String drawingHash) throws JSONException {
        SurveyScans scans = survey.getScans();
        Station station = scans.findStation(scanName);
        if (station == null) {
            return;
        }
        JSONObject line = new JSONObject(scans.get(station).getSource());
        scans.put(station, ScanJsonlTranslater.withReceived(line, revision, drawingHash));
        survey.setSaved(false);
    }

    private Outcome error(String scanName, int revision, String reason) throws JSONException {
        String scan = scanName == null || scanName.isEmpty() ? null : scanName;
        return new Outcome(ScannerMessages.reply(scan, revision, ERROR, reason), null);
    }

    /**
     * Whether the station's drawing has changed in SexyTopo since it took revision basedOn from the
     * scanner. An empty drawing has nothing to lose, so it never counts as edited.
     */
    private static boolean isEditedSince(CrossSectionDetail detail, SectionScan held, int basedOn)
            throws JSONException {
        if (SketchJsonTranslater.isSketchEmpty(detail.getSketch())) {
            return false;
        }
        SectionScan.Received received = held == null ? null : held.getReceived();
        return received == null
                || received.revision != basedOn
                || !hash(detail.getSketch()).equals(received.sketchHash);
    }

    /**
     * A scan has moved to another station, so its old station's section goes too, unless it has
     * been edited here since it came from the scanner.
     */
    private void removeIfUnedited(Station previous, SectionScan moved) throws JSONException {
        CrossSectionDetail detail = findSection(previous);
        if (detail == null) {
            return;
        }
        Sketch sketch = detail.getSketch();
        SectionScan.Received received = moved.getReceived();
        if (SketchJsonTranslater.isSketchEmpty(sketch)
                || (received != null && hash(sketch).equals(received.sketchHash))) {
            parentOf(detail).deleteDetail(detail);
        }
    }

    /**
     * Puts the scanner's drawing in the station's cross-section, making the section if there isn't
     * one, and returns the drawing the section then has. A section turned horizontal moves off the
     * plan to the elevation.
     */
    private Sketch putDrawing(
            Station station,
            CrossSection.Orientation orientation,
            boolean facingBack,
            Sketch drawing) {
        CrossSectionDetail detail = findSection(station);
        if (detail == null) {
            Sketch subSketch = drawing == null ? new Sketch() : drawing;
            addSection(station, orientation, facingBack, subSketch);
            return subSketch;
        }
        if (drawing == null) {
            return detail.getSketch();
        }
        if (detail.getCrossSection().getOrientation() == orientation) {
            detail.setSketch(drawing);
        } else if (orientation == CrossSection.Orientation.HORIZONTAL
                && parentOf(detail) == survey.getPlanSketch()) {
            survey.getPlanSketch().deleteDetail(detail);
            addSection(station, orientation, facingBack, drawing);
        } else {
            parentOf(detail)
                    .replaceCrossSectionDetail(
                            detail,
                            new CrossSectionDetail(
                                    newSection(station, orientation, facingBack),
                                    detail.getPosition(),
                                    drawing));
        }
        return drawing;
    }

    /** A new section beside its station, on the plan or the elevation as set. */
    private void addSection(
            Station station,
            CrossSection.Orientation orientation,
            boolean facingBack,
            Sketch drawing) {
        boolean onElevation =
                orientation == CrossSection.Orientation.HORIZONTAL || verticalOnElevation;
        Projection2D projection = onElevation ? Projection2D.EXTENDED_ELEVATION : Projection2D.PLAN;
        Coord2D position =
                projection.project(survey).getStationMap().get(station).plus(NEW_SECTION_OFFSET);
        survey.getSketch(projection)
                .addCrossSection(
                        new CrossSectionDetail(
                                newSection(station, orientation, facingBack), position, drawing));
    }

    private CrossSection newSection(
            Station station, CrossSection.Orientation orientation, boolean facingBack) {
        if (orientation == CrossSection.Orientation.HORIZONTAL) {
            return CrossSection.horizontal(station);
        }
        float angle = CrossSectioner.getAngleOfSection(survey, station);
        if (facingBack) {
            angle = (angle + 180) % 360;
        }
        return new CrossSection(station, angle);
    }

    /** The station's cross-section in the plan, or else the elevation, or null. */
    private CrossSectionDetail findSection(Station station) {
        CrossSectionDetail detail = survey.getPlanSketch().getCrossSectionDetail(station);
        return detail != null ? detail : survey.getElevationSketch().getCrossSectionDetail(station);
    }

    private Sketch parentOf(CrossSectionDetail detail) {
        return survey.getPlanSketch().getCrossSectionDetails().contains(detail)
                ? survey.getPlanSketch()
                : survey.getElevationSketch();
    }

    /** A hash of a drawing as SexyTopo writes it, to tell later whether it has been edited. */
    static String hash(Sketch sketch) throws JSONException {
        String json = SketchJsonTranslater.toSubSketchJson(sketch).toString();
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(json.getBytes(StandardCharsets.UTF_8));
            return String.format("%064x", new BigInteger(1, digest));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
