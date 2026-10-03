plugins { id("com.android.library"); `maven-publish` }
android {
    namespace = "io.github.backgroundservice.diagnosticsui"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    publishing { singleVariant("release") { withSourcesJar() } }
}
dependencies {
    api(project(":android-runtime"))
    implementation("androidx.core:core-ktx:1.17.0")

}
afterEvaluate { publishing { publications { create<MavenPublication>("release") { from(components["release"]) } } } }
