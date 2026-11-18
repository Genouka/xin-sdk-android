package top.genouka.xin.update;

/**
 * 检查更新/下载地址解析失败时抛出。
 *
 * <p>{@link #getHttpStatus()} 为 0 表示请求没有拿到 HTTP 响应（断网、超时、
 * 响应格式异常等），其余情况为服务端返回的状态码。
 */
public class XinUpdateException extends Exception {

    private final int httpStatus;

    public XinUpdateException(String message) {
        this(0, message, null);
    }

    public XinUpdateException(int httpStatus, String message) {
        this(httpStatus, message, null);
    }

    public XinUpdateException(int httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    /** HTTP 状态码；0 表示网络错误或响应无法解析。 */
    public int getHttpStatus() {
        return httpStatus;
    }

    /** 凭据无效、已撤销或请求未通过认证。 */
    public boolean isUnauthorized() {
        return httpStatus == 401;
    }

    /** 应用不存在、未上架或 identifier 与服务端记录不一致。 */
    public boolean isNotFound() {
        return httpStatus == 404;
    }

    /** 触发限流，应退避后重试，不要继续高频轮询。 */
    public boolean isRateLimited() {
        return httpStatus == 429;
    }
}
