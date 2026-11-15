package com.dismal.tvplayer.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewTreeObserver;

import com.dismal.tvplayer.R;

/**
 * Branded launch screen shown while the app process and library start up.
 * Hands off to MainActivity, which owns the permission/onboarding flow.
 */
public class SplashActivity extends Activity {

    private static final long SPLASH_DURATION_MS = 450;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean launched;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        holdSplashUntilDrawn();
    }

    /** Start the handoff timer after the splash has actually been drawn. */
    private void holdSplashUntilDrawn() {
        final View decor = getWindow().getDecorView();
        decor.getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        if (decor.getViewTreeObserver().isAlive()) {
                            decor.getViewTreeObserver().removeOnPreDrawListener(this);
                        }
                        handler.postDelayed(SplashActivity.this::launchLibrary,
                                SPLASH_DURATION_MS);
                        return true;
                    }
                });
    }

    private void launchLibrary() {
        if (launched || isFinishing()) {
            return;
        }
        launched = true;
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
