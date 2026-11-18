package top.genouka.xin.update;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 莘应用「应用内检查更新」客户端。
 *
 * <p>封装 {@code POST /api/app/update/check} 与 {@code POST /api/app/update/download}：
 * 使用开发者中心签发的 client_id / client_secret 识别应用，返回是否有新版本、
 * 最新版本信息与下载地址。所有方法为同步阻塞调用，Android 上请在工作线程执行，
 * 或直接使用 {@link XinUpdater} 的异步接口。
 *
 * <p>本类不依赖 Android SDK，可在 JVM 上直接测试；{@code org.json} 由 Android
 * 平台提供。
 */
public final class XinUpdateClient {

    /** 生产环境 API 基址（包含 {@code /api}）。 */
    public static final String DEFAULT_BASE_URL = "https://xinappapi.qiumingsanyu.top/api";

    /** SDK 版本，用于 User-Agent。 */
    public static final String VERSION = "1.0.0";

    private static final String PATH_CHECK = "/app/update/check";
    private static final String PATH_DOWNLOAD = "/app/update/download";
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private final String baseUrl;
    private final String clientId;
    private final String clientSecret;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final String userAgent;

    public XinUpdateClient(Config config) {
        if (config == null) {
            throw new IllegalArgumentException("config 不能为 null");
        }
        this.baseUrl = normalizeBaseUrl(config.getBaseUrl());
        this.clientId = requireText(config.getClientId(), "clientId");
        this.clientSecret = requireText(config.getClientSecret(), "clientSecret");
        this.connectTimeoutMs = requirePositive(config.getConnectTimeoutMs(), "connectTimeoutMs");
        this.readTimeoutMs = requirePositive(config.getReadTimeoutMs(), "readTimeoutMs");
        this.userAgent = config.getUserAgent();
    }

    public XinUpdateClient(String baseUrl, String clientId, String clientSecret) {
        this(Config.builder()
                .baseUrl(baseUrl)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build());
    }

    /** 检查更新。{@code current} 至少需要 versionCode 或 versionName 之一。 */
    public CheckResult check(CurrentVersion current) throws XinUpdateException {
        if (current == null) {
            throw new IllegalArgumentException("current 不能为 null");
        }
        if (current.getVersionCode() == null && current.getVersionName() == null) {
            throw new IllegalArgumentException("versionCode 与 versionName 至少提供一个");
        }

        JSONObject body = new JSONObject();
        putIfPresent(body, "version_code", current.getVersionCode());
        putIfPresent(body, "version_name", current.getVersionName());
        putIfPresent(body, "identifier", current.getIdentifier());

        return parseCheck(post(baseUrl + PATH_CHECK, body));
    }

    /** 检查更新，便于非 Android 调用方直接传值。 */
    public CheckResult check(String versionCode, String versionName, String identifier)
            throws XinUpdateException {
        return check(new CurrentVersion(versionCode, versionName, identifier));
    }

    /** 下载最新可用版本。 */
    public DownloadResult download() throws XinUpdateException {
        return download(null, null, null, null);
    }

    /**
     * 获取下载地址与候选列表。
     *
     * @param versionId  指定版本 ID；为 null 时由服务端选择最新可用版本
     * @param channel    渠道标识，例如 stable / beta；可为 null
     * @param deviceType 设备类型，例如 android_phone；为 null 时跳过设备兼容校验
     * @param deviceId   设备标识，用于下载统计与去重；可为 null
     */
    public DownloadResult download(Long versionId, String channel, String deviceType, String deviceId)
            throws XinUpdateException {
        return downloadInternal(baseUrl + PATH_DOWNLOAD, versionId, channel, deviceType, deviceId);
    }

    /** 使用检查更新响应中的 download_endpoint 获取下载地址。 */
    public DownloadResult downloadFor(CheckResult check, Long versionId, String channel,
                                      String deviceType, String deviceId) throws XinUpdateException {
        String endpoint = check == null ? null : check.getDownloadEndpoint();
        return downloadInternal(resolveEndpoint(endpoint == null ? PATH_DOWNLOAD : endpoint),
                versionId, channel, deviceType, deviceId);
    }

    private DownloadResult downloadInternal(String endpoint, Long versionId, String channel,
                                            String deviceType, String deviceId) throws XinUpdateException {
        if (versionId != null && versionId <= 0) {
            throw new IllegalArgumentException("versionId 必须为正数");
        }

        JSONObject body = new JSONObject();
        if (versionId != null) {
            putIfPresent(body, "version_id", versionId);
        }
        putIfPresent(body, "channel", channel);
        putIfPresent(body, "device_type", deviceType);
        putIfPresent(body, "device_id", deviceId);

        JSONObject root = post(endpoint, body);
        JSONObject data = root.optJSONObject("data");
        if (data == null) {
            throw new XinUpdateException("响应缺少 data");
        }

        String main = optString(data, "main");
        if (main == null) {
            throw new XinUpdateException("响应缺少下载地址");
        }

        List<DownloadCandidate> candidates = new ArrayList<>();
        JSONArray array = data.optJSONArray("candidates");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                candidates.add(new DownloadCandidate(
                        optString(item, "uri"),
                        optString(item, "type"),
                        optString(item, "resolved"),
                        optLongOrNull(item, "resource_id"),
                        optLongOrNull(item, "provider_id"),
                        optString(item, "provider_name")));
            }
        }

        VersionInfo version = null;
        JSONObject versionJson = data.optJSONObject("version");
        if (versionJson != null) {
            version = new VersionInfo(
                    optLong(versionJson, "id"),
                    optString(versionJson, "version_name"),
                    optString(versionJson, "version_code"));
        }

        return new DownloadResult(
                optLong(data, "download_id"),
                optString(data, "report_token"),
                main,
                candidates,
                optString(data, "channel"),
                optString(data, "identifier"),
                version,
                data.optBoolean("need_parse", false));
    }

    private CheckResult parseCheck(JSONObject root) {
        AppInfo app = null;
        JSONObject appJson = root.optJSONObject("app");
        if (appJson != null) {
            app = new AppInfo(
                    optLong(appJson, "id"),
                    optString(appJson, "name"),
                    optString(appJson, "identifier"));
        }

        LatestVersion latest = null;
        JSONObject latestJson = root.optJSONObject("latest");
        if (latestJson != null) {
            latest = new LatestVersion(
                    optLong(latestJson, "version_id"),
                    optString(latestJson, "version_name"),
                    optString(latestJson, "version_code"),
                    optString(latestJson, "changelog"),
                    optString(latestJson, "on_shelf_at"));
        }

        return new CheckResult(
                root.optBoolean("update_available", false),
                app,
                latest,
                optString(root, "download_endpoint"));
    }

    private JSONObject post(String endpoint, JSONObject body) throws XinUpdateException {
        String url = resolveEndpoint(endpoint);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("X-Client-Id", clientId);
            connection.setRequestProperty("X-Client-Secret", clientSecret);
            connection.setRequestProperty("User-Agent", userAgent);

            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(payload.length);
            connection.connect();

            try (OutputStream out = connection.getOutputStream()) {
                out.write(payload);
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String text = readBody(stream);
            JSONObject json = parseBody(text, status);

            if (status < 200 || status >= 300) {
                String message = optString(json, "message");
                if (message == null) {
                    message = "请求失败（HTTP " + status + "）";
                }
                throw new XinUpdateException(status, message);
            }

            return json;
        } catch (XinUpdateException e) {
            throw e;
        } catch (IOException e) {
            String detail = e.getMessage();
            throw new XinUpdateException(0,
                    detail == null || detail.isEmpty() ? "网络请求失败" : "网络请求失败：" + detail, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static JSONObject parseBody(String text, int status) throws XinUpdateException {
        if (text == null || text.trim().isEmpty()) {
            if (status >= 200 && status < 300) {
                throw new XinUpdateException(status, "响应为空");
            }
            return new JSONObject();
        }
        try {
            return new JSONObject(text);
        } catch (JSONException e) {
            // 非 2xx 时响应体可能不是 JSON（如网关错误页），保留状态码更有价值
            if (status >= 200 && status < 300) {
                throw new XinUpdateException(status, "响应格式异常", e);
            }
            return new JSONObject();
        }
    }

    private static String readBody(InputStream stream) throws IOException, XinUpdateException {
        if (stream == null) {
            return "";
        }
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new XinUpdateException("响应过大");
                }
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private String resolveEndpoint(String endpoint) {
        String value = trimToNull(endpoint);
        if (value == null) {
            return baseUrl + PATH_DOWNLOAD;
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        if (value.startsWith("/")) {
            try {
                URL base = new URL(baseUrl);
                return base.getProtocol() + "://" + base.getAuthority() + value;
            } catch (IOException e) {
                throw new IllegalStateException("baseUrl 无效", e);
            }
        }
        return baseUrl + "/" + value;
    }

    private static String normalizeBaseUrl(String value) {
        String base = trimToNull(value);
        if (base == null) {
            base = DEFAULT_BASE_URL;
        }
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            throw new IllegalArgumentException("baseUrl 必须以 http:// 或 https:// 开头");
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private static String requireText(String value, String name) {
        String text = trimToNull(value);
        if (text == null) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return text;
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " 必须大于 0");
        }
        return value;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void putIfPresent(JSONObject body, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String && ((String) value).trim().isEmpty()) {
            return;
        }
        try {
            body.put(key, value);
        } catch (JSONException e) {
            // 键和值均由 SDK 控制，正常不会发生
            throw new IllegalStateException(e);
        }
    }

    private static String optString(JSONObject object, String key) {
        Object value = object.opt(key);
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        return String.valueOf(value);
    }

    private static long optLong(JSONObject object, String key) {
        Long value = optLongOrNull(object, key);
        return value == null ? 0L : value;
    }

    private static Long optLongOrNull(JSONObject object, String key) {
        Object value = object.opt(key);
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 客户端配置。 */
    public static final class Config {

        private final String baseUrl;
        private final String clientId;
        private final String clientSecret;
        private final int connectTimeoutMs;
        private final int readTimeoutMs;
        private final String userAgent;

        private Config(Builder builder) {
            this.baseUrl = builder.baseUrl == null ? DEFAULT_BASE_URL : builder.baseUrl;
            this.clientId = builder.clientId;
            this.clientSecret = builder.clientSecret;
            this.connectTimeoutMs = builder.connectTimeoutMs;
            this.readTimeoutMs = builder.readTimeoutMs;
            this.userAgent = builder.userAgent == null
                    ? "XinUpdateSdk/" + VERSION + " (Android)"
                    : builder.userAgent;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public String getClientId() {
            return clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public String getUserAgent() {
            return userAgent;
        }

        /** 配置构造器；clientId / clientSecret 必填。 */
        public static final class Builder {

            private String baseUrl;
            private String clientId;
            private String clientSecret;
            private int connectTimeoutMs = 10000;
            private int readTimeoutMs = 15000;
            private String userAgent;

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

            public Builder userAgent(String userAgent) {
                this.userAgent = userAgent;
                return this;
            }

            public Config build() {
                return new Config(this);
            }
        }
    }
}
