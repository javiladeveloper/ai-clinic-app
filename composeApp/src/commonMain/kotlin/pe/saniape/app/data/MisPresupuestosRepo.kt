package pe.saniape.app.data

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.offline.CacheLectura
import pe.saniape.app.data.staff.ItemPresupuesto
import pe.saniape.app.data.staff.Presupuesto
import pe.saniape.app.data.staff.htmlPresupuesto
import pe.saniape.app.data.staff.leerItemsPresupuesto
import pe.saniape.app.data.staff.normalizarMoneda

/** La clínica que envió el presupuesto (cabecera y hoja). */
data class ClinicaMiPresupuesto(val nombre: String, val logoUrl: String?, val colorPrincipal: String?, val direccion: String?)

/** Un presupuesto que su clínica le ENVIÓ (contrato del portal §16). Sin notas internas ni ids internos. */
data class MiPresupuesto(
    val id: String,
    val titulo: String,
    val moneda: String,
    val items: List<ItemPresupuesto>,
    val descuentoTipo: String?,
    val descuentoValor: Double,
    val subtotal: Double,
    val descuento: Double,
    val total: Double,
    val porSeparado: Double,
    val ahorro: Double,
    val notasPaciente: String?,
    val vigenciaHasta: String,
    /** El VISIBLE: Enviado | Aceptado | Rechazado | Vencido. */
    val estado: String,
    val enviadoAt: String?,
    val aceptadoAt: String?,
    val aceptadoCanal: String?,
    val puedeResponder: Boolean,
    /** Va de vuelta al aceptar: si la clínica lo cambió después → 409 CAMBIO. */
    val updatedAt: String?,
    val clinica: ClinicaMiPresupuesto?,
    val profesional: String?,
    val pacienteNombre: String?,
)

private fun JsonObject.txtPp(k: String): String? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

private fun JsonObject.numPp(k: String): Double? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull

/**
 * JSON de GET /api/paciente/mis-presupuestos → lista (el servidor manda primero
 * los que esperan respuesta). Tolerante: uno sin id se salta. null = no es JSON.
 */
fun parsearMisPresupuestos(texto: String): List<MiPresupuesto>? {
    val raiz = runCatching { Json.parseToJsonElement(texto).jsonObject }.getOrNull() ?: return null
    return (raiz["presupuestos"] as? JsonArray).orEmpty().mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val id = o.txtPp("id") ?: return@mapNotNull null
        val cli = o["clinica"] as? JsonObject
        MiPresupuesto(
            id = id,
            titulo = o.txtPp("titulo") ?: "Presupuesto",
            moneda = normalizarMoneda(o.txtPp("moneda")),
            items = leerItemsPresupuesto(o["items"]),
            descuentoTipo = o.txtPp("descuento_tipo")?.takeIf { it == "porcentaje" || it == "monto" },
            descuentoValor = o.numPp("descuento_valor") ?: 0.0,
            subtotal = o.numPp("subtotal") ?: 0.0,
            descuento = o.numPp("descuento") ?: 0.0,
            total = o.numPp("total") ?: 0.0,
            porSeparado = o.numPp("por_separado") ?: 0.0,
            ahorro = o.numPp("ahorro") ?: 0.0,
            notasPaciente = o.txtPp("notas_paciente"),
            vigenciaHasta = o.txtPp("vigencia_hasta")?.take(10).orEmpty(),
            estado = o.txtPp("estado") ?: "Enviado",
            enviadoAt = o.txtPp("enviado_at"),
            aceptadoAt = o.txtPp("aceptado_at"),
            aceptadoCanal = o.txtPp("aceptado_canal"),
            puedeResponder = (o["puede_responder"] as? JsonPrimitive)?.booleanOrNull == true,
            updatedAt = o.txtPp("updated_at"),
            clinica = cli?.txtPp("nombre")?.let { ClinicaMiPresupuesto(it, cli.txtPp("logo_url"), cli.txtPp("color_principal"), cli.txtPp("direccion")) },
            profesional = o.txtPp("profesional"),
            pacienteNombre = (o["paciente"] as? JsonObject)?.txtPp("nombre"),
        )
    }.distinctBy { it.id }
}

/** La hoja (PDF) del portal: la misma que imprime la clínica, armada en la app. */
fun htmlMiPresupuesto(p: MiPresupuesto): String = htmlPresupuesto(
    Presupuesto(
        id = p.id, pacienteId = "", sedeId = null, moneda = p.moneda, citaId = null, terapeutaId = null, especialidadId = null,
        titulo = p.titulo, items = p.items, descuentoTipo = p.descuentoTipo, descuentoValor = p.descuentoValor,
        notasPaciente = p.notasPaciente, notasInternas = null, vigenciaHasta = p.vigenciaHasta,
        estado = if (p.estado == "Vencido") "Enviado" else p.estado, enviadoAt = p.enviadoAt, aceptadoAt = p.aceptadoAt,
        aceptadoCanal = p.aceptadoCanal,
    ),
    clinicaNombre = p.clinica?.nombre, clinicaDireccion = p.clinica?.direccion, logoUrl = p.clinica?.logoUrl,
    colorPrincipal = p.clinica?.colorPrincipal, pacienteNombre = p.pacienteNombre, pacienteDocumento = null,
    profesional = p.profesional, zona = null,
)

/** Lo que pasó al responder: ok, o el mensaje y si hay que recargar (409 CAMBIO / NO_DISPONIBLE). */
data class RespuestaPresupuestoPortal(val ok: Boolean, val error: String? = null, val recargar: Boolean = false)

/**
 * 🧾 Mis presupuestos (portal). Mismo camino seguro que [MisIndicacionesRepo]:
 * API web con el Bearer del paciente; el servidor decide qué fichas son suyas
 * (identidad verificada, nunca RLS) y solo manda los enviados y visibles.
 * 403/404 → lista vacía (la sección no aparece); 500 / sin red → null (la UI
 * conserva lo que tenga). Caché por USUARIO.
 */
object MisPresupuestosRepo {

    private val http = crearHttpClient()
    private const val RUTA = "/api/paciente/mis-presupuestos"

    private fun clave(): String? = Supabase.client.auth.currentUserOrNull()?.id?.let { "portal:mis-presupuestos:$it" }

    fun guardados(): List<MiPresupuesto>? {
        val k = clave() ?: return null
        val crudo = CacheLectura.leer(k) ?: return null
        return parsearMisPresupuestos(crudo)
    }

    suspend fun cargar(): List<MiPresupuesto>? {
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken ?: return null
        val resp = withTimeoutOrNull(12_000) {
            runCatching { http.get("${Supabase.SITE_URL}$RUTA") { header("Authorization", "Bearer $tk") } }.getOrNull()
        } ?: return null
        if (resp.status == HttpStatusCode.Forbidden || resp.status == HttpStatusCode.NotFound) {
            clave()?.let { CacheLectura.borrar(it) }
            return emptyList()
        }
        if (resp.status != HttpStatusCode.OK) return null
        val texto = runCatching { resp.bodyAsText() }.getOrNull() ?: return null
        val datos = parsearMisPresupuestos(texto) ?: return null
        clave()?.let { k -> if (datos.isNotEmpty()) CacheLectura.guardar(k, texto) else CacheLectura.borrar(k) }
        return datos
    }

    /** "aceptar" (o "rechazar" + motivo), con el `updated_at` de lo que vio. */
    suspend fun responder(id: String, accion: String, updatedAt: String?, motivo: String? = null): RespuestaPresupuestoPortal {
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken
            ?: return RespuestaPresupuestoPortal(false, "Tu sesión expiró. Vuelve a entrar.")
        return try {
            val resp = http.post("${Supabase.SITE_URL}$RUTA") {
                header("Authorization", "Bearer $tk")
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("id", id); put("accion", accion)
                    if (updatedAt != null) put("updatedAt", updatedAt)
                    if (!motivo.isNullOrBlank()) put("motivo", motivo.trim().take(500))
                }.toString())
            }
            val o = runCatching { Json.parseToJsonElement(resp.bodyAsText()).jsonObject }.getOrNull()
            if (resp.status.value in 200..299) return RespuestaPresupuestoPortal(true)
            val cod = (o?.get("codigo") as? JsonPrimitive)?.contentOrNull
            val msg = (o?.get("error") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            RespuestaPresupuestoPortal(
                false,
                msg ?: when (cod) {
                    "CAMBIO" -> "El presupuesto cambió, revísalo de nuevo"
                    "NO_DISPONIBLE" -> "Este presupuesto ya no está disponible. Pide a tu clínica uno actualizado."
                    else -> "No se pudo responder. Inténtalo de nuevo."
                },
                recargar = cod == "CAMBIO" || cod == "NO_DISPONIBLE" || cod == "NO_ENCONTRADO",
            )
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            RespuestaPresupuestoPortal(false, "Sin conexión. Revisa tu internet.")
        }
    }
}
