plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val mantraVersion = (project.findProperty("mantraVersion") as String).toInt()

android {
    namespace = "com.mantra.routercredits"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mantra.routercredits"
        minSdk = 29
        targetSdk = 35
        versionCode = mantraVersion
        versionName = mantraVersion.toString()
    }

    // The same key every build, so a new version installs over the old one. It lives in
    // GitHub Actions secrets and is restored to a path passed in by environment.
    signingConfigs {
        create("mantra") {
            val path = System.getenv("MANTRA_KEYSTORE")
            if (path != null && file(path).exists()) {
                storeFile = file(path)
                storeType = "pkcs12"
                storePassword = System.getenv("MANTRA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MANTRA_KEY_ALIAS")
                keyPassword = System.getenv("MANTRA_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (System.getenv("MANTRA_KEYSTORE") != null) {
                signingConfig = signingConfigs.getByName("mantra")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = false }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val impl = output as? com.android.build.api.variant.impl.VariantOutputImpl
            impl?.outputFileName?.set("$mantraVersion-router-credits-v$mantraVersion.apk")
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
