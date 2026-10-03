pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "android-background-service"
include(":core", ":android-runtime", ":diagnostics-ui", ":sample")
