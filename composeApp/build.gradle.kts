import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                }
            }
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.browser)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
            // Login Google nativo
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services)
            implementation(libs.googleid)
            // Mapa OSM + ubicación
            implementation(libs.osmdroid)
            implementation(libs.play.location)
            // Rotación EXIF al comprimir fotos clínicas antes de subirlas
            implementation("androidx.exifinterface:exifinterface:1.3.7")
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.navigation.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            // Supabase: mismo backend que la web
            implementation(project.dependencies.platform(libs.supabase.bom))
            implementation(libs.supabase.auth)
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.realtime)
            implementation(libs.supabase.compose.auth)
            implementation(libs.ktor.client.android)

            // Coil 3: carga de imágenes remotas (fotos evolutivas) con motor de red ktor
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
        }
    }
}

android {
    namespace = "pe.saniape.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "pe.saniape.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        getByName("debug") {
            // En debug, los endpoints /api apuntan al Next.js local (npm run dev).
            // 10.0.2.2 = localhost del PC visto desde el emulador.
            buildConfigField("String", "SITE_URL", "\"http://10.0.2.2:3000\"")
        }
        getByName("release") {
            isMinifyEnabled = false
            buildConfigField("String", "SITE_URL", "\"https://www.saniape.com\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}