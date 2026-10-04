plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.axymorrsen.corepatch.overlayfixture"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.axymorrsen.corepatch.overlayfixture"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0-test"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
}
