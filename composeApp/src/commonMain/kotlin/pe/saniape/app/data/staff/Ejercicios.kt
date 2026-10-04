package pe.saniape.app.data.staff

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

/**
 * EJERCICIOS DE APOYO — tipos y reglas PURAS (sin red ni Compose, testeables).
 * Gemelo de `lib/ejercicios.ts` de la web: lo que el fisio le deja al paciente
 * para hacer en casa, elegido de la biblioteca (`ejercicios`, 600 con GIF) y con SU
 * dosis (series, repeticiones, días, lado, carga, indicaciones).
 *
 * SE INDICAN POR SESIÓN: los de la sesión #N valen hasta la siguiente, y también
 * se dejan "al terminar el tratamiento" para que siga en casa. Cada plan dice su
 * MOMENTO ('sesion' | 'alta'); vigente hay uno por tratamiento y el anterior queda
 * de historial.
 *
 * Aquí solo va lo que la pestaña del STAFF necesita para PINTAR (textos, filtros
 * de la biblioteca, "Indicar para", cumplimiento): portado tal cual de la web,
 * con sus mismos tests. Las escrituras y sus validaciones son del servidor
 * (POST /api/staff/ejercicios/plan, ver EjerciciosRepo); lo del PACIENTE llega ya
 * armado (data/MisEjerciciosRepo.kt). Contrato: docs/app-contrato-ejercicios.md.
 */

// ─── Biblioteca ──────────────────────────────────────────────────────────────

/** Un ejercicio de la biblioteca (tabla `ejercicios`). */
data class EjercicioBiblioteca(
    val id: String,
    val slug: String = "",
    val nombre: String,
    val zona: String = "",
    val zonaNombre: String? = null,
    val zonaOrden: Int? = null,
    /** movilidad | estiramiento | fortalecimiento | isometrico | equilibrio | respiracion | relajacion | neurodinamia | automasaje */
    val objetivo: String? = null,
    /** de_pie | sentado | boca_arriba | boca_abajo | de_lado | cuadrupedia | arrodillado | plancha | cualquiera */
    val posicion: String? = null,
    val materiales: List<String> = emptyList(),
    val condiciones: List<String> = emptyList(),
    /** basico | intermedio | avanzado */
    val dificultad: String? = null,
    val pasos: List<String> = emptyList(),
    val series: Int? = null,
    val repeticiones: Int? = null,
    val descansoSeg: Int? = null,
    val diasSemana: Int? = null,
    val gifUrl: String? = null,
    val posturaInicialUrl: String? = null,
    val posturaFinalUrl: String? = null,
    /** 'aprobado' = lo revisó un fisio. Los 'pendiente' los redactó una IA: se marcan. */
    val estadoRevision: String = "pendiente",
    /** null = biblioteca global de Sania; con valor = ejercicio propio de la clínica. */
    val clinicaId: String? = null,
)

/** Columnas que pide la biblioteca (las mismas en la web y en la app: `COLS_EJERCICIO`). */
const val COLS_EJERCICIO =
    "id, slug, nombre, zona, zona_nombre, zona_orden, objetivo, posicion, materiales, condiciones, dificultad, pasos, " +
        "series, repeticiones, descanso_seg, dias_semana, gif_url, postura_inicial_url, postura_final_url, estado_revision, clinica_id"

data class OpcionEjercicio(val id: String, val nombre: String, val icono: String = "")

val OBJETIVOS: List<OpcionEjercicio> = listOf(
    OpcionEjercicio("movilidad", "Movilidad", "🔄"),
    OpcionEjercicio("estiramiento", "Estiramiento", "🧘"),
    OpcionEjercicio("fortalecimiento", "Fortalecimiento", "💪"),
    OpcionEjercicio("isometrico", "Isométrico", "✊"),
    OpcionEjercicio("equilibrio", "Equilibrio", "⚖️"),
    OpcionEjercicio("respiracion", "Respiración", "🌬️"),
    OpcionEjercicio("relajacion", "Relajación", "😌"),
    OpcionEjercicio("neurodinamia", "Neurodinamia", "⚡"),
    OpcionEjercicio("automasaje", "Automasaje", "👐"),
)

val POSICIONES: List<OpcionEjercicio> = listOf(
    OpcionEjercicio("de_pie", "De pie"),
    OpcionEjercicio("sentado", "Sentado"),
    OpcionEjercicio("boca_arriba", "Boca arriba"),
    OpcionEjercicio("boca_abajo", "Boca abajo"),
    OpcionEjercicio("de_lado", "De lado"),
    OpcionEjercicio("cuadrupedia", "En cuatro apoyos"),
    OpcionEjercicio("arrodillado", "Arrodillado"),
    OpcionEjercicio("plancha", "En plancha"),
    OpcionEjercicio("cualquiera", "Cualquier posición"),
)

val DIFICULTADES: List<OpcionEjercicio> = listOf(
    OpcionEjercicio("basico", "Básico"),
    OpcionEjercicio("intermedio", "Intermedio"),
    OpcionEjercicio("avanzado", "Avanzado"),
)

private val MATERIAL_NOMBRE: Map<String, String> = mapOf(
    "banda_elastica" to "Banda elástica", "liga" to "Liga", "silla" to "Silla", "colchoneta" to "Colchoneta",
    "baston" to "Bastón o palo", "peso_ligero" to "Peso ligero", "toalla" to "Toalla", "pelota" to "Pelota",
    "mesa" to "Mesa", "cama" to "Cama", "escalon" to "Escalón", "rodillo" to "Rodillo", "plastilina" to "Plastilina",
    "almohada" to "Almohada", "pared" to "Pared", "libro" to "Libro", "otros" to "Otros",
)

/**
 * Lo que hay en cualquier casa. "Sin material" estricto deja fuera a todo lo que
 * usa silla, pared o colchoneta; al fisio le sirve "sin equipo especial", no "sin nada".
 */
val MATERIAL_DE_CASA: List<String> = listOf("silla", "colchoneta", "pared", "mesa", "cama", "toalla", "almohada", "libro")

private fun capitalizar(s: String): String = if (s.isEmpty()) s else s.substring(0, 1).uppercase() + s.substring(1)

fun nombreMaterial(id: String): String = MATERIAL_NOMBRE[id] ?: capitalizar(id.replace('_', ' '))

/** Siglas y nombres propios que la capitalización simple rompería. */
private val CONDICION_NOMBRE: Map<String, String> = mapOf(
    "dolor_mandibular_atm" to "Dolor mandibular (ATM)",
    "epoc" to "EPOC",
    "post_covid" to "Post COVID",
    "postoperatorio_lca" to "Postoperatorio de LCA",
    "tendinopatia_manguito_rotador" to "Tendinopatía del manguito rotador",
    "tendinopatia_aquiles" to "Tendinopatía de Aquiles",
    "sindrome_cintilla_iliotibial" to "Síndrome de la cintilla iliotibial",
    "sindrome_piramidal" to "Síndrome del piramidal",
    "tunel_carpiano" to "Túnel carpiano",
    "ciatica" to "Ciática",
    "cifosis_postural" to "Cifosis postural",
    "rigidez_toracica" to "Rigidez torácica",
    "bursitis_trocanterica" to "Bursitis trocantérica",
    "debilidad_gluteo_medio" to "Debilidad del glúteo medio",
    "protesis_cadera" to "Prótesis de cadera",
    "protesis_rodilla" to "Prótesis de rodilla",
    "lesion_meniscal" to "Lesión meniscal",
    "diastasis_abdominal" to "Diástasis abdominal",
    "disfuncion_piso_pelvico" to "Disfunción del piso pélvico",
    "hipertonia_piso_pelvico" to "Hipertonía del piso pélvico",
    "dolor_pelvico" to "Dolor pélvico",
    "ansiedad_estres" to "Ansiedad y estrés",
    "reeducacion_respiratoria" to "Reeducación respiratoria",
    "rehabilitacion_neurologica" to "Rehabilitación neurológica",
    "riesgo_de_caidas" to "Riesgo de caídas",
    "limitacion_apertura_bucal" to "Limitación de la apertura bucal",
    "postfractura_muneca" to "Postfractura de muñeca",
    "rigidez_codo_muneca" to "Rigidez de codo y muñeca",
    "dolor_codo_muneca_mano" to "Dolor de codo, muñeca o mano",
    "epicondilitis" to "Epicondilitis",
    "epitrocleitis" to "Epitrocleítis",
    "pinzamiento_subacromial" to "Pinzamiento subacromial",
    "discinesia_escapular" to "Discinesia escapular",
)

/** `hombro_congelado` → "Hombro congelado". */
fun nombreCondicion(id: String): String = CONDICION_NOMBRE[id] ?: capitalizar(id.replace('_', ' '))

/**
 * Sin tildes ni mayúsculas, para buscar "flexion" y encontrar "Flexión". La web
 * usa `normalize('NFD')`, que en común no existe: se cubren las letras del español.
 */
fun normalizarTexto(s: String?): String = buildString {
    for (ch in s.orEmpty().lowercase()) {
        append(
            when (ch) {
                'á', 'à', 'ä', 'â', 'ã' -> 'a'
                'é', 'è', 'ë', 'ê' -> 'e'
                'í', 'ì', 'ï', 'î' -> 'i'
                'ó', 'ò', 'ö', 'ô', 'õ' -> 'o'
                'ú', 'ù', 'ü', 'û' -> 'u'
                'ñ' -> 'n'
                'ç' -> 'c'
                else -> ch
            }
        )
    }
}.trim()

data class FiltrosBiblioteca(
    val texto: String = "",
    val zona: String? = null,
    val condicion: String? = null,
    val objetivo: String? = null,
    val posicion: String? = null,
    /** 'sin_equipo' = solo cosas de casa; o un material concreto ('banda_elastica'). */
    val equipo: String? = null,
    val dificultad: String? = null,
    /** Solo los que aprobó un fisio. */
    val soloRevisados: Boolean = false,
) {
    val hayFiltros: Boolean
        get() = texto.isNotEmpty() || !zona.isNullOrEmpty() || !condicion.isNullOrEmpty() || !objetivo.isNullOrEmpty() ||
            !posicion.isNullOrEmpty() || !equipo.isNullOrEmpty() || !dificultad.isNullOrEmpty() || soloRevisados
}

fun sinEquipoEspecial(materiales: List<String>?): Boolean = materiales.orEmpty().all { it in MATERIAL_DE_CASA }

fun filtrarBiblioteca(lista: List<EjercicioBiblioteca>, f: FiltrosBiblioteca): List<EjercicioBiblioteca> {
    val palabras = normalizarTexto(f.texto).split(Regex("\\s+")).filter { it.isNotEmpty() }
    return lista.filter { e ->
        if (!f.zona.isNullOrEmpty() && e.zona != f.zona) return@filter false
        if (!f.condicion.isNullOrEmpty() && f.condicion !in e.condiciones) return@filter false
        if (!f.objetivo.isNullOrEmpty() && e.objetivo != f.objetivo) return@filter false
        if (!f.posicion.isNullOrEmpty() && e.posicion != f.posicion) return@filter false
        if (!f.dificultad.isNullOrEmpty() && e.dificultad != f.dificultad) return@filter false
        if (f.soloRevisados && e.estadoRevision != "aprobado") return@filter false
        if (f.equipo == "sin_equipo") { if (!sinEquipoEspecial(e.materiales)) return@filter false }
        else if (!f.equipo.isNullOrEmpty() && f.equipo !in e.materiales) return@filter false
        if (palabras.isNotEmpty()) {
            // Todas las palabras, en el nombre, la zona o los malestares.
            val pajar = normalizarTexto((listOf(e.nombre, e.zonaNombre.orEmpty()) + e.condiciones.map(::nombreCondicion)).joinToString(" "))
            if (!palabras.all { it in pajar }) return@filter false
        }
        true
    }
}

data class OpcionFiltro(val id: String, val nombre: String, val total: Int)

data class OpcionesBiblioteca(val zonas: List<OpcionFiltro>, val condiciones: List<OpcionFiltro>, val materiales: List<OpcionFiltro>)

/**
 * Zonas (en su orden) y malestares presentes, con cuántos ejercicios tiene cada
 * uno. Los malestares y materiales se acotan a la [zona] elegida; las zonas no.
 */
fun opcionesDeBiblioteca(lista: List<EjercicioBiblioteca>, zona: String? = null): OpcionesBiblioteca {
    data class Z(val nombre: String, val orden: Int, var total: Int)
    val zonas = LinkedHashMap<String, Z>()
    val condiciones = LinkedHashMap<String, Int>()
    val materiales = LinkedHashMap<String, Int>()
    for (e in lista) {
        val z = zonas.getOrPut(e.zona) { Z(e.zonaNombre?.takeIf { it.isNotEmpty() } ?: e.zona, e.zonaOrden ?: 99, 0) }
        z.total++
        if (!zona.isNullOrEmpty() && e.zona != zona) continue
        for (c in e.condiciones) condiciones[c] = (condiciones[c] ?: 0) + 1
        for (m in e.materiales) materiales[m] = (materiales[m] ?: 0) + 1
    }
    return OpcionesBiblioteca(
        zonas = zonas.entries.sortedBy { it.value.orden }.map { (id, z) -> OpcionFiltro(id, z.nombre, z.total) },
        // La web ordena con localeCompare('es'); aquí, sin tildes (mismo resultado en los nombres del catálogo).
        condiciones = condiciones.entries.map { (id, total) -> OpcionFiltro(id, nombreCondicion(id), total) }
            .sortedBy { normalizarTexto(it.nombre) },
        materiales = materiales.entries.map { (id, total) -> OpcionFiltro(id, nombreMaterial(id), total) }
            .sortedByDescending { it.total },
    )
}

// ─── Plan del paciente ───────────────────────────────────────────────────────

/** Lo de la biblioteca que viaja con cada ejercicio del plan (GIF, pasos, materiales). */
data class EjercicioDeItem(
    val id: String,
    val nombre: String? = null,
    val zonaNombre: String? = null,
    val objetivo: String? = null,
    val posicion: String? = null,
    val materiales: List<String> = emptyList(),
    val pasos: List<String> = emptyList(),
    val gifUrl: String? = null,
    val posturaInicialUrl: String? = null,
    val posturaFinalUrl: String? = null,
    val estadoRevision: String? = null,
)

/** Un ejercicio del plan con SU dosis (tabla `plan_ejercicios_items`). */
data class ItemPlanEjercicios(
    val id: String,
    val planId: String = "",
    val ejercicioId: String = "",
    val ejercicioNombre: String = "",
    val orden: Int = 0,
    val series: Int = 1,
    val repeticiones: Int? = null,
    val sostenerSeg: Int? = null,
    val descansoSeg: Int? = null,
    val vecesAlDia: Int = 1,
    /** 1 = lunes … 7 = domingo. */
    val dias: List<Int> = emptyList(),
    /** derecho | izquierdo | ambos | null. */
    val lado: String? = null,
    val carga: String? = null,
    val indicaciones: String? = null,
    val dolorMaximo: Int? = null,
    /** Quitar es apagar, no borrar: solo cuentan los activos. */
    val activo: Boolean = true,
    val createdAt: String = "",
    val ejercicio: EjercicioDeItem? = null,
)

/** Lo que el paciente marcó como hecho (tabla `plan_ejercicios_registros`). */
data class RegistroEjercicio(
    val itemId: String,
    val fecha: String,
    val completado: Boolean,
    val dolor: Int? = null,
    val comentario: String? = null,
)

/** La sesión en la que se indicaron (solo momento 'sesion'). */
data class SesionDePlan(val id: String, val numero: Int, val fecha: String)

data class PlanEjercicios(
    val id: String,
    val pacienteId: String = "",
    val tratamientoId: String? = null,
    val sesionId: String? = null,
    val terapeutaId: String? = null,
    /** 'sesion' = indicados en una sesión, hasta la siguiente · 'alta' = al terminar el tratamiento, para casa. */
    val momento: String = "sesion",
    val sesion: SesionDePlan? = null,
    val titulo: String = "",
    val motivo: String? = null,
    val indicacionesGenerales: String? = null,
    val precauciones: String? = null,
    val fechaInicio: String = "",
    val fechaFin: String? = null,
    /** activo | pausado | finalizado. */
    val estado: String = "activo",
    val visiblePaciente: Boolean = true,
    val token: String? = null,
    val creadoPorNombre: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val items: List<ItemPlanEjercicios> = emptyList(),
) {
    /** Los ejercicios que cuentan (los quitados quedan apagados). */
    val activos: List<ItemPlanEjercicios> get() = items.filter { it.activo }
}

const val MAX_EJERCICIOS_PLAN = 30

data class DiaSemana(val n: Int, val letra: String, val corto: String, val nombre: String)

val DIAS_SEMANA: List<DiaSemana> = listOf(
    DiaSemana(1, "L", "Lun", "Lunes"),
    DiaSemana(2, "M", "Mar", "Martes"),
    DiaSemana(3, "X", "Mié", "Miércoles"),
    DiaSemana(4, "J", "Jue", "Jueves"),
    DiaSemana(5, "V", "Vie", "Viernes"),
    DiaSemana(6, "S", "Sáb", "Sábado"),
    DiaSemana(7, "D", "Dom", "Domingo"),
)

/**
 * "5 días por semana" de la biblioteca → qué días concretos. Repartidos con
 * descanso entre medias cuando son pocos (3 → lunes, miércoles, viernes).
 */
fun diasPorDefecto(diasSemana: Int?): List<Int> = when (diasSemana?.takeIf { it != 0 } ?: 5) {
    1 -> listOf(1)
    2 -> listOf(2, 4)
    3 -> listOf(1, 3, 5)
    4 -> listOf(1, 2, 4, 5)
    5 -> listOf(1, 2, 3, 4, 5)
    6 -> listOf(1, 2, 3, 4, 5, 6)
    else -> if ((diasSemana ?: 0) >= 7) listOf(1, 2, 3, 4, 5, 6, 7) else listOf(1, 2, 3, 4, 5)
}

/** La dosis de un ejercicio (lo que la biblioteca sugiere antes de entrar al plan). */
data class DosisEjercicio(
    val series: Int,
    val repeticiones: Int?,
    val sostenerSeg: Int?,
    val descansoSeg: Int?,
    val vecesAlDia: Int,
    val dias: List<Int>,
)

private fun acotar(v: Int?, min: Int, max: Int): Int? = v?.takeIf { it in min..max }

/**
 * La dosis con la que entra un ejercicio al plan: la sugerida de la biblioteca.
 * En la app solo sirve de VISTA PREVIA en el buscador: la dosis real la pone el
 * servidor al `agregar` (con esta misma regla).
 */
fun dosisSugerida(e: EjercicioBiblioteca): DosisEjercicio = DosisEjercicio(
    series = acotar(e.series, 1, 20) ?: 3,
    repeticiones = acotar(e.repeticiones, 1, 200) ?: 10,
    sostenerSeg = null,
    descansoSeg = acotar(e.descansoSeg, 0, 600) ?: 30,
    vecesAlDia = 1,
    dias = diasPorDefecto(e.diasSemana),
)

/** "3 series × 10 repeticiones" · "3 series × 20 s sostenido" · "2 series × 5 rep. de 10 s". */
fun textoDosis(series: Int, repeticiones: Int?, sostenerSeg: Int?): String {
    val s = "$series ${if (series == 1) "serie" else "series"}"
    val reps = repeticiones?.takeIf { it != 0 }
    val sostener = sostenerSeg?.takeIf { it != 0 }
    if (reps != null && sostener != null) return "$s × $reps rep. de $sostener s"
    if (sostener != null) return "$s × $sostener s sostenido"
    return "$s × ${repeticiones ?: "—"} ${if (repeticiones == 1) "repetición" else "repeticiones"}"
}

fun textoDosis(i: ItemPlanEjercicios): String = textoDosis(i.series, i.repeticiones, i.sostenerSeg)
fun textoDosis(d: DosisEjercicio): String = textoDosis(d.series, d.repeticiones, d.sostenerSeg)

/** [1,2,3,4,5] → "Lunes a viernes" · 7 días → "Todos los días" · [1,3,5] → "Lun · Mié · Vie". */
fun textoDias(dias: List<Int>?): String {
    val d = dias.orEmpty().distinct().filter { it in 1..7 }.sorted()
    if (d.isEmpty()) return "Sin días"
    if (d.size == 7) return "Todos los días"
    val seguidos = d.withIndex().all { (i, n) -> i == 0 || n == d[i - 1] + 1 }
    if (seguidos && d.size >= 3) {
        return "${DIAS_SEMANA[d.first() - 1].nombre} a ${DIAS_SEMANA[d.last() - 1].nombre.lowercase()}"
    }
    return d.joinToString(" · ") { DIAS_SEMANA[it - 1].corto }
}

/** Día ISO de una fecha AAAA-MM-DD (1 = lunes … 7 = domingo), sin zonas horarias. 0 si no es fecha. */
fun diaIsoDe(fecha: String): Int =
    runCatching { LocalDate.parse(fecha.take(10)).dayOfWeek.ordinal + 1 }.getOrDefault(0)

fun tocaEnFecha(dias: List<Int>?, fecha: String): Boolean = diaIsoDe(fecha) in dias.orEmpty()

data class Adherencia(
    /** Días en que tocaba hacerlo dentro de la ventana. */
    val programados: Int,
    val hechos: Int,
    /** 0–100, o null si en la ventana no tocaba ningún día. */
    val porcentaje: Int?,
    /** Último dolor que el paciente anotó (0–10). */
    val ultimoDolor: Int?,
    val ultimaFecha: String?,
)

/** Tope de días hacia atrás que se miran (los registros se cargan con esa misma ventana). */
const val VENTANA_CUMPLIMIENTO = 60

/**
 * El día de la CLÍNICA (America/Lima) de un timestamp. `created_at` viene en UTC:
 * recortarlo tal cual corre la fecha un día después de las 19:00 de Perú, y un
 * plan indicado en una sesión de la tarde contaría desde el día siguiente.
 * Gemelo de `diaLimaDe` (lib/ejercicios.ts).
 */
fun diaLimaDe(ts: String?): String {
    val s = ts.orEmpty()
    return runCatching { Instant.parse(s).toLocalDateTime(ZONA_CLINICA).date.toString() }.getOrElse { s.take(10) }
}

/**
 * Cuánto cumplió el paciente desde que se le indicó el ejercicio hasta [hasta]
 * (hoy, o el día en que el plan se cerró), contando solo los días en que tocaba.
 * El día en que se indicó (el de la sesión) solo cuenta si lo hizo: nadie espera
 * que repita en casa lo que acaba de hacer en consulta.
 */
fun adherenciaDeItem(
    item: ItemPlanEjercicios,
    registros: List<RegistroEjercicio>,
    hasta: String,
    ventana: Int = VENTANA_CUMPLIMIENTO,
): Adherencia {
    val desdeCreado = diaLimaDe(item.createdAt)
    val propios = registros.filter { it.itemId == item.id && it.completado && it.fecha <= hasta }
    val hechosEn = propios.map { it.fecha }.toSet()
    var programados = 0
    var hechos = 0
    for (i in 0 until ventana) {
        val f = sumarDiasIso(hasta, -i)
        if (desdeCreado.isNotEmpty() && f < desdeCreado) break
        if (!tocaEnFecha(item.dias, f)) continue
        if (f == desdeCreado && f !in hechosEn) continue
        programados++
        if (f in hechosEn) hechos++
    }
    val conDolor = propios.filter { it.dolor != null }.sortedByDescending { it.fecha }.firstOrNull()
    val ultima = propios.sortedByDescending { it.fecha }.firstOrNull()
    return Adherencia(
        programados = programados,
        hechos = hechos,
        porcentaje = if (programados > 0) (hechos * 100.0 / programados).roundToInt() else null,
        ultimoDolor = conDolor?.dolor,
        ultimaFecha = ultima?.fecha,
    )
}

/** Hasta qué día se mide un plan: hoy si sigue vigente, o el día en que se cerró. */
fun hastaDePlan(fechaFin: String?, hoy: String): String =
    if (!fechaFin.isNullOrEmpty() && fechaFin < hoy) fechaFin else hoy

/** Adherencia del plan entero (suma de sus ejercicios activos), en SU periodo. */
fun adherenciaDePlan(
    plan: PlanEjercicios,
    registros: List<RegistroEjercicio>,
    hoy: String,
    ventana: Int = VENTANA_CUMPLIMIENTO,
): Adherencia {
    val hasta = hastaDePlan(plan.fechaFin, hoy)
    val partes = plan.activos.map { adherenciaDeItem(it, registros, hasta, ventana) }
    val programados = partes.sumOf { it.programados }
    val hechos = partes.sumOf { it.hechos }
    val conFecha = partes.filter { !it.ultimaFecha.isNullOrEmpty() }.sortedByDescending { it.ultimaFecha }.firstOrNull()
    return Adherencia(
        programados = programados,
        hechos = hechos,
        porcentaje = if (programados > 0) (hechos * 100.0 / programados).roundToInt() else null,
        ultimoDolor = conFecha?.ultimoDolor,
        ultimaFecha = conFecha?.ultimaFecha,
    )
}

/** Los planes vigentes (uno por tratamiento) y el resto como historial. */
fun separarPlanes(planes: List<PlanEjercicios>): Pair<List<PlanEjercicios>, List<PlanEjercicios>> =
    planes.filter { it.estado == "activo" } to planes.filter { it.estado != "activo" }

private fun ddmm(f: String?): String {
    val p = f.orEmpty().take(10).split("-")
    return if (p.size == 3 && p.all { it.isNotEmpty() }) "${p[2]}/${p[1]}/${p[0].drop(2)}" else ""
}

/** De cuándo es el plan: "Sesión #8 · 06/06/26", "Al terminar el tratamiento"… */
fun etiquetaPlan(momento: String, sesion: SesionDePlan?, tratamientoId: String?): String {
    if (momento == "alta") return if (!tratamientoId.isNullOrEmpty()) "Al terminar el tratamiento" else "Para casa"
    if (sesion != null) return "Sesión #${sesion.numero}" + if (sesion.fecha.isNotEmpty()) " · ${ddmm(sesion.fecha)}" else ""
    return if (!tratamientoId.isNullOrEmpty()) "Antes de la primera sesión" else "Entre sesiones"
}

fun etiquetaPlan(p: PlanEjercicios): String = etiquetaPlan(p.momento, p.sesion, p.tratamientoId)

/** Hasta cuándo vale lo indicado. */
fun vigenciaPlan(momento: String): String = if (momento == "alta") "Para seguir en casa" else "Hasta la próxima sesión"

// ─── Para cuándo se indican (el "destino"): una sesión o el fin del tratamiento ──

data class DestinoEjercicios(
    val momento: String,
    /** Solo 'sesion'. null = el tratamiento aún no tiene sesiones atendidas. */
    val sesionId: String?,
    val tratamientoId: String?,
)

/** Una sesión del paciente, como la necesita "Indicar para" (`SesionRef` en la web). */
data class SesionDestino(val id: String, val numero: Int, val fecha: String, val estado: String, val tratamientoId: String? = null)

/** Un tratamiento del paciente, como lo necesita "Indicar para" (`TratamientoRef` en la web). */
data class TratamientoDestino(val id: String, val nombre: String, val estado: String)

data class OpcionDestino(val clave: String, val destino: DestinoEjercicios, val etiqueta: String)
data class GrupoDestinos(val tratamientoId: String?, val nombre: String, val opciones: List<OpcionDestino>)

/** Lo que se pide al venir de cerrar una sesión ("dejarle ejercicios"). */
data class PedidoEjercicios(val sesionId: String? = null, val tratamientoId: String? = null, val alTerminar: Boolean = false)

fun claveDestino(d: DestinoEjercicios): String =
    if (d.momento == "alta") "alta:${d.tratamientoId.orEmpty()}"
    else "sesion:${d.sesionId ?: "antes:${d.tratamientoId.orEmpty()}"}"

private fun tratamientoTerminado(estado: String): Boolean = estado == "Completado" || estado == "Alta"

private fun tratamientosVivos(tratamientos: List<TratamientoDestino>): List<TratamientoDestino> =
    tratamientos.filter { it.estado != "Cancelado" && it.estado != "Eliminado" }

/**
 * Sesiones en las que tiene sentido indicar: las ya atendidas y la de hoy (el
 * fisio los deja durante la sesión, antes de cerrarla). De la más reciente a la más vieja.
 */
fun sesionesParaIndicar(sesiones: List<SesionDestino>, hoy: String): List<SesionDestino> =
    sesiones
        .filter { it.estado == "Completada" || it.estado == "En progreso" || (it.estado == "Planificada" && it.fecha.take(10) == hoy) }
        .sortedWith(compareByDescending<SesionDestino> { it.fecha }.thenByDescending { it.numero })

/** Las opciones del selector "Indicar para", agrupadas por tratamiento. */
fun destinosEjercicios(
    sesiones: List<SesionDestino>,
    tratamientos: List<TratamientoDestino>,
    hoy: String,
    maxSesiones: Int = 6,
): List<GrupoDestinos> {
    val vivos = tratamientosVivos(tratamientos)
    if (vivos.isEmpty()) {
        val destino = DestinoEjercicios("alta", null, null)
        return listOf(GrupoDestinos(null, "Sin tratamiento", listOf(OpcionDestino(claveDestino(destino), destino, "Para casa"))))
    }
    val atendidas = sesionesParaIndicar(sesiones, hoy)
    return vivos.map { t ->
        val opciones = atendidas.filter { it.tratamientoId == t.id }.take(maxSesiones).mapIndexed { i, s ->
            val destino = DestinoEjercicios("sesion", s.id, t.id)
            OpcionDestino(claveDestino(destino), destino, "Sesión #${s.numero} · ${ddmm(s.fecha)}${if (i == 0) " (la última)" else ""}")
        }.toMutableList()
        if (opciones.isEmpty() && !tratamientoTerminado(t.estado)) {
            val destino = DestinoEjercicios("sesion", null, t.id)
            opciones.add(OpcionDestino(claveDestino(destino), destino, "Antes de la primera sesión"))
        }
        val alta = DestinoEjercicios("alta", null, t.id)
        opciones.add(OpcionDestino(claveDestino(alta), alta, "🏁 Al terminar el tratamiento (para casa)"))
        GrupoDestinos(t.id, t.nombre, opciones)
    }
}

/**
 * Para cuándo se indica si nadie elige: lo que se pidió al venir de cerrar una
 * sesión; si no, la última sesión atendida — o "al terminar" si ese tratamiento
 * ya acabó.
 */
fun destinoPorDefecto(
    sesiones: List<SesionDestino>,
    tratamientos: List<TratamientoDestino>,
    hoy: String,
    pedido: PedidoEjercicios? = null,
): DestinoEjercicios {
    val vivos = tratamientosVivos(tratamientos)
    fun esVivo(id: String?) = !id.isNullOrEmpty() && vivos.any { it.id == id }
    if (pedido?.alTerminar == true && esVivo(pedido.tratamientoId)) return DestinoEjercicios("alta", null, pedido.tratamientoId)
    val pedida = pedido?.sesionId?.takeIf { it.isNotEmpty() }?.let { id -> sesiones.firstOrNull { it.id == id } }
    if (pedida != null && esVivo(pedida.tratamientoId)) return DestinoEjercicios("sesion", pedida.id, pedida.tratamientoId)

    val ultima = sesionesParaIndicar(sesiones, hoy)
        .firstOrNull { esVivo(it.tratamientoId) && (!esVivo(pedido?.tratamientoId) || it.tratamientoId == pedido?.tratamientoId) }
    if (ultima != null) {
        val t = vivos.first { it.id == ultima.tratamientoId }
        return if (tratamientoTerminado(t.estado)) DestinoEjercicios("alta", null, t.id)
        else DestinoEjercicios("sesion", ultima.id, t.id)
    }
    val t = vivos.firstOrNull { it.id == pedido?.tratamientoId }
        ?: vivos.firstOrNull { !tratamientoTerminado(it.estado) }
        ?: vivos.firstOrNull()
        ?: return DestinoEjercicios("alta", null, null)
    return if (tratamientoTerminado(t.estado)) DestinoEjercicios("alta", null, t.id)
    else DestinoEjercicios("sesion", null, t.id)
}

/** El plan que ya existe para ese destino (indicar de nuevo suma al mismo). */
fun planDeDestino(planes: List<PlanEjercicios>, d: DestinoEjercicios): PlanEjercicios? {
    if (d.momento == "sesion" && !d.sesionId.isNullOrEmpty()) return planes.firstOrNull { it.sesionId == d.sesionId }
    return planes.firstOrNull {
        it.momento == d.momento && it.sesionId.isNullOrEmpty() && it.estado != "finalizado" && it.tratamientoId == d.tratamientoId
    }
}

/** Lo vigente de ese tratamiento: lo que se puede repetir en la sesión siguiente. */
fun vigenteDeTratamiento(planes: List<PlanEjercicios>, tratamientoId: String?): PlanEjercicios? =
    planes.firstOrNull { it.estado == "activo" && it.tratamientoId == tratamientoId }

/** Texto del WhatsApp con el enlace del plan (lo manda la clínica desde su celular). */
fun mensajeCompartirPlan(nombrePaciente: String?, clinica: String?, url: String, cantidad: Int): String {
    val nombre = nombrePaciente.orEmpty().trim().split(Regex("\\s+")).firstOrNull().orEmpty()
    val saludo = if (nombre.isNotEmpty()) "Hola $nombre" else "Hola"
    val cuantos = if (cantidad == 1) "tu ejercicio de apoyo" else "tus $cantidad ejercicios de apoyo"
    return "$saludo, aquí tienes $cuantos para hacer en casa${if (!clinica.isNullOrEmpty()) " ($clinica)" else ""}. " +
        "Cada uno trae su animación y las indicaciones:\n$url\n\nSi algo te duele más de lo indicado, detente y avísanos."
}
