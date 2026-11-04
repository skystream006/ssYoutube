package com.skystream.ssyoutube;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

import okhttp3.CookieJar;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Applies to manifests, segments, initialization/index ranges and encryption keys alike. */
final class NativeMediaHttp implements Interceptor {
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .cookieJar(CookieJar.NO_COOKIES)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(NativeNetworkPolicy.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(NativeNetworkPolicy.READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .addInterceptor(new NativeMediaHttp())
            .build();

    static OkHttpClient client() {
        return CLIENT;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        if (!"GET".equals(request.method()) && !"HEAD".equals(request.method())) {
            throw new IOException("Unsupported media request method");
        }
        URI uri = NativeNetworkPolicy.requireHttps(request.url().toString(), true);
        for (int redirects = 0; redirects <= NativeNetworkPolicy.MAX_REDIRECTS; redirects++) {
            request = request.newBuilder().url(uri.toString())
                    .removeHeader("Cookie").removeHeader("Cookie2")
                    .removeHeader("Authorization").removeHeader("Proxy-Authorization")
                    .removeHeader("Host")
                    .header("User-Agent", NativeNetworkPolicy.USER_AGENT).build();
            Response response = chain.proceed(request);
            if (!NativeNetworkPolicy.isRedirect(response.code())) {
                return response;
            }
            try {
                uri = NativeNetworkPolicy.redirect(uri, response.header("Location"), true);
            } finally {
                response.close();
            }
        }
        throw new IOException("Too many media redirects");
    }
}
