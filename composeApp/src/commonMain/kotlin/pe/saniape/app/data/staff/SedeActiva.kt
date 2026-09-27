package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Preferencias
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

// Multisede: la sede en la que el usuario trabaja "hoy". Gemelo de
// app/(app)/SedeProvider.tsx + lib/sedes.ts + lib/sedes-acceso.ts de la web.
//
// Convenciones (las mismas que la web):
//   · sedeId "" = consolidado ("todas las sedes"): SOLO Admin.
//   · Una fila SIN sede (sede_id NULL) es de la sede PRINCIPAL: los datos de
//     antes de activar la multisede. Elegir la principal nunca esconde nada viejo.
//   · Clínica sin multisede (DALU y casi todas): multiSede=false, no se filtra
//     nada, no se pregunta nada y no se hace ninguna consulta extra.

/** Filtro de sede para una consulta. null = no filtrar (sin multisede o consolidado). */
data class FiltroSede(val sedeId: String, val principalId: String?) {
    val esPrincipal: Boolean get() = principalId != null && sedeId == principalId
    /** Sufijo para claves de caché (no mezclar la agenda de una sede con la de otra). */
    val clave: String get() = ":sede=$sedeId"
}

/**
 * Aplica el filtro de sede a un query (en el SERVIDOR, así paginado y conteos
 * cuadran). La principal incluye las filas sin sede:
 * `or(sede_id.eq.X, sede_id.is.null)`. Gemelo de `filtroSedeQuery` (lib/sedes.ts).
 */
fun PostgrestFilterBuilder.filtroSede(f: FiltroSede?, columna: String = "sede_id") {
    if (f == null) return
    if (f.esPrincipal) {
        or {
            eq(columna, f.sedeId)
            exact(columna, null)
        }
    } else {
        eq(columna, f.sedeId)
    }
}

/** ¿La fila pertenece a la sede? null = todo. Sin sede = principal. Gemelo de `enSede`. */
fun enSede(sedeFila: String?, f: FiltroSede?): Boolean {
    if (f == null) return true
    if (sedeFila == f.sedeId) return true
    return sedeFila == null && f.esPrincipal
}

/** Resultado de resolver la sede al entrar (gemelo de `resolverSedeInicial`). */
data class ResolucionSede(val sedeId: String?, val sinSedes: Boolean)

/**
 * Qué sede queda activa al entrar, en orden:
 *   1. Si solo tiene UNA sede posible → esa, sin preguntar (ni consolidado).
 *   2. La que eligió antes en este teléfono, si sigue siendo válida.
 *   3. La guardada en su perfil (la eligió en la web o en otro teléfono).
 *   4. Nada → hay que preguntar (diálogo obligatorio).
 */
fun resolverSedeInicial(
    disponibles: List<String>,
    puedeConsolidado: Boolean,
    previaLocal: String?,
    enPerfil: String?,
): ResolucionSede {
    if (disponibles.isEmpty()) return ResolucionSede(null, sinSedes = true)
    if (disponibles.size == 1 && !puedeConsolidado) return ResolucionSede(disponibles[0], false)
    fun valida(v: String?): Boolean =
        if (v == "") puedeConsolidado else v != null && v in disponibles
    if (previaLocal != null && valida(previaLocal)) return ResolucionSede(previaLocal, false)
    if (valida(enPerfil)) return ResolucionSede(enPerfil, false)
    return ResolucionSede(null, false)
}

/** Estado de la sede activa (lo que observan las pantallas). */
data class EstadoSede(
    val multiSede: Boolean = false,
    /** "" = consolidado (o sin multisede). */
    val sedeId: String = "",
    val principalId: String? = null,
    val sedes: List<SedeRef> = emptyList(),
    val puedeConsolidado: Boolean = false,
    /** Todavía no eligió (diálogo obligatorio, no se puede cerrar sin elegir). */
    val obligatorio: Boolean = false,
    /** Diálogo abierto a pedido ("cambiar de sede"). */
    val cambiando: Boolean = false,
    /** Limitado a sedes que hoy están todas desactivadas. */
    val sinSedes: Boolean = false,
) {
    val sedeActiva: SedeRef? get() = sedes.find { it.id == sedeId }
    /** Filtro para las lecturas. null = no filtrar. */
    val filtro: FiltroSede? get() = if (multiSede && sedeId.isNotEmpty()) FiltroSede(sedeId, principalId) else null
    /** ¿Tiene más de una opción? (si no, el chip no abre nada). */
    val puedeCambiar: Boolean get() = sedes.size + (if (puedeConsolidado) 1 else 0) > 1
    val pedirSede: Boolean get() = multiSede && (obligatorio || cambiando || sinSedes)
    /** Texto del chip. */
    val etiqueta: String get() = sedeActiva?.nombre ?: if (sedeId.isEmpty()) "Todas las sedes" else "Sede"
}

object SedeActiva {

    private val http = crearHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _estado = MutableStateFlow(EstadoSede())
    val estado: StateFlow<EstadoSede> = _estado.asStateFlow()

    /** Atajo para los repos: el filtro vigente (null = no filtrar). */
    val filtro: FiltroSede? get() = _estado.value.filtro

    /** Lo que guarda la base (perfiles.sede_actual_id) según lo último que sabemos. */
    private var enPerfil: String? = null
    private var claveLocal: String? = null

    private const val TODAS = "*"   // en el teléfono: "" podría confundirse con "nada guardado"

    /**
     * Se llama cada vez que llega el contexto (entrar, reintentar, cambiar de
     * clínica). Sin multisede deja todo en su default: cero cambios de UI.
     */
    fun iniciar(ctx: ContextoStaff) {
        if (!ctx.multiSede) {
            enPerfil = null; claveLocal = null
            if (_estado.value != EstadoSede()) _estado.value = EstadoSede()
            return
        }
        val userId = runCatching { Supabase.client.auth.currentSessionOrNull()?.user?.id }.getOrNull()
        claveLocal = userId?.let { "sede_activa:$it:${ctx.clinicaId}" }
        val previa = claveLocal?.let { k -> runCatching { Preferencias.texto(k) }.getOrNull() }
            ?.let { if (it == TODAS) "" else it }
        enPerfil = ctx.sedeActualId
        // "Todas las sedes" es SOLO del Admin (la RPC lo vuelve a verificar).
        val puedeConsolidado = ctx.esAdmin
        val disponibles = ctx.sedes.map { it.id }
        val r = resolverSedeInicial(disponibles, puedeConsolidado, previa, ctx.sedeActualId)
        val principal = ctx.sedePrincipalId ?: ctx.sedes.find { it.esPrincipal }?.id
        // Si ya estaba trabajando en una sede válida (recarga del contexto), se respeta.
        val actual = _estado.value
        val sigue = actual.multiSede && validaEn(actual.sedeId, disponibles, puedeConsolidado) && !actual.obligatorio
        val sedeId = if (sigue) actual.sedeId else (r.sedeId ?: "")
        _estado.value = EstadoSede(
            multiSede = true,
            sedeId = sedeId,
            principalId = principal,
            sedes = ctx.sedes,
            puedeConsolidado = puedeConsolidado,
            obligatorio = !sigue && r.sedeId == null && !r.sinSedes,
            sinSedes = r.sinSedes,
        )
        // La base tiene que saber la sede: su trigger la usa para completar la de
        // las citas y cobros que se crean sin sede (bot, cobros, app vieja…).
        if ((sigue || r.sedeId != null) && hayQueSincronizar(sedeId)) sincronizar(sedeId, avisarError = false)
    }

    private fun validaEn(id: String, disponibles: List<String>, puedeConsolidado: Boolean): Boolean =
        if (id == "") puedeConsolidado else id in disponibles

    private fun hayQueSincronizar(elegida: String): Boolean =
        (elegida.ifEmpty { null }) != enPerfil

    /** El usuario eligió una sede ("" = todas, solo Admin). */
    fun elegir(id: String) {
        val e = _estado.value
        if (!e.multiSede || !validaEn(id, e.sedes.map { it.id }, e.puedeConsolidado)) return
        claveLocal?.let { k -> runCatching { Preferencias.setTexto(k, if (id == "") TODAS else id) } }
        val cambio = id != e.sedeId || e.obligatorio
        _estado.value = e.copy(sedeId = id, obligatorio = false, cambiando = false)
        if (cambio && hayQueSincronizar(id)) sincronizar(id, avisarError = true)
    }

    fun abrirCambio() {
        val e = _estado.value
        if (e.multiSede && e.puedeCambiar) _estado.value = e.copy(cambiando = true)
    }

    fun cerrarCambio() {
        // Con elección pendiente el diálogo sigue (obligatorio).
        _estado.value = _estado.value.copy(cambiando = false)
    }

    /** Al cerrar sesión: nada de la sede del anterior queda en memoria. */
    fun limpiar() {
        enPerfil = null; claveLocal = null
        _estado.value = EstadoSede()
    }

    /** POST /api/staff/sede-actual → RPC fijar_sede_actual (valida acceso y consolidado). */
    private fun sincronizar(sedeId: String, avisarError: Boolean) {
        scope.launch {
            val tk = runCatching { Supabase.client.auth.currentSessionOrNull()?.accessToken }.getOrNull()
                ?: return@launch
            val cuerpo = buildJsonObject {
                if (sedeId.isEmpty()) put("sedeId", JsonNull) else put("sedeId", sedeId)
            }
            val r = runCatching {
                http.post("${Supabase.SITE_URL}/api/staff/sede-actual") {
                    header("Authorization", "Bearer $tk")
                    contentType(ContentType.Application.Json)
                    setBody(cuerpo.toString())
                }
            }.getOrNull()
            if (r != null && r.status.isSuccess()) {
                enPerfil = sedeId.ifEmpty { null }
            } else if (avisarError) {
                // Solo si la eligió a mano; la resolución automática falla en silencio (como la web).
                val msg = r?.let {
                    runCatching {
                        (json.parseToJsonElement(it.bodyAsText()).jsonObject["error"] as? JsonPrimitive)?.content
                    }.getOrNull()
                }
                pe.saniape.app.ui.Toaster.error(msg ?: "No se pudo guardar la sede. Se usará en este teléfono.")
            }
        }
    }
}

// ── Agendar por sede (gemelo de lib/sedes-agenda.ts) ─────────────────────────

/** Días como los guarda `horarios_terapeuta.dia`. Índice = domingo 0. */
private val DIAS_CORTOS = listOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")

/** "YYYY-MM-DD" → "Lun" | "Mar" … */
fun diaCorto(fechaIso: String): String? = runCatching {
    val d = kotlinx.datetime.LocalDate.parse(fechaIso)
    DIAS_CORTOS[d.dayOfWeek.ordinal.let { (it + 1) % 7 }]   // ordinal: lunes=0 … domingo=6
}.getOrNull()

/** Una franja de horarios_terapeuta (lo mínimo). */
data class FranjaSede(val terapeutaId: String, val dia: String, val sedeId: String?)

/** Sede base y "a demanda" de un profesional (terapeutas.sede_id / horario_flexible). */
data class SedeBaseTerapeuta(val sedeId: String?, val horarioFlexible: Boolean)

/**
 * Profesionales que se ofrecen al agendar en la sede el día [dia]. Gemelo de
 * `profesionalesEnSede` (web):
 *  · Tiene franjas cargadas → solo si alguna de ESE día es de esa sede.
 *  · A demanda o sin ninguna franja en la semana → por su sede base (sin sede = principal).
 *  · [mantener]: el ya elegido no se borra de la lista.
 * Sin filtro (sin multisede o consolidado) → la lista tal cual.
 */
fun <T> profesionalesEnSede(
    terapeutas: List<T>,
    idDe: (T) -> String,
    base: Map<String, SedeBaseTerapeuta>,
    franjas: List<FranjaSede>,
    dia: String?,
    f: FiltroSede?,
    mantener: String? = null,
): List<T> {
    if (f == null || dia == null) return terapeutas
    val porProfesional = franjas.groupBy { it.terapeutaId }
    return terapeutas.filter { t ->
        val id = idDe(t)
        if (mantener != null && id == mantener) return@filter true
        val suyas = porProfesional[id].orEmpty()
        val b = base[id]
        if (b?.horarioFlexible == true || suyas.isEmpty()) enSede(b?.sedeId, f)
        else suyas.any { it.dia == dia && enSede(it.sedeId, f) }
    }
}
