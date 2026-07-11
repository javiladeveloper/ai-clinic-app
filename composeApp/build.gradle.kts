import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import java.util.Properties

// Firma de release: lee las claves desde keystore.properties (ignorado por git).
// Si el archivo no existe (ej. CI sin secretos), el release queda sin firmar y el
// build normal/debug sigue funcionando.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

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

    // Targets iOS: iPhone real (arm64), simulador en Mac Intel (x64) y simulador
    // en Mac con Apple Silicon (simulatorArm64). Los tres empaquetan un mismo
    // Framework llamado "ComposeApp" que el proyecto Xcode (iosApp) enlaza.
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
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
            // Engine Ktor de Android (Supabase lo usa en Android). Es platform-specific:
            // va aquí, NO en commonMain, porque no tiene variante iOS (rompía el sync KMP).
            implementation(libs.ktor.client.android)
            // Login Google nativo
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services)
            implementation(libs.googleid)
            // Mapa OSM + ubicación
            implementation(libs.osmdroid)
            implementation(libs.play.location)
            // Rotación EXIF al comprimir fotos clínicas antes de subirlas
            implementation("androidx.exifinterface:exifinterface:1.3.7")
            // Notificaciones FCM (init programática — sin plugin google-services; los valores
            // del proyecto Firebase van en push/FirebaseCfg.kt)
            implementation("com.google.firebase:firebase-messaging:24.1.0")
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
            // Solo el core común de Ktor; el engine lo pone cada plataforma
            // (Android: OkHttp en androidMain — iOS: Darwin en iosMain).
            implementation(libs.ktor.client.core)

            // Coil 3: carga de imágenes remotas (fotos evolutivas) con motor de red ktor
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
        }
        iosMain.dependencies {
            // Ktor con engine Darwin (NSURLSession) — el que usa Supabase en iOS.
            implementation(libs.ktor.client.darwin)
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
        // La TWA anterior (mismo packageId pe.saniape.app) llegó a versionCode 4;
        // esta app nativa debe subir con un código MAYOR para actualizar la ficha.
        // v6 se usó en un AAB manual; v7 = primer despliegue por CI/CD (toasts, branding,
        // orden de sesiones, robustez auditoría, pulido UI).
        versionCode = 7
        versionName = "2.1.0"
    }
    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
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
            // Firmar el bundle solo si hay keystore.properties (si no, .aab sin firmar).
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}