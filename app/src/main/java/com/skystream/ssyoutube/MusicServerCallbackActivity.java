package com.skystream.ssyoutube;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Returns browser authorization to the existing app task; validation belongs to MusicServer. */
public final class MusicServerCallbackActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent callback = getIntent();
        if (callback != null && Intent.ACTION_VIEW.equals(callback.getAction())
                && callback.getData() != null
                && "com.ssytdlp.app".equals(callback.getData().getScheme())) {
            startActivity(new Intent(this, MainActivity.class)
                    .setAction(Intent.ACTION_VIEW)
                    .setData(callback.getData())
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        }
        finish();
    }
}
