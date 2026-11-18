package top.genouka.xin.update;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * SDK 与真实 HTTP 服务端的往返测试：请求头/请求体、响应解析、错误映射。
 */
public class XinUpdateClientTest {

    private TestHttpServer server;
    private String baseUrl;
    private volatile Route route;
    private volatile String requestMethod;
    private volatile String requestPath;
    private volatile String requestBody;
    private volatile String requestClientId;
    private volatile String requestClientSecret;
    private volatile String requestContentType;

    @Before
    public void setUp() throws Exception {
        server = new TestHttpServer();
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getPort() + "/api";
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void checkParsesUpdateAndSendsCredentials() throws Exception {
        route = new Route(200, "{\n"
                + "  \"update_available\": true,\n"
                + "  \"app\": {\"id\": 12, \"name\": \"示例应用\", \"identifier\": \"com.example.app\"},\n"
                + "  \"latest\": {\n"
                + "    \"version_id\": 88,\n"
                + "    \"version_name\": \"1.5.0\",\n"
                + "    \"version_code\": \"150\",\n"
                + "    \"changelog\": \"修复若干问题\",\n"
                + "    \"on_shelf_at\": \"2026-11-01T08:30:00+08:00\"\n"
                + "  },\n"
                + "  \"download_endpoint\": \"/api/app/update/download\"\n"
                + "}");

        CheckResult result = client("xin_test", "secret")
                .check(new CurrentVersion("140", "1.4.0", "com.example.app"));

        assertTrue(result.isUpdateAvailable());
        assertEquals(12, result.getApp().getId());
        assertEquals("com.example.app", result.getApp().getIdentifier());
        assertEquals(88, result.getLatest().getVersionId());
        assertEquals("1.5.0", result.getLatest().getVersionName());
        assertEquals("修复若干问题", result.getLatest().getChangelog());
        assertEquals("/api/app/update/download", result.getDownloadEndpoint());

        assertEquals("POST", requestMethod);
        assertEquals("/api/app/update/check", requestPath);
        assertEquals("xin_test", requestClientId);
        assertEquals("secret", requestClientSecret);
        assertTrue(requestContentType.startsWith("application/json"));
        JSONObject body = new JSONObject(requestBody);
        assertEquals("140", body.getString("version_code"));
        assertEquals("1.4.0", body.getString("version_name"));
        assertEquals("com.example.app", body.getString("identifier"));
    }

    @Test
    public void checkSupportsNoUpdateAndNullLatest() throws Exception {
        route = new Route(200, "{\"update_available\":false,\"app\":{\"id\":12},\"latest\":null}");

        CheckResult result = client("xin_test", "secret").check("150", null, null);

        assertFalse(result.isUpdateAvailable());
        assertNull(result.getLatest());
        assertNull(result.getDownloadEndpoint());
        JSONObject body = new JSONObject(requestBody);
        assertEquals("150", body.getString("version_code"));
        assertFalse(body.has("version_name"));
    }

    @Test
    public void checkMapsUnauthorizedStatus() {
        route = new Route(401, "{\"message\":\"客户端凭据无效\"}");

        try {
            client("xin_test", "bad").check("140", "1.4.0", null);
            fail("expected XinUpdateException");
        } catch (XinUpdateException e) {
            assertEquals(401, e.getHttpStatus());
            assertTrue(e.isUnauthorized());
            // JDK 的 HttpURLConnection 对 401 可能不提供 error stream，至少保留状态码
            assertTrue(e.getMessage().length() > 0);
        }
    }

    @Test
    public void checkParsesBusinessErrorMessage() {
        route = new Route(422, "{\"message\":\"当前状态不允许操作\"}");

        try {
            client("xin_test", "secret").check("140", "1.4.0", null);
            fail("expected XinUpdateException");
        } catch (XinUpdateException e) {
            assertEquals(422, e.getHttpStatus());
            assertEquals("当前状态不允许操作", e.getMessage());
        }
    }

    @Test
    public void checkKeepsStatusWhenErrorBodyIsNotJson() {
        route = new Route(429, "<html>too many requests</html>");

        try {
            client("xin_test", "secret").check("140", "1.4.0", null);
            fail("expected XinUpdateException");
        } catch (XinUpdateException e) {
            assertEquals(429, e.getHttpStatus());
            assertTrue(e.isRateLimited());
            assertTrue(e.getMessage().contains("429"));
        }
    }

    @Test
    public void malformedSuccessBodyIsRejected() {
        route = new Route(200, "not-json");

        try {
            client("xin_test", "secret").check("140", "1.4.0", null);
            fail("expected XinUpdateException");
        } catch (XinUpdateException e) {
            assertEquals(200, e.getHttpStatus());
            assertTrue(e.getMessage().contains("响应格式"));
        }
    }

    @Test
    public void downloadParsesMainAndCandidates() throws Exception {
        route = new Route(200, "{\"message\":\"ok\",\"data\":{"
                + "\"download_id\":1234,"
                + "\"report_token\":\"token-123\","
                + "\"main\":\"https://cdn.example.com/app.apk\","
                + "\"candidates\":[{\"uri\":\"store://app.apk\",\"type\":\"direct\","
                + "\"resolved\":\"https://cdn.example.com/app.apk\",\"resource_id\":987,"
                + "\"provider_id\":2,\"provider_name\":\"主存储\"}],"
                + "\"channel\":\"stable\","
                + "\"identifier\":\"com.example.app\","
                + "\"version\":{\"id\":88,\"version_name\":\"1.5.0\",\"version_code\":\"150\"},"
                + "\"need_parse\":true"
                + "}}");

        DownloadResult result = client("xin_test", "secret")
                .download(88L, "stable", "android_phone", "device-abc");

        assertEquals(1234, result.getDownloadId());
        assertEquals("token-123", result.getReportToken());
        assertEquals("https://cdn.example.com/app.apk", result.getMain());
        assertEquals(1, result.getCandidates().size());
        DownloadCandidate candidate = result.getCandidates().get(0);
        assertEquals("store://app.apk", candidate.getUri());
        assertEquals("direct", candidate.getType());
        assertEquals(Long.valueOf(987), candidate.getResourceId());
        assertEquals(Long.valueOf(2), candidate.getProviderId());
        assertEquals("主存储", candidate.getProviderName());
        assertEquals(88, result.getVersion().getId());
        assertEquals("1.5.0", result.getVersion().getVersionName());
        assertTrue(result.isNeedParse());

        assertEquals("/api/app/update/download", requestPath);
        JSONObject body = new JSONObject(requestBody);
        assertEquals(88, body.getInt("version_id"));
        assertEquals("stable", body.getString("channel"));
        assertEquals("android_phone", body.getString("device_type"));
        assertEquals("device-abc", body.getString("device_id"));
    }

    @Test
    public void downloadUsesEndpointFromCheck() throws Exception {
        route = new Route(200, "{\"update_available\":true,\"latest\":{\"version_id\":88},"
                + "\"download_endpoint\":\"/api/app/update/download\"}");
        XinUpdateClient client = client("xin_test", "secret");
        CheckResult check = client.check("140", null, null);

        route = new Route(200, "{\"data\":{\"main\":\"https://cdn.example.com/app.apk\"}}");
        DownloadResult result = client.downloadFor(check, 88L, null, null, null);

        assertEquals("https://cdn.example.com/app.apk", result.getMain());
        assertTrue(result.getCandidates().isEmpty());
        assertNull(result.getVersion());
        assertEquals("/api/app/update/download", requestPath);
    }

    @Test
    public void baseUrlTrailingSlashAndAbsoluteEndpointAreSupported() throws Exception {
        route = new Route(200, "{\"update_available\":false,\"latest\":null}");
        XinUpdateClient client = new XinUpdateClient(
                baseUrl + "/", "xin_test", "secret");

        client.check("140", "1.4.0", null);
        assertEquals("/api/app/update/check", requestPath);

        route = new Route(200, "{\"update_available\":true,\"latest\":{\"version_id\":88},"
                + "\"download_endpoint\":\"" + baseUrl + "/custom/download\"}");
        CheckResult check = client.check("140", "1.4.0", null);
        route = new Route(200, "{\"data\":{\"main\":\"https://cdn.example.com/app.apk\"}}");
        client.downloadFor(check, null, null, null, null);
        assertEquals("/api/custom/download", requestPath);
    }

    @Test
    public void checkRejectsMissingVersionInformation() throws Exception {
        XinUpdateClient client = new XinUpdateClient(baseUrl, "xin_test", "secret");
        try {
            client.check(null, "  ", "com.example.app");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("versionCode"));
        }
    }

    @Test
    public void downloadRejectsNonPositiveVersionId() throws Exception {
        XinUpdateClient client = new XinUpdateClient(baseUrl, "xin_test", "secret");
        try {
            client.download(0L, null, null, null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("versionId"));
        }
    }

    private XinUpdateClient client(String clientId, String secret) {
        return new XinUpdateClient(baseUrl, clientId, secret);
    }

    private static final class Route {
        final int status;
        final String body;

        Route(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    /**
     * 极简 HTTP/1.1 测试服务器：只服务测试需要的 POST + Content-Length，
     * 避免依赖 com.sun.net.httpserver（JDK 模块在 Android 单元测试中不可见）。
     */
    private final class TestHttpServer implements Runnable {

        private final ServerSocket socket;
        private final Thread thread;

        TestHttpServer() throws IOException {
            socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            thread = new Thread(this, "xin-update-sdk-test-server");
            thread.setDaemon(true);
        }

        void start() {
            thread.start();
        }

        int getPort() {
            return socket.getLocalPort();
        }

        void stop() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // 测试清理失败不影响断言
            }
        }

        @Override
        public void run() {
            while (!socket.isClosed()) {
                try (Socket client = socket.accept()) {
                    handle(client);
                } catch (IOException e) {
                    if (!socket.isClosed()) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        private void handle(Socket client) throws IOException {
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();

            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }
            String[] parts = requestLine.split(" ");
            requestMethod = parts.length > 0 ? parts[0] : null;
            requestPath = parts.length > 1 ? parts[1] : null;

            int contentLength = 0;
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                String name = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                if ("Content-Length".equalsIgnoreCase(name)) {
                    contentLength = Integer.parseInt(value);
                } else if ("X-Client-Id".equalsIgnoreCase(name)) {
                    requestClientId = value;
                } else if ("X-Client-Secret".equalsIgnoreCase(name)) {
                    requestClientSecret = value;
                } else if ("Content-Type".equalsIgnoreCase(name)) {
                    requestContentType = value;
                }
            }

            byte[] body = new byte[contentLength];
            int offset = 0;
            while (offset < contentLength) {
                int read = in.read(body, offset, contentLength - offset);
                if (read == -1) {
                    break;
                }
                offset += read;
            }
            requestBody = new String(body, 0, offset, StandardCharsets.UTF_8);

            Route current = route;
            int status = current == null ? 500 : current.status;
            byte[] response = (current == null ? "{}" : current.body).getBytes(StandardCharsets.UTF_8);
            String header = "HTTP/1.1 " + status + " X\r\n"
                    + "Content-Type: application/json; charset=utf-8\r\n"
                    + "Content-Length: " + response.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(header.getBytes(StandardCharsets.US_ASCII));
            out.write(response);
            out.flush();
        }

        private String readLine(InputStream in) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int previous = -1;
            int value;
            while ((value = in.read()) != -1) {
                if (value == '\n') {
                    break;
                }
                if (previous == '\r') {
                    buffer.write('\r');
                }
                if (value != '\r') {
                    buffer.write(value);
                }
                previous = value;
            }
            if (value == -1 && buffer.size() == 0) {
                return null;
            }
            return buffer.toString(StandardCharsets.ISO_8859_1.name());
        }
    }
}
