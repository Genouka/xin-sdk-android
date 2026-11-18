plugins {
    id("com.android.library") version "8.10.1"
    id("maven-publish")
}

// 版本号需与 top.genouka.xin.update.XinUpdateClient.VERSION 保持一致（发版时同步修改）
group = "com.github.Genouka"
version = "1.0.0"

android {
    namespace = "top.genouka.xin.update"
    compileSdk = 36

    defaultConfig {
        // HttpURLConnection + org.json 均为 Android 平台自带能力，兼容到 API 21
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    testOptions {
        unitTests.isReturnDefaultValues = false
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // 本地单元测试运行在 JVM 上，用真实实现替换 android.jar 中的 org.json 占位类
    testImplementation("org.json:json:20240303")
}

// JitPack 以 com.github.Genouka:xin-sdk-android:<tag> 解析；artifactId 用仓库名，便于单行依赖
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.Genouka"
                artifactId = "xin-sdk-android"
                version = project.version.toString()
            }
        }
    }
}
