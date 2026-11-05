const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawnSync} = require('node:child_process');
const {pathToFileURL} = require('node:url');
const source = fs.readFileSync(
    path.join(__dirname, '../../main/res/raw/youtube_page_elements.js'), 'utf8');
const adSource = fs.readFileSync(
    path.join(__dirname, '../../main/res/raw/youtube_video_adblock.js'), 'utf8');
const backgroundSource = fs.readFileSync(
    path.join(__dirname, '../../main/res/raw/youtube_background_audio.js'), 'utf8');

// Run the shipped function against Chromium's real DOM, selectors and MutationObserver.
async function browserFixtures(source, adSource, backgroundSource) {
    const results = [];
    const location = new URL('https://m.youtube.com/');
    const inject = new Function('location', 'options', `return (${source})(options);`);
    const keys = ['shorts', 'playables', 'recommended', 'distracting', 'thumbnails',
        'autodubbed', 'members_only', 'end_video'];
    const off = Object.fromEntries(keys.map(key => [key, false]));
    const defaults = {...off, playables: true,
        autodubbed: true, members_only: true, end_video: true};
    const original = {
        fetch: window.fetch, parse: JSON.parse, json: Response.prototype.json,
        play: HTMLMediaElement.prototype.play, pushState: history.pushState
    };
    let observers = 0;
    const OriginalObserver = window.MutationObserver;
    window.MutationObserver = class extends OriginalObserver {
        constructor(callback) { super(callback); observers++; }
    };
    const check = (value, message) => { if (!value) throw new Error(message); };
    const visible = id => {
        let element = document.getElementById(id);
        check(element, `Missing fixture: ${id}`);
        for (; element; element = element.parentElement) {
            if (getComputedStyle(element).display === 'none') return false;
        }
        return true;
    };
    const update = options => inject(location, {...off, ...options});
    const fixture = (html, options = off) => {
        location.href = 'https://m.youtube.com/';
        update(off);
        document.body.innerHTML = html;
        update(options);
    };
    const wait = () => new Promise(resolve => setTimeout(resolve, 150));
    async function run(name, fn) {
        try {
            await fn();
            results.push({name, passed: true});
        } catch (error) {
            results.push({name, passed: false, error: String(error)});
        }
    }

    const content = `
        <ytm-rich-item-renderer id="short"><a href="/shorts/video">Short</a></ytm-rich-item-renderer>
        <ytd-rich-item-renderer id="playable"><a href="/playables/game">Game</a></ytd-rich-item-renderer>
        <ytm-comments-entry-point-header-renderer id="comments">Comments</ytm-comments-entry-point-header-renderer>
        <ytm-slim-video-metadata-section-renderer id="info">Info</ytm-slim-video-metadata-section-renderer>
        <ytm-rich-item-renderer id="dub"><span class="ytContentMetadataViewModelMetadataText">Auto-dubbed</span></ytm-rich-item-renderer>
        <ytd-video-renderer id="member"><ytd-badge-supported-renderer>Members only</ytd-badge-supported-renderer></ytd-video-renderer>
        <ytm-item-section-renderer id="related" section-identifier="related-items">Up next</ytm-item-section-renderer>
        <div id="end" class="ytm-player-endscreen">End cards</div>
        <div id="endOverlay" class="ytp-ce-element-show">Clickable overlay</div>
        <ytd-thumbnail id="thumbnail" style="display:inline-block">Image</ytd-thumbnail>
        <ytm-rich-grid-renderer id="home">Home feed</ytm-rich-grid-renderer>
        <img id="avatar" alt="Channel avatar">
        <video id="player"></video>
        <a id="search" href="/results?search_query=music">Search</a>`;

    await run('defaults keep Shorts, comments, recommendations and thumbnails visible', () => {
        fixture(content, defaults);
        for (const id of ['playable', 'dub', 'member', 'endOverlay']) {
            check(!visible(id), `${id} must be hidden by default`);
        }
        for (const id of ['short', 'comments', 'info', 'related', 'end', 'thumbnail',
            'home', 'avatar', 'player', 'search']) {
            check(visible(id), `${id} must remain visible`);
        }
    });
    await run('each option works alone and disabling restores the same DOM nodes and styles', () => {
        const targets = {shorts: 'short', playables: 'playable', recommended: 'related',
            distracting: 'comments', thumbnails: 'thumbnail', autodubbed: 'dub', members_only: 'member',
            end_video: 'endOverlay'};
        fixture(content);
        const nodes = Object.fromEntries(Object.values(targets)
            .map(id => [id, document.getElementById(id)]));
        for (const key of keys) {
            update({[key]: true});
            for (const [category, id] of Object.entries(targets)) {
                check(visible(id) === (category !== key), `${key} must only hide ${targets[key]}`);
            }
            update(off);
            for (const id of Object.values(targets)) {
                check(visible(id) && nodes[id] === document.getElementById(id), `Restore ${id} in place`);
            }
        }
        check(document.getElementById('thumbnail').style.display === 'inline-block', 'Keep site styles');
        check(!document.getElementById('ssyoutube-page-elements'), 'Remove disabled stylesheet');
        check(!document.querySelector('[data-ssyoutube-blocked-element]'), 'Remove disabled markers');
    });
    await run('mobile and desktop shelves, modern lockups, navigation and tabs are blocked', () => {
        fixture(`
            <ytm-rich-section-renderer id="mobile"><h2>Shorts</h2></ytm-rich-section-renderer>
            <div class="ytGridShelfViewModelHost" id="grid"><h2>Shorts</h2></div>
            <ytd-rich-item-renderer id="lockup"><yt-lockup-view-model><a href="https://www.youtube.com/shorts/id">Short</a></yt-lockup-view-model></ytd-rich-item-renderer>
            <ytm-pivot-bar-item-renderer id="nav"><a href="/shorts">Shorts</a></ytm-pivot-bar-item-renderer>
            <yt-tab-shape id="tab"><div class="ytTabShapeTab">Shorts</div></yt-tab-shape>
            <ytd-mini-guide-entry-renderer id="guide"><a href="/playables">Games</a></ytd-mini-guide-entry-renderer>
            <ytd-horizontal-card-list-renderer id="games"><h2>YouTube Playables</h2></ytd-horizontal-card-list-renderer>
            <ytm-shelf-renderer id="regular"><h2>Videos</h2></ytm-shelf-renderer>
            <ytm-rich-item-renderer id="title"><a href="/watch?v=x">How Shorts and Playables work</a></ytm-rich-item-renderer>
        `, {shorts: true, playables: true});
        for (const id of ['mobile', 'grid', 'lockup', 'nav', 'tab', 'guide', 'games']) {
            check(!visible(id), `Hide ${id}`);
        }
        check(visible('regular') && visible('title'), 'Do not filter by unrelated video titles');
    });
    await run('badges identify auto-dubbed and members-only cards, not arbitrary titles', () => {
        fixture(`
            <yt-lockup-view-model id="dubbed"><div class="yt-content-metadata-view-model__metadata-text">Auto-dubbed</div></yt-lockup-view-model>
            <ytm-video-with-context-renderer id="french"><ytm-badge-supported-renderer>R\u00e9serv\u00e9 aux membres</ytm-badge-supported-renderer></ytm-video-with-context-renderer>
            <ytd-rich-item-renderer id="nested"><yt-lockup-view-model><span badge-style="BADGE_STYLE_TYPE_MEMBERS_ONLY">Localized badge</span></yt-lockup-view-model></ytd-rich-item-renderer>
            <ytm-media-item id="normal"><h3>Auto-dubbed and Members only tutorial</h3></ytm-media-item>
        `, {autodubbed: true, members_only: true});
        check(!visible('dubbed') && !visible('french') && !visible('nested'), 'Hide marked cards');
        check(visible('normal'), 'Keep title-only matches');
    });
    await run('procedural filters leave comments and non-YouTube lookalike links alone', () => {
        fixture(`
            <ytm-engagement-panel id="panel"><ytm-comment-renderer>
                <ytm-media-item id="comment"><a href="/shorts/id">Shorts</a>
                <span class="badge">Members only</span></ytm-media-item>
            </ytm-comment-renderer></ytm-engagement-panel>
            <ytd-video-renderer id="external"><a href="https://youtube.com.evil.example/playables/game">Game</a></ytd-video-renderer>
            <ytm-media-item id="malformed"><a href="https://[">Bad link</a></ytm-media-item>
            <ytm-media-item id="parameter"><a href="/watch?v=x&amp;next=/shorts/id">Video</a></ytm-media-item>
        `, {...defaults, distracting: false});
        for (const id of ['panel', 'comment', 'external', 'malformed', 'parameter']) {
            check(visible(id), `Preserve ${id}`);
        }
    });
    await run('recommended content hides up next and end cards but not the home feed or playlists', () => {
        fixture(`
            <ytd-watch-flexy><div id="related">Recommendations</div>
                <ytd-playlist-panel-renderer id="playlist">Playlist</ytd-playlist-panel-renderer></ytd-watch-flexy>
            <div class="ytp-ce-element" id="end">End card</div>
            <div class="ytp-endscreen-content" id="endGrid">End grid</div>
            <div class="feed-nudge-wrapper" id="nudge">Try searching to get started</div>
            <ytm-rich-grid-renderer id="home">Home</ytm-rich-grid-renderer>
        `, {recommended: true});
        for (const id of ['related', 'endGrid', 'nudge']) check(!visible(id), `Hide ${id}`);
        check(visible('end'), 'Clickable overlays have their own setting');
        check(visible('home') && visible('playlist'), 'Preserve home and playlist controls');
    });
    await run('end-video elements are independently reversible with recommendations off or on', () => {
        fixture(content, {end_video: true});
        check(!visible('endOverlay') && visible('related') && visible('end'), 'Only hide overlays');
        update({end_video: true, recommended: true});
        check(!visible('endOverlay') && !visible('related') && !visible('end'), 'Both filters apply');
        update({recommended: true});
        check(visible('endOverlay') && !visible('related'), 'Restore overlays independently');
        update(off);
        check(visible('endOverlay') && visible('related') && visible('end'), 'Restore all elements');
    });
    await run('distracting controls cover mobile and desktop without hiding search or the player', () => {
        fixture(`
            <ytm-topbar-menu-button-renderer id="mobileMenu">Menu</ytm-topbar-menu-button-renderer>
            <button class="mobile-topbar-header-sign-in-button" id="signin">Sign in</button>
            <ytd-masthead><button id="guide-button">Menu</button><input id="search"></ytd-masthead>
            <ytd-comments id="comments">Comments</ytd-comments>
            <ytd-watch-metadata id="metadata">Info</ytd-watch-metadata>
            <video id="video"></video>
        `, {distracting: true});
        for (const id of ['mobileMenu', 'signin', 'guide-button', 'comments', 'metadata']) {
            check(!visible(id), `Hide ${id}`);
        }
        check(visible('search') && visible('video'), 'Keep core browsing and playback');
    });
    await run('thumbnail option covers mobile, desktop, Shorts and previews but not avatars', () => {
        fixture(`
            <div class="video-thumbnail-container-large" id="mobile">Thumbnail</div>
            <div class="ytThumbnailViewModelHost" id="desktop">Thumbnail</div>
            <div class="shortsLockupViewModelHostThumbnailParentContainer" id="shorts">Thumbnail</div>
            <div id="video-preview-container">Preview</div><ytm-video-preview id="preview">Preview</ytm-video-preview>
            <img id="avatar"><video id="video"></video>
        `, {thumbnails: true});
        for (const id of ['mobile', 'desktop', 'shorts', 'video-preview-container', 'preview']) {
            check(!visible(id), `Hide ${id}`);
        }
        check(visible('avatar') && visible('video'), 'Keep avatars and video playback');
    });
    await run('late insertion, recycled badges, changing hrefs and stylesheet replacement are handled', async () => {
        fixture('<div id="feed"></div>', {shorts: true, members_only: true, autodubbed: true});
        document.getElementById('feed').innerHTML = `
            <ytm-media-item id="late"><a href="/watch?v=x">Video</a><span class="badge">Members only</span></ytm-media-item>`;
        await wait();
        check(!visible('late'), 'Hide late members-only card');
        document.querySelector('.badge').firstChild.data = 'Public';
        await wait();
        check(visible('late'), 'Restore recycled public card');
        document.querySelector('#late a').setAttribute('href', '/shorts/new');
        await wait();
        check(!visible('late'), 'Hide recycled Shorts card');
        document.getElementById('ssyoutube-page-elements').remove();
        await wait();
        check(!visible('late'), 'Repair stylesheet after SPA replacement');
        update(off);
        document.querySelector('.badge').textContent = 'Auto-dubbed';
        await wait();
        check(visible('late'), 'Disabled observers cannot reapply an old preference');
    });
    await run('reinjection and SPA events reuse a single observer and preserve unrelated styles', async () => {
        fixture(content, defaults);
        const before = observers;
        const siteStyle = document.createElement('style');
        siteStyle.id = 'site-style';
        siteStyle.textContent = '#home { color: rgb(1, 2, 3); }';
        document.head.appendChild(siteStyle);
        for (let count = 0; count < 5; count++) update(defaults);
        location.href = 'https://www.youtube.com/watch?v=next';
        document.dispatchEvent(new Event('yt-navigate-finish'));
        window.dispatchEvent(new PopStateEvent('popstate'));
        await wait();
        check(observers === before && observers === 1, 'Only one observer per document');
        check(document.querySelectorAll('#ssyoutube-page-elements').length === 1, 'Only one stylesheet');
        update(off);
        check(getComputedStyle(document.getElementById('home')).color === 'rgb(1, 2, 3)', 'Keep site styles');
        siteStyle.remove();
    });
    await run('direct Shorts playback is not navigated or suppressed', () => {
        fixture('<video id="video"></video><div id="player-controls">Controls</div>');
        location.href = 'https://m.youtube.com/shorts/direct';
        update({shorts: true});
        check(visible('video') && visible('player-controls'), 'Leave direct Shorts playback available');
        check(location.pathname === '/shorts/direct', 'Do not rewrite navigation');
    });
    await run('only supported HTTPS main documents receive filtering', () => {
        fixture(content);
        const enabled = {...defaults, shorts: true, distracting: true};
        for (const url of ['https://accounts.google.com/', 'https://youtube.com.evil.example/',
            'http://m.youtube.com/', 'https://www.youtube.com:444/', 'https://studio.youtube.com/']) {
            location.href = url;
            update(enabled);
            check(visible('short') && visible('comments'), `Reject ${url}`);
        }
        for (const host of ['youtube.com', 'www.youtube.com', 'm.youtube.com', 'music.youtube.com']) {
            location.href = `https://${host}/`;
            update(enabled);
            check(!visible('short'), `Accept ${host}`);
            update(off);
        }
        const frame = document.createElement('iframe');
        document.body.appendChild(frame);
        frame.contentWindow.Function('location', 'options', `return (${source})(options);`)
            (location, enabled);
        check(!frame.contentWindow.__ssyoutubePageElements, 'Do not filter embedded frames');
        frame.remove();
    });
    await run('filtering never replaces network, media or history APIs', () => {
        check(window.fetch === original.fetch && JSON.parse === original.parse
            && Response.prototype.json === original.json, 'Network/JSON APIs remain untouched');
        check(HTMLMediaElement.prototype.play === original.play, 'Media APIs remain untouched');
        check(history.pushState === original.pushState, 'History API remains untouched');
    });
    await run('video adblocking and native-player styles remain independent of page filters', () => {
        fixture(content, {...defaults, shorts: true});
        const nativeStyle = document.createElement('style');
        nativeStyle.id = 'ssyoutube-native-player';
        nativeStyle.textContent = 'video{display:none!important;}';
        document.head.appendChild(nativeStyle);
        new Function('location', `return (${adSource})(false);`)(location);
        window.ytInitialPlayerResponse = {
            videoDetails: {videoId: 'content'}, playabilityStatus: {status: 'OK'},
            adPlacements: [{ad: 'preroll'}]
        };
        check(!window.ytInitialPlayerResponse.adPlacements, 'Existing video-ad filtering still works');
        window.__ssyoutubeVideoAdBlocking.stop();
        check(!visible('short') && !visible('member'), 'Stopping adblocking keeps page filtering');
        update(off);
        check(visible('short') && visible('member'), 'Page filters can still be restored');
        check(nativeStyle.isConnected && !visible('player'), 'Keep native player suppression');
        nativeStyle.remove();
        delete window.ytInitialPlayerResponse;
    });
    await run('patched adblocking preserves native Response bodies, clones and browser metadata', async () => {
        const originalText = Response.prototype.text;
        const originalXhrText = Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, 'responseText');
        const injectAds = patched =>
            new Function('location', `return (${adSource})(${patched});`)(location);
        injectAds(true);
        try {
            const body = JSON.stringify({videoDetails: {videoId: 'content'},
                streamingData: {formats: [{url: 'https://media.googlevideo.com/video'}]},
                playabilityStatus: {status: 'OK'}, adPlacements: [{ad: 'preroll'}]});
            const response = new Response(body, {
                status: 201, statusText: 'Created', headers: {'Content-Type': 'application/json'}
            });
            // A local fixture supplies the URL without making a YouTube request.
            Object.defineProperty(response, 'url', {value: 'https://m.youtube.com/youtubei/v1/player'});
            const clone = response.clone();
            Object.defineProperty(clone, 'url', {value: response.url});
            const headers = response.headers;
            const parsed = original.parse(await response.text());
            check(!parsed.adPlacements && parsed.streamingData.formats.length === 1, 'Patch text before parsing');
            check(response.bodyUsed && !clone.bodyUsed, 'Preserve stream consumption');
            check(response.status === 201 && response.statusText === 'Created'
                && response.headers === headers, 'Preserve browser metadata');
            let rejected = false;
            try { await response.json(); } catch (error) { rejected = error instanceof TypeError; }
            check(rejected, 'A second body read still rejects');
            check(!(await clone.json()).adPlacements, 'Cloned JSON responses are patched');
            const xhr = new XMLHttpRequest();
            xhr.open('GET', 'data:application/json,' + encodeURIComponent(body));
            Object.defineProperty(xhr, 'responseURL', {value: response.url});
            await new Promise((resolve, reject) => {
                xhr.onload = resolve;
                xhr.onerror = reject;
                xhr.send();
            });
            check(!original.parse(xhr.responseText).adPlacements
                && xhr.response === xhr.responseText, 'Native XHR text and response are patched together');
            const bad = new Response('{invalid');
            Object.defineProperty(bad, 'url', {value: response.url});
            rejected = false;
            try { await bad.json(); } catch (error) { rejected = error instanceof SyntaxError; }
            check(rejected, 'JSON errors are not converted to successes');
            injectAds(false);
            check(Response.prototype.text === originalText, 'Basic mode restores text consumption');
            check(Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, 'responseText').get
                === originalXhrText.get, 'Basic mode restores XHR text access');
            check(window.fetch === original.fetch && HTMLMediaElement.prototype.play === original.play,
                'Do not replay requests or restart media');
        } finally {
            window.__ssyoutubeVideoAdBlocking.stop();
        }
        check(JSON.parse === original.parse && Response.prototype.json === original.json,
            'Restore browser methods on stop');
    });
    const injectAds = patched =>
        new Function('location', `return (${adSource})(${patched});`)(location);
    async function adFixture(html, verify, setup = () => {}) {
        fixture(html);
        setup();
        injectAds(true);
        try {
            await verify();
        } finally {
            window.__ssyoutubeVideoAdBlocking?.stop();
        }
    }
    await run('patched-only UI hides explicit ads and shopping without the basic rule engine', async () => {
        await adFixture(`
            <ytd-rich-item-renderer id="adCard"><ytd-ad-slot-renderer>Ad</ytd-ad-slot-renderer></ytd-rich-item-renderer>
            <ytm-merchandise-shelf-renderer id="merch">Merchandise</ytm-merchandise-shelf-renderer>
            <ytd-product-list-renderer id="products">Products</ytd-product-list-renderer>
            <div class="ytp-paid-content-overlay" id="disclosure">Includes paid promotion</div>
            <ytm-paid-content-overlay-renderer id="mobileDisclosure">Paid promotion</ytm-paid-content-overlay-renderer>
            <ytd-engagement-panel-section-list-renderer target-id="engagement-panel-PAproduct" id="shoppingPanel">Shop</ytd-engagement-panel-section-list-renderer>
            <ytd-engagement-panel-section-list-renderer panel-identifier="jumpahead" id="premiumPanel">Premium</ytd-engagement-panel-section-list-renderer>
            <ytd-video-renderer id="organic">Premium product review</ytd-video-renderer>
            <ytm-rich-item-renderer id="short"><a href="/shorts/id">Short</a></ytm-rich-item-renderer>
            <video id="video" style="opacity:0.9"></video>
        `, () => {
            const hidden = ['adCard', 'merch', 'products', 'disclosure', 'mobileDisclosure',
                'shoppingPanel', 'premiumPanel'];
            const nodes = hidden.map(id => document.getElementById(id));
            hidden.forEach(id => check(!visible(id), `Hide ${id}`));
            check(visible('organic') && visible('video') && visible('short'), 'Keep ordinary playback and cards');
            update({shorts: true});
            injectAds(false);
            hidden.forEach((id, index) => {
                check(visible(id) && document.getElementById(id) === nodes[index], `Restore ${id} in place`);
            });
            check(!visible('short'), 'Keep the independent page-element setting');
            check(!document.querySelector('[data-ssyoutube-patched-ad]')
                && !document.getElementById('ssyoutube-patched-adblocking'), 'Remove owned UI state');
            check(document.getElementById('video').style.opacity === '0.9', 'Preserve site styling');
            check(window.fetch === original.fetch && HTMLMediaElement.prototype.play === original.play,
                'No request replay or media control');
        });
    });
    await run('patched Premium and end-card UI preserves ordinary banners, videos and comments', async () => {
        await adFixture(`
            <ytd-statement-banner-renderer id="premium"><img src="https://www.gstatic.com/youtube/img/promos/growth/premium.png"></ytd-statement-banner-renderer>
            <ytm-background-promo-renderer id="upsell"><a href="/premium">Try Premium</a></ytm-background-promo-renderer>
            <ytd-statement-banner-renderer id="doodle">SPunlimited img/promos/growth/ tutorial</ytd-statement-banner-renderer>
            <ytd-statement-banner-renderer id="lookalike"><img src="https://www.gstatic.com.evil.test/youtube/img/promos/growth/a.png"></ytd-statement-banner-renderer>
            <ytd-statement-banner-renderer id="external"><a href="https://example.test/premium">Premium</a></ytd-statement-banner-renderer>
            <ytd-video-renderer id="organic"><ytd-paid-content-overlay-renderer>Paid label in ordinary card</ytd-paid-content-overlay-renderer></ytd-video-renderer>
            <ytd-rich-grid-media><ytd-ad-slot-renderer id="homeContent">Ordinary home card</ytd-ad-slot-renderer></ytd-rich-grid-media>
            <yt-lockup-view-model><ytd-product-list-renderer id="lockupContent">Ordinary lockup</ytd-product-list-renderer></yt-lockup-view-model>
            <ytd-rich-item-renderer id="mixedCard"><ytd-merchandise-shelf-renderer id="mixedShop">Shop</ytd-merchandise-shelf-renderer><span id="mixedText">Unclassified content</span></ytd-rich-item-renderer>
            <ytd-comment-thread-renderer id="comment"><ytd-product-list-renderer id="commentProduct">Comment fixture</ytd-product-list-renderer></ytd-comment-thread-renderer>
            <yt-live-chat-renderer><div class="ytp-paid-content-overlay" id="chatLabel">Chat fixture</div></yt-live-chat-renderer>
            <ytd-playlist-panel-renderer><ytd-ad-slot-renderer id="playlistItem">Playlist fixture</ytd-ad-slot-renderer></ytd-playlist-panel-renderer>
            <ytd-engagement-panel-section-list-renderer target-id="engagement-panel-comments-section" id="commentsPanel">Comments</ytd-engagement-panel-section-list-renderer>
            <a class="ytp-ce-element ytp-ce-website" id="store"><span style="background-image:url('https://encrypted-tbn0.gstatic.com/shopping?q=product')">Shop</span></a>
            <a class="ytp-ce-element ytp-ce-website" id="website"><img src="https://example.test/website.png">Website</a>
            <a class="ytp-ce-element ytp-ce-video" id="endVideo"><img src="https://encrypted-tbn0.gstatic.com/shopping?q=product">Video</a>
            <div role="dialog" id="accountDialog"><ytd-statement-banner-renderer id="accountBanner"><a href="/premium">Account information</a></ytd-statement-banner-renderer></div>
        `, () => {
            for (const id of ['premium', 'upsell', 'store', 'mixedShop']) check(!visible(id), `Hide ${id}`);
            for (const id of ['doodle', 'lookalike', 'external', 'organic', 'commentProduct',
                'chatLabel', 'playlistItem', 'commentsPanel', 'website', 'endVideo', 'accountBanner',
                'homeContent', 'lockupContent', 'mixedText']) {
                check(visible(id), `Preserve ${id}`);
            }
        });
    });
    await run('patched UI repairs its stylesheet and restores recycled nodes without extra observers', async () => {
        await adFixture(`
            <ytd-statement-banner-renderer id="recycled"><a href="/premium">Promotion</a></ytd-statement-banner-renderer>
            <ytd-rich-item-renderer id="recycledCard"><ytd-merchandise-shelf-renderer>Shop</ytd-merchandise-shelf-renderer></ytd-rich-item-renderer>
        `, async () => {
            const before = observers;
            const node = document.getElementById('recycled');
            check(!visible('recycled') && !visible('recycledCard'), 'Hide initial promotions');
            node.querySelector('a').setAttribute('href', '/watch?v=organic');
            const card = document.getElementById('recycledCard');
            card.innerHTML = '<ytd-video-renderer>Organic video</ytd-video-renderer>';
            window.__ssyoutubeVideoAdBlocking.update();
            check(visible('recycled') && visible('recycledCard'), 'Restore recycled content');
            node.data = {button: {buttonRenderer: {navigationEndpoint: {browseEndpoint: {browseId: 'SPunlimited'}}}}};
            window.__ssyoutubeVideoAdBlocking.update();
            check(!visible('recycled'), 'Read typed site promotion data');
            node.data = {title: {simpleText: 'Ordinary banner'}};
            window.__ssyoutubeVideoAdBlocking.update();
            check(visible('recycled'), 'Clear stale data markers');
            document.body.insertAdjacentHTML('beforeend', '<ytm-product-list-renderer id="lateShop">Shop</ytm-product-list-renderer>');
            await new Promise(resolve => setTimeout(resolve, 600));
            check(!visible('lateShop'), 'Existing heartbeat handles dynamic content');
            document.getElementById('ssyoutube-patched-adblocking').textContent = '';
            window.__ssyoutubeVideoAdBlocking.update();
            check(!visible('lateShop'), 'Repair changed stylesheet contents');
            document.getElementById('ssyoutube-patched-adblocking').remove();
            injectAds(true);
            check(!visible('lateShop') && document.querySelectorAll('#ssyoutube-patched-adblocking').length === 1,
                'Reinjection repairs without stacking styles');
            check(observers === before, 'Do not install another mutation observer');
            window.__ssyoutubeVideoAdBlocking.stop();
            check(visible('lateShop') && visible('recycledCard'), 'Stop restores late and recycled content');
        });
    });
    await run('ad dialogs dismiss through site close handlers so backdrops and focus traps are released', async () => {
        let closed = 0;
        await adFixture(`
            <div id="backdrop">Backdrop</div>
            <div role="dialog" id="adDialog" style="pointer-events:none"><ytd-ad-slot-renderer>Ad</ytd-ad-slot-renderer>
                <button class="ytp-ad-overlay-close-button" style="display:none">Hidden close</button>
                <button id="dismiss-button" style="pointer-events:auto">Close</button></div>
            <div class="ytp-ad-overlay-container" id="overlay">Overlay ad
                <button class="ytp-ad-overlay-close-button">Close</button></div>
            <div role="dialog" id="unsupported"><ytd-ad-slot-renderer>Ad without dismiss control</ytd-ad-slot-renderer></div>
        `, () => {
            check(closed === 2, 'Use each supported close handler');
            check(!document.getElementById('adDialog') && !document.getElementById('backdrop')
                && !document.getElementById('overlay'), 'Let site handlers release their own modal state');
            check(visible('unsupported') && !document.querySelector('#unsupported [data-ssyoutube-patched-ad]'),
                'Never merely hide an unsupported modal');
        }, () => {
            document.getElementById('dismiss-button').onclick = () => {
                closed++;
                document.getElementById('adDialog').remove();
                document.getElementById('backdrop').remove();
            };
            document.querySelector('#overlay .ytp-ad-overlay-close-button').onclick = () => {
                closed++;
                document.getElementById('overlay').remove();
            };
        });
    });
    await run('ad dismissal leaves disabled, invisible, account, consent and playback-error dialogs intact', async () => {
        let clicked = 0;
        await adFixture(`
            <div role="dialog" id="disabled"><ytd-ad-slot-renderer>Ad</ytd-ad-slot-renderer><button class="ytp-ad-overlay-close-button" disabled>Close</button></div>
            <div role="dialog" id="ariaDisabled"><ytd-ad-slot-renderer>Ad</ytd-ad-slot-renderer><button class="ytp-ad-overlay-close-button" aria-disabled="true">Close</button></div>
            <div role="dialog" id="invisible" style="opacity:0"><ytd-ad-slot-renderer>Ad</ytd-ad-slot-renderer><button class="ytp-ad-overlay-close-button">Close</button></div>
            <div role="dialog" id="inertDialog" inert><ytd-ad-slot-renderer>Ad</ytd-ad-slot-renderer><button class="ytp-ad-overlay-close-button">Close</button></div>
            <div role="dialog" id="generic"><button class="ytp-ad-overlay-close-button">Close account</button></div>
            <div role="dialog" id="confirm"><ytd-ad-slot-renderer>Ad</ytd-ad-slot-renderer><button>OK</button></div>
            <div role="dialog" id="login"><ytd-ad-slot-renderer>Stale ad</ytd-ad-slot-renderer><form>Sign in</form><button class="ytp-ad-overlay-close-button">Close</button></div>
            <div role="dialog" id="denied"><ytd-ad-slot-renderer>Stale ad</ytd-ad-slot-renderer><yt-enforcement-message-view-model><a href="/premium">Playback unavailable</a></yt-enforcement-message-view-model><button class="ytp-ad-overlay-close-button">Close</button></div>
            <div role="dialog" id="error"><ytd-ad-slot-renderer>Stale ad</ytd-ad-slot-renderer><div class="ytp-error">Playback error</div><button class="ytp-ad-overlay-close-button">Close</button></div>
            <div role="dialog" id="consent"><ytd-ad-slot-renderer>Stale ad</ytd-ad-slot-renderer><ytd-consent-bump-v2-lightbox>Consent</ytd-consent-bump-v2-lightbox><button class="ytp-ad-overlay-close-button">Close</button></div>
            <div aria-modal="true" id="ariaModal"><ytd-ad-slot-renderer>Ad without dismiss control</ytd-ad-slot-renderer></div>
            <ytd-ad-interstitial-renderer id="interstitial"><ytd-ad-slot-renderer>Ad
                <button class="ytp-ad-overlay-close-button" disabled>Close later</button></ytd-ad-slot-renderer></ytd-ad-interstitial-renderer>
        `, () => {
            window.__ssyoutubeVideoAdBlocking.update();
            check(clicked === 0, 'No unsafe or unrelated clicks');
            check(!document.querySelector('[data-ssyoutube-patched-ad]'), 'Do not hide modal contents either');
            for (const id of ['disabled', 'ariaDisabled', 'generic', 'confirm', 'login', 'denied', 'error', 'consent']) {
                check(visible(id), `Leave ${id} visible`);
            }
            document.querySelector('#interstitial button').disabled = false;
            window.__ssyoutubeVideoAdBlocking.update();
            check(clicked === 1 && visible('interstitial'), 'A newly enabled close stays reachable');
        }, () => {
            document.querySelectorAll('button').forEach(button => { button.onclick = () => clicked++; });
        });
    });
    await run('ad close retries are rate-limited per button and stop with patched mode', () => {
        fixture('<div class="ytp-ad-overlay-container"><button class="ytp-ad-overlay-close-button">Close</button></div>');
        let now = 10000;
        let clicked = 0;
        document.querySelector('button').onclick = () => clicked++;
        new Function('location', 'Date', `return (${adSource})(true);`)(location, {now: () => now});
        try {
            check(clicked === 1, 'Initial close attempt');
            now += 4999;
            window.__ssyoutubeVideoAdBlocking.update();
            check(clicked === 1, 'No retry before five seconds');
            now++;
            window.__ssyoutubeVideoAdBlocking.update();
            check(clicked === 2, 'Retry at five seconds if the site has not dismissed the ad');
            injectAds(false);
            now += 5000;
            window.__ssyoutubeVideoAdBlocking.update();
            check(clicked === 2, 'Basic mode does not dismiss patched ad dialogs');
        } finally {
            window.__ssyoutubeVideoAdBlocking.stop();
        }
    });
    await run('patched UI work is bounded and owned markers are not rewritten every heartbeat', async () => {
        await adFixture(Array.from({length: 2049}, (_, index) =>
            `<ytd-merchandise-shelf-renderer id="shop${index}">Shop</ytd-merchandise-shelf-renderer>`).join(''),
        () => {
            check(!visible('shop2047') && visible('shop2048'), 'Process at most 2048 candidates per selector');
            const node = document.getElementById('shop0');
            const mutations = new OriginalObserver(() => {});
            mutations.observe(node, {attributes: true});
            window.__ssyoutubeVideoAdBlocking.update();
            check(mutations.takeRecords().length === 0, 'Do not repeatedly set an unchanged marker');
            mutations.disconnect();
        });
    });
    const messages = [];
    window.ssYouTubeBackgroundAudio = {postMessage: value => messages.push(value)};
    let backgroundNow = 1000000;
    const injectBackground = value =>
        new Function('location', 'enabled', 'Date', `return (${backgroundSource})(enabled);`)
            (location, value, {now: () => backgroundNow});
    let mediaState;
    function audioFixture() {
        fixture('<video id="audio"></video>');
        const video = document.getElementById('audio');
        mediaState = {paused: false, ended: false, muted: false, volume: 1, pauseCalls: 0};
        for (const key of ['paused', 'ended', 'muted', 'volume']) {
            Object.defineProperty(video, key, {get: () => mediaState[key], configurable: true});
        }
        video.pause = () => {
            mediaState.pauseCalls++;
            mediaState.paused = true;
            video.dispatchEvent(new Event('pause', {bubbles: true}));
        };
        video.play = () => { throw new Error('Background audio must not restart paused media'); };
        return video;
    }
    await run('background audio overrides and restores visibility without stacking hooks', () => {
        audioFixture();
        const originalHidden = () => true;
        const originalState = () => 'hidden';
        Object.defineProperty(document, 'hidden', {get: originalHidden, configurable: true});
        Object.defineProperty(document, 'visibilityState', {get: originalState, configurable: true});
        injectBackground(true);
        check(!document.hidden && document.visibilityState === 'visible', 'Expose foreground visibility');
        const hidden = Object.getOwnPropertyDescriptor(document, 'hidden').get;
        injectBackground(true);
        check(Object.getOwnPropertyDescriptor(document, 'hidden').get === hidden, 'Do not stack getters');
        let delivered = 0;
        const visibility = () => delivered++;
        document.addEventListener('visibilitychange', visibility);
        document.dispatchEvent(new Event('visibilitychange', {bubbles: true}));
        check(delivered === 0, 'Suppress hidden-page visibility events while enabled');
        injectBackground(false);
        check(Object.getOwnPropertyDescriptor(document, 'hidden').get === originalHidden, 'Restore getter');
        check(document.visibilityState === 'hidden', 'Restore visibility state');
        document.dispatchEvent(new Event('visibilitychange', {bubbles: true}));
        check(delivered === 1, 'Restore event delivery');
        document.removeEventListener('visibilitychange', visibility);
        delete document.hidden;
        delete document.visibilityState;
    });
    await run('protected background playback continues, but disabling it pauses without restarting', () => {
        audioFixture();
        injectBackground(true);
        const background = window.__ssyoutubeBackgroundAudio;
        background.setForeground(false, true);
        check(!mediaState.paused && mediaState.pauseCalls === 0, 'Keep protected audio playing');
        injectBackground(false);
        check(mediaState.paused && mediaState.pauseCalls === 1, 'Pause when disabled in background');
        injectBackground(true);
        background.setForeground(true, false);
        document.dispatchEvent(new Event('yt-navigate-finish'));
        check(mediaState.paused, 'Do not undo a pause when foregrounding or re-enabling');
    });
    await run('a missing foreground service cannot leave audio playing in the background', () => {
        audioFixture();
        injectBackground(true);
        window.__ssyoutubeBackgroundAudio.setForeground(false, false);
        check(mediaState.paused, 'Pause unprotected playback');
        window.__ssyoutubeBackgroundAudio.setForeground(true, false);
        check(mediaState.paused, 'Never automatically replay');
    });
    await run('late playback stays paused without protection or a message bridge', async () => {
        const video = audioFixture();
        injectBackground(true);
        const bridge = window.ssYouTubeBackgroundAudio;
        delete window.ssYouTubeBackgroundAudio;
        try {
            window.__ssyoutubeBackgroundAudio.setForeground(false, false);
            mediaState.paused = false;
            video.dispatchEvent(new Event('play', {bubbles: true}));
            check(mediaState.paused, 'Reject an unprotected autoplay event');
            mediaState.paused = false;
            await new Promise(resolve => setTimeout(resolve, 1100));
            check(mediaState.paused, 'Heartbeat also rejects a late start without a media event');
            check(window.__ssyoutubeBackgroundAudio.state() === 'paused', 'Expose state for polling');
        } finally {
            window.ssYouTubeBackgroundAudio = bridge;
            window.__ssyoutubeBackgroundAudio.setForeground(true, false);
        }
    });
    await run('background reports track media events, muted previews, removal and page identity', async () => {
        const video = audioFixture();
        injectBackground(true);
        video.dispatchEvent(new Event('play', {bubbles: true}));
        check(messages.at(-1) === `playing\n${location.href}`, 'Report active page URL');
        mediaState.muted = true;
        video.dispatchEvent(new Event('volumechange', {bubbles: true}));
        check(messages.at(-1).startsWith('paused\n'), 'Muted previews do not need a service');
        mediaState.muted = false;
        video.dispatchEvent(new Event('volumechange', {bubbles: true}));
        location.href = 'https://www.youtube.com/watch?v=next';
        document.dispatchEvent(new Event('yt-navigate-finish'));
        check(messages.at(-1) === `playing\n${location.href}`, 'Bind reports to current page');
        video.remove();
        await new Promise(resolve => setTimeout(resolve, 1100));
        check(messages.at(-1).startsWith('paused\n'), 'Detached media does not hold a service');
    });
    await run('notification pause and manual pause remain paused through background heartbeats', async () => {
        audioFixture();
        injectBackground(true);
        window.__ssyoutubeBackgroundAudio.setForeground(false, true);
        window.__ssyoutubeBackgroundAudio.pause();
        await new Promise(resolve => setTimeout(resolve, 1100));
        check(mediaState.paused && mediaState.pauseCalls === 1, 'No pause reversal or automatic play');
        window.__ssyoutubeBackgroundAudio.setForeground(true, false);
        injectBackground(false);
    });
    await run('YouTube activity is refreshed only for enabled, actively playing audio', async () => {
        audioFixture();
        injectBackground(true);
        window._lact = 10;
        backgroundNow += 300001;
        await new Promise(resolve => setTimeout(resolve, 1100));
        check(window._lact === backgroundNow, 'Refresh activity during enabled playback');
        const last = window._lact;
        mediaState.paused = true;
        backgroundNow += 300001;
        await new Promise(resolve => setTimeout(resolve, 1100));
        check(window._lact === last, 'Do not refresh activity for paused playback');
        mediaState.paused = false;
        injectBackground(false);
        backgroundNow += 300001;
        await new Promise(resolve => setTimeout(resolve, 1100));
        check(window._lact === last, 'Do not refresh activity when disabled');
        delete window._lact;
    });
    await run('background script rejects other origins, insecure ports and embedded documents', () => {
        audioFixture();
        Object.defineProperty(document, 'hidden', {get: () => true, configurable: true});
        for (const url of ['http://m.youtube.com/', 'https://accounts.google.com/',
            'https://youtube.com.evil.example/', 'https://www.youtube.com:444/',
            'https://user@m.youtube.com/']) {
            location.href = url;
            injectBackground(true);
            check(document.hidden, `Do not override ${url}`);
        }
        for (const host of ['youtube.com', 'm.youtube.com', 'www.youtube.com', 'music.youtube.com']) {
            location.href = `https://${host}/`;
            injectBackground(true);
            check(!document.hidden, `Enable ${host}`);
            injectBackground(false);
        }
        const frame = document.createElement('iframe');
        document.body.appendChild(frame);
        frame.contentWindow.Function('location', 'enabled', `return (${backgroundSource})(enabled);`)
            (location, true);
        check(!frame.contentWindow.__ssyoutubeBackgroundAudio, 'No subframe hooks');
        frame.remove();
        delete document.hidden;
    });
    await run('background audio and page blocking do not change network or media play APIs', () => {
        fixture(content, defaults);
        injectBackground(true);
        check(!visible('endOverlay'), 'End-video filter still applies');
        update(off);
        check(visible('endOverlay') && !document.hidden, 'Page filters do not disable background audio');
        check(window.fetch === original.fetch && JSON.parse === original.parse
            && HTMLMediaElement.prototype.play === original.play, 'Do not patch playback or network APIs');
        injectBackground(false);
    });
    await run('unsupported visibility overrides explicitly report failure instead of breaking the page', () => {
        Object.defineProperty(document, 'hidden', {value: true, configurable: false});
        const before = messages.length;
        injectBackground(true);
        check(messages.slice(before).some(value => value.startsWith('unavailable\n')), 'Report failure');
        check(window.__ssyoutubeBackgroundAudio.state() === 'unavailable', 'Polling sees failure too');
        check(document.hidden, 'Do not overwrite a nonconfigurable page property');
        injectBackground(false);
    });
    update(off);
    return results;
}

test('page-element filtering passes real browser behavior fixtures', t => {
    const browser = [process.env.CHROME_BIN, 'google-chrome', 'chromium', 'chromium-browser']
        .filter(Boolean).find(candidate => spawnSync(candidate, ['--version']).status === 0);
    if (!browser) {
        t.skip('Chrome/Chromium is required; set CHROME_BIN to run real DOM fixtures');
        return;
    }
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'ssyoutube-elements-'));
    try {
        const html = path.join(directory, 'fixtures.html');
        fs.writeFileSync(html, `<!doctype html><html><head><meta charset="utf-8">
            <meta http-equiv="Content-Security-Policy" content="img-src 'none'"></head><body>
            <script>
            (${browserFixtures})(${JSON.stringify(source).replace(/</g, '\\u003c')},
                ${JSON.stringify(adSource).replace(/</g, '\\u003c')},
                ${JSON.stringify(backgroundSource).replace(/</g, '\\u003c')})
                .then(results => {
                    document.body.innerHTML = '<pre id="results"></pre>';
                    document.getElementById('results').textContent = JSON.stringify(results);
                }).catch(error => {
                    document.body.innerHTML = '<pre id="results"></pre>';
                    document.getElementById('results').textContent =
                        JSON.stringify([{name: 'fixture runner', passed: false, error: String(error)}]);
                });
            </script></body></html>`);
        const result = spawnSync(browser, [
            '--headless', '--no-sandbox', '--disable-gpu', '--disable-background-networking',
            '--no-first-run', '--no-default-browser-check', `--user-data-dir=${directory}/profile`,
            '--virtual-time-budget=15000', '--dump-dom', pathToFileURL(html).href
        ], {encoding: 'utf8', timeout: 30000, maxBuffer: 4 * 1024 * 1024});
        assert.equal(result.status, 0, result.error?.message || result.stderr);
        const report = result.stdout.match(/<pre id="results">([^]*?)<\/pre>/);
        assert.ok(report, `Browser fixtures did not finish: ${result.stdout}\n${result.stderr}`);
        const results = JSON.parse(report[1].replace(/&lt;/g, '<').replace(/&gt;/g, '>')
            .replace(/&amp;/g, '&'));
        assert.equal(results.length, 33);
        for (const result of results) {
            assert.equal(result.passed, true, `${result.name}: ${result.error}`);
            t.diagnostic(result.name);
        }
    } finally {
        fs.rmSync(directory, {recursive: true, force: true});
    }
});
