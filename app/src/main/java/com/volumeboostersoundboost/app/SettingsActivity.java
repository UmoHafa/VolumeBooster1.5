package com.volumeboostersoundboost.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private SharedPreferences prefs;
    private TextView maxSummary;
    private TextView engineSummary;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(BoosterService.PREFS, MODE_PRIVATE);
        final ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(250, 250, 250));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.BLACK);
        scroll.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                scroll.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
                return insets;
            }
        });
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        Button back = new Button(this);
        back.setText("‹  Settings");
        back.setAllCaps(false);
        back.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        back.setTextSize(24f);
        back.setPadding(dp(16), 0, dp(16), 0);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { finish(); }
        });
        content.addView(back, new LinearLayout.LayoutParams(-1, dp(64)));
        addSwitch(content, "Show volume control", "Show or hide the phone media-volume slider.",
                BoosterService.KEY_VOLUME, true, false);
        addSwitch(content, "Non-uniform boost",
                "With Equalizer, boosts low and high frequencies less than middle frequencies. " +
                "Has no effect with Loudness Enhancer. This is not hearing or speaker protection.",
                BoosterService.KEY_SHAPE, true, true);
        maxSummary = addAction(content, "Maximum allowed boost", "", new View.OnClickListener() {
            @Override public void onClick(View view) { chooseMaximum(); }
        });
        engineSummary = addAction(content, "Boost engine", "", new View.OnClickListener() {
            @Override public void onClick(View view) { chooseEngine(); }
        });
        addAction(content, "Reconnect audio effects",
                "Re-create effects after starting your player or changing the audio output. " +
                "To discover a player session, restart that player while the service is running.",
                new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        if (!BoosterService.isRunning()) {
                            Toast.makeText(SettingsActivity.this, "Start the service on the main screen first.", Toast.LENGTH_LONG).show();
                        } else {
                            notifyEngine(true);
                            Toast.makeText(SettingsActivity.this, "Reconnecting. Check Boost diagnostics on the main screen.", Toast.LENGTH_LONG).show();
                        }
                    }
                });
        addAction(content, "Reset Boost to 0%", "Immediately remove the requested amplification.",
                new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        prefs.edit().putInt(BoosterService.KEY_BOOST, 0).apply();
                        notifyEngine(false);
                        Toast.makeText(SettingsActivity.this, "Boost reset to 0%", Toast.LENGTH_SHORT).show();
                    }
                });
        addAction(content, "Phone sound settings", "Open Android's sound controls.",
                new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        try { startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS)); }
                        catch (RuntimeException error) {
                            Toast.makeText(SettingsActivity.this, "Sound settings unavailable.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
        TextView footer = text("v1.3 source build\nThe 0–40 control scale requests up to +12 dB from one effect. " +
                "The phone may apply less or bypass it. No automatic measurement of audible gain.", 14);
        footer.setPadding(dp(16), dp(20), dp(16), dp(24));
        content.addView(footer);
        setContentView(scroll);
        scroll.requestApplyInsets();
        updateSummaries();
    }

    private void addSwitch(LinearLayout parent, String title, String description,
                           final String key, boolean defaultValue, final boolean affectsAudio) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(20), dp(16), dp(20));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 18));
        labels.addView(text(description, 15));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch toggle = new Switch(this);
        toggle.setContentDescription(title);
        toggle.setChecked(prefs.getBoolean(key, defaultValue));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.setMargins(dp(14), 0, 0, 0);
        row.addView(toggle, params);
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                prefs.edit().putBoolean(key, checked).apply();
                if (affectsAudio) notifyEngine(false);
            }
        });
        parent.addView(row);
        divider(parent);
    }

    private TextView addAction(LinearLayout parent, String title, String description, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(20), dp(16), dp(20));
        row.addView(text(title, 18));
        TextView summary = text(description, 15);
        row.addView(summary);
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(click);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        divider(parent);
        return summary;
    }

    private void chooseMaximum() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(12), dp(20), dp(12));
        final TextView value = text("", 20);
        final SeekBar seek = new SeekBar(this);
        seek.setMax(BoostConfig.HARD_LIMIT);
        seek.setProgress(BoostConfig.clamp(prefs.getInt(BoosterService.KEY_MAX, BoostConfig.HARD_LIMIT), BoostConfig.HARD_LIMIT));
        value.setText(seek.getProgress() + "%");
        body.addView(value);
        body.addView(seek, new LinearLayout.LayoutParams(-1, dp(54)));
        body.addView(text("Any positive boost can distort audio or damage hearing/speakers. " +
                "A lower maximum is a limit, not a safety guarantee.", 15));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean user) { value.setText(progress + "%"); }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        new AlertDialog.Builder(this).setTitle("Maximum allowed boost").setView(body)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        int maximum = seek.getProgress();
                        int boost = BoostConfig.clamp(prefs.getInt(BoosterService.KEY_BOOST, 0), maximum);
                        prefs.edit().putInt(BoosterService.KEY_MAX, maximum)
                                .putInt(BoosterService.KEY_BOOST, boost).apply();
                        notifyEngine(false);
                        updateSummaries();
                    }
                }).show();
    }

    private void chooseEngine() {
        final String[] names = {"Automatic", "Equalizer", "Loudness Enhancer", "Dynamics Processing"};
        int current = Math.max(0, Math.min(3, prefs.getInt(BoosterService.KEY_ENGINE, 0)));
        new AlertDialog.Builder(this).setTitle("Try one engine at a time")
                .setSingleChoiceItems(names, current, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        // Reset before changing engines to avoid a surprise jump in loudness.
                        prefs.edit().putInt(BoosterService.KEY_ENGINE, which)
                                .putInt(BoosterService.KEY_BOOST, 0).apply();
                        notifyEngine(true);
                        updateSummaries();
                        dialog.dismiss();
                        Toast.makeText(SettingsActivity.this, "Engine changed. Boost reset to 0%; raise it slowly.", Toast.LENGTH_LONG).show();
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void updateSummaries() {
        maxSummary.setText(BoostConfig.clamp(prefs.getInt(BoosterService.KEY_MAX, BoostConfig.HARD_LIMIT), BoostConfig.HARD_LIMIT) + "% — limits the Boost slider.");
        engineSummary.setText(BoostConfig.engineName(prefs.getInt(BoosterService.KEY_ENGINE, 0)) +
                "\nAutomatic tries Dynamics Processing first on Android 9+, then Loudness Enhancer, then Equalizer.");
    }

    private void notifyEngine(boolean retry) {
        if (!BoosterService.isRunning()) return;
        Intent intent = new Intent(this, BoosterService.class)
                .setAction(retry ? BoosterService.ACTION_RETRY : BoosterService.ACTION_SET_BOOST)
                .putExtra(BoosterService.EXTRA_BOOST_PERCENT, prefs.getInt(BoosterService.KEY_BOOST, 0));
        startService(intent);
    }

    private void divider(LinearLayout parent) {
        View line = new View(this);
        line.setBackgroundColor(Color.rgb(220, 220, 220));
        parent.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private TextView text(String value, int size) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(size >= 18 ? Color.rgb(35, 35, 35) : Color.rgb(100, 100, 100));
        text.setTextSize(size);
        return text;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
