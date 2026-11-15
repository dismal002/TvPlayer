package com.dismal.tvplayer.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;

import androidx.leanback.preference.LeanbackPreferenceFragment;
import androidx.leanback.preference.LeanbackSettingsFragment;
import androidx.preference.DialogPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceScreen;

import com.dismal.tvplayer.R;
import com.dismal.tvplayer.data.AppConfig;

/**
 * Preference-based settings, matching the Leanback sample's
 * LeanbackSettingsFragment + LeanbackPreferenceFragment structure.
 */
public class SettingsFragment extends LeanbackSettingsFragment
        implements DialogPreference.TargetFragment {

    private static final String PREFERENCE_RESOURCE_ID = "preferenceResource";
    private static final String PREFERENCE_ROOT = "root";

    private PreferenceFragment preferenceFragment;

    @Override
    public void onPreferenceStartInitialScreen() {
        preferenceFragment = buildPreferenceFragment(R.xml.settings, null);
        startPreferenceFragment(preferenceFragment);
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragment fragment,
                                             Preference preference) {
        return false;
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragment fragment,
                                           PreferenceScreen preferenceScreen) {
        PreferenceFragment nested = buildPreferenceFragment(R.xml.settings,
                preferenceScreen.getKey());
        startPreferenceFragment(nested);
        return true;
    }

    @Override
    public Preference findPreference(CharSequence key) {
        return preferenceFragment == null ? null : preferenceFragment.findPreference(key);
    }

    private PreferenceFragment buildPreferenceFragment(int preferenceResId, String root) {
        PreferenceFragment fragment = new PrefFragment();
        Bundle args = new Bundle();
        args.putInt(PREFERENCE_RESOURCE_ID, preferenceResId);
        args.putString(PREFERENCE_ROOT, root);
        fragment.setArguments(args);
        return fragment;
    }

    public static class PrefFragment extends LeanbackPreferenceFragment {

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            setTitle(getString(R.string.settings));
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            Bundle args = getArguments();
            int prefResId = args.getInt(PREFERENCE_RESOURCE_ID, R.xml.settings);
            String root = args.getString(PREFERENCE_ROOT, null);
            if (root == null) {
                addPreferencesFromResource(prefResId);
            } else {
                setPreferencesFromResource(prefResId, root);
            }
            bindPreferences();
        }

        private void bindPreferences() {
            if (getActivity() == null) {
                return;
            }
            final AppConfig config = AppConfig.get(getActivity());

            ListPreference sort = findPreference("settings_sort");
            if (sort != null) {
                sort.setValue(String.valueOf(config.getSortMode()));
                sort.setOnPreferenceChangeListener((preference, newValue) -> {
                    try {
                        config.setSortMode(Integer.parseInt(String.valueOf(newValue)));
                    } catch (NumberFormatException ignored) {
                        return false;
                    }
                    return true;
                });
            }

            ListPreference order = findPreference("settings_order");
            if (order != null) {
                order.setValue(config.isAscending() ? "0" : "1");
                order.setOnPreferenceChangeListener((preference, newValue) -> {
                    config.setAscending("0".equals(String.valueOf(newValue)));
                    return true;
                });
            }

            Preference rescan = findPreference("settings_rescan");
            if (rescan != null) {
                rescan.setOnPreferenceClickListener(preference -> {
                    SettingsActivity.finishWithRescan(getActivity());
                    return true;
                });
            }

            Preference about = findPreference("settings_about");
            if (about != null) {
                about.setOnPreferenceClickListener(preference -> {
                    new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.about)
                            .setMessage(R.string.about_message)
                            .setPositiveButton(R.string.dialog_ok, null)
                            .show();
                    return true;
                });
            }

            Preference exit = findPreference("settings_exit");
            if (exit != null) {
                exit.setOnPreferenceClickListener(preference -> {
                    getActivity().finishAffinity();
                    return true;
                });
            }
        }
    }
}
