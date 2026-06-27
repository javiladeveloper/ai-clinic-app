package pe.saniape.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Android: persiste el modo en SharedPreferences. Hay que llamar a [init] una vez
 * (desde Application.onCreate) para darle el Context.
 */
actual object Preferencias {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences("sania_prefs", Context.MODE_PRIVATE)
        }
    }

    actual fun modoActivo(): String? = prefs?.getString("modo_activo", null)

    actual fun setModoActivo(modo: String?) {
        prefs?.edit()?.apply {
            if (modo == null) remove("modo_activo") else putString("modo_activo", modo)
            apply()
        }
    }
}