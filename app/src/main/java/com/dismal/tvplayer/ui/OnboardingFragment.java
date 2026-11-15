package com.dismal.tvplayer.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.app.OnboardingSupportFragment;

import com.dismal.tvplayer.R;

/**
 * First-run onboarding in the Leanback style. The final page asks for the
 * storage permission the library needs, then opens the library.
 */
public class OnboardingFragment extends OnboardingSupportFragment {

    private static final int REQUEST_STORAGE = 201;

    private boolean leaving;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setLogoResourceId(R.drawable.banner);
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    protected int getPageCount() {
        return 2;
    }

    @Override
    protected CharSequence getPageTitle(int pageIndex) {
        return pageIndex == 0
                ? getString(R.string.onboarding_title_welcome)
                : getString(R.string.onboarding_title_storage);
    }

    @Override
    protected CharSequence getPageDescription(int pageIndex) {
        return pageIndex == 0
                ? getString(R.string.onboarding_description_welcome)
                : getString(R.string.onboarding_description_storage);
    }

    @Nullable
    @Override
    protected View onCreateBackgroundView(LayoutInflater inflater, ViewGroup container) {
        View background = new View(getActivity());
        background.setBackgroundColor(getResources().getColor(R.color.vp_teal));
        return background;
    }

    @Nullable
    @Override
    protected View onCreateContentView(LayoutInflater inflater, ViewGroup container) {
        ImageView content = new ImageView(getActivity());
        int size = Math.round(200 * getResources().getDisplayMetrics().density);
        content.setLayoutParams(new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        content.setScaleType(ImageView.ScaleType.FIT_CENTER);
        content.setImageResource(R.drawable.ic_movie);
        return content;
    }

    @Nullable
    @Override
    protected View onCreateForegroundView(LayoutInflater inflater, ViewGroup container) {
        return null;
    }

    @Override
    protected void onFinishFragment() {
        super.onFinishFragment();
        if (hasStoragePermission()) {
            launchMain();
        } else {
            requestPermissions(new String[]{storagePermission()}, REQUEST_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_STORAGE) {
            return;
        }
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchMain();
        } else {
            showPermissionDeniedDialog();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!leaving && hasStoragePermission()) {
            launchMain();
        }
    }

    private boolean hasStoragePermission() {
        return getActivity() != null
                && getActivity().checkSelfPermission(storagePermission())
                == PackageManager.PERMISSION_GRANTED;
    }

    private String storagePermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_VIDEO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private void launchMain() {
        if (leaving || getActivity() == null) {
            return;
        }
        leaving = true;
        startActivity(new Intent(getActivity(), MainActivity.class));
        getActivity().finish();
    }

    private void showPermissionDeniedDialog() {
        if (getActivity() == null) {
            return;
        }
        String permission = storagePermission();
        boolean canAskAgain = shouldShowRequestPermissionRationale(permission);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.onboarding_permission_title)
                .setMessage(R.string.onboarding_permission_message);
        if (canAskAgain) {
            builder.setPositiveButton(R.string.onboarding_grant_again,
                    (dialog, which) ->
                            requestPermissions(new String[]{permission}, REQUEST_STORAGE));
        } else {
            builder.setPositiveButton(R.string.onboarding_open_settings,
                    (dialog, which) -> openAppSettings());
        }
        builder.setNegativeButton(R.string.dialog_cancel, null);
        builder.show();
    }

    private void openAppSettings() {
        if (getActivity() == null) {
            return;
        }
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getActivity().getPackageName(), null));
        startActivity(intent);
    }
}
