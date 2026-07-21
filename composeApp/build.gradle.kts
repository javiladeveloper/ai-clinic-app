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
    alias(libs.plugins.sqldelight)
}

// Base de datos local (cola offline): genera SaniaDb a partir de los .sq de
// composeApp/src/commonMain/sqldelight.
sqldelight {
    databases {
        create("SaniaDb") {
            packageName.set("pe.saniape.app.db")
        }
    }
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
            // Driver de la BD local (cola offline)
            implementation(libs.sqldelight.android)
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
            // Cola offline: base local para que ninguna escritura se pierda sin señal
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
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
            // Driver nativo de la BD local (cola offline)
            implementation(libs.sqldelight.native)
        }
        // Tests de la lógica pura crítica (traducción de ids temporales, orden de la cola).
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "pe.saniape.app"
    // SDK 36 (Android 16): Google Play EXIGE apuntar aquí desde el 30/08/2026 — sin
    // esto rechaza cualquier actualización, incluidas las correcciones urgentes.
    compileSdk = 36

    defaultConfig {
        applicationId = "pe.saniape.app"
        minSdk = 26
        targetSdk = 36
        // La TWA anterior (mismo packageId pe.saniape.app) llegó a versionCode 4;
        // esta app nativa debe subir con un código MAYOR para actualizar la ficha.
        // v6 = AAB manual; v7 = primer deploy CI/CD; v8 = mejoras staff (repetir cita,
        // buscador, caja, resumen semanal), fluidez, popup actualización, seguridad portal.
        // v9 = fix buscador fisio-gestor, form nueva sesión sin #N, versión en pantalla Más.
        // v10 = prueba del circuito CI completo con VERCEL_DEPLOY_HOOK ya configurado.
        // v11 = agilizar flujo del fisio (técnicas precargadas, chips, agendar próxima 1-tap,
        //       hora próxima, monto=saldo) + buscador de paciente al crear cita.
        // v12 = fix CI: publicar en PRUEBAS CERRADAS (track alpha, completed), no en interna.
        // v13 = Inicio no recarga ni parpadea al volver de otro tab (fluidez).
        // v14 = banner citas 7 días, login Google todas las cuentas, portal sin error si no vinculado.
        // v15 = OFFLINE: cola local (SQLDelight) para que ninguna escritura se pierda sin
        //       señal; se envía inline si hay red y se encola solo si falla; chip de pendientes.
        // v16 = indicador de carga visible en TODAS las pantallas (antes solo vivía en el
        //       header de Inicio, así que al borrar/crear en la ficha no se veía nada), con
        //       rótulo honesto (Cargando/Guardando/Eliminando/Actualizando) y estable durante
        //       toda la ráfaga; las pantallas se recargan solas al volver la señal (antes la
        //       lista quedaba vacía y parecía que se habían perdido los datos); borrar sesión
        //       borra también su pago, avisando si es de un día ya cerrado en caja.
        versionCode = 16
        versionName = "2.6.1"
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