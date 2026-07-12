plugins { id("com.android.application") }

val releaseKeystorePath = System.getenv("TZF_KEYSTORE_PATH")
val ciVersionCode = System.getenv("TZF_VERSION_CODE")?.toIntOrNull()
val ciVersionName = System.getenv("TZF_VERSION_NAME")

android {
    namespace = "ru.tzfviewer"
    compileSdk = 35
    ndkVersion = "26.3.11579264"
    buildFeatures { buildConfig = true }

    defaultConfig {
        applicationId = "ru.tzfviewer"
        minSdk = 26
        targetSdk = 35
        versionCode = ciVersionCode ?: 2
        versionName = ciVersionName ?: "0.2-dev"
        externalNativeBuild { cmake { cppFlags += listOf("-std=c++20") } }
    }
    signingConfigs {
        if (releaseKeystorePath != null) create("release") {
            storeFile = file(releaseKeystorePath)
            storePassword = System.getenv("TZF_STORE_PASSWORD")
            keyAlias = System.getenv("TZF_KEY_ALIAS")
            keyPassword = System.getenv("TZF_KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (releaseKeystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
}

dependencies {
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    testImplementation("junit:junit:4.13.2")
}
