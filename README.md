[![JitPack](https://jitpack.io/v/Genouka/xin-sdk-android.svg)](https://jitpack.io/#Genouka/xin-sdk-android)
[![Build](https://github.com/Genouka/xin-sdk-android/actions/workflows/build.yml/badge.svg)](https://github.com/Genouka/xin-sdk-android/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

# 莘应用检查更新 SDK（Android）

面向应用开发者的客户端 SDK，封装莘应用资源站（下称“平台”）的应用内检查更新接口：

- `POST /api/app/update/check`：按客户端版本判断是否有新版本，返回版本名、版本号、更新日志；
- `POST /api/app/update/download`：返回主下载地址、候选地址、下载记录与回传凭证。

SDK 使用开发者中心签发的**应用接入凭据**（`client_id` / `client_secret`）识别应用，与开发者 API 的
`access_token` 相互独立。凭据创建入口：开发者中心 → API 接入 → 应用接入。

- 平台主页：<https://xinappstore.qiumingsanyu.top>
- 开发者中心：<https://xinappstore.qiumingsanyu.top/dev/apps>
- 接入文档：<https://wiki.qiumingsanyu.top/xin:developer:sdk>
- 服务端接口字段：<https://wiki.qiumingsanyu.top/xin:developer:api>

> **English**：An Android update-check SDK for the Xin App Store platform. It wraps
> `POST /api/app/update/check` and `POST /api/app/update/download`, authenticating with
> per-app client credentials. Java 8 bytecode, API 21+, no third-party runtime dependencies.

## 环境要求

- Android 5.0（API 21）及以上；
- Java 8 字节码；
- 运行时只依赖 Android 平台自带的 `HttpURLConnection` 与 `org.json`，不引入第三方网络库。

## 安装

### 方式一：JitPack（推荐）

```groovy
// settings.gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

```groovy
// app/build.gradle
dependencies {
    implementation 'com.github.Genouka:xin-sdk-android:v1.0.0'
}
```

Kotlin DSL：

```kotlin
dependencies {
    implementation("com.github.Genouka:xin-sdk-android:v1.0.0")
}
```

### 方式二：AAR 文件

从 [Releases](https://github.com/Genouka/xin-sdk-android/releases) 下载
`xin-sdk-android-release.aar`，放入宿主工程的 `libs/`：

```groovy
dependencies {
    implementation files('libs/xin-sdk-android-release.aar')
}
```

SDK 的清单已声明 `INTERNET` 权限，合并后无需重复添加。

### 方式三：源码模块

```groovy
// settings.gradle
include ':xin-sdk-android'
project(':xin-sdk-android').projectDir = new File('../xin-sdk-android')
```

```groovy
dependencies {
    implementation project(':xin-sdk-android')
}
```

## 快速开始

### 1. 创建客户端

```java
XinUpdater updater = XinUpdater.builder(context)
        .clientId("xin_xxxxxxxxxxxxxxxxxxxxxxxx")
        .clientSecret("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
        .build();
```

默认 API 基址为 `https://xinappapi.qiumingsanyu.top/api`。本地联调可覆盖：

```java
XinUpdater updater = XinUpdater.builder(context)
        .baseUrl("http://10.0.2.2:8124/api") // Android 模拟器访问宿主机
        .clientId("xin_...")
        .clientSecret("...")
        .build();
```

Android 9 及以上默认禁止明文 HTTP，本地联调需要配置 `networkSecurityConfig`；生产环境请使用 HTTPS。

### 2. 检查更新

```java
updater.check(new XinUpdater.Callback() {
    @Override
    public void onSuccess(CheckResult result) {
        if (!result.isUpdateAvailable() || result.getLatest() == null) {
            return;
        }
        LatestVersion latest = result.getLatest();
        String message = "新版本 " + latest.getVersionName()
                + "\n\n" + (latest.getChangelog() == null ? "暂无更新日志" : latest.getChangelog());
        // 使用自己的对话框展示 message，确认后进入下载流程
    }

    @Override
    public void onError(XinUpdateException error) {
        if (error.isRateLimited()) {
            // 429：退避后重试，不要继续高频轮询
        } else if (error.isUnauthorized()) {
            // 401：凭据无效或已撤销，需要开发者重新创建并更新客户端
        }
    }
});
```

回调在主线程执行，可以直接更新 UI。`XinUpdater` 内部使用单线程池，`shutdown()`
可释放资源（例如在业务容器销毁时调用）。

同步接口 `checkNow()` / `downloadNow(...)` 禁止在主线程调用。

### 3. 获取下载地址

```java
updater.download(result.getLatest().getVersionId(), null, "android_phone", null,
        new XinUpdater.DownloadCallback() {
            @Override
            public void onSuccess(DownloadResult download) {
                String url = download.getMain();
                // 交给自己的下载器；下载完成后可用 download.getDownloadId()
                // 与 download.getReportToken() 回传 /api/app/download/result
            }

            @Override
            public void onError(XinUpdateException error) {
                // 403 设备不兼容、404 无可用资源、429 限流等
            }
        });
```

SDK 只负责检查更新与解析下载地址，不包含 APK 下载、通知栏进度、安装器跳转；
这些能力由宿主应用按现有更新模块实现。

### 4. 可选：通过 AndroidManifest 配置

```xml
<application>
    <meta-data
        android:name="top.genouka.xin.update.CLIENT_ID"
        android:value="xin_xxxxxxxxxxxxxxxxxxxxxxxx" />
    <meta-data
        android:name="top.genouka.xin.update.CLIENT_SECRET"
        android:value="xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" />
    <!-- 可选，默认生产环境 -->
    <meta-data
        android:name="top.genouka.xin.update.BASE_URL"
        android:value="https://xinappapi.qiumingsanyu.top/api" />
</application>
```

```java
XinUpdater updater = XinUpdater.fromManifest(context);
```

缺少必填 meta-data 时 `fromManifest` 抛出 `IllegalStateException`。

## 当前版本如何取得

`XinUpdater` 默认通过 `PackageManager` 读取宿主应用自身的包名、`versionCode`、`versionName`，
调用方无需手动传入。若宿主使用自有版本体系，可覆盖：

```java
XinUpdater updater = XinUpdater.builder(context)
        .clientId("xin_...")
        .clientSecret("...")
        .currentVersion(new CurrentVersion("140", "1.4.0", "com.example.app"))
        .build();
```

服务端比较规则：双方 `version_code` 均为数字时按整数比较；否则按 `version_name`
做版本比较；没有可比信息时视为有更新。因此 `versionCode` / `versionName`
至少提供一个，SDK 会在缺失时直接抛出 `IllegalArgumentException`。

## 错误处理

`XinUpdateException` 提供 HTTP 状态码与辅助判断：

| 状态码 | 含义 | 建议处理 |
|---|---|---|
| `401` | 凭据无效、已撤销 | 停止自动重试，提示开发者更新凭据 |
| `404` | 应用不存在、未上架、identifier 不一致 | 视为暂无更新；检查凭据与应用归属 |
| `403` | 设备类型不兼容等 | 提示用户当前设备不受支持 |
| `422` | 参数校验失败 | 检查版本信息与请求参数 |
| `429` | 触发限流（默认 60 次/分钟/凭据+IP） | 指数退避，不要循环重试 |
| `0` | 网络错误、超时、响应无法解析 | 按普通网络错误处理 |

`getHttpStatus()` 为 0 时，`getCause()` 可能包含底层网络异常。

## 安全说明

`client_secret` 必须随客户端分发，安装包可被反编译；它用于识别应用来源与降低滥用，
不是绝对机密。泄露后请立即在开发者中心撤销并创建新凭据。切勿把开发者 API 的
`access_token` 放进客户端。

## 构建与测试

构建需要 Android SDK（`compileSdk 36`）与 JDK 17+；Android Studio 会自动生成
`local.properties`，命令行构建也可通过 `ANDROID_HOME` 指定 SDK。

```bat
gradlew.bat testDebugUnitTest
gradlew.bat assembleRelease
```

产物：`build/outputs/aar/xin-sdk-android-release.aar`。

单元测试会启动本地 HTTP 服务，验证请求头、请求体与错误映射。

## 发版流程

1. 修改 `build.gradle.kts` 的 `version` 与 `XinUpdateClient.VERSION`（两者需一致），更新 `CHANGELOG.md`；
2. 提交并打 tag：`git tag -a v1.0.1 -m "v1.0.1" && git push origin v1.0.1`；
3. GitHub Actions 自动跑测试与 `assembleRelease`，产物见该次运行；
4. 创建 Release 并上传 `xin-sdk-android-release.aar`；
5. JitPack 在首次被请求时按 tag 构建，坐标 `com.github.Genouka:xin-sdk-android:v1.0.1`。

## 目录

```text
xin-sdk-android/
├── src/main/java/top/genouka/xin/update/
│   ├── XinUpdater.java          # Android 便捷封装（异步 + PackageManager）
│   ├── XinUpdateClient.java     # 同步 HTTP 客户端
│   ├── CurrentVersion.java
│   ├── CheckResult.java
│   ├── DownloadResult.java
│   └── ...                      # 响应模型与异常
├── src/test/java/...            # HTTP 往返测试
├── build.gradle.kts
└── CHANGELOG.md
```

## 许可证

[Apache License 2.0](LICENSE)。
