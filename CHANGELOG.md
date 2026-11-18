# 更新日志

本项目遵循语义化版本：`MAJOR.MINOR.PATCH`。`build.gradle.kts` 的 `version` 与
`XinUpdateClient.VERSION` 必须一致。

## v1.0.0 — 2026-11-18

首个公开版本。

- `XinUpdater`：Android 便捷封装，自动读取包名/versionCode/versionName，回调在主线程；
  同步接口 `checkNow()` / `downloadNow(...)` 供后台线程使用。
- `XinUpdateClient`：同步 HTTP 客户端，封装 `POST /api/app/update/check` 与
  `POST /api/app/update/download`，使用 `X-Client-Id` / `X-Client-Secret` 凭据。
- 支持 `AndroidManifest` meta-data 配置（`XinUpdater.fromManifest`）。
- 错误映射：`XinUpdateException` 暴露 HTTP 状态码与 `isUnauthorized()` /
  `isNotFound()` / `isRateLimited()`。
- 仅依赖 Android 平台自带 `HttpURLConnection` 与 `org.json`，API 21+ / Java 8 字节码。
