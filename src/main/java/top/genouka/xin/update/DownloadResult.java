package top.genouka.xin.update;

import java.util.Collections;
import java.util.List;

/** 下载接口返回的主地址与候选列表。 */
public final class DownloadResult {

    private final long downloadId;
    private final String reportToken;
    private final String main;
    private final List<DownloadCandidate> candidates;
    private final String channel;
    private final String identifier;
    private final VersionInfo version;
    private final boolean needParse;

    DownloadResult(long downloadId, String reportToken, String main, List<DownloadCandidate> candidates,
                   String channel, String identifier, VersionInfo version, boolean needParse) {
        this.downloadId = downloadId;
        this.reportToken = reportToken;
        this.main = main;
        this.candidates = Collections.unmodifiableList(candidates);
        this.channel = channel;
        this.identifier = identifier;
        this.version = version;
        this.needParse = needParse;
    }

    /** 下载记录 ID，可用于 {@code POST /api/app/download/result} 回传结果。 */
    public long getDownloadId() {
        return downloadId;
    }

    /** 本次下载的发起凭证，回传结果时需要携带。 */
    public String getReportToken() {
        return reportToken;
    }

    /** 主下载地址。 */
    public String getMain() {
        return main;
    }

    /** 按优先级排序的候选地址。 */
    public List<DownloadCandidate> getCandidates() {
        return candidates;
    }

    public String getChannel() {
        return channel;
    }

    public String getIdentifier() {
        return identifier;
    }

    public VersionInfo getVersion() {
        return version;
    }

    /** 是否有 server:// 候选需要客户端二次解析。 */
    public boolean isNeedParse() {
        return needParse;
    }
}
