package com.malacca.videostore;

import java.io.File;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import android.net.Uri;
import android.util.SparseArray;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.danikula.videocache.Logger;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.ReactContextBaseJavaModule;

import com.danikula.videocache.ProxyCacheUtils;
import com.danikula.videocache.file.FileNameGenerator;
import com.danikula.videocache.headers.CustomHeaders;
import com.danikula.videocache.headers.HeaderInjector;
import com.danikula.videocache.HttpProxyCacheServer;

public class VideoStoreModule extends ReactContextBaseJavaModule {
    private HttpProxyCacheServer.Builder cacheBuilder;
    private HttpProxyCacheServer httpProxyCacheServer;
    private Map<String, CustomHeaders> httpRequestHeaders;
    private CustomHeaders defaultCustomHeader;
    private boolean disableSystemUserAgent;
    private ExecutorService mExecutorService = Executors.newSingleThreadExecutor();
    private SparseArray<VideoStoreTask> preloadTasks = new SparseArray<>();

    class PreloadTaskCallback implements VideoStoreTask.TaskCallback {
        private int taskId;
        private Promise reactPromise;

        PreloadTaskCallback(int id, Promise promise) {
            taskId = id;
            reactPromise = promise;
        }

        @Override
        public void resolve(String state) {
            // 手动取消, 已在取消前 clearTask 了
            if (!"canceled".equals(state)) {
                clearTask();
            }
            reactPromise.resolve(state);
        }

        @Override
        public void reject(Throwable throwable) {
            clearTask();
            reactPromise.reject(throwable);
        }

        private void clearTask() {
            preloadTasks.remove(taskId);
        }
    }

    static class Md5NameGenerator implements FileNameGenerator {
        @Override
        public String generate(String url) {
            String id = Uri.parse(url).getQueryParameter("_l");
            id = id == null ? url : id;
            return ProxyCacheUtils.computeMD5(id);
        }
    }

    class CustomHeaderInjector implements HeaderInjector {
        @Override
        public CustomHeaders addHeaders(String url) {
            CustomHeaders customHeaders = getCustomHeaders(url);
            if (!disableSystemUserAgent) {
                customHeaders.headers.put("User-Agent", System.getProperty("http.agent"));
            }
            return customHeaders;
        }

        private CustomHeaders getCustomHeaders(String url) {
            String host = Uri.parse(url).getHost();
            if (httpRequestHeaders != null && host != null) {
                for (String key: httpRequestHeaders.keySet()) {
                    if (host.endsWith(key)) {
                        return httpRequestHeaders.get(key);
                    }
                }
            }
            return defaultCustomHeader;
        }
    }

    VideoStoreModule(ReactApplicationContext reactContext) {
        super(reactContext);
        Logger.enableDebug(BuildConfig.DEBUG);
        cacheBuilder = new HttpProxyCacheServer.Builder(reactContext)
            .fileNameGenerator(new Md5NameGenerator());
        defaultCustomHeader = makeCustomHeaders(
                new HashMap<String, String>(),
                true
        );
    }

    @NonNull
    @Override
    public String getName() {
        return "RNVideoStore";
    }

    @ReactMethod
    public void setMaxSize(long maxSize) {
        if (cacheBuilder != null) {
            cacheBuilder.maxCacheSize(1024 * maxSize);
        }
    }

    @ReactMethod
    public void setMaxFiles(int count) {
        if (cacheBuilder != null) {
            cacheBuilder.maxCacheFilesCount(count);
        }
    }

    @ReactMethod
    public void setCacheDirectory(String directory) {
        if (cacheBuilder != null) {
            cacheBuilder.cacheDirectory(new File(directory));
        }
    }

    @ReactMethod
    public void disableSystemUA(boolean enable) {
        disableSystemUserAgent = enable;
    }

    @ReactMethod
    public void setDefaultHeaders(ReadableMap headers, boolean applyRedirect) {
        defaultCustomHeader = makeCustomHeaders(headers, applyRedirect);
    }

    @ReactMethod
    public void setDomainHeaders(String host, ReadableMap headers, boolean applyRedirect) {
        if (httpRequestHeaders == null) {
            httpRequestHeaders = new HashMap<>();
        }
        httpRequestHeaders.put(host, makeCustomHeaders(headers, applyRedirect));
    }

    private static CustomHeaders makeCustomHeaders(ReadableMap headers, boolean applyRedirect) {
        Map<String, String> headerMap = new HashMap<>();
        ReadableMapKeySetIterator headerIterator = headers.keySetIterator();
        while (headerIterator.hasNextKey()) {
            String key = headerIterator.nextKey();
            headerMap.put(key, headers.getString(key));
        }
        return makeCustomHeaders(headerMap, applyRedirect);
    }

    private static CustomHeaders makeCustomHeaders(Map<String, String> headers, boolean applyRedirect) {
        CustomHeaders customHeaders = new CustomHeaders();
        customHeaders.headers = headers;
        customHeaders.applyRedirect = applyRedirect;
        return customHeaders;
    }

    /**
     * 创建 HTTP Proxy Server
     */
    private HttpProxyCacheServer getHttpProxyCacheServer() {
        if (httpProxyCacheServer == null) {
            httpProxyCacheServer = cacheBuilder.headerInjector(new CustomHeaderInjector()).build();
            cacheBuilder = null;
        }
        return httpProxyCacheServer;
    }

    // 获取指定 url 的代理 url
    @ReactMethod
    public void getProxyUrl(String url, boolean allowFileUri, final Promise promise) {
        if (url == null || url.isEmpty()) {
            promise.reject(new IllegalArgumentException("url is empty"));
            return;
        }
        try {
            promise.resolve(getHttpProxyCacheServer().getProxyUrl(url, allowFileUri));
        } catch (Throwable e) {
            promise.reject(e);
        }
    }

    // 预加载指定 URL
    @ReactMethod
    public void startPreload(int id, String url, float preloadSize, Promise promise) {
        if (url == null || url.isEmpty()) {
            promise.reject(new IllegalArgumentException("url is empty"));
            return;
        }
        preloadSize = 1024 * preloadSize;
        try {
            String state = preloadState(url, preloadSize);
            if (state != null) {
                promise.resolve(state);
            } else {
                preloadTasks.put(id, new VideoStoreTask(
                        getHttpProxyCacheServer(),
                        url,
                        preloadSize,
                        new PreloadTaskCallback(id, promise)
                ).start(mExecutorService));
            }
        } catch (Throwable e) {
            promise.reject(e);
        }
    }

    // 是否已加载
    @Nullable
    private String preloadState(String url, float preloadSize) {
        // 超过 1KB, 认为已缓存
        File cacheFile = getHttpProxyCacheServer().getCacheFile(url);
        if (cacheFile.exists()) {
            if (cacheFile.length() >= 1024) {
                return "cached";
            } else {
                //noinspection ResultOfMethodCallIgnored
                cacheFile.delete();
                return null;
            }
        }
        // 要完整预加载的情况, 不判断缓存文件
        if (preloadSize == 0f) {
            return null;
        }
        // 加载一半就中断的情况, 会保存为缓存文件, 确认是否已大于 preloadSize
        File tmpFile = new File(cacheFile.getAbsoluteFile() + ".download");
        return tmpFile.exists() && tmpFile.length() >= preloadSize ? "preloaded" : null;
    }

    // 取消预加载
    @ReactMethod
    public void stopPreload(int id) {
        VideoStoreTask task = preloadTasks.get(id);
        if (task != null) {
            preloadTasks.remove(id);
            task.stop();
        }
    }

    // 取消所有预加载
    @ReactMethod
    public void stopAllPreload() {
        try {
            for (int i = 0; i < preloadTasks.size(); i++) {
                preloadTasks.valueAt(i).stop();
                preloadTasks.removeAt(i);
            }
        } catch (Throwable ignored) {
        }
    }

    // 获取当前总缓存 size
    @ReactMethod
    public void getCacheSize(final Promise promise) {
        new Thread() {
            @Override
            public void run() {
                promise.resolve(getFilesSize(getHttpProxyCacheServer().getCacheRoot()));
            }
        }.start();
    }

    // 删除指定 URL 的缓存
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @ReactMethod
    public void removeCache(String url) {
        if (url == null || url.isEmpty()) {
            return;
        }
        File cacheFile = getHttpProxyCacheServer().getCacheFile(url);
        if (cacheFile.exists() && cacheFile.isFile()) {
            cacheFile.delete();
        }
        File tmpFile = new File(cacheFile.getAbsoluteFile() + ".download");
        if (tmpFile.exists() && tmpFile.isFile()) {
            tmpFile.delete();
        }
    }

    // 删除所有缓存
    @ReactMethod
    public void clearCache() {
        new Thread() {
            @Override
            public void run() {
                deleteFiles(getHttpProxyCacheServer().getCacheRoot());
            }
        }.start();
    }

    /**
     * get directory size
     */
    private static float getFilesSize(File root) {
        float size = 0;
        File[] files = root.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) {
                    size += f.length();
                }
            }
        }
        return Math.round(size / 1024);
    }

    /**
     * delete directory
     */
    private static void deleteFiles(File root) {
        File[] files = root.listFiles();
        if (files != null) {
            for (File f : files) {
                if (!f.isDirectory() && f.exists()) { // 判断是否存在
                    if (!f.delete()) {
                        return;
                    }
                }
            }
        }
    }
}
