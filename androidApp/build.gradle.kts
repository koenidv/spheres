plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "dev.koeni.spheres"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.koeni.spheres"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1006
        versionName = "1.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

fun isFlagEnabled(v: String): Boolean {
    val norm = v.trim().lowercase()
    return norm != "false" && norm != "0" && norm != "no"
}

val gradlePhoneLogging = providers.gradleProperty("spheres.phoneLoggingEnabled").orElse("false").get()
val envPhoneLogging = providers.environmentVariable("PHONE_LOGGING_ENABLED").orElse("").get()
val phoneLoggingEnabled = isFlagEnabled(gradlePhoneLogging) && (envPhoneLogging.isBlank() || isFlagEnabled(envPhoneLogging))

if (phoneLoggingEnabled) {
    androidComponents {
        onVariants { variant ->
            variant.sources.manifests.addStaticManifestFile(
                layout.projectDirectory.file("src/phoneLogging/AndroidManifest.xml").asFile.absolutePath
            )
        }
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(libs.androidx.activity.compose)
    implementation(libs.posthog.kmp)
    debugImplementation(libs.compose.uiTooling)
}
