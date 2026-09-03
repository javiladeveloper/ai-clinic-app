package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.datetime.toLocalDateTime
import pe.saniape.app.data.Supabase

/**
 * Catálogos de COBRO de la clínica: métodos de pago configurables y campañas de
 * descuento vigentes. Espeja useMetodosPago y lib/campanias.ts de la web — la
 * lógica de precios es un PORT 1:1 (misma fuente de verdad conceptual): una
 * campaña es una capa de precio con vigencia, NUNCA toca el precio base.
 */

data class MetodoPago(val nombre: String, val icono: String?)

/** Los de siempre, como fallback si la clínica no configuró ninguno (igual que la web). */
private val METODOS_BASE = listOf(
    MetodoPago("Efectivo", "💵"), MetodoPago("Yape", "📱"), MetodoPago("Plin", "📲"),
    MetodoPago("BCP", "🏦"), MetodoPago("Transferencia", "💳"), MetodoPago("Otro", "💰"),
)

data class CampaniaApp(
    val id: String,
    val nombre: String,
    val tipo: String,          // paquete_fijo | precio_fijo | porcentaje | monto_fijo
    val alcance: String,       // todos | servicios | citas
    val aplicaA: String?,      // para alcance 'citas': ambas | Evaluación | Consulta
    val valor: Double?,        // % o monto del descuento
    val precio: Double?,       // precio cerrado (paquete_fijo / precio_fijo)
    val cantidad: Int?,        // sesiones del combo (paquete_fijo)
    val serviciosIds: List<String>,
) {
    val esPaquete: Boolean get() = tipo == "paquete_fijo"

    fun alcanzaProcedimiento(procedimientoId: String): Boolean =
        alcance == "todos" || procedimientoId in serviciosIds

    /** Precio FINAL al aplicar la campaña sobre un base (nunca negativo). */
    fun precioCon(base: Double): Double = when (tipo) {
        "paquete_fijo", "precio_fijo" -> maxOf(0.0, precio ?: base)
        "porcentaje" -> maxOf(0.0, redondear2(base * (1 - (valor ?: 0.0) / 100)))
        "monto_fijo" -> maxOf(0.0, redondear2(base - (valor ?: 0.0)))
        else -> base
    }

    /** Etiqueta corta para el chip ("10 sesiones a S/500", "20% de descuento"…). */
    fun etiqueta(): String = when (tipo) {
        "paquete_fijo" -> "${cantidad ?: 0} sesiones a S/${fmt(precio ?: 0.0)}"
        "precio_fijo" -> "S/${fmt(precio ?: 0.0)}"
        "porcentaje" -> "${fmt(valor ?: 0.0)}% de descuento"
        "monto_fijo" -> "S/${fmt(valor ?: 0.0)} de descuento"
        else -> nombre
    }

    /** ¿Puede descontar una cita suelta (Consulta/Evaluación)? Port de campaniaAplicaACita. */
    fun aplicaACita(tipoCita: String, servicioId: String?): Boolean {
        if (esPaquete) return false           // un combo de sesiones no descuenta la cita
        return when (alcance) {
            "citas" -> aplicaA == "ambas" || aplicaA == tipoCita
            // 'todos' solo si descuenta sobre el base; un precio_fijo plano es de servicios.
            "todos" -> tipo == "porcentaje" || tipo == "monto_fijo"
            "servicios" -> servicioId != null && servicioId in serviciosIds
            else -> false
        }
    }
}

private fun redondear2(n: Double): Double = kotlin.math.round(n * 100) / 100
private fun fmt(n: Double): String = if (n % 1.0 == 0.0) n.toInt().toString() else {
    val cent = kotlin.math.round(n * 100).toLong()
    "${cent / 100}.${(cent % 100).toString().padStart(2, '0')}"
}

object CatalogosCobroRepo {

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
    private fun JsonObject.dbl(k: String): Double? = str(k)?.toDoubleOrNull()

    // Cache en memoria por sesión de app: los catálogos cambian poco y cada modal
    // que cobra los pide. null = todavía no cargado (el fallo también reintenta).
    private var cacheMetodos: List<MetodoPago>? = null
    private var cacheCampanias: List<CampaniaApp>? = null

    /** Métodos ACTIVOS configurados por la clínica; los de siempre si no hay ninguno. */
    suspend fun metodosPago(): List<MetodoPago> {
        cacheMetodos?.let { return it }
        val lista = try {
            Supabase.client.postgrest["metodos_pago"]
                .select(Columns.list("nombre, icono, estado, orden")) {
                    order("orden", Order.ASCENDING)
                    order("nombre", Order.ASCENDING)
                }
                .decodeList<JsonObject>()
                .filter { it.str("estado") == "Activo" }
                .mapNotNull { o -> o.str("nombre")?.let { MetodoPago(it, o.str("icono")) } }
        } catch (_: Exception) { emptyList() }
        val res = lista.ifEmpty { METODOS_BASE }
        if (lista.isNotEmpty()) cacheMetodos = res
        return res
    }

    /** Solo los nombres — para los chips de cobrar. */
    suspend fun nombresMetodos(): List<String> = metodosPago().map { it.nombre }

    /** Campañas VIGENTES de la clínica (activas y dentro de fechas), con sus servicios. */
    suspend fun campaniasVigentes(): List<CampaniaApp> {
        cacheCampanias?.let { return it }
        val hoy = hoyIsoLocal()
        val res = try {
            Supabase.client.postgrest["campanias"]
                .select(Columns.raw("id, nombre, tipo, alcance, aplica_a, valor, precio, cantidad, fecha_inicio, fecha_fin, activo, campania_servicios(procedimiento_id)"))
                .decodeList<JsonObject>()
                .filter { o ->
                    val activo = (o["activo"] as? JsonPrimitive)?.content == "true"
                    val ini = o.str("fecha_inicio"); val fin = o.str("fecha_fin")
                    activo && (ini == null || ini <= hoy) && (fin == null || fin >= hoy)
                }
                .mapNotNull { o ->
                    val id = o.str("id") ?: return@mapNotNull null
                    CampaniaApp(
                        id = id,
                        nombre = o.str("nombre") ?: "Promoción",
                        tipo = o.str("tipo") ?: "",
                        alcance = o.str("alcance") ?: "todos",
                        aplicaA = o.str("aplica_a"),
                        valor = o.dbl("valor"), precio = o.dbl("precio"),
                        cantidad = o.str("cantidad")?.toIntOrNull(),
                        serviciosIds = (o["campania_servicios"] as? JsonArray)
                            ?.mapNotNull { (it as? JsonObject)?.str("procedimiento_id") } ?: emptyList(),
                    )
                }
        } catch (_: Exception) { emptyList() }
        cacheCampanias = res
        return res
    }

    /** Las vigentes que alcanzan a un servicio (combos primero — el gancho fuerte). */
    fun paraProcedimiento(camps: List<CampaniaApp>, procedimientoId: String): List<CampaniaApp> =
        camps.filter { it.alcance != "citas" && it.alcanzaProcedimiento(procedimientoId) }
            .sortedByDescending { it.esPaquete }

    /**
     * Precio de una cita (Consulta/Evaluación) con la MEJOR campaña vigente — UNA
     * sola, la que más conviene; nunca se acumulan (port de precioCitaConCampania).
     */
    fun precioCitaConCampania(
        camps: List<CampaniaApp>, tipoCita: String, precioBase: Double, servicioId: String? = null,
    ): Pair<Double, CampaniaApp?> {
        var mejor: CampaniaApp? = null
        var mejorPrecio = precioBase
        for (c in camps.filter { it.aplicaACita(tipoCita, servicioId) }) {
            val p = c.precioCon(precioBase)
            if (mejor == null || p < mejorPrecio) { mejor = c; mejorPrecio = p }
        }
        if (mejorPrecio >= precioBase) return precioBase to null
        return redondear2(mejorPrecio) to mejor
    }

    private fun hoyIsoLocal(): String {
        val d = kotlinx.datetime.Clock.System.now()
            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
        return "${d.year}-${d.monthNumber.toString().padStart(2, '0')}-${d.dayOfMonth.toString().padStart(2, '0')}"
    }
}
