const assert = require('node:assert/strict');
const test = require('node:test');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const script = fs.readFileSync(
    path.join(__dirname, '../../main/res/raw/youtube_video_adblock.js'), 'utf8');

function page(url = 'https://m.youtube.com/watch?v=content', enabled = true) {
    const intervals = new Map();
    const listeners = new Map();
    const players = [];
    let nextId = 0;
    const context = vm.createContext({
        URL, location: new URL(url),
        setInterval(fn) { const id = ++nextId; intervals.set(id, fn); return id; },
        clearInterval(id) { intervals.delete(id); },
        addEventListener(name, fn) { listeners.set(name, fn); },
        removeEventListener(name, fn) { if (listeners.get(name) === fn) listeners.delete(name); },
        getComputedStyle(button) { return button.style; },
        document: {
            addEventListener(name, fn) { listeners.set(name, fn); },
            removeEventListener(name, fn) { if (listeners.get(name) === fn) listeners.delete(name); },
            querySelectorAll(selector) {
                assert.match(selector, /\.ad-showing/);
                return players.filter(player => player.ad);
            }
        }
    });
    function run(code) { return vm.runInContext(code, context, { timeout: 1000 }); }
    run(`
        window = globalThis;
        class Response {
            constructor(value, url = 'https://m.youtube.com/youtubei/v1/player') {
                this.value = value;
                this.url = url;
                this.status = 200;
                this.headers = { marker: 'untouched' };
            }
            json() { return this.failure ? Promise.reject(this.failure) : Promise.resolve(this.value); }
        }
        class XMLHttpRequest {
            constructor(value, url = 'https://m.youtube.com/youtubei/v1/player') {
                this.value = value;
                this.responseURL = url;
                this.responseType = 'json';
            }
            get response() { return this.value; }
        }
        window.Response = Response;
        window.XMLHttpRequest = XMLHttpRequest;
        window.fetch = function () { throw new Error('Must not replay requests'); };
        originals = {
            parse: JSON.parse, json: Response.prototype.json,
            xhr: Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, 'response').get,
            fetch: window.fetch
        };
        function fixture() {
            return {
                videoDetails: {videoId: 'content', isLiveContent: false},
                playabilityStatus: {status: 'OK'},
                streamingData: {adaptiveFormats: [{url: 'https://media.googlevideo.com/videoplayback'}]},
                captions: {playerCaptionsTracklistRenderer: {captionTracks: []}},
                playbackTracking: {videostatsWatchtimeUrl: {baseUrl: '/api/stats/watchtime'}},
                playerAds: [{ad: 'preroll'}], adPlacements: [{ad: 'midroll'}],
                adSlots: [{ad: 'postroll'}], adBreakHeartbeatParams: 'ad-only'
            };
        }
    `);
    if (enabled) vm.runInContext(script, context, { timeout: 1000 });
    return {run, context, intervals, listeners, players,
        inject() { vm.runInContext(script, context, { timeout: 1000 }); }};
}

function assertFiltered(p, expression) {
    assert.equal(p.run(`${expression}.playerAds`), undefined);
    assert.equal(p.run(`${expression}.adPlacements`), undefined);
    assert.equal(p.run(`${expression}.adSlots`), undefined);
    assert.equal(p.run(`${expression}.adBreakHeartbeatParams`), undefined);
}

test('initial inline player response removes pre-, mid- and post-roll scheduling only', () => {
    const p = page();
    p.run('content = fixture(); expected = JSON.stringify(content.streamingData); ytInitialPlayerResponse = content');
    assertFiltered(p, 'content');
    assert.equal(p.run('JSON.stringify(content.streamingData) === expected'), true);
    assert.equal(p.run('content.videoDetails.videoId'), 'content');
    assert.equal(p.run('content.playabilityStatus.status'), 'OK');
    assert.equal(p.run('content.playbackTracking.videostatsWatchtimeUrl.baseUrl'), '/api/stats/watchtime');
    assert.equal(p.run('content.captions.playerCaptionsTracklistRenderer.captionTracks.length'), 0);
});

test('responses already assigned before late injection are filtered', () => {
    const p = page(undefined, false);
    p.run('ytInitialPlayerResponse = fixture(); ytplayer = {config: {args: {player_response: JSON.stringify(fixture())}}}');
    p.inject();
    assertFiltered(p, 'ytInitialPlayerResponse');
    assert.equal(p.run('JSON.parse(ytplayer.config.args.player_response).adPlacements'), undefined);
});

test('legacy player strings and raw responses survive incremental property assignments', () => {
    const p = page();
    p.run(`ytplayer = {}; ytplayer.config = {}; ytplayer.config.args = {};
        ytplayer.config.args.player_response = JSON.stringify(fixture());
        ytplayer.config.args.raw_player_response = fixture();`);
    assert.equal(p.run('originals.parse(ytplayer.config.args.player_response).playerAds'), undefined);
    assertFiltered(p, 'ytplayer.config.args.raw_player_response');
    p.run(`ytplayer.config = {args: {player_response: '{"invalid":'}}`);
    assert.equal(p.run('ytplayer.config.args.player_response'), '{"invalid":');
});

test('SPA and playlist JSON response envelopes are cleaned', () => {
    const p = page();
    for (const expression of ['fixture()', '({playerResponse: fixture()})',
        '[{response: {playerResponse: fixture()}}]']) {
        p.run(`parsed = JSON.parse(JSON.stringify(${expression}))`);
        assert.equal(p.run('JSON.stringify(parsed).includes(\'"adPlacements"\')'), false);
        assert.equal(p.run('JSON.stringify(parsed).includes(\'"videoId":"content"\')'), true);
    }
    p.run('ytInitialPlayerResponse = fixture()');
    p.listeners.get('yt-navigate-finish')();
    assertFiltered(p, 'ytInitialPlayerResponse');
});

test('live streams retain manifests, timeline and playability status', () => {
    const p = page();
    p.run(`live = fixture(); live.videoDetails.isLiveContent = true;
        live.streamingData.hlsManifestUrl = 'https://manifest.googlevideo.com/live.m3u8';
        live.microformat = {playerMicroformatRenderer: {liveBroadcastDetails: {isLiveNow: true}}};
        ytInitialPlayerResponse = live;`);
    assertFiltered(p, 'live');
    assert.equal(p.run('live.streamingData.hlsManifestUrl'), 'https://manifest.googlevideo.com/live.m3u8');
    assert.equal(p.run('live.microformat.playerMicroformatRenderer.liveBroadcastDetails.isLiveNow'), true);
});

test('Shorts ad entries are removed without removing organic Shorts or changing pagination', () => {
    const p = page('https://m.youtube.com/shorts/content');
    p.run(`ytInitialData = {continuation: 'next', entries: [
        {command: {reelWatchEndpoint: {videoId: 'organic'}}},
        {command: {reelWatchEndpoint: {adClientParams: {isAd: true}}}},
        {command: {reelWatchEndpoint: {adClientParams: {isAd: false}, videoId: 'more'}}}
    ]};`);
    assert.equal(p.run('ytInitialData.entries.length'), 2);
    assert.equal(p.run('ytInitialData.entries[1].command.reelWatchEndpoint.videoId'), 'more');
    assert.equal(p.run('ytInitialData.continuation'), 'next');
});

test('unrelated JSON, revivers, syntax errors and response metadata stay intact', async () => {
    const p = page();
    assert.equal(p.run('JSON.parse(\'{"adSlots":[1],"count":2}\', (k,v) => k === "count" ? v+1 : v).count'), 3);
    assert.equal(p.run('JSON.parse(\'{"adSlots":[1]}\').adSlots.length'), 1);
    assert.throws(() => p.run('JSON.parse("{invalid")'), /SyntaxError/);
    p.run('response = new Response(fixture()); status = response.status; headers = response.headers;');
    await p.run('response.json().then(value => { parsed = value; })');
    assertFiltered(p, 'parsed');
    assert.equal(p.run('response.status === status && response.headers === headers'), true);
    assert.equal(p.run('window.fetch === originals.fetch'), true);
    assert.equal(p.run('response.value.streamingData.adaptiveFormats.length'), 1);
});

test('fetch JSON wrapper preserves original rejection', async () => {
    const p = page();
    p.run('response = new Response(null); response.failure = new Error("network");');
    assert.equal(await p.run('response.json().catch(error => error === response.failure)'), true);
});

test('XHR JSON is filtered without replacing the request or response object', () => {
    const p = page();
    p.run('xhr = new XMLHttpRequest(fixture()); received = xhr.response;');
    assertFiltered(p, 'received');
    assert.equal(p.run('received === xhr.value'), true);
    p.run('xhr.responseType = "text"; xhr.value = "not JSON";');
    assert.equal(p.run('xhr.response'), 'not JSON');
});

test('response hooks reject sign-in, other endpoints, insecure ports and host lookalikes', async () => {
    const p = page();
    for (const url of ['https://accounts.google.com/youtubei/v1/player',
        'https://youtube.com.evil.test/youtubei/v1/player',
        'http://m.youtube.com/youtubei/v1/player',
        'https://m.youtube.com:444/youtubei/v1/player',
        'https://www.youtube.com/account', 'https://www.youtube.com/youtubei/v1/browse']) {
        p.run(`response = new Response(fixture(), ${JSON.stringify(url)});
            xhr = new XMLHttpRequest(fixture(), ${JSON.stringify(url)});`);
        assert.equal(await p.run('response.json().then(value => value.playerAds.length)'), 1);
        assert.equal(p.run('xhr.response.playerAds.length'), 1);
    }
});

test('supported player and reel endpoints work on all supported origins', async () => {
    for (const host of ['youtube.com', 'www.youtube.com', 'm.youtube.com', 'music.youtube.com']) {
        const p = page(`https://${host}/`);
        for (const endpoint of ['player', 'next', 'reel/reel_item_watch', 'reel/reel_watch_sequence']) {
            assert.equal(await p.run(`new Response(fixture(),
                'https://${host}/youtubei/v1/${endpoint}?prettyPrint=false').json()
                .then(value => value.playerAds)`), undefined);
        }
    }
});

test('script is inert on untrusted documents and without injection', () => {
    for (const url of ['http://m.youtube.com/', 'https://m.youtube.com:444/',
        'https://accounts.youtube.com/', 'https://accounts.google.com/', 'https://youtube.com.evil.test/']) {
        const p = page(url);
        assert.equal(p.run('JSON.parse === originals.parse'), true);
        assert.equal(p.run('Response.prototype.json === originals.json'), true);
        assert.equal(p.intervals.size, 0);
    }
    const p = page(undefined, false);
    assert.equal(p.run('JSON.parse(JSON.stringify(fixture())).adPlacements.length'), 1);
    assert.equal(p.intervals.size, 0);
});

test('reinjection does not stack wrappers or timers', () => {
    const p = page();
    p.run('installedParse = JSON.parse; installedJson = Response.prototype.json');
    p.inject();
    p.inject();
    assert.equal(p.run('JSON.parse === installedParse && Response.prototype.json === installedJson'), true);
    assert.equal(p.intervals.size, 1);
});

test('turning adblocking off restores hooks and stops pending response filtering', async () => {
    const p = page();
    p.run(`pending = new Response(fixture()).json();
        ytInitialPlayerResponse = fixture();
        __ssyoutubeVideoAdBlocking.stop();`);
    assert.equal(await p.run('pending.then(value => value.adPlacements.length)'), 1);
    assert.equal(p.run('JSON.parse === originals.parse && Response.prototype.json === originals.json'), true);
    assert.equal(p.run('Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, "response").get === originals.xhr'), true);
    assert.equal(p.intervals.size, 0);
    assert.equal(p.listeners.size, 0);
    p.run('ytInitialPlayerResponse = fixture()');
    assert.equal(p.run('ytInitialPlayerResponse.adPlacements.length'), 1);
    p.inject();
    assertFiltered(p, 'ytInitialPlayerResponse');
});

test('nonconfigurable globals, frozen responses and cyclic envelopes do not break playback', () => {
    const p = page(undefined, false);
    p.run(`Object.defineProperty(window, 'ytInitialPlayerResponse', {value: fixture(), configurable: false});
        ytplayer = Object.freeze({config: Object.freeze({})});`);
    p.inject();
    p.run(`frozen = Object.freeze(fixture()); response = new XMLHttpRequest(frozen);
        cycle = {playerResponse: fixture()}; cycle.response = cycle;
        xhr = new XMLHttpRequest(cycle); result = xhr.response;`);
    assert.equal(p.run('response.response === frozen'), true);
    assertFiltered(p, 'result.playerResponse');
});

function skipButton(overrides = {}) {
    return Object.assign({
        clicks: 0, disabled: false, ariaDisabled: 'false',
        style: {display: 'block', visibility: 'visible', opacity: '1'},
        getAttribute() { return this.ariaDisabled; },
        getClientRects() { return [1]; },
        click() { this.clicks++; }
    }, overrides);
}

test('only visible, enabled skip buttons in an explicitly ad-playing player are clicked', () => {
    const p = page();
    const button = skipButton();
    const player = {ad: false, querySelectorAll() { return [button]; }};
    p.players.push(player);
    const update = p.intervals.values().next().value;
    update();
    assert.equal(button.clicks, 0);
    player.ad = true;
    button.disabled = true;
    update();
    assert.equal(button.clicks, 0);
    button.disabled = false;
    button.ariaDisabled = 'true';
    update();
    assert.equal(button.clicks, 0);
    button.ariaDisabled = 'false';
    button.style.visibility = 'hidden';
    update();
    assert.equal(button.clicks, 0);
    button.style.visibility = 'visible';
    update();
    assert.equal(button.clicks, 1);
    p.run('__ssyoutubeVideoAdBlocking.stop()');
    update();
    assert.equal(button.clicks, 1);
});

test('fallback never seeks, changes rate, mutes, or plays content when ad UI is stale', () => {
    assert.doesNotMatch(script, /currentTime\s*=|playbackRate\s*=|muted\s*=|\.play\(/);
    const p = page();
    p.players.push({ad: true, querySelectorAll() { return []; }});
    p.intervals.values().next().value();
    assert.equal(p.run('typeof __ssyoutubeVideoAdBlocking.update'), 'function');
});
