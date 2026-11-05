const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const vm = require('node:vm');
const {webcrypto} = require('node:crypto');

// JUnit exports the exact shipped Java string, including its trusted-page predicate.
const source = fs.readFileSync(process.env.SSYOUTUBE_HISTORY_SCRIPT, 'utf8');
const ID = 'abcdefghijk';
const NEXT = '12345678901';
const settle = () => new Promise(resolve => setImmediate(resolve));

function metadata(id = ID) {
    return {
        videoDetails: {videoId: id},
        playbackTracking: {
            videostatsPlaybackUrl: {
                baseUrl: `https://s.youtube.com/api/stats/playback?docid=${id}&ei=fixture-token`
            },
            videostatsWatchtimeUrl: {
                baseUrl: `https://s.youtube.com/api/stats/watchtime?docid=${id}&ei=fixture-token`
            }
        }
    };
}

function report(position = 10, start = 0, playing = true) {
    return {position, duration: 120, starts: start.toFixed(3),
        ends: position.toFixed(3), playing};
}

function page(url = `https://m.youtube.com/watch?v=${ID}`, mainFrame = true) {
    let now = 0;
    let nextTimer = 0;
    const timers = new Map();
    const events = new Map();
    const requests = [];
    const warnings = [];
    const nodes = [];
    const config = {SESSION_INDEX: '0', LOGGED_IN: true, DELEGATED_SESSION_ID: 'fixture-account'};
    const context = vm.createContext({
        URL, Uint8Array, AbortController, crypto: webcrypto,
        location: new URL(url),
        document: {querySelectorAll: () => nodes},
        performance: {now: () => now},
        console: {warn: value => warnings.push(value)},
        setTimeout(callback, delay) {
            const id = ++nextTimer;
            timers.set(id, {at: now + delay, callback});
            return id;
        },
        clearTimeout: id => timers.delete(id),
        addEventListener(name, callback) {
            if (!events.has(name)) events.set(name, []);
            events.get(name).push(callback);
        },
        ytcfg: {get: key => config[key]},
        ytInitialPlayerResponse: metadata(),
        fetch: async (value, options) => {
            const request = {url: new URL(value), options};
            requests.push(request);
            return fixture.respond(request);
        }
    });
    context.window = context;
    context.top = mainFrame ? context : {};
    const fixture = {
        context, config, nodes, requests, warnings, timers,
        respond: async () => ({ok: true, type: 'opaque'}),
        update(value = report(), finish = false, id = ID, session = 1) {
            context.__ssyoutubeWatchHistory(id, session, value, finish);
        },
        navigate(url) {
            for (const callback of events.get('yt-navigate-start') || []) callback();
            context.location.href = url;
        },
        async advance(milliseconds) {
            const end = now + milliseconds;
            let runs = 0;
            while (true) {
                const next = [...timers.entries()].sort((a, b) => a[1].at - b[1].at)[0];
                if (!next || next[1].at > end) break;
                assert.ok(++runs < 200, 'Timers must remain bounded');
                now = next[1].at;
                timers.delete(next[0]);
                next[1].callback();
                await settle();
            }
            now = end;
        }
    };
    vm.runInContext(source, context);
    return fixture;
}

test('only trusted top-level HTTPS documents install the history bridge', () => {
    for (const host of ['youtube.com', 'm.youtube.com', 'www.youtube.com', 'music.youtube.com']) {
        const fixture = page(`https://${host}/watch?v=${ID}`);
        const installed = fixture.context.__ssyoutubeWatchHistory;
        assert.equal(typeof installed, 'function');
        vm.runInContext(source, fixture.context);
        assert.equal(fixture.context.__ssyoutubeWatchHistory, installed);
    }
    for (const url of ['http://m.youtube.com/', 'https://m.youtube.com:444/',
        'https://youtube.com.evil.test/', 'https://accounts.google.com/']) {
        assert.equal(page(url).context.__ssyoutubeWatchHistory, undefined);
    }
    assert.equal(page(undefined, false).context.__ssyoutubeWatchHistory, undefined);
});

test('records video, timestamp, duration and watched ranges through the signed-in browser', async () => {
    const fixture = page();
    fixture.update(report(40, 30));
    await settle();
    assert.deepEqual(fixture.requests.map(r => r.url.pathname),
        ['/api/stats/playback', '/api/stats/watchtime']);
    const [playback, watched] = fixture.requests;
    for (const request of fixture.requests) {
        assert.equal(request.url.searchParams.get('docid'), ID);
        assert.equal(request.url.searchParams.get('cmt'), '40');
        assert.equal(request.url.searchParams.get('len'), '120');
        assert.equal(request.url.searchParams.get('ei'), 'fixture-token');
        assert.equal(request.options.credentials, 'include');
        assert.equal(request.options.redirect, 'error');
        assert.equal(request.options.cache, 'no-store');
    }
    assert.match(watched.url.searchParams.get('cpn'), /^[A-Za-z0-9_-]{16}$/);
    assert.equal(playback.url.searchParams.get('cpn'), watched.url.searchParams.get('cpn'));
    assert.equal(watched.url.searchParams.get('st'), '30.000');
    assert.equal(watched.url.searchParams.get('et'), '40.000');
    fixture.update(report(45, 40, false), true);
    await settle();
    assert.equal(fixture.requests.length, 3, 'Only one playback registration per session');
    const final = fixture.requests[2].url.searchParams;
    assert.equal(final.get('cmt'), '45');
    assert.equal(final.get('final'), '1');
    assert.equal(final.get('state'), 'paused');
    assert.equal(final.get('cpn'), watched.url.searchParams.get('cpn'));
});

test('late metadata retains ranges and the newest paused timestamp, including after close', async () => {
    const fixture = page();
    fixture.context.ytInitialPlayerResponse = null;
    fixture.update();
    fixture.update(report(12, 10, false), true);
    await settle();
    assert.equal(fixture.requests.length, 0);
    fixture.context.ytInitialPlayerResponse = metadata();
    await fixture.advance(1000);
    assert.equal(fixture.requests.length, 2);
    const params = fixture.requests[1].url.searchParams;
    assert.equal(params.get('st'), '0.000,10.000');
    assert.equal(params.get('et'), '10.000,12.000');
    assert.equal(params.get('cmt'), '12');
    assert.equal(params.get('final'), '1');
});

test('SPA navigation accepts a fresh initial response without reusing stale metadata', async () => {
    const fixture = page('https://m.youtube.com/');
    fixture.navigate(`https://m.youtube.com/watch?v=${ID}`);
    fixture.update();
    await settle();
    assert.equal(fixture.requests.length, 0, 'Initial homepage metadata was invalidated');
    fixture.context.ytInitialPlayerResponse = metadata();
    await fixture.advance(1000);
    assert.equal(fixture.requests.length, 2);
});

test('late mobile and desktop player metadata is supported', async () => {
    for (const node of [
        {getPlayerResponse: () => metadata()},
        {data: {playerResponse: metadata()}},
        {playerResponse: metadata()},
        {playerData: metadata()}
    ]) {
        const fixture = page();
        fixture.context.ytInitialPlayerResponse = null;
        fixture.nodes.push(node);
        fixture.update();
        await settle();
        assert.equal(fixture.requests.length, 2);
    }
});

test('missing sign-in metadata at startup does not invalidate later signed-in metadata', async () => {
    const fixture = page();
    delete fixture.config.LOGGED_IN;
    fixture.update(null);
    fixture.config.LOGGED_IN = true;
    fixture.update();
    await settle();
    assert.equal(fixture.requests.length, 2);
});

test('signed-out playback never sends account history', async () => {
    const fixture = page();
    fixture.config.LOGGED_IN = false;
    fixture.update();
    await fixture.advance(60000);
    assert.equal(fixture.requests.length, 0);
    assert.equal(fixture.timers.size, 0);
});

test('wrong-video metadata and untrusted tracking endpoints cannot receive reports', async () => {
    for (const endpoint of [
        'http://s.youtube.com/api/stats/watchtime',
        'https://youtube.com.evil.test/api/stats/watchtime',
        'https://user:password@s.youtube.com/api/stats/watchtime',
        'https://s.youtube.com:444/api/stats/watchtime',
        'https://s.youtube.com/api/stats/watchtime#fragment',
        'https://s.youtube.com/unrelated',
        `https://s.youtube.com/api/stats/watchtime?docid=${NEXT}`,
        `https://s.youtube.com/api/stats/watchtime?docid=${ID}&docid=${NEXT}`
    ]) {
        const fixture = page();
        fixture.context.ytInitialPlayerResponse.playbackTracking
            .videostatsWatchtimeUrl.baseUrl = endpoint;
        fixture.update();
        await fixture.advance(1000);
        assert.equal(fixture.requests.length, 0);
    }
    const fixture = page();
    fixture.context.ytInitialPlayerResponse = metadata(NEXT);
    fixture.update();
    await fixture.advance(1000);
    assert.equal(fixture.requests.length, 0);
});

test('transient registration failures retain ranges and the newest position for retry', async () => {
    const fixture = page();
    fixture.respond = async () => {
        if (fixture.requests.length === 1) throw Error('private request details');
        return {ok: true};
    };
    fixture.update();
    await settle();
    fixture.update(report(15, 10, false), true);
    await fixture.advance(1000);
    assert.equal(fixture.requests.length, 3);
    const params = fixture.requests[2].url.searchParams;
    assert.equal(params.get('st'), '0.000,10.000');
    assert.equal(params.get('et'), '10.000,15.000');
    assert.equal(params.get('cmt'), '15');
    assert.equal(params.get('final'), '1');
    assert.deepEqual(fixture.warnings, ['ssYouTube: native history request failed']);
});

test('watchtime retry does not repeat successful playback registration and is bounded', async () => {
    const fixture = page();
    fixture.respond = async request => ({
        ok: request.url.pathname === '/api/stats/playback', type: 'basic'
    });
    fixture.update();
    await settle();
    await fixture.advance(60000);
    assert.deepEqual(fixture.requests.map(r => r.url.pathname),
        ['/api/stats/playback', '/api/stats/watchtime', '/api/stats/watchtime',
            '/api/stats/watchtime']);
    assert.equal(fixture.timers.size, 0);
    assert.equal(fixture.warnings.at(-1), 'ssYouTube: native history update unavailable');
    assert.ok(fixture.warnings.every(value => !value.includes('fixture-token')
        && !value.includes(ID)));
});

test('metadata waiting expires after 30 seconds instead of retaining history indefinitely', async () => {
    const fixture = page();
    fixture.context.ytInitialPlayerResponse = null;
    fixture.update();
    await fixture.advance(30000);
    assert.equal(fixture.requests.length, 0);
    assert.equal(fixture.timers.size, 0);
    assert.deepEqual(fixture.warnings, ['ssYouTube: native history update unavailable']);
    fixture.context.ytInitialPlayerResponse = metadata();
    fixture.update(report(40, 30));
    await settle();
    assert.equal(fixture.requests[1].url.searchParams.get('st'), '30.000');
});

test('queued delivery rechecks account identity after an in-flight request', async () => {
    const fixture = page();
    fixture.nodes.push({playerData: metadata()});
    let release;
    fixture.respond = () => new Promise(resolve => { release = resolve; });
    fixture.update();
    await settle();
    fixture.update(report(20, 10));
    fixture.config.SESSION_INDEX = '1';
    release({ok: true});
    await settle();
    await fixture.advance(60000);
    assert.equal(fixture.requests.length, 1, 'No old-account watchtime or queued progress');
    fixture.respond = async () => ({ok: true});
    fixture.update(report(30, 20));
    await settle();
    assert.equal(fixture.requests.length, 1, 'Old account metadata cannot be reused');
    fixture.context.ytInitialPlayerResponse = metadata();
    await fixture.advance(1000);
    assert.equal(fixture.requests.length, 3);
    assert.equal(fixture.requests[2].url.searchParams.get('st'), '20.000');
});

test('miniplayer reporting needs cached video metadata and the same signed-in account', async () => {
    const fixture = page();
    fixture.update(null);
    fixture.navigate('https://m.youtube.com/results?search_query=fixture');
    fixture.update();
    await settle();
    assert.equal(fixture.requests.length, 2);
    fixture.config.LOGGED_IN = false;
    fixture.update(report(20, 10));
    await settle();
    assert.equal(fixture.requests.length, 2);

    const missing = page('https://m.youtube.com/');
    missing.update();
    await missing.advance(30000);
    assert.equal(missing.requests.length, 0, 'Cannot acquire video metadata on another page');
});

test('outgoing finals and new videos keep their own IDs, positions and playback nonces', async () => {
    const fixture = page();
    fixture.update(null);
    fixture.navigate(`https://m.youtube.com/watch?v=${NEXT}`);
    fixture.context.ytInitialPlayerResponse = metadata(NEXT);
    fixture.update(report(11, 0, false), true);
    fixture.update(report(25, 20), false, NEXT, 2);
    await settle();
    const outgoing = fixture.requests.find(r => r.url.pathname.endsWith('/watchtime')
        && r.url.searchParams.get('docid') === ID);
    const incoming = fixture.requests.find(r => r.url.pathname.endsWith('/watchtime')
        && r.url.searchParams.get('docid') === NEXT);
    assert.ok(outgoing && incoming);
    assert.equal(outgoing.url.searchParams.get('cmt'), '11');
    assert.equal(outgoing.url.searchParams.get('final'), '1');
    assert.equal(incoming.url.searchParams.get('cmt'), '25');
    assert.notEqual(outgoing.url.searchParams.get('cpn'), incoming.url.searchParams.get('cpn'));
});

test('invalid native reports never produce requests', async () => {
    for (const value of [
        {...report(), position: NaN}, {...report(), duration: -1},
        {...report(), starts: '0;alert(1)'}, {...report(), ends: ''},
        {...report(), starts: '0'.repeat(2049)}
    ]) {
        const fixture = page();
        fixture.update(value);
        await settle();
        assert.equal(fixture.requests.length, 0);
    }
});
