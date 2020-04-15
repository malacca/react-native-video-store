package com.malacca.videostore;

import java.net.URL;
import java.net.HttpURLConnection;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.util.concurrent.ExecutorService;

import com.danikula.videocache.HttpProxyCacheServer;

public class VideoStoreTask implements Runnable{
    interface TaskCallback {
        void resolve(String state);
        void reject(Throwable throwable);
    }

    private HttpProxyCacheServer cacheServer;
    private String preloadUrl;
    private float preloadSize;
    private TaskCallback taskCallback;
    private boolean isExecuted = false;
    private boolean isCanceled = false;

    VideoStoreTask(HttpProxyCacheServer server, String url, float size, TaskCallback callback) {
        cacheServer = server;
        preloadUrl = url;
        preloadSize = size;
        taskCallback = callback;
    }

    @Override
    public void run() {
        HttpURLConnection connection = null;
        try {
            String proxyUrl = cacheServer.getProxyUrl(preloadUrl);
            URL url = new URL(proxyUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(5_000);
            InputStream in = new BufferedInputStream(connection.getInputStream());
            int length;
            int read = -1;
            byte[] bytes = new byte[8 * 1024];
            boolean breakLoad = false;
            while ((length = in.read(bytes)) != -1) {
                read += length;
                //预加载完成或者取消预加载
                if (isCanceled || (preloadSize != 0f && read >= preloadSize)) {
                    breakLoad = true;
                    taskCallback.resolve(isCanceled ? "canceled" : "preloaded");
                    break;
                }
            }
            if (read == -1) {
                //预加载出错，删掉缓存
                File cacheFile = cacheServer.getCacheFile(preloadUrl);
                if (cacheFile.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    cacheFile.delete();
                }
                taskCallback.reject(new IOException("read preDownload file failed"));
            } else if (!breakLoad) {
                // 完整加载
                taskCallback.resolve("cached");
            }
        } catch (Exception e) {
            taskCallback.reject(e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // 开始预加载
    VideoStoreTask start(ExecutorService executorService) {
        if (!isExecuted) {
            isExecuted = true;
            executorService.submit(this);
        }
        return this;
    }

    // 结束预加载
    void stop() {
        if (isExecuted) {
            isCanceled = true;
        }
    }
}
