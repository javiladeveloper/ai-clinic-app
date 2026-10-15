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
import pe.saniape.app.data.staff.FASES_ORTO

/** Una fase de la barra del portal: "hecha" · "actual" · "pendiente". */
data class FasePortalOrto(val id: String, val nombre: String, val estado: String)

/** El próximo control: el agendado ("agenda", con hora) o el estimado por el último control ("estimado"). */
data class ProximoControlPortal(val fecha: String, val hora: String?, val origen: String) {
    val agendado: Boolean get() = origen == "agenda"
}

/** Un plan de ortodoncia del paciente (sin notas, observaciones, higiene, clase ni arcos: no vienen). */
data class OrtodonciaPortal(
    val id: String,
    val aparatologia: String,
    val arcadas: String?,
    val fechaInstalacion: String?,
    val retirado: Boolean,
    val fase: FasePortalOrto?,
    val fases: List<FasePortalOrto>,
    val meses: Int?,
    val duracionEstimadaMeses: Int?,
    val progresoPct: Int?,
    val progresoTexto: String,
    val proximoControl: ProximoControlPortal?,
    val ultimoControl: String?,
    /** Cómo usar los elásticos del último control (frases listas). */
    val elasticos: List<String>,
    /** Cuidados según la aparatología (o la retención). */
    val consejos: List<String>,
    val retencion: String?,
    val clinicaNombre: String?,
    val clinicaColor: String?,
    val profesional: String?,
)

/** Respuesta de /api/paciente/mi-ortodoncia: el plan más reciente primero. */
data class MiOrtodoncia(val planes: List<OrtodonciaPortal>) {
    val tieneDatos: Boolean get() = planes.isNotEmpty()

    companion object {
        val VACIA = MiOrtodoncia(emptyList())
    }
}

private fun JsonObject.txtOrtoP(k: String): String? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
private fun JsonObject.intOrtoP(k: String): Int? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull()?.takeIf { it.isFinite() }?.toInt()
private fun textosOrtoP(v: Any?): List<String> = (v as? JsonArray).orEmpty().mapNotNull { x ->
    (x as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
}

/** Las 5 fases a partir de la actual (servidor viejo sin `fases`). */
private fun fasesDesde(actual: String?): List<FasePortalOrto> {
    val i = FASES_ORTO.indexOfFirst { it.id == actual }.coerceAtLeast(0)
    return FASES_ORTO.mapIndexed { k, f -> FasePortalOrto(f.id, f.nombre, if (k < i) "hecha" else if (k == i) "actual" else "pendiente") }
}

/**
 * Convierte el JSON de /api/paciente/mi-ortodoncia (contrato en la web:
 * docs/app-contrato-portal-paciente.md §10). Tolerante: planes sin id se saltan;
 * sin `fases` se arman desde la fase actual; estados desconocidos → pendiente;
 * `proximo_control` sin fecha → null. Null = no es JSON válido.
 */
fun parsearMiOrtodoncia(texto: String): MiOrtodoncia? {
    val raiz = runCatching { Json.parseToJsonElement(texto).jsonObject }.getOrNull() ?: return null
    val planes = (raiz["planes"] as? JsonArray).orEmpty().mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val id = o.txtOrtoP("id") ?: return@mapNotNull null
        val apObj = o["aparatologia"] as? JsonObject
        val aparatologia = apObj?.txtOrtoP("nombre") ?: o.txtOrtoP("aparatologia") ?: "Ortodoncia"
        val faseObj = o["fase"] as? JsonObject
        val fase = faseObj?.txtOrtoP("id")?.let { fid -> FasePortalOrto(fid, faseObj.txtOrtoP("nombre") ?: fid, "actual") }
        val fases = (o["fases"] as? JsonArray).orEmpty().mapNotNull { f ->
            val fo = f as? JsonObject ?: return@mapNotNull null
            val fid = fo.txtOrtoP("id") ?: return@mapNotNull null
            val est = fo.txtOrtoP("estado")?.takeIf { it == "hecha" || it == "actual" || it == "pendiente" } ?: "pendiente"
            FasePortalOrto(fid, fo.txtOrtoP("nombre") ?: fid, est)
        }.ifEmpty { fasesDesde(fase?.id) }
        val prox = (o["proximo_control"] as? JsonObject)?.let { p ->
            p.txtOrtoP("fecha")?.take(10)?.let { f -> ProximoControlPortal(f, p.txtOrtoP("hora")?.take(5), p.txtOrtoP("origen") ?: "estimado") }
        }
        val cli = o["clinica"] as? JsonObject
        OrtodonciaPortal(
            id = id,
            aparatologia = aparatologia,
            arcadas = o.txtOrtoP("arcadas"),
            fechaInstalacion = o.txtOrtoP("fecha_instalacion")?.take(10),
            retirado = (o["retirado"] as? JsonPrimitive)?.content == "true",
            fase = fase,
            fases = fases,
            meses = o.intOrtoP("meses"),
            duracionEstimadaMeses = o.intOrtoP("duracion_estimada_meses"),
            progresoPct = o.intOrtoP("progreso_pct")?.coerceIn(0, 100),
            progresoTexto = o.txtOrtoP("progreso_texto") ?: "",
            proximoControl = prox,
            ultimoControl = o.txtOrtoP("ultimo_control")?.take(10),
            elasticos = textosOrtoP(o["elasticos"]),
            consejos = textosOrtoP(o["consejos"]),
            retencion = o.txtOrtoP("retencion"),
            clinicaNombre = cli?.txtOrtoP("nombre"),
            clinicaColor = cli?.txtOrtoP("color_principal")?.takeIf { Regex("^#[0-9a-fA-F]{6}$").matches(it) },
            profesional = o.txtOrtoP("profesional"),
        )
    }
    return MiOrtodoncia(planes.distinctBy { it.id })
}

/**
 * Qué se le dice del próximo control (gemelo del portal web): agendado con su
 * hora; estimado = "aproximado, confírmalo con tu clínica"; sin fecha (ya pasó)
 * = "Agenda tu próximo control". En retención no se muestra (null).
 */
fun textoProximoControlPortal(p: OrtodonciaPortal, fecha: (String) -> String): String? {
    if (p.retirado) return null
    val px = p.proximoControl ?: return "Agenda tu próximo control con tu clínica."
    return if (px.agendado) "📅 ${fecha(px.fecha)}${px.hora?.let { " · $it" }.orEmpty()}"
    else "≈ ${fecha(px.fecha)} (aproximado, confírmalo con tu clínica)"
}

/**
 * 😁 Mi ortodoncia (portal). Mismo camino seguro que mi-nutricion: API web con
 * el Bearer del paciente; el servidor decide qué fichas son suyas (identidad
 * verificada, nunca RLS). 403 (cuenta sin ficha) / 404 (servidor sin la sección)
 * → "sin datos" y la sección no aparece; 500/sin red → null (la UI conserva lo
 * que tenga). Caché por USUARIO.
 */
object MiOrtodonciaRepo {

    private val http = crearHttpClient()
    private const val RUTA = "/api/paciente/mi-ortodoncia"

    private fun clave(): String? =
        Supabase.client.auth.currentUserOrNull()?.id?.let { "portal:mi-ortodoncia:$it" }

    /** Lo último guardado para este usuario, o null si no hay nada. */
    fun guardada(): MiOrtodoncia? {
        val k = clave() ?: return null
        val crudo = CacheLectura.leer(k) ?: return null
        return parsearMiOrtodoncia(crudo)
    }

    /** Desde el servidor. Null = no se pudo saber (la UI conserva lo que tenga). */
    suspend fun cargar(): MiOrtodoncia? {
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken ?: return null
        val resp = withTimeoutOrNull(12_000) {
            runCatching {
                http.get("${Supabase.SITE_URL}$RUTA") { header("Authorization", "Bearer $tk") }
            }.getOrNull()
        } ?: return null
        if (resp.status == HttpStatusCode.Forbidden || resp.status == HttpStatusCode.NotFound) {
            clave()?.let { CacheLectura.borrar(it) }
            return MiOrtodoncia.VACIA
        }
        if (resp.status != HttpStatusCode.OK) return null
        val texto = runCatching { resp.bodyAsText() }.getOrNull() ?: return null
        val datos = parsearMiOrtodoncia(texto) ?: return null
        clave()?.let { k -> if (datos.tieneDatos) CacheLectura.guardar(k, texto) else CacheLectura.borrar(k) }
        return datos
    }
}
