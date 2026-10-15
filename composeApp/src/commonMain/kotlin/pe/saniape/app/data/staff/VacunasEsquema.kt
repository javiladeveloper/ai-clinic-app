package pe.saniape.app.data.staff

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.abs

/**
 * CARNÉ DE VACUNAS (Pediatría, pestaña 👶 Crecimiento → 💉 Vacunas). Gemelo PURO
 * de `lib/vacunas-esquema.ts` de la web: catálogo, el esquema nacional, el
 * estado de cada dosis (aplicada, pendiente, atrasada, próxima…), validación y
 * el cuerpo del POST/PATCH de /api/staff/vacunas.
 *
 * Esquema: NTS N° 246-MINSA/DGIESP-2026 "Norma Técnica de Salud que establece
 * el Esquema Nacional de Inmunizaciones" (RM N° 561-2026/MINSA), §6.2.2, §6.2.3
 * y Anexo 2 (dosis de rescate). Es APOYO: muestra lo que falta, no bloquea nada.
 */

data class VacunaCatalogo(val codigo: String, val nombre: String, val corto: String)

/** Vacunas que se pueden registrar. `otra` pide el nombre. GEMELO del catálogo de la web. */
val CATALOGO_VACUNAS: List<VacunaCatalogo> = listOf(
    VacunaCatalogo("bcg", "BCG (tuberculosis)", "BCG"),
    VacunaCatalogo("hvb", "Hepatitis B (HvB)", "HvB"),
    VacunaCatalogo("vrs_anticuerpo", "Anticuerpo monoclonal contra el VRS (nirsevimab)", "Anticuerpo VRS"),
    VacunaCatalogo("pentavalente", "Pentavalente (DPT-HvB-Hib)", "Pentavalente"),
    VacunaCatalogo("hexavalente", "Hexavalente (DPT-HvB-Hib-IPV)", "Hexavalente"),
    VacunaCatalogo("ipv", "Antipolio inactivada inyectable (IPV)", "IPV"),
    VacunaCatalogo("apo", "Antipolio oral (APO)", "APO"),
    VacunaCatalogo("rotavirus", "Rotavirus", "Rotavirus"),
    VacunaCatalogo("neumococo", "Neumococo (conjugada)", "Neumococo"),
    VacunaCatalogo("influenza", "Influenza", "Influenza"),
    VacunaCatalogo("spr", "Sarampión, paperas y rubéola (SPR)", "SPR"),
    VacunaCatalogo("sr", "Sarampión y rubéola (SR)", "SR"),
    VacunaCatalogo("varicela", "Varicela", "Varicela"),
    VacunaCatalogo("ama", "Antiamarílica (fiebre amarilla)", "AMA"),
    VacunaCatalogo("hva", "Hepatitis A (HvA)", "HvA"),
    VacunaCatalogo("dpt", "Difteria, pertussis y tétanos (DPT)", "DPT"),
    VacunaCatalogo("dt", "Difteria y tétanos (DT / dT)", "DT"),
    VacunaCatalogo("tdap", "Tdap (difteria, tétanos y tos ferina acelular)", "Tdap"),
    VacunaCatalogo("vph", "Virus del papiloma humano (VPH)", "VPH"),
    VacunaCatalogo("meningococo", "Meningococo", "Meningococo"),
    VacunaCatalogo("hib", "Haemophilus influenzae tipo b (Hib)", "Hib"),
    VacunaCatalogo("covid19", "COVID-19", "COVID-19"),
    VacunaCatalogo("dengue", "Dengue", "Dengue"),
    VacunaCatalogo("otra", "Otra vacuna", "Otra"),
)
private val VACUNA_POR_CODIGO = CATALOGO_VACUNAS.associateBy { it.codigo }

fun nombreVacuna(codigo: String, nombreLibre: String? = null): String {
    val libre = nombreLibre?.trim()?.takeIf { it.isNotEmpty() }
    if (codigo == "otra" && libre != null) return libre
    return VACUNA_POR_CODIGO[codigo]?.nombre ?: libre ?: codigo
}

fun cortoVacuna(codigo: String, nombreLibre: String? = null): String {
    val libre = nombreLibre?.trim()?.takeIf { it.isNotEmpty() }
    if (codigo == "otra" && libre != null) return libre
    return VACUNA_POR_CODIGO[codigo]?.corto ?: codigo
}

// ─── El esquema nacional (NTS 246-MINSA/DGIESP-2026) ─────────────────────────

data class DosisEsquema(
    /** Estable ("penta-1"): el mismo id de la web y del portal. */
    val id: String,
    /** Columna del carné ("2 meses"). */
    val grupo: String,
    /** Edad recomendada en meses (0 = al nacer). */
    val edadMeses: Int,
    val vacuna: String,
    /** Códigos que la cubren (el primero es el que se propone al registrar). */
    val acepta: List<String>,
    val dosis: String,
    /** Edad límite para aplicarla (Anexo 2); null = sin límite en la niñez. */
    val limiteMeses: Int?,
    /** Límite en días (HvB del recién nacido: hasta 7 días). */
    val limiteDias: Int? = null,
    /** El día del límite TODAVÍA se puede ("hasta los 8 meses CERO días"). */
    val limiteIncluido: Boolean = false,
    /** Atrasada recién desde esta edad (VPH: 9–11 años). */
    val atrasadaDesdeMeses: Int? = null,
    /** Solo si corresponde (anticuerpo VRS). Nunca "atrasada". */
    val segunIndicacion: Boolean = false,
    /** Se incorpora progresivamente (2.ª de varicela): nunca "atrasada". */
    val progresiva: Boolean = false,
    /** La cubre otra dosis aplicada con hexavalente (IPV n ← penta n). */
    val cubiertaPorHexaDe: String? = null,
    val nota: String? = null,
)

private val PENTA = listOf("pentavalente", "hexavalente")

val ESQUEMA_NACIONAL: List<DosisEsquema> = listOf(
    DosisEsquema("bcg", "Recién nacido", 0, "BCG", listOf("bcg"), "Única", 60,
        nota = "Después del año, previo descarte de TB (PPD)."),
    DosisEsquema("hvb-rn", "Recién nacido", 0, "Hepatitis B", listOf("hvb"), "Única", null, limiteDias = 7, limiteIncluido = true,
        nota = "Dentro de las 12 h de nacido (excepcionalmente hasta 7 días)."),
    DosisEsquema("vrs", "Recién nacido", 0, "Anticuerpo VRS", listOf("vrs_anticuerpo"), "Única", 6,
        segunIndicacion = true, nota = "Si la madre no se vacunó contra el VRS o por alto riesgo (temporada abril–setiembre)."),
    DosisEsquema("penta-1", "2 meses", 2, "Pentavalente / Hexavalente", PENTA, "1.ª", 84),
    DosisEsquema("ipv-1", "2 meses", 2, "IPV", listOf("ipv"), "1.ª", 84, cubiertaPorHexaDe = "penta-1"),
    // Rotavirus (NTS 246 §6.1.1.9 y Anexo 2): las DOS dosis hasta los 8 meses CERO días.
    DosisEsquema("rota-1", "2 meses", 2, "Rotavirus", listOf("rotavirus"), "1.ª", 8, limiteIncluido = true),
    DosisEsquema("neumo-1", "2 meses", 2, "Neumococo", listOf("neumococo"), "1.ª", 60),
    DosisEsquema("penta-2", "4 meses", 4, "Pentavalente / Hexavalente", PENTA, "2.ª", 84),
    DosisEsquema("ipv-2", "4 meses", 4, "IPV", listOf("ipv"), "2.ª", 84, cubiertaPorHexaDe = "penta-2"),
    DosisEsquema("rota-2", "4 meses", 4, "Rotavirus", listOf("rotavirus"), "2.ª", 8, limiteIncluido = true),
    DosisEsquema("neumo-2", "4 meses", 4, "Neumococo", listOf("neumococo"), "2.ª", 60),
    DosisEsquema("penta-3", "6 meses", 6, "Pentavalente / Hexavalente", PENTA, "3.ª", 84),
    DosisEsquema("ipv-3", "6 meses", 6, "IPV", listOf("ipv"), "3.ª", 84, cubiertaPorHexaDe = "penta-3"),
    DosisEsquema("flu-1", "6 meses", 6, "Influenza pediátrica", listOf("influenza"), "1.ª", 12),
    DosisEsquema("flu-2", "7 meses", 7, "Influenza pediátrica", listOf("influenza"), "2.ª", 12),
    DosisEsquema("spr-1", "12 meses", 12, "SPR", listOf("spr"), "1.ª", 60),
    DosisEsquema("neumo-3", "12 meses", 12, "Neumococo", listOf("neumococo"), "3.ª", 60),
    DosisEsquema("vari-1", "12 meses", 12, "Varicela", listOf("varicela"), "1.ª", 60),
    DosisEsquema("flu-12m", "12 meses", 12, "Influenza pediátrica", listOf("influenza"), "Anual (1 año)", 24),
    DosisEsquema("ama", "15 meses", 15, "Antiamarílica (AMA)", listOf("ama"), "Única", null),
    DosisEsquema("hva", "15 meses", 15, "Hepatitis A", listOf("hva"), "Única", 60),
    DosisEsquema("ref-1", "18 meses", 18, "DPT (+ IPV) / Hexavalente", listOf("dpt", "hexavalente"), "1.er refuerzo", 84),
    DosisEsquema("spr-2", "18 meses", 18, "SPR", listOf("spr"), "2.ª", 60),
    DosisEsquema("flu-2a", "2 años", 24, "Influenza pediátrica", listOf("influenza"), "Anual (2 años)", 36),
    DosisEsquema("vari-2", "2 años", 24, "Varicela", listOf("varicela"), "2.ª", 60,
        progresiva = true, nota = "Se incorpora progresivamente (NTS 246)."),
    DosisEsquema("flu-3a", "3 años", 36, "Influenza adulto", listOf("influenza"), "Anual (3 años)", 48),
    DosisEsquema("ref-2", "4 años", 48, "DPT", listOf("dpt"), "2.º refuerzo", 84),
    DosisEsquema("apo-ref", "4 años", 48, "Antipolio oral (APO)", listOf("apo"), "2.º refuerzo", 60),
    DosisEsquema("vph", "9 años", 108, "VPH", listOf("vph"), "Única", 228, atrasadaDesdeMeses = 144,
        nota = "Dosis única de 9 a 11 años; de rescate hasta los 18."),
)

/** Los grupos (columnas del carné) en orden de edad. */
val GRUPOS_ESQUEMA: List<String> = ESQUEMA_NACIONAL.map { it.grupo }.distinct()

/** Etiquetas de dosis (chips del registro libre): EXACTAMENTE las del esquema. */
val DOSIS_OPCIONES: List<String> = listOf(
    "1.ª", "2.ª", "3.ª", "Única", "1.er refuerzo", "2.º refuerzo", "Anual (1 año)", "Anual (2 años)", "Anual (3 años)",
)

// ─── Fechas ──────────────────────────────────────────────────────────────────

private val ISO_VAC = Regex("""^\d{4}-\d{2}-\d{2}$""")

private fun ultimoDiaDelMes(anio: Int, mes1: Int): Int {
    val sig = if (mes1 == 12) LocalDate(anio + 1, 1, 1) else LocalDate(anio, mes1 + 1, 1)
    return sig.plus(DatePeriod(days = -1)).dayOfMonth
}

/** 'YYYY-MM-DD' + n meses (el 31 de enero + 1 = el 28/29 de febrero). */
fun sumarMeses(iso: String, n: Int): String {
    val (y, m, d) = iso.take(10).split("-").map { it.toInt() }
    val total = (y * 12 + (m - 1)) + n
    val ny = total.floorDiv(12)
    val nm = total.mod(12)
    val dia = minOf(d, ultimoDiaDelMes(ny, nm + 1))
    return "$ny-${(nm + 1).toString().padStart(2, '0')}-${dia.toString().padStart(2, '0')}"
}

fun sumarDias(iso: String, n: Int): String {
    val f = LocalDate.parse(iso.take(10)).plus(DatePeriod(days = n))
    return "${f.year}-${f.monthNumber.toString().padStart(2, '0')}-${f.dayOfMonth.toString().padStart(2, '0')}"
}

/** Desde qué día la dosis ya no se aplica ("fuera de edad"). null = sin límite o sin nacimiento. */
fun fechaLimiteDosis(d: DosisEsquema, nac: String?): String? {
    if (nac == null) return null
    val base = when {
        d.limiteDias != null -> sumarDias(nac, d.limiteDias)
        d.limiteMeses != null -> sumarMeses(nac, d.limiteMeses)
        else -> null
    }
    return if (base != null && d.limiteIncluido) sumarDias(base, 1) else base
}

private fun diasEntreVac(a: String, b: String): Int =
    (LocalDate.parse(b.take(10)).toEpochDays().toLong() - LocalDate.parse(a.take(10)).toEpochDays().toLong()).toInt()

// ─── Registros y estado del carné ────────────────────────────────────────────

/** Una fila de `vacunas_paciente` (las del portal vienen sin observaciones ni profesional). */
data class VacunaPaciente(
    val id: String,
    val pacienteId: String = "",
    val vacunaCodigo: String,
    val vacunaNombre: String? = null,
    val dosis: String,
    val fechaAplicacion: String,
    val lote: String? = null,
    val establecimiento: String? = null,
    val externa: Boolean = false,
    val terapeutaId: String? = null,
    val observaciones: String? = null,
)

private fun JsonObject.txtVac(k: String): String? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

/** Fila del API (snake_case) → modelo. Sin id, código, dosis o fecha se descarta. */
fun vacunaDeJson(o: JsonObject): VacunaPaciente? {
    val id = o.txtVac("id") ?: return null
    val codigo = o.txtVac("vacuna_codigo") ?: return null
    val dosis = o.txtVac("dosis") ?: return null
    val fecha = o.txtVac("fecha_aplicacion")?.take(10)?.takeIf { ISO_VAC.matches(it) } ?: return null
    return VacunaPaciente(
        id = id, pacienteId = o.txtVac("paciente_id").orEmpty(), vacunaCodigo = codigo,
        vacunaNombre = o.txtVac("vacuna_nombre"), dosis = dosis, fechaAplicacion = fecha,
        lote = o.txtVac("lote"), establecimiento = o.txtVac("establecimiento"),
        externa = (o["externa"] as? JsonPrimitive)?.booleanOrNull == true,
        terapeutaId = o.txtVac("terapeuta_id"), observaciones = o.txtVac("observaciones"),
    )
}

enum class EstadoDosis(val clave: String, val texto: String) {
    APLICADA("aplicada", "Aplicada"),
    PENDIENTE("pendiente", "Le toca"),
    ATRASADA("atrasada", "Atrasada"),
    PROXIMA("proxima", "Próxima"),
    FUTURA("futura", "Más adelante"),
    FUERA_DE_EDAD("fuera_de_edad", "Fuera de edad"),
    SEGUN_INDICACION("segun_indicacion", "Según indicación"),
    SIN_FECHA("sin_fecha", "Falta la fecha de nacimiento");

    companion object {
        fun de(clave: String?): EstadoDosis? = entries.firstOrNull { it.clave == clave }
    }
}

data class DosisCarne(
    val dosis: DosisEsquema,
    val estado: EstadoDosis,
    /** Cuándo le toca (null sin fecha de nacimiento). */
    val fechaRecomendada: String?,
    /** Desde cuándo ya no se aplica (null = sin límite). */
    val fechaLimite: String?,
    /** El registro que la cubre (para IPV con hexavalente: el de la hexavalente). */
    val registro: VacunaPaciente?,
    val viaHexavalente: Boolean,
)

data class ResumenCarne(
    val aplicadas: Int,
    /** Casilleros que cuentan (sin "según indicación" ni "fuera de edad"). */
    val total: Int,
    val pendientes: Int,
    val atrasadas: Int,
    /** Las que le tocan (pendientes/atrasadas) y las de los próximos 30 días, por fecha. */
    val siguientes: List<DosisCarne>,
    /** Todo lo que le toca a su edad está aplicado. */
    val alDia: Boolean,
)

data class CarneVacunas(val dosis: List<DosisCarne>, val otras: List<VacunaPaciente>, val resumen: ResumenCarne)

/** Cuánto antes de la edad recomendada una dosis todavía "es" de ese casillero (al asignar por edad). */
private const val MARGEN_VENTANA_DIAS = 30

/** Como `norm` de la web: sin tildes, minúsculas, solo letras y dígitos. */
private fun normDosis(s: String?): String = normalizarTexto(s).filter { it in 'a'..'z' || it in '0'..'9' }

/**
 * El carné de un niño: el estado de cada dosis del esquema a la fecha [hoy].
 *  1. Cada registro cubre su casillero por código aceptado + dosis.
 *  2. Lo que no calzó, POR EDAD: al casillero libre cuya ventana contiene la fecha (o el más cercano).
 *  3. IPV n queda cubierta si la dosis n de pentavalente se aplicó con HEXAVALENTE.
 */
fun carneVacunas(
    fechaNacimiento: String?, hoy: String, registros: List<VacunaPaciente>?,
    esquema: List<DosisEsquema> = ESQUEMA_NACIONAL,
): CarneVacunas {
    val nac = fechaNacimiento?.take(10)?.takeIf { ISO_VAC.matches(it) }
    val regs = registros.orEmpty().sortedWith(compareBy<VacunaPaciente>({ it.fechaAplicacion }, { it.id }))
    val usados = mutableSetOf<String>()
    val asignado = mutableMapOf<String, VacunaPaciente>()

    // 1. Exacto: código aceptado + misma dosis.
    for (d in esquema) {
        val r = regs.firstOrNull { it.id !in usados && it.vacunaCodigo in d.acepta && normDosis(it.dosis) == normDosis(d.dosis) }
        if (r != null) { asignado[d.id] = r; usados.add(r.id) }
    }
    // 2. Por edad (etiqueta que no calzó).
    for (r in regs) {
        if (r.id in usados) continue
        val libres = esquema.filter { it.id !in asignado && r.vacunaCodigo in it.acepta }
        if (libres.isEmpty()) continue
        var elegido = libres.first()
        if (nac != null) {
            val f = r.fechaAplicacion
            fun distancia(d: DosisEsquema) = abs(diasEntreVac(sumarMeses(nac, d.edadMeses), f))
            fun enVentana(d: DosisEsquema): Boolean {
                val lim = fechaLimiteDosis(d, nac)
                return f >= sumarDias(sumarMeses(nac, d.edadMeses), -MARGEN_VENTANA_DIAS) && (lim == null || f < lim)
            }
            val dentro = libres.filter { enVentana(it) }
            // reduce: con empate se queda el primero (como la web).
            elegido = (dentro.ifEmpty { libres }).reduce { a, b -> if (distancia(b) < distancia(a)) b else a }
        }
        asignado[elegido.id] = r
        usados.add(r.id)
    }

    val dosis = esquema.map { d ->
        val fechaRecomendada = nac?.let { sumarMeses(it, d.edadMeses) }
        val fechaLimite = fechaLimiteDosis(d, nac)
        var registro = asignado[d.id]
        var viaHexa = false
        if (registro == null && d.cubiertaPorHexaDe != null) {
            val base = asignado[d.cubiertaPorHexaDe]
            if (base?.vacunaCodigo == "hexavalente") { registro = base; viaHexa = true }
        }
        val estado = when {
            registro != null -> EstadoDosis.APLICADA
            nac == null || fechaRecomendada == null -> if (d.segunIndicacion) EstadoDosis.SEGUN_INDICACION else EstadoDosis.SIN_FECHA
            fechaLimite != null && hoy >= fechaLimite -> EstadoDosis.FUERA_DE_EDAD
            d.segunIndicacion -> EstadoDosis.SEGUN_INDICACION
            hoy < fechaRecomendada -> if (diasEntreVac(hoy, fechaRecomendada) <= 30) EstadoDosis.PROXIMA else EstadoDosis.FUTURA
            d.progresiva -> EstadoDosis.PENDIENTE
            else -> {
                val desde = d.atrasadaDesdeMeses?.let { sumarMeses(nac, it) } ?: sumarDias(fechaRecomendada, 30)
                if (hoy > desde) EstadoDosis.ATRASADA else EstadoDosis.PENDIENTE
            }
        }
        DosisCarne(d, estado, fechaRecomendada, fechaLimite, registro, viaHexa)
    }

    val cuentan = dosis.filter { it.estado != EstadoDosis.SEGUN_INDICACION && it.estado != EstadoDosis.FUERA_DE_EDAD }
    val siguientes = dosis.filter { it.estado == EstadoDosis.PENDIENTE || it.estado == EstadoDosis.ATRASADA || it.estado == EstadoDosis.PROXIMA }
        .sortedBy { it.fechaRecomendada.orEmpty() }
    return CarneVacunas(
        dosis = dosis,
        otras = regs.filter { it.id !in usados },
        resumen = ResumenCarne(
            aplicadas = dosis.count { it.estado == EstadoDosis.APLICADA },
            total = cuentan.size,
            pendientes = dosis.count { it.estado == EstadoDosis.PENDIENTE },
            atrasadas = dosis.count { it.estado == EstadoDosis.ATRASADA },
            siguientes = siguientes,
            alDia = nac != null && dosis.none { it.estado == EstadoDosis.PENDIENTE || it.estado == EstadoDosis.ATRASADA },
        ),
    )
}

/** "Al día…", "2 atrasadas · 1 le toca", "Falta la fecha de nacimiento…" (igual que la web). */
fun textoResumenCarne(c: CarneVacunas, conFecha: Boolean): String {
    if (!conFecha) return "Falta la fecha de nacimiento para calcular el esquema"
    val partes = mutableListOf<String>()
    if (c.resumen.atrasadas > 0) partes.add("${c.resumen.atrasadas} ${if (c.resumen.atrasadas == 1) "atrasada" else "atrasadas"}")
    if (c.resumen.pendientes > 0) partes.add("${c.resumen.pendientes} ${if (c.resumen.pendientes == 1) "le toca" else "le tocan"}")
    return if (partes.isNotEmpty()) partes.joinToString(" · ") else "Al día con el esquema nacional"
}

/** "En la clínica", el establecimiento externo u "Otro establecimiento". */
fun lugarVacuna(v: VacunaPaciente): String =
    if (v.externa) v.establecimiento?.trim()?.takeIf { it.isNotEmpty() } ?: "Otro establecimiento" else "En la clínica"

// ─── Validación y cuerpo (POST/PATCH /api/staff/vacunas) ─────────────────────

/** GEMELOS de los CHECK de vacunas_paciente. */
object LimitesVacuna {
    const val DOSIS = 40
    const val NOMBRE = 120
    const val LOTE = 60
    const val ESTABLECIMIENTO = 160
    const val OBSERVACIONES = 1000
}

/** Lo que se escribió en el formulario. */
data class BorradorVacuna(
    val pacienteId: String,
    val vacunaCodigo: String,
    val vacunaNombre: String = "",
    val dosis: String = "",
    val fechaAplicacion: String = "",
    val lote: String = "",
    val establecimiento: String = "",
    val externa: Boolean = false,
    val observaciones: String = "",
)

/** Errores para mostrar (vacío = válida). [hoy] = el día de la clínica: la fecha no puede ser futura. */
fun validarVacuna(b: BorradorVacuna, hoy: String, fechaNacimiento: String? = null): List<String> {
    val e = mutableListOf<String>()
    if (b.pacienteId.isBlank()) e.add("Falta el paciente.")
    if (b.vacunaCodigo !in VACUNA_POR_CODIGO) e.add("Elige la vacuna.")
    if (b.vacunaCodigo == "otra" && b.vacunaNombre.isBlank()) e.add("Escribe el nombre de la vacuna.")
    if (b.vacunaNombre.trim().length > LimitesVacuna.NOMBRE) e.add("El nombre admite hasta ${LimitesVacuna.NOMBRE} caracteres.")
    val dosis = b.dosis.trim()
    if (dosis.isEmpty()) e.add("Indica la dosis.")
    else if (dosis.length > LimitesVacuna.DOSIS) e.add("La dosis admite hasta ${LimitesVacuna.DOSIS} caracteres.")
    if (b.fechaAplicacion.isNotEmpty()) {
        if (!ISO_VAC.matches(b.fechaAplicacion)) e.add("Fecha inválida.")
        else if (b.fechaAplicacion > hoy) e.add("La fecha no puede ser futura.")
    }
    if (b.lote.trim().length > LimitesVacuna.LOTE) e.add("El lote admite hasta ${LimitesVacuna.LOTE} caracteres.")
    if (b.establecimiento.trim().length > LimitesVacuna.ESTABLECIMIENTO) e.add("El establecimiento admite hasta ${LimitesVacuna.ESTABLECIMIENTO} caracteres.")
    if (b.observaciones.trim().length > LimitesVacuna.OBSERVACIONES) e.add("Las observaciones admiten hasta ${LimitesVacuna.OBSERVACIONES} caracteres.")
    // Como el formulario de la web: no antes del nacimiento (la base también lo rechaza).
    val nac = fechaNacimiento?.take(10)?.takeIf { ISO_VAC.matches(it) }
    if (nac != null && ISO_VAC.matches(b.fechaAplicacion) && b.fechaAplicacion < nac) e.add("La fecha es anterior al nacimiento.")
    return e
}

/**
 * Cuerpo del POST (con [b].pacienteId) o del PATCH (con [id]): camelCase del API.
 * Textos vacíos → null; el lugar solo si es externa (como la web).
 */
fun cuerpoVacuna(b: BorradorVacuna, id: String? = null): JsonObject = buildJsonObject {
    if (id != null) put("id", id) else put("pacienteId", b.pacienteId)
    fun txt(k: String, v: String, max: Int) {
        val t = v.trim().take(max)
        if (t.isEmpty()) put(k, JsonNull) else put(k, t)
    }
    put("vacunaCodigo", b.vacunaCodigo)
    txt("vacunaNombre", b.vacunaNombre, LimitesVacuna.NOMBRE)
    put("dosis", b.dosis.trim().take(LimitesVacuna.DOSIS))
    if (b.fechaAplicacion.isNotEmpty()) put("fechaAplicacion", b.fechaAplicacion)
    txt("lote", b.lote, LimitesVacuna.LOTE)
    put("externa", b.externa)
    txt("establecimiento", if (b.externa) b.establecimiento else "", LimitesVacuna.ESTABLECIMIENTO)
    txt("observaciones", b.observaciones, LimitesVacuna.OBSERVACIONES)
}

/** El borrador de un registro guardado (para editarlo). */
fun borradorDeVacuna(v: VacunaPaciente): BorradorVacuna = BorradorVacuna(
    pacienteId = v.pacienteId, vacunaCodigo = v.vacunaCodigo, vacunaNombre = v.vacunaNombre.orEmpty(),
    dosis = v.dosis, fechaAplicacion = v.fechaAplicacion, lote = v.lote.orEmpty(),
    establecimiento = v.establecimiento.orEmpty(), externa = v.externa, observaciones = v.observaciones.orEmpty(),
)
