package org.hwyl.sexytopo.control.table;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.hwyl.sexytopo.R;
import org.hwyl.sexytopo.model.survey.Leg;
import org.hwyl.sexytopo.model.survey.Station;
import org.hwyl.sexytopo.model.table.TableCol;

/** Utility class for displaying the individual raw readings that make up a promoted leg. */
public class LegReadingsDialog {

    private LegReadingsDialog() {}

    /**
     * Shows a read-only dialog listing every raw shot that was promoted into {@code leg}.
     *
     * @param context the calling context
     * @param from the originating station of the leg
     * @param leg the promoted leg whose {@link Leg#getPromotedFrom()} readings are shown
     */
    public static void show(Context context, Station from, Leg leg) {
        Leg[] readings = leg.getPromotedFrom();

        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_leg_readings, null);
        TableLayout table = dialogView.findViewById(R.id.readingsTable);

        for (int i = 0; i < readings.length; i++) {
            Leg reading = readings[i];
            Leg display = reading.wasShotBackwards() ? reading.reverse() : reading;

            View rowView = inflater.inflate(R.layout.dialog_leg_readings_row, table, false);

            ((TextView) rowView.findViewById(R.id.readingsRowDistance))
                    .setText(TableCol.DISTANCE.format(display.getDistance()));
            ((TextView) rowView.findViewById(R.id.readingsRowAzimuth))
                    .setText(TableCol.AZIMUTH.format(display.getAzimuth()));
            ((TextView) rowView.findViewById(R.id.readingsRowInclination))
                    .setText(TableCol.INCLINATION.format(display.getInclination()));

            int bgColor =
                    ContextCompat.getColor(
                            context,
                            i % 2 == 0 ? R.color.tableBackground : R.color.tableBackgroundAlt);
            rowView.setBackgroundColor(bgColor);

            table.addView(rowView);
        }

        String fromName = leg.wasShotBackwards() ? leg.getDestination().getName() : from.getName();
        String toName = leg.wasShotBackwards() ? from.getName() : leg.getDestination().getName();
        String title = context.getString(R.string.menu_leg_title_dynamic, fromName, toName);

        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(dialogView)
                .setPositiveButton(R.string.dialog_readings_close, null)
                .show();
    }
}
