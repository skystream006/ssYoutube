package com.skystream.ssyoutube;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.webkit.ScriptHandler;
import androidx.webkit.ServiceWorkerClientCompat;
import androidx.webkit.ServiceWorkerControllerCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owns local filtering only; allowed requests keep WebView's normal cookie/TLS handling. */
final class WebAdBlocker {
    private static WebAdBlocker serviceWorkerOwner;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private WeakReference<WebView> view = new WeakReference<>(null);
    private volatile AdBlockEngine engine;
    private volatile boolean enabled;
    private volatile String documentUrl;
    private boolean loading;
    private boolean destroyed;
    private ScriptHandler scriptHandler;
    private String script;

    WebAdBlocker(Context context) {
        this.context = context.getApplicationContext();
    }

    void setEnabled(WebView target, boolean value) {
        if (destroyed) {
            return;
        }
        enabled = value;
        view = new WeakReference<>(target);
        documentUrl = target.getUrl();
        removeScript();
        updateServiceWorker();
        if (!value) {
            return;
        }
        if (engine != null) {
            installScript(target);
        } else if (!loading) {
            loading = true;
            loader.execute(() -> {
                AdBlockEngine loaded = null;
                try (InputStreamReader input = new InputStreamReader(
                        context.getResources().openRawResource(R.raw.adblocking_rules),
                        StandardCharsets.UTF_8)) {
                    loaded = AdBlockEngine.read(input);
                } catch (IOException | RuntimeException ignored) {
                    // Filtering is optional; a bad bundled resource must not break browsing.
                }
                AdBlockEngine result = loaded;
                main.post(() -> {
                    if (destroyed) {
                        return;
                    }
                    loading = false;
                    engine = result;
                    if (result != null) {
                        script = AdBlockingScript.create(result);
                    }
                    WebView current = view.get();
                    if (enabled && current != null) {
                        if (result == null) {
                            Toast.makeText(context, R.string.adblocking_unavailable,
                                    Toast.LENGTH_LONG).show();
                        } else {
                            installScript(current);
                            // The first load may have started before the rules were ready.
                            current.reload();
                        }
                    }
                });
            });
        }
    }

    void onPage(WebView target, String url) {
        documentUrl = url;
        if (enabled && script != null && AdBlockEngine.isSupportedPage(url)) {
            target.evaluateJavascript(script, null);
        }
    }

    WebResourceResponse intercept(WebResourceRequest request) {
        AdBlockEngine current = engine;
        if (!enabled || current == null || request.isForMainFrame()) {
            return null;
        }
        String page = documentUrl;
        if (!AdBlockEngine.isSupportedPage(page)) {
            return null;
        }
        for (Map.Entry<String, String> header : request.getRequestHeaders().entrySet()) {
            if ("Referer".equalsIgnoreCase(header.getKey())) {
                page = header.getValue();
                break;
            }
        }
        if (!current.shouldBlock(page, request.getUrl().toString(), false) || !enabled) {
            return null;
        }
        return new WebResourceResponse("text/plain", "UTF-8", 200, "OK",
                Collections.singletonMap("Cache-Control", "no-store"),
                new ByteArrayInputStream(new byte[0]));
    }

    void destroy() {
        destroyed = true;
        enabled = false;
        documentUrl = null;
        updateServiceWorker();
        removeScript();
        view.clear();
        loader.shutdownNow();
        main.removeCallbacksAndMessages(null);
        engine = null;
    }

    private void installScript(WebView target) {
        removeScript();
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                scriptHandler = WebViewCompat.addDocumentStartJavaScript(
                        target, script, NativePlaybackScript.ORIGIN_RULES);
            } catch (IllegalArgumentException | UnsupportedOperationException ignored) {
                // Page callbacks still apply cosmetic rules on older WebView providers.
            }
        }
    }

    private void removeScript() {
        if (scriptHandler != null) {
            scriptHandler.remove();
            scriptHandler = null;
        }
    }

    private void updateServiceWorker() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)
                || !WebViewFeature.isFeatureSupported(
                        WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST)) {
            return;
        }
        ServiceWorkerControllerCompat controller = ServiceWorkerControllerCompat.getInstance();
        if (enabled) {
            serviceWorkerOwner = this;
            controller.setServiceWorkerClient(new ServiceWorkerClientCompat() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                    return intercept(request);
                }
            });
        } else if (serviceWorkerOwner == this) {
            controller.setServiceWorkerClient(null);
            serviceWorkerOwner = null;
        }
    }
}
