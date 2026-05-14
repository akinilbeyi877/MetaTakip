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
        google()
        mavenCentral()
    }
}

rootProject.name = "MetaTakip"
include(":app")
include(":permissions")
include(":feature_admin")
include(":feature_data")
include(":feature_uruntipi")
include(":feature_order")
include(":feature_customer")
include(":feature_unvan")
include(":feature_firma")
include(":feature_personel")
include(":feature_label")
include(":feature_genericlist")
include(":feature_backup")
