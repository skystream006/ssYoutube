(function () {
    'use strict';
    if (location.protocol !== 'https:' || (location.port && location.port !== '443')
            || !/^(www\.|m\.|music\.)?youtube\.com$/.test(location.hostname)) {
        return;
    }
    if (window.__ssyoutubeVideoAdBlocking) {
        window.__ssyoutubeVideoAdBlocking.update();
        return;
    }

    var active = true;
    var restore = [];
    var watched = new WeakMap();
    var parse = JSON.parse;
    var stringify = JSON.stringify;
    var adKeys = ['playerAds', 'adPlacements', 'adSlots', 'adBreakHeartbeatParams'];

    function ownValue(object, key) {
        var descriptor = Object.getOwnPropertyDescriptor(object, key);
        return descriptor && descriptor.value;
    }

    function object(value) {
        return value !== null && typeof value === 'object';
    }

    function player(value) {
        if (!active || !object(value)) {
            return value;
        }
        adKeys.forEach(function (key) {
            var descriptor = Object.getOwnPropertyDescriptor(value, key);
            if (descriptor && descriptor.configurable) {
                delete value[key];
            }
        });
        return value;
    }

    // Visit only player-response envelopes, never arbitrary account/page object trees.
    function prune(value) {
        if (!active || !object(value)) {
            return value;
        }
        var pending = [value];
        var seen = new WeakSet();
        for (var count = 0; pending.length && count < 256; count++) {
            var current = pending.shift();
            if (!object(current) || seen.has(current)) {
                continue;
            }
            seen.add(current);
            var entries = ownValue(current, 'entries');
            if (Array.isArray(entries) && !Object.isSealed(entries)) {
                for (var entry = Math.min(entries.length, 256) - 1; entry >= 0; entry--) {
                    var command = object(entries[entry]) && ownValue(entries[entry], 'command');
                    var reel = object(command) && ownValue(command, 'reelWatchEndpoint');
                    var params = object(reel) && ownValue(reel, 'adClientParams');
                    if (object(params) && ownValue(params, 'isAd') === true) {
                        entries.splice(entry, 1);
                    }
                }
            }
            if (object(ownValue(current, 'playabilityStatus'))
                    || object(ownValue(current, 'videoDetails'))) {
                player(current);
            }
            ['playerResponse', 'player_response'].forEach(function (key) {
                var response = ownValue(current, key);
                if (object(response)) {
                    player(response);
                }
            });
            ['response', 'playerResponse', 'player', 'entries', 'command',
                'reelWatchEndpoint'].forEach(function (key) {
                var child = ownValue(current, key);
                if (object(child) && pending.length < 256) {
                    pending.push(child);
                }
            });
            if (Array.isArray(current)) {
                for (var i = 0; i < current.length && i < 256 && pending.length < 256; i++) {
                    if (object(ownValue(current, String(i)))) {
                        pending.push(current[i]);
                    }
                }
            }
        }
        return value;
    }

    function safelyPrune(value) {
        try {
            return prune(value);
        } catch (ignored) {
            return value;
        }
    }

    function playerString(value) {
        if (!active || typeof value !== 'string' || value.length > 5000000
                || !/"(?:playerAds|adPlacements|adSlots|adBreakHeartbeatParams)"/.test(value)) {
            return value;
        }
        try {
            return stringify(player(parse(value)));
        } catch (ignored) {
            return value;
        }
    }

    function watch(target, key, transform) {
        if (!object(target) || !Object.isExtensible(target) || restore.length >= 64) {
            return;
        }
        var keys = watched.get(target);
        if (!keys) {
            keys = new Set();
            watched.set(target, keys);
        }
        if (keys.has(key)) {
            return;
        }
        var descriptor = Object.getOwnPropertyDescriptor(target, key);
        // Do not replace site-defined accessors or non-writable properties.
        if (descriptor && (!descriptor.configurable || !descriptor.writable)) {
            if ('value' in descriptor) {
                transform(descriptor.value);
            }
            return;
        }
        var value = transform(descriptor && descriptor.value);
        var get = function () { return value; };
        Object.defineProperty(target, key, {
            configurable: true,
            enumerable: descriptor ? descriptor.enumerable : true,
            get: get,
            set: function (next) { value = active ? transform(next) : next; }
        });
        keys.add(key);
        restore.push(function () {
            var current = Object.getOwnPropertyDescriptor(target, key);
            if (!current || current.get !== get) {
                return;
            }
            if (!descriptor && value === undefined) {
                delete target[key];
            } else {
                Object.defineProperty(target, key, {
                    value: value, writable: true, configurable: true,
                    enumerable: descriptor ? descriptor.enumerable : true
                });
            }
        });
    }

    function installGlobals() {
        watch(window, 'ytInitialPlayerResponse', player);
        watch(window, 'ytInitialData', safelyPrune);
        watch(window, 'ytplayer', function (ytplayer) {
            watch(ytplayer, 'config', function (config) {
                watch(config, 'args', function (args) {
                    watch(args, 'player_response', playerString);
                    watch(args, 'raw_player_response', player);
                    return args;
                });
                return config;
            });
            return ytplayer;
        });
    }

    function replace(target, key, replacement) {
        var original = target[key];
        target[key] = replacement;
        restore.push(function () {
            if (target[key] === replacement) {
                target[key] = original;
            }
        });
    }

    function playerApi(url) {
        try {
            var parsed = new URL(url, location.href);
            return parsed.protocol === 'https:' && (!parsed.port || parsed.port === '443')
                    && !parsed.username && !parsed.password
                    && /^(www\.|m\.|music\.)?youtube\.com$/.test(parsed.hostname)
                    && /^\/youtubei\/v1\/(?:player|next|reel\/reel_item_watch|reel\/reel_watch_sequence)$/
                        .test(parsed.pathname);
        } catch (ignored) {
            return false;
        }
    }

    // Keep the browser's requests, response metadata, parsing errors and revivers intact.
    replace(JSON, 'parse', function () {
        var result = parse.apply(this, arguments);
        return active ? safelyPrune(result) : result;
    });
    if (window.Response && Response.prototype.json) {
        var responseJson = Response.prototype.json;
        replace(Response.prototype, 'json', function () {
            var filtered = playerApi(this.url);
            return responseJson.apply(this, arguments).then(function (value) {
                return active && filtered ? safelyPrune(value) : value;
            });
        });
    }
    if (window.XMLHttpRequest) {
        var prototype = XMLHttpRequest.prototype;
        var response = Object.getOwnPropertyDescriptor(prototype, 'response');
        if (response && response.get && response.configurable) {
            var getResponse = function () {
                var value = response.get.call(this);
                return active && this.responseType === 'json' && playerApi(this.responseURL)
                        ? safelyPrune(value) : value;
            };
            Object.defineProperty(prototype, 'response', {
                configurable: response.configurable,
                enumerable: response.enumerable,
                get: getResponse,
                set: response.set
            });
            restore.push(function () {
                var current = Object.getOwnPropertyDescriptor(prototype, 'response');
                if (current && current.get === getResponse) {
                    Object.defineProperty(prototype, 'response', response);
                }
            });
        }
    }

    function update() {
        if (!active) {
            return;
        }
        try {
            installGlobals();
            player(window.ytInitialPlayerResponse);
            // Fallback for a player that consumed its response before injection.
            // Never seek/accelerate media: stale ad UI must not skip the content video.
            var players = document.querySelectorAll(
                '#movie_player.ad-showing, #movie_player.ad-interrupting,'
                + '.html5-video-player.ad-showing, .html5-video-player.ad-interrupting');
            for (var i = 0; i < players.length; i++) {
                var buttons = players[i].querySelectorAll(
                    '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');
                for (var j = 0; j < buttons.length; j++) {
                    var button = buttons[j];
                    var style = window.getComputedStyle(button);
                    if (!button.disabled && button.getAttribute('aria-disabled') !== 'true'
                            && button.getClientRects().length && style.display !== 'none'
                            && style.visibility !== 'hidden' && style.opacity !== '0') {
                        button.click();
                        break;
                    }
                }
            }
        } catch (ignored) {
            // Site changes must not break content playback.
        }
    }

    var interval = setInterval(update, 500);
    document.addEventListener('DOMContentLoaded', update);
    window.addEventListener('yt-navigate-finish', update);
    window.__ssyoutubeVideoAdBlocking = {
        update: update,
        stop: function () {
            active = false;
            clearInterval(interval);
            document.removeEventListener('DOMContentLoaded', update);
            window.removeEventListener('yt-navigate-finish', update);
            for (var i = restore.length - 1; i >= 0; i--) {
                try { restore[i](); } catch (ignored) { }
            }
            delete window.__ssyoutubeVideoAdBlocking;
        }
    };
    update();
})();
