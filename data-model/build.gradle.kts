import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    id("android-library-convention")
    id("kotlin-library-convention")
    alias(libs.plugins.kotlin.serialization)
}
android {
    namespace = "com.onion.data.model"
    buildFeatures {
        compose = false
    }
}

kotlin {

    sourceSets {
        named("commonMain") {
            dependencies {
                implementation(libs.ktor.client.serialization)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        named("commonTest") {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

