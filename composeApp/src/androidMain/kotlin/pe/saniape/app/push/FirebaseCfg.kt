package pe.saniape.app.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

/**
 * Configuración de Firebase SIN el plugin google-services (inicialización programática):
 * así el proyecto COMPILA sin google-services.json y basta pegar aquí los 4 valores del
 * proyecto Firebase (Console → Configuración del proyecto → Tus apps → app Android).
 *
 * Mientras estén vacíos, TODO el pipeline de push es no-op (la app funciona normal).
 */
object FirebaseCfg {
    // ── Valores del proyecto Firebase "sania-f9daa" (google-services.json) ───
    const val API_KEY = "AIzaSyAmtUvatYwXm1gQvFk8PnoxaBBrgeWczdY"
    const val APP_ID = "1:209141436625:android:3abb00bafae1f699d875f0"
    const val PROJECT_ID = "sania-f9daa"
    const val SENDER_ID = "209141436625"
    // ─────────────────────────────────────────────────────────────────────────

    val activo: Boolean
        get() = API_KEY.isNotBlank() && APP_ID.isNotBlank() && PROJECT_ID.isNotBlank() && SENDER_ID.isNotBlank()

    /** Inicializa Firebase si hay valores (llamar desde Application.onCreate). */
    fun inicializar(context: Context) {
        if (!activo) return
        if (FirebaseApp.getApps(context).isNotEmpty()) return
        val opts = FirebaseOptions.Builder()
            .setApiKey(API_KEY)
            .setApplicationId(APP_ID)
            .setProjectId(PROJECT_ID)
            .setGcmSenderId(SENDER_ID)
            .build()
        FirebaseApp.initializeApp(context, opts)
    }
}
