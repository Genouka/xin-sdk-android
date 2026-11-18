package top.genouka.xin.update;

/**
 * 客户端当前版本信息，用于服务端判断是否有更新。
 *
 * <p>Android 应用通常直接使用 {@link XinUpdater#readCurrentVersion(android.content.Context)}
 * 自动读取，无需手动构造。
 */
public final class CurrentVersion {

    private final String versionCode;
    private final String versionName;
    private final String identifier;

    public CurrentVersion(String versionCode, String versionName, String identifier) {
        this.versionCode = trimToNull(versionCode);
        this.versionName = trimToNull(versionName);
        this.identifier = trimToNull(identifier);
    }

    public String getVersionCode() {
        return versionCode;
    }

    public String getVersionName() {
        return versionName;
    }

    public String getIdentifier() {
        return identifier;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
