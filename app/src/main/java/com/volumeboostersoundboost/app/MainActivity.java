package com.volumeboostersoundboost.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.media.audiofx.AudioEffect;
import android.util.TypedValue;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.Toast;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class MainActivity extends Activity {

    private static final int COLOR_DARK = Color.rgb(35, 35, 35);
    private static final int COLOR_MUTED = Color.rgb(100, 100, 100);
    private static final int COLOR_GREEN = Color.rgb(27, 128, 78);
    private static final int COLOR_RED = Color.rgb(198, 52, 52);

    private AudioManager audioManager;
    private TextView volumeLabel;
    private TextView boostLabel;
    private TextView engineStatusLabel;
    private SeekBar volumeSeek;
    private SeekBar boostSeek;
    private Button serviceButton;
    private ImageButton menuButton;
    private SharedPreferences prefs;
    private boolean serviceRunning;
    private LinearLayout volumeRow;
    private boolean volumeDragging;

    private static final String TAG = "VolumeBoosterAds";
    private static final long INTERSTITIAL_COOLDOWN_MS = 120_000L;
    private static final int STOPS_PER_INTERSTITIAL = 3;

    private FrameLayout bannerContainer;
    private TextView bannerStatus;
    private AdView bannerAdView;
    private InterstitialAd interstitialAd;
    private boolean adsInitialized;
    private int serviceStopsSinceInterstitial;
    private long lastInterstitialShownAt;
    private ConsentInformation consentInformation;

    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusUpdater = new Runnable() {
        @Override
        public void run() {
            refreshEngineStatus();
            statusHandler.postDelayed(this, 750);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        prefs = getSharedPreferences(BoosterService.PREFS, MODE_PRIVATE);
        // v1.4: DynamicsProcessing-first boost engine with safe 0–40 control scale.
        // Reset old boost values so an update never starts unexpectedly loud.
        if (prefs.getInt("settings_schema", 0) < 4) {
            prefs.edit()
                    .putInt("settings_schema", 4)
                    .putInt(BoosterService.KEY_BOOST, 0)
                    .putInt(BoosterService.KEY_MAX, BoostConfig.HARD_LIMIT)
                    .putInt(BoosterService.KEY_ENGINE, BoostConfig.AUTO)
                    .apply();
        }
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bannerAdView != null) bannerAdView.resume();
        syncUi();
        statusHandler.removeCallbacks(statusUpdater);
        statusHandler.postDelayed(statusUpdater, 350);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (BoosterService.isRunning() && panelSession() > 0) {
            startService(new Intent(this, BoosterService.class)
                    .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, panelSession()));
        }
    }

    private int panelSession() {
        Intent intent = getIntent();
        if (intent == null || !AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL.equals(intent.getAction())) return 0;
        return Math.max(0, intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0));
    }

    @Override
    protected void onPause() {
        statusHandler.removeCallbacks(statusUpdater);
        if (bannerAdView != null) bannerAdView.pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (bannerAdView != null) {
            bannerAdView.destroy();
            bannerAdView = null;
        }
        interstitialAd = null;
        super.onDestroy();
    }

    private void buildUi() {
        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(0, dp(8), 0, 0);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.BLACK);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                root.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop() + dp(8),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
                return insets;
            }
        });
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), 0, dp(8), 0);

        menuButton = new ImageButton(this);
        menuButton.setImageResource(R.drawable.ic_menu);
        menuButton.setColorFilter(COLOR_DARK);
        menuButton.setBackground(roundBackground(Color.rgb(232, 232, 232), 30));
        menuButton.setPadding(dp(16), dp(16), dp(16), dp(16));
        menuButton.setContentDescription("Menu");
        top.addView(menuButton, new LinearLayout.LayoutParams(dp(58), dp(58)));

        serviceButton = new Button(this);
        serviceButton.setTextSize(18f);
        serviceButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        serviceButton.setTextColor(Color.WHITE);
        serviceButton.setAllCaps(false);
        LinearLayout.LayoutParams serviceParams = new LinearLayout.LayoutParams(0, dp(58), 1f);
        serviceParams.setMargins(dp(10), 0, 0, 0);
        top.addView(serviceButton, serviceParams);
        content.addView(top, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(66)));

        engineStatusLabel = new TextView(this);
        engineStatusLabel.setTextSize(14f);
        engineStatusLabel.setTextColor(COLOR_MUTED);
        engineStatusLabel.setGravity(Gravity.CENTER);
        engineStatusLabel.setPadding(dp(12), dp(2), dp(12), dp(10));
        content.addView(engineStatusLabel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        volumeRow = new LinearLayout(this);
        volumeRow.setOrientation(LinearLayout.HORIZONTAL);
        volumeRow.setGravity(Gravity.CENTER_VERTICAL);
        volumeRow.setPadding(dp(8), dp(4), dp(14), 0);

        volumeLabel = label("Vol.: 0%");
        volumeRow.addView(volumeLabel, new LinearLayout.LayoutParams(dp(112), LinearLayout.LayoutParams.WRAP_CONTENT));
        volumeSeek = new SeekBar(this);
        volumeSeek.setMax(100);
        volumeRow.addView(volumeSeek, new LinearLayout.LayoutParams(0, dp(54), 1f));
        content.addView(volumeRow);

        LinearLayout boostRow = new LinearLayout(this);
        boostRow.setOrientation(LinearLayout.HORIZONTAL);
        boostRow.setGravity(Gravity.CENTER_VERTICAL);
        boostRow.setPadding(dp(8), 0, dp(14), 0);

        boostLabel = label("Boost: 0%");
        boostRow.addView(boostLabel, new LinearLayout.LayoutParams(dp(112), LinearLayout.LayoutParams.WRAP_CONTENT));
        boostSeek = new SeekBar(this);
        boostSeek.setMax(BoostConfig.HARD_LIMIT);
        boostRow.addView(boostSeek, new LinearLayout.LayoutParams(0, dp(54), 1f));
        content.addView(boostRow);

        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        presets.setGravity(Gravity.CENTER);
        presets.setPadding(dp(14), 0, dp(14), dp(8));
        addBoostPreset(presets, 10);
        addBoostPreset(presets, 20);
        addBoostPreset(presets, 30);
        addBoostPreset(presets, 40);
        content.addView(presets, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView warning = new TextView(this);
        warning.setText("Warning: Playing audio at high volumes may damage speakers or hearing. Increase Boost slowly and use it at your own risk.");
        warning.setTextColor(Color.rgb(105, 105, 105));
        warning.setTextSize(17f);
        warning.setPadding(dp(18), dp(16), dp(18), dp(16));
        content.addView(warning, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        Button diagnosticsButton = new Button(this);
        diagnosticsButton.setText("Boost diagnostics");
        diagnosticsButton.setAllCaps(false);
        diagnosticsButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showDiagnostics(); }
        });
        LinearLayout.LayoutParams diagnosticParams = new LinearLayout.LayoutParams(-1, -2);
        diagnosticParams.setMargins(dp(16), 0, dp(16), dp(12));
        content.addView(diagnosticsButton, diagnosticParams);

        // Footer is outside the weighted scroll area, above the system navigation inset.
        View redLine = new View(this);
        redLine.setBackgroundColor(Color.rgb(211, 47, 47));
        root.addView(redLine, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(10)));

        bannerContainer = new FrameLayout(this);
        bannerContainer.setBackgroundColor(Color.rgb(248, 205, 210));
        bannerContainer.setMinimumHeight(dp(54));

        bannerStatus = new TextView(this);
        bannerStatus.setText("Loading advertisement…");
        bannerStatus.setTextColor(Color.BLACK);
        bannerStatus.setTextSize(15f);
        bannerStatus.setGravity(Gravity.CENTER);
        bannerContainer.addView(bannerStatus, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        root.addView(bannerContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        root.requestApplyInsets();
        attachListeners();
        syncUi();
        requestConsentAndInitializeAds();
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(COLOR_MUTED);
        view.setTextSize(22f);
        return view;
    }

    private void attachListeners() {
        menuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showMainMenu();
            }
        });

        volumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                volumeLabel.setText("Vol.: " + progress + "%");
                if (fromUser) {
                    int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    int newVolume = Math.round((progress / 100f) * max);
                    newVolume = Math.max(0, Math.min(max, newVolume));
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { volumeDragging = true; }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { volumeDragging = false; }
        });

        boostSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int safe = clampBoost(progress);
                boostLabel.setText("Boost: " + safe + "%");
                if (fromUser) {
                    prefs.edit().putInt(BoosterService.KEY_BOOST, safe).apply();
                    applyUserBoost(safe);
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        serviceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, BoosterService.class);
                if (!BoosterService.isRunning() && !serviceRunning) {
                    requestNotificationPermissionIfNeeded();
                    int boost = clampBoost(boostSeek.getProgress());
                    intent.putExtra(BoosterService.EXTRA_BOOST_PERCENT, boost);
                    if (panelSession() > 0) intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, panelSession());
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent);
                    } else {
                        startService(intent);
                    }
                    serviceRunning = true;
                    engineStatusLabel.setText("Starting boost engine…");
                } else {
                    stopService(intent);
                    serviceRunning = false;
                    engineStatusLabel.setText("Boost engine stopped");
                    serviceStopsSinceInterstitial++;
                    maybeShowInterstitialAfterServiceStop();
                }
                updateServiceButton();
                statusHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        refreshEngineStatus();
                    }
                }, 300);
            }
        });
    }

    private void addBoostPreset(LinearLayout parent, final int value) {
        Button button = new Button(this);
        button.setText(value + "%");
        button.setAllCaps(false);
        button.setTextSize(15f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(46), 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        parent.addView(button, params);
        button.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                int safe = clampBoost(value);
                boostSeek.setProgress(safe);
                boostLabel.setText("Boost: " + safe + "%");
                prefs.edit().putInt(BoosterService.KEY_BOOST, safe).apply();
                applyUserBoost(safe);
            }
        });
    }

    /** Apply boost immediately. A user should not have to discover a separate Start step. */
    private void applyUserBoost(int boost) {
        int safe = clampBoost(boost);
        if (BoosterService.isRunning()) {
            sendBoostToService(safe);
            return;
        }
        // If the foreground service is still starting, queue the newest value as well.
        if (serviceRunning) {
            Intent update = new Intent(this, BoosterService.class)
                    .setAction(BoosterService.ACTION_SET_BOOST)
                    .putExtra(BoosterService.EXTRA_BOOST_PERCENT, safe);
            startService(update);
            return;
        }
        if (safe <= 0) return;

        requestNotificationPermissionIfNeeded();
        Intent intent = new Intent(this, BoosterService.class)
                .putExtra(BoosterService.EXTRA_BOOST_PERCENT, safe);
        if (panelSession() > 0) intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, panelSession());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
        else startService(intent);
        serviceRunning = true;
        updateServiceButton();
        engineStatusLabel.setText("Starting boost engine…");
        statusHandler.postDelayed(new Runnable() {
            @Override public void run() { refreshEngineStatus(); }
        }, 350);
    }

    private void sendBoostToService(int boost) {
        Intent intent = new Intent(this, BoosterService.class);
        intent.setAction(BoosterService.ACTION_SET_BOOST);
        intent.putExtra(BoosterService.EXTRA_BOOST_PERCENT, clampBoost(boost));
        startService(intent);
        statusHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                refreshEngineStatus();
            }
        }, 120);
    }

    private void syncUi() {
        serviceRunning = BoosterService.isRunning();
        updateServiceButton();

        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int percent = max > 0 ? Math.round((current * 100f) / max) : 0;
        volumeSeek.setProgress(percent);
        volumeLabel.setText("Vol.: " + percent + "%");

        int boost = clampBoost(prefs.getInt(BoosterService.KEY_BOOST, 0));
        boostSeek.setMax(BoostConfig.clamp(prefs.getInt(BoosterService.KEY_MAX, BoostConfig.HARD_LIMIT), BoostConfig.HARD_LIMIT));
        volumeRow.setVisibility(prefs.getBoolean(BoosterService.KEY_VOLUME, true) ? View.VISIBLE : View.GONE);
        boostSeek.setProgress(boost);
        boostLabel.setText("Boost: " + boost + "%");
        refreshEngineStatus();
    }

    private void refreshEngineStatus() {
        if (!volumeDragging) {
            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            volumeSeek.setProgress(max == 0 ? 0 : Math.round(current * 100f / max));
        }
        boolean actualRunning = BoosterService.isRunning();
        if (serviceRunning != actualRunning) {
            serviceRunning = actualRunning;
            updateServiceButton();
        }

        if (!actualRunning) {
            engineStatusLabel.setText("Boost engine stopped");
            engineStatusLabel.setTextColor(COLOR_MUTED);
            return;
        }

        engineStatusLabel.setText(BoosterService.getEngineStatus());
        if (BoosterService.getActiveEffectCount() > 0) {
            engineStatusLabel.setTextColor(Color.rgb(135, 90, 20));
        } else if (boostSeek.getProgress() > 0) {
            engineStatusLabel.setTextColor(COLOR_RED);
        } else {
            engineStatusLabel.setTextColor(COLOR_MUTED);
        }
    }

    private void updateServiceButton() {
        serviceButton.setText(serviceRunning ? "STOP SERVICE" : "START SERVICE");
        serviceButton.setBackground(roundBackground(serviceRunning ? COLOR_RED : COLOR_GREEN, 10));
    }

    private void requestConsentAndInitializeAds() {
        consentInformation = UserMessagingPlatform.getConsentInformation(this);
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();

        consentInformation.requestConsentInfoUpdate(
                this,
                params,
                new ConsentInformation.OnConsentInfoUpdateSuccessListener() {
                    @Override
                    public void onConsentInfoUpdateSuccess() {
                        UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                                MainActivity.this,
                                formError -> {
                                    if (formError != null) {
                                        Log.w(TAG, "Consent form: " + formError.getMessage());
                                    }
                                    if (consentInformation.canRequestAds()) initializeAdsOnce();
                                });

                        // Consent may already be valid from a previous launch.
                        if (consentInformation.canRequestAds()) initializeAdsOnce();
                    }
                },
                new ConsentInformation.OnConsentInfoUpdateFailureListener() {
                    @Override
                    public void onConsentInfoUpdateFailure(com.google.android.ump.FormError requestConsentError) {
                        Log.w(TAG, "Consent update: " + requestConsentError.getMessage());
                        if (consentInformation.canRequestAds()) {
                            initializeAdsOnce();
                        } else if (bannerStatus != null) {
                            bannerStatus.setText("Advertisement unavailable");
                        }
                    }
                });
    }

    private void initializeAdsOnce() {
        if (adsInitialized) return;
        adsInitialized = true;

        MobileAds.initialize(this, initializationStatus -> {
            loadBannerAd();
            loadInterstitialAd();
        });
    }

    private void loadBannerAd() {
        if (bannerContainer == null || isFinishing()) return;

        if (bannerAdView != null) {
            bannerAdView.destroy();
            bannerContainer.removeView(bannerAdView);
        }

        bannerAdView = new AdView(this);
        int widthPx = getResources().getDisplayMetrics().widthPixels;
        float density = getResources().getDisplayMetrics().density;
        int widthDp = Math.max(1, Math.round(widthPx / density));
        bannerAdView.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, widthDp));
        bannerAdView.setAdUnitId(getString(R.string.admob_banner_id));
        bannerAdView.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                if (bannerStatus != null) bannerStatus.setVisibility(View.GONE);
            }

            @Override
            public void onAdFailedToLoad(LoadAdError error) {
                Log.w(TAG, "Banner load failed: " + error.getCode() + " " + error.getMessage());
                if (bannerStatus != null) {
                    bannerStatus.setVisibility(View.VISIBLE);
                    bannerStatus.setText("Advertisement unavailable");
                }
            }
        });

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        bannerContainer.addView(bannerAdView, params);
        bannerAdView.loadAd(new AdRequest.Builder().build());
    }

    private void loadInterstitialAd() {
        if (!adsInitialized || isFinishing()) return;

        InterstitialAd.load(
                this,
                getString(R.string.admob_interstitial_id),
                new AdRequest.Builder().build(),
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(InterstitialAd ad) {
                        interstitialAd = ad;
                        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                            @Override
                            public void onAdShowedFullScreenContent() {
                                interstitialAd = null;
                            }

                            @Override
                            public void onAdDismissedFullScreenContent() {
                                loadInterstitialAd();
                            }

                            @Override
                            public void onAdFailedToShowFullScreenContent(AdError adError) {
                                Log.w(TAG, "Interstitial show failed: " + adError.getMessage());
                                interstitialAd = null;
                                loadInterstitialAd();
                            }
                        });
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError error) {
                        Log.w(TAG, "Interstitial load failed: " + error.getCode() + " " + error.getMessage());
                        interstitialAd = null;
                    }
                });
    }

    private void maybeShowInterstitialAfterServiceStop() {
        if (!adsInitialized || interstitialAd == null) return;
        if (serviceStopsSinceInterstitial < STOPS_PER_INTERSTITIAL) return;

        long now = System.currentTimeMillis();
        if (now - lastInterstitialShownAt < INTERSTITIAL_COOLDOWN_MS) return;

        serviceStopsSinceInterstitial = 0;
        lastInterstitialShownAt = now;
        interstitialAd.show(this);
    }

    private void showMainMenu() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(0, dp(14), 0, dp(12));

        GradientDrawable sheetBackground = new GradientDrawable();
        sheetBackground.setColor(Color.rgb(255, 249, 255));
        float radius = dp(22);
        sheetBackground.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        sheet.setBackground(sheetBackground);

        TextView title = new TextView(this);
        title.setText("Menu");
        title.setTextSize(30f);
        title.setTextColor(COLOR_DARK);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(30), 0, dp(24), 0);
        sheet.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(70)
        ));

        addMenuItem(sheet, android.R.drawable.ic_menu_help, "Help", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                showHelp();
            }
        });

        addMenuItem(sheet, android.R.drawable.ic_menu_preferences, "Settings", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                showSettings();
            }
        });

        if (consentInformation != null &&
                consentInformation.getPrivacyOptionsRequirementStatus() ==
                        ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
            addMenuItem(sheet, android.R.drawable.ic_menu_manage, "Privacy choices", new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    dialog.dismiss();
                    UserMessagingPlatform.showPrivacyOptionsForm(MainActivity.this, formError -> {
                        if (formError != null) {
                            Toast.makeText(MainActivity.this,
                                    "Privacy options could not be opened. Try again later.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        }

        addMenuItem(sheet, android.R.drawable.ic_menu_info_details, "Privacy policy", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                showPrivacyPolicy();
            }
        });

        addMenuItem(sheet, android.R.drawable.ic_menu_view, "More apps", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                showMessage("More apps", "More apps are coming soon.");
            }
        });

        addMenuItem(sheet, android.R.drawable.ic_menu_close_clear_cancel, "Close", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                stopService(new Intent(MainActivity.this, BoosterService.class));
                finishAndRemoveTask();
            }
        });

        dialog.setContentView(sheet);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.65f;
            window.setAttributes(params);
            window.setNavigationBarColor(Color.BLACK);
        }
    }

    private void addMenuItem(LinearLayout parent, int iconResource, String text, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(30), 0, dp(24), 0);
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(listener);

        TypedValue ripple = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)) {
            row.setBackgroundResource(ripple.resourceId);
        }

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResource);
        icon.setColorFilter(Color.rgb(150, 150, 150));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        iconParams.setMargins(0, 0, dp(26), 0);
        row.addView(icon, iconParams);

        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(20f);
        label.setTextColor(COLOR_DARK);
        label.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(label, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
        ));

        parent.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(78)
        ));
    }

    private void showHelp() {
        showMessage("Help",
                "1. Keep phone Volume at a comfortable, fixed level.\n\n" +
                "2. Move Boost above 0% and the service starts automatically. For best compatibility, start Boost before opening/restarting your music player.\n\n" +
                "3. Compare Boost 0% with 10%, 20%, 30% and only then 40%. Stop immediately if audio distorts.\n\n" +
                "4. No difference? Settings → Boost engine → try Equalizer or Loudness Enhancer, one at a time. Tap Reconnect audio effects after restarting your player.\n\n" +
                "5. Open Boost diagnostics and copy the report if neither engine changes the sound.\n\n" +
                "Some players expose no usable audio session and some phones bypass global effects. " +
                "An enabled effect is NOT proof of louder sound. Boost cannot be guaranteed for all apps, routes or devices.\n\n" +
                "The Boost percentage is a control setting, not a measured percentage increase in loudness. " +
                "Non-uniform boost only shapes Equalizer bands; it does not protect your hearing or speaker.\n\n" +
                "The bottom area can display a Google AdMob banner after privacy checks. " +
                "A full-screen ad may appear only at a limited natural pause after the boost service is stopped.");
    }

    private void showSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void showPrivacyPolicy() {
        try {
            InputStream stream = getAssets().open("privacy_policy.txt");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try {
                byte[] chunk = new byte[4096];
                int length;
                while ((length = stream.read(chunk)) != -1) buffer.write(chunk, 0, length);
            } finally {
                stream.close();
            }
            showScrollableText("Privacy policy", buffer.toString("UTF-8"), false);
        } catch (IOException error) {
            showMessage("Privacy policy", "The policy could not be loaded. Please reinstall the complete test build.");
        }
    }

    private void showDiagnostics() {
        showScrollableText("Boost diagnostics", BoosterService.getDiagnostics(), true);
    }

    private void showScrollableText(String title, final String text, boolean copy) {
        ScrollView scroll = new ScrollView(this);
        TextView body = new TextView(this);
        body.setText(text);
        body.setTextSize(16f);
        body.setTextColor(COLOR_DARK);
        body.setTextIsSelectable(true);
        body.setPadding(dp(20), dp(12), dp(20), dp(20));
        scroll.addView(body);
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(title)
                .setView(scroll).setPositiveButton("Close", null);
        if (copy) builder.setNeutralButton("Copy report", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("Boost diagnostics", text));
                Toast.makeText(MainActivity.this, "Copied. Share only if you choose.", Toast.LENGTH_SHORT).show();
            }
        });
        builder.show();
    }

    private void showMessage(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }

    private GradientDrawable roundBackground(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int clampBoost(int value) {
        return BoostConfig.clamp(value, prefs.getInt(BoosterService.KEY_MAX, BoostConfig.HARD_LIMIT));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
