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

    actual fun logoClinica(): String? = defaults.stringForKey("logo_clinica")

    actual fun setLogoClinica(url: String?) {
        if (url == null) defaults.removeObjectForKey("logo_clinica")
        else defaults.setObject(url, "logo_clinica")
    }

    actual fun nombreClinica(): String? = defaults.stringForKey("nombre_clinica")

    actual fun setNombreClinica(nombre: String?) {
        if (nombre == null) defaults.removeObjectForKey("nombre_clinica")
        else defaults.setObject(nombre, "nombre_clinica")
    }

    actual fun ultimaNovedadVista(): String? = defaults.stringForKey("novedad_vista")

    actual fun setUltimaNovedadVista(version: String) {
        defaults.setObject(version, "novedad_vista")
    }

    // iOS no expone la fecha de instalación vs actualización: si nunca se guardó
    // nada (ni modo, ni tema, ni clínica), es una instalación nueva.
    actual fun esInstalacionNueva(): Boolean =
        modoActivo() == null && tema() == null && nombreClinica() == null
}
