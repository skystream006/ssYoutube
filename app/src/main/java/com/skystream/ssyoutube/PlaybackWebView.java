package com.skystream.ssyoutube;

import android.content.Context;
import android.util.AttributeSet;
import android.webkit.WebView;

/** Keeps an actively protected audio page alive when its activity's window is hidden. */
public final class PlaybackWebView extends WebView {
    private boolean backgroundPlaybackAllowed;
    private int windowVisibility = VISIBLE;

    public PlaybackWebView(Context context, AttributeSet attributes) {
        super(context, attributes);
    }

    void setBackgroundPlaybackAllowed(boolean allowed) {
        if (backgroundPlaybackAllowed != allowed) {
            backgroundPlaybackAllowed = allowed;
            super.onWindowVisibilityChanged(allowed ? VISIBLE : windowVisibility);
        }
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        windowVisibility = visibility;
        super.onWindowVisibilityChanged(backgroundPlaybackAllowed ? VISIBLE : visibility);
    }
}
