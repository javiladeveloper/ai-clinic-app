package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Reportes "Mes a mes" (nativo). Gemelo de la página /reportes de la web y de
 * lib/series-mensuales.ts: los números los arma el servidor
 * (GET /api/reportes/series); aquí solo se leen y se preparan para pintar.
 *
 * Lógica pura (sin red ni Compose) para poder probarla: es donde es más fácil
 * mentir sin darse cuenta — el mes en curso NO se compara con un mes cerrado.
 */

/** Un mes de una serie (forma de `PuntoMes` en la web). */
data class PuntoMes(
    /** 'YYYY-MM' */
    val mes: String,
    /** 'Jul', 'Ago', 'Dic 25'… (la arma el servidor). */
    val etiqueta: String,
    val valor: Double,
    /** El mes aún no termina: el valor va a seguir subiendo. */
    val parcial: Boolean,
    /** Solo en el mes en curso: a cuánto va camino de cerrar. */
    val proyectado: Double? = null,
    /** Solo en el mes en curso: cuánto llevaba el mes anterior a estas alturas. */
    val mismoTramoAnterior: Double? = null,
    val diasTranscurridos: Int? = null,
    val diasDelMes: Int? = null,
)

/** "Qué pasó cada mes": una promoción, un feriado… (tabla `hitos_mes`). */
data class HitoMes(
    val id: String,
    val mes: String,
    val titulo: String,
    val detalle: String?,
    val tipo: String,
)

data class SeriesReporte(
    val pacientes: List<PuntoMes>,
    val citas: List<PuntoMes>,
    val sesiones: List<PuntoMes>,
    val ingresos: List<PuntoMes>,
    val egresos: List<PuntoMes>,
    val hitos: List<HitoMes>,
    /** 'YYYY-MM-DD' del día de la clínica (lo da el servidor). */
    val hoy: String?,
)

/** Tipos de hito (los mismos cuatro de HitosDelMes.tsx). */
data class TipoHito(val valor: String, val emoji: String, val etiqueta: String, val ayuda: String)

val TIPOS_HITO = listOf(
    TipoHito("promocion", "🎉", "Promoción", "Un descuento o campaña que atrajo gente"),
    TipoHito("feriado", "📆", "Feriado", "Fiestas Patrias, Navidad, un puente largo"),
    TipoHito("campania", "📣", "Campaña", "Publicidad, volanteo, redes"),
    TipoHito("otro", "📌", "Otro", "Obra en la calle, un profesional de vacaciones…"),
)

fun emojiHito(tipo: String?): String = TIPOS_HITO.firstOrNull { it.valor == tipo }?.emoji ?: "📌"

// ── Lectura de la respuesta ──

private fun JsonObject.texto(k: String): String? =
    (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }

private fun JsonObject.numero(k: String): Double? = texto(k)?.toDoubleOrNull()

private fun parsearPunto(o: JsonObject): PuntoMes? {
    val mes = o.texto("mes") ?: return null
    return PuntoMes(
        mes = mes,
        etiqueta = o.texto("etiqueta") ?: etiquetaMes(mes),
        valor = o.numero("valor") ?: 0.0,
        parcial = (o["parcial"] as? JsonPrimitive)?.booleanOrNull ?: false,
        proyectado = o.numero("proyectado"),
        mismoTramoAnterior = o.numero("mismoTramoAnterior"),
        diasTranscurridos = o.numero("diasTranscurridos")?.toInt(),
        diasDelMes = o.numero("diasDelMes")?.toInt(),
    )
}

private fun serie(o: JsonObject?, k: String): List<PuntoMes> =
    (o?.get(k) as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::parsearPunto) }

/** Respuesta de GET /api/reportes/series → lo que pinta la pantalla. */
fun parsearSeriesReporte(cuerpo: String): SeriesReporte {
    val raiz = Json.parseToJsonElement(cuerpo).jsonObject
    val s = raiz["series"] as? JsonObject
    val hitos = (raiz["hitos"] as? JsonArray).orEmpty().mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        HitoMes(
            id = o.texto("id") ?: return@mapNotNull null,
            mes = o.texto("mes") ?: return@mapNotNull null,
            titulo = o.texto("titulo") ?: "",
            detalle = o.texto("detalle")?.takeIf { it.isNotBlank() },
            tipo = o.texto("tipo") ?: "otro",
        )
    }
    return SeriesReporte(
        pacientes = serie(s, "pacientes"),
        citas = serie(s, "citas"),
        sesiones = serie(s, "sesiones"),
        ingresos = serie(s, "ingresos"),
        egresos = serie(s, "egresos"),
        hitos = hitos,
        hoy = raiz.texto("hoy"),
    )
}

// ── Meses ──

private val MESES_CORTOS = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")

/** 'YYYY-MM' → 'Jul' (o 'Jul 25' si es de otro año que el de referencia). Gemelo de etiquetaMes(). */
fun etiquetaMes(mes: String, anioReferencia: Int? = null): String {
    val partes = mes.split("-")
    val a = partes.getOrNull(0)?.toIntOrNull()
    val m = partes.getOrNull(1)?.toIntOrNull() ?: 1
    val corto = MESES_CORTOS.getOrNull(m - 1) ?: mes
    return if (anioReferencia != null && a != null && a != anioReferencia) "$corto ${a.toString().takeLast(2)}" else corto
}

/** 'YYYY-MM' → 'Jul 2026' (para el selector de mes de los hitos). */
fun mesConAnio(mes: String): String = "${etiquetaMes(mes)} ${mes.take(4)}"

// ── Titulares del mes (TarjetaMetrica.tsx) ──

/**
 * Variación entre el tramo actual y el mismo tramo del mes pasado, en %.
 * null cuando no hay base (dividir por cero no es "creció infinito").
 */
fun variacionTramo(actual: Double, anterior: Double): Int? {
    if (anterior == 0.0) return null
    return (((actual - anterior) / anterior) * 100).roundToInt()
}

/** Lo que dice la tarjeta de una métrica: el mes en curso con su comparación honesta. */
data class TitularMetrica(
    val valor: Double,
    val parcial: Boolean,
    val diasTranscurridos: Int?,
    val diasDelMes: Int?,
    val proyectado: Double?,
    /** % vs. el mismo tramo del mes pasado; null = no se muestra (no significa nada todavía). */
    val variacion: Int?,
    /** Cuánto llevaba el mes pasado a estas alturas (se muestra pelado si no hay %). */
    val mismoTramoAnterior: Double?,
)

/**
 * El porcentaje solo se muestra cuando de verdad significa algo (mismos tres
 * filtros que la web): base chica (1 vs 2 es "▼50%"), mes en cero ("▼100%"
 * asusta sin decir nada) y menos de 10 días corridos (una semana de datos
 * exagera siempre). Cuando se calla, queda el número del tramo anterior.
 */
fun titularDe(serie: List<PuntoMes>): TitularMetrica? {
    val actual = serie.lastOrNull() ?: return null
    val anterior = actual.mismoTramoAnterior
    val variacion = anterior?.let { variacionTramo(actual.valor, it) }
    val base = anterior ?: 0.0
    val dias = actual.diasTranscurridos ?: 99
    val mostrar = variacion != null && base >= 5 && actual.valor > 0 && dias >= 10
    return TitularMetrica(
        valor = actual.valor,
        parcial = actual.parcial,
        diasTranscurridos = actual.diasTranscurridos,
        diasDelMes = actual.diasDelMes,
        proyectado = actual.proyectado,
        variacion = if (mostrar) variacion else null,
        mismoTramoAnterior = anterior,
    )
}

/** Balance del último mes CERRADO (el en curso aún no es comparable). */
data class BalanceMes(val etiqueta: String, val ingresos: Double, val egresos: Double) {
    val balance: Double get() = ((ingresos - egresos) * 100).roundToLong() / 100.0
}

fun balanceUltimoCerrado(ingresos: List<PuntoMes>, egresos: List<PuntoMes>): BalanceMes? {
    val idx = ingresos.indexOfLast { !it.parcial }
    if (idx < 0) return null
    return BalanceMes(ingresos[idx].etiqueta, ingresos[idx].valor, egresos.getOrNull(idx)?.valor ?: 0.0)
}

// ── Formato (gemelos de soles()/entero() de lib/grafico-tema.ts) ──

private fun conMiles(n: Long): String {
    val s = abs(n).toString().reversed().chunked(3).joinToString(",").reversed()
    return if (n < 0) "-$s" else s
}

/** Entero con separador de miles: 1,205. */
fun entero(n: Double): String = conMiles(n.roundToLong())

/**
 * Soles para gráficos y titulares: "S/ 28,349" en detalle, "S/ 28.3k" en el eje
 * (compacto, desde mil).
 */
fun solesGrafico(n: Double, compacto: Boolean = false): String {
    if (compacto && abs(n) >= 1000) {
        val miles = n / 1000
        val txt = if (n % 1000 == 0.0) miles.roundToLong().toString()
        else {
            val decimas = (miles * 10).roundToLong()
            val ent = decimas / 10
            val dec = abs(decimas % 10)
            if (dec == 0L) ent.toString() else "${if (decimas < 0 && ent == 0L) "-" else ""}$ent.$dec"
        }
        return "S/ ${txt}k"
    }
    return "S/ ${conMiles(n.roundToLong())}"
}

// ── Eje y toques del gráfico ──

/**
 * Marcas "redondas" del eje Y desde 0 hasta cubrir [maximo] (0, 5, 10, 15…;
 * 0, 2k, 4k…). Siempre devuelve al menos [0, algo]: un gráfico todo en cero
 * no debe dividir por cero.
 *
 * [conteo] = citas, pacientes, sesiones: pasos de 1/2/5/10 y nunca menos de 1
 * (con 2.5 el eje entero mostraba "0, 3, 5, 8, 10"). El dinero sí admite 2.5
 * (S/ 2.5k, S/ 5k…).
 */
fun marcasEje(maximo: Double, marcasDeseadas: Int = 4, conteo: Boolean = true): List<Double> {
    if (maximo <= 0) return listOf(0.0, 1.0)
    val crudo = maximo / marcasDeseadas
    val potencia = 10.0.pow(floor(log10(crudo)))
    val fraccion = crudo / potencia
    val paso0 = when {
        fraccion <= 1 -> 1.0
        fraccion <= 2 -> 2.0
        fraccion <= 2.5 && !conteo -> 2.5
        fraccion <= 5 -> 5.0
        else -> 10.0
    } * potencia
    // Conteos (citas, pacientes) no llevan medias marcas: 0, 0.5, 1 se lee mal.
    val paso = if (conteo && paso0 < 1) 1.0 else paso0
    val tope = ceil(maximo / paso) * paso
    val n = (tope / paso).roundToInt()
    return (0..n).map { it * paso }
}

/** Qué barra tocó el dedo: índice 0..n-1 según la x dentro del área de barras, o null fuera. */
fun indiceTocado(x: Float, izquierda: Float, ancho: Float, n: Int): Int? {
    if (n <= 0 || ancho <= 0f || x < izquierda || x > izquierda + ancho) return null
    val i = floor((x - izquierda) / (ancho / n)).toInt()
    return i.coerceIn(0, n - 1)
}

/**
 * Cada cuántos meses poner etiqueta en el eje X para que no se pisen: con 24
 * meses a 360 dp no caben todas. Siempre se ve la última (el mes en curso).
 */
fun pasoEtiquetas(n: Int, anchoDisponible: Float, anchoEtiqueta: Float): Int {
    if (n <= 0 || anchoDisponible <= 0f) return 1
    val porMes = anchoDisponible / n
    return ceil(anchoEtiqueta / porMes).toInt().coerceAtLeast(1)
}

/** ¿Se dibuja la etiqueta del mes [i]? Se cuenta desde el último hacia atrás. */
fun muestraEtiqueta(i: Int, n: Int, paso: Int): Boolean = (n - 1 - i) % paso == 0

// ── Exportar (lo mismo que el botón "⬇ Exportar" de la web) ──

private fun celdaCsv(v: String): String =
    if (v.any { it == '"' || it == ',' || it == '\n' || it == ';' }) "\"${v.replace("\"", "\"\"")}\"" else v

/** Número para CSV: 93 → "93", 1234.5 → "1234.5" (como String(n) en JS). */
fun numeroCsv(n: Double): String {
    val cent = (n * 100).roundToLong()
    val ent = cent / 100
    val dec = abs(cent % 100)
    if (dec == 0L) return ent.toString()
    val signo = if (cent < 0 && ent == 0L) "-" else ""
    val d = dec.toString().padStart(2, '0').trimEnd('0')
    return "$signo$ent.$d"
}

/**
 * Una fila por mes con todo junto: es lo que la clínica lleva al contador. El
 * estado ('En curso') va en su columna para que nadie sume un mes a medias con
 * meses cerrados. Separador ';' y BOM: Excel en español lo abre bien.
 */
fun csvReporteMensual(s: SeriesReporte, conDinero: Boolean, etiquetaPacientes: String = "Pacientes"): String {
    // Ingresos/egresos solo con permiso de finanzas (la misma regla que los gráficos de dinero).
    val cab = listOf("Mes", "Estado", etiquetaPacientes, "Citas atendidas", "Sesiones") +
        (if (conDinero) listOf("Ingresos", "Egresos") else emptyList()) + "Qué pasó"
    val filas = s.pacientes.mapIndexed { i, p ->
        listOf(
            p.mes,
            if (p.parcial) "En curso" else "Cerrado",
            numeroCsv(p.valor),
            numeroCsv(s.citas.getOrNull(i)?.valor ?: 0.0),
            numeroCsv(s.sesiones.getOrNull(i)?.valor ?: 0.0),
        ) + (
            if (conDinero) listOf(
                numeroCsv(s.ingresos.getOrNull(i)?.valor ?: 0.0),
                numeroCsv(s.egresos.getOrNull(i)?.valor ?: 0.0),
            ) else emptyList()
            ) + s.hitos.filter { it.mes == p.mes }.joinToString(" / ") { it.titulo }
    }
    val lineas = listOf(cab) + filas
    return "﻿" + lineas.joinToString("\n") { fila -> fila.joinToString(";") { celdaCsv(it) } }
}

/** reporte-mensual-2026-10-08.csv (la fecha es la de la clínica). */
fun nombreCsvReporte(hoy: String?): String = "reporte-mensual${hoy?.let { "-$it" } ?: ""}.csv"
