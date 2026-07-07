package pe.saniape.app.data

import platform.Foundation.NSUserDefaults

/**
 * iOS: persiste el modo/tema en NSUserDefaults (equivalente a SharedPreferences).
 * No requiere init: NSUserDefaults.standardUserDefaults está siempre disponible.
 */
actual object Preferencias {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun modoActivo(): String? = defaults.stringForKey("modo_activo")

    actual fun setModoActivo(modo: String?) {
        if (modo == null) defaults.removeObjectForKey("modo_activo")
        else defaults.setObject(modo, "modo_activo")
    }

    actual fun tema(): String? = defaults.stringForKey("tema")

    actual fun setTema(tema: String?) {
        if (tema == null) defaults.removeObjectForKey("tema")
        else defaults.setObject(tema, "tema")
    }
}
