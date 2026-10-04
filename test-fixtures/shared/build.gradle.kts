plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.axymorrsen.corepatch.shared"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.axymorrsen.corepatch.shared"
        minSdk = 28
        targetSdk = 28
        versionCode = 1
        versionName = "1.0-test"
    }

    flavorDimensions += "identity"
    productFlavors {
        create("a") {
            dimension = "identity"
            applicationIdSuffix = ".a"
        }
        create("b") {
            dimension = "identity"
            applicationIdSuffix = ".b"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
}
