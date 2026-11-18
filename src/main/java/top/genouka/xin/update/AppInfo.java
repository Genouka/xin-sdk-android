package top.genouka.xin.update;

/** 检查更新响应中的应用信息。 */
public final class AppInfo {

    private final long id;
    private final String name;
    private final String identifier;

    AppInfo(long id, String name, String identifier) {
        this.id = id;
        this.name = name;
        this.identifier = identifier;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getIdentifier() {
        return identifier;
    }
}
