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

// Run the shipped function against Chromium's real DOM, selectors and MutationObserver.
async function browserFixtures(source, adSource) {
    const results = [];
    const location = new URL('https://m.youtube.com/');
    const inject = new Function('location', 'options', `return (${source})(options);`);
    const keys = ['shorts', 'playables', 'recommended', 'distracting', 'thumbnails',
        'autodubbed', 'members_only'];
    const off = Object.fromEntries(keys.map(key => [key, false]));
    const defaults = {...off, shorts: true, playables: true, distracting: true,
        autodubbed: true, members_only: true};
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
        <ytd-thumbnail id="thumbnail" style="display:inline-block">Image</ytd-thumbnail>
        <ytm-rich-grid-renderer id="home">Home feed</ytm-rich-grid-renderer>
        <img id="avatar" alt="Channel avatar">
        <video id="player"></video>
        <a id="search" href="/results?search_query=music">Search</a>`;

    await run('requested defaults hide five categories but keep recommendations and thumbnails', () => {
        fixture(content, defaults);
        for (const id of ['short', 'playable', 'comments', 'info', 'dub', 'member']) {
            check(!visible(id), `${id} must be hidden by default`);
        }
        for (const id of ['related', 'end', 'thumbnail', 'home', 'avatar', 'player', 'search']) {
            check(visible(id), `${id} must remain visible`);
        }
    });
    await run('each option works alone and disabling restores the same DOM nodes and styles', () => {
        const targets = {shorts: 'short', playables: 'playable', recommended: 'related',
            distracting: 'comments', thumbnails: 'thumbnail', autodubbed: 'dub', members_only: 'member'};
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
        for (const id of ['related', 'end', 'endGrid', 'nudge']) check(!visible(id), `Hide ${id}`);
        check(visible('home') && visible('playlist'), 'Preserve home and playlist controls');
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
        for (const url of ['https://accounts.google.com/', 'https://youtube.com.evil.example/',
            'http://m.youtube.com/', 'https://www.youtube.com:444/', 'https://studio.youtube.com/']) {
            location.href = url;
            update(defaults);
            check(visible('short') && visible('comments'), `Reject ${url}`);
        }
        for (const host of ['youtube.com', 'www.youtube.com', 'm.youtube.com', 'music.youtube.com']) {
            location.href = `https://${host}/`;
            update(defaults);
            check(!visible('short'), `Accept ${host}`);
            update(off);
        }
        const frame = document.createElement('iframe');
        document.body.appendChild(frame);
        frame.contentWindow.Function('location', 'options', `return (${source})(options);`)
            (location, defaults);
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
        fixture(content, defaults);
        const nativeStyle = document.createElement('style');
        nativeStyle.id = 'ssyoutube-native-player';
        nativeStyle.textContent = 'video{display:none!important;}';
        document.head.appendChild(nativeStyle);
        new Function('location', adSource)(location);
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
        fs.writeFileSync(html, `<!doctype html><html><head><meta charset="utf-8"></head><body>
            <script>
            (${browserFixtures})(${JSON.stringify(source).replace(/</g, '\\u003c')},
                ${JSON.stringify(adSource).replace(/</g, '\\u003c')})
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
            '--virtual-time-budget=10000', '--dump-dom', pathToFileURL(html).href
        ], {encoding: 'utf8', timeout: 30000, maxBuffer: 4 * 1024 * 1024});
        assert.equal(result.status, 0, result.error?.message || result.stderr);
        const report = result.stdout.match(/<pre id="results">([^]*?)<\/pre>/);
        assert.ok(report, `Browser fixtures did not finish: ${result.stdout}\n${result.stderr}`);
        const results = JSON.parse(report[1].replace(/&lt;/g, '<').replace(/&gt;/g, '>')
            .replace(/&amp;/g, '&'));
        assert.equal(results.length, 14);
        for (const result of results) {
            assert.equal(result.passed, true, `${result.name}: ${result.error}`);
            t.diagnostic(result.name);
        }
    } finally {
        fs.rmSync(directory, {recursive: true, force: true});
    }
});
