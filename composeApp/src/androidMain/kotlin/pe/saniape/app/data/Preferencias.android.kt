package pe.saniape.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Android: persiste el modo en SharedPreferences. Hay que llamar a [init] una vez
 * (desde Application.onCreate) para darle el Context.
 */
actual object Preferencias {
    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
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

    actual fun tema(): String? = prefs?.getString("tema", null)

    actual fun setTema(tema: String?) {
        prefs?.edit()?.apply {
            if (tema == null) remove("tema") else putString("tema", tema)
            apply()
        }
    }

    actual fun logoClinica(): String? = prefs?.getString("logo_clinica", null)

    actual fun setLogoClinica(url: String?) {
        prefs?.edit()?.apply {
            if (url == null) remove("logo_clinica") else putString("logo_clinica", url)
            apply()
        }
    }

    actual fun nombreClinica(): String? = prefs?.getString("nombre_clinica", null)

    actual fun setNombreClinica(nombre: String?) {
        prefs?.edit()?.apply {
            if (nombre == null) remove("nombre_clinica") else putString("nombre_clinica", nombre)
            apply()
        }
    }

    actual fun ultimaNovedadVista(): String? = prefs?.getString("novedad_vista", null)

    actual fun setUltimaNovedadVista(version: String) {
        prefs?.edit()?.putString("novedad_vista", version)?.apply()
    }

    actual fun esInstalacionNueva(): Boolean {
        val ctx = appContext ?: return false
        return runCatching {
            @Suppress("DEPRECATION")
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            info.firstInstallTime == info.lastUpdateTime
        }.getOrDefault(false)
    }
}
