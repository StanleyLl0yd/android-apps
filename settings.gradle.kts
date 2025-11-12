pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "everyday-android-apps"

include(":apps:biorhythms")
project(":apps:biorhythms").projectDir = file("apps/biorhythms")