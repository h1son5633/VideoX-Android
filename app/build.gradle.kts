plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.videox.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.videox.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 120
        versionName = "1.2.0"
        // 底栏图标等新依赖让方法数超过单 dex 的 64K 上限，开启多 dex
        // （minSdk 23，系统原生支持，无需额外 multidex 库）
        multiDexEnabled = true
    }

    // 固定签名：钥匙路径与密码从环境变量读（CI 由 GitHub Secrets 注入）。
    // 没有环境变量时（本地/未配置机密）不挂签名配置，保持原有行为。
    val keystorePath = System.getenv("ANDROID_KEYSTORE_FILE")

    signingConfigs {
        if (keystorePath != null) {
            create("fixed") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("fixed")
            }
        }
        debug {
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("fixed")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
