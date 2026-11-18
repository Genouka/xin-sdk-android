package top.genouka.xin.update;

/** 服务端当前最新上架版本。 */
public final class LatestVersion {

    private final long versionId;
    private final String versionName;
    private final String versionCode;
    private final String changelog;
    private final String onShelfAt;

    LatestVersion(long versionId, String versionName, String versionCode, String changelog, String onShelfAt) {
        this.versionId = versionId;
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.changelog = changelog;
        this.onShelfAt = onShelfAt;
    }

    public long getVersionId() {
        return versionId;
    }

    public String getVersionName() {
        return versionName;
    }

    public String getVersionCode() {
        return versionCode;
    }

    public String getChangelog() {
        return changelog;
    }

    /** 上架时间，ISO-8601 字符串；服务端未提供时为 null。 */
    public String getOnShelfAt() {
        return onShelfAt;
    }
}
