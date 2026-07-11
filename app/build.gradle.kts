plugins { id("com.android.application") }

android {
    namespace = "ru.tzfviewer"
    compileSdk = 35
    ndkVersion = "26.3.11579264"

    defaultConfig {
        applicationId = "ru.tzfviewer"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
        externalNativeBuild { cmake { cppFlags += listOf("-std=c++20") } }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
