package pe.saniape.app.data.staff

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.floor

// 😁 ORTODONCIA: seguimiento estructurado del tratamiento — el plan (aparatología,
// fase, clase inicial, objetivos, retención) y los controles (arcos, ligaduras,
// elásticos, cadeneta, brackets despegados, higiene, próxima cita).
// GEMELO de `lib/ortodoncia.ts` de la web (mismos ids de catálogo, mismas reglas
// y los mismos casos de prueba). Diseño en la web:
// docs/superpowers/specs/2026-10-16-ortodoncia-controles-design.md
//
// Todo lo clínico es ORIENTATIVO (fase esperada, siguiente arco, semanas por
// aparatología): avisa, nunca bloquea. Fuentes en el diseño (Proffit 2019;
// McLaughlin, Bennett y Trevisi 2001; Angle 1899).

// ─── Catálogos (ids = valores de los CHECK de la base: no cambiarlos) ────────

data class AparatologiaOrto(
    val id: String,
    val nombre: String,
    val corto: String,
    /** Fija con brackets: el control registra arcos, ligaduras, cadeneta y brackets despegados. */
    val brackets: Boolean,
    /** Semanas habituales hasta el próximo control (orientativo). */
    val semanas: Int,
    /** Duración estimada por defecto al iniciar el plan (meses, orientativo). */
    val meses: Int,
)

val APARATOLOGIAS_ORTO: List<AparatologiaOrto> = listOf(
    AparatologiaOrto("brackets_metalicos", "Brackets metálicos", "Metálicos", true, 4, 24),
    AparatologiaOrto("brackets_esteticos", "Brackets estéticos (cerámica / zafiro)", "Estéticos", true, 4, 24),
    AparatologiaOrto("autoligado", "Brackets de autoligado", "Autoligado", true, 6, 24),
    AparatologiaOrto("linguales", "Brackets linguales", "Linguales", true, 4, 24),
    AparatologiaOrto("alineadores", "Alineadores transparentes", "Alineadores", false, 6, 18),
    AparatologiaOrto("removible", "Aparato removible", "Removible", false, 6, 12),
    AparatologiaOrto("expansor", "Expansor palatino", "Expansor", false, 2, 6),
    AparatologiaOrto("ortopedia_funcional", "Ortopedia funcional", "Ortopedia", false, 6, 12),
    AparatologiaOrto("otro", "Otra aparatología", "Otra", false, 4, 24),
)

/** Una opción de catálogo (id estable + nombre; `corto` para chips, `detalle` para la ayuda). */
data class OpcionOrto(val id: String, val nombre: String, val corto: String = nombre, val detalle: String? = null)

val ARCADAS_ORTO: List<OpcionOrto> = listOf(
    OpcionOrto("ambas", "Ambas arcadas"),
    OpcionOrto("superior", "Solo superior"),
    OpcionOrto("inferior", "Solo inferior"),
)

/**
 * Fases del tratamiento integral (Proffit 2019, cap. 14–17). `peso` = la parte
 * del tiempo activo que suele llevar (orientativo); retención va después del
 * retiro y no cuenta en la duración estimada.
 */
data class FaseOrto(val id: String, val nombre: String, val corto: String, val peso: Double)

val FASES_ORTO: List<FaseOrto> = listOf(
    FaseOrto("alineacion", "Alineación y nivelación", "Alineación", 0.30),
    FaseOrto("cierre_espacios", "Cierre de espacios", "Cierre", 0.25),
    FaseOrto("correccion", "Corrección de clase / mordida", "Corrección", 0.25),
    FaseOrto("finalizacion", "Detallado y finalización", "Finalización", 0.20),
    FaseOrto("retencion", "Retención", "Retención", 0.0),
)

/** Clase de Angle (molar y canina, por lado del PACIENTE). */
val CLASES_ANGLE: List<String> = listOf("I", "II", "III")

val RETENCIONES_ORTO: List<OpcionOrto> = listOf(
    OpcionOrto("fija", "Fija (lingual)"),
    OpcionOrto("removible", "Removible (Hawley / Essix)"),
    OpcionOrto("fija_removible", "Fija + removible"),
)

/** Objetivos frecuentes (chips del plan; se puede escribir uno propio). */
val OBJETIVOS_ORTO: List<String> = listOf(
    "Alinear y nivelar", "Corregir apiñamiento", "Cerrar espacios / diastemas", "Corregir clase II",
    "Corregir clase III", "Corregir mordida profunda", "Corregir mordida abierta", "Corregir mordida cruzada",
    "Reducir overjet", "Centrar línea media", "Mejorar la sonrisa", "Preparar para prótesis / implantes",
)

/** Arcos de uso común (chips). Notación: material + calibre en pulgadas; × = rectangular. */
val ARCOS_COMUNES: List<String> = listOf(
    "NiTi .012", "NiTi .014", "NiTi .016", "NiTi .018", "NiTi .016×.022", "NiTi .017×.025", "NiTi .018×.025", "NiTi .019×.025",
    "Acero .016", "Acero .018", "Acero .016×.022", "Acero .017×.025", "Acero .019×.025", "TMA .017×.025", "TMA .019×.025",
)

/**
 * Secuencia típica de arcos en slot .022" (McLaughlin, Bennett y Trevisi 2001):
 * redondos superelásticos → rectangulares superelásticos → acero de trabajo.
 * "Siguiente sugerido" = el que sigue (orientativo).
 */
val SECUENCIA_ARCOS: List<String> = listOf(
    "NiTi .012", "NiTi .014", "NiTi .016", "NiTi .018", "NiTi .016×.022", "NiTi .019×.025", "Acero .019×.025",
)

val LIGADURAS_ORTO: List<OpcionOrto> = listOf(
    OpcionOrto("elasticas", "Elásticas (de color)", "Elásticas"),
    OpcionOrto("metalicas", "Metálicas", "Metálicas"),
    OpcionOrto("sin_ligadura", "Sin ligadura (autoligado)", "Sin ligadura"),
)

val COLORES_LIGADURA: List<String> = listOf(
    "Transparente", "Gris", "Plateado", "Azul", "Celeste", "Rojo", "Rosado", "Morado", "Verde", "Amarillo", "Naranja", "Negro", "Blanco", "Varios colores",
)

val TIPOS_ELASTICO: List<OpcionOrto> = listOf(
    OpcionOrto("clase_ii", "Clase II", "Clase II"),
    OpcionOrto("clase_iii", "Clase III", "Clase III"),
    OpcionOrto("cruzado", "Cruzados (mordida cruzada)", "Cruzados"),
    OpcionOrto("vertical", "Verticales / box (mordida abierta)", "Verticales"),
    OpcionOrto("triangular", "Triangulares (asentamiento)", "Triangulares"),
    OpcionOrto("otro", "Otro", "Otro"),
)

val LADOS_ELASTICO: List<OpcionOrto> = listOf(
    OpcionOrto("ambos", "Ambos lados"),
    OpcionOrto("der", "Derecho"),
    OpcionOrto("izq", "Izquierdo"),
)

val TAMANOS_ELASTICO: List<String> = listOf("1/8\"", "3/16\"", "1/4\"", "5/16\"", "3/8\"")

/** Fuerza del elástico (onzas del fabricante; gramos aproximados). */
val FUERZAS_ELASTICO: List<OpcionOrto> = listOf(
    OpcionOrto("ligera", "Ligera", detalle = "2 oz ≈ 57 g"),
    OpcionOrto("media", "Media", detalle = "3,5 oz ≈ 99 g"),
    OpcionOrto("pesada", "Pesada", detalle = "4,5 oz ≈ 128 g"),
    OpcionOrto("extra", "Extra pesada", detalle = "6 oz ≈ 170 g"),
)

/** Horas al día (chips): todo el día, tarde y noche, solo de noche. */
val HORAS_ELASTICO: List<Pair<Int, String>> = listOf(
    22 to "Todo el día (22 h)",
    12 to "Tarde y noche (12 h)",
    10 to "Solo de noche (10 h)",
)

val CADENETAS_ORTO: List<OpcionOrto> = listOf(
    OpcionOrto("superior", "Superior"),
    OpcionOrto("inferior", "Inferior"),
    OpcionOrto("ambas", "Ambas"),
)

val NIVELES_ORTO: List<OpcionOrto> = listOf(
    OpcionOrto("buena", "Buena"),
    OpcionOrto("regular", "Regular"),
    OpcionOrto("mala", "Mala"),
)

val DOLORES_ORTO: List<OpcionOrto> = listOf(
    OpcionOrto("ninguno", "Ninguno"),
    OpcionOrto("leve", "Leve"),
    OpcionOrto("moderado", "Moderado"),
    OpcionOrto("intenso", "Intenso"),
)

/** Semanas hasta el próximo control (chips). */
val SEMANAS_CONTROL_ORTO: List<Int> = listOf(2, 3, 4, 5, 6, 8)

/** Duraciones estimadas (chips del plan, meses). */
val DURACIONES_ORTO: List<Int> = listOf(6, 12, 18, 24, 30, 36)

/** Chips base de las observaciones del control (se suman las que la clínica escribe). */
val OBSERVACIONES_BASE_ORTO: List<String> = listOf(
    "Sin novedades", "Se activó el arco", "Se cambiaron ligaduras", "Se recementó bracket", "Se colocó cadeneta",
    "Se cortó el extremo del arco", "No usa los elásticos", "Refiere molestias leves", "Lesión por bracket / alambre",
    "Se reforzó la higiene", "Se tomaron fotos de control",
)

/** Límites: gemelos de los CHECK de la base (LIMITES_ORTODONCIA). */
object LimitesOrtodoncia {
    const val DURACION_MESES = 60
    const val OBJETIVOS = 20
    const val OBJETIVO = 80
    const val NOTAS = 2000
    const val ARCO = 40
    const val COLOR = 30
    const val ELASTICOS = 6
    const val PIEZAS = 32
    const val OBSERVACIONES = 2000
    const val SEMANAS = 26
}

/** Más de esto entre dos controles (o desde el último hasta hoy) = control atrasado. */
const val DIAS_CONTROL_ATRASADO = 56

/** La clave de la plantilla base "Cuidados con brackets" del catálogo de 📝 Indicaciones. */
const val PLANTILLA_CUIDADOS_BRACKETS = "brackets"
const val SERVICIO_CUIDADOS_BRACKETS = "Cuidados con brackets"

/** Mensaje cuando el servidor todavía no tiene el endpoint (backend viejo → 404 sin JSON). */
const val ORTODONCIA_NO_DISPONIBLE =
    "La ortodoncia aún no está disponible en el servidor. Intenta más tarde."

fun aparatologiaOrtoPorId(id: String?): AparatologiaOrto? = APARATOLOGIAS_ORTO.firstOrNull { it.id == id }
fun faseOrtoPorId(id: String?): FaseOrto? = FASES_ORTO.firstOrNull { it.id == id }
fun nombreOrto(lista: List<OpcionOrto>, id: String?): String? = lista.firstOrNull { it.id == id }?.nombre

// ─── Filas ───────────────────────────────────────────────────────────────────

data class ElasticoOrto(
    val tipo: String,
    val lado: String = "ambos",
    val tamano: String? = null,
    val fuerza: String? = null,
    val horas: Int? = null,
)

data class PlanOrtodoncia(
    val id: String,
    val pacienteId: String,
    val tratamientoId: String? = null,
    val terapeutaId: String? = null,
    val aparatologia: String,
    val arcadas: String = "ambas",
    val fechaInstalacion: String? = null,
    val duracionEstimadaMeses: Int? = null,
    val fase: String = "alineacion",
    val claseMolarDer: String? = null,
    val claseMolarIzq: String? = null,
    val claseCaninaDer: String? = null,
    val claseCaninaIzq: String? = null,
    val objetivos: List<String> = emptyList(),
    val retencionPlanificada: String? = null,
    val fechaRetiro: String? = null,
    val notas: String? = null,
    val createdAt: String? = null,
)

data class ControlOrtodoncia(
    val id: String,
    val planId: String,
    val pacienteId: String = "",
    val citaId: String? = null,
    val terapeutaId: String? = null,
    val fecha: String,
    val fase: String? = null,
    val arcoSuperior: String? = null,
    val arcoInferior: String? = null,
    val ligaduraTipo: String? = null,
    val ligaduraColor: String? = null,
    val elasticos: List<ElasticoOrto> = emptyList(),
    val cadeneta: String? = null,
    val bracketsDespegados: List<String> = emptyList(),
    val bracketsRecementados: List<String> = emptyList(),
    val higiene: String? = null,
    val cooperacionElasticos: String? = null,
    val dolor: String? = null,
    val observaciones: String? = null,
    val proximaSemanas: Int? = null,
    val createdAt: String? = null,
)

// ─── Ayudas ──────────────────────────────────────────────────────────────────

private val RE_FECHA_ORTO = Regex("""^\d{4}-\d{2}-\d{2}$""")
/** Pieza FDI: permanentes 11–48, temporales 51–85 (los mismos números del odontograma). */
private val RE_PIEZA_FDI = Regex("""^([1-4][1-8]|[5-8][1-5])$""")

/** Minúsculas y sin tildes, para comparar nombres (gemelo de `plegar`). */
fun plegarOrto(s: String?): String = plegarTildes(s.orEmpty()).lowercase()

private fun esFechaOrto(v: String?): Boolean =
    v != null && RE_FECHA_ORTO.matches(v) && runCatching { LocalDate.parse(v) }.isSuccess

/** ¿Es una pieza dental FDI válida? */
fun esPiezaFDI(p: String?): Boolean = p != null && RE_PIEZA_FDI.matches(p)

/** Piezas FDI: únicas, válidas, ordenadas por número (11, 12… 48). Lo inválido se descarta. */
fun normalizarPiezas(lista: List<String>): List<String> =
    lista.map { it.trim() }.filter { esPiezaFDI(it) }.distinct().sortedBy { it.toInt() }

/** Desde un texto ("36, 11 11;x"). */
fun normalizarPiezas(texto: String): List<String> = normalizarPiezas(texto.split(Regex("""[\s,;]+""")))

/** Normaliza un elástico; null si el tipo no es válido. Lado por defecto: ambos. */
fun normalizarElastico(e: ElasticoOrto): ElasticoOrto? {
    if (TIPOS_ELASTICO.none { it.id == e.tipo }) return null
    return ElasticoOrto(
        tipo = e.tipo,
        lado = e.lado.takeIf { l -> LADOS_ELASTICO.any { it.id == l } } ?: "ambos",
        tamano = e.tamano?.takeIf { it in TAMANOS_ELASTICO },
        fuerza = e.fuerza?.takeIf { f -> FUERZAS_ELASTICO.any { it.id == f } },
        horas = e.horas?.takeIf { it in 1..24 },
    )
}

fun normalizarElasticos(xs: List<ElasticoOrto>): List<ElasticoOrto> = xs.mapNotNull(::normalizarElastico)

/** Objetivos: textos únicos (sin distinguir mayúsculas ni tildes), sin vacíos. */
fun normalizarObjetivos(xs: List<String>): List<String> {
    val vistos = HashSet<String>()
    val out = mutableListOf<String>()
    for (x in xs) {
        val t = x.replace(Regex("""\s+"""), " ").trim()
        val k = plegarOrto(t)
        if (t.isEmpty() || !vistos.add(k)) continue
        out += t
    }
    return out
}

/** Arco para comparar: "niti .016x.022" = "NiTi 0.016 × 0.022". */
fun claveArco(a: String?): String = plegarOrto(a).replace("×", "x").replace("0.", ".").replace(Regex("""\s+"""), "")

fun mismoArco(a: String?, b: String?): Boolean = !a.isNullOrEmpty() && !b.isNullOrEmpty() && claveArco(a) == claveArco(b)

/** El arco que sigue en la secuencia típica (orientativo); null si no está o es el último. */
fun siguienteArco(arco: String?): String? {
    val i = SECUENCIA_ARCOS.indexOfFirst { mismoArco(it, arco) }
    return if (i >= 0 && i < SECUENCIA_ARCOS.size - 1) SECUENCIA_ARCOS[i + 1] else null
}

// ─── ¿Es ortodoncia? (por nombre: la especialidad es de rubro odontología) ───

private val RE_SERVICIO_ORTO =
    Regex("""ortod[o]n|brac?kets?|braket|alineador|invisalign|retenedor|expansor|disyuntor|aparatolog|ortopedia (maxilar|funcional|dentofacial)|hawley|essix""")

/**
 * ¿Este servicio es de ortodoncia? Por el NOMBRE (el catálogo de Ortodoncia:
 * "Instalación de brackets", "Control mensual de ortodoncia", "Retenedores"…).
 * Quien llama exige además que sea DENTAL. "Brackets ungueales" (podología,
 * onicocorrección) NO es ortodoncia.
 */
fun esServicioOrtodoncia(nombre: String?): Boolean {
    val s = plegarOrto(nombre)
    if (s.contains("unguea")) return false
    return RE_SERVICIO_ORTO.containsMatchIn(s)
}

/** ¿La especialidad es de ortodoncia? ("Ortodoncia", "Ortodoncista", "Ortodoncia y ortopedia maxilar"…). */
fun esEspecialidadOrtodoncia(nombre: String?): Boolean = plegarOrto(nombre).contains("ortodon")

/**
 * Qué especialidades de la clínica son de ortodoncia. Lo arma la web
 * (`especialidadesOrtodoncia` de /api/staff/contexto); un backend sin el campo
 * deja el mapa vacío (y queda la regla del servicio por nombre).
 */
data class MapaOrtodoncia(val ids: List<String> = emptyList())

/** Gemelo de `mapaOrtodoncia`: por nombre, solo dentales o sin rubro. */
fun mapaOrtodonciaDe(especialidades: List<Triple<String, String?, String?>>): MapaOrtodoncia =
    MapaOrtodoncia(especialidades.filter { (_, nombre, rubro) ->
        esEspecialidadOrtodoncia(nombre) && (rubro.isNullOrBlank() || rubro == "odontologia")
    }.map { it.first })

/** ¿Esta atención / tratamiento es de ortodoncia? Servicio o especialidad. */
fun esAtencionOrtodoncia(
    mapa: MapaOrtodoncia,
    servicio: String? = null,
    especialidadId: String? = null,
    especialidadServicioId: String? = null,
): Boolean {
    if (esServicioOrtodoncia(servicio)) return true
    return listOf(especialidadId, especialidadServicioId).any { !it.isNullOrBlank() && it in mapa.ids }
}

/** Una atención (cita o tratamiento) para decidir la pestaña. */
data class AtencionOrto(val servicio: String? = null, val especialidadId: String? = null, val especialidadServicioId: String? = null)

/**
 * ¿La ficha muestra la pestaña 😁 Ortodoncia? Solo pacientes DENTALES (la regla
 * de la 🦷) y, entre ellos, si tiene un tratamiento / cita de ortodoncia, un plan
 * ya guardado o si quien mira es de una especialidad de ortodoncia.
 */
fun pacienteEsOrtodoncia(
    mapa: MapaOrtodoncia,
    esDental: Boolean,
    atenciones: List<AtencionOrto> = emptyList(),
    tienePlan: Boolean = false,
    especialidadesDeQuienMira: List<String>? = null,
): Boolean {
    if (!esDental) return false
    if (tienePlan) return true
    if (atenciones.any { esAtencionOrtodoncia(mapa, it.servicio, it.especialidadId, it.especialidadServicioId) }) return true
    return especialidadesDeQuienMira.orEmpty().any { it.isNotBlank() && it in mapa.ids }
}

/**
 * Qué atención de ortodoncia es, por el servicio: "consulta" (estudio),
 * "instalacion", "control" o "retencion". null = no es ortodoncia. Un servicio
 * genérico ("Tratamiento de ortodoncia") es control si la cita es una Sesión.
 */
fun tipoAtencionOrtodoncia(servicio: String?, tipoCita: String? = null): String? {
    if (!esServicioOrtodoncia(servicio)) return null
    val s = plegarOrto(servicio)
    if (Regex("""control|ajuste|activacion|mensual|cambio de arco""").containsMatchIn(s)) return "control"
    if (Regex("""consulta|estudio|evaluacion|diagnost|cefalom|modelos""").containsMatchIn(s)) return "consulta"
    if (Regex("""retenedor|retencion|retiro|hawley|essix""").containsMatchIn(s)) return "retencion"
    if (Regex("""instalacion|colocacion|cementad|montaje""").containsMatchIn(s)) return "instalacion"
    return if (tipoCita.isNullOrEmpty() || tipoCita == "Sesión") "control" else "consulta"
}

/** Tras cerrar una cita: ¿se ofrece "😁 Registrar control"? (control o instalación). */
fun ofreceRegistrarControl(tipo: String?): Boolean = tipo == "control" || tipo == "instalacion"

/**
 * El tipo de la cita para la agenda (gemelo de /citas web): el del servicio y,
 * si no se reconoce, una Sesión de una especialidad de ortodoncia es un control.
 */
fun tipoOrtoDeCita(servicio: String?, tipoCita: String?, especialidadEsOrto: Boolean): String? =
    tipoAtencionOrtodoncia(servicio, tipoCita) ?: if (especialidadEsOrto && tipoCita == "Sesión") "control" else null

/**
 * ¿La agenda ofrece "😁 Registrar control" tras cerrar esta cita? Cita DENTAL de
 * control o instalación, con permiso; y hace falta dónde guardarlo: la cita tiene
 * su tratamiento o el paciente ya tiene plan ([tienePlan] null = no se pudo saber → no).
 */
fun ofrecerControlOrtoTrasCompletar(
    pacienteId: String?,
    puedeEditar: Boolean,
    esDental: Boolean,
    tipo: String?,
    tieneTratamiento: Boolean,
    tienePlan: Boolean?,
): Boolean = !pacienteId.isNullOrBlank() && puedeEditar && esDental && ofreceRegistrarControl(tipo) &&
    (tieneTratamiento || tienePlan == true)

/** La aparatología que sugiere el nombre del servicio (al iniciar el plan). */
fun aparatologiaSugerida(servicio: String?): String {
    val s = plegarOrto(servicio)
    return when {
        Regex("""alineador|invisalign""").containsMatchIn(s) -> "alineadores"
        Regex("""autoligad|damon""").containsMatchIn(s) -> "autoligado"
        s.contains("lingual") -> "linguales"
        Regex("""estetic|ceramic|zafiro""").containsMatchIn(s) -> "brackets_esteticos"
        Regex("""expansor|disyuntor""").containsMatchIn(s) -> "expansor"
        s.contains("ortopedia") -> "ortopedia_funcional"
        Regex("""removible|hawley""").containsMatchIn(s) -> "removible"
        else -> "brackets_metalicos"
    }
}

/** Un tratamiento de ortodoncia del paciente (para atar el plan). */
data class TratamientoOrto(
    val id: String,
    val nombre: String?,
    val estado: String?,
    val terapeutaId: String? = null,
    val especialidadId: String? = null,
    /** Multipaís: moneda del tratamiento (las cuotas), por SU sede (`regionalDeTratamiento`); null = la de la ficha. */
    val moneda: String? = null,
)

/**
 * El tratamiento al que se ata el plan: los vivos primero; la instalación o el
 * tratamiento integral antes que el control mensual, y la consulta / los
 * retenedores al final.
 */
fun elegirTratamientoPlan(trats: List<TratamientoOrto>): String? {
    fun rango(t: TratamientoOrto): Int {
        if (!esServicioOrtodoncia(t.nombre)) return 4
        val s = plegarOrto(t.nombre)
        return when {
            Regex("""instalacion|colocacion|cementad|montaje""").containsMatchIn(s) -> 0
            Regex("""control|ajuste|activacion|mensual""").containsMatchIn(s) -> 2
            Regex("""consulta|estudio|evaluacion|diagnost|retenedor|retencion|retiro""").containsMatchIn(s) -> 3
            else -> 1
        }
    }
    fun vivo(e: String?) = if (e == "Alta" || e == "Cancelado" || e == "Eliminado" || e == "Completado") 1 else 0
    return trats.sortedWith(compareBy<TratamientoOrto> { vivo(it.estado) }.thenBy { rango(it) }).firstOrNull()?.id
}

// ─── Borradores, validación y cuerpos para la API ────────────────────────────

/** Lo que arma el formulario del plan. [duracionEstimadaMeses] = -1 si se escribió algo que no es un número. */
data class BorradorPlanOrto(
    val pacienteId: String,
    val tratamientoId: String? = null,
    val terapeutaId: String? = null,
    val aparatologia: String? = null,
    val arcadas: String = "ambas",
    val fechaInstalacion: String? = null,
    val duracionEstimadaMeses: Int? = null,
    val fase: String = "alineacion",
    val claseMolarDer: String? = null,
    val claseMolarIzq: String? = null,
    val claseCaninaDer: String? = null,
    val claseCaninaIzq: String? = null,
    val objetivos: List<String> = emptyList(),
    val retencionPlanificada: String? = null,
    val fechaRetiro: String? = null,
    val notas: String? = null,
)

private fun validarClase(v: String?, nombre: String, e: MutableList<String>) {
    if (!v.isNullOrEmpty() && v !in CLASES_ANGLE) e += "$nombre: elige I, II o III."
}

/** Errores del plan (vacío = válido). [nuevo]: exige el tratamiento. Mensajes gemelos de `validarPlan`. */
fun validarPlanOrto(b: BorradorPlanOrto, nuevo: Boolean = false): List<String> {
    val e = mutableListOf<String>()
    if (b.pacienteId.isBlank()) e += "Falta el paciente."
    if (nuevo && b.tratamientoId.isNullOrBlank()) e += "Elige el tratamiento de ortodoncia."
    if (aparatologiaOrtoPorId(b.aparatologia) == null) e += "Elige la aparatología."
    if (ARCADAS_ORTO.none { it.id == b.arcadas }) e += "Arcadas inválidas."
    if (faseOrtoPorId(b.fase) == null) e += "Fase inválida."
    if (!b.fechaInstalacion.isNullOrEmpty() && !esFechaOrto(b.fechaInstalacion)) e += "Fecha de instalación inválida."
    if (!b.fechaRetiro.isNullOrEmpty() && !esFechaOrto(b.fechaRetiro)) e += "Fecha de retiro inválida."
    if (esFechaOrto(b.fechaInstalacion) && esFechaOrto(b.fechaRetiro) && b.fechaRetiro!! < b.fechaInstalacion!!) {
        e += "El retiro no puede ser antes de la instalación."
    }
    b.duracionEstimadaMeses?.let { d ->
        if (d < 1 || d > LimitesOrtodoncia.DURACION_MESES) e += "La duración estimada va de 1 a ${LimitesOrtodoncia.DURACION_MESES} meses."
    }
    validarClase(b.claseMolarDer, "Clase molar derecha", e)
    validarClase(b.claseMolarIzq, "Clase molar izquierda", e)
    validarClase(b.claseCaninaDer, "Clase canina derecha", e)
    validarClase(b.claseCaninaIzq, "Clase canina izquierda", e)
    val obj = normalizarObjetivos(b.objetivos)
    if (obj.size > LimitesOrtodoncia.OBJETIVOS) e += "Máximo ${LimitesOrtodoncia.OBJETIVOS} objetivos."
    if (obj.any { it.length > LimitesOrtodoncia.OBJETIVO }) e += "Cada objetivo, máximo ${LimitesOrtodoncia.OBJETIVO} caracteres."
    if (!b.retencionPlanificada.isNullOrEmpty() && RETENCIONES_ORTO.none { it.id == b.retencionPlanificada }) e += "Retención inválida."
    if (b.notas.orEmpty().trim().length > LimitesOrtodoncia.NOTAS) e += "Las notas son muy largas (máx. ${LimitesOrtodoncia.NOTAS} caracteres)."
    return e
}

/** POST (sin [id], con pacienteId) o PATCH (con [id], sin pacienteId). camelCase, como la API. */
fun cuerpoPlanOrto(b: BorradorPlanOrto, id: String? = null): JsonObject = buildJsonObject {
    if (id == null) put("pacienteId", b.pacienteId) else put("id", id)
    put("tratamientoId", b.tratamientoId?.takeIf { it.isNotBlank() })
    put("aparatologia", b.aparatologia)
    put("arcadas", b.arcadas)
    put("fechaInstalacion", b.fechaInstalacion?.takeIf { it.isNotBlank() })
    put("duracionEstimadaMeses", b.duracionEstimadaMeses)
    put("fase", b.fase)
    put("claseMolarDer", b.claseMolarDer)
    put("claseMolarIzq", b.claseMolarIzq)
    put("claseCaninaDer", b.claseCaninaDer)
    put("claseCaninaIzq", b.claseCaninaIzq)
    put("objetivos", buildJsonArray { normalizarObjetivos(b.objetivos).forEach { add(JsonPrimitive(it)) } })
    put("retencionPlanificada", b.retencionPlanificada)
    put("fechaRetiro", b.fechaRetiro?.takeIf { it.isNotBlank() })
    // Vacío = el servidor pone el del tratamiento o el del usuario.
    put("terapeutaId", b.terapeutaId?.takeIf { it.isNotBlank() })
    put("notas", b.notas?.trim()?.takeIf { it.isNotEmpty() })
}

/** Lo que arma el formulario del control. */
data class BorradorControlOrto(
    val planId: String,
    val citaId: String? = null,
    val terapeutaId: String? = null,
    val fecha: String? = null,
    val fase: String? = null,
    val arcoSuperior: String? = null,
    val arcoInferior: String? = null,
    val ligaduraTipo: String? = null,
    val ligaduraColor: String? = null,
    val elasticos: List<ElasticoOrto> = emptyList(),
    val cadeneta: String? = null,
    val bracketsDespegados: List<String> = emptyList(),
    val bracketsRecementados: List<String> = emptyList(),
    val higiene: String? = null,
    val cooperacionElasticos: String? = null,
    val dolor: String? = null,
    val observaciones: String? = null,
    val proximaSemanas: Int? = null,
)

private fun hayTexto(v: String?) = !v.isNullOrBlank()

/** ¿El control trae algún dato clínico? (fecha y semanas solas no son un control). */
private fun controlConDatos(b: BorradorControlOrto): Boolean =
    hayTexto(b.arcoSuperior) || hayTexto(b.arcoInferior) || hayTexto(b.ligaduraTipo) || hayTexto(b.ligaduraColor) ||
        normalizarElasticos(b.elasticos).isNotEmpty() || hayTexto(b.cadeneta) ||
        normalizarPiezas(b.bracketsDespegados).isNotEmpty() || normalizarPiezas(b.bracketsRecementados).isNotEmpty() ||
        hayTexto(b.higiene) || hayTexto(b.cooperacionElasticos) || hayTexto(b.dolor) || hayTexto(b.observaciones) || hayTexto(b.fase)

const val SIN_DATOS_CONTROL_ORTO = "Registra al menos un dato del control (arco, elásticos, higiene, observaciones…)."

/** Errores del control (vacío = válido). Mensajes gemelos de `validarControl`. */
fun validarControlOrto(b: BorradorControlOrto): List<String> {
    val e = mutableListOf<String>()
    if (b.planId.isBlank()) e += "Falta el plan de ortodoncia."
    if (!b.fecha.isNullOrEmpty() && !esFechaOrto(b.fecha)) e += "Fecha inválida."
    if (!b.fase.isNullOrEmpty() && faseOrtoPorId(b.fase) == null) e += "Fase inválida."
    if (b.arcoSuperior.orEmpty().trim().length > LimitesOrtodoncia.ARCO) e += "El arco superior es muy largo (máx. ${LimitesOrtodoncia.ARCO} caracteres)."
    if (b.arcoInferior.orEmpty().trim().length > LimitesOrtodoncia.ARCO) e += "El arco inferior es muy largo (máx. ${LimitesOrtodoncia.ARCO} caracteres)."
    if (!b.ligaduraTipo.isNullOrEmpty() && LIGADURAS_ORTO.none { it.id == b.ligaduraTipo }) e += "Tipo de ligadura inválido."
    if (b.ligaduraColor.orEmpty().trim().length > LimitesOrtodoncia.COLOR) e += "El color es muy largo (máx. ${LimitesOrtodoncia.COLOR} caracteres)."
    if (b.elasticos.any { normalizarElastico(it) == null }) e += "Algún elástico no tiene un tipo válido."
    if (b.elasticos.size > LimitesOrtodoncia.ELASTICOS) e += "Máximo ${LimitesOrtodoncia.ELASTICOS} elásticos."
    if (!b.cadeneta.isNullOrEmpty() && CADENETAS_ORTO.none { it.id == b.cadeneta }) e += "Cadeneta inválida."
    for ((lista, nombre) in listOf(b.bracketsDespegados to "Brackets despegados", b.bracketsRecementados to "Brackets recementados")) {
        if (lista.any { !esPiezaFDI(it.trim()) }) e += "$nombre: pieza inválida (numeración FDI, ej. 11, 36)."
        if (normalizarPiezas(lista).size > LimitesOrtodoncia.PIEZAS) e += "$nombre: demasiadas piezas."
    }
    if (!b.higiene.isNullOrEmpty() && NIVELES_ORTO.none { it.id == b.higiene }) e += "Higiene: elige buena, regular o mala."
    if (!b.cooperacionElasticos.isNullOrEmpty() && NIVELES_ORTO.none { it.id == b.cooperacionElasticos }) {
        e += "Cooperación con los elásticos: elige buena, regular o mala."
    }
    if (!b.dolor.isNullOrEmpty() && DOLORES_ORTO.none { it.id == b.dolor }) e += "Dolor / molestias inválido."
    if (b.observaciones.orEmpty().trim().length > LimitesOrtodoncia.OBSERVACIONES) {
        e += "Las observaciones son muy largas (máx. ${LimitesOrtodoncia.OBSERVACIONES} caracteres)."
    }
    b.proximaSemanas?.let { s ->
        if (s < 1 || s > LimitesOrtodoncia.SEMANAS) e += "La próxima cita va de 1 a ${LimitesOrtodoncia.SEMANAS} semanas."
    }
    if (e.isEmpty() && !controlConDatos(b)) e += SIN_DATOS_CONTROL_ORTO
    return e
}

private fun elasticoAJson(x: ElasticoOrto): JsonObject = buildJsonObject {
    put("tipo", x.tipo); put("lado", x.lado); put("tamano", x.tamano); put("fuerza", x.fuerza); put("horas", x.horas)
}

/** POST (sin [id], con planId) o PATCH (con [id], sin planId). */
fun cuerpoControlOrto(b: BorradorControlOrto, id: String? = null): JsonObject = buildJsonObject {
    if (id == null) put("planId", b.planId) else put("id", id)
    b.fecha?.takeIf { it.isNotBlank() }?.let { put("fecha", it) }
    put("citaId", b.citaId?.takeIf { it.isNotBlank() })
    // Sin profesional: el servidor pone el de la cita o el del usuario (como la web, no se manda).
    b.terapeutaId?.takeIf { it.isNotBlank() }?.let { put("terapeutaId", it) }
    put("fase", b.fase)
    put("arcoSuperior", b.arcoSuperior?.trim()?.takeIf { it.isNotEmpty() })
    put("arcoInferior", b.arcoInferior?.trim()?.takeIf { it.isNotEmpty() })
    put("ligaduraTipo", b.ligaduraTipo)
    put("ligaduraColor", if (b.ligaduraTipo == "elasticas") b.ligaduraColor?.trim()?.takeIf { it.isNotEmpty() } else null)
    put("elasticos", buildJsonArray { normalizarElasticos(b.elasticos).forEach { add(elasticoAJson(it)) } })
    put("cadeneta", b.cadeneta)
    put("bracketsDespegados", buildJsonArray { normalizarPiezas(b.bracketsDespegados).forEach { add(JsonPrimitive(it)) } })
    put("bracketsRecementados", buildJsonArray { normalizarPiezas(b.bracketsRecementados).forEach { add(JsonPrimitive(it)) } })
    put("higiene", b.higiene)
    put("cooperacionElasticos", b.cooperacionElasticos)
    put("dolor", b.dolor)
    put("observaciones", b.observaciones?.trim()?.takeIf { it.isNotEmpty() })
    put("proximaSemanas", b.proximaSemanas)
}

/** Fila guardada → borrador (editar). */
fun borradorControlDesde(c: ControlOrtodoncia): BorradorControlOrto = BorradorControlOrto(
    planId = c.planId, citaId = c.citaId, terapeutaId = c.terapeutaId, fecha = c.fecha, fase = c.fase,
    arcoSuperior = c.arcoSuperior, arcoInferior = c.arcoInferior, ligaduraTipo = c.ligaduraTipo, ligaduraColor = c.ligaduraColor,
    elasticos = normalizarElasticos(c.elasticos), cadeneta = c.cadeneta,
    bracketsDespegados = c.bracketsDespegados, bracketsRecementados = c.bracketsRecementados,
    higiene = c.higiene, cooperacionElasticos = c.cooperacionElasticos, dolor = c.dolor,
    observaciones = c.observaciones, proximaSemanas = c.proximaSemanas,
)

/**
 * "↺ Repetir el control anterior": lo que suele seguir igual (arcos, ligaduras,
 * elásticos, cadeneta y semanas). Lo que es de ESE día —brackets despegados,
 * higiene, dolor, observaciones— queda como estaba. La FASE no se copia: el
 * control nuevo parte de la fase ACTUAL del plan, así repetir un control viejo
 * nunca hace retroceder el plan. Gemelo de `copiarControlAnterior`.
 */
fun copiarControlAnterior(b: BorradorControlOrto, anterior: ControlOrtodoncia): BorradorControlOrto = b.copy(
    arcoSuperior = anterior.arcoSuperior,
    arcoInferior = anterior.arcoInferior,
    ligaduraTipo = anterior.ligaduraTipo,
    ligaduraColor = anterior.ligaduraColor,
    elasticos = normalizarElasticos(anterior.elasticos).map { it.copy() },
    cadeneta = anterior.cadeneta,
    // Sin semanas en el anterior, se quedan las que había (como la web).
    proximaSemanas = anterior.proximaSemanas ?: b.proximaSemanas,
)

// ─── JSON de la API → modelo (tolerante) ─────────────────────────────────────

private fun JsonObject.txtOrto(k: String): String? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
private fun JsonObject.intOrto(k: String): Int? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull()?.takeIf { it.isFinite() }?.toInt()
private fun listaTextosOrto(v: JsonElement?): List<String> = (v as? JsonArray).orEmpty().mapNotNull { x ->
    (x as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
}

fun elasticoOrtoDeJson(o: JsonObject?): ElasticoOrto? {
    if (o == null) return null
    val tipo = o.txtOrto("tipo") ?: return null
    return normalizarElastico(ElasticoOrto(tipo, o.txtOrto("lado") ?: "ambos", o.txtOrto("tamano"), o.txtOrto("fuerza"), o.intOrto("horas")))
}

/** Fila de `ortodoncia_planes` → modelo. null sin id, paciente o aparatología. */
fun planOrtoDeJson(o: JsonObject?): PlanOrtodoncia? {
    if (o == null) return null
    val id = o.txtOrto("id") ?: return null
    return PlanOrtodoncia(
        id = id,
        pacienteId = o.txtOrto("paciente_id") ?: return null,
        tratamientoId = o.txtOrto("tratamiento_id"),
        terapeutaId = o.txtOrto("terapeuta_id"),
        aparatologia = o.txtOrto("aparatologia") ?: return null,
        arcadas = o.txtOrto("arcadas") ?: "ambas",
        fechaInstalacion = o.txtOrto("fecha_instalacion")?.take(10),
        duracionEstimadaMeses = o.intOrto("duracion_estimada_meses"),
        fase = o.txtOrto("fase") ?: "alineacion",
        claseMolarDer = o.txtOrto("clase_molar_der"),
        claseMolarIzq = o.txtOrto("clase_molar_izq"),
        claseCaninaDer = o.txtOrto("clase_canina_der"),
        claseCaninaIzq = o.txtOrto("clase_canina_izq"),
        objetivos = listaTextosOrto(o["objetivos"]),
        retencionPlanificada = o.txtOrto("retencion_planificada"),
        fechaRetiro = o.txtOrto("fecha_retiro")?.take(10),
        notas = o.txtOrto("notas"),
        createdAt = o.txtOrto("created_at"),
    )
}

/** Fila de `ortodoncia_controles` → modelo. null sin id, plan o fecha. */
fun controlOrtoDeJson(o: JsonObject?): ControlOrtodoncia? {
    if (o == null) return null
    val id = o.txtOrto("id") ?: return null
    return ControlOrtodoncia(
        id = id,
        planId = o.txtOrto("plan_id") ?: return null,
        pacienteId = o.txtOrto("paciente_id").orEmpty(),
        citaId = o.txtOrto("cita_id"),
        terapeutaId = o.txtOrto("terapeuta_id"),
        fecha = o.txtOrto("fecha")?.take(10) ?: return null,
        fase = o.txtOrto("fase"),
        arcoSuperior = o.txtOrto("arco_superior"),
        arcoInferior = o.txtOrto("arco_inferior"),
        ligaduraTipo = o.txtOrto("ligadura_tipo"),
        ligaduraColor = o.txtOrto("ligadura_color"),
        elasticos = (o["elasticos"] as? JsonArray).orEmpty().mapNotNull { elasticoOrtoDeJson(it as? JsonObject) },
        cadeneta = o.txtOrto("cadeneta"),
        bracketsDespegados = normalizarPiezas(listaTextosOrto(o["brackets_despegados"])),
        bracketsRecementados = normalizarPiezas(listaTextosOrto(o["brackets_recementados"])),
        higiene = o.txtOrto("higiene"),
        cooperacionElasticos = o.txtOrto("cooperacion_elasticos"),
        dolor = o.txtOrto("dolor"),
        observaciones = o.txtOrto("observaciones"),
        proximaSemanas = o.intOrto("proxima_semanas"),
        createdAt = o.txtOrto("created_at"),
    )
}

/** GET ?sugerencias=1 → lo aprendido de las observaciones (más usado primero). */
fun sugerenciasOrtoDeJson(o: JsonObject?): List<AprendidoPlan> = (o?.get("observaciones") as? JsonArray).orEmpty().mapNotNull { x ->
    when (x) {
        is JsonPrimitive -> x.takeIf { it !is JsonNull && it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }?.let { AprendidoPlan(it, 1) }
        is JsonObject -> x.txtOrto("texto")?.let { AprendidoPlan(it, x.intOrto("usos")) }
        else -> null
    }
}

/** Chips (base + lo que la clínica más escribe) y autocompletado de las observaciones (modo "coma"). */
fun frasesObservacionesOrto(aprendidos: List<AprendidoPlan>?): FrasesCampoPlan =
    frasesCampoPlan(OBSERVACIONES_BASE_ORTO, aprendidos, porLinea = false)

// ─── Progreso: meses, fase esperada, controles atrasados, arcos, higiene ─────

private fun fechaOrto(f: String): LocalDate? = runCatching { LocalDate.parse(f.take(10)) }.getOrNull()

/** Días entre dos fechas YYYY-MM-DD (positivo si [hasta] es después). 0 si alguna no es fecha. */
fun diasEntreOrto(desde: String, hasta: String): Int {
    val a = fechaOrto(desde) ?: return 0
    val b = fechaOrto(hasta) ?: return 0
    return (b.toEpochDays() - a.toEpochDays()).toInt()
}

/** Suma días a una fecha YYYY-MM-DD (si no es fecha, vuelve tal cual). */
fun sumarDiasOrto(fecha: String, dias: Int): String =
    fechaOrto(fecha)?.plus(dias, DateTimeUnit.DAY)?.toString() ?: fecha

private fun partesOrto(f: String): Triple<Int, Int, Int>? {
    val p = f.take(10).split('-').mapNotNull { it.toIntOrNull() }
    return if (p.size == 3) Triple(p[0], p[1], p[2]) else null
}

/** Meses COMPLETOS de calendario entre dos fechas (≥ 0). */
fun mesesCompletos(desde: String, hasta: String): Int {
    val (y1, m1, d1) = partesOrto(desde) ?: return 0
    val (y2, m2, d2) = partesOrto(hasta) ?: return 0
    val n = (y2 - y1) * 12 + (m2 - m1) - (if (d2 < d1) 1 else 0)
    return maxOf(0, n)
}

/** Math.round de JS (mitades hacia arriba). */
private fun redondearPctOrto(x: Double): Int = floor(x + 0.5).toInt()

data class ProgresoOrto(
    /** Meses completos con la aparatología (hasta el retiro). null = sin instalar. */
    val meses: Int?,
    val estimados: Int?,
    /** 0–100 (tope 100). null sin instalación o sin duración. */
    val pct: Int?,
    /** Pasó la duración estimada sin retirar. */
    val excedido: Boolean,
    val retirado: Boolean,
    /** "Mes 8 de 24" · "26 meses (estimado: 24)" · "Retirado a los 23 meses" · "Sin instalar". */
    val texto: String,
)

fun progresoTratamiento(fechaInstalacion: String?, duracionEstimadaMeses: Int?, fechaRetiro: String?, hoy: String): ProgresoOrto {
    val est = duracionEstimadaMeses
    if (fechaInstalacion.isNullOrBlank()) return ProgresoOrto(null, est, null, excedido = false, retirado = false, texto = "Sin instalar")
    val retirado = !fechaRetiro.isNullOrBlank()
    val hasta = if (retirado) fechaRetiro!! else hoy
    val meses = mesesCompletos(fechaInstalacion, hasta)
    val pct = if (est != null && est > 0) minOf(100, redondearPctOrto(meses.toDouble() / est * 100)) else null
    val excedido = !retirado && est != null && est > 0 && meses >= est
    val texto = when {
        retirado -> "Retirado a los $meses ${if (meses == 1) "mes" else "meses"}"
        est != null && est > 0 -> if (excedido) "$meses meses (estimado: $est)" else "Mes ${meses + 1} de $est"
        else -> "Mes ${meses + 1}"
    }
    return ProgresoOrto(meses, est, pct, excedido, retirado, texto)
}

fun progresoTratamiento(p: PlanOrtodoncia, hoy: String): ProgresoOrto =
    progresoTratamiento(p.fechaInstalacion, p.duracionEstimadaMeses, p.fechaRetiro, hoy)

/**
 * La fase en la que SUELE estar un tratamiento a estos meses, repartiendo la
 * duración estimada según el peso de cada fase (30/25/25/20 %). Orientativo:
 * se muestra como "por el tiempo: …", nunca corrige la fase real.
 */
fun faseEsperada(meses: Int?, duracion: Int?): String? {
    if (meses == null || duracion == null || duracion == 0) return null
    val frac = meses.toDouble() / duracion
    var acum = 0.0
    for (f in FASES_ORTO) {
        if (f.id == "retencion") break
        acum += f.peso
        if (frac < acum - 1e-9) return f.id
    }
    return "finalizacion"
}

/** Índice de la fase (0–4) para pintar la barra. */
fun indiceFase(id: String?): Int = maxOf(0, FASES_ORTO.indexOfFirst { it.id == id })

/** Estado de cada fase para la barra: "hecha" · "actual" · "pendiente". */
fun estadosFases(faseActual: String?): List<Pair<FaseOrto, String>> {
    val actual = indiceFase(faseActual)
    return FASES_ORTO.mapIndexed { i, f -> f to (if (i < actual) "hecha" else if (i == actual) "actual" else "pendiente") }
}

data class BrechaControl(val desde: String, val hasta: String, val semanas: Int)

data class EstadoControlesOrto(
    val ultimo: ControlOrtodoncia?,
    /** Fecha estimada del próximo control: último + sus semanas (o las de la aparatología). */
    val proximoEstimado: String?,
    val semanasDesdeUltimo: Int?,
    /** Más de 8 semanas desde el último control (o desde la instalación). No en retención. */
    val atrasado: Boolean,
    /** Ya pasó la fecha estimada, pero todavía sin llegar a "atrasado". */
    val toca: Boolean,
    /** Saltos de más de 8 semanas entre controles (fuera de la retención). */
    val brechas: List<BrechaControl>,
)

/** Controles del más antiguo al más reciente (a igual fecha, por creación). */
fun ordenarControlesAsc(cs: List<ControlOrtodoncia>): List<ControlOrtodoncia> =
    cs.sortedWith(compareBy<ControlOrtodoncia> { it.fecha }.thenBy { it.createdAt.orEmpty() })

/** El más reciente primero (la lista de la ficha y la API). */
fun ordenarControles(cs: List<ControlOrtodoncia>): List<ControlOrtodoncia> = ordenarControlesAsc(cs).reversed()

/** El más reciente primero (por creación). */
fun ordenarPlanesOrto(ps: List<PlanOrtodoncia>): List<PlanOrtodoncia> = ps.sortedByDescending { it.createdAt.orEmpty() }

private fun semanasDe(dias: Int): Int = floor(dias / 7.0).toInt()

fun estadoControles(controles: List<ControlOrtodoncia>, plan: PlanOrtodoncia, hoy: String): EstadoControlesOrto {
    val asc = ordenarControlesAsc(controles)
    val ultimo = asc.lastOrNull()
    val enRetencion = plan.fase == "retencion" || !plan.fechaRetiro.isNullOrBlank()
    val semanasDef = aparatologiaOrtoPorId(plan.aparatologia)?.semanas ?: 4
    val brechas = mutableListOf<BrechaControl>()
    for (i in 1 until asc.size) {
        val a = asc[i - 1]
        val b = asc[i]
        if (b.fase == "retencion" || a.fase == "retencion") continue
        val dias = diasEntreOrto(a.fecha, b.fecha)
        if (dias > DIAS_CONTROL_ATRASADO) brechas += BrechaControl(a.fecha, b.fecha, semanasDe(dias))
    }
    val base = ultimo?.fecha ?: plan.fechaInstalacion?.takeIf { it.isNotBlank() }
    val proximoEstimado = base?.let { sumarDiasOrto(it, 7 * (ultimo?.proximaSemanas ?: semanasDef)) }
    val desde = base?.let { diasEntreOrto(it, hoy) }
    val atrasado = !enRetencion && desde != null && desde > DIAS_CONTROL_ATRASADO
    val toca = !enRetencion && !atrasado && proximoEstimado != null && proximoEstimado < hoy
    return EstadoControlesOrto(
        ultimo = ultimo,
        proximoEstimado = if (enRetencion && ultimo?.proximaSemanas == null) null else proximoEstimado,
        semanasDesdeUltimo = ultimo?.let { semanasDe(diasEntreOrto(it.fecha, hoy)) },
        atrasado = atrasado,
        toca = toca,
        brechas = brechas,
    )
}

/** La progresión de arcos de una arcada: el arco y desde cuándo, sin repetir el mismo seguido. */
fun progresionArcos(controles: List<ControlOrtodoncia>, superior: Boolean): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    for (c in ordenarControlesAsc(controles)) {
        val a = (if (superior) c.arcoSuperior else c.arcoInferior)?.takeIf { it.isNotBlank() } ?: continue
        if (out.isNotEmpty() && mismoArco(out.last().first, a)) continue
        out += a to c.fecha
    }
    return out
}

data class TendenciaHigiene(val serie: List<String>, val tendencia: String?)

private val PUNTOS_NIVEL = mapOf("buena" to 2, "regular" to 1, "mala" to 0)

/**
 * La higiene de los últimos controles (hasta 6, del más antiguo al último) y su
 * tendencia: el promedio de los 3 últimos contra los 3 anteriores (buena 2 ·
 * regular 1 · mala 0); medio punto de diferencia ya es un cambio. Sin tendencia
 * con menos de 2 registros.
 */
fun tendenciaHigiene(controles: List<ControlOrtodoncia>): TendenciaHigiene {
    val todos = ordenarControlesAsc(controles).mapNotNull { c -> c.higiene?.takeIf { it in PUNTOS_NIVEL } }
    val serie = todos.takeLast(6)
    if (todos.size < 2) return TendenciaHigiene(serie, null)
    val ult = todos.takeLast(3)
    // slice(-6, -3) de la web.
    val ant = todos.subList(maxOf(0, todos.size - 6), maxOf(0, todos.size - 3))
    fun prom(xs: List<String>) = xs.sumOf { PUNTOS_NIVEL.getValue(it) }.toDouble() / xs.size
    val base = if (ant.isNotEmpty()) ant else todos.take(1)
    val cmp = if (ant.isNotEmpty()) ult else todos.takeLast(1)
    val dif = prom(cmp) - prom(base)
    return TendenciaHigiene(serie, if (dif >= 0.5) "mejora" else if (dif <= -0.5) "empeora" else "estable")
}

// ─── 💰 Cuotas del tratamiento (solo lectura; gemelo de ResumenCuotasOrto.tsx) ─

/**
 * Lo que se lee para el resumen: el cronograma (`cuotas_tratamiento`: número,
 * monto, vence) y lo PAGADO del tratamiento (la suma de `pagos_tratamiento`,
 * como la web). Qué cuota está pagada no se guarda: se calcula aplicando lo
 * pagado en orden ([estadoCuotas], gemelo de lib/cuotas.ts).
 */
data class CuotasTratamientoOrto(val plan: List<CuotaPlan>, val pagado: Double)

/** Filas crudas de la base → [CuotasTratamientoOrto] (montos numeric pueden llegar como texto). */
fun cuotasOrtoDeFilas(cuotas: List<JsonObject>, pagos: List<JsonObject>): CuotasTratamientoOrto {
    fun JsonObject.num(k: String): Double? = txtOrto(k)?.toDoubleOrNull()?.takeIf { it.isFinite() }
    val plan = cuotas.mapNotNull { o ->
        val numero = o.intOrto("numero") ?: return@mapNotNull null
        val vence = o.txtOrto("vence")?.take(10)?.takeIf { Regex("""^\d{4}-\d{2}-\d{2}$""").matches(it) } ?: return@mapNotNull null
        CuotaPlan(numero, o.num("monto") ?: 0.0, vence)
    }.distinctBy { it.numero }
    return CuotasTratamientoOrto(plan, pagos.sumOf { it.num("monto") ?: 0.0 })
}

/** Lo que pinta la tarjeta 💰 Cuotas: cada cuota con su estado y el resumen (null = sin cronograma). */
data class ResumenCuotasOrto(val cuotas: List<CuotaConEstado>, val resumen: ResumenCuotas?, val pagado: Double)

fun resumenCuotasOrto(datos: CuotasTratamientoOrto, hoy: String): ResumenCuotasOrto {
    val cuotas = estadoCuotas(datos.plan, datos.pagado, hoy)
    return ResumenCuotasOrto(cuotas, if (cuotas.isEmpty()) null else resumenCuotas(cuotas), datos.pagado)
}

/** "⚠ 1 atrasada (S/ 125.00)" o null si está al día. */
fun textoAtrasoCuotas(r: ResumenCuotas, moneda: String): String? =
    if (r.atrasadas == 0) null
    else "⚠ ${r.atrasadas} atrasada${if (r.atrasadas == 1) "" else "s"} (${formatearDinero(r.montoAtrasado, moneda)})"

private val MESES_CORTOS = listOf("ene.", "feb.", "mar.", "abr.", "may.", "jun.", "jul.", "ago.", "set.", "oct.", "nov.", "dic.")

/**
 * `fechaCorta` de la web (toLocaleDateString es-PE, día y mes corto): "15 oct.";
 * de otro año que [hoy], con el año: "5 ene. 2027". Otra cosa se deja como vino.
 */
fun fechaCortaCuota(iso: String, hoy: String): String {
    val m = Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(iso) ?: return iso
    val (a, mes, d) = m.destructured
    val nombre = MESES_CORTOS.getOrNull(mes.toInt() - 1) ?: return iso
    return "${d.toInt()} $nombre" + if (a != hoy.take(4)) " $a" else ""
}

/** "5 de 16: S/ 125.00 — atrasada 10 días (15 oct.)" — la próxima por pagar, como la web. */
fun textoProximaCuota(r: ResumenCuotas, moneda: String, hoy: String): String? {
    val p = r.proxima ?: return null
    return "${etiquetaCuota(p.numero, r.nCuotas)}: ${formatearDinero(p.saldo, moneda)} — " +
        "${textoEstadoCuota(p).lowercase()} (${fechaCortaCuota(p.vence, hoy)})"
}

// ─── Textos ──────────────────────────────────────────────────────────────────

/** "Clase II · ambos lados · 3/16" · media · 22 h/día" */
fun textoElastico(x: ElasticoOrto): String {
    val tipo = TIPOS_ELASTICO.firstOrNull { it.id == x.tipo }?.corto ?: x.tipo
    val lado = when (x.lado) { "ambos" -> "ambos lados"; "der" -> "lado derecho"; else -> "lado izquierdo" }
    val fuerza = x.fuerza?.let { f -> FUERZAS_ELASTICO.firstOrNull { it.id == f }?.nombre?.lowercase() }
    return listOfNotNull(tipo, lado, x.tamano, fuerza, x.horas?.takeIf { it > 0 }?.let { "$it h/día" })
        .filter { it.isNotEmpty() }.joinToString(" · ")
}

/** "Elásticas azul" · "Metálicas" · "Sin ligadura". null sin tipo ni color. */
fun textoLigadura(tipo: String?, color: String?): String? {
    if (tipo.isNullOrEmpty() && color.isNullOrEmpty()) return null
    val n = when (tipo) { "elasticas" -> "Elásticas"; "metalicas" -> "Metálicas"; "sin_ligadura" -> "Sin ligadura"; else -> "Ligaduras" }
    return if (!color.isNullOrEmpty() && tipo != "sin_ligadura") "$n ${color.lowercase()}" else n
}

/** "Molar: der. II · izq. I · Canina: der. — · izq. II" (lado del paciente). null si no hay nada. */
fun textoClaseInicial(p: PlanOrtodoncia): String? {
    fun par(n: String, d: String?, i: String?) = if (d != null || i != null) "$n: der. ${d ?: "—"} · izq. ${i ?: "—"}" else null
    val t = listOfNotNull(par("Molar", p.claseMolarDer, p.claseMolarIzq), par("Canina", p.claseCaninaDer, p.claseCaninaIzq))
    return t.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** "Sup. NiTi .016 · Inf. NiTi .014" o null. */
fun textoArcosControl(c: ControlOrtodoncia): String? =
    listOfNotNull(c.arcoSuperior?.let { "Sup. $it" }, c.arcoInferior?.let { "Inf. $it" }).joinToString(" · ").ifEmpty { null }

/** El texto de la cita para el selector del control. */
data class CitaOrto(
    val id: String,
    val fecha: String,
    val hora: String? = null,
    val estado: String,
    val tipo: String? = null,
    val servicio: String? = null,
    val tratamientoId: String? = null,
    val terapeutaId: String? = null,
)

private val ESTADOS_SIN_CITA_ORTO = setOf("Cancelada", "Completada", "No asistió")

/** El próximo control ya agendado (de hoy en adelante, sin cancelar ni completar). */
fun proximaCitaAgendada(citas: List<CitaOrto>, hoy: String): CitaOrto? = citas
    .filter { it.fecha.take(10) >= hoy && it.estado !in ESTADOS_SIN_CITA_ORTO }
    .minByOrNull { "${it.fecha}${it.hora.orEmpty()}" }

/** Citas que se pueden vincular a un control: sin cancelar, sin control (o la propia), la más reciente primero. */
fun citasElegiblesControl(citas: List<CitaOrto>, conControl: Set<String>, propia: String?): List<CitaOrto> = citas
    .filter { it.estado != "Cancelada" && it.estado != "No asistió" && (it.id !in conControl || it.id == propia) }
    .sortedByDescending { "${it.fecha}${it.hora.orEmpty()}" }

/**
 * Tras guardar un control NUEVO: ¿se ofrece "📝 Dejar los cuidados con brackets"?
 * Aparatología con brackets y la cita del control es la INSTALACIÓN.
 */
fun ofreceCuidadosBrackets(nuevo: Boolean, aparatologia: String?, cita: CitaOrto?): Boolean =
    nuevo && aparatologiaOrtoPorId(aparatologia)?.brackets == true &&
        tipoAtencionOrtodoncia(cita?.servicio, cita?.tipo) == "instalacion"

// ─── Ficha de ortodoncia imprimible (gemela de HojaOrtodoncia.tsx) ───────────

private fun escOrto(s: String?): String = s.orEmpty()
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

/**
 * La FICHA DE ORTODONCIA (A4), armada en la app (el visor nativo la imprime o
 * guarda en PDF): la clínica, el paciente, el plan, el progreso, la
 * progresión de arcos, la tabla de controles y la firma. Todo lo que viene de
 * la base se escapa; el logo solo si es https y el color solo si es #rrggbb.
 */
fun htmlFichaOrtodoncia(
    plan: PlanOrtodoncia,
    controles: List<ControlOrtodoncia>,
    clinicaNombre: String?,
    clinicaDireccion: String?,
    logoUrl: String?,
    colorPrincipal: String?,
    pacienteNombre: String?,
    pacienteDocumento: String?,
    profesional: String?,
    servicio: String?,
    hoy: String,
): String {
    val color = colorHojaIndicaciones(colorPrincipal)
    val prog = progresoTratamiento(plan, hoy)
    val esperada = if (prog.retirado || plan.fase == "retencion") null else faseEsperada(prog.meses, plan.duracionEstimadaMeses)
    val est = estadoControles(controles, plan, hoy)
    val asc = ordenarControlesAsc(controles)
    val sup = progresionArcos(controles, superior = true)
    val inf = progresionArcos(controles, superior = false)
    val datos = listOf(
        "Aparatología" to (aparatologiaOrtoPorId(plan.aparatologia)?.nombre ?: plan.aparatologia),
        "Arcadas" to nombreOrto(ARCADAS_ORTO, plan.arcadas),
        "Instalación" to (plan.fechaInstalacion?.let { fechaIndicacion(it) } ?: "Sin instalar"),
        "Duración estimada" to plan.duracionEstimadaMeses?.let { "$it meses" },
        "Avance" to prog.texto,
        "Fase actual" to (faseOrtoPorId(plan.fase)?.nombre ?: plan.fase),
        "Clase inicial" to textoClaseInicial(plan),
        "Retención" to listOfNotNull(nombreOrto(RETENCIONES_ORTO, plan.retencionPlanificada), plan.fechaRetiro?.let { "retiro ${fechaIndicacion(it)}" })
            .joinToString(" · ").ifEmpty { null },
    ).filter { !it.second.isNullOrBlank() } +
        (if (esperada != null && esperada != plan.fase) listOf("Por el tiempo" to "${faseOrtoPorId(esperada)?.nombre} (orientativo)") else emptyList())
    val logo = logoUrl?.trim()?.takeIf { it.startsWith("https://") }
        ?.let { "<img src=\"${escOrto(it)}\" alt=\"\" class=\"logo\">" }.orEmpty()
    val filasPlan = datos.joinToString("") { (k, v) -> "<tr><td class=\"k\">${escOrto(k)}</td><td>${escOrto(v)}</td></tr>" }
    val arcos = if (sup.isEmpty() && inf.isEmpty()) "" else buildString {
        append("<div class=\"arcos\"><b>Progresión de arcos</b>")
        if (sup.isNotEmpty()) append("<div>Superior: ${escOrto(sup.joinToString(" → ") { it.first })}</div>")
        if (inf.isNotEmpty()) append("<div>Inferior: ${escOrto(inf.joinToString(" → ") { it.first })}</div>")
        append("</div>")
    }
    val atrasos = if (est.brechas.isEmpty() && !est.atrasado) "" else buildString {
        append("<p class=\"rojo\">")
        if (est.brechas.isNotEmpty()) append("${est.brechas.size} intervalo${if (est.brechas.size == 1) "" else "s"} de más de 8 semanas sin control. ")
        if (est.atrasado) append("Control atrasado a la fecha.")
        append("</p>")
    }
    val filasControles = asc.joinToString("") { c ->
        val lig = buildString {
            textoLigadura(c.ligaduraTipo, c.ligaduraColor)?.let { append("<div>${escOrto(it)}</div>") }
            c.cadeneta?.let { append("<div>Cadeneta ${escOrto(it)}</div>") }
            normalizarElasticos(c.elasticos).forEach { append("<div>${escOrto(textoElastico(it))}</div>") }
        }.ifEmpty { "—" }
        val br = buildString {
            if (c.bracketsDespegados.isNotEmpty()) append("<div>Despeg. ${escOrto(c.bracketsDespegados.joinToString(", "))}</div>")
            if (c.bracketsRecementados.isNotEmpty()) append("<div>Recem. ${escOrto(c.bracketsRecementados.joinToString(", "))}</div>")
        }.ifEmpty { "—" }
        val ar = buildString {
            c.arcoSuperior?.let { append("<div>Sup. ${escOrto(it)}</div>") }
            c.arcoInferior?.let { append("<div>Inf. ${escOrto(it)}</div>") }
        }.ifEmpty { "—" }
        "<tr><td class=\"nw\">${escOrto(fechaIndicacion(c.fecha))}</td><td>${escOrto(c.fase?.let { faseOrtoPorId(it)?.corto } ?: "—")}</td>" +
            "<td>$ar</td><td>$lig</td><td>$br</td><td>${escOrto(nombreOrto(NIVELES_ORTO, c.higiene) ?: "—")}</td>" +
            "<td class=\"obs\">${escOrto(c.observaciones ?: "—")}</td></tr>"
    }
    val tablaControles = if (asc.isEmpty()) "<p class=\"s\">Sin controles registrados.</p>" else
        "<table class=\"ctl\"><thead><tr><th>Fecha</th><th>Fase</th><th>Arcos</th><th>Ligaduras / elásticos</th><th>Brackets</th><th>Higiene</th><th>Observaciones</th></tr></thead><tbody>$filasControles</tbody></table>"
    return """<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Ficha de ortodoncia — ${escOrto(pacienteNombre)}</title>
<style>
@page { size: A4 portrait; margin: 1.5cm; }
* { box-sizing: border-box; }
body { font-family: Lato, 'Segoe UI', Arial, sans-serif; color: #111827; background: #fff; margin: 0 auto; max-width: 800px; padding: 16px; font-size: 10pt; line-height: 1.35; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
.cab { display: flex; align-items: center; gap: 12px; padding-bottom: 10px; border-bottom: 2px solid $color; }
.logo { width: 64px; height: 64px; object-fit: contain; }
.cli { flex: 1; min-width: 0; } .cli .n { font-weight: bold; font-size: 13pt; text-transform: uppercase; word-break: break-word; }
.s { color: #4b5563; font-size: 8.5pt; }
.doc { text-align: right; } .doc .n { font-weight: bold; font-size: 11pt; color: $color; }
.pac { padding: 8px 0; border-bottom: 1px solid #e5e7eb; font-size: 9.5pt; } .pac span { margin-right: 24px; }
h2 { margin: 14px 0 4px; font-size: 9.5pt; text-transform: uppercase; letter-spacing: .03em; color: $color; }
table { width: 100%; border-collapse: collapse; }
.plan td { padding: 2px 12px 2px 0; font-size: 9pt; vertical-align: top; } .plan .k { width: 38mm; color: #4b5563; font-weight: bold; }
.obj, .arcos { margin-top: 6px; font-size: 9pt; }
.rojo { margin-top: 4px; font-size: 8.5pt; color: #b91c1c; }
.ctl { margin-top: 4px; font-size: 8.5pt; }
.ctl th { text-align: left; color: $color; border-bottom: 2px solid #d1d5db; padding: 4px 6px 4px 0; }
.ctl td { border-bottom: 1px solid #e5e7eb; padding: 4px 6px 4px 0; vertical-align: top; }
tr { break-inside: avoid; } .nw { white-space: nowrap; } .obs { word-break: break-word; }
.firma { margin-top: 48px; display: flex; justify-content: flex-end; break-inside: avoid; } .firma div { width: 64mm; text-align: center; font-size: 8.5pt; }
.linea { border-top: 1px solid #1f2937; margin-top: 14mm; padding-top: 2px; }
.pie-hoja { margin-top: 24px; padding-top: 6px; border-top: 1px solid #e5e7eb; font-size: 7.5pt; color: #9ca3af; display: flex; justify-content: space-between; gap: 8px; }
</style></head><body>
<div class="cab">$logo<div class="cli"><div class="n">${escOrto(clinicaNombre)}</div>${clinicaDireccion?.takeIf { it.isNotBlank() }?.let { "<div class=\"s\">${escOrto(it)}</div>" }.orEmpty()}</div>
<div class="doc"><div class="n">FICHA DE ORTODONCIA</div><div class="s">${escOrto(fechaIndicacion(hoy))}</div></div></div>
<div class="pac"><span><b>Paciente:</b> ${escOrto(pacienteNombre?.takeIf { it.isNotBlank() } ?: "—")}</span>${pacienteDocumento?.takeIf { it.isNotBlank() }?.let { "<span><b>Documento:</b> ${escOrto(it)}</span>" }.orEmpty()}${servicio?.takeIf { it.isNotBlank() }?.let { "<span><b>Tratamiento:</b> ${escOrto(it)}</span>" }.orEmpty()}</div>
<h2>Plan de tratamiento</h2>
<table class="plan"><tbody>$filasPlan</tbody></table>
${if (plan.objetivos.isNotEmpty()) "<p class=\"obj\"><b>Objetivos:</b> ${escOrto(plan.objetivos.joinToString(" · "))}</p>" else ""}
$arcos
$atrasos
<h2>Controles (${asc.size})</h2>
$tablaControles
<div class="firma"><div><div class="linea">Firma y sello del cirujano dentista</div>${profesional?.takeIf { it.isNotBlank() }?.let { "<b>${escOrto(it)}</b>" }.orEmpty()}</div></div>
<div class="pie-hoja"><span>Clase de Angle: lado del paciente. Fase por el tiempo: orientativa.</span><span>Emitido con Sania</span></div>
</body></html>"""
}
