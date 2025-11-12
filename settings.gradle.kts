pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }

rootProject.name = "android-apps"

// модуль физически лежит в ./app/biorhythms
include(":app:biorhythms")
project(":app:biorhythms").projectDir = file("app/biorhythms")