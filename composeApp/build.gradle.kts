import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

abstract class GenerateBuildFlagsTask : DefaultTask() {
    @get:Input
    abstract val paywallEnabled: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val dir = outputDir.get().asFile.resolve("de/koenidv/spheres")
        dir.mkdirs()
        dir.resolve("BuildFlags.kt").writeText(
            """
            package de.koenidv.spheres

            object BuildFlags {
                const val PAYWALL_ENABLED: Boolean = ${paywallEnabled.get()}
            }
            """.trimIndent() + "\n"
        )
    }
}

val generateBuildFlags = tasks.register<GenerateBuildFlagsTask>("generateBuildFlags") {
    fun isEnabled(v: String): Boolean {
        val norm = v.trim().lowercase()
        return norm != "false" && norm != "0" && norm != "no"
    }

    val gradlePaywall = providers.gradleProperty("spheres.paywallEnabled").orElse("true")
    val envPaywall = providers.environmentVariable("PAYWALL_ENABLED").orElse("")
    paywallEnabled.set(
        gradlePaywall.zip(envPaywall) { g, e ->
            isEnabled(g) && (e.isBlank() || isEnabled(e))
        }
    )

    outputDir.set(layout.buildDirectory.dir("generated/source/buildFlags/commonMain/kotlin"))
}

kotlin {
    android {
        namespace = "de.koenidv.spheres"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources.enable = true
        withHostTest {}

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    
    sourceSets {
        commonMain {
            kotlin.srcDir(generateBuildFlags)
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.cio)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services.auth)
            implementation(libs.googleid)
            implementation(libs.play.billing)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.posthog.kmp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "de.koenidv.spheres.resources"
    generateResClass = always
}