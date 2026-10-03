plugins { kotlin("jvm"); application }
kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11 } }
java { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
dependencies { implementation(project(":core")) }
application { mainClass.set("io.github.backgroundservice.sample.MainKt") }
