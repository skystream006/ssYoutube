package com.skystream.ssyoutube;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.CookieJar;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class NativeMediaHttpTest {
    private static final String CDN = "https://rr1.googlevideo.com/video";

    @Test
    public void isolatedClientDoesNotUseCookiesOrAutomaticRedirects() {
        OkHttpClient client = NativeMediaHttp.client();
        assertSame(CookieJar.NO_COOKIES, client.cookieJar());
        assertFalse(client.followRedirects());
        assertFalse(client.followSslRedirects());
        assertEquals(NativeNetworkPolicy.READ_TIMEOUT_MS, client.readTimeoutMillis());
    }

    @Test
    public void rangeRequestsAndSafeRedirectsRemainAnonymous() throws Exception {
        Request request = new Request.Builder().url(CDN).header("Range", "bytes=700-999")
                .header("Cookie", "session=private").header("Cookie2", "private")
                .header("Authorization", "private").header("Proxy-Authorization", "private").build();
        FakeChain chain = new FakeChain(request,
                "https://rr2.googlevideo.com/segment?sig=example");
        try (Response response = new NativeMediaHttp().intercept(chain)) {
            assertEquals(200, response.code());
            assertEquals(2, chain.requests.size());
            for (Request sent : chain.requests) {
                assertNull(sent.header("Cookie"));
                assertNull(sent.header("Cookie2"));
                assertNull(sent.header("Authorization"));
                assertNull(sent.header("Proxy-Authorization"));
                assertEquals("bytes=700-999", sent.header("Range"));
                assertEquals(NativeNetworkPolicy.USER_AGENT, sent.header("User-Agent"));
            }
        }
    }

    @Test
    public void initialUrlsAreCheckedBeforeAnyConnection() {
        for (String url : new String[]{"http://rr1.googlevideo.com/video",
                "https://untrusted.example/segment", "https://user@rr1.googlevideo.com/video",
                "https://rr1.googlevideo.com:8443/video"}) {
            FakeChain chain = new FakeChain(new Request.Builder().url(url).build());
            assertThrows(IOException.class, () -> new NativeMediaHttp().intercept(chain));
            assertTrue(chain.requests.isEmpty());
        }
    }

    @Test
    public void redirectTargetsAreCheckedBeforeConnectingAndLoopsAreBounded() {
        for (String location : new String[]{"http://rr1.googlevideo.com/video",
                "file:///private", "https://untrusted.example/segment"}) {
            FakeChain chain = new FakeChain(new Request.Builder().url(CDN).build(), location);
            assertThrows(IOException.class, () -> new NativeMediaHttp().intercept(chain));
            assertEquals(1, chain.requests.size());
        }
        String[] loop = new String[NativeNetworkPolicy.MAX_REDIRECTS + 2];
        java.util.Arrays.fill(loop, "/video");
        FakeChain chain = new FakeChain(new Request.Builder().url(CDN).build(), loop);
        assertThrows(IOException.class, () -> new NativeMediaHttp().intercept(chain));
        assertEquals(NativeNetworkPolicy.MAX_REDIRECTS + 1, chain.requests.size());
    }

    private static final class FakeChain implements Interceptor.Chain {
        final Request initial;
        final String[] redirects;
        final List<Request> requests = new ArrayList<>();

        FakeChain(Request initial, String... redirects) {
            this.initial = initial;
            this.redirects = redirects;
        }

        @Override public Request request() { return initial; }
        @Override public Response proceed(Request request) {
            int index = requests.size();
            requests.add(request);
            Response.Builder response = new Response.Builder().request(request)
                    .protocol(Protocol.HTTP_1_1).message("fixture")
                    .body(ResponseBody.create(null, new byte[0]));
            if (index < redirects.length) {
                return response.code(302).header("Location", redirects[index]).build();
            }
            return response.code(200).build();
        }
        @Override public Connection connection() { return null; }
        @Override public Call call() { return new OkHttpClient().newCall(initial); }
        @Override public int connectTimeoutMillis() { return 1000; }
        @Override public int readTimeoutMillis() { return 1000; }
        @Override public int writeTimeoutMillis() { return 1000; }
        @Override public Interceptor.Chain withConnectTimeout(int timeout, TimeUnit unit) {
            return this;
        }
        @Override public Interceptor.Chain withReadTimeout(int timeout, TimeUnit unit) {
            return this;
        }
        @Override public Interceptor.Chain withWriteTimeout(int timeout, TimeUnit unit) {
            return this;
        }
    }
}
