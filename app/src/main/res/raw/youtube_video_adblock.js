function (patched) {
    'use strict';
    if (location.protocol !== 'https:' || (location.port && location.port !== '443')
            || !/^(www\.|m\.|music\.)?youtube\.com$/.test(location.hostname)
            || (window.top && window.top !== window)) {
        return;
    }
    if (window.__ssyoutubeVideoAdBlocking) {
        if (window.__ssyoutubeVideoAdBlocking.patched === patched) {
            window.__ssyoutubeVideoAdBlocking.update();
            return;
        }
        window.__ssyoutubeVideoAdBlocking.stop();
    }

    var active = true;
    var restore = [];
    var watched = new WeakMap();
    var parse = JSON.parse;
    var stringify = JSON.stringify;
    var adKeys = ['playerAds', 'adPlacements', 'adSlots', 'adBreakHeartbeatParams'];
    var adRenderers = ['adSlotRenderer', 'displayAdRenderer', 'inFeedAdLayoutRenderer',
        'promotedSparklesWebRenderer', 'promotedSparklesTextSearchRenderer',
        'promotedVideoRenderer', 'compactPromotedVideoRenderer', 'searchPyvRenderer',
        'companionAdRenderer', 'playerLegacyDesktopWatchAdsRenderer',
        'merchandiseShelfRenderer', 'productListRenderer', 'paidContentOverlayRenderer',
        'adInterstitialRenderer'];
    var promoRenderers = ['statementBannerRenderer', 'bannerPromoRenderer',
        'mealbarPromoRenderer', 'backgroundPromoRenderer', 'premiumUpsellRenderer'];
    var conditionalRenderers = promoRenderers.concat(
        ['engagementPanelSectionListRenderer', 'endscreenElementRenderer']);
    var componentWrappers = {richItemRenderer: 'content', richSectionRenderer: 'content',
        openPopupAction: 'popup'};
    var componentPaths = ['response', 'contents', 'content', 'items', 'tabs', 'masthead',
        'richGridRenderer', 'richItemRenderer', 'richSectionRenderer', 'itemSectionRenderer',
        'sectionListRenderer', 'tabRenderer', 'singleColumnBrowseResultsRenderer',
        'twoColumnBrowseResultsRenderer', 'twoColumnSearchResultsRenderer',
        'singleColumnSearchResultsRenderer', 'twoColumnWatchNextResults',
        'singleColumnWatchNextResults', 'results', 'secondaryResults',
        'continuationContents', 'richGridContinuation', 'sectionListContinuation',
        'itemSectionContinuation', 'onResponseReceivedActions', 'onResponseReceivedEndpoints',
        'onResponseReceivedCommands', 'appendContinuationItemsAction',
        'reloadContinuationItemsCommand', 'continuationItems', 'engagementPanels',
        'paidContentOverlay', 'endscreen', 'endscreenRenderer', 'elements'];
    var textMarkers = new RegExp('"(' + adKeys.concat(adRenderers, conditionalRenderers,
        ['adClientParams']).join('|') + ')"');
    var changes = 0;
    var reported = new Set();
    var xhrText = new WeakMap();
    var uiStyle = null;
    var uiMarked = new Set();
    var dismissed = new WeakMap();
    var uiMarker = 'data-ssyoutube-patched-ad';
    var protectedUi = 'ytd-comments,ytm-comments-renderer,ytd-comment-thread-renderer,'
        + 'ytm-comment-thread-renderer,ytd-comment-renderer,ytm-comment-renderer,'
        + 'yt-live-chat-renderer,ytd-video-renderer,ytd-compact-video-renderer,'
        + 'ytd-rich-grid-media,ytm-media-item,yt-lockup-view-model,'
        + 'ytm-video-with-context-renderer,ytd-playlist-panel-renderer';
    var dialogs = 'tp-yt-paper-dialog,dialog,[role="dialog"],[aria-modal="true"],'
        + 'ytd-ad-interstitial-renderer,ytm-ad-interstitial-renderer,.ytp-ad-overlay-container';

    function report(reason) {
        if (!reported.has(reason)) {
            reported.add(reason);
            // Never log response bodies, request URLs, or exception messages.
            console.warn('ssYouTube adblocking: ' + reason);
        }
    }

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
                changes++;
            }
        });
        if (patched) {
            try { patchComponents(value); } catch (error) { report('Player UI data patch unavailable'); }
        }
        return value;
    }

    function promotionImage(value, store) {
        if (typeof value !== 'string') {
            return false;
        }
        try {
            var url = new URL(value, location.href);
            return url.protocol === 'https:' && (!url.port || url.port === '443')
                && !url.username && !url.password && /(^|\.)gstatic\.com$/.test(url.hostname)
                && (store ? /^\/shopping(?:\/|$)/.test(url.pathname)
                    : url.pathname.indexOf('/youtube/img/promos/growth/') === 0);
        } catch (error) {
            return false;
        }
    }

    // The archive distinguishes Premium promotions from ordinary statement banners.
    // Inspect typed endpoints/images only, never titles or arbitrary serialized page data.
    function promotionEvidence(value, store) {
        var pending = [value];
        var seen = new WeakSet();
        var paths = ['image', 'thumbnail', 'thumbnails', 'backgroundImage', 'foregroundImage',
            'bannerImage', 'button', 'buttons', 'buttonRenderer', 'buttonViewModel',
            'navigationEndpoint', 'endpoint', 'browseEndpoint', 'onTap', 'innertubeCommand',
            'callToAction', 'ctaButton', 'actionButton'].concat(promoRenderers);
        for (var index = 0; index < pending.length && index < 128; index++) {
            var current = pending[index];
            if (!object(current) || seen.has(current)) {
                continue;
            }
            seen.add(current);
            if ((!store && ownValue(current, 'browseId') === 'SPunlimited')
                    || promotionImage(ownValue(current, 'url'), store)) {
                return true;
            }
            var keys = Array.isArray(current)
                ? Array.from({length: Math.min(current.length, 64)}, function (_, i) { return String(i); })
                : paths;
            keys.forEach(function (key) {
                var child = ownValue(current, key);
                if (object(child) && pending.length < 128) {
                    pending.push(child);
                }
            });
        }
        return false;
    }

    function promotionalPanelId(value) {
        return typeof value === 'string'
            && /^(?:engagement-panel-)?(?:PAproduct|jumpahead)(?:$|[-:])/.test(value);
    }

    function promotionalPanel(value) {
        if (!object(value)) {
            return false;
        }
        var id = ownValue(value, 'targetId') || ownValue(value, 'panelIdentifier');
        var identifier = ownValue(value, 'identifier');
        if (promotionalPanelId(id)
                || (object(identifier) && promotionalPanelId(ownValue(identifier, 'tag')))) {
            return true;
        }
        var content = ownValue(value, 'content');
        return (!id || id === 'engagement-panel-shopping' || id === 'engagement-panel-product-list')
            && object(content) && object(ownValue(content, 'productListRenderer'));
    }

    function conditionalAd(value) {
        if (promoRenderers.some(function (key) {
            return promotionEvidence(ownValue(value, key), false);
        }) || promotionalPanel(ownValue(value, 'engagementPanelSectionListRenderer'))) {
            return true;
        }
        var end = ownValue(value, 'endscreenElementRenderer');
        return object(end) && ownValue(end, 'style') === 'WEBSITE'
            && promotionEvidence(ownValue(end, 'image'), true);
    }

    function adComponent(value) {
        for (var depth = 0; object(value) && !Array.isArray(value) && depth < 3; depth++) {
            var keys = Object.keys(value).filter(function (key) {
                return key !== 'trackingParams' && key !== 'clickTrackingParams';
            });
            if (keys.length !== 1) {
                return false;
            }
            var key = keys[0];
            var payload = ownValue(value, key);
            if (!object(payload)) {
                return false;
            }
            if (adRenderers.indexOf(key) !== -1
                    || (conditionalRenderers.indexOf(key) !== -1 && conditionalAd(value))) {
                return true;
            }
            if (!Object.prototype.hasOwnProperty.call(componentWrappers, key)) {
                return false;
            }
            value = ownValue(payload, componentWrappers[key]);
        }
        return false;
    }

    function mutableArray(value) {
        if (!Object.isExtensible(value) || Object.isSealed(value)
                || !Object.getOwnPropertyDescriptor(value, 'length').writable) {
            return false;
        }
        for (var i = 0; i < value.length; i++) {
            var descriptor = Object.getOwnPropertyDescriptor(value, String(i));
            if (descriptor && (!descriptor.configurable || !descriptor.writable)) {
                return false;
            }
        }
        return true;
    }

    // The web equivalent of a component filter: explicit ad renderers, not text matching.
    // Follow rendering envelopes only, leaving account, comments and media metadata alone.
    function patchComponents(value) {
        var pending = [value];
        var seen = new WeakSet();
        var budget = 16384;
        for (var cursor = 0; cursor < pending.length && cursor < 4096 && budget > 0; cursor++) {
            var current = pending[cursor];
            if (!object(current) || seen.has(current)) {
                continue;
            }
            seen.add(current);
            if (Array.isArray(current)) {
                if (current.length > 4096 || current.length * 2 > budget) {
                    report('Component limit reached');
                    continue;
                }
                budget -= current.length * 2 + 1;
                var mutable = mutableArray(current);
                for (var i = current.length - 1; i >= 0; i--) {
                    var item = ownValue(current, String(i));
                    if (adComponent(item) && mutable) {
                        current.splice(i, 1);
                        changes++;
                    } else if (object(item) && pending.length < 4096) {
                        pending.push(item);
                    }
                }
            } else {
                budget -= componentPaths.length;
                componentPaths.forEach(function (key) {
                    var child = ownValue(current, key);
                    if (adComponent(child)) {
                        var descriptor = Object.getOwnPropertyDescriptor(current, key);
                        if (descriptor.configurable) {
                            delete current[key];
                            changes++;
                        }
                    } else if (object(child) && pending.length < 4096) {
                        pending.push(child);
                    }
                });
            }
        }
        if (pending.length >= 4096 || budget <= 0) {
            report('Component limit reached');
        }
    }

    function prune(value, components) {
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
            if (patched && ((components && current === value)
                    || object(ownValue(current, 'responseContext')))) {
                patchComponents(current);
            }
            var entries = ownValue(current, 'entries');
            if (Array.isArray(entries) && !Object.isSealed(entries)) {
                for (var entry = Math.min(entries.length, 256) - 1; entry >= 0; entry--) {
                    var command = object(entries[entry]) && ownValue(entries[entry], 'command');
                    var reel = object(command) && ownValue(command, 'reelWatchEndpoint');
                    var params = object(reel) && ownValue(reel, 'adClientParams');
                    if (object(params) && ownValue(params, 'isAd') === true) {
                        entries.splice(entry, 1);
                        changes++;
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

    function safelyPrune(value, components) {
        try {
            return prune(value, components);
        } catch (error) {
            report('Response patch unavailable');
            return value;
        }
    }

    function responseText(value) {
        if (!active || typeof value !== 'string' || value.length > 5000000
                || !textMarkers.test(value)) {
            return value;
        }
        // Some Innertube responses carry an anti-XSSI prefix. Preserve it for the caller.
        var prefix = /^\)\]\}'(?:\r?\n)?/.exec(value);
        var body = prefix ? value.slice(prefix[0].length) : value;
        var parsed;
        try {
            parsed = parse(body);
        } catch (error) {
            report('Non-JSON response left unchanged');
            return value;
        }
        var before = changes;
        safelyPrune(parsed, true);
        return changes === before ? value : (prefix ? prefix[0] : '') + stringify(parsed);
    }

    function cachedXhrText(xhr, value) {
        if (!active || !patched || xhr.readyState !== 4
                || !playerApi(xhr.responseURL)) {
            return value;
        }
        var cached = xhrText.get(xhr);
        if (!cached || cached.input !== value || cached.url !== xhr.responseURL) {
            cached = {input: value, url: xhr.responseURL, output: responseText(value)};
            xhrText.set(xhr, cached);
        }
        return cached.output;
    }

    function playerString(value) {
        if (!active || typeof value !== 'string' || value.length > 5000000
                || !(patched ? textMarkers
                    : /"(?:playerAds|adPlacements|adSlots|adBreakHeartbeatParams)"/).test(value)) {
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
        watch(window, 'ytInitialData', function (value) { return safelyPrune(value, true); });
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
        var descriptor = Object.getOwnPropertyDescriptor(target, key);
        if (descriptor && (!descriptor.writable || !('value' in descriptor))) {
            report('Read-only browser hook left unchanged');
            return;
        }
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
                    && (/^\/youtubei\/v1\/(?:player|next|reel\/reel_item_watch|reel\/reel_watch_sequence)$/
                        .test(parsed.pathname)
                        || (patched && /^\/youtubei\/v1\/(?:browse|search)$/.test(parsed.pathname)));
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
                return active && filtered ? safelyPrune(value, true) : value;
            });
        });
    }
    if (patched && window.Response && Response.prototype.text) {
        var text = Response.prototype.text;
        replace(Response.prototype, 'text', function () {
            var filtered = playerApi(this.url);
            return text.apply(this, arguments).then(function (value) {
                return active && filtered ? responseText(value) : value;
            });
        });
    }
    if (window.XMLHttpRequest) {
        var prototype = XMLHttpRequest.prototype;
        var response = Object.getOwnPropertyDescriptor(prototype, 'response');
        if (response && response.get && response.configurable) {
            var getResponse = function () {
                var value = response.get.call(this);
                if (active && this.responseType === 'json' && playerApi(this.responseURL)) {
                    return safelyPrune(value, true);
                }
                return this.responseType === '' || this.responseType === 'text'
                        ? cachedXhrText(this, value) : value;
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
        var responseTextDescriptor = Object.getOwnPropertyDescriptor(prototype, 'responseText');
        if (patched && responseTextDescriptor && responseTextDescriptor.get
                && responseTextDescriptor.configurable) {
            var getText = function () {
                return cachedXhrText(this, responseTextDescriptor.get.call(this));
            };
            Object.defineProperty(prototype, 'responseText', {
                configurable: responseTextDescriptor.configurable,
                enumerable: responseTextDescriptor.enumerable,
                get: getText,
                set: responseTextDescriptor.set
            });
            restore.push(function () {
                var current = Object.getOwnPropertyDescriptor(prototype, 'responseText');
                if (current && current.get === getText) {
                    Object.defineProperty(prototype, 'responseText', responseTextDescriptor);
                }
            });
        } else if (patched) {
            report('XHR text hook unavailable');
        }
    }

    function clickable(button) {
        var style = window.getComputedStyle(button);
        if (button.disabled || button.getAttribute('aria-disabled') === 'true'
                || !button.getClientRects().length || style.visibility === 'hidden'
                || style.visibility === 'collapse' || style.pointerEvents === 'none') {
            return false;
        }
        for (var element = button; element; element = element.parentElement) {
            style = window.getComputedStyle(element);
            if (element.inert || style.display === 'none' || style.opacity === '0') {
                return false;
            }
        }
        return true;
    }

    function rendererSelectors(names) {
        return names.map(function (name) {
            var tag = name.replace(/[A-Z]/g, function (letter) { return '-' + letter.toLowerCase(); });
            return 'ytd-' + tag + ',ytm-' + tag;
        }).join(',');
    }

    function domPromotionEvidence(element, store) {
        if (promotionEvidence(ownValue(element, 'data'), store)) {
            return true;
        }
        var images = element.querySelectorAll('img[src],[style],a[href]');
        for (var i = 0; i < images.length && i < 128; i++) {
            var node = images[i];
            var background = /^url\(["']?([^"')]+)["']?\)$/.exec(node.style.backgroundImage);
            if (promotionImage(node.getAttribute('src'), store)
                    || (background && promotionImage(background[1], store))) {
                return true;
            }
            if (!store && node.hasAttribute('href')) {
                try {
                    var target = new URL(node.getAttribute('href'), location.href);
                    if (target.origin === location.origin && !target.username && !target.password
                            && target.pathname === '/premium') {
                        return true;
                    }
                } catch (error) {
                    // A malformed site link is not evidence of a promotion.
                }
            }
        }
        return false;
    }

    function updatePatchedUi() {
        var parent = document.head || document.documentElement;
        if (!parent) {
            return;
        }
        var next = new Set();
        function each(selector, callback) {
            var nodes = document.querySelectorAll(selector);
            if (nodes.length > 2048) {
                report('UI component limit reached');
            }
            for (var index = 0; index < nodes.length && index < 2048; index++) {
                var element = nodes[index];
                if (!element.closest(protectedUi)) {
                    callback(element);
                }
            }
        }
        function mark(element) {
            // Hiding only the contents of a modal can leave a backdrop or focus trap.
            if (element.closest(dialogs)) {
                return;
            }
            var wrapper = element.closest('ytd-rich-item-renderer,ytm-rich-item-renderer,'
                + 'ytd-rich-section-renderer,ytm-rich-section-renderer');
            next.add(wrapper && wrapper.children.length === 1 && wrapper.firstElementChild === element
                ? wrapper : element);
        }
        each(rendererSelectors(adRenderers.filter(function (name) {
            return name !== 'adInterstitialRenderer';
        })) + ',.ytp-paid-content-overlay', mark);
        each(rendererSelectors(promoRenderers), function (element) {
            if (domPromotionEvidence(element, false)) {
                mark(element);
            }
        });
        each('ytd-engagement-panel-section-list-renderer,ytm-engagement-panel-section-list-renderer',
            function (element) {
                if (promotionalPanelId(element.getAttribute('target-id'))
                        || promotionalPanelId(element.getAttribute('panel-identifier'))
                        || promotionalPanel(ownValue(element, 'data'))) {
                    mark(element);
                }
            });
        each('.ytp-ce-element.ytp-ce-website', function (element) {
            if (domPromotionEvidence(element, true)) {
                mark(element);
            }
        });
        each(rendererSelectors(['adSlotRenderer', 'adInterstitialRenderer'])
                + ',.ytp-ad-overlay-container', function (element) {
            var dialog = element.closest(dialogs);
            if (!dialog || dialog.querySelector('form,input[type="password"],'
                    + 'yt-enforcement-message-view-model,ytd-enforcement-message-view-model,'
                    + 'ytm-enforcement-message-view-model,ytd-confirm-dialog-renderer,'
                    + 'ytm-confirm-dialog-renderer,ytd-player-interstitial-renderer,'
                    + 'ytm-player-interstitial-renderer,.ytp-error,'
                    + 'ytd-sign-in-dialog-renderer,ytm-sign-in-dialog-renderer,'
                    + 'ytd-consent-bump-v2-lightbox')) {
                return;
            }
            var buttons = dialog.querySelectorAll('.ytp-ad-overlay-close-button,button#dismiss-button,'
                + '#dismiss-button button,button#close-button,#close-button button');
            if (!buttons.length) {
                report('Ad dialog dismissal unavailable');
                return;
            }
            var close = Array.from(buttons).find(clickable);
            var now = Date.now();
            if (close && (!dismissed.has(close) || now - dismissed.get(close) >= 5000)) {
                dismissed.set(close, now);
                close.click();
            }
        });
        if (!active) {
            return;
        }
        uiMarked.forEach(function (element) {
            if (!next.has(element)) {
                element.removeAttribute(uiMarker);
            }
        });
        next.forEach(function (element) {
            if (!element.hasAttribute(uiMarker)) {
                element.setAttribute(uiMarker, '');
            }
        });
        uiMarked = next;
        if (!uiStyle) {
            uiStyle = document.createElement('style');
            uiStyle.id = 'ssyoutube-patched-adblocking';
        }
        if (uiStyle.parentNode !== parent) {
            parent.appendChild(uiStyle);
        }
        var css = '[' + uiMarker + ']{display:none!important;}';
        if (uiStyle.textContent !== css) {
            uiStyle.textContent = css;
        }
    }

    if (patched) {
        restore.push(function () {
            uiMarked.forEach(function (element) { element.removeAttribute(uiMarker); });
            uiMarked.clear();
            if (uiStyle) {
                uiStyle.remove();
            }
            dismissed = new WeakMap();
        });
    }

    function update() {
        if (!active) {
            return;
        }
        try {
            installGlobals();
            player(window.ytInitialPlayerResponse);
            if (patched) {
                try { updatePatchedUi(); } catch (error) { report('Patched UI filtering unavailable'); }
            }
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
                    if (clickable(button)) {
                        button.click();
                        break;
                    }
                }
            }
        } catch (error) {
            report('Player update unavailable');
        }
    }

    var interval = setInterval(update, 500);
    document.addEventListener('DOMContentLoaded', update);
    window.addEventListener('yt-navigate-finish', update);
    window.__ssyoutubeVideoAdBlocking = {
        patched: patched,
        update: update,
        stop: function () {
            if (!active) {
                return;
            }
            active = false;
            clearInterval(interval);
            document.removeEventListener('DOMContentLoaded', update);
            window.removeEventListener('yt-navigate-finish', update);
            for (var i = restore.length - 1; i >= 0; i--) {
                try { restore[i](); } catch (error) { report('Hook restoration unavailable'); }
            }
            xhrText = new WeakMap();
            delete window.__ssyoutubeVideoAdBlocking;
        }
    };
    update();
}
