pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal {
            content {
                includeGroup("io.github.libxposed")
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "Core Patch"
include(":app")

include(":test-fixtures:app")
include(":test-fixtures:shared")
include(":test-fixtures:domain")
include(":test-fixtures:overlaytarget")
include(":test-fixtures:overlay")
