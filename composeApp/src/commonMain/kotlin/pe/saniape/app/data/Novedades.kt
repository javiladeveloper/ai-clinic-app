package pe.saniape.app.data

/**
 * "Novedades de la versión X.Y.Z": lo que se mejoró o arregló, contado a la
 * clínica la PRIMERA vez que abre la app después de actualizar.
 *
 * La fuente es `novedades/<versionName>.txt` (raíz del repo), la MISMA que el CI
 * manda a Play Console: Gradle la embebe en [NovedadesGeneradas] al compilar.
 * Formato del archivo (ver docs/ci-cd-despliegue.md):
 *
 *   # comentario
 *   resumen para Play (≤ 500)
 *   ---
 *   detalle para la app (opcional)
 *
 * La app muestra el DETALLE si existe; si no, el resumen.
 */
object Novedades {

    /** Una línea del diálogo: título de grupo ("Odontología:") o viñeta. */
    data class Linea(val texto: String, val esTitulo: Boolean)

    data class NotasVersion(val version: String, val lineas: List<Linea>)

    /** Notas de la versión instalada, o null si esa versión no trae archivo. */
    fun deEstaVersion(): NotasVersion? = de(VersionApp.nombre)

    fun de(version: String): NotasVersion? =
        NovedadesGeneradas.porVersion[version]
            ?.let { parsear(it) }
            ?.takeIf { it.isNotEmpty() }
            ?.let { NotasVersion(version, it) }

    /**
     * Para "Más → Novedades": las de esta versión y, si no tiene (build de
     * desarrollo, o versión sin archivo), las más recientes que haya.
     */
    fun paraConsultar(): NotasVersion? =
        deEstaVersion() ?: NovedadesGeneradas.porVersion.keys
            .sortedWith(::compararVersiones).asReversed()
            .firstNotNullOfOrNull { de(it) }

    /** Convierte el texto del archivo en líneas para el diálogo. */
    fun parsear(texto: String): List<Linea> {
        val filas = texto.lines().filterNot { it.trimStart().startsWith("#") }
        val corte = filas.indexOfFirst { it.trim() == "---" }
        val detalle = if (corte >= 0) filas.drop(corte + 1) else emptyList()
        val resumen = if (corte >= 0) filas.take(corte) else filas
        val elegidas = detalle.takeIf { d -> d.any { it.isNotBlank() } } ?: resumen
        return elegidas.map { it.trim() }.filter { it.isNotEmpty() }.map { l ->
            val sinVineta = l.removePrefix("•").removePrefix("-").removePrefix("*").trim()
            val esVineta = sinVineta.length != l.length
            Linea(sinVineta, esTitulo = !esVineta && sinVineta.endsWith(":"))
        }
    }

    /**
     * ¿Hay que mostrar el diálogo al abrir?
     *  - Ya se vieron las de esta versión → no.
     *  - Instalación NUEVA (nunca se guardó una versión y el sistema dice que
     *    recién se instaló) → no: quien recién llega no tiene con qué comparar.
     *  - Esta versión no trae notas → no.
     *  En los dos últimos casos igual se marca como vista (ver [marcarVista]),
     *  así la siguiente versión se compara contra esta.
     */
    fun debeMostrar(
        ultimaVista: String?,
        versionActual: String,
        instalacionNueva: Boolean,
        hayNotas: Boolean,
    ): Boolean = when {
        ultimaVista == versionActual -> false
        ultimaVista == null && instalacionNueva -> false
        else -> hayNotas
    }

    /** Lo que decide App al arrancar, con las preferencias reales. */
    fun pendientesAlAbrir(): NotasVersion? {
        val notas = deEstaVersion()
        val mostrar = debeMostrar(
            ultimaVista = Preferencias.ultimaNovedadVista(),
            versionActual = VersionApp.nombre,
            instalacionNueva = Preferencias.esInstalacionNueva(),
            hayNotas = notas != null,
        )
        if (!mostrar) marcarVista()
        return if (mostrar) notas else null
    }

    fun marcarVista() = Preferencias.setUltimaNovedadVista(VersionApp.nombre)

    /** "2.15.10" > "2.15.9" (numérico por partes, no alfabético). */
    fun compararVersiones(a: String, b: String): Int {
        val pa = a.split('.').map { it.toIntOrNull() ?: 0 }
        val pb = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val d = (pa.getOrElse(i) { 0 }).compareTo(pb.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }
}
