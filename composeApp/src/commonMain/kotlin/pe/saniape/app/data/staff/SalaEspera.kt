package pe.saniape.app.data.staff

import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

// ─────────────────────────────────────────────────────────────────────────────
// SALA DE ESPERA PARA TODAS LAS CLÍNICAS (09/11/2026) — reglas puras.
// GEMELO de lib/sala-espera.ts de la web (mismos casos: SalaEsperaTest.kt ↔
// lib/__tests__/sala-espera.test.ts), más `llegoALaVista` (lib/acciones-cita.ts),
// `pasarEnInicio` (lib/cola-inicio.ts) y las piezas puras de
// hooks/useAccionSala.ts y de /citas (SalaEsperaAppTest.kt). Si cambia uno,
// cambia el otro.
//
// «🔔 Llegó» y «▶ Pasar» viven en la CITA (citas.llegada_at / consulta_at).
// Las clínicas con triaje o flujo médico además guardan la llegada en su
// atención ([AtencionLlegada]): acá se mezclan. La CITA manda para «atendido»
// y «no vino», como [etapaLlegada]. Un momento solo cuenta si es del DÍA de la
// cita en la zona de la sede (una cita reprogramada no arrastra la llegada de
// otro día). Entre los que esperan sigue el que llegó primero (comparando
// instantes, no textos).
//
// Ninguna indicación sin nombre: cada fila lleva el del paciente
// ([SALA_PACIENTE_SIN_NOMBRE] si falta) y cada grupo el del profesional
// ([SALA_PROFESIONAL_SIN_NOMBRE] / [SALA_SIN_PROFESIONAL]).
// ─────────────────────────────────────────────────────────────────────────────

enum class EstadoSala { FUERA, POR_LLEGAR, ESPERANDO, EN_CONSULTA }

/** Lo que se manda a POST /api/staff/cita/sala ({ citaId, accion }). */
enum class AccionSala(val clave: String) {
    LLEGO("llego"), PASAR("pasar"), VOLVER("volver"), DESHACER("deshacer");

    companion object {
        /** `esAccionSala` de la web: la acción de esa clave, o null si no es una. */
        fun deClave(v: String?): AccionSala? = entries.firstOrNull { it.clave == v }
    }
}

data class MomentosSala(val llegadaAt: String? = null, val consultaAt: String? = null)

/** Lo mínimo de una cita para sus momentos de sala (`ConMomentosSala` de la web). */
interface ConMomentosSala {
    val id: String
    /** AAAA-MM-DD (o con hora); null = sin fecha: los momentos cuentan tal cual. */
    val fecha: String?
    val llegadaAt: String?
    val consultaAt: String?
}

/** Lo que la sala necesita de una cita de hoy (de [CitaStaff] o de la agenda del Inicio). */
data class CitaSalaEntrada(
    override val id: String,
    override val fecha: String,
    val hora: String? = null,
    val estado: String? = null,
    val noAsistio: Boolean = false,
    val tipo: String? = null,
    val terapeutaId: String? = null,
    val terapeutaNombre: String? = null,
    val pacienteNombre: String? = null,
    override val llegadaAt: String? = null,
    override val consultaAt: String? = null,
) : ConMomentosSala

data class FilaSala(
    val citaId: String,
    val paciente: String,
    val hora: String,
    val llegadaAt: String?,
    val consultaAt: String?,
    /** Minutos esperando (solo en «esperando»). */
    val minutos: Int?,
    val tipo: String?,
)

data class GrupoSala(
    val terapeutaId: String?,
    val nombre: String,
    val enConsulta: List<FilaSala>,
    /** Por orden de llegada: el primero es quien sigue. */
    val esperando: List<FilaSala>,
) {
    val sigue: FilaSala? get() = esperando.firstOrNull()
}

const val SALA_SIN_PROFESIONAL = "Sin profesional"
const val SALA_PACIENTE_SIN_NOMBRE = "Paciente sin nombre"
const val SALA_PROFESIONAL_SIN_NOMBRE = "Profesional sin nombre"

private val EN_CURSO = setOf("Pendiente", "Confirmada")

private fun instanteSala(iso: String?): Instant? = if (iso.isNullOrEmpty()) null else parsearInstante(iso)

/** Día (AAAA-MM-DD) de un instante en la zona; null si no se entiende. */
fun diaDelInstante(iso: String?, zona: String?): String? = instanteSala(iso)?.let { hoyEnIso(zona, it) }

/** "HH:MM" (24 h) de un instante en la zona; null si no se entiende. */
fun horaDelInstante(iso: String?, zona: String?): String? {
    val t = instanteSala(iso) ?: return null
    val l = t.toLocalDateTime(zonaDe(zona))
    return "${l.hour.toString().padStart(2, '0')}:${l.minute.toString().padStart(2, '0')}"
}

/** El momento si es del DÍA [fecha] (AAAA-MM-DD) en la zona; de otro día (o ilegible) → null. */
fun delDia(iso: String?, fecha: String, zona: String?): String? =
    if (!iso.isNullOrEmpty() && diaDelInstante(iso, zona) == fecha) iso else null

private fun masTemprano(vararg xs: String?): String? {
    var mejor: String? = null
    var tm: Instant? = null
    for (x in xs) {
        val t = instanteSala(x) ?: continue
        if (tm == null || t < tm) { tm = t; mejor = x }
    }
    return mejor
}

private fun fechaSala(c: ConMomentosSala): String = c.fecha.orEmpty().take(10)

/** Cuándo llegó: el más temprano entre la cita y su atención (llegada o triaje), del día de la cita. */
fun llegadaEfectiva(c: ConMomentosSala, a: AtencionLlegada?, zona: String?): String? {
    val f = fechaSala(c)
    return masTemprano(delDia(c.llegadaAt, f, zona), delDia(a?.llegadaAt, f, zona), delDia(a?.triajeAt, f, zona))
}

/** Cuándo entró a consulta: la cita («▶ Pasar») o su atención (consulta guiada). */
fun consultaEfectiva(c: ConMomentosSala, a: AtencionLlegada?, zona: String?): String? {
    val f = fechaSala(c)
    return masTemprano(delDia(c.consultaAt, f, zona), delDia(a?.consultaInicioAt, f, zona))
}

/**
 * La atención de la cita con la llegada / consulta de la CITA mezcladas (solo
 * las de su día): la MISMA cuenta de [estadoSala], para quien lee la etapa de la
 * atención ([etapaLlegada] de la tarjeta en las clínicas con triaje o flujo
 * médico). Así la tarjeta y la franja dicen lo mismo apenas llega la cita, sin
 * esperar la relectura de las atenciones. Sin momentos de la cita de su día →
 * la atención tal cual (el mismo objeto, o null).
 */
fun atencionSalaDeCita(c: ConMomentosSala, a: AtencionLlegada?, zona: String?): AtencionLlegada? {
    val f = fechaSala(c)
    val consulta = delDia(c.consultaAt, f, zona)
    if (delDia(c.llegadaAt, f, zona) == null && consulta == null) return a
    return (a ?: AtencionLlegada(id = null, citaId = c.id)).copy(
        llegadaAt = llegadaEfectiva(c, a, zona),
        consultaInicioAt = a?.consultaInicioAt?.takeIf { it.isNotEmpty() } ?: consulta,
    )
}

/** En qué punto de la sala está una cita de HOY ([hoy] en la zona de la sede). */
fun estadoSala(c: CitaSalaEntrada, a: AtencionLlegada?, hoy: String, zona: String?): EstadoSala {
    if (c.fecha.take(10) != hoy) return EstadoSala.FUERA
    if (c.noAsistio || c.estado !in EN_CURSO) return EstadoSala.FUERA
    if (consultaEfectiva(c, a, zona) != null) return EstadoSala.EN_CONSULTA
    if (llegadaEfectiva(c, a, zona) != null) return EstadoSala.ESPERANDO
    return EstadoSala.POR_LLEGAR
}

/** El nombre del paciente para la sala y sus avisos: nunca vacío. */
fun nombrePacienteSala(nombre: String?): String = nombre?.trim()?.takeIf { it.isNotEmpty() } ?: SALA_PACIENTE_SIN_NOMBRE

/**
 * Grupos por profesional de los que esperan o están en consulta: el propio
 * primero, luego por nombre, «Sin profesional» al final. Sin nadie → [].
 * [terapeutas] (id → nombre) nombra al profesional si la cita no lo trae.
 */
fun armarSala(
    citas: List<CitaSalaEntrada>,
    atenciones: Map<String, AtencionLlegada>,
    hoy: String,
    zona: String?,
    ahora: Instant,
    miTerapeutaId: String? = null,
    terapeutas: Map<String, String> = emptyMap(),
): List<GrupoSala> {
    class Acum(val terapeutaId: String?, val nombre: String) {
        val enConsulta = mutableListOf<FilaSala>()
        val esperando = mutableListOf<FilaSala>()
    }
    val grupos = LinkedHashMap<String, Acum>()
    for (c in citas) {
        val a = atenciones[c.id]
        val e = estadoSala(c, a, hoy, zona)
        if (e != EstadoSala.ESPERANDO && e != EstadoSala.EN_CONSULTA) continue
        val tid = c.terapeutaId?.takeIf { it.isNotEmpty() }
        val g = grupos.getOrPut(tid ?: "") {
            val nombre = if (tid == null) SALA_SIN_PROFESIONAL
            else c.terapeutaNombre?.trim()?.takeIf { it.isNotEmpty() }
                ?: terapeutas[tid]?.trim()?.takeIf { it.isNotEmpty() }
                ?: SALA_PROFESIONAL_SIN_NOMBRE
            Acum(tid, nombre)
        }
        val llegada = llegadaEfectiva(c, a, zona)
        val fila = FilaSala(
            citaId = c.id,
            paciente = nombrePacienteSala(c.pacienteNombre),
            hora = (c.hora ?: "").take(5),
            llegadaAt = llegada,
            consultaAt = if (e == EstadoSala.EN_CONSULTA) consultaEfectiva(c, a, zona) else null,
            // La misma cuenta que la tarjeta de la agenda (minutosEsperando).
            minutos = if (e == EstadoSala.ESPERANDO) minutosEsperando(llegada, ahora) else null,
            tipo = c.tipo,
        )
        if (e == EstadoSala.EN_CONSULTA) g.enConsulta.add(fila) else g.esperando.add(fila)
    }
    if (grupos.isEmpty()) return emptyList()
    fun t(iso: String?): Long = instanteSala(iso)?.toEpochMilliseconds() ?: Long.MAX_VALUE
    val porLlegada = compareBy<FilaSala>({ t(it.llegadaAt) }, { it.hora })
    val porConsulta = compareBy<FilaSala>({ t(it.consultaAt) }, { it.hora })
    fun rango(id: String?) = when {
        id == null -> 2
        miTerapeutaId != null && id == miTerapeutaId -> 0
        else -> 1
    }
    // Como localeCompare(…, 'es') de la web: Collator español en Android.
    val porNombre = comparadorNombresEs()
    return grupos.values
        .map { GrupoSala(it.terapeutaId, it.nombre, it.enConsulta.sortedWith(porConsulta), it.esperando.sortedWith(porLlegada)) }
        .sortedWith(compareBy<GrupoSala> { rango(it.terapeutaId) }.thenComparator { x, y -> porNombre.compare(x.nombre, y.nombre) })
}

/** Cuántos esperan en toda la sala (la cabecera «Sala de espera · N esperando»). */
fun totalEsperando(grupos: List<GrupoSala>): Int = grupos.sumOf { it.esperando.size }

private fun conValorSala(v: String?): Boolean = !v.isNullOrEmpty()

/**
 * Cómo queda la cita tras la acción (lo que se pinta al tocar). Repetirla no
 * cambia nada (devuelve el mismo [actual]). Las mismas reglas que `cambioSala`
 * del servidor: «▶ Pasar» sin «Llegó» marca las dos horas.
 */
fun aplicarAccionSala(accion: AccionSala, actual: MomentosSala, ahoraIso: String): MomentosSala = when (accion) {
    AccionSala.LLEGO -> if (conValorSala(actual.llegadaAt)) actual else actual.copy(llegadaAt = ahoraIso)
    AccionSala.PASAR -> when {
        conValorSala(actual.consultaAt) -> actual
        conValorSala(actual.llegadaAt) -> actual.copy(consultaAt = ahoraIso)
        else -> MomentosSala(llegadaAt = ahoraIso, consultaAt = ahoraIso)
    }
    AccionSala.VOLVER -> if (conValorSala(actual.consultaAt)) actual.copy(consultaAt = null) else actual
    AccionSala.DESHACER -> if (conValorSala(actual.llegadaAt) || conValorSala(actual.consultaAt)) MomentosSala() else actual
}

/** ¿Mismo punto de la sala (llegó / en consulta)? La hora exacta no importa: la del servidor manda. */
fun mismoEstadoSala(a: MomentosSala, b: MomentosSala): Boolean =
    conValorSala(a.llegadaAt) == conValorSala(b.llegadaAt) && conValorSala(a.consultaAt) == conValorSala(b.consultaAt)

/**
 * Los momentos que cuentan de la cita: solo los de SU día en la zona (como el
 * servidor), así una reprogramada que trae la llegada de otro día está «por
 * llegar». Sin fecha, tal cual.
 */
fun momentosDeSuDia(c: ConMomentosSala, zona: String?): MomentosSala {
    if (c.fecha.isNullOrEmpty()) return MomentosSala(c.llegadaAt, c.consultaAt)
    val f = fechaSala(c)
    return MomentosSala(delDia(c.llegadaAt, f, zona), delDia(c.consultaAt, f, zona))
}

/**
 * Lo tocado en ESTE teléfono: optimista ([confirmadaEn] null) o ya confirmado
 * por el servidor ([confirmadaEn] = epoch ms de la confirmación).
 */
data class PendienteSala(val momentos: MomentosSala, val confirmadaEn: Long? = null)

/** Pasado este tiempo, lo confirmado deja de pisar a la lista (otro equipo pudo moverla). */
const val VIDA_PENDIENTE_MS: Long = 10_000

/** Las citas con lo tocado en este teléfono encima. Sin pendientes, la MISMA lista. */
fun aplicarPendientes(citas: List<CitaSalaEntrada>, pendientes: Map<String, PendienteSala>): List<CitaSalaEntrada> {
    if (pendientes.isEmpty()) return citas
    return citas.map { c ->
        val p = pendientes[c.id] ?: return@map c
        c.copy(llegadaAt = p.momentos.llegadaAt, consultaAt = p.momentos.consultaAt)
    }
}

/**
 * La base del cambio optimista, como la ve el servidor: la cita con lo tocado
 * aquí encima y SOLO los momentos de su día (una reprogramada con la llegada de
 * ayer → «Llegó» cambia algo). Va a [aplicarAccionSala].
 */
fun baseDeAccionSala(c: ConMomentosSala, pendientes: Map<String, PendienteSala>, zona: String?): MomentosSala {
    val p = pendientes[c.id]?.momentos ?: return momentosDeSuDia(c, zona)
    return momentosDeSuDia(MomentosDeCita(c.id, c.fecha, p.llegadaAt, p.consultaAt), zona)
}

private data class MomentosDeCita(
    override val id: String,
    override val fecha: String?,
    override val llegadaAt: String?,
    override val consultaAt: String?,
) : ConMomentosSala

/**
 * Tras una recarga: se va lo que la lista ya trae (mismo estado DEL DÍA de la
 * cita: una llegada de otro día no cuenta) y lo confirmado hace más de
 * [VIDA_PENDIENTE_MS]. Queda lo optimista (o en la cola sin señal). Sin cambios
 * → el MISMO mapa.
 */
fun podarPendientes(
    citas: List<ConMomentosSala>,
    pendientes: Map<String, PendienteSala>,
    ahoraMs: Long,
    zona: String? = null,
): Map<String, PendienteSala> {
    if (pendientes.isEmpty()) return pendientes
    val porId = citas.associateBy { it.id }
    var cambio = false
    val out = LinkedHashMap<String, PendienteSala>()
    for ((id, p) in pendientes) {
        val c = porId[id]
        val alDia = c != null && mismoEstadoSala(momentosDeSuDia(c, zona), p.momentos)
        val vencida = p.confirmadaEn != null && ahoraMs - p.confirmadaEn > VIDA_PENDIENTE_MS
        if (alDia || vencida) { cambio = true; continue }
        out[id] = p
    }
    return if (cambio) out else pendientes
}

/**
 * ¿La cita tiene un momento de sala de HOY? (`llegoHoy` de /citas y del Inicio
 * web). Compara con [hoy], no con la fecha de la cita.
 */
fun llegoHoy(c: ConMomentosSala, hoy: String, zona: String?): Boolean =
    delDia(c.llegadaAt, hoy, zona) != null || delDia(c.consultaAt, hoy, zona) != null

/**
 * ¿La agenda de HOY usa la sala de espera? (`usaSala` de /citas). La franja
 * aparece solo con sala médica (triaje o flujo médico) o desde la primera
 * llegada del día (DALU: nada nuevo si nadie toca «Llegó»). [citasHoy]: las de
 * hoy a la vista (con lo tocado aquí encima); vacía si no se está mirando hoy.
 */
fun usaSalaHoy(citasHoy: List<ConMomentosSala>, salaMedica: Boolean, hoy: String, zona: String?): Boolean =
    citasHoy.isNotEmpty() && (salaMedica || citasHoy.any { llegoHoy(it, hoy, zona) })

/**
 * ¿El Inicio muestra «Sigue» (y abre su escucha en vivo)? (`sigueVisible` del
 * Inicio web): con la cola del día (sala médica) o desde la primera llegada de
 * hoy. Sin eso, el Inicio de siempre.
 */
fun sigueVisibleEnInicio(colaActiva: Boolean, citasHoy: List<ConMomentosSala>, hoy: String, zona: String?): Boolean =
    colaActiva || citasHoy.any { llegoHoy(it, hoy, zona) }

/**
 * ¿«🔔 Llegó» sin sala médica va a la vista en la tarjeta? (`llegoALaVista` de
 * lib/acciones-cita.ts): la clínica tiene el aviso al profesional (plan con
 * `notificacionesFlujo`) o ya usa la sala hoy ([usaSalaHoy]). Si no, va a
 * «⋯ Más» y Reprogramar conserva su lugar.
 */
fun llegoALaVista(notificacionesFlujo: Boolean, usaSala: Boolean): Boolean = notificacionesFlujo || usaSala

/**
 * ¿El «Sigue» del Inicio ofrece «▶ Pasar»? (`pasarEnInicio` de
 * lib/cola-inicio.ts). NUNCA en una clínica con sala médica (triaje o flujo
 * médico): ahí cada cita sigue su camino (🩺 Triaje / ▶ Atender). Va por
 * clínica, no por profesional (resolución 8). Mientras no cargó la
 * configuración ([contextoListo]), tampoco: el mapa vacío de mientras carga no
 * quiere decir «sin sala médica».
 */
fun pasarEnInicio(mapaMedico: MapaClinico, triaje: Boolean, contextoListo: Boolean): Boolean =
    contextoListo && !triaje && !mapaMedico.solo && mapaMedico.ids.isEmpty()

/** `cita` de la respuesta de POST /api/staff/cita/sala (null si no vino). */
fun momentosDeRespuesta(cuerpo: JsonObject?): MomentosSala? {
    val c = cuerpo?.get("cita") as? JsonObject ?: return null
    fun s(k: String) = (c[k] as? JsonPrimitive)?.content?.takeIf { it != "null" && it.isNotBlank() }
    return MomentosSala(s("llegada_at"), s("consulta_at"))
}

/** La respuesta de POST /api/staff/cita/sala: { ok, cita: { id, llegada_at, consulta_at }, cambio, avisoSolicitado }. */
data class RespuestaSala(val momentos: MomentosSala, val cambio: Boolean, val avisoSolicitado: Boolean)

fun respuestaSala(cuerpo: JsonObject?): RespuestaSala? {
    val m = momentosDeRespuesta(cuerpo) ?: return null
    fun b(k: String) = (cuerpo?.get(k) as? JsonPrimitive)?.booleanOrNull == true
    return RespuestaSala(m, cambio = b("cambio"), avisoSolicitado = b("avisoSolicitado"))
}

/** El profesional de la cita, para el aviso: su nombre (null si no hay) y si el aviso del «Llegó» le llegaría. */
data class ProfesionalSala(val nombre: String?, val avisable: Boolean)

/**
 * `profesionalSala` de /citas: el nombre embebido o el del catálogo
 * ([terapeutas] id → nombre); [ProfesionalSala.avisable] = hay profesional y no
 * es quien marca (el servidor nunca avisa a quien marca la llegada).
 */
fun profesionalSala(c: CitaSalaEntrada, miTerapeutaId: String?, terapeutas: Map<String, String> = emptyMap()): ProfesionalSala {
    val id = c.terapeutaId?.takeIf { it.isNotEmpty() }
    val nombre = if (id == null) null
    else c.terapeutaNombre?.trim()?.takeIf { it.isNotEmpty() } ?: terapeutas[id]?.trim()?.takeIf { it.isNotEmpty() }
    return ProfesionalSala(nombre, avisable = id != null && id != miTerapeutaId)
}

/** El aviso de cada acción, siempre con el nombre del paciente y, si lo hay, el del profesional. */
fun textoAccionSala(accion: AccionSala, paciente: String, profesional: String? = null): String {
    val con = profesional?.trim()?.takeIf { it.isNotEmpty() }?.let { " con $it" } ?: ""
    return when (accion) {
        AccionSala.LLEGO -> "$paciente en sala de espera$con"
        AccionSala.PASAR -> "$paciente pasó a consulta$con"
        AccionSala.VOLVER -> "$paciente volvió a la sala de espera$con"
        AccionSala.DESHACER -> "Se quitó la llegada de $paciente$con"
    }
}

/**
 * El aviso tras la respuesta del servidor (hooks/useAccionSala.ts): el texto
 * de la acción y «· se avisó» solo si el servidor pidió el aviso (primera
 * llegada y plan con avisos) y le llegaría al profesional.
 */
fun avisoAccionSala(accion: AccionSala, paciente: String, profesional: ProfesionalSala?, avisoSolicitado: Boolean): String =
    textoAccionSala(accion, paciente, profesional?.nombre) +
        if (accion == AccionSala.LLEGO && avisoSolicitado && profesional?.avisable == true) " · se avisó" else ""

/** "recién llegó" / "12 min" / "1 h 05 min". */
fun esperaTexto(min: Int?): String = when {
    min == null -> ""
    min == 0 -> "recién llegó"
    min < 60 -> "$min min"
    min % 60 == 0 -> "${min / 60} h"
    else -> "${min / 60} h ${(min % 60).toString().padStart(2, '0')} min"
}

/**
 * El botón de una fila de la franja. Sin sala médica: «▶ Pasar» al que espera
 * y el cierre de la tarjeta al que está en consulta (✓ Completar, o ▶ Atender
 * de la atención directa). Con sala médica ([salaMedica]: la cita tiene etapa
 * de sala médica —triaje de la clínica o consulta guiada—), el paso de siempre
 * de su tarjeta (la web pinta ahí `accionesCita`): 🩺 Triaje si falta, si no el
 * cierre (▶ Atender / ▶ Continuar de la guiada, ✓ Completar). NUNCA «▶ Pasar»
 * con sala médica.
 *
 * [medica]: la cita cierra con la consulta guiada (como la tarjeta).
 * [directa]: atención directa. [faltaTriaje]: llegó y su triaje falta.
 */
enum class BotonFilaSala(val texto: String) {
    PASAR("▶ Pasar"),
    COMPLETAR("✓ Completar"),
    ATENDER("▶ Atender"),
    CONTINUAR("▶ Continuar"),
    TRIAJE("🩺 Triaje"),
    ATENDER_DIRECTO("▶ Atender"),
}

fun botonFilaSala(
    estado: EstadoSala,
    salaMedica: Boolean,
    medica: Boolean,
    directa: Boolean,
    faltaTriaje: Boolean,
): BotonFilaSala? {
    if (estado != EstadoSala.ESPERANDO && estado != EstadoSala.EN_CONSULTA) return null
    val cierre = when {
        directa -> BotonFilaSala.ATENDER_DIRECTO
        medica -> if (estado == EstadoSala.EN_CONSULTA) BotonFilaSala.CONTINUAR else BotonFilaSala.ATENDER
        else -> BotonFilaSala.COMPLETAR
    }
    if (estado == EstadoSala.EN_CONSULTA) return cierre
    // Esperando.
    val conSalaMedica = salaMedica || medica || faltaTriaje
    if (!conSalaMedica) return BotonFilaSala.PASAR
    return if (faltaTriaje) BotonFilaSala.TRIAJE else cierre
}

// ── «Sigue» del Inicio (agenda de hoy de /api/dashboard/stats) ──────────────

/**
 * Una cita de la agenda del Inicio como entrada de la sala. La agenda del
 * Inicio ya es la de hoy (el endpoint la acota): se le pone la fecha [hoy],
 * como `SigueInicio.tsx`.
 */
fun entradaSalaDeAgenda(c: CitaAgenda, hoy: String): CitaSalaEntrada = CitaSalaEntrada(
    id = c.id, fecha = hoy, hora = c.hora, estado = c.estado, noAsistio = c.noAsistio, tipo = c.tipo,
    terapeutaId = c.terapeutaId, terapeutaNombre = c.terapeutaNombre, pacienteNombre = c.paciente,
    llegadaAt = c.llegadaAt, consultaAt = c.consultaAt,
)

/**
 * La sala del Inicio, desde la agenda de hoy de /api/dashboard/stats (y su
 * `cola`, con sala médica). [pendientes]: lo tocado en este teléfono (▶ Pasar)
 * mientras la agenda no lo trae.
 */
fun gruposSalaDeAgenda(
    agenda: List<CitaAgenda>,
    cola: Map<String, AtencionLlegada>,
    hoy: String,
    zona: String?,
    ahora: Instant,
    miTerapeutaId: String?,
    pendientes: Map<String, PendienteSala> = emptyMap(),
): List<GrupoSala> = armarSala(
    citas = aplicarPendientes(agenda.map { entradaSalaDeAgenda(it, hoy) }, pendientes),
    atenciones = cola, hoy = hoy, zona = zona, ahora = ahora, miTerapeutaId = miTerapeutaId,
)
