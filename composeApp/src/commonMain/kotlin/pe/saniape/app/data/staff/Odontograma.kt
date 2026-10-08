package pe.saniape.app.data.staff

/**
 * Lógica pura del odontograma: numeración FDI, pintado por prioridad y
 * agrupación en líneas de presupuesto.
 *
 * Gemelo de `lib/odontograma.ts` en la web. Si cambia una regla aquí, cambia
 * allá: el mismo paciente se abre desde el celular del odontólogo y desde la
 * computadora de recepción, y pintar distinto sería peor que no pintar.
 *
 * Sin Compose y sin red a propósito, para poder probarlo entero.
 */

data class HallazgoDental(
    val id: String,
    val nombre: String,
    val color: String,
    val clinicaId: String? = null,
    /** El servicio que se cobra por este hallazgo. El catálogo sembrado no lo asocia a todos. */
    val procedimientoId: String? = null,
    val marcaAusente: Boolean = false,
    val orden: Int = 0,
    val estado: String = "Activo",
    /**
     * El hallazgo es de la BOCA, no de una pieza (sarro, gingivitis). Se marca
     * sobre dientes para ubicarlo, pero en el presupuesto cuenta UNA vez: una
     * profilaxis no se cobra doce veces por estar marcada en doce dientes.
     */
    val porBoca: Boolean = false,
)

/** Una pieza con sus caras y lo que cuesta según ellas. */
data class PrecioPieza(val pieza: String, val caras: Int, val precio: Double)

data class DienteHallazgo(
    val id: String,
    val pacienteId: String,
    val diente: String,
    val hallazgoId: String,
    val superficies: List<String>? = null,
    /** "Pendiente" o "Realizado". */
    val estado: String = "Pendiente",
    val citaId: String? = null,
    val tratamientoId: String? = null,
    val notas: String? = null,
    val fecha: String = "",
    /** La sesión en que se resolvió (null = pendiente, o realizado antes de guardarse esto). */
    val sesionId: String? = null,
    /** Hallazgo de VARIAS piezas (puente, brackets, diastema): la pieza final. */
    val dienteHasta: String? = null,
)

data class LineaPresupuesto(
    val procedimientoId: String,
    val nombre: String,
    val hallazgoNombre: String,
    val piezas: List<String>,
    val hallazgoIds: List<String>,
    val precioUnitario: Double,
    val subtotal: Double,
    val porBoca: Boolean = false,
    /** Servicio con precio según caras: lo que vale cada pieza. null = precio único. */
    val preciosPieza: List<PrecioPieza>? = null,
)

/** Qué color lleva cada cara del diente, y si la pieza está ausente. */
data class PintadoDiente(
    val porSuperficie: Map<String, String?>,
    val ausente: Boolean,
    // ── Símbolos de la norma (NTS 150-MINSA/2019). Color hex: rojo (por hacer)
    //    o azul (hecho / existente). null = no aplica. Gemelo de la web.
    /** Corona: circunferencia alrededor de la pieza. */
    val corona: String? = null,
    /** Tratamiento de conductos: línea en la raíz. */
    val endodoncia: String? = null,
    /** Fractura pendiente: línea diagonal. */
    val fractura: String? = null,
    /** Extracción INDICADA (aún no hecha): aspa roja. La hecha es `ausente` (aspa azul). */
    val extraccion: String? = null,
    /** Siglas del recuadro: R (restaurada), TC (conductos), IMP (implante). */
    val siglas: List<String> = emptyList(),
)

/** Hallazgos que se registran de una pieza a otra (puente, brackets, diastema). Gemelo de la web. */
fun esHallazgoDeRango(nombre: String): Boolean =
    Regex("puente|pr[oó]tesis fija|ortod[oó]n|bracket|aparato|diastema|ed[eé]ntul", RegexOption.IGNORE_CASE)
        .containsMatchIn(nombre)

/** Qué símbolo de la norma corresponde a un hallazgo, por su nombre. Gemelo de `tipoDeHallazgo` (web). */
fun tipoDeHallazgo(nombre: String): String? {
    val n = nombre.lowercase()
    return when {
        Regex("corona|funda").containsMatchIn(n) -> "corona"
        Regex("endodoncia|conducto").containsMatchIn(n) -> "endodoncia"
        Regex("extracci|exodoncia").containsMatchIn(n) -> "extraccion"
        Regex("implante").containsMatchIn(n) -> "implante"
        Regex("fractura").containsMatchIn(n) -> "fractura"
        Regex("caries").containsMatchIn(n) -> "caries"
        Regex("restauraci|obturad|resina|amalgama").containsMatchIn(n) -> "restauracion"
        else -> null
    }
}

/**
 * Hallazgos de PIEZA ENTERA (no tienen caras que sumar): ausente, corona,
 * endodoncia, extracción, implante, perno y los de rango. Gemelo de
 * `esDePiezaEntera` en DientePanel.tsx.
 */
fun esDePiezaEntera(h: HallazgoDental?): Boolean =
    h != null && (h.marcaAusente ||
        Regex("ausente|corona|endodoncia|extracci|implante|perno", RegexOption.IGNORE_CASE).containsMatchIn(h.nombre) ||
        esHallazgoDeRango(h.nombre))

/** Orden fijo de las caras (O M D V L): "MO" y "OM" son la misma caries. */
fun ordenarCaras(caras: Collection<String>): List<String> = SUPERFICIES.filter { it in caras }

/** Cómo se lee un hallazgo ya tratado: la caries queda "restaurada", no "realizada". */
fun etiquetaHecho(nombre: String): String = when (tipoDeHallazgo(nombre)) {
    "caries", "fractura" -> "restaurada"
    "extraccion" -> "extraída"
    "endodoncia" -> "conductos hechos"
    else -> "realizado"
}

/** Azul estándar dental: trabajo ya realizado. */
const val COLOR_REALIZADO = "#1d6fa8"

/** Las cinco caras de un diente: oclusal, mesial, distal, vestibular, lingual. */
val SUPERFICIES = listOf("O", "M", "D", "V", "L")

/**
 * Orden VISUAL por fila, como mira el odontólogo la boca del paciente de
 * frente: arriba-derecha del paciente primero (18→11), luego arriba-izquierda
 * (21→28), y abajo igual. No es el orden numérico.
 */
val CUADRANTES_ADULTO: List<List<String>> = listOf(
    listOf("18", "17", "16", "15", "14", "13", "12", "11"),
    listOf("21", "22", "23", "24", "25", "26", "27", "28"),
    listOf("48", "47", "46", "45", "44", "43", "42", "41"),
    listOf("31", "32", "33", "34", "35", "36", "37", "38"),
)

/** Dentición de leche (cuadrantes 5 a 8). */
val CUADRANTES_DECIDUO: List<List<String>> = listOf(
    listOf("55", "54", "53", "52", "51"),
    listOf("61", "62", "63", "64", "65"),
    listOf("85", "84", "83", "82", "81"),
    listOf("71", "72", "73", "74", "75"),
)

/**
 * De qué color va cada cara de un diente.
 *
 * Prioridad: lo PENDIENTE manda sobre lo realizado. Un diente con una caries
 * sin tratar y una restauración vieja se ve rojo, porque lo que importa de un
 * vistazo es lo que falta por hacer.
 *
 * Un hallazgo sin superficies pinta las cinco caras (afecta al diente entero);
 * con superficies, solo esas.
 */
fun pintarDiente(
    hallazgosDelDiente: List<DienteHallazgo>,
    catalogo: List<HallazgoDental>,
): PintadoDiente {
    val porId = catalogo.associateBy { it.id }
    val pintado = SUPERFICIES.associateWith { null as String? }.toMutableMap()

    // El ausente se resuelve aparte: la UI tacha el diagrama entero.
    var ausente = hallazgosDelDiente.any { porId[it.hallazgoId]?.marcaAusente == true }
    var corona: String? = null
    var endodoncia: String? = null
    var fractura: String? = null
    var extraccion: String? = null
    val siglas = linkedSetOf<String>()

    // Primero lo realizado y después lo pendiente, para que lo pendiente pise.
    val orden = hallazgosDelDiente.filter { it.estado == "Realizado" } +
        hallazgosDelDiente.filter { it.estado != "Realizado" }
    for (h in orden) {
        val hal = porId[h.hallazgoId] ?: continue
        if (hal.marcaAusente) continue
        val hecho = h.estado == "Realizado"
        val color = if (hecho) COLOR_REALIZADO else hal.color
        // Los de varias piezas se dibujan como línea entre piezas, no pintando caras.
        if (esHallazgoDeRango(hal.nombre)) continue
        // Los que la norma dibuja con un SÍMBOLO, no pintando caras.
        when (tipoDeHallazgo(hal.nombre)) {
            "corona" -> { corona = color; continue }
            "endodoncia" -> {
                endodoncia = color
                if (hecho || hal.color == COLOR_REALIZADO) siglas.add("TC")
                continue
            }
            "implante" -> { siglas.add("IMP"); if (corona == null) corona = color; continue }
            "extraccion" -> {
                // Hecha = ya no está (aspa azul); indicada = aspa roja.
                if (hecho) ausente = true else extraccion = color
                continue
            }
            "fractura" -> if (!hecho) fractura = color
        }
        // Caries / fractura ya tratadas: la pieza quedó RESTAURADA (azul + R).
        val tipo = tipoDeHallazgo(hal.nombre)
        if (hecho && (tipo == "caries" || tipo == "fractura")) siglas.add("R")
        val caras = h.superficies?.takeIf { it.isNotEmpty() } ?: SUPERFICIES
        for (c in caras) if (c in pintado) pintado[c] = color
    }
    return PintadoDiente(pintado, ausente, corona, endodoncia, fractura, extraccion, siglas.toList())
}

/**
 * Precio de UNA pieza según cuántas caras tiene (resina simple/compuesta/compleja).
 * Gemelo de `precioSegunCaras` (lib/odontograma.ts): sin cara usa el tramo más
 * alto (pieza entera); si falta el tramo exacto, el más cercano por debajo y,
 * si no hay, el de arriba. null = el servicio no tiene tramos (precio único).
 */
fun precioSegunCaras(tramos: Map<String, Double>?, caras: Int): Double? {
    if (tramos == null) return null
    fun v(k: Int): Double? = tramos[k.toString()]?.takeIf { it > 0 }
    if ((1..3).none { v(it) != null }) return null
    val n = if (caras <= 0) 3 else minOf(caras, 3)
    for (k in n downTo 1) v(k)?.let { return it }
    for (k in n + 1..3) v(k)?.let { return it }
    return null
}

/**
 * Busca el servicio sugerido por palabra clave cuando el hallazgo no tiene uno
 * enlazado. Gemelo de `buscarProcedimientoSugerido` (lib/odontograma.ts).
 */
fun buscarProcedimientoSugerido(nombre: String, procedimientos: List<ProcedimientoRef>): ProcedimientoRef? {
    val n = nombre.lowercase()
    fun busca(re: String) = procedimientos.firstOrNull { Regex(re, RegexOption.IGNORE_CASE).containsMatchIn(it.nombre) }
    return when {
        Regex("caries|fractura|restauraci|empaste|resina").containsMatchIn(n) -> busca("resina|curaci[oó]n|empaste|obturaci[oó]n|restauraci[oó]n")
        Regex("sarro|gingivitis|profilaxis|limpieza|placa").containsMatchIn(n) -> busca("limpieza|profilaxis|destartraje")
        Regex("endodoncia").containsMatchIn(n) -> busca("endodoncia")
        Regex("extracci").containsMatchIn(n) -> busca("extracci[oó]n|cirug[ií]a")
        Regex("corona").containsMatchIn(n) -> busca("corona|pr[oó]tesis")
        Regex("blanqueamiento").containsMatchIn(n) -> busca("blanqueamiento")
        Regex("bruxismo").containsMatchIn(n) -> busca("placa|f[eé]rula")
        Regex("ortodoncia|bracket|frenillos|alineador|maloclusi").containsMatchIn(n) -> busca("ortodoncia|bracket|frenillo|alineador")
        else -> null
    }
}

/**
 * Agrupa los hallazgos PENDIENTES (sin tratamiento) en líneas de presupuesto, una
 * por servicio. Gemelo de `agruparPresupuesto` (lib/odontograma.ts): si cambia una
 * regla, cambia la otra.
 *
 * - Un hallazgo de boca se cobra UNA vez por muchas piezas que tenga marcadas.
 * - Pieza marcada ausente: lo registrado en ella ANTES ya no se cobra; lo de
 *   después (un implante planificado) sí.
 * - Trabajo previo (azul, sin servicio asignado a propósito) describe la boca: no se cobra.
 * - Sin servicio enlazado se sugiere por nombre entre [sugeribles] (en clínicas
 *   mixtas, solo los dentales; por defecto, todos).
 * - Servicio con precio según caras: cada pieza vale según sus caras.
 *
 * Devuelve también los hallazgos sin servicio: no se pueden cobrar, pero el
 * odontólogo tiene que verlos para no olvidarlos.
 */
fun agruparPresupuesto(
    hallazgos: List<DienteHallazgo>,
    catalogo: List<HallazgoDental>,
    procedimientos: List<ProcedimientoRef>,
    sugeribles: List<ProcedimientoRef> = procedimientos,
): Pair<List<LineaPresupuesto>, List<DienteHallazgo>> {
    val porId = catalogo.associateBy { it.id }
    val procPorId = procedimientos.associateBy { it.id }

    val ausenteDesde = hashMapOf<String, String>()
    for (r in hallazgos) {
        if (porId[r.hallazgoId]?.marcaAusente != true) continue
        val previa = ausenteDesde[r.diente]
        if (previa == null || r.fecha < previa) ausenteDesde[r.diente] = r.fecha
    }
    fun trabajoPrevio(h: HallazgoDental?) = h != null && h.color.equals(COLOR_REALIZADO, ignoreCase = true) && h.procedimientoId == null
    val abiertos = hallazgos.filter { r ->
        val h = porId[r.hallazgoId]
        r.estado == "Pendiente" && r.tratamientoId == null &&
            h?.marcaAusente != true && !trabajoPrevio(h) &&
            !(ausenteDesde[r.diente]?.let { r.fecha <= it } ?: false)
    }

    class Acum(val proc: ProcedimientoRef) {
        val nombres = mutableListOf<String>()
        val piezas = mutableListOf<String>()
        val ids = mutableListOf<String>()
        var porBoca = false
    }
    val porProc = linkedMapOf<String, Acum>()
    val sinProcedimiento = mutableListOf<DienteHallazgo>()
    // Caras de cada pieza en cada servicio (se juntan las de todos sus registros); null = pieza entera.
    val caras = hashMapOf<String, Set<String>?>()

    for (r in abiertos) {
        val hal = porId[r.hallazgoId]
        var proc = hal?.procedimientoId?.let { procPorId[it] }
        if (proc == null && hal != null) proc = buscarProcedimientoSugerido(hal.nombre, sugeribles)
        if (hal == null || proc == null) { sinProcedimiento.add(r); continue }

        val acum = porProc.getOrPut(proc.id) { Acum(proc) }
        if (hal.nombre !in acum.nombres) acum.nombres.add(hal.nombre)
        // Basta UN hallazgo de boca para que la línea sea de boca.
        if (hal.porBoca || r.diente == "BOCA") acum.porBoca = true
        if (r.diente !in acum.piezas) acum.piezas.add(r.diente)
        val clave = "${proc.id}|${r.diente}"
        val sup = r.superficies?.takeIf { it.isNotEmpty() }
        val tiene = caras.containsKey(clave)
        val previas = caras[clave]
        caras[clave] = when {
            tiene && previas == null -> null
            sup == null -> null
            !tiene -> sup.toSet()
            else -> previas!! + sup
        }
        acum.ids.add(r.id)
    }

    val lineas = porProc.values.map { a ->
        val unitario = a.proc.precio
        val tramos = a.proc.precioPorCaras
        var subtotal = if (a.porBoca) unitario else unitario * a.piezas.size
        var preciosPieza: List<PrecioPieza>? = null
        if (!a.porBoca && precioSegunCaras(tramos, 1) != null) {
            preciosPieza = a.piezas.map { p ->
                val n = caras["${a.proc.id}|$p"]?.size ?: 0
                PrecioPieza(p, n, precioSegunCaras(tramos, n) ?: unitario)
            }
            subtotal = preciosPieza.sumOf { it.precio }
        }
        LineaPresupuesto(
            procedimientoId = a.proc.id,
            nombre = a.proc.nombre,
            hallazgoNombre = a.nombres.joinToString(" / "),
            piezas = a.piezas.toList(),
            hallazgoIds = a.ids.toList(),
            precioUnitario = unitario,
            subtotal = subtotal,
            porBoca = a.porBoca,
            preciosPieza = preciosPieza,
        )
    }
    return lineas to sinProcedimiento
}

/**
 * Redacta un diagnóstico con lo que el odontólogo marcó.
 *
 * Agrupa por hallazgo, no por diente: "Caries en piezas 26, 27 y 36" es como
 * lo escribe un dentista; "26: caries. 27: caries." es un listado que nadie
 * quiere leer en una historia clínica.
 *
 * Se deja fuera lo que no es un problema a tratar hoy: lo ya Realizado y los
 * hallazgos que el catálogo pinta de azul (trabajo previo: "Corona existente").
 * Los de boca se redactan como generalizados a partir de tres piezas.
 */
/**
 * Hallazgos que ya nombran un cuadro general. Mismo patrón que la web
 * (lib/odontograma.ts): si cambia allá, cambia acá.
 */
private val YA_GENERALES = Regex(
    "generalizad|gingivitis|bruxismo|maloclusi|ortodoncia|bracket|profilaxis|limpieza",
    RegexOption.IGNORE_CASE,
)

/** Pseudo-dientes: hallazgos de la boca entera, no de una pieza. No se dibujan en el diagrama. */
val PSEUDO_DIENTES = setOf("BOCA", "GENERAL")

fun diagnosticoDesdeHallazgos(
    hallazgos: List<DienteHallazgo>,
    catalogo: List<HallazgoDental>,
): String {
    val utiles = catalogo.filter { it.color != COLOR_REALIZADO }
    val nombrePorId = utiles.associate { it.id to it.nombre }
    val esDeBoca = utiles.filter { it.porBoca }.map { it.nombre }.toSet()

    val piezasPorHallazgo = linkedMapOf<String, MutableList<String>>()
    for (h in hallazgos) {
        if (h.estado != "Pendiente") continue
        val nombre = nombrePorId[h.hallazgoId] ?: continue
        val lista = piezasPorHallazgo.getOrPut(nombre) { mutableListOf() }
        // Una pieza con dos caries en caras distintas se nombra UNA vez.
        if (h.diente !in lista) lista.add(h.diente)
    }
    if (piezasPorHallazgo.isEmpty()) return ""

    // Orden estable: el mismo odontograma da siempre el mismo texto.
    val frases = piezasPorHallazgo.entries.sortedBy { it.key }.map { (nombre, piezas) ->
        val ord = piezas.sortedWith(compareBy({ it.length }, { it }))
        // "BOCA" es un pseudo-diente: el hallazgo se marcó para la boca entera
        // (igual que en la web). Fuerza la redacción general aunque haya una
        // sola marca.
        if (nombre in esDeBoca || "BOCA" in ord) {
            if ("BOCA" in ord || ord.size >= 3) {
                // Hay hallazgos que YA son generales por definición: decir
                // "Gingivitis generalizado" es redundante (y mal concordado).
                if (YA_GENERALES.containsMatchIn(nombre)) nombre else "$nombre generalizado"
            } else {
                "$nombre en ${if (ord.size == 1) "zona de pieza" else "zona de piezas"} ${ord.joinToString(" y ")}"
            }
        } else {
            val lista = if (ord.size == 1) ord[0]
            else ord.dropLast(1).joinToString(", ") + " y " + ord.last()
            "$nombre en ${if (ord.size == 1) "pieza" else "piezas"} $lista"
        }
    }
    val texto = frases.joinToString(". ")
    return texto.replaceFirstChar { it.uppercase() } + "."
}

/** Cómo se crea el tratamiento de una línea del presupuesto. */
data class PlanTratamiento(
    val procedimientoId: String,
    val modalidad: String,
    val totalSesiones: Int?,
    val precioPaquete: Double?,
    val cantidadUnidades: Int?,
    val precioUnitario: Double,
    val precioAcordado: Double,
    val diagnostico: String,
    val hallazgoIds: List<String>,
)

/** ¿La línea es de la boca entera? (hallazgo de boca, o marcada en "BOCA"). */
fun esLineaDeBoca(l: LineaPresupuesto): Boolean = l.porBoca || "BOCA" in l.piezas

/**
 * El tratamiento que sale de una línea del presupuesto, respetando cómo
 * configuró el médico el cobro del servicio.
 *
 * Gemelo de `crearTratamientos` en components/odontologia/PresupuestoPanel.tsx:
 * - servicio por SESIONES → un paquete con las sesiones del primer tarifario;
 * - servicio SIMPLE, o hallazgo de boca → una sesión suelta (una profilaxis es
 *   un acto, no "12 unidades" por estar marcada en 12 dientes);
 * - lo demás → por UNIDADES: una por pieza (3 caries = 3 resinas).
 *
 * Dinero: el precio acordado es el subtotal de la línea, que ya descuenta el
 * caso de boca (se cobra una vez).
 */
fun planTratamiento(l: LineaPresupuesto, proc: ProcedimientoRef?): PlanTratamiento {
    val deBoca = esLineaDeBoca(l)
    val cantidad = if (deBoca) 1 else l.piezas.size
    val diag = when {
        deBoca && "BOCA" in l.piezas -> "${l.hallazgoNombre} (Boca completa)"
        deBoca -> "${l.hallazgoNombre} generalizado"
        else -> "${l.hallazgoNombre} en pieza(s) ${l.piezas.joinToString(", ")}"
    }
    return when {
        proc?.modoCobro == "sesiones" -> PlanTratamiento(
            procedimientoId = l.procedimientoId, modalidad = "Sesiones",
            totalSesiones = proc.tarifarios.firstOrNull()?.cantidadSesiones ?: 1,
            precioPaquete = l.subtotal, cantidadUnidades = null,
            precioUnitario = if (deBoca) l.subtotal else l.precioUnitario,
            precioAcordado = l.subtotal, diagnostico = diag, hallazgoIds = l.hallazgoIds,
        )
        proc?.modoCobro == "simple" || deBoca -> PlanTratamiento(
            procedimientoId = l.procedimientoId, modalidad = "Sesión suelta",
            totalSesiones = 1, precioPaquete = null, cantidadUnidades = null,
            precioUnitario = if (deBoca) l.subtotal else l.precioUnitario,
            precioAcordado = l.subtotal, diagnostico = diag, hallazgoIds = l.hallazgoIds,
        )
        else -> PlanTratamiento(
            procedimientoId = l.procedimientoId, modalidad = "Unidades",
            totalSesiones = null, precioPaquete = null, cantidadUnidades = cantidad,
            precioUnitario = l.precioUnitario, precioAcordado = l.subtotal,
            diagnostico = diag, hallazgoIds = l.hallazgoIds,
        )
    }
}

// ── Piezas por sesión ("¿Qué se le hizo hoy?" y "Piezas del plan") ────────────
// Gemelos de components/odontologia/PiezasTratadas.tsx y PlanPiezas.tsx. El
// registro (pasar a Realizado con la sesión, re-sincronizar Unidades) lo hace la
// web en /api/staff/sesion/estado y /api/staff/cita/completar (`piezas`).

/**
 * Lo que se puede marcar como hecho al completar una sesión dental: TODO lo
 * pendiente del paciente (en el sillón se trata lo que se pueda ese día, a veces
 * de otro plan) + lo que esta misma sesión ya había resuelto (al re-completarla).
 * Fuera: piezas ausentes y hallazgos azules del catálogo (trabajo previo).
 * Primero las del tratamiento de la sesión, luego por número de pieza.
 */
fun filasPiezasTratadas(
    hallazgos: List<DienteHallazgo>,
    catalogo: List<HallazgoDental>,
    sesionId: String?,
    tratamientoId: String?,
): List<DienteHallazgo> {
    val porId = catalogo.associateBy { it.id }
    fun pieza(r: DienteHallazgo) = if (r.diente == "BOCA") 0 else r.diente.toIntOrNull() ?: 999
    return hallazgos.filter { r ->
        val h = porId[r.hallazgoId] ?: return@filter false
        if (h.marcaAusente || h.color.equals(COLOR_REALIZADO, ignoreCase = true)) return@filter false
        r.estado == "Pendiente" || (sesionId != null && r.sesionId == sesionId)
    }.sortedWith(compareBy({ if (it.tratamientoId == tratamientoId) 0 else 1 }, { pieza(it) }))
}

/** Las filas de "Piezas del plan" de un tratamiento: las suyas + las que resolvieron sus sesiones. */
fun filasPlanPiezas(
    hallazgos: List<DienteHallazgo>,
    tratamientoId: String,
    sesionIds: Set<String>,
): List<DienteHallazgo> = hallazgos
    .filter { it.tratamientoId == tratamientoId || (it.sesionId != null && it.sesionId in sesionIds) }
    .sortedWith(compareBy({ if (it.estado == "Pendiente") 0 else 1 }, { it.diente.toIntOrNull() ?: 0 }))

/**
 * "2 de 4 hechas": se cuentan PIEZAS (pieza + hallazgo), no registros — dos
 * caries sueltas en la misma pieza son una sola resina y el plan cobró una.
 * Devuelve (hechas, total).
 */
fun contarPlanPiezas(filas: List<DienteHallazgo>): Pair<Int, Int> {
    fun clave(r: DienteHallazgo) = "${r.diente}|${r.hallazgoId}"
    val total = filas.map(::clave).toSet().size
    val pendientes = filas.filter { it.estado != "Realizado" }.map(::clave).toSet().size
    return (total - pendientes) to total
}

/** Suma una técnica al texto ("A + B") si todavía no está. Gemelo de `sumarTecnica` (web). */
fun sumarTecnica(texto: String, tecnica: String): String {
    val actuales = texto.split(" + ").map { it.trim() }.filter { it.isNotEmpty() }
    if (tecnica.isBlank() || actuales.any { it.equals(tecnica.trim(), ignoreCase = true) }) return texto
    return (actuales + tecnica.trim()).joinToString(" + ")
}

/** Índice CPO-D (permanente) o ceo-d (temporal). Gemelo de `calcularCPO` (web). */
data class IndiceCPO(val c: Int, val p: Int, val o: Int) {
    val total: Int get() = c + p + o
}

fun calcularCPO(
    hallazgos: List<DienteHallazgo>,
    catalogo: List<HallazgoDental>,
    permanente: Boolean,
): IndiceCPO {
    val porId = catalogo.associateBy { it.id }
    fun enRango(d: String): Boolean {
        val n = d.toIntOrNull() ?: return false
        return if (permanente) n in 11..48 else n in 51..85
    }
    val peso = mapOf('c' to 3, 'p' to 2, 'o' to 1)
    val categoria = mutableMapOf<String, Char>()
    fun poner(d: String, cat: Char) {
        val previa = categoria[d]
        if (previa == null || peso.getValue(cat) > peso.getValue(previa)) categoria[d] = cat
    }
    for (r in hallazgos) {
        if (!enRango(r.diente)) continue
        val h = porId[r.hallazgoId] ?: continue
        val n = h.nombre.lowercase()
        val pendiente = r.estado == "Pendiente"
        when {
            h.marcaAusente || Regex("ausente|perdid").containsMatchIn(n) -> {
                // En niños, una temporal que falta suele ser exfoliación natural: no cuenta.
                if (permanente) poner(r.diente, 'p')
            }
            Regex("extracci").containsMatchIn(n) -> {
                if (pendiente) poner(r.diente, if (permanente) 'c' else 'p')
                else if (permanente) poner(r.diente, 'p')
            }
            Regex("caries|fractura").containsMatchIn(n) -> poner(r.diente, if (pendiente) 'c' else 'o')
            Regex("restauraci|obturad|resina|amalgama").containsMatchIn(n) -> poner(r.diente, 'o')
        }
    }
    return IndiceCPO(
        c = categoria.values.count { it == 'c' },
        p = categoria.values.count { it == 'p' },
        o = categoria.values.count { it == 'o' },
    )
}
