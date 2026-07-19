package pe.saniape.app.data.offline

import app.cash.sqldelight.db.SqlDriver
import pe.saniape.app.db.SaniaDb

/**
 * Base de datos local de la cola offline. El driver lo provee cada plataforma
 * (Android: AndroidSqliteDriver, iOS: NativeSqliteDriver).
 *
 * En Android hay que llamar a `DbLocal.init(context)` una vez desde
 * Application.onCreate (mismo patrón que Preferencias); en iOS no hace falta.
 */
expect object DriverFactory {
    fun crear(): SqlDriver
}

object DbLocal {
    private var instancia: SaniaDb? = null

    /** BD local (se crea la primera vez que se usa). */
    val db: SaniaDb
        get() = instancia ?: SaniaDb(DriverFactory.crear()).also { instancia = it }
}
