package com.skystream.ssyoutube;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owns login and job work across activity recreation, without retaining browser or activity state. */
public final class MusicServer extends AndroidViewModel {
    private final MusicServerStore store;
    private final MusicServerClient client = new MusicServerClient();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<Status> status = new MutableLiveData<>();
    private final MutableLiveData<String> browserUrl = new MutableLiveData<>();
    private MusicServerProtocol.Session session;
    private MusicServerProtocol.Pending pending;
    private boolean busy = true;
    private boolean restoring = true;
    private volatile boolean cleared;
    private String queuedCallback;
    private String lastOrigin = "";

    public MusicServer(Application application) {
        super(application);
        store = new MusicServerStore(application);
        publish(0);
        worker.execute(() -> {
            MusicServerProtocol.Session savedSession = null;
            MusicServerProtocol.Pending savedPending = null;
            int message = 0;
            try {
                savedSession = store.session();
                savedPending = store.pending();
                long now = System.currentTimeMillis();
                if (savedSession != null && savedSession.expiresAt <= now) {
                    savedSession = null;
                    store.clearSession();
                    message = R.string.music_login_expired;
                }
                if (savedPending != null && (now < savedPending.createdAt
                        || now - savedPending.createdAt >= MusicServerProtocol.LOGIN_MAX_AGE_MS)) {
                    savedPending = null;
                    store.clearPending();
                }
            } catch (IOException | RuntimeException e) {
                store.clearSession();
                store.clearPending();
                savedSession = null;
                savedPending = null;
                message = R.string.music_storage_failed;
            }
            MusicServerProtocol.Session restoredSession = savedSession;
            MusicServerProtocol.Pending restoredPending = savedPending;
            int result = message;
            complete(() -> {
                session = restoredSession;
                pending = restoredPending;
                lastOrigin = session != null ? session.origin : pending != null ? pending.origin : "";
                busy = false;
                restoring = false;
                publish(result);
                if (queuedCallback != null) {
                    String callback = queuedCallback;
                    queuedCallback = null;
                    acceptCallback(callback);
                }
            });
        });
    }

    LiveData<Status> status() {
        return status;
    }

    LiveData<String> browserUrl() {
        return browserUrl;
    }

    String takeBrowserUrl() {
        String url = browserUrl.getValue();
        browserUrl.setValue(null);
        return url;
    }

    String origin() {
        return lastOrigin;
    }

    void login(String origin) {
        if (busy || session != null) {
            return;
        }
        busy = true;
        publish(R.string.music_connecting);
        worker.execute(() -> {
            try {
                MusicServerProtocol.Pending login =
                        MusicServerProtocol.newLogin(origin, System.currentTimeMillis());
                store.save(login);
                complete(() -> {
                    pending = login;
                    lastOrigin = login.origin;
                    busy = false;
                    publish(R.string.music_waiting);
                    browserUrl.setValue(MusicServerProtocol.authorizationUrl(login));
                });
            } catch (IOException | RuntimeException e) {
                complete(() -> {
                    busy = false;
                    publish(R.string.music_login_failed);
                });
            }
        });
    }

    void acceptCallback(String callback) {
        if (busy) {
            // A cold-start callback may arrive while encrypted credentials are being restored.
            if (restoring) {
                queuedCallback = callback;
            }
            return;
        }
        final String code;
        final MusicServerProtocol.Pending login = pending;
        try {
            code = MusicServerProtocol.callbackCode(callback, login, System.currentTimeMillis());
        } catch (IOException | RuntimeException e) {
            publish(R.string.music_callback_invalid);
            return;
        }
        pending = null;
        browserUrl.setValue(null);
        busy = true;
        publish(R.string.music_connecting);
        worker.execute(() -> {
            store.clearPending();
            try {
                MusicServerProtocol.Session authenticated = client.exchange(login, code);
                store.save(authenticated);
                complete(() -> {
                    session = authenticated;
                    busy = false;
                    publish(R.string.music_connected);
                });
            } catch (IOException | RuntimeException e) {
                complete(() -> {
                    busy = false;
                    publish(R.string.music_login_failed);
                });
            }
        });
    }

    void forget(int message) {
        if (busy) {
            return;
        }
        session = null;
        pending = null;
        browserUrl.setValue(null);
        busy = true;
        publish(0);
        worker.execute(() -> {
            store.clearSession();
            store.clearPending();
            complete(() -> {
                busy = false;
                publish(message);
            });
        });
    }

    void send(String url) {
        if (busy || session == null || url == null) {
            return;
        }
        if (session.expiresAt <= System.currentTimeMillis()) {
            forget(R.string.music_login_expired);
            return;
        }
        MusicServerProtocol.Session authenticated = session;
        busy = true;
        publish(R.string.music_sending);
        worker.execute(() -> {
            int message;
            boolean unauthorized = false;
            try {
                client.createJob(authenticated, url);
                message = R.string.music_job_started;
            } catch (MusicServerClient.HttpFailure e) {
                unauthorized = e.status == 401;
                message = unauthorized ? R.string.music_login_expired
                        : e.status == 403 ? R.string.music_forbidden
                        : e.status == 409 ? R.string.music_duplicate
                        : e.status == 429 ? R.string.music_rate_limited : R.string.music_job_failed;
            } catch (IOException | RuntimeException e) {
                message = R.string.music_job_failed;
            }
            if (unauthorized) {
                store.clearSession();
            }
            boolean clearSession = unauthorized;
            int result = message;
            complete(() -> {
                if (clearSession) {
                    session = null;
                }
                busy = false;
                publish(result);
            });
        });
    }

    void refresh() {
        if (busy) {
            return;
        }
        long now = System.currentTimeMillis();
        if ((session != null && session.expiresAt <= now)
                || (pending != null && (now < pending.createdAt
                || now - pending.createdAt >= MusicServerProtocol.LOGIN_MAX_AGE_MS))) {
            forget(R.string.music_login_expired);
        } else {
            Status current = status.getValue();
            publish(current != null ? current.message : 0);
        }
    }

    private void publish(int message) {
        status.setValue(new Status(session != null, pending != null, busy, message));
    }

    private void complete(Runnable action) {
        if (!cleared) {
            main.post(() -> {
                if (!cleared) {
                    action.run();
                }
            });
        }
    }

    @Override
    protected void onCleared() {
        cleared = true;
        main.removeCallbacksAndMessages(null);
        worker.shutdownNow();
    }

    static final class Status {
        final boolean loggedIn;
        final boolean awaitingLogin;
        final boolean busy;
        final int message;

        Status(boolean loggedIn, boolean awaitingLogin, boolean busy, int message) {
            this.loggedIn = loggedIn;
            this.awaitingLogin = awaitingLogin;
            this.busy = busy;
            this.message = message;
        }
    }
}
