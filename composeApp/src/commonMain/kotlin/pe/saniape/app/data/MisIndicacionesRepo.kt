package pe.saniape.app.data

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import pe.saniape.app.data.offline.CacheLectura
import pe.saniape.app.data.staff.ContenidoIndicacionesFuente
import pe.saniape.app.data.staff.htmlIndicaciones
import pe.saniape.app.data.staff.limpiarLineasIndicacion

/** La clínica que dejó las indicaciones (para la cabecera y la hoja / PDF). */
data class ClinicaIndicacionPortal(val nombre: String, val logoUrl: String?, val colorPrincipal: String?)

/** Una hoja de 📝 indicaciones para casa VISIBLE del paciente (contrato §5). */
data class IndicacionPortal(
    val id: String,
    val fecha: String,
    override val titulo: String,
    override val indicaciones: String?,
    override val evitar: String?,
    override val signosAlarma: String?,
    override val control: String?,
    val clinica: ClinicaIndicacionPortal?,
    /** Quién las indicó, o null. */
    val profesional: String?,
    /** El nombre del propio paciente, para la hoja impresa. */
    val pacienteNombre: String?,
) : ContenidoIndicacionesFuente

private fun JsonObject.txtPortal(k: String): String? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

/**
 * JSON de /api/paciente/mis-indicaciones (docs/app-contrato-portal-paciente.md
 * §5) → lista, la más reciente primero (`[0]` va abierta). Tolerante: una hoja
 * sin id se salta; los textos se limpian como "una por línea". Null = el texto
 * no es JSON válido.
 */
fun parsearMisIndicaciones(texto: String): List<IndicacionPortal>? {
    val raiz = runCatching { Json.parseToJsonElement(texto).jsonObject }.getOrNull() ?: return null
    val lista = (raiz["indicaciones"] as? JsonArray).orEmpty().mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val id = o.txtPortal("id") ?: return@mapNotNull null
        val cli = o["clinica"] as? JsonObject
        IndicacionPortal(
            id = id,
            fecha = o.txtPortal("fecha").orEmpty().take(10),
            titulo = o.txtPortal("titulo") ?: "Indicaciones",
            indicaciones = limpiarLineasIndicacion(o.txtPortal("indicaciones")),
            evitar = limpiarLineasIndicacion(o.txtPortal("evitar")),
            signosAlarma = limpiarLineasIndicacion(o.txtPortal("signos_alarma")),
            control = limpiarLineasIndicacion(o.txtPortal("control")),
            clinica = cli?.txtPortal("nombre")?.let { ClinicaIndicacionPortal(it, cli.txtPortal("logo_url"), cli.txtPortal("color_principal")) },
            profesional = o.txtPortal("profesional"),
            pacienteNombre = (o["paciente"] as? JsonObject)?.txtPortal("nombre"),
        )
    }
    // El servidor ya las manda ordenadas; se reordena igual (la caché o un servidor viejo). Estable a igual fecha.
    return lista.distinctBy { it.id }.sortedByDescending { it.fecha }
}

/**
 * La hoja A4 (gemela de HojaIndicaciones.tsx), armada en la app: la página web
 * /portal/indicaciones/<id> pide la sesión del navegador. La misma hoja que
 * imprime la clínica: todo escapado, el logo solo https y el color solo #rrggbb.
 */
fun htmlIndicacionPortal(i: IndicacionPortal): String = htmlIndicaciones(
    fecha = i.fecha, i = i,
    clinicaNombre = i.clinica?.nombre, clinicaDireccion = null,
    logoUrl = i.clinica?.logoUrl, colorPrincipal = i.clinica?.colorPrincipal,
    pacienteNombre = i.pacienteNombre, pacienteDocumento = null, profesional = i.profesional,
)

/**
 * 📝 Mis indicaciones para casa (portal). Mismo camino seguro que
 * [MiNutricionRepo]: API web con el Bearer del paciente; el servidor decide qué
 * fichas son suyas (identidad verificada, nunca RLS) y solo manda las visibles.
 *
 * Tolerante: un servidor viejo sin el endpoint (404) o una cuenta sin ficha
 * (403) → lista vacía y la sección no aparece; 500 / sin red → null (la UI
 * conserva lo que tenga). Caché por USUARIO: se consultan en casa, justo cuando
 * algo preocupa, y la señal puede fallar.
 */
object MisIndicacionesRepo {

    private val http = crearHttpClient()
    private const val RUTA = "/api/paciente/mis-indicaciones"

    private fun clave(): String? =
        Supabase.client.auth.currentUserOrNull()?.id?.let { "portal:mis-indicaciones:$it" }

    /** Lo último guardado para este usuario, o null si no hay nada. */
    fun guardada(): List<IndicacionPortal>? {
        val k = clave() ?: return null
        val crudo = CacheLectura.leer(k) ?: return null
        return parsearMisIndicaciones(crudo)
    }

    /** Desde el servidor. Null = no se pudo saber (la UI conserva lo que tenga). */
    suspend fun cargar(): List<IndicacionPortal>? {
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken ?: return null
        val resp = withTimeoutOrNull(12_000) {
            runCatching {
                http.get("${Supabase.SITE_URL}$RUTA") { header("Authorization", "Bearer $tk") }
            }.getOrNull()
        } ?: return null
        // 403 = cuenta sin ficha vinculada; 404 = servidor sin esta sección todavía.
        if (resp.status == HttpStatusCode.Forbidden || resp.status == HttpStatusCode.NotFound) {
            clave()?.let { CacheLectura.borrar(it) }
            return emptyList()
        }
        if (resp.status != HttpStatusCode.OK) return null
        val texto = runCatching { resp.bodyAsText() }.getOrNull() ?: return null
        val datos = parsearMisIndicaciones(texto) ?: return null
        clave()?.let { k -> if (datos.isNotEmpty()) CacheLectura.guardar(k, texto) else CacheLectura.borrar(k) }
        return datos
    }
}
