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
import pe.saniape.app.data.staff.MedicionCorporal
import pe.saniape.app.data.staff.MetricaMedicion
import pe.saniape.app.data.staff.PlanAlimentacion
import pe.saniape.app.data.staff.PuntoSerieHc
import pe.saniape.app.data.staff.htmlPlanAlimentario
import pe.saniape.app.data.staff.imcDeMedicion
import pe.saniape.app.data.staff.medicionDeJson
import pe.saniape.app.data.staff.normalizarComidas
import pe.saniape.app.data.staff.numeroHc
import pe.saniape.app.data.staff.ordenarMediciones
import pe.saniape.app.data.staff.ordenarPlanes
import pe.saniape.app.data.staff.planDeJson
import pe.saniape.app.data.staff.serieMetrica
import pe.saniape.app.data.staff.textoCambio

/** La clínica que indicó el plan (para la cabecera y la hoja / PDF). */
data class ClinicaPlanPortal(val nombre: String?, val logoUrl: String?, val colorPrincipal: String?)

/** Un plan alimentario VISIBLE del paciente, con quién y dónde se lo indicaron. */
data class PlanNutricionPortal(
    /** El plan (mismo modelo que la ficha del staff; comidas ya normalizadas). */
    val plan: PlanAlimentacion,
    val clinica: ClinicaPlanPortal?,
    /** "Lic. Paola Ríos" — quién lo indicó, o null. */
    val profesional: String?,
    /** El nombre del propio paciente, para la hoja impresa. */
    val pacienteNombre: String?,
) {
    val id: String get() = plan.id
}

/** Una medición del paciente + el IMC que calculó el servidor (con el peso y la talla de ESA toma). */
data class MedicionPortal(val medicion: MedicionCorporal, val imc: Double?)

/**
 * Respuesta de /api/paciente/mi-nutricion: planes (el más reciente primero:
 * `planes[0]` es el vigente) y mediciones (cronológicas: la última es "tu
 * última medición").
 */
data class MiNutricion(val planes: List<PlanNutricionPortal>, val mediciones: List<MedicionPortal>) {
    val tieneDatos: Boolean get() = planes.isNotEmpty() || mediciones.isNotEmpty()

    companion object {
        val VACIA = MiNutricion(emptyList(), emptyList())
    }
}

private fun JsonObject.txtNut(k: String): String? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

/**
 * Convierte el JSON de /api/paciente/mi-nutricion (contrato en la web:
 * docs/app-contrato-portal-paciente.md §4). Tolerante: los números pueden
 * llegar como texto (numeric de Postgres) o null; las comidas mal formadas o
 * vacías se descartan; un plan o medición sin id se salta en vez de romper la
 * lista. Null = el texto no es JSON válido.
 */
fun parsearMiNutricion(texto: String): MiNutricion? {
    val raiz = runCatching { Json.parseToJsonElement(texto).jsonObject }.getOrNull() ?: return null
    val planes = (raiz["planes"] as? JsonArray).orEmpty().mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val plan = runCatching { planDeJson(o) }.getOrNull() ?: return@mapNotNull null
        val cli = o["clinica"] as? JsonObject
        val pac = o["paciente"] as? JsonObject
        PlanNutricionPortal(
            plan = plan.copy(comidas = normalizarComidas(plan.comidas), calculo = null),
            clinica = cli?.let {
                ClinicaPlanPortal(it.txtNut("nombre"), it.txtNut("logo_url"), it.txtNut("color_principal"))
            },
            profesional = o.txtNut("profesional"),
            pacienteNombre = pac?.txtNut("nombre"),
        )
    }
    // §4 (2026-10-16): las mediciones de un NIÑO de una clínica con pediatría sin plan
    // alimentario (`enNutricion: false`) van en 👶 Crecimiento, no en ⚖️ Mi evolución; y
    // `mostrarEvolucion: false` en la raíz = ninguna es de nutrición. Ausentes (servidor
    // viejo) = mostrar todo, como antes.
    val mostrarEvolucion = (raiz["mostrarEvolucion"] as? JsonPrimitive)?.content != "false"
    val mediciones = (raiz["mediciones"] as? JsonArray).orEmpty().filter { mostrarEvolucion }.mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        if ((o["enNutricion"] as? JsonPrimitive)?.content == "false") return@mapNotNull null
        val m = runCatching { medicionDeJson(o) }.getOrNull() ?: return@mapNotNull null
        val imc = (o["imc"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()
            ?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
        MedicionPortal(m.copy(notas = null), imc ?: imcDeMedicion(m))
    }
    // El servidor ya los manda ordenados; se reordena igual (la caché o un servidor viejo).
    val porId = planes.associateBy { it.id }
    val planesOrden = ordenarPlanes(planes.map { it.plan }).mapNotNull { porId[it.id] }.distinctBy { it.id }
    val medPorId = mediciones.associateBy { it.medicion.id }
    val medOrden = ordenarMediciones(mediciones.map { it.medicion }).mapNotNull { medPorId[it.id] }.distinctBy { it.medicion.id }
    return MiNutricion(planesOrden, medOrden)
}

// ─── Evolución (presentación pura, testeable) ────────────────────────────────

/** Un recuadro de "tu última medición": "Peso · 72.5 kg · −1.5 kg desde 01/09/2026". */
data class DatoMedicionPortal(val etiqueta: String, val valor: String, val detalle: String? = null)

/** "2026-09-01" → "01/09/2026". */
private fun fechaCorta(iso: String): String {
    val p = iso.take(10).split("-")
    return if (p.size == 3 && p[0].length == 4) "${p[2]}/${p[1]}/${p[0]}" else iso
}

/**
 * Los recuadros de la última medición (gemelo de MiEvolucion de la web): peso
 * (con el cambio desde la primera toma con peso), IMC, cintura, % grasa y masa
 * muscular — solo los que se midieron.
 */
fun datosUltimaMedicion(mediciones: List<MedicionPortal>): List<DatoMedicionPortal> {
    val ultima = mediciones.lastOrNull() ?: return emptyList()
    val m = ultima.medicion
    val conPeso = mediciones.filter { it.medicion.peso != null }
    val primeraPeso = conPeso.firstOrNull()
    val cambio = if (m.peso != null && primeraPeso != null && primeraPeso !== ultima)
        textoCambio(m.peso - primeraPeso.medicion.peso!!, "kg") + " desde " + fechaCorta(primeraPeso.medicion.fecha)
    else null
    return listOfNotNull(
        m.peso?.let { DatoMedicionPortal("Peso", "${numeroHc(it)} kg", cambio) },
        ultima.imc?.let { DatoMedicionPortal("IMC", numeroHc(it)) },
        m.cintura?.let { DatoMedicionPortal("Cintura", "${numeroHc(it)} cm") },
        m.grasaPct?.let { DatoMedicionPortal("% grasa", "${numeroHc(it)} %") },
        m.masaMuscular?.let { DatoMedicionPortal("Masa muscular", "${numeroHc(it)} kg") },
    )
}

/** Peso o IMC en el tiempo (solo las tomas que lo tienen). El IMC, el del servidor. */
fun serieNutricionPortal(mediciones: List<MedicionPortal>, metrica: MetricaMedicion): List<PuntoSerieHc> =
    if (metrica == MetricaMedicion.IMC) mediciones.mapNotNull { mp -> mp.imc?.let { PuntoSerieHc(mp.medicion.fecha, it) } }
    else serieMetrica(mediciones.map { it.medicion }, metrica)

/** Las métricas del gráfico del portal (peso e IMC) que tienen al menos 2 puntos. */
fun metricasGraficoPortal(mediciones: List<MedicionPortal>): List<MetricaMedicion> =
    listOf(MetricaMedicion.PESO, MetricaMedicion.IMC).filter { serieNutricionPortal(mediciones, it).size >= 2 }

/**
 * La hoja A4 del plan (gemela de HojaPlanAlimentario.tsx), armada en la app: la
 * página web /portal/plan/<id> pide la sesión del navegador. Reusa la misma
 * hoja que imprime el nutricionista: todo el texto escapado, el color solo si
 * es #rrggbb y el logo solo si es https.
 */
fun htmlPlanPortal(p: PlanNutricionPortal): String = htmlPlanAlimentario(
    plan = p.plan,
    clinicaNombre = p.clinica?.nombre,
    clinicaDireccion = null,
    logoUrl = p.clinica?.logoUrl,
    colorPrincipal = p.clinica?.colorPrincipal,
    pacienteNombre = p.pacienteNombre,
    pacienteDocumento = null,
    profesional = p.profesional,
)

/**
 * Plan de alimentación y evolución del paciente (portal). Mismo camino seguro
 * que mis-ejercicios / recetas: API web con el Bearer del paciente; el servidor
 * decide qué fichas son suyas (identidad verificada, nunca RLS).
 *
 * Tolerante a propósito: un servidor viejo sin el endpoint (404) o una cuenta
 * sin ficha (403) → "sin datos" y la sección no aparece; 500/sin red → null (la
 * UI conserva lo que tenga). Un paciente de fisio no ve nada nuevo.
 *
 * Caché por USUARIO: el plan se consulta en la cocina o en el mercado, donde la
 * señal falla. Se pinta lo último conocido y el servidor lo corrige.
 */
object MiNutricionRepo {

    private val http = crearHttpClient()
    private const val RUTA = "/api/paciente/mi-nutricion"

    private fun clave(): String? =
        Supabase.client.auth.currentUserOrNull()?.id?.let { "portal:mi-nutricion:$it" }

    /** Lo último guardado para este usuario, o null si no hay nada. */
    fun guardada(): MiNutricion? {
        val k = clave() ?: return null
        val crudo = CacheLectura.leer(k) ?: return null
        return parsearMiNutricion(crudo)
    }

    /** Desde el servidor. Null = no se pudo saber (la UI conserva lo que tenga). */
    suspend fun cargar(): MiNutricion? {
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken ?: return null
        val resp = withTimeoutOrNull(12_000) {
            runCatching {
                http.get("${Supabase.SITE_URL}$RUTA") { header("Authorization", "Bearer $tk") }
            }.getOrNull()
        } ?: return null
        // 403 = cuenta sin ficha vinculada; 404 = servidor sin esta sección todavía.
        if (resp.status == HttpStatusCode.Forbidden || resp.status == HttpStatusCode.NotFound) {
            clave()?.let { CacheLectura.borrar(it) }
            return MiNutricion.VACIA
        }
        if (resp.status != HttpStatusCode.OK) return null
        val texto = runCatching { resp.bodyAsText() }.getOrNull() ?: return null
        val datos = parsearMiNutricion(texto) ?: return null
        clave()?.let { k ->
            if (datos.tieneDatos) CacheLectura.guardar(k, texto) else CacheLectura.borrar(k)
        }
        return datos
    }
}
