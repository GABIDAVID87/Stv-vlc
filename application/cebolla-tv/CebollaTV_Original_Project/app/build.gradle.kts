import java.util.Properties

plugins {
    id("com.android.application") version "8.11.0"
    kotlin("android") version "1.9.22"
}

val secrets = Properties()
val secretsFile = rootProject.file("secrets.properties")
if (secretsFile.exists()) {
    secretsFile.inputStream().use { secrets.load(it) }
}

fun secret(name: String): String = secrets.getProperty(name, "")

android {
    namespace = "com.segovia.tv"
    compileSdk = 36

    lint {
        checkReleaseBuilds = false
    }

    defaultConfig {
        applicationId = "com.segovia.tv"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField(
            "String",
            "DRIVE_SERVICE_EMAIL",
            "\"${secret("DRIVE_SERVICE_EMAIL").replace("\"", "\\\"")}\""
        )

        buildConfigField(
            "String",
            "DRIVE_PRIVATE_KEY",
            "\"${secret("DRIVE_PRIVATE_KEY").replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""
        )
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }

        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties"
            )
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("androidx.media:media:1.7.0")
}