import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)        // quítalo si tu AGP ya incluye Kotlin integrado
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)  // necesario para @Serializable (modelos de Supabase)
    alias(libs.plugins.ksp)                   // procesador de anotaciones de Room
}

// Lee las credenciales de Supabase desde local.properties (este archivo NO se sube a GitHub).
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.cocinacapital.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cocinacapital.app"
        minSdk = 26 // supabase-kt exige API 26 como mínimo
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "SUPABASE_URL", "\"${localProps.getProperty("SUPABASE_URL", "")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProps.getProperty("SUPABASE_ANON_KEY", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false // si activas R8, agrega reglas para kotlinx.serialization
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" } // con Kotlin 2.2+ puedes migrar a compilerOptions { }
    buildFeatures {
        compose = true
        buildConfig = true // necesario para BuildConfig.SUPABASE_URL
    }
}

dependencies {
    // --- AndroidX / Compose ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)    // collectAsStateWithLifecycle
    implementation(libs.androidx.lifecycle.viewmodel.compose)  // viewModel()
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended) // Explore, Wallet, Schedule, CloudOff
    debugImplementation(libs.androidx.compose.ui.tooling)

    // --- Supabase (supabase-kt) ---
    implementation(platform(libs.supabase.bom))  // la BOM alinea las versiones de todos los módulos
    implementation(libs.supabase.postgrest)      // consultas a las tablas
    implementation(libs.supabase.storage)        // opcional: URLs/archivos de Storage
    // implementation(libs.supabase.auth)        // Login/Registro (compañeros)
    implementation(libs.ktor.client.okhttp)      // motor HTTP requerido por supabase-kt en Android
    implementation(libs.kotlinx.serialization.json)

    // --- Room (SQLite local: preferencias y favoritos) ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // --- Coil 3 (imágenes desde URL) ---
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)     // Coil 3 necesita un motor de red explícito
}
