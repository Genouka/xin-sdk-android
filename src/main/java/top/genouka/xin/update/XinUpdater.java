package top.genouka.xin.update;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Android 便捷封装：自动读取当前包名/版本，提供主线程回调的异步接口。
 *
 * <p>典型用法：
 * <pre>{@code
 * XinUpdater updater = XinUpdater.builder(context)
 *         .clientId("xin_...")
 *         .clientSecret("...")
 *         .build();
 * updater.check(new XinUpdater.Callback() {
 *     public void onSuccess(CheckResult result) {
 *         if (result.isUpdateAvailable()) {
 *             // 展示 result.getLatest().getVersionName() / getChangelog()
 *         }
 *     }
 *     public void onError(XinUpdateException error) {
 *         // 401：凭据失效；429：退避重试；其余按网络错误处理
 *     }
 * });
 * }</pre>
 *
 * <p>同步方法 {@link #checkNow()} / {@link #downloadNow(Long, String, String, String)}
 * 禁止在主线程调用。使用完毕后可调用 {@link #shutdown()} 释放线程池。
 */
public final class XinUpdater {

    /** 从 AndroidManifest 读取 client_id 的 meta-data 键。 */
    public static final String META_CLIENT_ID = "top.genouka.xin.update.CLIENT_ID";
    /** 从 AndroidManifest 读取 client_secret 的 meta-data 键。 */
    public static final String META_CLIENT_SECRET = "top.genouka.xin.update.CLIENT_SECRET";
    /** 从 AndroidManifest 覆盖 API 基址的 meta-data 键（可选）。 */
    public static final String META_BASE_URL = "top.genouka.xin.update.BASE_URL";

    private final Context context;
    private final XinUpdateClient client;
    private final CurrentVersion currentVersion;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private XinUpdater(Builder builder) {
        this.context = builder.context;
        this.client = builder.client;
        this.currentVersion = builder.currentVersion;
        this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "xin-update-sdk");
                thread.setDaemon(true);
                return thread;
            }
        });
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public static Builder builder(Context context) {
        return new Builder(context);
    }

    /**
     * 使用 AndroidManifest 中的 meta-data 创建实例。
     *
     * <pre>{@code
     * <meta-data android:name="top.genouka.xin.update.CLIENT_ID" android:value="xin_..." />
     * <meta-data android:name="top.genouka.xin.update.CLIENT_SECRET" android:value="..." />
     * }</pre>
     *
     * @throws IllegalStateException 缺少必填 meta-data
     */
    public static XinUpdater fromManifest(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context 不能为 null");
        }
        Bundle meta = readManifestMeta(context);
        String clientId = meta == null ? null : meta.getString(META_CLIENT_ID);
        String clientSecret = meta == null ? null : meta.getString(META_CLIENT_SECRET);
        String baseUrl = meta == null ? null : meta.getString(META_BASE_URL);

        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalStateException("AndroidManifest 缺少 " + META_CLIENT_ID);
        }
        if (clientSecret == null || clientSecret.trim().isEmpty()) {
            throw new IllegalStateException("AndroidManifest 缺少 " + META_CLIENT_SECRET);
        }

        Builder builder = builder(context)
                .clientId(clientId)
                .clientSecret(clientSecret);
        if (baseUrl != null && !baseUrl.trim().isEmpty()) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }

    /** 同步检查更新；禁止在主线程调用。 */
    public CheckResult checkNow() throws XinUpdateException {
        assertNotMainThread("checkNow");
        return client.check(currentVersion);
    }

    /** 异步检查更新，回调在主线程执行。 */
    public void check(final Callback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback 不能为 null");
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final CheckResult result = client.check(currentVersion);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(result);
                        }
                    });
                } catch (final XinUpdateException e) {
                    postError(callback, e);
                } catch (final RuntimeException e) {
                    postError(callback, new XinUpdateException(0, safeMessage(e), e));
                }
            }
        });
    }

    /** 同步获取下载地址；禁止在主线程调用。 */
    public DownloadResult downloadNow(Long versionId, String channel, String deviceType, String deviceId)
            throws XinUpdateException {
        assertNotMainThread("downloadNow");
        return client.download(versionId, channel, deviceType, deviceId);
    }

    /** 异步获取下载地址，回调在主线程执行。 */
    public void download(final Long versionId, final String channel, final String deviceType,
                         final String deviceId, final DownloadCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback 不能为 null");
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final DownloadResult result = client.download(versionId, channel, deviceType, deviceId);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(result);
                        }
                    });
                } catch (final XinUpdateException e) {
                    postDownloadError(callback, e);
                } catch (final RuntimeException e) {
                    postDownloadError(callback, new XinUpdateException(0, safeMessage(e), e));
                }
            }
        });
    }

    /** 释放内部线程池；之后不再接受异步任务。 */
    public void shutdown() {
        executor.shutdownNow();
    }

    public CurrentVersion getCurrentVersion() {
        return currentVersion;
    }

    /** 底层同步客户端，可在自有线程模型中使用。 */
    public XinUpdateClient getClient() {
        return client;
    }

    /**
     * 读取当前应用的包名、versionCode、versionName。
     *
     * @throws IllegalStateException 无法读取自身包信息
     */
    public static CurrentVersion readCurrentVersion(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context 不能为 null");
        }
        String packageName = context.getPackageName();
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            String versionCode;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                versionCode = Long.toString(info.getLongVersionCode());
            } else {
                versionCode = Integer.toString(info.versionCode);
            }
            return new CurrentVersion(versionCode, info.versionName, packageName);
        } catch (PackageManager.NameNotFoundException e) {
            throw new IllegalStateException("无法读取当前应用版本信息", e);
        }
    }

    private static Bundle readManifestMeta(Context context) {
        try {
            ApplicationInfo info = context.getPackageManager()
                    .getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            return info.metaData;
        } catch (PackageManager.NameNotFoundException e) {
            throw new IllegalStateException("无法读取当前应用清单", e);
        }
    }

    private void postError(final Callback callback, final XinUpdateException error) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onError(error);
            }
        });
    }

    private void postDownloadError(final DownloadCallback callback, final XinUpdateException error) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onError(error);
            }
        });
    }

    private static void assertNotMainThread(String method) {
        Looper main = Looper.getMainLooper();
        if (main != null && Looper.myLooper() == main) {
            throw new IllegalStateException(method + " 不能在主线程调用，请使用异步接口");
        }
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isEmpty() ? throwable.getClass().getSimpleName() : message;
    }

    /** 检查更新回调，均在主线程触发。 */
    public interface Callback {
        void onSuccess(CheckResult result);

        void onError(XinUpdateException error);
    }

    /** 下载地址解析回调，均在主线程触发。 */
    public interface DownloadCallback {
        void onSuccess(DownloadResult result);

        void onError(XinUpdateException error);
    }

    /** 构造器。 */
    public static final class Builder {

        private final Context context;
        private String baseUrl;
        private String clientId;
        private String clientSecret;
        private int connectTimeoutMs = 10000;
        private int readTimeoutMs = 15000;
        private CurrentVersion currentVersion;
        private XinUpdateClient client;

        private Builder(Context context) {
            if (context == null) {
                throw new IllegalArgumentException("context 不能为 null");
            }
            this.context = context.getApplicationContext() == null
                    ? context
                    : context.getApplicationContext();
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder clientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        public Builder connectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
            return this;
        }

        public Builder readTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
            return this;
        }

        /** 覆盖自动读取的当前版本（默认读取自身包信息）。 */
        public Builder currentVersion(CurrentVersion currentVersion) {
            this.currentVersion = currentVersion;
            return this;
        }

        /** 复用已构造的同步客户端（便于测试或共享连接配置）。 */
        public Builder client(XinUpdateClient client) {
            this.client = client;
            return this;
        }

        public XinUpdater build() {
            if (client == null) {
                XinUpdateClient.Config.Builder config = XinUpdateClient.Config.builder()
                        .clientId(clientId)
                        .clientSecret(clientSecret)
                        .connectTimeoutMs(connectTimeoutMs)
                        .readTimeoutMs(readTimeoutMs);
                if (baseUrl != null && !baseUrl.trim().isEmpty()) {
                    config.baseUrl(baseUrl);
                }
                client = new XinUpdateClient(config.build());
            }
            if (currentVersion == null) {
                currentVersion = readCurrentVersion(context);
            }
            return new XinUpdater(this);
        }
    }
}
