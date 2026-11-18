package top.genouka.xin.update;

/** 下载候选地址。 */
public final class DownloadCandidate {

    private final String uri;
    private final String type;
    private final String resolved;
    private final Long resourceId;
    private final Long providerId;
    private final String providerName;

    DownloadCandidate(String uri, String type, String resolved, Long resourceId, Long providerId, String providerName) {
        this.uri = uri;
        this.type = type;
        this.resolved = resolved;
        this.resourceId = resourceId;
        this.providerId = providerId;
        this.providerName = providerName;
    }

    /** 资源表中的原始 URI。 */
    public String getUri() {
        return uri;
    }

    /** 资源类型：direct / server / client 等。 */
    public String getType() {
        return type;
    }

    /** 服务端已解析的地址；server:// 候选可能为 null，需按站点规则二次解析。 */
    public String getResolved() {
        return resolved;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public Long getProviderId() {
        return providerId;
    }

    public String getProviderName() {
        return providerName;
    }
}
