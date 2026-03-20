import org.gradle.kotlin.dsl.secrets

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.secrets)
}

android {
    namespace = "aero.digitalhangar.secrets"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "aero.digitalhangar.secrets"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    // Specifies one flavor dimension.
    flavorDimensions += listOf("mode", "api")
    productFlavors {
        create("demo") {
            dimension = "mode"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
        }
        create("full") {
            dimension = "mode"
            applicationIdSuffix = ".full"
            versionNameSuffix = "-full"
        }
        create("min24") {
            dimension = "api"
            versionNameSuffix = "-min24"
        }
        create("min21") {
            dimension = "api"
            versionNameSuffix = "-min21"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
    }
}

secrets {
    defaultSecretsFileName.set("secrets.default.properties")
    variantSecretsMapping.put("debug", "secrets.stage.properties")
    variantSecretsMapping.put("release", "secrets.prod.properties")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}