package com.dismal.tvplayer.ui;

import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

import com.dismal.tvplayer.R;

/** Hosts the first-run onboarding flow. */
public class OnboardingActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);
    }
}
