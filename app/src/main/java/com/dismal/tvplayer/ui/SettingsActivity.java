package com.dismal.tvplayer.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

import com.dismal.tvplayer.R;

/**
 * Leanback settings screen, following the SettingsFragment / SettingsActivity
 * pattern from the Leanback sample.
 */
public class SettingsActivity extends FragmentActivity {

    public static final String EXTRA_RESCAN = "rescan";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
    }

    static void finishWithRescan(Activity activity) {
        Intent result = new Intent();
        result.putExtra(EXTRA_RESCAN, true);
        activity.setResult(Activity.RESULT_OK, result);
        activity.finish();
    }
}
