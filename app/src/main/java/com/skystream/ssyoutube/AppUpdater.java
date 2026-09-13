package com.skystream.ssyoutube;

import android.app.Activity;
import android.app.Application;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Keeps update work across activity recreation without retaining an activity. */
public final class AppUpdater extends AndroidViewModel {
    static final int INSTALL_PERMISSION_REQUEST = 410;
    private static final String PENDING_VERSION = "update_pending_version";
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<Event> events = new MutableLiveData<>();
    private final GitHubUpdateClient client = new GitHubUpdateClient();
    private final File directory;
    private Future<?> work;
    private volatile boolean cleared;
    private boolean startupChecked;
    private boolean busy;
    private boolean waitingForPermission;
    private String pendingVersion;

    public AppUpdater(Application application) {
        super(application);
        directory = new File(application.getCacheDir(), "updates");
    }

    LiveData<Event> events() {
        return events;
    }

    void checkOnStartup(Bundle savedState) {
        if (startupChecked) {
            return;
        }
        startupChecked = true;
        if (savedState != null && savedState.getString(PENDING_VERSION) != null) {
            pendingVersion = savedState.getString(PENDING_VERSION);
            waitingForPermission = true;
            return;
        }
        check(false);
    }

    void saveState(Bundle outState) {
        if (waitingForPermission) {
            outState.putString(PENDING_VERSION, pendingVersion);
        }
    }

    void check(boolean manual) {
        if (busy || waitingForPermission) {
            if (manual) {
                toast(R.string.updates_busy, null);
            }
            return;
        }
        busy = true;
        if (manual) {
            events.setValue(new Event(R.string.updates_checking, null, false));
        }
        work = WORKER.submit(() -> {
            try {
                GitHubUpdateClient.Release release = client.fetchLatest();
                int comparison = GitHubUpdateClient.compareVersions(
                        release.version, BuildConfig.VERSION_NAME);
                if (comparison <= 0) {
                    complete(manual ? new Event(comparison == 0 ? R.string.updates_current
                            : R.string.updates_ahead, release.version, false) : null);
                } else if (!manual) {
                    complete(new Event(R.string.updates_available, release.version, false));
                } else {
                    publish(new Event(R.string.updates_downloading, release.version, false));
                    File apk = client.download(release, directory);
                    if (!isCompatible(apk, release.version)) {
                        apk.delete();
                        complete(new Event(R.string.updates_invalid, null, false));
                        return;
                    }
                    complete(new Event(0, release.version, true));
                }
            } catch (IOException | RuntimeException e) {
                complete(manual ? new Event(R.string.updates_failed, null, false) : null);
            }
        });
    }

    // Called only while the activity is resumed, so downloads never launch background UI.
    void dispatch(Activity activity) {
        Event event = events.getValue();
        if (event == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        events.setValue(null);
        if (event.install) {
            pendingVersion = event.version;
            install(activity);
        } else {
            toast(event.message, event.version);
        }
    }

    private void install(Activity activity) {
        if (!new File(directory, "update.apk").isFile()) {
            toast(R.string.updates_invalid, null);
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && !activity.getPackageManager().canRequestPackageInstalls()) {
                waitingForPermission = true;
                toast(R.string.updates_allow_install, null);
                activity.startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName())), INSTALL_PERMISSION_REQUEST);
                return;
            }
            Uri uri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", new File(directory, "update.apk"));
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive");
            intent.setClipData(ClipData.newRawUri("App update", uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException | IllegalArgumentException e) {
            waitingForPermission = false;
            toast(R.string.updates_installer_unavailable, null);
        }
    }

    void onInstallPermissionResult() {
        if (!waitingForPermission) {
            return;
        }
        waitingForPermission = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getApplication().getPackageManager().canRequestPackageInstalls()) {
            events.setValue(new Event(R.string.updates_install_denied, null, false));
            return;
        }
        // Revalidate the private file, including after process death while in system settings.
        busy = true;
        String version = pendingVersion;
        work = WORKER.submit(() -> {
            try {
                complete(isCompatible(new File(directory, "update.apk"), version)
                        ? new Event(0, version, true)
                        : new Event(R.string.updates_invalid, null, false));
            } catch (RuntimeException e) {
                complete(new Event(R.string.updates_invalid, null, false));
            }
        });
    }

    @SuppressWarnings("deprecation")
    private boolean isCompatible(File apk, String version) {
        PackageManager pm = getApplication().getPackageManager();
        PackageInfo candidate = pm.getPackageArchiveInfo(apk.getAbsolutePath(),
                PackageManager.GET_SIGNATURES);
        try {
            PackageInfo installed = pm.getPackageInfo(getApplication().getPackageName(),
                    PackageManager.GET_SIGNATURES);
            return candidate != null
                    && installed.packageName.equals(candidate.packageName)
                    && candidate.versionName != null
                    && GitHubUpdateClient.compareVersions(candidate.versionName, version) == 0
                    && versionCode(candidate) > versionCode(installed)
                    && sameSigners(installed.signatures, candidate.signatures);
        } catch (PackageManager.NameNotFoundException | IllegalArgumentException e) {
            return false;
        }
    }

    private static long versionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? info.getLongVersionCode() : info.versionCode;
    }

    private static boolean sameSigners(Signature[] installed, Signature[] candidate) {
        return installed != null && candidate != null && installed.length > 0
                && new HashSet<>(Arrays.asList(installed))
                .equals(new HashSet<>(Arrays.asList(candidate)));
    }

    private void publish(Event event) {
        main.post(() -> {
            if (!cleared) {
                events.setValue(event);
            }
        });
    }

    private void complete(Event event) {
        main.post(() -> {
            if (!cleared) {
                busy = false;
                events.setValue(event);
            }
        });
    }

    private void toast(int message, String version) {
        String text = version == null ? getApplication().getString(message)
                : getApplication().getString(message, version);
        Toast.makeText(getApplication(), text, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onCleared() {
        cleared = true;
        if (work != null) {
            work.cancel(true);
        }
        main.removeCallbacksAndMessages(null);
    }

    static final class Event {
        final int message;
        final String version;
        final boolean install;

        Event(int message, String version, boolean install) {
            this.message = message;
            this.version = version;
            this.install = install;
        }
    }
}
