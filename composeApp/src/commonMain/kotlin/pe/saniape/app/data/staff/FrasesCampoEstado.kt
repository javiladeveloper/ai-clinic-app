package pe.saniape.app.data.staff

/**
 * Estado del autocompletado abierto (FrasesCampo.kt) que vive entre llamadas:
 * la caché de lecturas, los envíos ya hechos y lo ya aprendido en una pantalla.
 * Puro y con reloj inyectable (tests). NO es seguro entre hilos por sí solo:
 * [FrasesCampoRepo] serializa las escrituras con un Mutex; las lecturas ven una
 * copia inmutable completa (nunca un mapa a medio escribir).
 */

/** Lo que identifica una lectura: la clínica, los campos (ordenados), la especialidad y el rubro. */
data class ClaveCacheFrases(val clinica: String, val campos: List<String>, val especialidadId: String?, val rubro: String?)

/**
 * Caché de lecturas de `/api/staff/frases` con vencimiento. Lo que aprende el
 * SERVIDOR (tricoscopía, estética) o lo que escribe otro profesional no pasa
 * por un POST de esta app: sin el [ttlMs], la pantalla no lo vería hasta cerrar
 * sesión. Además se invalida a mano tras guardar ([invalidar]).
 */
class CacheFrases(private val ttlMs: Long = TTL_POR_DEFECTO_MS, private val ahora: () -> Long) {
    private data class Entrada(val valor: Map<String, RespuestaFrasesCampo>, val vence: Long)

    @kotlin.concurrent.Volatile
    private var mapa: Map<ClaveCacheFrases, Entrada> = emptyMap()

    /** null = no hay, o ya venció (hay que volver a pedir). */
    fun leer(k: ClaveCacheFrases): Map<String, RespuestaFrasesCampo>? =
        mapa[k]?.takeIf { it.vence > ahora() }?.valor

    fun guardar(k: ClaveCacheFrases, valor: Map<String, RespuestaFrasesCampo>) {
        val t = ahora()
        // De paso se barren las vencidas (la caché no crece sin límite).
        mapa = mapa.filterValues { it.vence > t } + (k to Entrada(valor, t + ttlMs))
    }

    /** Toda lectura que incluya alguno de esos campos (en cualquier clínica): la próxima apertura vuelve a pedir. */
    fun invalidar(campos: Collection<String>) {
        if (campos.isEmpty()) return
        mapa = mapa.filterKeys { k -> k.campos.none { it in campos } }
    }

    fun limpiar() { mapa = emptyMap() }

    companion object {
        /** 10 minutos: una sesión de trabajo ve lo que aprendió la clínica sin pagar un viaje en cada apertura. */
        const val TTL_POR_DEFECTO_MS: Long = 10 * 60 * 1000L
    }
}

/**
 * Deduplicación de POSTs: el MISMO cuerpo (mismo campo, texto, antes, paciente,
 * especialidad) en la misma clínica viaja una sola vez por sesión. Cubre el
 * doble toque, el salir() + onCleared() de una pantalla y el doble guardado del
 * mismo registro. Si el envío falla se [libera] (otro guardado puede
 * reintentar). Tope de [max] marcas: al llenarse se vacía (lo peor que pasa es
 * un uso de más, y el servidor igual descarta lo que ya estaba en `antes`).
 */
class EnviadosFrases(private val max: Int = 200) {
    @kotlin.concurrent.Volatile
    private var marcas: Set<String> = emptySet()

    /** true = es nuevo (y queda marcado); false = ya se envió en esta sesión. */
    fun marcar(marca: String): Boolean {
        if (marca in marcas) return false
        marcas = (if (marcas.size >= max) emptySet() else marcas) + marca
        return true
    }

    fun liberar(marca: String) { marcas = marcas - marca }

    fun limpiar() { marcas = emptySet() }
}

/**
 * Lo ya aprendido en UNA pantalla que se autoguarda (evaluación psicológica;
 * gemelo del `useRef aprendido` de EspacioEvaluacionPsico.tsx). Se aprende al
 * cerrar un componente comparando con lo último aprendido (o con lo que tenía
 * al abrir): llamarlo dos veces seguidas (salir() y luego onCleared()) no
 * vuelve a mandar nada.
 */
class AprendizajePantalla {
    private var aprendido: Map<String, String>? = null
    private var habilitado = false

    /** Al cargar: lo que ya tenía no se aprende. Solo la PRIMERA carga cuenta (un "Reintentar" no lo pisa). */
    fun iniciar(textos: Map<String, String>?, puedeAprender: Boolean) {
        if (aprendido == null && textos != null) aprendido = textos
        habilitado = puedeAprender
    }

    /** Lo que cambió desde la última vez (y queda como aprendido). Vacío sin carga o en solo lectura. */
    fun tomarCambios(ahora: Map<String, String>): List<CambioFrase> {
        val previo = aprendido ?: return emptyList()
        if (!habilitado) return emptyList()
        aprendido = ahora
        return cambiosParaAprender(previo, ahora)
    }
}
