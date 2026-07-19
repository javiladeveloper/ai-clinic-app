package pe.saniape.app.data.offline

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import pe.saniape.app.db.SaniaDb

/**
 * Android: el driver necesita un Context. Hay que llamar a [init] una vez desde
 * Application.onCreate (mismo patrón que Preferencias).
 */
actual object DriverFactory {
    private var contexto: Context? = null

    fun init(context: Context) {
        if (contexto == null) contexto = context.applicationContext
    }

    actual fun crear(): SqlDriver {
        val ctx = contexto ?: error("DriverFactory.init(context) no fue llamado en Application.onCreate")
        return AndroidSqliteDriver(SaniaDb.Schema, ctx, "sania_offline.db")
    }
}
