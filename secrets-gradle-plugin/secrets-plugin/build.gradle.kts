import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "aero.digitalhangar.secretsgradleplugin"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

dependencies {
    implementation(libs.android.gradlePlugin)
    testImplementation(kotlin("test"))
}


gradlePlugin {
    plugins {
        register("androidSecrets") {
            id = libs.plugins.secrets.get().pluginId
            implementationClass = "aero.digitalhangar.secrets.Secrets"
        }
    }
}
