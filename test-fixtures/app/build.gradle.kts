plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.axymorrsen.corepatch.test"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.axymorrsen.corepatch.test"
        minSdk = 28
        targetSdk = 37
    }

    flavorDimensions += "generation"
    productFlavors {
        create("v1") {
            dimension = "generation"
            versionCode = 1
            versionName = "1.0-test"
        }
        create("v2") {
            dimension = "generation"
            versionCode = 2
            versionName = "2.0-test"
        }
        create("v3") {
            dimension = "generation"
            versionCode = 3
            versionName = "3.0-test"
        }
        create("v4") {
            dimension = "generation"
            versionCode = 4
            versionName = "4.0-test"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
}
