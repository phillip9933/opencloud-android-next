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

rootProject.name = "opencloud-android-next"

include(":app")
include(":core:model")
include(":core:designsystem")
include(":core:ui")
include(":core:network")
include(":core:security")
include(":core:database")
include(":core:sync")
include(":core:documentsprovider")
include(":core:datastore")
include(":feature:auth")
include(":feature:files")
include(":feature:search")
include(":feature:transfers")
include(":feature:settings")
include(":feature:account")
include(":feature:shares")
