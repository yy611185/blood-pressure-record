plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.example.bloodpressurerecord"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yang.bloodpressure"
        minSdk = 26
        targetSdk = 36
        versionCode = 29
        versionName = "1.8.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    val releaseStorePath = providers.environmentVariable("BP_RELEASE_STORE_FILE").orNull
    val releaseStorePassword = providers.environmentVariable("BP_RELEASE_STORE_PASSWORD").orNull
    val releaseKeyAlias = providers.environmentVariable("BP_RELEASE_KEY_ALIAS").orNull
    val releaseKeyPassword = providers.environmentVariable("BP_RELEASE_KEY_PASSWORD").orNull
    if (listOf(
            releaseStorePath,
            releaseStorePassword,
            releaseKeyAlias,
            releaseKeyPassword
        ).all { !it.isNullOrBlank() }
    ) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(requireNotNull(releaseStorePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // 本地未配置签名环境变量时生成 unsigned Release，绝不回退到 Debug 签名。
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Compose 1.7（strong skipping 等性能强化）；Navigation 2.8 支持预测式返回。
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.3")

    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // 桌面小部件（Glance AppWidget，2×2/4×2/4×4 响应式布局）
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    // 5.4.0 之前的 OOXML 解析存在重复 ZIP 条目安全问题；备份导入必须使用修复版本。
    implementation("org.apache.poi:poi-ooxml:5.5.1")
    baselineProfile(project(":baselineprofile"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
