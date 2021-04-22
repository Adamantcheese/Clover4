package com.github.adamantcheese.chan.core.net;

import android.graphics.Bitmap;
import android.util.JsonReader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LruCache;
import androidx.core.util.Pair;

import com.franmontiel.persistentcookiejar.PersistentCookieJar;
import com.franmontiel.persistentcookiejar.cache.SetCookieCache;
import com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor;
import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.OkHttpClientWithUtils;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.http.HttpCall;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.BitmapUtils;
import com.github.adamantcheese.chan.utils.ExceptionCatchingInputStream;
import com.github.adamantcheese.chan.utils.IOUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.StringUtils;

import org.jetbrains.annotations.NotNull;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.github.adamantcheese.chan.core.di.AppModule.getCacheDir;
import static com.github.adamantcheese.chan.core.net.DnsSelector.Mode.IPV4_ONLY;
import static com.github.adamantcheese.chan.core.net.DnsSelector.Mode.SYSTEM;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static java.lang.Runtime.getRuntime;
import static okhttp3.Protocol.HTTP_1_1;
import static okhttp3.Protocol.HTTP_2;

public class NetUtils {
    public static final String USER_AGENT = BuildConfig.APP_LABEL + "/" + BuildConfig.VERSION_NAME;

    private static final int MB = 1024 * 1024;
    // The OkHttpClient installed cache, used for all requests
    private static final Cache OK_HTTP_CACHE = new Cache(new File(getCacheDir(), "okhttp"),
            ChanSettings.autoLoadThreadImages.get()
                    ? (long) ChanSettings.fileCacheSize.get() * 2 * MB
                    : (long) ChanSettings.fileCacheSize.get() * MB
    );

    public static final OkHttpClientWithUtils applicationClient =
            new OkHttpClientWithUtils(new OkHttpClient.Builder().cache(OK_HTTP_CACHE)
                    .protocols(ChanSettings.okHttpAllowHttp2.get()
                            ? Arrays.asList(HTTP_2, HTTP_1_1)
                            : Collections.singletonList(HTTP_1_1))
                    .dns(new DnsSelector(ChanSettings.okHttpAllowIpv6.get() ? SYSTEM : IPV4_ONLY))
                    .cookieJar(new WebviewSyncCookieManager(new PersistentCookieJar(new SetCookieCache(),
                            new SharedPrefsCookiePersistor(getAppContext())
                    )))
                    .addNetworkInterceptor(chain -> {
                        // interceptor to add the User-Agent for all requests
                        Request request = chain.request().newBuilder().header("User-Agent", USER_AGENT).build();
                        return chain.proceed(request);
                    }));

    // max 1/4 the maximum Dalvik runtime size
    // by default, the max heap size of stock android is 512MiB; keep that in mind if you change things here
    private static final LruCache<HttpUrl, Bitmap> imageCache =
            new LruCache<HttpUrl, Bitmap>((int) (getRuntime().maxMemory() / 4)) {
                @Override
                protected int sizeOf(@NonNull HttpUrl key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    private static final Map<HttpUrl, List<NetUtilsClasses.BitmapResult>> resultListeners = new HashMap<>();

    public synchronized static void cleanup() {
        resultListeners.clear();
    }

    public static void makeHttpCall(HttpCall httpCall) {
        makeHttpCall(httpCall, null);
    }

    public static void makeHttpCall(
            HttpCall httpCall, @Nullable ProgressRequestBody.ProgressRequestListener progressListener
    ) {
        Request.Builder requestBuilder = new Request.Builder();
        httpCall.setup(requestBuilder, progressListener);
        applicationClient.getProxiedClient().newCall(requestBuilder.build()).enqueue(httpCall);
    }

    /**
     * Simple wrapper to check if a url has been cached by OkHttp
     *
     * @param url The url to check
     * @return true if the url has a cached response
     */
    @SuppressWarnings("KotlinInternalInJava")
    public static boolean isCached(HttpUrl url) {
        return OK_HTTP_CACHE.getCache$okhttp().getLruEntries$okhttp().containsKey(Cache.key(url));
    }

    /**
     * Get a raw, cached response.
     * You are responsible for clearing up the file resource!
     *
     * @param url              the url to get the resposne from
     * @param filename         the name of the cached file you want to make
     * @param fileExt          the extension for the cached file
     * @param result           the result callback
     * @param progressListener an optional progress listener
     * @return An enqueued file call. WILL RUN RESULT ON MAIN THREAD!
     */
    public static Call makeFileRequest(
            @NonNull final HttpUrl url,
            @NonNull final String filename,
            @NonNull final String fileExt,
            @NonNull final NetUtilsClasses.ResponseResult<File> result,
            @Nullable final ProgressResponseBody.ProgressListener progressListener
    ) {
        return makeRequest(applicationClient.getHttpRedirectClient(), url, (response) -> {
            File tempFile = new File(new File(getCacheDir(), "requested"),
                    StringUtils.fileNameRemoveBadCharacters(filename) + "." + fileExt
            );
            tempFile.getParentFile().mkdirs();
            IOUtils.writeToFile(response.body().byteStream(), tempFile, -1);

            if (response.body().contentLength() != tempFile.length()) {
                throw new IOException(
                        "File sizes don't match! Expected:" + response.body().contentLength() + ", Actual: "
                                + tempFile.length());
            }

            return tempFile;
        }, new NetUtilsClasses.MainThreadResponseResult<>(new NetUtilsClasses.ResponseResult<File>() {
            @Override
            public void onFailure(Exception e) {
                result.onFailure(e);
            }

            @Override
            public void onSuccess(File res) {
                if (res != null) {
                    result.onSuccess(res);
                } else {
                    result.onFailure(new NullPointerException("File returned was null!"));
                }
            }
        }), progressListener, NetUtilsClasses.ONE_DAY_CACHE, 0);
    }

    /**
     * Request a bitmap without resizing.
     *
     * @param url    The request URL.
     * @param result The callback for this call.
     * @return An enqueued bitmap call. WILL RUN RESULT ON MAIN THREAD!
     */
    public static Call makeBitmapRequest(
            final HttpUrl url, @NonNull final NetUtilsClasses.BitmapResult result
    ) {
        return makeBitmapRequest(url, result, 0, 0);
    }

    /**
     * Request a bitmap with resizing.
     *
     * @param url    The request URL.
     * @param result The callback for this call.
     * @param width  The explicit width of the result
     * @param height The explicit height of the result
     * @return An enqueued bitmap call. WILL RUN RESULT ON MAIN THREAD!
     */
    public static Call makeBitmapRequest(
            final HttpUrl url, @NonNull final NetUtilsClasses.BitmapResult result, final int width, final int height
    ) {
        Pair<Call, Callback> ret = makeBitmapRequest(url, result, width, height, true);
        return ret == null ? null : ret.first;
    }

    /**
     * Request a bitmap with resizing.
     *
     * @param url    The request URL.
     * @param result The callback for this call.
     * @param width  The requested width of the result
     * @param height The requested height of the result
     * @return An enqueued bitmap call. WILL RUN RESULT ON MAIN THREAD!
     */
    public static Pair<Call, Callback> makeBitmapRequest(
            final HttpUrl url,
            @NonNull final NetUtilsClasses.BitmapResult result,
            final int width,
            final int height,
            boolean enqueue
    ) {
        if (url == null) return null;
        synchronized (NetUtils.class) {
            List<NetUtilsClasses.BitmapResult> results = resultListeners.get(url);
            if (results != null) {
                results.add(result);
                return null;
            } else {
                List<NetUtilsClasses.BitmapResult> listeners = new ArrayList<>();
                listeners.add(result);
                resultListeners.put(url, listeners);
            }
        }
        Bitmap cachedBitmap = imageCache.get(url);
        if (cachedBitmap != null) {
            performBitmapSuccess(url, cachedBitmap);
            return null;
        }
        Call call = applicationClient.getHttpRedirectClient()
                .newCall(new Request.Builder().url(url).addHeader("Referer", url.toString()).build());
        Callback callback = new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                if (!"Canceled".equals(e.getMessage())) {
                    Logger.e("NetUtils", "Error loading bitmap from " + url.toString());
                    performBitmapFailure(url, e);
                    return;
                }
                synchronized (NetUtils.class) {
                    resultListeners.remove(url);
                }
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (!response.isSuccessful()) {
                    performBitmapFailure(url, new NetUtilsClasses.HttpCodeException(response));
                    response.close();
                    return;
                }

                try (ResponseBody body = response.body()) {
                    if (body == null) {
                        performBitmapFailure(url, new NullPointerException("No response data"));
                        return;
                    }
                    String fileExtension = StringUtils.extractFileNameExtension(url.toString());
                    if (fileExtension != null && fileExtension.equalsIgnoreCase("webm")) {
                        File tempFile = new File(getCacheDir(), UUID.randomUUID().toString());
                        if (!tempFile.createNewFile()) {
                            tempFile.delete();
                            performBitmapFailure(url, new IOException("Failed to create temp file for decode."));
                        }
                        IOUtils.writeToFile(body.byteStream(), tempFile, -1);
                        BitmapUtils.decodeFilePreviewImage(tempFile, 0, 0, bitmap -> {
                            //noinspection ResultOfMethodCallIgnored
                            tempFile.delete();
                            checkBitmap(url, bitmap);
                        }, false);
                    } else {
                        ExceptionCatchingInputStream wrappedStream =
                                new ExceptionCatchingInputStream(body.byteStream());
                        Bitmap result = BitmapUtils.decode(wrappedStream, width, height);
                        if (wrappedStream.getException() != null) {
                            performBitmapFailure(url, wrappedStream.getException());
                            return;
                        }
                        checkBitmap(url, result);
                    }
                } catch (Exception e) {
                    performBitmapFailure(url, e);
                } catch (OutOfMemoryError e) {
                    getRuntime().gc();
                    performBitmapFailure(url, new IOException(e));
                }
            }
        };
        if (enqueue) {
            call.enqueue(callback);
        }
        return new Pair<>(call, callback);
    }

    private static void checkBitmap(HttpUrl url, Bitmap result) {
        if (result == null) {
            performBitmapFailure(url, new NullPointerException("Bitmap returned is null"));
            return;
        }
        imageCache.put(url, result);
        performBitmapSuccess(url, result);
    }

    private static synchronized void performBitmapSuccess(
            @NonNull final HttpUrl url, @NonNull Bitmap bitmap
    ) {
        final List<NetUtilsClasses.BitmapResult> results = resultListeners.remove(url);
        if (results == null) return;
        for (final NetUtilsClasses.BitmapResult bitmapResult : results) {
            if (bitmapResult == null) continue;
            BackgroundUtils.runOnMainThread(() -> bitmapResult.onBitmapSuccess(url, bitmap));
        }
    }

    private static synchronized void performBitmapFailure(@NonNull final HttpUrl url, Exception e) {
        final List<NetUtilsClasses.BitmapResult> results = resultListeners.remove(url);
        if (results == null) return;
        for (final NetUtilsClasses.BitmapResult bitmapResult : results) {
            if (bitmapResult == null) continue;
            BackgroundUtils.runOnMainThread(() -> bitmapResult.onBitmapFailure(url, e));
        }
    }

    /**
     * Request some JSON, no timeout.
     *
     * @param url          The request URL.
     * @param result       The callback for this call.
     * @param cacheControl Set cache parameters for this request
     * @param <T>          Your type
     * @return An enqueued JSON call. WILL RUN RESULT ON BACKGROUND OKHTTP THREAD!
     */
    public static <T> Call makeJsonRequest(
            @NonNull final HttpUrl url,
            @NonNull final NetUtilsClasses.ResponseResult<T> result,
            @NonNull final NetUtilsClasses.Converter<T, JsonReader> reader,
            @Nullable final CacheControl cacheControl
    ) {
        return makeJsonRequest(url, result, reader, cacheControl, 0);
    }

    /**
     * Request some JSON, with a timeout.
     *
     * @param url          The request URL.
     * @param result       The callback for this call.
     * @param cacheControl Set cache parameters for this request
     * @param timeoutMs    Optional timeout in milliseconds
     * @param <T>          Your type
     * @return An enqueued JSON call. WILL RUN RESULT ON BACKGROUND OKHTTP THREAD!
     */
    public static <T> Call makeJsonRequest(
            @NonNull final HttpUrl url,
            @NonNull final NetUtilsClasses.ResponseResult<T> result,
            @NonNull final NetUtilsClasses.Converter<T, JsonReader> reader,
            @Nullable final CacheControl cacheControl,
            int timeoutMs
    ) {
        return makeRequest(applicationClient,
                url,
                new NetUtilsClasses.ChainConverter<>(reader).chain(NetUtilsClasses.JSON_CONVERTER),
                result,
                null,
                cacheControl,
                timeoutMs
        );
    }

    /**
     * Request some HTML, no timeout.
     *
     * @param url          The request URL.
     * @param result       The callback for this call.
     * @param reader       The reader that will process the response into your result
     * @param cacheControl Set cache parameters for this request
     * @param <T>          Your type
     * @return An enqueued HTML call. WILL RUN RESULT ON BACKGROUND OKHTTP THREAD!
     */
    @SuppressWarnings("UnusedReturnValue")
    public static <T> Call makeHTMLRequest(
            @NonNull final HttpUrl url,
            @NonNull final NetUtilsClasses.ResponseResult<T> result,
            @NonNull final NetUtilsClasses.Converter<T, Document> reader,
            @Nullable final CacheControl cacheControl
    ) {
        return makeRequest(applicationClient,
                url,
                new NetUtilsClasses.ChainConverter<>(reader).chain(NetUtilsClasses.HTML_CONVERTER),
                result,
                null,
                cacheControl,
                0
        );
    }

    /**
     * Request something, no timeout.
     *
     * @param url          The request URL.
     * @param converter    The converter for the response.
     * @param result       The callback for this call.
     * @param cacheControl Set cache parameters for this request
     * @param <T>          Your result type, the something you're requesting
     * @return An enequeued call. WILL RUN RESULT ON BACKGROUND OKHTTP THREAD!
     */
    public static <T> Call makeRequest(
            @NonNull final OkHttpClient client,
            @NonNull final HttpUrl url,
            @NonNull final NetUtilsClasses.Converter<T, Response> converter,
            @NonNull final NetUtilsClasses.ResponseResult<T> result,
            @Nullable final ProgressResponseBody.ProgressListener progressListener,
            @Nullable final CacheControl cacheControl
    ) {
        return makeRequest(client, url, converter, result, progressListener, cacheControl, 0);
    }

    /**
     * Request something, timeout.
     *
     * @param url          The request URL.
     * @param converter    The converter for the response.
     * @param result       The callback for this call.
     * @param cacheControl Set cache parameters for this request
     * @param timeoutMs    Optional timeout in milliseconds
     * @param <T>          Your result type, the something you're requesting
     * @return An enequeued call. WILL RUN RESULT ON BACKGROUND OKHTTP THREAD!
     */
    private static <T> Call makeRequest(
            @NonNull final OkHttpClient client,
            @NonNull final HttpUrl url,
            @NonNull final NetUtilsClasses.Converter<T, Response> converter,
            @NonNull final NetUtilsClasses.ResponseResult<T> result,
            @Nullable final ProgressResponseBody.ProgressListener progressListener,
            @Nullable final CacheControl cacheControl,
            int timeoutMs
    ) {
        return makeCall(client, url, converter, result, progressListener, cacheControl, timeoutMs, true).first;
    }

    /**
     * This is the mothership of this class mostly, it does all the heavy lifting for you once provided the proper stuff
     * Generally don't use this! Use one of the wrapper methods instead.
     *
     * @param url              The request URL.
     * @param converter        The converter that will convert the response into a form the reader can process.
     * @param result           The callback for this call.
     * @param progressListener An optional progress listener for this response
     * @param <T>              Your result type
     * @param cacheControl     Set cache parameters for this request
     * @param timeoutMs        Optional timeout in milliseconds
     * @param enqueue          whether or not to enqueue this call as a step
     * @return An optionally enqueued call along with the callback it is associated with. WILL RUN RESULT ON BACKGROUND OKHTTP THREAD!
     */
    public static <T> Pair<Call, Callback> makeCall(
            @NonNull OkHttpClient client,
            @NonNull final HttpUrl url,
            @NonNull final NetUtilsClasses.Converter<T, Response> converter,
            @NonNull final NetUtilsClasses.ResponseResult<T> result,
            @Nullable final ProgressResponseBody.ProgressListener progressListener,
            @Nullable final CacheControl cacheControl,
            int timeoutMs,
            boolean enqueue
    ) {
        OkHttpClient.Builder clientBuilder = client.newBuilder();
        clientBuilder.callTimeout(timeoutMs, TimeUnit.MILLISECONDS);
        clientBuilder.addNetworkInterceptor(chain -> {
            Response originalResponse = chain.proceed(chain.request());
            return originalResponse.newBuilder()
                    .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                    .build();
        });
        Request.Builder builder = new Request.Builder().url(url).addHeader("Referer", url.toString());
        if (cacheControl != null) {
            builder.cacheControl(cacheControl);
        }
        Call call = clientBuilder.build().newCall(builder.build());
        Callback callback = new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                result.onFailure(e);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (!response.isSuccessful()) {
                    result.onFailure(new NetUtilsClasses.HttpCodeException(response));
                    response.close();
                    return;
                }

                try {
                    T read = converter.convert(response);
                    if (read == null) throw new NullPointerException("Process returned null!");
                    result.onSuccess(read);
                } catch (Exception e) {
                    result.onFailure(e);
                } finally {
                    response.close();
                }
            }
        };
        if (enqueue) {
            call.enqueue(callback);
        }
        return new Pair<>(call, callback);
    }

    /**
     * @param url    The request URL.
     * @param result The callback for this call.
     * @return An enqueued headers call. WILL RUN RESULT ON BACKGROUND THREAD!
     */
    public static Call makeHeadersRequest(
            @NonNull final HttpUrl url, @NonNull final NetUtilsClasses.ResponseResult<Headers> result
    ) {
        Call call = applicationClient.newCall(new Request.Builder().url(url).head().build());
        NetUtilsClasses.BackgroundThreadResponseResult<Headers> wrap =
                new NetUtilsClasses.BackgroundThreadResponseResult<>(result);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                wrap.onFailure(e);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (!response.isSuccessful()) {
                    wrap.onFailure(new NetUtilsClasses.HttpCodeException(response));
                } else {
                    wrap.onSuccess(response.headers());
                }
                response.close();
            }
        });
        return call;
    }
}
