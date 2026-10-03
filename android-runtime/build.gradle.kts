plugins { id("com.android.library"); `maven-publish` }
android {
    namespace = "io.github.backgroundservice.androidruntime"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    testOptions { unitTests.isIncludeAndroidResources = true }
    publishing { singleVariant("release") { withSourcesJar() } }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.7.0")
    api(project(":core"))
    implementation("androidx.core:core-ktx:1.17.0")
    api("androidx.work:work-runtime-ktx:2.10.5")
}
afterEvaluate { publishing { publications { create<MavenPublication>("release") { from(components["release"]) } } } }
