plugins { kotlin("jvm"); `maven-publish` }
kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11 } }
java { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
dependencies { testImplementation("junit:junit:4.13.2") }
publishing { publications { create<MavenPublication>("library") { from(components["java"]) } } }
