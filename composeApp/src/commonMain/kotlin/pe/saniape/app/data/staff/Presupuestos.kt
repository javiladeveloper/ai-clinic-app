package pe.saniape.app.data.staff

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// 🧾 PRESUPUESTO / PAQUETE DE SERVICIOS — lógica PURA, gemela de lib/presupuestos.ts
// (y de lo que usa de lib/precio-acordado.ts). Contrato: docs/app-contrato-presupuestos.md.
//
// La lógica de dinero NO se calcula distinto que en la web: totales, descuento
// (en monto o %), reparto por mayor resto en unidades mínimas, paquetes del
// servicio (tarifarios o precio_paquete × 10), sueltas ↔ paquete, textos de
// WhatsApp y del recordatorio. Los tests (PresupuestosTest) copian los casos de
// lib/__tests__/presupuestos.test.ts, incluido el Láser CO2 (380 la sesión,
// paquete de 3 = 999 exacto).
//
// El presupuesto DENTAL (PresupuestoOdontograma) es otro documento y no cambia.

// ─────────────────────────────────────────────────────────────────────────────
// Tipos
// ─────────────────────────────────────────────────────────────────────────────

/** Modo del ítem (lo dicta el catálogo): `simple` | `sesiones` | `unidades`. */
object ModoItemPresu {
    const val SIMPLE = "simple"
    const val SESIONES = "sesiones"
    const val UNIDADES = "unidades"
    fun valido(v: String?) = v == SIMPLE || v == SESIONES || v == UNIDADES
}

/** Cómo se cobra un ítem POR SESIONES: sueltas (N × precio) o paquete (el precio del paquete). */
object CobroPresu {
    const val SUELTAS = "sueltas"
    const val PAQUETE = "paquete"
}

/** Una línea del presupuesto (así se guarda en `presupuestos.items`). */
data class ItemPresupuesto(
    /** Identificador estable dentro del presupuesto (va a tratamientos.presupuesto_linea). */
    val clave: String,
    /** Servicio del catálogo; null = ítem libre. */
    val procedimientoId: String?,
    val nombre: String,
    /** "Tercio superior"… (sale al paciente). */
    val detalle: String?,
    val modo: String,
    /** Modo unidades: "unidades", "ml"… */
    val unidadLabel: String?,
    /** Unidades, sesiones (sueltas o las del paquete) o veces. */
    val cantidad: Int,
    /** El precio NEGOCIADO; con cobro 'paquete', el del PAQUETE ENTERO. */
    val precioUnitario: Double,
    /** Lista de lo mismo (la pone el servidor desde el catálogo; null = libre / personalizado). */
    val precioLista: Double?,
    /** Solo modo 'sesiones': 'sueltas' | 'paquete' (sin dato = sueltas). */
    val cobro: String? = null,
    /** Solo modo 'sesiones': lista de UNA sesión (el "por sueltas" del paquete). */
    val precioSesion: Double? = null,
)

/** El paciente de una fila de pendientes (`?pendientes=1`). */
data class PacientePresupuesto(
    val id: String,
    val nombre: String,
    val telefono: String?,
    val apoderadoTelefono: String?,
    val apoderadoRecibeAvisos: Boolean,
    val sedeId: String?,
)

/** La fila de `presupuestos` tal como la devuelve la API (snake_case → camelCase). */
data class Presupuesto(
    val id: String,
    val pacienteId: String,
    val sedeId: String?,
    val moneda: String,
    val citaId: String?,
    val terapeutaId: String?,
    val especialidadId: String?,
    val titulo: String,
    val items: List<ItemPresupuesto>,
    val descuentoTipo: String?,
    val descuentoValor: Double,
    val notasPaciente: String?,
    val notasInternas: String?,
    val vigenciaHasta: String,
    val estado: String,
    val enviadoAt: String? = null,
    val recordadoAt: String? = null,
    val aceptadoAt: String? = null,
    val aceptadoPor: String? = null,
    val aceptadoPorNombre: String? = null,
    val aceptadoCanal: String? = null,
    val rechazadoAt: String? = null,
    val motivoRechazo: String? = null,
    val convertidoAt: String? = null,
    val visiblePaciente: Boolean = true,
    val creadoPor: String? = null,
    val createdAt: String = "",
    val updatedAt: String? = null,
    /** Solo en la lista de pendientes. */
    val paciente: PacientePresupuesto? = null,
)

/** Un tratamiento creado desde un presupuesto (la lista del GET ?pacienteId=). */
data class TratDePresupuesto(
    val id: String,
    val presupuestoId: String,
    val presupuestoLinea: String?,
    val estado: String?,
    val modalidad: String?,
    val precioAcordado: Double?,
    val procedimientoNombre: String?,
)

data class PlantillaPresupuesto(
    val id: String,
    val nombre: String,
    val especialidadId: String?,
    val titulo: String,
    val items: List<ItemPresupuesto>,
    val descuentoTipo: String?,
    val descuentoValor: Double,
    val notasPaciente: String?,
    val vigenciaDias: Int,
)

/** Gemelos de los CHECK de la migración 20261030100000_presupuestos.sql. */
object LimitesPresupuesto {
    const val TITULO = 120
    const val ITEMS = 30
    const val NOMBRE_ITEM = 120
    const val DETALLE = 200
    const val UNIDAD = 30
    const val CANTIDAD_MAX = 100_000
    const val NOTAS = 2000
    const val MOTIVO = 500
    const val VIGENCIA_MAX_DIAS = 365
    const val NOMBRE_PLANTILLA = 80
}

const val VIGENCIA_DIAS_DEFECTO = 15
val CHIPS_VIGENCIA = listOf(7, 15, 30)
val CHIPS_DESCUENTO_PCT = listOf(5, 10, 15, 20)
val TITULOS_SUGERIDOS = listOf("Plan de tratamiento", "Paquete de sesiones", "Plan facial", "Plan corporal", "Plan integral")
val NOTAS_SUGERIDAS = listOf(
    "El precio incluye todas las sesiones del paquete.",
    "Puedes pagarlo en partes: un adelanto para empezar y el resto en las siguientes sesiones.",
    "Los resultados varían según cada paciente.",
    "Incluye controles sin costo durante el tratamiento.",
)
val MOTIVOS_RECHAZO = listOf("Por el precio", "Lo pensará más adelante", "Eligió otra clínica", "No es el momento")

/** Tope de un precio (gemelo de MAX_PRECIO de lib/precio-acordado). */
const val MAX_PRECIO_PRESU = 100_000_000.0

// ─────────────────────────────────────────────────────────────────────────────
// Dinero (siempre en unidades mínimas de la moneda: céntimos; CLP/COP enteros)
// ─────────────────────────────────────────────────────────────────────────────

/** Math.round de JS (la mitad sube hacia +∞): kotlin.math.round redondea a par. */
internal fun redondeoJs(x: Double): Double = floor(x + 0.5)

private fun factorMoneda(moneda: String?): Double {
    var f = 1.0
    repeat(decimalesMoneda(moneda)) { f *= 10 }
    return f
}

/** Redondeo al céntimo (o a la unidad en CLP/COP). Gemelo de `redondearPrecio`. */
fun redondearPrecio(n: Double, moneda: String?): Double {
    val f = factorMoneda(moneda)
    return redondeoJs(n * f) / f
}

private fun aMinimas(n: Double, moneda: String?): Long =
    redondeoJs((if (n.isFinite()) n else 0.0) * factorMoneda(moneda)).toLong()

private fun deMinimas(n: Long, moneda: String?): Double = n / factorMoneda(moneda)

/** "1,000" (toLocaleString('es-PE') de un entero). */
fun milesPresu(n: Int): String {
    val s = abs(n).toString().reversed().chunked(3).joinToString(",").reversed()
    return if (n < 0) "-$s" else s
}

/** ¿Es un paquete (modo sesiones con cobro 'paquete')? */
fun esPaquete(it: ItemPresupuesto): Boolean = it.cobro == CobroPresu.PAQUETE && it.modo == ModoItemPresu.SESIONES

/** Lo que vale la línea: cantidad × precio; un PAQUETE vale su precio (por sus N sesiones). */
fun subtotalItem(it: ItemPresupuesto, moneda: String?): Double {
    val precio = max(0.0, it.precioUnitario.takeIf { p -> p.isFinite() } ?: 0.0)
    if (esPaquete(it)) return redondearPrecio(precio, moneda)
    return redondearPrecio(max(0, it.cantidad) * precio, moneda)
}

/**
 * "Por separado" (para el ahorro): todo a lista de UNA sesión / unidad. Un
 * paquete se compara con sus N sueltas (si se conoce el precio de una sesión);
 * si no, con su lista. Sin lista, el negociado (ahorro 0).
 */
fun subtotalListaItem(it: ItemPresupuesto, moneda: String?): Double {
    val cant = max(0, it.cantidad)
    if (esPaquete(it)) {
        val ses = it.precioSesion
        if (ses != null) return redondearPrecio(cant * ses, moneda)
        return redondearPrecio(max(0.0, it.precioLista ?: it.precioUnitario), moneda)
    }
    val unit = it.precioLista ?: it.precioUnitario
    return redondearPrecio(cant * max(0.0, unit), moneda)
}

/** La lista de LO MISMO que se cobra (paquete: su precio; si no, cantidad × lista). */
fun listaDeLinea(it: ItemPresupuesto, moneda: String?): Double? {
    val l = it.precioLista ?: return null
    return if (esPaquete(it)) redondearPrecio(l, moneda) else redondearPrecio(it.cantidad * l, moneda)
}

/** El descuento en dinero: % sobre el subtotal (redondeado); monto, a lo más el subtotal. */
fun montoDescuento(subtotal: Double, tipo: String?, valor: Double?, moneda: String?): Double {
    val v = max(0.0, valor?.takeIf { it.isFinite() } ?: 0.0)
    if (tipo == null || v <= 0 || subtotal <= 0) return 0.0
    if (tipo == "porcentaje") return redondearPrecio(subtotal * min(v, 100.0) / 100, moneda)
    if (tipo == "monto") return redondearPrecio(min(v, subtotal), moneda)
    return 0.0
}

data class TotalesPresupuesto(
    val subtotal: Double,
    val descuento: Double,
    val total: Double,
    /** Σ cantidad × lista ("por separado"). */
    val porSeparado: Double,
    /** porSeparado − total (0 si no hay ahorro). */
    val ahorro: Double,
)

fun totalesPresupuesto(items: List<ItemPresupuesto>, tipo: String?, valor: Double?, moneda: String?): TotalesPresupuesto {
    val subMin = items.sumOf { aMinimas(subtotalItem(it, moneda), moneda) }
    val subtotal = deMinimas(subMin, moneda)
    val desc = montoDescuento(subtotal, tipo, valor, moneda)
    val total = deMinimas(subMin - aMinimas(desc, moneda), moneda)
    val sepMin = items.sumOf { aMinimas(subtotalListaItem(it, moneda), moneda) }
    val porSeparado = deMinimas(sepMin, moneda)
    return TotalesPresupuesto(subtotal, desc, total, porSeparado, max(0.0, deMinimas(sepMin - aMinimas(total, moneda), moneda)))
}

fun totalesDe(p: Presupuesto): TotalesPresupuesto = totalesPresupuesto(p.items, p.descuentoTipo, p.descuentoValor, p.moneda)

/** "S/ 1,800.00 · por separado S/ 2,100.00" — sin ahorro, solo el total. */
fun textoTotalConAhorro(t: TotalesPresupuesto, moneda: String?): String {
    val total = formatearDinero(t.total, moneda)
    return if (t.ahorro > 0) "$total · por separado ${formatearDinero(t.porSeparado, moneda)}" else total
}

/**
 * Reparte el descuento entre las líneas en proporción a su subtotal (mayor resto,
 * en unidades mínimas). Devuelve lo que vale CADA línea tras el descuento:
 * Σ = Σ subtotales − descuento, exacto. Ninguna negativa. Empates: la línea más
 * grande y después la primera.
 */
fun prorratearDescuento(subtotales: List<Double>, descuento: Double, moneda: String?): List<Double> {
    val subs = subtotales.map { max(0L, aMinimas(it, moneda)) }
    val s = subs.sum()
    val d = min(max(0L, aMinimas(descuento, moneda)), s)
    if (s == 0L || d == 0L) return subs.map { deMinimas(it, moneda) }
    val exactas = subs.map { (d.toDouble() * it) / s }
    val pisos = exactas.map { floor(it).toLong() }
    var resto = d - pisos.sum()
    val orden = subs.indices.map { i -> Triple(i, exactas[i] - pisos[i], subs[i]) }
        .sortedWith(compareByDescending<Triple<Int, Double, Long>> { it.second }.thenByDescending { it.third }.thenBy { it.first })
    val partes = pisos.toMutableList()
    for (o in orden) {
        if (resto <= 0) break
        if (o.second > 0) { partes[o.first] += 1; resto-- }
    }
    return subs.indices.map { deMinimas(subs[it] - partes[it], moneda) }
}

// ─────────────────────────────────────────────────────────────────────────────
// Lo escrito → precio, y precio negociado frente a la lista (lib/precio-acordado)
// ─────────────────────────────────────────────────────────────────────────────

/** Lectura de un precio escrito: ok + valor (null = vacío), o el error. */
data class LecturaPrecio(val ok: Boolean, val valor: Double? = null, val error: String? = null)

private val RE_NUMERO = Regex("^-?\\d*\\.?\\d*$")

/** Gemelo de `leerPrecio`: "150", "150.5", "150,50", " 1 200 ". Vacío = ok sin valor. */
fun leerPrecio(entrada: String?, moneda: String?): LecturaPrecio {
    if (entrada == null) return LecturaPrecio(true)
    val t = entrada.trim().replace(Regex("\\s+"), "").replaceFirst(',', '.')
    if (t.isEmpty()) return LecturaPrecio(true)
    if (!RE_NUMERO.matches(t) || t == "." || t == "-") return LecturaPrecio(false, error = "Escribe solo números (ej. 150 o 150.50)")
    val n = t.toDoubleOrNull() ?: return LecturaPrecio(false, error = "Escribe solo números (ej. 150 o 150.50)")
    if (!n.isFinite()) return LecturaPrecio(false, error = "Escribe solo números (ej. 150 o 150.50)")
    if (n < 0) return LecturaPrecio(false, error = "El precio no puede ser negativo")
    if (n > MAX_PRECIO_PRESU) return LecturaPrecio(false, error = "El precio es demasiado alto: revisa los ceros")
    val dec = decimalesMoneda(moneda)
    if (abs(redondearPrecio(n, moneda) - n) > 1e-9) {
        return LecturaPrecio(false, error = if (dec == 0) "Esta moneda no usa decimales" else "Usa a lo más $dec decimales")
    }
    return LecturaPrecio(true, n)
}

/** El número en el campo ("15", "15.5", "999"): sin ".0" de más. */
fun textoPrecioCampo(n: Double?): String {
    if (n == null || !n.isFinite() || n == 0.0) return ""
    val r = redondeoJs(n * 100) / 100
    val entero = r.toLong()
    if (r == entero.toDouble()) return entero.toString()
    val cent = redondeoJs(abs(r - entero) * 100).toLong()
    val s = "$entero.${cent.toString().padStart(2, '0')}"
    return s.trimEnd('0')
}

/** Texto del campo al apretar "↺ Precio de lista" (con los decimales de la moneda). */
fun textoLista(lista: Double, moneda: String?): String {
    val dec = decimalesMoneda(moneda)
    val r = redondearPrecio(lista, moneda)
    if (dec == 0) return r.toLong().toString()
    val f = factorMoneda(moneda)
    val unidades = redondeoJs(r * f).toLong()
    val ent = unidades / f.toLong()
    val frac = abs(unidades % f.toLong())
    return "$ent.${frac.toString().padStart(dec, '0')}"
}

data class ComparacionPrecio(val distinto: Boolean, val descuento: Double, val recargo: Double, val porcentaje: Int)

/** Lo acordado frente a la lista. Sin lista (o sin acordado) no hay nada que comparar. */
fun compararConLista(acordado: Double?, lista: Double?): ComparacionPrecio {
    val nada = ComparacionPrecio(false, 0.0, 0.0, 0)
    if (acordado == null || lista == null || !acordado.isFinite() || !lista.isFinite()) return nada
    val dif = redondeoJs((lista - acordado) * 100) / 100
    if (abs(dif) < 0.005) return nada
    val pct = if (lista > 0) redondeoJs((abs(dif) / lista) * 100).toInt() else 0
    return if (dif > 0) ComparacionPrecio(true, dif, 0.0, pct) else ComparacionPrecio(true, 0.0, -dif, pct)
}

/**
 * "S/ 950.00 · lista S/ 999.00 (−5 %)"; por ENCIMA de la lista no es un descuento:
 * "S/ 1,000.00 · sobre lista S/ 999.00". Gratis: "Sin costo · lista S/ 200.00".
 * null = es el precio de lista (o no hay lista).
 */
fun textoPrecioNegociado(acordado: Double?, lista: Double?, moneda: String? = "PEN"): String? {
    val c = compararConLista(acordado, lista)
    if (!c.distinto || acordado == null || lista == null) return null
    val izq = if (acordado == 0.0) "Sin costo" else formatearDinero(acordado, moneda)
    val pct = if (c.porcentaje > 0 && acordado > 0) " (${if (c.descuento > 0) "−" else "+"}${c.porcentaje} %)" else ""
    return "$izq · ${if (c.recargo > 0) "sobre lista" else "lista"} ${formatearDinero(lista, moneda)}$pct"
}

// ─────────────────────────────────────────────────────────────────────────────
// Estado, vigencia y días
// ─────────────────────────────────────────────────────────────────────────────

/** Días enteros entre dos fechas YYYY-MM-DD (b − a). */
fun diasEntre(a: String, b: String): Int = runCatching {
    val da = LocalDate.parse(a.take(10))
    val db = LocalDate.parse(b.take(10))
    (db.toEpochDays() - da.toEpochDays()).toInt()
}.getOrDefault(0)

/** Estado que se MUESTRA: un Enviado con la vigencia pasada está Vencido. */
fun estadoVisiblePresupuesto(estado: String, vigenciaHasta: String?, hoy: String): String {
    if (estado == "Aceptado" || estado == "Rechazado" || estado == "Vencido" || estado == "Borrador") return estado
    if (!vigenciaHasta.isNullOrBlank() && vigenciaHasta.take(10) < hoy.take(10)) return "Vencido"
    return "Enviado"
}

/** ¿Lo puede aceptar el paciente (portal) o el staff (en persona) ahora? */
fun aceptable(p: Presupuesto, hoy: String, canal: String): Boolean {
    val v = estadoVisiblePresupuesto(p.estado, p.vigenciaHasta, hoy)
    if (canal == "portal") return v == "Enviado"
    return p.estado == "Borrador" || p.estado == "Enviado"
}

/** ¿Falta convertirlo en tratamientos? */
fun faltaConvertir(p: Presupuesto): Boolean = p.estado == "Aceptado" && p.convertidoAt.isNullOrBlank()

/** Días desde el envío (hoy en la zona de la sede). */
fun diasDesdeEnvio(enviadoDia: String?, hoy: String): Int? {
    if (enviadoDia.isNullOrBlank()) return null
    return max(0, diasEntre(enviadoDia.take(10), hoy))
}

/** "hoy" · "ayer" · "hace 5 días". */
fun textoDiasEnvio(n: Int?): String = when (n) {
    null -> ""
    0 -> "hoy"
    1 -> "ayer"
    else -> "hace $n días"
}

/** El día (YYYY-MM-DD) de un timestamp ISO en la zona [zona] (hoyEn(zona, new Date(iso)) de la web). */
fun diaEnZona(iso: String?, zona: String?): String? {
    if (iso.isNullOrBlank()) return null
    return runCatching { hoyEnIso(zona, Instant.parse(iso)) }.getOrElse {
        runCatching { hoyEnIso(zona, Instant.parse(iso.replace(" ", "T"))) }.getOrNull() ?: iso.take(10)
    }
}

/** "16/10 a las 15:30" en la zona de la sede (fechaHoraAceptacion de la web). */
fun fechaHoraAceptacion(iso: String, zona: String?): String = runCatching {
    val t = Instant.parse(iso.replace(" ", "T")).toLocalDateTime(zonaDe(zona))
    "${t.dayOfMonth.toString().padStart(2, '0')}/${t.monthNumber.toString().padStart(2, '0')} a las " +
        "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
}.getOrElse { iso.take(10) }

// ─────────────────────────────────────────────────────────────────────────────
// Ítems: validar lo que se manda (gemelo de normalizarItems), descuento y vigencia
// ─────────────────────────────────────────────────────────────────────────────

private val RE_CLAVE = Regex("^[A-Za-z0-9_-]{1,40}$")
/** Clave corta y estable para una línea nueva ("l_" + 10 caracteres). */
fun nuevaClave(): String {
    val abc = "abcdefghijklmnopqrstuvwxyz0123456789"
    return "l_" + (1..10).map { abc[kotlin.random.Random.Default.nextInt(abc.length)] }.joinToString("")
}

/**
 * Lo que se va a mandar: null si sirve; si no, por qué (los mismos mensajes que
 * `normalizarItems`): 1–30 ítems, nombre, cantidad entera 1–100 000, precio ≥ 0
 * con los decimales de la moneda, nada demasiado alto, claves únicas.
 */
fun errorItems(items: List<ItemPresupuesto>, moneda: String?): String? {
    if (items.isEmpty()) return "Agrega al menos un servicio"
    if (items.size > LimitesPresupuesto.ITEMS) return "A lo más ${LimitesPresupuesto.ITEMS} servicios por presupuesto"
    val claves = mutableSetOf<String>()
    val dec = decimalesMoneda(moneda)
    items.forEachIndexed { k, it ->
        val nombre = it.nombre.replace(Regex("\\s+"), " ").trim().take(LimitesPresupuesto.NOMBRE_ITEM)
        if (nombre.isEmpty()) return "Servicio ${k + 1}: falta el nombre"
        if (!RE_CLAVE.matches(it.clave)) return "$nombre: línea no válida"
        if (!claves.add(it.clave)) return "$nombre: línea repetida"
        if (it.cantidad < 1 || it.cantidad > LimitesPresupuesto.CANTIDAD_MAX) {
            return "$nombre: la cantidad debe ser un número entero entre 1 y ${milesPresu(LimitesPresupuesto.CANTIDAD_MAX)}"
        }
        val precio = it.precioUnitario
        if (!precio.isFinite()) return "$nombre: falta el precio"
        if (precio < 0) return "$nombre: el precio no puede ser negativo"
        if (precio > MAX_PRECIO_PRESU || (!esPaquete(it) && precio * it.cantidad > MAX_PRECIO_PRESU)) {
            return "$nombre: el precio es demasiado alto, revisa los ceros"
        }
        if (abs(redondearPrecio(precio, moneda) - precio) > 1e-9) {
            return if (dec == 0) "$nombre: esta moneda no usa decimales" else "$nombre: usa a lo más $dec decimales"
        }
    }
    return null
}

/**
 * El descuento tal como se escribe: null si sirve; si no, por qué. % entre 0 y
 * 100; monto con los decimales de la moneda y NUNCA más que el total.
 */
fun errorDescuento(tipo: String?, valor: String?, subtotal: Double, moneda: String?): String? {
    if (valor == null || valor.trim().isEmpty()) return null
    val v = valor.trim().replaceFirst(',', '.').toDoubleOrNull()
    if (v == null || !v.isFinite() || v < 0) return "Escribe un número"
    if (tipo == "porcentaje") return if (v > 100) "El descuento no puede pasar del 100 %" else null
    if (tipo == "monto") {
        if (abs(redondearPrecio(v, moneda) - v) > 1e-9) {
            return if (decimalesMoneda(moneda) == 0) "Esta moneda no usa decimales" else "Usa a lo más ${decimalesMoneda(moneda)} decimales"
        }
        if (v > subtotal + 1e-9) return "No puede pasar del total (${formatearDinero(subtotal, moneda)})"
    }
    return null
}

/** El descuento que llega → limpio (gemelo de normalizarDescuento). Error = null en [tipo] y el mensaje. */
data class DescuentoNormalizado(val ok: Boolean, val tipo: String? = null, val valor: Double = 0.0, val error: String? = null)

fun normalizarDescuento(tipo: String?, valor: String?): DescuentoNormalizado {
    val v = if (valor.isNullOrEmpty()) 0.0 else valor.replaceFirst(',', '.').toDoubleOrNull()
    if (v == null || !v.isFinite() || v < 0) return DescuentoNormalizado(false, error = "El descuento no es válido")
    if (tipo.isNullOrEmpty() || v == 0.0) return DescuentoNormalizado(true, null, 0.0)
    if (tipo == "porcentaje") {
        if (v > 100) return DescuentoNormalizado(false, error = "El descuento no puede pasar del 100 %")
        return DescuentoNormalizado(true, tipo, redondeoJs(v * 100) / 100)
    }
    if (tipo == "monto") {
        if (v > MAX_PRECIO_PRESU) return DescuentoNormalizado(false, error = "El descuento es demasiado alto")
        return DescuentoNormalizado(true, tipo, redondeoJs(v * 100) / 100)
    }
    return DescuentoNormalizado(false, error = "Tipo de descuento no válido")
}

/** La vigencia: YYYY-MM-DD, no antes de hoy y a lo más un año. */
fun validarVigencia(vigencia: String?, hoy: String): String? {
    if (vigencia == null || !Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(vigencia) ||
        runCatching { LocalDate.parse(vigencia) }.isFailure
    ) return "La fecha de vigencia no es válida"
    if (vigencia < hoy) return "La vigencia no puede ser una fecha pasada"
    if (diasEntre(hoy, vigencia) > LimitesPresupuesto.VIGENCIA_MAX_DIAS) return "La vigencia es de a lo más un año"
    return null
}

// ─────────────────────────────────────────────────────────────────────────────
// Catálogo → ítem (modo, precio de lista, paquetes)
// ─────────────────────────────────────────────────────────────────────────────

data class TarifaPresupuesto(val id: String?, val cantidadSesiones: Int, val precioTotal: Double, val estado: String? = null)

/** El servicio con lo que hace falta para armar el ítem (de ProcedimientoRef). */
data class ServicioPresupuesto(
    val id: String,
    val nombre: String,
    val precio: Double? = null,
    val modoCobro: String? = null,
    val unidadLabel: String? = null,
    val precioUnitarioSugerido: Double? = null,
    /** usa_sesiones de su especialidad (null = no se sabe). */
    val usaSesiones: Boolean? = null,
    val estado: String? = "Activo",
    val precioPaquete: Double? = null,
    val tarifarios: List<TarifaPresupuesto> = emptyList(),
    val especialidadId: String? = null,
)

fun servicioDe(p: ProcedimientoRef): ServicioPresupuesto = ServicioPresupuesto(
    id = p.id, nombre = p.nombre, precio = p.precio, modoCobro = p.modoCobro?.takeIf { ModoItemPresu.valido(it) },
    unidadLabel = p.unidadLabel, precioUnitarioSugerido = p.precioUnitarioSugerido, usaSesiones = p.usaSesiones,
    precioPaquete = p.precioPaquete, especialidadId = p.especialidadId,
    tarifarios = p.tarifarios.map { TarifaPresupuesto(it.id, it.cantidadSesiones, it.precioTotal) },
)

/** Un paquete del catálogo: N sesiones por un precio. `id` null = el precio_paquete del servicio. */
data class PaqueteServicio(val id: String?, val sesiones: Int, val precio: Double)

/** Sesiones del "precio de paquete" de un servicio sin tarifarios (el 10 de TratamientoForm). */
const val SESIONES_PAQUETE_SIN_TARIFARIO = 10

/** Los paquetes de un servicio: sus tarifarios activos (de menos a más sesiones); sin ellos, precio_paquete × 10. */
fun paquetesDelServicio(s: ServicioPresupuesto?): List<PaqueteServicio> {
    if (s == null) return emptyList()
    val tarifas = s.tarifarios.filter { it.cantidadSesiones > 0 && (it.estado ?: "Activo") != "Inactivo" }
    if (tarifas.isNotEmpty()) {
        return tarifas.map { PaqueteServicio(it.id, it.cantidadSesiones, it.precioTotal) }.sortedBy { it.sesiones }
    }
    val pp = s.precioPaquete ?: 0.0
    return if (pp > 0) listOf(PaqueteServicio(null, SESIONES_PAQUETE_SIN_TARIFARIO, pp)) else emptyList()
}

/** El paquete que se propone: el de 10 sesiones o el primero. */
fun paquetePorDefecto(s: ServicioPresupuesto?): PaqueteServicio? {
    val ps = paquetesDelServicio(s)
    return ps.firstOrNull { it.sesiones == 10 } ?: ps.firstOrNull()
}

/** Precio de lista del paquete de EXACTAMENTE N sesiones (null = personalizado). */
fun listaPaquete(s: ServicioPresupuesto?, n: Int, otraMoneda: Boolean = false): Double? {
    if (otraMoneda) return null
    return paquetesDelServicio(s).firstOrNull { it.sesiones == n }?.precio
}

/** Modo efectivo (modoCobroEfectivo de la web). */
fun modoDeServicio(s: ServicioPresupuesto): String {
    s.modoCobro?.takeIf { ModoItemPresu.valido(it) }?.let { return it }
    return if (s.usaSesiones == false) ModoItemPresu.SIMPLE else ModoItemPresu.SESIONES
}

/** Precio de lista POR UNIDAD del ítem; `otraMoneda` → sin lista (se escribe). */
fun precioListaItem(s: ServicioPresupuesto?, otraMoneda: Boolean = false): Double? {
    if (s == null || otraMoneda) return null
    if (modoDeServicio(s) == ModoItemPresu.UNIDADES) return s.precioUnitarioSugerido ?: s.precio
    return s.precio
}

/** Un ítem nuevo desde un servicio del catálogo (cantidad 1, precio = lista; sesiones = sueltas). */
fun itemDeServicio(s: ServicioPresupuesto, cantidad: Int = 1, otraMoneda: Boolean = false, precio: Double? = null): ItemPresupuesto {
    val modo = modoDeServicio(s)
    val lista = precioListaItem(s, otraMoneda)
    return ItemPresupuesto(
        clave = nuevaClave(),
        procedimientoId = s.id,
        nombre = s.nombre,
        detalle = null,
        modo = modo,
        unidadLabel = if (modo == ModoItemPresu.UNIDADES) (s.unidadLabel?.trim()?.takeIf { it.isNotEmpty() } ?: "unidades") else null,
        cantidad = max(1, cantidad),
        precioUnitario = precio ?: lista ?: 0.0,
        precioLista = lista,
        cobro = if (modo == ModoItemPresu.SESIONES) CobroPresu.SUELTAS else null,
        precioSesion = if (modo == ModoItemPresu.SESIONES) lista else null,
    )
}

/**
 * Pasar un ítem por sesiones a PAQUETE: N sesiones y el precio del paquete del
 * catálogo (el elegido, o el de 10 / el primero). Sin paquetes: personalizado de
 * las mismas sesiones, a cantidad × precio por sesión (sin lista).
 */
fun comoPaquete(it: ItemPresupuesto, s: ServicioPresupuesto?, otraMoneda: Boolean = false, paquete: PaqueteServicio? = null): ItemPresupuesto {
    val ses = if (otraMoneda) null else (if (s != null) precioListaItem(s) else it.precioSesion)
    val paq = paquete ?: (if (otraMoneda) null else paquetePorDefecto(s))
    if (paq != null) {
        return it.copy(
            modo = ModoItemPresu.SESIONES, cobro = CobroPresu.PAQUETE, cantidad = paq.sesiones,
            precioUnitario = if (otraMoneda) 0.0 else paq.precio, precioLista = if (otraMoneda) null else paq.precio, precioSesion = ses,
        )
    }
    val unitActual = if (it.cobro == CobroPresu.PAQUETE) it.precioUnitario else it.cantidad * it.precioUnitario
    return it.copy(
        modo = ModoItemPresu.SESIONES, cobro = CobroPresu.PAQUETE, precioUnitario = redondeoJs(unitActual * 100) / 100,
        precioLista = listaPaquete(s, it.cantidad, otraMoneda), precioSesion = ses,
    )
}

/** Pasar un ítem por sesiones a SUELTAS: mismas sesiones, a lista por sesión. */
fun comoSueltas(it: ItemPresupuesto, s: ServicioPresupuesto?, otraMoneda: Boolean = false): ItemPresupuesto {
    val ses = if (otraMoneda) null else (if (s != null) precioListaItem(s) else it.precioSesion)
    return it.copy(modo = ModoItemPresu.SESIONES, cobro = CobroPresu.SUELTAS, precioUnitario = ses ?: 0.0, precioLista = ses, precioSesion = ses)
}

/** "Paquete de 10 · S/ 500.00 · por sueltas S/ 600.00" (null si no es paquete o no hay precio por sesión). */
fun textoAhorroPaquete(it: ItemPresupuesto, moneda: String?): String? {
    val ses = it.precioSesion
    if (!esPaquete(it) || ses == null) return null
    val sueltas = redondearPrecio(it.cantidad * ses, moneda)
    return "Paquete de ${it.cantidad} · ${formatearDinero(it.precioUnitario, moneda)} · por sueltas ${formatearDinero(sueltas, moneda)}"
}

/** Un ítem libre (no está en el catálogo): pago único por cantidad. */
fun itemLibre(nombre: String, precio: Double = 0.0, cantidad: Int = 1): ItemPresupuesto = ItemPresupuesto(
    clave = nuevaClave(), procedimientoId = null, nombre = nombre.trim(), detalle = null, modo = ModoItemPresu.SIMPLE,
    unidadLabel = null, cantidad = cantidad, precioUnitario = precio, precioLista = null,
)

/**
 * La lista que manda el CATÁLOGO (el servidor nunca confía en la del cliente; la
 * app la usa para mostrar lo mismo). Un ítem cuyo servicio ya no existe pasa a libre.
 */
fun conListaDelCatalogo(items: List<ItemPresupuesto>, catalogo: List<ServicioPresupuesto>, otraMoneda: Boolean = false): List<ItemPresupuesto> {
    val porId = catalogo.associateBy { it.id }
    return items.map { it ->
        val s = it.procedimientoId?.let { id -> porId[id] }
        if (s == null) {
            val modo = if (it.procedimientoId != null) ModoItemPresu.SIMPLE else it.modo
            return@map it.copy(
                procedimientoId = null, precioLista = null, precioSesion = null, modo = modo,
                cobro = if (modo == ModoItemPresu.SESIONES) (it.cobro ?: CobroPresu.SUELTAS) else null,
            )
        }
        val modo = modoDeServicio(s)
        val porUnidad = precioListaItem(s, otraMoneda)
        val cobro = if (modo == ModoItemPresu.SESIONES) (if (it.cobro == CobroPresu.PAQUETE) CobroPresu.PAQUETE else CobroPresu.SUELTAS) else null
        it.copy(
            modo = modo, cobro = cobro,
            unidadLabel = if (modo == ModoItemPresu.UNIDADES) (it.unidadLabel?.takeIf { l -> l.isNotEmpty() } ?: s.unidadLabel?.trim()?.takeIf { l -> l.isNotEmpty() } ?: "unidades") else null,
            precioLista = if (cobro == CobroPresu.PAQUETE) listaPaquete(s, it.cantidad, otraMoneda) else porUnidad,
            precioSesion = if (modo == ModoItemPresu.SESIONES) porUnidad else null,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Permisos (servidor = pantalla)
// ─────────────────────────────────────────────────────────────────────────────

/** Ver: `sesiones`, `pagos` o `pacientes`. */
fun puedeVerPresupuestos(puede: (String) -> Boolean): Boolean = puede("sesiones") || puede("pagos") || puede("pacientes")

/** Armar, editar, enviar, rechazar, reabrir y borrar borradores. */
fun puedeArmarPresupuesto(puede: (String) -> Boolean): Boolean = puede("sesiones") || puede("pagos")

/** Aceptar POR el paciente en la clínica: médico, recepción, Admin. */
fun puedeAceptarEnPersona(puede: (String) -> Boolean): Boolean = puede("sesiones") || puede("pagos")

/** Convertir en tratamientos. */
fun puedeConvertirPresupuesto(puede: (String) -> Boolean): Boolean = puede("sesiones")

/** ¿Es el autor? El que lo creó o su profesional. Uno nuevo es de quien lo arma. */
fun esAutorPresupuesto(p: Presupuesto?, userId: String?, miTerapeutaId: String?): Boolean {
    if (p == null) return true
    return (!userId.isNullOrBlank() && p.creadoPor == userId) || (!miTerapeutaId.isNullOrBlank() && p.terapeutaId == miTerapeutaId)
}

/** Cambiar el profesional de uno ya creado: `pagos` o quien lo CREÓ. */
fun puedeCambiarProfesionalPresupuesto(puede: (String) -> Boolean, p: Presupuesto?, userId: String?): Boolean =
    p == null || puede("pagos") || (!userId.isNullOrBlank() && p.creadoPor == userId)

/** Precios y descuento: quien cobra (`pagos`) o el autor con `sesiones`. */
fun puedeFijarPrecioPresupuesto(puede: (String) -> Boolean, esAutor: Boolean): Boolean =
    puede("pagos") || (puede("sesiones") && esAutor)

/**
 * ¿Cambia lo que el paciente VE (título, ítems, descuento, notas)? Uno Enviado que
 * cambia vuelve a borrador. Vigencia, nota interna y vínculos no cuentan.
 */
fun contenidoCambiado(
    previo: Presupuesto,
    titulo: String? = null,
    items: List<ItemPresupuesto>? = null,
    descuentoTipo: String? = previo.descuentoTipo,
    descuentoValor: Double? = null,
    notasPaciente: String? = previo.notasPaciente,
): Boolean {
    fun canon(l: List<ItemPresupuesto>) = l.map { listOf(it.clave, it.procedimientoId, it.nombre, it.detalle, it.modo, it.unidadLabel, it.cantidad, it.precioUnitario, it.precioLista, if (it.modo == ModoItemPresu.SESIONES) (it.cobro ?: CobroPresu.SUELTAS) else null) }
    if (titulo != null && titulo != previo.titulo) return true
    if (items != null && canon(items) != canon(previo.items)) return true
    if (descuentoTipo != previo.descuentoTipo) return true
    if (descuentoValor != null && abs(descuentoValor - previo.descuentoValor) >= 0.005) return true
    if (notasPaciente != previo.notasPaciente) return true
    return false
}

/**
 * Para quien NO puede fijar precios: cada ítem al precio de lista o al que ya
 * tenía guardado (misma clave y misma forma de cobro), y el descuento no cambia.
 */
fun cambiosDePrecioNoPermitidos(
    items: List<ItemPresupuesto>,
    previoItems: List<ItemPresupuesto>?,
    previoTipo: String?,
    previoValor: Double,
    tipo: String?,
    valor: Double,
): String? {
    val antes = previoItems.orEmpty().associateBy { it.clave }
    fun igual(a: Double?, b: Double?) = a != null && b != null && abs(a - b) < 0.005
    for (it in items) {
        val prev = antes[it.clave]
        if (igual(it.precioUnitario, it.precioLista)) continue
        if (prev != null && igual(it.precioUnitario, prev.precioUnitario) && prev.cobro == it.cobro &&
            (!esPaquete(it) || prev.cantidad == it.cantidad)
        ) continue
        return if (it.precioLista == null) "Solo quien cobra puede ponerle precio a «${it.nombre}»"
        else "Solo quien cobra o el profesional que armó el presupuesto puede cambiar el precio de «${it.nombre}»"
    }
    if (tipo != previoTipo || abs(valor - previoValor) >= 0.005) {
        return "Solo quien cobra o el profesional que armó el presupuesto puede cambiar el descuento"
    }
    return null
}

// ─────────────────────────────────────────────────────────────────────────────
// Conversión: cada ítem → SU tratamiento (la hace el servidor; acá, la gemela)
// ─────────────────────────────────────────────────────────────────────────────

/** La fila de `tratamientos` de un ítem (gemela de `tratamientoDeLinea`). */
data class TratamientoDeLinea(
    val procedimientoId: String?,
    val terapeutaId: String?,
    val citaOrigenId: String?,
    val sedeId: String?,
    val precioAcordado: Double,
    val presupuestoId: String,
    val presupuestoLinea: String,
    val diagnostico: String?,
    val notas: String,
    val motivoPrecio: String?,
    val modalidad: String,
    val totalSesiones: Int? = null,
    val precioPaquete: Double? = null,
    val precioPorSesion: Double? = null,
    val cantidadUnidades: Int? = null,
    val precioUnitario: Double? = null,
)

fun tratamientoDeLinea(
    it: ItemPresupuesto, monto: Double, presupuestoId: String, titulo: String,
    citaId: String? = null, terapeutaId: String? = null, sedeId: String? = null, procedimientoId: String? = null,
    descuentoLinea: Double = 0.0, moneda: String? = "PEN", diagnostico: String? = null,
): TratamientoDeLinea {
    val procId = it.procedimientoId ?: procedimientoId
    val cant = textoCantidad(it)
    val base = TratamientoDeLinea(
        procedimientoId = procId, terapeutaId = terapeutaId, citaOrigenId = citaId, sedeId = sedeId,
        precioAcordado = monto, presupuestoId = presupuestoId, presupuestoLinea = it.clave,
        diagnostico = if (procId == null) "${it.nombre}${it.detalle?.let { d -> " — $d" } ?: ""}" else diagnostico,
        notas = "Del presupuesto «$titulo»" + (if (cant.isNotEmpty() && it.modo != ModoItemPresu.UNIDADES) " · $cant" else "") +
            (if (it.detalle != null && procId != null) " · ${it.detalle}" else ""),
        motivoPrecio = motivoPrecioLinea(it, monto, descuentoLinea, titulo, moneda),
        modalidad = "Consulta",
    )
    if (it.modo == ModoItemPresu.UNIDADES) return base.copy(modalidad = "Unidades", cantidadUnidades = it.cantidad, precioUnitario = it.precioUnitario)
    if (it.modo == ModoItemPresu.SESIONES && esPaquete(it)) return base.copy(modalidad = "Paquete", totalSesiones = it.cantidad, precioPaquete = monto)
    if (it.modo == ModoItemPresu.SESIONES) {
        val n = max(1, it.cantidad)
        return base.copy(modalidad = "Sesión suelta", totalSesiones = n, precioPorSesion = redondearPrecio(monto / n, moneda))
    }
    return base
}

/** "Presupuesto «Plan facial»: descuento de paquete S/ 30.00" (auditoría de descuentos). */
fun motivoPrecioLinea(it: ItemPresupuesto, monto: Double, descuentoLinea: Double, titulo: String, moneda: String?): String? {
    if (descuentoLinea > 0) return "Presupuesto «$titulo»: descuento de paquete ${formatearDinero(descuentoLinea, moneda)}"
    val lista = listaDeLinea(it, moneda)
    if (lista != null && monto < lista) return "Presupuesto «$titulo»: precio acordado (lista ${formatearDinero(lista, moneda)})"
    return null
}

data class LineaAConvertir(val clave: String, val nombre: String, val monto: Double, val descuentoLinea: Double, val input: TratamientoDeLinea)

/** Todas las líneas: montos prorrateados (Σ = total, al céntimo) y su fila. */
fun lineasAConvertir(
    p: Presupuesto, terapeutaId: String? = p.terapeutaId, servicios: Map<String, String?> = emptyMap(),
    sedeId: String? = p.sedeId, diagnostico: String? = null,
): List<LineaAConvertir> {
    val items = p.items
    val subtotales = items.map { subtotalItem(it, p.moneda) }
    val t = totalesPresupuesto(items, p.descuentoTipo, p.descuentoValor, p.moneda)
    val montos = prorratearDescuento(subtotales, t.descuento, p.moneda)
    return items.mapIndexed { i, it ->
        val dl = redondearPrecio(subtotales[i] - montos[i], p.moneda)
        LineaAConvertir(it.clave, it.nombre, montos[i], dl, tratamientoDeLinea(
            it, montos[i], p.id, p.titulo, citaId = p.citaId, terapeutaId = terapeutaId, sedeId = sedeId,
            procedimientoId = servicios[it.clave], descuentoLinea = dl, moneda = p.moneda, diagnostico = diagnostico,
        ))
    }
}

/**
 * El ADELANTO repartido EN ORDEN entre los tratamientos recién creados, cubriendo
 * cada uno antes de pasar al siguiente. Más que el total → null (no se cobra).
 */
fun repartirAdelanto(monto: Double, lineas: List<Pair<String, Double>>, moneda: String?): List<Pair<String, Double>>? {
    var resto = aMinimas(monto, moneda)
    if (resto <= 0) return emptyList()
    val total = lineas.sumOf { max(0L, aMinimas(it.second, moneda)) }
    if (resto > total) return null
    val partes = mutableListOf<Pair<String, Double>>()
    for ((id, m) in lineas) {
        if (resto <= 0) break
        val cabe = min(resto, max(0L, aMinimas(m, moneda)))
        if (cabe > 0) { partes.add(id to deMinimas(cabe, moneda)); resto -= cabe }
    }
    return partes
}

/** El que más vale (adónde va el adelanto por defecto). */
fun <T> lineaPrincipal(lineas: List<T>, monto: (T) -> Double): T? = lineas.fold(null as T?) { m, l -> if (m == null || monto(l) > monto(m)) l else m }

/** Modalidad de un tratamiento creado → qué agendar (ofertaAgendaTrasCrear de la web). */
fun tipoAgendaDeModalidad(modalidad: String?, monto: Double): TipoTratamientoNuevo = when (modalidad) {
    "Unidades" -> TipoTratamientoNuevo.UNIDADES
    "Consulta" -> if (monto > 0) TipoTratamientoNuevo.SERVICIO_UNICO else TipoTratamientoNuevo.CONSULTA
    else -> TipoTratamientoNuevo.SESIONES
}

/** "📅 Agendar sesiones" · "📅 Agendar intervención" · "📅 Agendar la cita" (etiquetaOfertaAgenda). */
fun etiquetaAgendaPresupuesto(modalidad: String?): String = when (modalidad) {
    "Unidades" -> "📅 Agendar intervención"
    "Consulta" -> "📅 Agendar la cita"
    else -> "📅 Agendar sesiones"
}

// ─────────────────────────────────────────────────────────────────────────────
// Textos (ítems, WhatsApp, recordatorio)
// ─────────────────────────────────────────────────────────────────────────────

/** "× 50 unidades" · "× 3 sesiones sueltas" · "Paquete de 10 sesiones" · "× 2" · "". */
fun textoCantidad(it: ItemPresupuesto): String {
    val n = it.cantidad
    if (it.modo == ModoItemPresu.UNIDADES) return "× ${milesPresu(n)} ${it.unidadLabel?.takeIf { l -> l.isNotEmpty() } ?: if (n == 1) "unidad" else "unidades"}"
    if (it.modo == ModoItemPresu.SESIONES && esPaquete(it)) return if (n == 1) "Paquete de 1 sesión" else "Paquete de $n sesiones"
    if (it.modo == ModoItemPresu.SESIONES) return if (n == 1) "× 1 sesión" else "× $n sesiones sueltas"
    return if (n > 1) "× $n" else ""
}

/** Para el paciente: "Paquete de 10 sesiones" · "10 sesiones sueltas × S/ 60.00" · "50 unidades × S/ 15.00" · "2 × S/ 900.00" · "". */
fun textoCobroItem(it: ItemPresupuesto, moneda: String?): String {
    val n = it.cantidad
    val p = formatearDinero(it.precioUnitario, moneda)
    if (it.modo == ModoItemPresu.SESIONES && esPaquete(it)) return if (n == 1) "Paquete de 1 sesión" else "Paquete de $n sesiones"
    if (it.modo == ModoItemPresu.SESIONES) return if (n == 1) "1 sesión × $p" else "$n sesiones sueltas × $p"
    if (it.modo == ModoItemPresu.UNIDADES) return "${milesPresu(n)} ${it.unidadLabel?.takeIf { l -> l.isNotEmpty() } ?: if (n == 1) "unidad" else "unidades"} × $p"
    return if (n > 1) "$n × $p" else ""
}

/** "Descuento de paquete (10 %)" / "Descuento de paquete". */
fun etiquetaDescuento(tipo: String?, valor: Double?): String {
    val v = valor ?: 0.0
    return if (tipo == "porcentaje" && v > 0) "Descuento de paquete (${numeroCorto(v)} %)" else "Descuento de paquete"
}

/** 10.0 → "10"; 12.5 → "12.5" (Number(valor) de JS). */
fun numeroCorto(v: Double): String = if (v == floor(v) && abs(v) < 1e15) v.toLong().toString() else v.toString()

private val MESES_LARGOS = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")

/** "16 de octubre de 2026" (fecha de calendario, sin husos). */
fun fechaLarga(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val d = runCatching { LocalDate.parse(iso.take(10)) }.getOrNull() ?: return ""
    return "${d.dayOfMonth} de ${MESES_LARGOS[d.monthNumber - 1]} de ${d.year}"
}

/** Primer nombre para el saludo ("MARÍA ELENA" → "María"). */
fun primerNombre(nombre: String?): String {
    val n = nombre.orEmpty().trim().split(Regex("\\s+")).firstOrNull().orEmpty()
    return if (n.isEmpty()) "" else n.substring(0, 1).uppercase() + n.substring(1).lowercase()
}

/** El presupuesto por WhatsApp (con *negritas*). Sin notas internas. */
fun textoPresupuestoWhatsApp(
    p: Presupuesto, clinica: String? = null, paciente: String? = null, profesional: String? = null, conPortal: Boolean = false,
): String {
    val items = p.items
    fun din(n: Double) = formatearDinero(n, p.moneda)
    val t = totalesPresupuesto(items, p.descuentoTipo, p.descuentoValor, p.moneda)
    val nombre = primerNombre(paciente)
    val partes = mutableListOf(
        "🧾 *Presupuesto — ${p.titulo}*",
        "${if (nombre.isNotEmpty()) "Hola $nombre, " else "Hola, "}este es el plan que conversamos${if (!clinica.isNullOrBlank()) " en $clinica" else ""}:",
        items.joinToString("\n") { it ->
            val cobro = textoCobroItem(it, p.moneda)
            "• *${it.nombre}*${it.detalle?.let { d -> " ($d)" } ?: ""}${if (cobro.isNotEmpty()) " — $cobro" else ""}: ${din(subtotalItem(it, p.moneda))}"
        },
    )
    val cuentas = mutableListOf<String>()
    if (t.descuento > 0) {
        cuentas.add("Subtotal: ${din(t.subtotal)}")
        cuentas.add("${etiquetaDescuento(p.descuentoTipo, p.descuentoValor)}: −${din(t.descuento)}")
    }
    cuentas.add("*Total: ${din(t.total)}*")
    if (t.ahorro > 0) cuentas.add("_Por separado: ${din(t.porSeparado)} · ahorras ${din(t.ahorro)}_")
    partes.add(cuentas.joinToString("\n"))
    p.notasPaciente?.trim()?.takeIf { it.isNotEmpty() }?.let { partes.add(it) }
    partes.add("Válido hasta el ${fechaLarga(p.vigenciaHasta)}.")
    partes.add(
        if (conPortal) "Para aceptarlo, responde a este mensaje o acéptalo desde tu app de la clínica."
        else "Para aceptarlo o resolver tus dudas, responde a este mensaje.",
    )
    if (!profesional.isNullOrBlank()) partes.add("— $profesional")
    return partes.joinToString("\n\n")
}

/** "📱 Recordar por WhatsApp": corto, amable, con el total y la vigencia. */
fun textoRecordatorioWhatsApp(p: Presupuesto, hoy: String, clinica: String? = null, paciente: String? = null): String {
    val t = totalesDe(p)
    val nombre = primerNombre(paciente)
    val vencido = p.vigenciaHasta < hoy
    return listOf(
        "${if (nombre.isNotEmpty()) "Hola $nombre" else "Hola"} 👋, te escribimos${if (!clinica.isNullOrBlank()) " de $clinica" else ""} por el presupuesto *${p.titulo}* (${formatearDinero(t.total, p.moneda)}).",
        if (vencido) "Su vigencia ya pasó, pero con gusto te lo actualizamos. ¿Te gustaría empezar?"
        else "Sigue vigente hasta el ${fechaLarga(p.vigenciaHasta)}. ¿Tienes alguna duda o te gustaría agendar tu primera sesión?",
    ).joinToString("\n\n")
}

// ─────────────────────────────────────────────────────────────────────────────
// Seguimiento (pendientes)
// ─────────────────────────────────────────────────────────────────────────────

/** `esperan` | `vencidos` | `aceptados` (sin tratamientos); el resto no es pendiente (null). */
fun grupoPendiente(p: Presupuesto, hoy: String): String? {
    if (faltaConvertir(p)) return "aceptados"
    if (p.estado != "Enviado") return null
    return if (estadoVisiblePresupuesto(p.estado, p.vigenciaHasta, hoy) == "Vencido") "vencidos" else "esperan"
}

/** ¿Toca recordarle? Enviado hace ≥ 2 días y sin recordatorio en los últimos 2. */
fun tocaRecordar(diasEnvio: Int?, diasRecordado: Int?): Boolean {
    if (diasEnvio == null || diasEnvio < 2) return false
    return diasRecordado == null || diasRecordado >= 2
}

// ─────────────────────────────────────────────────────────────────────────────
// Errores de la base → mensaje claro
// ─────────────────────────────────────────────────────────────────────────────

private val ERRORES_PRESU = listOf(
    "PRESUPUESTO_PACIENTE_INVALIDO" to "El paciente no es de esta clínica",
    "PRESUPUESTO_CITA_INVALIDA" to "La cita no es de este paciente",
    "PRESUPUESTO_PROFESIONAL_INVALIDO" to "El profesional no es de esta clínica",
    "PRESUPUESTO_ESPECIALIDAD_INVALIDA" to "La especialidad no es de esta clínica",
    "PRESUPUESTO_SEDE_INVALIDA" to "La sede no es de esta clínica",
    "PRESUPUESTO_INMUTABLE" to "El paciente y la moneda de un presupuesto no cambian",
    "PRESUPUESTO_ACEPTADO_INMUTABLE" to "Un presupuesto aceptado ya no se modifica",
    "PRESUPUESTO_RECHAZADO_INMUTABLE" to "Reábrelo para modificarlo",
    "PRESUPUESTO_TRANSICION_INVALIDA" to "Ese cambio de estado no es posible",
    "PRESUPUESTO_ACEPTACION_SIN_CANAL" to "Falta registrar cómo se aceptó",
    "FICHA_FUSIONADA" to "Esta ficha se fusionó con otra: arma el presupuesto en la ficha que quedó",
)

fun mensajeErrorPresupuesto(msg: String?): String {
    val m = msg.orEmpty()
    for ((k, v) in ERRORES_PRESU) if (m.contains(k)) return v
    if (m.contains("presupuestos_items_chk")) return "El presupuesto debe tener entre 1 y ${LimitesPresupuesto.ITEMS} servicios"
    if (m.contains("presupuestos_titulo_chk")) return "Falta el título (máx. 120 caracteres)"
    if (m.contains("presupuestos_descuento_chk")) return "El descuento no es válido"
    return "No se pudo guardar el presupuesto"
}

/** Texto del plan bloqueado (403 PLAN_REQUERIDO / Básico / vencido). */
const val TEXTO_CANDADO_PRESUPUESTOS = "Arma el plan con varios servicios y precio de paquete, envíaselo por WhatsApp o PDF y " +
    "conviértelo en tratamientos cuando lo acepte. Disponible en Premium y Plus."

// ─────────────────────────────────────────────────────────────────────────────
// JSON ↔ modelo (respuestas en snake_case; pedidos en camelCase)
// ─────────────────────────────────────────────────────────────────────────────

private fun JsonObject.txtP(k: String): String? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

private fun JsonObject.numP(k: String): Double? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }?.takeIf { it.isFinite() }

private fun JsonObject.boolP(k: String): Boolean? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.booleanOrNull

/** Ítems guardados (jsonb) → tipados; lo que no sirva se descarta (nunca revienta). Gemelo de `leerItems`. */
fun leerItemsPresupuesto(v: JsonElement?): List<ItemPresupuesto> {
    val arr = v as? JsonArray ?: return emptyList()
    return arr.mapNotNull { el ->
        val x = el as? JsonObject ?: return@mapNotNull null
        val clave = (x["clave"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@mapNotNull null
        val modoCrudo = x.txtP("modo")
        val modo = if (ModoItemPresu.valido(modoCrudo)) modoCrudo!! else ModoItemPresu.SIMPLE
        ItemPresupuesto(
            clave = clave,
            procedimientoId = x.txtP("procedimiento_id"),
            nombre = (x["nombre"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            detalle = x.txtP("detalle"),
            modo = modo,
            unidadLabel = x.txtP("unidad_label"),
            cantidad = max(0, (x.numP("cantidad") ?: 0.0).toLong().toInt()),
            precioUnitario = max(0.0, x.numP("precio_unitario") ?: 0.0),
            precioLista = x.numP("precio_lista"),
            cobro = if (modoCrudo == ModoItemPresu.SESIONES) (if (x.txtP("cobro") == CobroPresu.PAQUETE) CobroPresu.PAQUETE else CobroPresu.SUELTAS) else null,
            precioSesion = x.numP("precio_sesion"),
        )
    }
}

/** Una fila de la API → [Presupuesto]. null si no trae id. */
fun presupuestoDeJson(o: JsonObject): Presupuesto? {
    val id = o.txtP("id") ?: return null
    val pac = o["paciente"] as? JsonObject
    return Presupuesto(
        id = id,
        pacienteId = o.txtP("paciente_id").orEmpty(),
        sedeId = o.txtP("sede_id"),
        moneda = normalizarMoneda(o.txtP("moneda")),
        citaId = o.txtP("cita_id"),
        terapeutaId = o.txtP("terapeuta_id"),
        especialidadId = o.txtP("especialidad_id"),
        titulo = o.txtP("titulo") ?: "Presupuesto",
        items = leerItemsPresupuesto(o["items"]),
        descuentoTipo = o.txtP("descuento_tipo")?.takeIf { it == "porcentaje" || it == "monto" },
        descuentoValor = o.numP("descuento_valor") ?: 0.0,
        notasPaciente = o.txtP("notas_paciente"),
        notasInternas = o.txtP("notas_internas"),
        vigenciaHasta = o.txtP("vigencia_hasta")?.take(10).orEmpty(),
        estado = o.txtP("estado") ?: "Borrador",
        enviadoAt = o.txtP("enviado_at"),
        recordadoAt = o.txtP("recordado_at"),
        aceptadoAt = o.txtP("aceptado_at"),
        aceptadoPor = o.txtP("aceptado_por"),
        aceptadoPorNombre = o.txtP("aceptado_por_nombre"),
        aceptadoCanal = o.txtP("aceptado_canal"),
        rechazadoAt = o.txtP("rechazado_at"),
        motivoRechazo = o.txtP("motivo_rechazo"),
        convertidoAt = o.txtP("convertido_at"),
        visiblePaciente = o.boolP("visible_paciente") ?: true,
        creadoPor = o.txtP("creado_por"),
        createdAt = o.txtP("created_at").orEmpty(),
        updatedAt = o.txtP("updated_at"),
        paciente = pac?.txtP("id")?.let { pid ->
            PacientePresupuesto(
                id = pid, nombre = pac.txtP("nombre") ?: "Paciente", telefono = pac.txtP("telefono"),
                apoderadoTelefono = pac.txtP("apoderado_telefono"),
                apoderadoRecibeAvisos = pac.boolP("apoderado_recibe_avisos") ?: false, sedeId = pac.txtP("sede_id"),
            )
        },
    )
}

fun tratDePresupuestoDeJson(o: JsonObject): TratDePresupuesto? {
    val id = o.txtP("id") ?: return null
    return TratDePresupuesto(
        id = id, presupuestoId = o.txtP("presupuesto_id").orEmpty(), presupuestoLinea = o.txtP("presupuesto_linea"),
        estado = o.txtP("estado"), modalidad = o.txtP("modalidad"), precioAcordado = o.numP("precio_acordado"),
        procedimientoNombre = (o["procedimiento"] as? JsonObject)?.txtP("nombre"),
    )
}

fun plantillaPresupuestoDeJson(o: JsonObject): PlantillaPresupuesto? {
    val id = o.txtP("id") ?: return null
    return PlantillaPresupuesto(
        id = id, nombre = o.txtP("nombre") ?: "Paquete", especialidadId = o.txtP("especialidad_id"),
        titulo = o.txtP("titulo").orEmpty(), items = leerItemsPresupuesto(o["items"]),
        descuentoTipo = o.txtP("descuento_tipo")?.takeIf { it == "porcentaje" || it == "monto" },
        descuentoValor = o.numP("descuento_valor") ?: 0.0, notasPaciente = o.txtP("notas_paciente"),
        vigenciaDias = (o.numP("vigencia_dias") ?: VIGENCIA_DIAS_DEFECTO.toDouble()).toInt(),
    )
}

/** Los ítems como los pide la API (camelCase; `precioLista`/`precioSesion` los pone el servidor). */
fun itemsAJson(items: List<ItemPresupuesto>): JsonArray = buildJsonArray {
    items.forEach { it ->
        add(buildJsonObject {
            put("clave", it.clave)
            if (it.procedimientoId != null) put("procedimientoId", it.procedimientoId) else put("procedimientoId", JsonNull)
            put("nombre", it.nombre.replace(Regex("\\s+"), " ").trim())
            if (!it.detalle.isNullOrBlank()) put("detalle", it.detalle.trim()) else put("detalle", JsonNull)
            put("modo", it.modo)
            if (it.unidadLabel != null) put("unidadLabel", it.unidadLabel)
            put("cantidad", it.cantidad)
            put("precioUnitario", it.precioUnitario)
            if (it.modo == ModoItemPresu.SESIONES) put("cobro", it.cobro ?: CobroPresu.SUELTAS)
        })
    }
}

/** Lo que guarda el editor (crear o editar). */
data class DatosPresupuesto(
    val titulo: String,
    val items: List<ItemPresupuesto>,
    val descuentoTipo: String?,
    val descuentoValor: Double,
    val notasPaciente: String?,
    val notasInternas: String?,
    val vigenciaHasta: String,
)

fun cuerpoDatosPresupuesto(d: DatosPresupuesto): JsonObject = buildJsonObject {
    put("titulo", d.titulo)
    put("items", itemsAJson(d.items))
    if (d.descuentoTipo != null) put("descuentoTipo", d.descuentoTipo) else put("descuentoTipo", JsonNull)
    put("descuentoValor", d.descuentoValor)
    if (d.notasPaciente != null) put("notasPaciente", d.notasPaciente) else put("notasPaciente", JsonNull)
    if (d.notasInternas != null) put("notasInternas", d.notasInternas) else put("notasInternas", JsonNull)
    put("vigenciaHasta", d.vigenciaHasta)
}

// ─────────────────────────────────────────────────────────────────────────────
// La hoja (PDF): gemela de HojaPresupuesto.tsx, armada en la app
// ─────────────────────────────────────────────────────────────────────────────

private fun escP(s: String?): String = s.orEmpty()
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

/**
 * La hoja A4 del presupuesto: logo, clínica, paciente, el plan, descuento, TOTAL
 * y ahorro, notas para el paciente, vigencia, firmas y el sello de aceptación.
 * Nunca las notas internas. Todo escapado; logo solo https; color solo #rrggbb.
 */
fun htmlPresupuesto(
    p: Presupuesto,
    clinicaNombre: String?,
    clinicaDireccion: String?,
    logoUrl: String?,
    colorPrincipal: String?,
    pacienteNombre: String?,
    pacienteDocumento: String?,
    profesional: String?,
    zona: String?,
): String {
    val color = colorHojaIndicaciones(colorPrincipal)
    val t = totalesDe(p)
    fun din(n: Double) = escP(formatearDinero(n, p.moneda))
    val emitido = (p.enviadoAt ?: p.createdAt).takeIf { it.isNotBlank() }?.take(10)
    val logo = logoUrl?.trim()?.takeIf { it.startsWith("https://") }?.let { "<img src=\"${escP(it)}\" alt=\"\" class=\"logo\">" }.orEmpty()
    val filas = p.items.joinToString("") { it ->
        val cant = textoCantidad(it).removePrefix("× ").removePrefix("×").trim().ifEmpty { "1" }
        "<tr><td><div class=\"sv\">${escP(it.nombre)}</div>${it.detalle?.let { d -> "<div class=\"s\">${escP(d)}</div>" }.orEmpty()}</td>" +
            "<td class=\"nw\">${escP(cant)}</td><td class=\"r nw\">${din(it.precioUnitario)}</td><td class=\"r nw b\">${din(subtotalItem(it, p.moneda))}</td></tr>"
    }
    val descuento = if (t.descuento > 0) {
        "<div class=\"fila\"><span>Subtotal</span><span>${din(t.subtotal)}</span></div>" +
            "<div class=\"fila\"><span>${escP(etiquetaDescuento(p.descuentoTipo, p.descuentoValor))}</span><span>−${din(t.descuento)}</span></div>"
    } else ""
    val ahorro = if (t.ahorro > 0) "<div class=\"ah\">Por separado ${din(t.porSeparado)} · <b>ahorras ${din(t.ahorro)}</b></div>" else ""
    val notas = p.notasPaciente?.trim()?.takeIf { it.isNotEmpty() }?.let { "<div class=\"notas\">${escP(it)}</div>" }.orEmpty()
    val sello = if (p.estado == "Aceptado" && !p.aceptadoAt.isNullOrBlank()) {
        "<div class=\"sello\">✓ Aceptado el ${escP(fechaHoraAceptacion(p.aceptadoAt, zona))}${if (p.aceptadoCanal == "portal") " · desde su app" else " · en la clínica"}</div>"
    } else ""
    return """<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Presupuesto — ${escP(pacienteNombre)}</title>
<style>
@page { size: A4 portrait; margin: 1.5cm; }
* { box-sizing: border-box; }
body { font-family: Lato, 'Segoe UI', Arial, sans-serif; color: #111827; margin: 0 auto; max-width: 800px; padding: 16px; font-size: 10.5pt; line-height: 1.35; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
.cab { display: flex; align-items: center; gap: 12px; padding-bottom: 10px; border-bottom: 2px solid $color; }
.logo { width: 64px; height: 64px; object-fit: contain; }
.cli { flex: 1; min-width: 0; } .cli .n { font-weight: bold; font-size: 13pt; text-transform: uppercase; word-break: break-word; }
.s { color: #4b5563; font-size: 8.5pt; word-break: break-word; }
.doc { text-align: right; } .doc .n { font-weight: bold; font-size: 11pt; color: $color; }
.pac { padding: 8px 0; border-bottom: 1px solid #e5e7eb; font-size: 9.5pt; }
.pac span { margin-right: 24px; }
h1 { margin: 16px 0 0; font-size: 14pt; color: $color; word-break: break-word; }
table { width: 100%; border-collapse: collapse; margin-top: 12px; font-size: 9.5pt; }
th { text-align: left; font-size: 8pt; text-transform: uppercase; letter-spacing: .03em; color: #6b7280; border-bottom: 1px solid #d1d5db; padding: 6px 8px 6px 0; }
td { border-bottom: 1px solid #f3f4f6; padding: 6px 8px 6px 0; vertical-align: top; }
tr { break-inside: avoid; }
.sv { font-weight: 600; word-break: break-word; } .r { text-align: right; } .nw { white-space: nowrap; } .b { font-weight: 600; }
.tot { margin-top: 12px; margin-left: auto; max-width: 300px; font-size: 9.5pt; break-inside: avoid; }
.fila { display: flex; justify-content: space-between; gap: 12px; }
.total { display: flex; justify-content: space-between; gap: 12px; border-top: 2px solid $color; color: $color; margin-top: 4px; padding-top: 4px; font-size: 12pt; font-weight: bold; }
.ah { text-align: right; font-size: 8.5pt; color: #4b5563; } .ah b { color: #15803d; }
.notas { margin-top: 16px; border: 1px solid #d1d5db; border-radius: 8px; padding: 8px 12px; white-space: pre-line; word-break: break-word; break-inside: avoid; }
.vig { margin-top: 12px; font-size: 9pt; color: #374151; }
.sello { margin-top: 16px; display: inline-block; border: 2px solid #15803d; color: #166534; border-radius: 8px; padding: 6px 12px; font-weight: bold; font-size: 9.5pt; }
.firmas { margin-top: 40px; display: flex; gap: 32px; }
.firmas div { flex: 1; text-align: center; font-size: 8.5pt; }
.linea { border-top: 1px solid #1f2937; margin-top: 14mm; padding-top: 2px; }
.pie { margin-top: 24px; padding-top: 6px; border-top: 1px solid #e5e7eb; font-size: 7.5pt; color: #9ca3af; display: flex; justify-content: space-between; gap: 8px; }
</style></head><body>
<div class="cab">$logo<div class="cli"><div class="n">${escP(clinicaNombre)}</div>${clinicaDireccion?.takeIf { it.isNotBlank() }?.let { "<div class=\"s\">${escP(it)}</div>" }.orEmpty()}</div>
<div class="doc"><div class="n">PRESUPUESTO</div>${emitido?.let { "<div class=\"s\">${escP(fechaLarga(it))}</div>" }.orEmpty()}</div></div>
<div class="pac"><span><b>Paciente:</b> ${escP(pacienteNombre?.takeIf { it.isNotBlank() } ?: "—")}</span>${pacienteDocumento?.takeIf { it.isNotBlank() }?.let { "<span><b>Documento:</b> ${escP(it)}</span>" }.orEmpty()}</div>
<h1>${escP(p.titulo)}</h1>
<table><thead><tr><th>Servicio</th><th>Cantidad</th><th class="r">Precio</th><th class="r">Subtotal</th></tr></thead><tbody>$filas</tbody></table>
<div class="tot">$descuento<div class="total"><span>TOTAL</span><span>${din(t.total)}</span></div>$ahorro</div>
$notas
<div class="vig"><b>Válido hasta el ${escP(fechaLarga(p.vigenciaHasta))}.</b> Los precios pueden variar después de esa fecha.</div>
$sello
<div class="firmas"><div><div class="linea">Firma y sello del profesional</div>${profesional?.takeIf { it.isNotBlank() }?.let { "<b>${escP(it)}</b>" }.orEmpty()}</div>
<div><div class="linea">Conformidad del paciente</div>${pacienteNombre?.takeIf { it.isNotBlank() }?.let { "<b>${escP(it)}</b>" }.orEmpty()}</div></div>
<div class="pie"><span>Presupuesto referencial: el plan puede ajustarse según la evolución clínica.</span><span>Emitido con Sania</span></div>
</body></html>"""
}
