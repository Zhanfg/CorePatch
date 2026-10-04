plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.axymorrsen.corepatch.domainfixture"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.axymorrsen.corepatch.domainfixture"
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
