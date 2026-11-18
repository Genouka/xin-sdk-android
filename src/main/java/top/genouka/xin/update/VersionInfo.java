package top.genouka.xin.update;

/** 下载响应中的版本信息。 */
public final class VersionInfo {

    private final long id;
    private final String versionName;
    private final String versionCode;

    VersionInfo(long id, String versionName, String versionCode) {
        this.id = id;
        this.versionName = versionName;
        this.versionCode = versionCode;
    }

    public long getId() {
        return id;
    }

    public String getVersionName() {
        return versionName;
    }

    public String getVersionCode() {
        return versionCode;
    }
}
