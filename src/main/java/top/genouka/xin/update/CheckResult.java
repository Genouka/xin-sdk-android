package top.genouka.xin.update;

/** 检查更新结果。 */
public final class CheckResult {

    private final boolean updateAvailable;
    private final AppInfo app;
    private final LatestVersion latest;
    private final String downloadEndpoint;

    CheckResult(boolean updateAvailable, AppInfo app, LatestVersion latest, String downloadEndpoint) {
        this.updateAvailable = updateAvailable;
        this.app = app;
        this.latest = latest;
        this.downloadEndpoint = downloadEndpoint;
    }

    /** 是否有新版本；只有为 true 时才建议提示用户升级。 */
    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public AppInfo getApp() {
        return app;
    }

    /**
     * 最新上架版本；应用暂无上架版本时为 null。
     *
     * <p>没有更新时也可能返回当前最新版本，便于展示版本信息。
     */
    public LatestVersion getLatest() {
        return latest;
    }

    /** 服务端给出的下载接口路径，例如 {@code /api/app/update/download}。 */
    public String getDownloadEndpoint() {
        return downloadEndpoint;
    }
}
