function (enabled) {
    'use strict';
    if (window.top !== window || location.protocol !== 'https:'
            || location.username || location.password
            || (location.port && location.port !== '443')
            || !/^(www\.|m\.|music\.)?youtube\.com$/.test(location.hostname)) {
        return;
    }
    if (window.__ssyoutubeBackgroundAudio) {
        window.__ssyoutubeBackgroundAudio.update(enabled);
        return;
    }

    var foreground = true;
    var protectedPlayback = false;
    var overrides = [];
    var lastMessage = '';
    var lastActivityRefresh = 0;
    var visibilityOverridden = false;
    var available = true;

    function isActive() {
        return Array.prototype.some.call(document.querySelectorAll('video,audio'), function (media) {
            return !media.paused && !media.ended && !media.muted && media.volume > 0;
        });
    }

    function notify(state) {
        var message = state + '\n' + location.href;
        if (message !== lastMessage && window.ssYouTubeBackgroundAudio
                && window.ssYouTubeBackgroundAudio.postMessage) {
            window.ssYouTubeBackgroundAudio.postMessage(message);
            lastMessage = message;
        }
    }

    function state() {
        return available ? (isActive() ? 'playing' : 'paused') : 'unavailable';
    }

    function report() {
        if (!foreground && (!enabled || !protectedPlayback)) {
            pauseMedia();
        }
        notify(state());
    }

    function blockVisibility(event) {
        event.stopImmediatePropagation();
    }

    function restoreVisibility() {
        window.removeEventListener('visibilitychange', blockVisibility, true);
        document.removeEventListener('visibilitychange', blockVisibility, true);
        overrides.forEach(function (override) {
            var current = Object.getOwnPropertyDescriptor(document, override.key);
            if (current && current.get === override.get) {
                if (override.original) {
                    Object.defineProperty(document, override.key, override.original);
                } else {
                    delete document[override.key];
                }
            }
        });
        overrides = [];
        visibilityOverridden = false;
    }

    function updateVisibility() {
        var shouldOverride = enabled && (foreground || protectedPlayback);
        if (visibilityOverridden === shouldOverride) {
            return;
        }
        if (!shouldOverride) {
            restoreVisibility();
            return;
        }
        try {
            ['hidden', 'visibilityState'].forEach(function (key) {
                var original = Object.getOwnPropertyDescriptor(document, key);
                var get = function () { return key === 'hidden' ? false : 'visible'; };
                Object.defineProperty(document, key, {get: get, configurable: true});
                overrides.push({key: key, get: get, original: original});
            });
            window.addEventListener('visibilitychange', blockVisibility, true);
            document.addEventListener('visibilitychange', blockVisibility, true);
            visibilityOverridden = true;
            available = true;
        } catch (error) {
            restoreVisibility();
            available = false;
            console.warn('ssYouTube background visibility overrides unavailable');
            notify('unavailable');
        }
    }

    function pauseMedia() {
        Array.prototype.forEach.call(document.querySelectorAll('video,audio'), function (media) {
            if (!media.paused) {
                media.pause();
            }
        });
    }

    function pause() {
        pauseMedia();
        report();
    }

    function heartbeat() {
        report();
        var active = isActive();
        if (enabled && active && Date.now() - lastActivityRefresh >= 5 * 60 * 1000) {
            var activity = Object.getOwnPropertyDescriptor(window, '_lact');
            if (activity && activity.writable && typeof activity.value === 'number') {
                window._lact = Date.now();
            }
            lastActivityRefresh = Date.now();
        }
    }

    window.__ssyoutubeBackgroundAudio = {
        update: function (value) {
            enabled = value;
            available = true;
            lastMessage = '';
            updateVisibility();
            if (!foreground && (!enabled || !protectedPlayback)) {
                pause();
            }
            report();
        },
        setForeground: function (value, protectedAudio) {
            foreground = value;
            protectedPlayback = protectedAudio;
            updateVisibility();
            if (!foreground && (!enabled || !protectedPlayback)) {
                pause();
            }
            report();
        },
        isActive: isActive,
        state: state,
        pause: pause
    };
    ['play', 'playing', 'pause', 'ended', 'emptied', 'volumechange'].forEach(function (name) {
        document.addEventListener(name, report, true);
    });
    document.addEventListener('yt-navigate-finish', report);
    window.addEventListener('popstate', report);
    setInterval(heartbeat, 1000);
    updateVisibility();
    report();
}
