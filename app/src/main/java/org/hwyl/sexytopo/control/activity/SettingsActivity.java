package org.hwyl.sexytopo.control.activity;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.hwyl.sexytopo.R;
import org.hwyl.sexytopo.control.io.thirdparty.therion.ThconfigExporter;
import org.hwyl.sexytopo.control.util.GeneralPreferences;

public class SettingsActivity extends SexyTopoActivity {

    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setupMaterialToolbar();
        applyEdgeToEdgeInsets(R.id.rootLayout, true, true);

        getSupportFragmentManager().beginTransaction()
            .replace(R.id.settingsFragment, new SettingsFragment())
            .commit();

        prefListener = (prefs, key) -> {
            if (key.equals("pref_theme")) {
                setTheme();
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
    }


    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
            addPreferencesFromResource(R.xml.general_preferences);

            Preference thconfigEditorPref = findPreference("pref_therion_thconfig_editor");
            if (thconfigEditorPref != null) {
                thconfigEditorPref.setOnPreferenceClickListener(preference -> {
                    openThconfigEditor();
                    return true;
                });
            }
        }

        private void openThconfigEditor() {
            float density = getResources().getDisplayMetrics().density;
            int padding = (int) (16 * density);

            LinearLayout container = new LinearLayout(requireContext());
            container.setOrientation(LinearLayout.VERTICAL);
            container.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

            // Title with underline
            TextView title = new TextView(requireContext());
            title.setText(R.string.settings_export_therion_thconfig_editor_title);
            title.setTextSize(20);
            title.setTypeface(null, Typeface.BOLD);
            title.setPadding(padding, padding, padding, (int) (8 * density));
            container.addView(title);

            // Divider line under title
            View divider = new View(requireContext());
            divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * density)));
            divider.setBackgroundColor(Color.GRAY);
            container.addView(divider);

            // Editor area
            EditText input = new EditText(requireContext());
            input.setGravity(Gravity.START | Gravity.TOP);
            input.setHorizontallyScrolling(false);
            input.setHint(R.string.settings_export_therion_thconfig_editor_hint);
            input.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            input.setPadding(padding, padding, padding, padding);

            String currentTemplate = GeneralPreferences.getTherionThconfigTemplate();
            if (currentTemplate.isEmpty()) {
                currentTemplate = ThconfigExporter.getDefaultContent();
            }
            input.setText(currentTemplate);

            ScrollView scrollView = new ScrollView(requireContext());
            scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            scrollView.setFillViewport(true);
            scrollView.addView(input);
            container.addView(scrollView);

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
            builder.setView(container)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null);

            AlertDialog dialog = builder.create();
            dialog.setOnShowListener(dialogInterface -> {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String template = input.getText().toString();
                    if (ThconfigExporter.isValidTemplate(template)) {
                        GeneralPreferences.setTherionThconfigTemplate(template);
                        dialog.dismiss();
                    } else {
                        Toast.makeText(requireContext(),
                            R.string.settings_export_therion_thconfig_editor_error,
                            Toast.LENGTH_LONG).show();
                    }
                });
            });

            dialog.show();

            // Make dialog 95% of screen height and nearly full width
            if (dialog.getWindow() != null) {
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                dialog.getWindow().setLayout(
                    (int) (screenWidth * 0.95),
                    (int) (screenHeight * 0.95));
            }
            input.requestFocus();
        }
    }
}
