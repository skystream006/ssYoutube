package com.skystream.ssyoutube;

import java.io.IOException;
import java.io.Reader;
import java.util.Set;

/** Independent, reversible page customization; never enables network adblocking. */
final class PageElementBlockingScript {
    enum Option {
        SHORTS("shorts", true),
        PLAYABLES("playables", true),
        RECOMMENDED("recommended", false),
        DISTRACTING("distracting", true),
        THUMBNAILS("thumbnails", false),
        AUTODUBBED("autodubbed", true),
        MEMBERS_ONLY("members_only", true);

        final String key;
        final String preferenceKey;
        final boolean defaultEnabled;

        Option(String key, boolean defaultEnabled) {
            this.key = key;
            this.preferenceKey = "block_youtube_" + key;
            this.defaultEnabled = defaultEnabled;
        }
    }

    private PageElementBlockingScript() { }

    static String read(Reader reader) throws IOException {
        StringBuilder source = new StringBuilder();
        char[] buffer = new char[4096];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            source.append(buffer, 0, count);
        }
        return source.toString();
    }

    static String create(String source, Set<Option> enabled) {
        StringBuilder options = new StringBuilder();
        for (Option option : Option.values()) {
            if (options.length() != 0) {
                options.append(',');
            }
            options.append('"').append(option.key).append("\":")
                    .append(enabled.contains(option));
        }
        return "(" + source + ")({" + options + "});";
    }
}
