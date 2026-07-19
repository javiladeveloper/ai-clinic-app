package pe.saniape.app.data.offline

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import pe.saniape.app.db.SaniaDb

/** iOS: el driver nativo no necesita contexto. */
actual object DriverFactory {
    actual fun crear(): SqlDriver = NativeSqliteDriver(SaniaDb.Schema, "sania_offline.db")
}
