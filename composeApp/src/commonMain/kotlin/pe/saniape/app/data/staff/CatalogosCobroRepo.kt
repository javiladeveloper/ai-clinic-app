package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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
    /** «Incluye la evaluación» (sin la columna en la base: false). */
    val incluyeEntrada: Boolean = false,
    /**
     * «Solo para estas especialidades» (18/11/2026, gemelo de la web). Vacío = toda la
     * clínica. [serviciosDeEspecialidades] = los servicios de esas especialidades
     * (lo calcula [CatalogosCobroRepo.campaniasVigentes] al leer).
     */
    val especialidadIds: List<String> = emptyList(),
    val serviciosDeEspecialidades: List<String> = emptyList(),
) {
    val esPaquete: Boolean get() = tipo == "paquete_fijo"
    val porEspecialidad: Boolean get() = especialidadIds.isNotEmpty()

    /** Con especialidades, el servicio tiene que ser de una de ellas (ante la duda, no). */
    fun alcanzaProcedimiento(procedimientoId: String): Boolean {
        if (porEspecialidad && procedimientoId !in serviciosDeEspecialidades) return false
        return alcance == "todos" || procedimientoId in serviciosIds
    }

    /** Precio FINAL al aplicar la campaña sobre un base (nunca negativo). */
    fun precioCon(base: Double): Double = when (tipo) {
        "paquete_fijo", "precio_fijo" -> maxOf(0.0, precio ?: base)
        "porcentaje" -> maxOf(0.0, redondear2(base * (1 - (valor ?: 0.0) / 100)))
        "monto_fijo" -> maxOf(0.0, redondear2(base - (valor ?: 0.0)))
        else -> base
    }

    /** Etiqueta corta para el chip ("10 sesiones a S/500", "20% de descuento"…), con la moneda de la sede. */
    fun etiqueta(moneda: String = monedaActiva()): String = when (tipo) {
        "paquete_fijo" -> "${cantidad ?: 0} sesiones a ${dineroCorto(precio ?: 0.0, moneda)}"
        "precio_fijo" -> dineroCorto(precio ?: 0.0, moneda)
        "porcentaje" -> "${fmt(valor ?: 0.0)}% de descuento"
        "monto_fijo" -> "${dineroCorto(valor ?: 0.0, moneda)} de descuento"
        else -> nombre
    }

    /** ¿Puede descontar una cita suelta (Consulta/Evaluación)? Port de campaniaAplicaACita. */
    fun aplicaACita(tipoCita: String, servicioId: String?, especialidadId: String? = null): Boolean {
        if (esPaquete) return false           // un combo de sesiones no descuenta la cita
        return when (alcance) {
            "citas" -> (aplicaA == "ambas" || aplicaA == tipoCita) && (
                // Por especialidad: la cita tiene que ser de una de ellas (por su
                // especialidad o por el servicio que la define). Sin saberlo, no.
                !porEspecialidad ||
                    (especialidadId != null && especialidadId in especialidadIds) ||
                    (servicioId != null && servicioId in serviciosDeEspecialidades)
                )
            // 'todos' = «Todos los servicios»: NO la consulta ni la evaluación (para eso
            // está «Consulta / evaluación»). GLAUCOMA 18/11/2026: un 30% de sus
            // servicios dejaba la consulta de S/ 200 en S/ 140.
            "todos" -> false
            "servicios" -> servicioId != null && alcanzaProcedimiento(servicioId)
            else -> false
        }
    }
}

private fun redondear2(n: Double): Double = kotlin.math.round(n * 100) / 100
private fun fmt(n: Double): String = montoCorto(n)

object CatalogosCobroRepo {

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
    private fun JsonObject.dbl(k: String): Double? = str(k)?.toDoubleOrNull()

    // Cache en memoria por sesión de app: los catálogos cambian poco y cada modal
    // que cobra los pide. null = todavía no cargado (el fallo también reintenta).
    private var cacheMetodos: List<MetodoPago>? = null
    private var cacheCampanias: List<CampaniaApp>? = null

    /** Olvida los métodos en caché (tras editarlos en Ajustes: el próximo cobro los relee). */
    fun limpiar() { cacheMetodos = null }

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
        // El día de LIMA (no el del teléfono): una campaña que vence hoy no debe
        // caerse a las 19:00 en un celular con la zona mal puesta.
        val hoy = hoyClinicaIso()
        val res = try {
            Supabase.client.postgrest["campanias"]
                // `*` (no las columnas por nombre): sin la migración de «incluye la
                // entrada» la lectura sigue funcionando igual.
                .select(Columns.raw("*, campania_servicios(procedimiento_id)"))
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
                        incluyeEntrada = o.str("incluye_entrada") == "true",
                        especialidadIds = (o["especialidad_ids"] as? JsonArray)
                            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s != "null" } } ?: emptyList(),
                    )
                }
                .let { conServiciosDeEspecialidades(it) }
        } catch (_: Exception) { emptyList() }
        cacheCampanias = res
        return res
    }

    /**
     * Las campañas limitadas a especialidades necesitan saber qué servicios son de
     * ellas (una lectura de procedimientos). Si falla, quedan sin servicios: ante
     * la duda no se descuenta (gemelo de cargarCampanias web).
     */
    private suspend fun conServiciosDeEspecialidades(camps: List<CampaniaApp>): List<CampaniaApp> {
        val esps = camps.flatMap { it.especialidadIds }.distinct()
        if (esps.isEmpty()) return camps
        val porEsp: Map<String, List<String>> = runCatching {
            Supabase.client.postgrest["procedimientos"].select(Columns.raw("id, especialidad_id")) {
                filter { isIn("especialidad_id", esps) }
            }.decodeList<JsonObject>()
                .mapNotNull { p -> p.str("especialidad_id")?.let { e -> p.str("id")?.let { e to it } } }
                .groupBy({ it.first }, { it.second })
        }.getOrDefault(emptyMap())
        return camps.map { c ->
            if (!c.porEspecialidad) c else c.copy(serviciosDeEspecialidades = c.especialidadIds.flatMap { porEsp[it].orEmpty() })
        }
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
        especialidadId: String? = null,
    ): Pair<Double, CampaniaApp?> {
        var mejor: CampaniaApp? = null
        var mejorPrecio = precioBase
        for (c in camps.filter { it.aplicaACita(tipoCita, servicioId, especialidadId) }) {
            val p = c.precioCon(precioBase)
            if (mejor == null || p < mejorPrecio) { mejor = c; mejorPrecio = p }
        }
        if (mejorPrecio >= precioBase) return precioBase to null
        return redondear2(mejorPrecio) to mejor
    }

    /**
     * Las campañas que se pueden APLICAR a una cita (Consulta/Evaluación), con el
     * precio que deja cada una, de la más barata a la más cara. Ya no se aplica
     * ninguna sola (GLAUCOMA 18/11/2026): quien agenda toca «Aplicar». Port de
     * campaniasParaCita (web).
     */
    fun campaniasParaCita(
        camps: List<CampaniaApp>, tipoCita: String, precioBase: Double, servicioId: String? = null,
        especialidadId: String? = null,
    ): List<Pair<CampaniaApp, Double>> = camps
        .filter { it.aplicaACita(tipoCita, servicioId, especialidadId) }
        .map { it to redondear2(it.precioCon(precioBase)) }
        .filter { it.second < precioBase }
        .sortedBy { it.second }
}
