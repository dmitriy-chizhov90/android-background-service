plugins {
    kotlin("jvm") version "2.2.10" apply false
    id("com.android.library") version "9.2.1" apply false
}
allprojects { group = "io.github.backgroundservice"; version = "0.1.0" }
tasks.register("verify") { dependsOn(":core:test", ":android-runtime:testDebugUnitTest", ":sample:classes", ":android-runtime:lintDebug", ":diagnostics-ui:lintDebug") }
