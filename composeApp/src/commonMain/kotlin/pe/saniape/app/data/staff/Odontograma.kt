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
)

/** Qué color lleva cada cara del diente, y si la pieza está ausente. */
data class PintadoDiente(
    val porSuperficie: Map<String, String?>,
    val ausente: Boolean,
)

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
    val ausente = hallazgosDelDiente.any { porId[it.hallazgoId]?.marcaAusente == true }

    // Primero lo realizado y después lo pendiente, para que lo pendiente pise.
    for (estadoBuscado in listOf("Realizado", "Pendiente")) {
        for (h in hallazgosDelDiente.filter { it.estado == estadoBuscado }) {
            val hal = porId[h.hallazgoId] ?: continue
            if (hal.marcaAusente) continue
            val color = if (estadoBuscado == "Realizado") COLOR_REALIZADO else hal.color
            val caras = h.superficies?.takeIf { it.isNotEmpty() } ?: SUPERFICIES
            for (c in caras) if (c in pintado) pintado[c] = color
        }
    }
    return PintadoDiente(pintado, ausente)
}

/**
 * Agrupa los hallazgos PENDIENTES en líneas de presupuesto, una por servicio.
 *
 * Un hallazgo de boca se cobra UNA vez por muchas piezas que tenga marcadas:
 * la profilaxis es un procedimiento de toda la boca. Las piezas se conservan
 * igual, porque sirven para saber dónde estaba.
 *
 * Devuelve también los hallazgos sin servicio asociado: no se pueden cobrar,
 * pero el odontólogo tiene que verlos para no olvidarlos.
 *
 * Usa el `ProcedimientoRef` que ya define PacientesRepo.kt: declarar otro en el
 * mismo paquete rompía la compilación entera por redeclaración.
 */
fun agruparPresupuesto(
    hallazgos: List<DienteHallazgo>,
    catalogo: List<HallazgoDental>,
    procedimientos: List<ProcedimientoRef>,
): Pair<List<LineaPresupuesto>, List<DienteHallazgo>> {
    val porId = catalogo.associateBy { it.id }
    val procPorId = procedimientos.associateBy { it.id }
    val abiertos = hallazgos.filter { it.estado == "Pendiente" }

    // Se acumula en estructuras mutables y se congela al final.
    data class Acum(
        val proc: ProcedimientoRef,
        val nombres: MutableList<String> = mutableListOf(),
        val piezas: MutableList<String> = mutableListOf(),
        val ids: MutableList<String> = mutableListOf(),
        var porBoca: Boolean = false,
    )

    val porProc = linkedMapOf<String, Acum>()
    val sinProcedimiento = mutableListOf<DienteHallazgo>()

    for (r in abiertos) {
        val hal = porId[r.hallazgoId]
        val proc = hal?.procedimientoId?.let { procPorId[it] }
        if (hal == null || proc == null) { sinProcedimiento.add(r); continue }

        val acum = porProc.getOrPut(proc.id) { Acum(proc) }
        // Cuando dos hallazgos distintos comparten servicio (caries y fractura
        // → resina), la línea debe nombrar los dos, no solo el primero.
        if (hal.nombre !in acum.nombres) acum.nombres.add(hal.nombre)
        if (r.diente !in acum.piezas) acum.piezas.add(r.diente)
        acum.ids.add(r.id)
        if (hal.porBoca || r.diente == "BOCA") acum.porBoca = true
    }

    val lineas = porProc.values.map { a ->
        val unitario = a.proc.precio
        LineaPresupuesto(
            procedimientoId = a.proc.id,
            nombre = a.proc.nombre,
            hallazgoNombre = a.nombres.joinToString(" / "),
            piezas = a.piezas.toList(),
            hallazgoIds = a.ids.toList(),
            precioUnitario = unitario,
            subtotal = if (a.porBoca) unitario else unitario * a.piezas.size,
            porBoca = a.porBoca,
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
