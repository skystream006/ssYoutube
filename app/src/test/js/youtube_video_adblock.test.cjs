const assert = require('node:assert/strict');
const test = require('node:test');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const source = fs.readFileSync(
    path.join(__dirname, '../../main/res/raw/youtube_video_adblock.js'), 'utf8');
const script = `(${source})(false);`;

function page(url = 'https://m.youtube.com/watch?v=content', enabled = true, patched = false) {
    const intervals = new Map();
    const listeners = new Map();
    const players = [];
    const warnings = [];
    let nextId = 0;
    const context = vm.createContext({
        URL, location: new URL(url),
        console: {warn(message) { warnings.push(message); }},
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
            text() { return this.failure ? Promise.reject(this.failure) : Promise.resolve(this.value); }
        }
        class XMLHttpRequest {
            constructor(value, url = 'https://m.youtube.com/youtubei/v1/player') {
                this.value = value;
                this.responseURL = url;
                this.responseType = 'json';
                this.readyState = 4;
            }
            get response() { return this.value; }
            get responseText() {
                if (this.responseType !== '' && this.responseType !== 'text') {
                    throw new Error('InvalidStateError');
                }
                return this.value;
            }
        }
        window.Response = Response;
        window.XMLHttpRequest = XMLHttpRequest;
        window.fetch = function () { throw new Error('Must not replay requests'); };
        originals = {
            parse: JSON.parse, json: Response.prototype.json,
            text: Response.prototype.text,
            xhr: Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, 'response').get,
            xhrText: Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, 'responseText').get,
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
    function inject(mode = patched) {
        vm.runInContext(`(${source})(${mode});`, context, { timeout: 1000 });
    }
    if (enabled) inject();
    return {run, context, intervals, listeners, players, warnings, inject};
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

test('patched mode filters fetch text before a previously captured parser consumes it', async () => {
    for (const patched of [false, true]) {
        const p = page(undefined, true, patched);
        p.run('body = JSON.stringify(fixture()); response = new Response(body);');
        await p.run('response.text().then(text => { parsed = originals.parse(text); })');
        assert.equal(p.run('parsed.adPlacements === undefined'), patched);
        assert.equal(p.run('parsed.videoDetails.videoId'), 'content');
        assert.equal(p.run('parsed.streamingData.adaptiveFormats.length'), 1);
        assert.equal(p.run('window.fetch === originals.fetch'), true);
    }
});

test('basic and patched filtering work alone, together, or with both off', async () => {
    for (const basic of [false, true]) {
        for (const patched of [false, true]) {
            const p = page(undefined, basic || patched, patched);
            p.run('ytInitialPlayerResponse = fixture();');
            assert.equal(p.run('ytInitialPlayerResponse.adPlacements === undefined'), basic || patched);
            assert.equal(p.intervals.size, basic || patched ? 1 : 0);
            assert.equal(await p.run(`new Response(JSON.stringify(fixture())).text()
                .then(text => originals.parse(text).adPlacements === undefined)`), patched);
        }
    }
});

test('patched XHR text and response agree, preserve partial reads and handle reuse', () => {
    const p = page(undefined, true, true);
    p.run(`xhr = new XMLHttpRequest(JSON.stringify(fixture())); xhr.responseType = '';
        xhr.readyState = 3;`);
    assert.equal(p.run('xhr.responseText === xhr.value && xhr.response === xhr.value'), true);
    p.run('xhr.readyState = 4; received = originals.parse(xhr.responseText);');
    assertFiltered(p, 'received');
    assert.equal(p.run('xhr.responseText === xhr.response'), true);
    p.run(`xhr.responseURL = 'https://accounts.google.com/youtubei/v1/player';`);
    assert.equal(p.run('xhr.responseText === xhr.value'), true);
    p.run(`xhr.responseURL = 'https://m.youtube.com/youtubei/v1/player';
        next = fixture(); next.videoDetails.videoId = 'next'; xhr.value = JSON.stringify(next);
        xhr.responseType = 'text'; received = originals.parse(xhr.response);`);
    assertFiltered(p, 'received');
    assert.equal(p.run('received.videoDetails.videoId'), 'next');
    p.run('xhr.responseType = "json";');
    assert.throws(() => p.run('xhr.responseText'), /InvalidStateError/);
    p.run('xhr.responseType = "arraybuffer";');
    assert.equal(p.run('xhr.response === xhr.value'), true);
});

test('patched text preserves XSSI, malformed bodies, untouched formatting, and size bounds', async () => {
    const p = page(undefined, true, true);
    const prefix = ")]}'\n";
    p.run(`body = ${JSON.stringify(prefix)} + JSON.stringify(fixture());`);
    const filtered = await p.run('new Response(body).text()');
    assert.ok(filtered.startsWith(prefix));
    assert.equal(JSON.parse(filtered.slice(prefix.length)).adSlots, undefined);
    for (const text of [' { "value" : 42 }\n', '{"adSlots": [1]}', '{"adSlots":invalid',
        '{"videoDetails":{},"adSlots":[1],"padding":"' + 'x'.repeat(5000000) + '"}']) {
        p.context.body = text;
        assert.equal(await p.run('new Response(body).text()'), text);
    }
    assert.deepEqual(p.warnings, ['ssYouTube adblocking: Non-JSON response left unchanged']);
});

test('text limit accepts exactly five million characters and rejects larger bodies unchanged', async () => {
    const p = page(undefined, true, true);
    const response = {videoDetails: {}, adSlots: [{}], padding: ''};
    response.padding = 'x'.repeat(5000000 - JSON.stringify(response).length);
    p.context.body = JSON.stringify(response);
    assert.equal(p.context.body.length, 5000000);
    assert.equal(JSON.parse(await p.run('new Response(body).text()')).adSlots, undefined);
    p.context.body += ' ';
    assert.equal(await p.run('new Response(body).text()'), p.context.body);
});

test('patched text hooks keep rejections and never patch non-player/feed endpoints', async () => {
    const p = page(undefined, true, true);
    p.run('response = new Response(""); response.failure = new Error("network");');
    assert.equal(await p.run('response.text().catch(error => error === response.failure)'), true);
    for (const url of ['https://accounts.google.com/youtubei/v1/player',
        'http://m.youtube.com/youtubei/v1/player', 'https://www.youtube.com:444/youtubei/v1/player',
        'https://youtube.com.evil.test/youtubei/v1/player', 'https://www.youtube.com/api/stats/watchtime',
        'https://www.youtube.com/youtubei/v1/account/account_menu',
        'https://www.youtube.com/youtubei/v1/player/extra',
        'https://user:password@www.youtube.com/youtubei/v1/player']) {
        p.context.endpoint = url;
        p.run(`body = JSON.stringify(fixture()); response = new Response(body, endpoint);
            xhr = new XMLHttpRequest(body, endpoint); xhr.responseType = 'text';`);
        assert.equal(await p.run('response.text()'), p.run('body'));
        assert.equal(p.run('xhr.response === body && xhr.responseText === body'), true);
    }
});

function installFeedFixture(p) {
    p.run(`
        function feed() {
            return {responseContext: {}, contents: {twoColumnBrowseResultsRenderer: {tabs: [
                {tabRenderer: {content: {richGridRenderer: {contents: [
                    {richItemRenderer: {content: {videoRenderer: {videoId: 'organic',
                        title: {simpleText: 'promotedVideoRenderer ad tutorial'}}}}},
                    {richItemRenderer: {content: {adSlotRenderer: {slot: 'paid'}}}},
                    {richSectionRenderer: {content: {inFeedAdLayoutRenderer: {slot: 'paid'}}}},
                    {continuationItemRenderer: {continuationEndpoint: {token: 'next'}}},
                    {richItemRenderer: {content: {reelItemRenderer: {videoId: 'short'}}}}
                ]}}}}
            ]}}};
        }
        function items(value) {
            return value.contents.twoColumnBrowseResultsRenderer.tabs[0]
                .tabRenderer.content.richGridRenderer.contents;
        }
    `);
}

test('patched inline feed removes explicit ad components, preserving organic cards and pagination', () => {
    for (const host of ['m', 'www', 'music']) {
        const p = page(`https://${host}.youtube.com/`, true, true);
        installFeedFixture(p);
        p.run('ytInitialData = feed(); result = items(ytInitialData);');
        assert.equal(p.run('result.length'), 3);
        assert.equal(p.run('result[0].richItemRenderer.content.videoRenderer.videoId'), 'organic');
        assert.equal(p.run('result[1].continuationItemRenderer.continuationEndpoint.token'), 'next');
        assert.equal(p.run('result[2].richItemRenderer.content.reelItemRenderer.videoId'), 'short');
    }
});

test('patched browse/search responses and continuation actions filter before rendering', async () => {
    const p = page(undefined, true, true);
    installFeedFixture(p);
    for (const endpoint of ['browse', 'search', 'next']) {
        p.context.endpoint = `https://m.youtube.com/youtubei/v1/${endpoint}?key=fixture`;
        await p.run('new Response(feed(), endpoint).json().then(value => { received = value; });');
        assert.equal(p.run('items(received).length'), 3);
        await p.run(`new Response(JSON.stringify(feed()), endpoint).text()
            .then(text => { received = originals.parse(text); });`);
        assert.equal(p.run('items(received).length'), 3);
        p.run('xhr = new XMLHttpRequest(feed(), endpoint); received = xhr.response;');
        assert.equal(p.run('items(received).length'), 3);
    }
    for (const action of ['onResponseReceivedActions', 'onResponseReceivedEndpoints',
        'onResponseReceivedCommands']) {
        p.context.action = action;
        p.run(`ytInitialData = {[action]: [{appendContinuationItemsAction: {
            continuationItems: [{promotedVideoRenderer: {}}, {videoRenderer: {videoId: 'next'}},
                {continuationItemRenderer: {continuationEndpoint: {token: 'more'}}}]
        }}]}; result = ytInitialData[action][0].appendContinuationItemsAction.continuationItems;`);
        assert.equal(p.run('result.length'), 2);
        assert.equal(p.run('result[0].videoRenderer.videoId'), 'next');
        assert.equal(p.run('result[1].continuationItemRenderer.continuationEndpoint.token'), 'more');
    }
});

test('basic mode does not opt into component or text patches', async () => {
    const p = page();
    installFeedFixture(p);
    p.run('ytInitialData = feed();');
    assert.equal(p.run('items(ytInitialData).length'), 5);
    assert.equal(p.run('Response.prototype.text === originals.text'), true);
    assert.equal(p.run(`Object.getOwnPropertyDescriptor(
        XMLHttpRequest.prototype, 'responseText').get === originals.xhrText`), true);
    assert.equal(await p.run(`new Response(feed(), 'https://m.youtube.com/youtubei/v1/browse')
        .json().then(value => items(value).length)`), 5);
});

test('patched JSON parser only traverses recognized response and rendering envelopes', () => {
    const p = page(undefined, true, true);
    installFeedFixture(p);
    p.run(`received = JSON.parse(JSON.stringify(feed()));
        unrelated = JSON.parse('{"contents":[{"adSlotRenderer":{}}]}');
        account = {contents: [{adSlotRenderer: {}}]};
        ytInitialData = {responseContext: {}, account: account, comments: account,
            streamingData: account, captions: account, playbackTracking: account};`);
    assert.equal(p.run('items(received).length'), 3);
    assert.equal(p.run('unrelated.contents.length'), 1);
    assert.equal(p.run('account.contents.length'), 1);
    assert.equal(p.run('ytInitialData.account === account && ytInitialData.comments === account'), true);
    assert.throws(() => p.run('JSON.parse("{invalid")'), /SyntaxError/);
    assert.equal(p.run('JSON.parse(\'{"x":1}\', (k,v) => k === "x" ? 2 : v).x'), 2);
});

test('ambiguous mixed renderer records are preserved instead of dropping organic content', () => {
    const p = page(undefined, true, true);
    p.run(`mixed = {adSlotRenderer: {}, videoRenderer: {videoId: 'organic'}};
        ytInitialData = {contents: [mixed, {adSlotRenderer: {}, trackingParams: 'ad'},
            {richItemRenderer: {content: mixed}}]};`);
    assert.equal(p.run('ytInitialData.contents.length'), 2);
    assert.equal(p.run('ytInitialData.contents[0].videoRenderer.videoId'), 'organic');
    assert.equal(p.run('ytInitialData.contents[1].richItemRenderer.content.videoRenderer.videoId'), 'organic');
});

test('patched mode preserves denied playback, live media, subtitles, playlist and history data', async () => {
    const p = page(undefined, true, true);
    p.run(`value = fixture(); value.playabilityStatus = {status: 'LOGIN_REQUIRED', reason: 'denied'};
        value.videoDetails.isLiveContent = true; value.streamingData.hlsManifestUrl = 'https://manifest.googlevideo.com/live';
        value.playlist = {index: 4, videoIds: ['a', 'b']};
        expected = JSON.stringify([value.streamingData, value.playabilityStatus,
            value.captions, value.playbackTracking, value.playlist, value.videoDetails]);`);
    await p.run('new Response(JSON.stringify(value)).text().then(text => { received = originals.parse(text); });');
    assertFiltered(p, 'received');
    assert.equal(p.run(`JSON.stringify([received.streamingData, received.playabilityStatus,
        received.captions, received.playbackTracking, received.playlist, received.videoDetails]) === expected`), true);
});

test('patched mode still filters marked Shorts entries but keeps organic Shorts', async () => {
    const p = page('https://m.youtube.com/shorts/content', true, true);
    p.run(`value = {entries: [
        {command: {reelWatchEndpoint: {videoId: 'short'}}},
        {command: {reelWatchEndpoint: {adClientParams: {isAd: true}}}},
        {command: {reelWatchEndpoint: {videoId: 'next', adClientParams: {isAd: false}}}}
    ], continuation: 'more'};`);
    await p.run(`new Response(JSON.stringify(value),
        'https://m.youtube.com/youtubei/v1/reel/reel_watch_sequence').text()
        .then(text => { received = originals.parse(text); });`);
    assert.equal(p.run('received.entries.length'), 2);
    assert.equal(p.run('received.entries[1].command.reelWatchEndpoint.videoId'), 'next');
    assert.equal(p.run('received.continuation'), 'more');
});

test('switching implementation restores then reinstalls exactly one set of hooks', async () => {
    const p = page();
    p.inject(true);
    p.run(`installed = Response.prototype.text; pending = new Response(JSON.stringify(fixture())).text();`);
    p.inject(true);
    assert.equal(p.run('Response.prototype.text === installed'), true);
    assert.equal(p.intervals.size, 1);
    p.inject(false);
    assert.equal(await p.run('pending.then(text => originals.parse(text).adSlots.length)'), 1);
    assert.equal(p.run('Response.prototype.text === originals.text'), true);
    assert.equal(p.intervals.size, 1);
    p.inject(true);
    p.run(`xhr = new XMLHttpRequest(JSON.stringify(fixture())); xhr.responseType = 'text';
        first = xhr.responseText; __ssyoutubeVideoAdBlocking.stop();`);
    assert.equal(p.run('xhr.responseText === xhr.value'), true);
    assert.equal(p.run('JSON.parse === originals.parse && Response.prototype.json === originals.json'), true);
    assert.equal(p.run('Response.prototype.text === originals.text'), true);
    assert.equal(p.run(`Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, 'response').get === originals.xhr
        && Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, 'responseText').get === originals.xhrText`), true);
    assert.equal(p.intervals.size, 0);
    assert.equal(p.listeners.size, 0);
});

test('stop does not overwrite hooks installed later by the page', () => {
    const p = page(undefined, true, true);
    p.run(`siteText = function () { return 'site'; }; Response.prototype.text = siteText;
        siteGetter = function () { return 'site'; };
        Object.defineProperty(XMLHttpRequest.prototype, 'responseText', {get: siteGetter, configurable: true});
        __ssyoutubeVideoAdBlocking.stop();`);
    assert.equal(p.run('Response.prototype.text === siteText'), true);
    assert.equal(p.run(`Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, 'responseText').get === siteGetter`), true);
});

test('a stale stop callback cannot remove a new implementation instance', () => {
    const p = page(undefined, true, true);
    p.run('oldStop = __ssyoutubeVideoAdBlocking.stop;');
    p.inject(false);
    p.run('oldStop();');
    assert.equal(p.run('__ssyoutubeVideoAdBlocking.patched'), false);
    assert.equal(p.intervals.size, 1);
});

test('patched injection excludes sign-in, insecure origins, lookalikes and subframes', () => {
    for (const url of ['https://accounts.google.com/', 'https://accounts.youtube.com/',
        'https://m.youtube.com:444/', 'http://www.youtube.com/', 'https://www.youtube.com.evil.test/']) {
        const p = page(url, true, true);
        assert.equal(p.run('Response.prototype.text === originals.text && JSON.parse === originals.parse'), true);
        assert.equal(p.intervals.size, 0);
    }
    const p = page(undefined, false, true);
    p.run('window.top = {};');
    p.inject();
    assert.equal(p.intervals.size, 0);
    assert.equal(p.run('Response.prototype.text === originals.text'), true);
});

test('frozen responses, read-only hooks, cycles, and large renderer arrays are safe', () => {
    const p = page(undefined, false, true);
    p.run(`Object.defineProperty(Response.prototype, 'text', {writable: false});
        ytInitialData = {contents: Object.freeze([{adSlotRenderer: {}}])};`);
    p.inject();
    assert.equal(p.run('Response.prototype.text === originals.text'), true);
    assert.equal(p.run('ytInitialData.contents.length'), 1);
    p.run(`cycle = {}; cycle.contents = [cycle, {adSlotRenderer: {}}, {videoRenderer: {}}];
        ytInitialData = cycle;`);
    assert.equal(p.run('ytInitialData.contents.length'), 2);
    p.run(`large = Array.from({length: 4097}, () => ({adSlotRenderer: {}}));
        ytInitialData = {contents: large};`);
    assert.equal(p.run('large.length'), 4097);
    assert.ok(p.warnings.includes('ssYouTube adblocking: Component limit reached'));
    assert.ok(p.warnings.includes('ssYouTube adblocking: Read-only browser hook left unchanged'));
});

test('nonconfigurable XHR text hooks are left intact and reported without page data', () => {
    const p = page(undefined, false, true);
    p.run("Object.defineProperty(XMLHttpRequest.prototype, 'responseText', {configurable: false});");
    p.inject();
    assert.equal(p.run(`Object.getOwnPropertyDescriptor(
        XMLHttpRequest.prototype, 'responseText').get === originals.xhrText`), true);
    assert.deepEqual(p.warnings, ['ssYouTube adblocking: XHR text hook unavailable']);
});

function installArchiveFixture(p) {
    p.run(`
        function archiveFixture() {
            return {
                responseContext: {},
                videoDetails: {videoId: 'organic'},
                playabilityStatus: {status: 'OK'},
                contents: [
                    {merchandiseShelfRenderer: {items: [{title: 'Merchandise'}]}},
                    {richSectionRenderer: {content: {productListRenderer: {contents: []}}}},
                    {statementBannerRenderer: {title: {simpleText: 'Ordinary Doodle'}}},
                    {statementBannerRenderer: {thumbnail: {thumbnails: [
                        {url: 'https://www.gstatic.com/youtube/img/promos/growth/premium.png'}
                    ]}}},
                    {backgroundPromoRenderer: {ctaButton: {buttonRenderer: {
                        navigationEndpoint: {browseEndpoint: {browseId: 'SPunlimited'}}
                    }}}},
                    {videoRenderer: {videoId: 'keep', title: {simpleText: 'Premium products'}}},
                    {continuationItemRenderer: {continuationEndpoint: {token: 'next'}}}
                ],
                paidContentOverlay: {paidContentOverlayRenderer: {text: 'Includes paid promotion'}},
                engagementPanels: [
                    {engagementPanelSectionListRenderer: {targetId: 'engagement-panel-PAproduct',
                        content: {productListRenderer: {contents: []}}}},
                    {engagementPanelSectionListRenderer: {identifier: {tag: 'jumpahead'}}},
                    {engagementPanelSectionListRenderer: {targetId: 'engagement-panel-comments-section',
                        content: {sectionListRenderer: {contents: [{commentThreadRenderer: {
                            comment: {text: 'SPunlimited'}
                        }}]}}}},
                    {engagementPanelSectionListRenderer: {targetId: 'engagement-panel-macro-markers-description-chapters'}}
                ],
                endscreen: {endscreenRenderer: {elements: [
                    {endscreenElementRenderer: {style: 'WEBSITE', image: {thumbnails: [
                        {url: 'https://encrypted-tbn0.gstatic.com/shopping?q=product'}
                    ]}}},
                    {endscreenElementRenderer: {style: 'VIDEO', endpoint: {watchEndpoint: {videoId: 'next'}}}},
                    {endscreenElementRenderer: {style: 'WEBSITE', image: {thumbnails: [
                        {url: 'https://example.test/channel-site.png'}
                    ]}}}
                ]}},
                onResponseReceivedActions: [
                    {openPopupAction: {popup: {adInterstitialRenderer: {title: 'Ad'}}, popupType: 'DIALOG'}},
                    {openPopupAction: {popup: {confirmDialogRenderer: {
                        title: 'Sign in', endpoint: {browseEndpoint: {browseId: 'SPunlimited'}}
                    }}, popupType: 'DIALOG'}},
                    {openPopupAction: {popup: {playerInterstitialRenderer: {
                        content: {interstitialViewModel: {text: 'Playback unavailable'}}
                    }}, popupType: 'DIALOG'}}
                ]
            };
        }
        function archiveResult(value) {
            return {
                contents: value.contents.length,
                paidLabel: !!value.paidContentOverlay,
                panels: value.engagementPanels.length,
                endCards: value.endscreen.endscreenRenderer.elements.length,
                popups: value.onResponseReceivedActions.length
            };
        }
    `);
}

test('archive-inspired filters apply to inline and legacy player data without scheduling fields', () => {
    const p = page(undefined, true, true);
    installArchiveFixture(p);
    for (const assignment of [
        'ytInitialData = archiveFixture(); result = ytInitialData;',
        'ytInitialPlayerResponse = archiveFixture(); result = ytInitialPlayerResponse;',
        `ytplayer = {config: {args: {player_response: JSON.stringify(archiveFixture())}}};
            result = originals.parse(ytplayer.config.args.player_response);`
    ]) {
        p.run(assignment);
        assert.deepEqual(JSON.parse(p.run('JSON.stringify(archiveResult(result))')),
            {contents: 3, paidLabel: false, panels: 2, endCards: 2, popups: 2});
        assert.equal(p.run('result.contents[0].statementBannerRenderer.title.simpleText'), 'Ordinary Doodle');
        assert.equal(p.run('result.contents[1].videoRenderer.videoId'), 'keep');
        assert.equal(p.run('result.contents[2].continuationItemRenderer.continuationEndpoint.token'), 'next');
        assert.equal(p.run('result.engagementPanels[0].engagementPanelSectionListRenderer.targetId'),
            'engagement-panel-comments-section');
        assert.equal(p.run('result.endscreen.endscreenRenderer.elements[0].endscreenElementRenderer.style'), 'VIDEO');
        assert.equal(p.run('result.onResponseReceivedActions[0].openPopupAction.popup.confirmDialogRenderer.title'),
            'Sign in');
    }
});

test('archive features work through all scoped response transports and continuations', async () => {
    const p = page(undefined, true, true);
    installArchiveFixture(p);
    for (const expression of [
        'JSON.parse(JSON.stringify(archiveFixture()))',
        'new Response(archiveFixture()).json()',
        'new Response(JSON.stringify(archiveFixture())).text().then(text => originals.parse(text))',
        `(() => { var xhr = new XMLHttpRequest(JSON.stringify(archiveFixture()));
            xhr.responseType = 'text'; return originals.parse(xhr.responseText); })()`,
        'new XMLHttpRequest(archiveFixture()).response'
    ]) {
        const result = await p.run(`Promise.resolve(${expression}).then(value => JSON.stringify(archiveResult(value)))`);
        assert.deepEqual(JSON.parse(result),
            {contents: 3, paidLabel: false, panels: 2, endCards: 2, popups: 2});
    }
    p.run(`ytInitialData = {onResponseReceivedActions: [{appendContinuationItemsAction: {
        continuationItems: archiveFixture().contents
    }}]};`);
    assert.equal(p.run('ytInitialData.onResponseReceivedActions[0].appendContinuationItemsAction.continuationItems.length'), 3);
});

test('Premium promotion evidence requires typed endpoints or trusted image URL boundaries', () => {
    const p = page(undefined, true, true);
    const rejected = [
        'https://gstatic.com.evil.test/youtube/img/promos/growth/a.png',
        'https://evilgstatic.com/youtube/img/promos/growth/a.png',
        'http://www.gstatic.com/youtube/img/promos/growth/a.png',
        'https://www.gstatic.com:444/youtube/img/promos/growth/a.png',
        'https://www.gstatic.com/youtube/img/promos/growth-other/a.png',
        'https://www.gstatic.com/youtube/img/doodle.png?next=/img/promos/growth/',
        'https://www.gstatic.com/youtube/img/promos/growth/../doodle.png',
        'https://['
    ];
    rejected.push('https://' + ['user', 'www.gstatic.com'].join('@') + '/youtube/img/promos/growth/a.png');
    p.context.rejected = rejected;
    p.run(`safe = rejected.map(url => ({statementBannerRenderer: {thumbnail: {thumbnails: [{url}]}}}));
        safe.push({statementBannerRenderer: {title: {simpleText: 'SPunlimited img/promos/growth/'}}});
        safe.push({statementBannerRenderer: {title: {browseId: 'SPunlimited'}}});
        safe.push({statementBannerRenderer: {image: {thumbnails: [{url:
            'https://www.gstatic.com/youtube/img/doodle.png'}]}}});
        safe.push({videoRenderer: {thumbnail: {thumbnails: [{url:
            'https://www.gstatic.com/youtube/img/promos/growth/premium.png'}]}}});
        expected = JSON.stringify(safe);
        ytInitialData = {contents: safe};`);
    assert.equal(p.run('JSON.stringify(ytInitialData.contents) === expected'), true);
    for (const url of ['https://gstatic.com/youtube/img/promos/growth/a.png',
        'https://www.gstatic.com:443/youtube/img/promos/growth/a.png',
        '//www.gstatic.com/youtube/img/promos/growth/a.png']) {
        p.context.image = url;
        p.run('ytInitialData = {contents: [{statementBannerRenderer: {image: {thumbnails: [{url: image}]}}}]};');
        assert.equal(p.run('ytInitialData.contents.length'), 0);
    }
});

test('shopping panels, end cards and popups require explicit ad identity', () => {
    const p = page(undefined, true, true);
    p.run(`safe = {
        engagementPanels: [
            {engagementPanelSectionListRenderer: {targetId: 'engagement-panel-PAproductivity'}},
            {engagementPanelSectionListRenderer: {targetId: 'comments-jumpahead'}},
            {engagementPanelSectionListRenderer: {targetId: 'engagement-panel-comments',
                content: {productListRenderer: {contents: []}}}}
        ],
        endscreen: {endscreenRenderer: {elements: [
            {endscreenElementRenderer: {style: 'VIDEO', image: {thumbnails: [
                {url: 'https://encrypted-tbn0.gstatic.com/shopping?q=image'}
            ]}}},
            {endscreenElementRenderer: {style: 'WEBSITE', image: {thumbnails: [
                {url: 'https://encrypted-tbn0.gstatic.com.evil.test/shopping?q=image'}
            ]}}},
            {endscreenElementRenderer: {style: 'WEBSITE', image: {thumbnails: [
                {url: 'https://encrypted-tbn0.gstatic.com/shopping-other?q=image'}
            ]}}}
        ]}},
        contents: [{adSlotRenderer: {}, statementBannerRenderer: {title: 'Doodle'}}],
        onResponseReceivedActions: [{openPopupAction: {
            popup: {adInterstitialRenderer: {}, confirmDialogRenderer: {title: 'Account'}}
        }}]
    };
    expected = JSON.stringify(safe); ytInitialData = safe;`);
    assert.equal(p.run('JSON.stringify(ytInitialData) === expected'), true);
    p.run(`ytInitialData = {engagementPanels: [
        {engagementPanelSectionListRenderer: {content: {productListRenderer: {contents: []}}}},
        {engagementPanelSectionListRenderer: {panelIdentifier: 'PAproduct-products'}},
        {engagementPanelSectionListRenderer: {targetId: 'engagement-panel-jumpahead:promo'}}
    ]};`);
    assert.equal(p.run('ytInitialData.engagementPanels.length'), 0);
});

test('basic mode does not remove archive-specific UI data', () => {
    const p = page();
    installArchiveFixture(p);
    p.run(`expected = JSON.stringify(archiveFixture());
        ytInitialData = archiveFixture(); ytInitialPlayerResponse = archiveFixture();
        ytplayer = {config: {args: {player_response: expected}}};`);
    assert.equal(p.run('JSON.stringify(ytInitialData) === expected'), true);
    assert.equal(p.run('JSON.stringify(ytInitialPlayerResponse) === expected'), true);
    assert.equal(p.run('ytplayer.config.args.player_response === expected'), true);
});

test('promotion detection is bounded, ignores getters and leaves immutable ad records alone', () => {
    const p = page(undefined, true, true);
    p.run(`calls = 0;
        banner = {};
        Object.defineProperty(banner, 'image', {get() { calls++; throw new Error('Do not evaluate'); }});
        banner.button = banner;
        frozen = Object.freeze([{merchandiseShelfRenderer: {}}]);
        ytInitialData = {contents: [{statementBannerRenderer: banner}], items: frozen};
        Object.defineProperty(ytInitialData, 'paidContentOverlay', {
            value: {paidContentOverlayRenderer: {}}, configurable: false
        });
        __ssyoutubeVideoAdBlocking.update();`);
    assert.equal(p.run('calls'), 0);
    assert.equal(p.run('ytInitialData.contents.length'), 1);
    assert.equal(p.run('ytInitialData.items.length'), 1);
    assert.equal(p.run('!!ytInitialData.paidContentOverlay'), true);
    p.run(`large = {button: {}};
        var current = large.button;
        for (var i = 0; i < 1000; i++) { current.button = {}; current = current.button; }
        current.browseId = 'SPunlimited';
        ytInitialData = {contents: [{statementBannerRenderer: large}]};`);
    assert.equal(p.run('ytInitialData.contents.length'), 1);
});
