package pe.saniape.app.data.offline

import kotlinx.datetime.Clock

/**
 * Caché de lectura: lo último que vio el profesional.
 *
 * El patrón es "mostrar y refrescar": la pantalla pinta AL INSTANTE lo guardado
 * y consulta al servidor por detrás; cuando llega la respuesta, se actualiza.
 * Sin esto cada pantalla arranca en blanco y el fisio espera a la red — en una
 * clínica con señal irregular eso se siente lento aunque el servidor vaya bien.
 *
 * La caché NUNCA es la verdad: es lo último conocido, y el servidor la corrige.
 * Por eso se guarda el JSON crudo — si el SELECT cambia, no hay que migrar nada.
 */
object CacheLectura {

    /** Lo guardado para esa clave, o null si no hay nada. */
    fun leer(clave: String): String? =
        runCatching {
            DbLocal.db.cacheQueries.leer(clave).executeAsOneOrNull()?.payload
        }.getOrNull()

    /**
     * Lo guardado SOLO si no es más viejo que [maxEdadMs]. Para datos que
     * envejecen mal (la agenda de hoy) conviene un límite corto; para la ficha
     * de un paciente, uno largo — sus datos no cambian solos.
     */
    fun leerFresco(clave: String, maxEdadMs: Long): String? =
        runCatching {
            val fila = DbLocal.db.cacheQueries.leer(clave).executeAsOneOrNull() ?: return null
            val edad = Clock.System.now().toEpochMilliseconds() - fila.guardado_en
            if (edad <= maxEdadMs) fila.payload else null
        }.getOrNull()

    /** Guarda la respuesta. Falla en silencio: la caché nunca rompe la pantalla. */
    fun guardar(clave: String, payload: String) {
        runCatching {
            DbLocal.db.cacheQueries.guardar(clave, payload, Clock.System.now().toEpochMilliseconds())
        }
    }

    fun borrar(clave: String) {
        runCatching { DbLocal.db.cacheQueries.borrar(clave) }
    }

    /** Limpia lo que nadie miró en [dias] días (por defecto, una semana). */
    fun purgar(dias: Int = 7) {
        runCatching {
            val limite = Clock.System.now().toEpochMilliseconds() - dias * 24L * 3600_000
            DbLocal.db.cacheQueries.purgarViejo(limite)
        }
    }

    // Claves: una función por cada cosa cacheada, para no repartir strings
    // sueltos por el código y que sea evidente qué hay guardado.
    fun claveListaPacientes(scope: String?): String = "pacientes:lista:${scope ?: "todos"}"
    fun claveFicha(pacienteId: String): String = "ficha:$pacienteId"
    fun claveAgenda(fecha: String): String = "agenda:$fecha"
}
