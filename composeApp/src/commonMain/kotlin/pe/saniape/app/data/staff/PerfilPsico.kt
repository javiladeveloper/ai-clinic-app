package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// ─────────────────────────────────────────────────────────────────────────────
// EVALUACIÓN PSICOLÓGICA — FASE 3: PERFIL de puntajes y RETEST
// (contrato §14.2 y §14.3; web: lib/perfil-psico.ts).
//
// El perfil es solo DIBUJO de lo que ya trae el test (los puntajes que calculó
// el servidor o que escribió la psicóloga): misma regla que perfilDeTest. Si el
// servidor manda perfiles hechos (anexo del informe), se usan esos.
// La comparación del retest la hace el servidor (GET /retest): aquí solo se lee.
// ─────────────────────────────────────────────────────────────────────────────

data class BandaPerfilPsico(val desde: Double, val hasta: Double, val nombre: String, val nivel: Int)

data class BarraPerfilPsico(
    val escala: String,
    val valor: Double,
    val min: Double,
    val max: Double,
    /** "12 / 27", "T 65", "CI 98". */
    val texto: String,
    val categoria: String = "",
    /** 0 sin hallazgo … 4 grave (color); null = barra neutra. */
    val nivel: Int? = null,
)

data class EjePerfilPsico(val min: Double, val max: Double)

data class PerfilTestPsico(
    val testAplicadoId: String,
    val titulo: String,
    val fecha: String = "",
    val unidad: String = "",
    /** Eje común; null = cada barra en % de su máximo (escalas con rangos distintos). */
    val eje: EjePerfilPsico? = null,
    /** Media de la escala normalizada (T 50, CI 100…). */
    val media: Double? = null,
    val bandas: List<BandaPerfilPsico> = emptyList(),
    val barras: List<BarraPerfilPsico> = emptyList(),
    val instrumento: String? = null,
)

private data class EscalaNormalizadaPsico(
    val unidad: String, val prefijo: String, val min: Double, val max: Double, val media: Double?,
    val bandas: List<BandaPerfilPsico>, val percentil: Boolean = false,
)

private fun b(d: Int, h: Int, n: String, nivel: Int) = BandaPerfilPsico(d.toDouble(), h.toDouble(), n, nivel)

/** Bandas DESCRIPTIVAS de uso general (no son baremos de ningún test): copia de ESCALAS_NORMALIZADAS. */
private val ESCALAS_NORMALIZADAS_PSICO: Map<String, EscalaNormalizadaPsico> = mapOf(
    "T" to EscalaNormalizadaPsico("Puntaje T", "T", 20.0, 100.0, 50.0, listOf(
        b(20, 39, "Bajo", 1), b(40, 59, "Promedio", 0), b(60, 69, "Alto", 2), b(70, 100, "Muy alto", 3))),
    "CI" to EscalaNormalizadaPsico("CI / índice", "CI", 40.0, 160.0, 100.0, listOf(
        b(40, 69, "Muy bajo", 4), b(70, 79, "Limítrofe", 3), b(80, 89, "Promedio bajo", 1), b(90, 109, "Promedio", 0),
        b(110, 119, "Promedio alto", 0), b(120, 129, "Superior", 0), b(130, 160, "Muy superior", 0))),
    "escalar" to EscalaNormalizadaPsico("Puntaje escalar", "Esc.", 1.0, 19.0, 10.0, listOf(
        b(1, 3, "Muy bajo", 4), b(4, 5, "Limítrofe", 3), b(6, 7, "Promedio bajo", 1), b(8, 12, "Promedio", 0),
        b(13, 14, "Promedio alto", 0), b(15, 19, "Superior", 0))),
    "percentil" to EscalaNormalizadaPsico("Percentil", "Pc", 0.0, 100.0, 50.0, listOf(
        b(0, 9, "Bajo", 3), b(10, 24, "Promedio bajo", 1), b(25, 75, "Promedio", 0), b(76, 90, "Promedio alto", 0),
        b(91, 100, "Alto", 0)), percentil = true),
    "decatipo" to EscalaNormalizadaPsico("Decatipo", "Dec.", 1.0, 10.0, 5.5, listOf(
        b(1, 3, "Bajo", 1), b(4, 7, "Medio", 0), b(8, 10, "Alto", 1))),
    "BR" to EscalaNormalizadaPsico("Tasa base (BR)", "BR", 0.0, 115.0, null, listOf(
        b(0, 74, "Sin indicador", 0), b(75, 84, "Presencia del rasgo", 2), b(85, 115, "Prominente", 4))),
)

/** El primer número de un texto de puntaje: "T 65" → 65; "112 (Medio)" → 112; "7,5" → 7.5. */
fun numeroDePuntaje(texto: String?): Double? {
    val m = Regex("-?\\d+(?:[.,]\\d+)?").find(texto.orEmpty()) ?: return null
    return m.value.replace(',', '.').toDoubleOrNull()
}

private fun bandaDe(bandas: List<BandaPerfilPsico>, v: Double) = bandas.firstOrNull { v >= it.desde && v <= it.hasta }

/**
 * Perfil de un test puntuado con un instrumento libre. El VALOR y la
 * CATEGORÍA de cada escala salen SIEMPRE de `puntajes` del test (lo que la
 * psicóloga dejó, quizá corregido a mano: el informe no puede contradecirla);
 * del instrumento solo se usan el rango, las bandas y los cortes (color). Las
 * escalas son las de la definición (total y subescalas); sin definiciones a
 * mano (no llegaron), las del resultado del servidor.
 */
private fun perfilInstrumento(t: TestAplicadoPsico, r: RespuestasTestPsico, ins: InstrumentoPsico?): PerfilTestPsico? {
    val k = { x: String -> x.trim().lowercase() }
    val filas = t.puntajes.filter { it.escala.isNotBlank() }.associateBy { k(it.escala) }
    data class Escala(val nombre: String, val min: Double, val max: Double, val cortes: List<BandaCortePsico>, val nivelServidor: Int?, val puntajeServidor: Double?)
    val escalas: List<Escala> = if (ins != null) {
        (listOfNotNull(ins.total) + ins.subescalas).map { e ->
            Escala(e.nombre, e.min, e.max, r.sexo?.let { e.bandasSexo?.get(it) } ?: e.bandas, null, null)
        }
    } else {
        val res = r.resultado ?: return null
        (listOfNotNull(res.total) + res.subescalas).map { Escala(it.nombre, it.min, it.max, emptyList(), it.nivel, it.puntaje) }
    }
    val conBandas = escalas.mapNotNull { e ->
        val fila = filas[k(e.nombre)] ?: return@mapNotNull null
        val v = numeroDePuntaje(fila.directo) ?: return@mapNotNull null
        val bandas = e.cortes.map { BandaPerfilPsico(it.min, it.max, it.categoria, it.nivel) }
        val banda = bandaDe(bandas, v)
        // Sin definiciones: el nivel del servidor vale solo si el valor no se corrigió.
        val nivel = banda?.nivel ?: e.nivelServidor?.takeIf { e.puntajeServidor == v }
        val texto = textoPuntajeEscala(ResultadoEscalaPsico(puntaje = v, min = e.min, max = e.max))
        BarraPerfilPsico(e.nombre, v, e.min, e.max, texto, fila.categoria.trim().ifBlank { banda?.nombre.orEmpty() }, nivel) to bandas
    }
    if (conBandas.isEmpty()) return null
    val barras = conBandas.map { it.first }
    val mismoRango = barras.all { it.min == barras[0].min && it.max == barras[0].max }
    val modo = ins?.modo ?: "suma"
    return PerfilTestPsico(
        testAplicadoId = t.id, titulo = ins?.corto ?: t.nombreCorto, fecha = t.fecha,
        unidad = if (modo == "positivos") "Ítems positivos" else "Puntaje directo",
        eje = if (mismoRango) EjePerfilPsico(barras[0].min, barras[0].max) else null,
        // Las bandas de fondo solo con una escala.
        media = null, bandas = if (barras.size == 1) conBandas[0].second else emptyList(), barras = barras, instrumento = r.instrumento,
    )
}

/**
 * El perfil de un test aplicado (perfilDeTest de la web), o null si no hay nada
 * que graficar (cualitativo o sin puntajes numéricos). [instrumentos] solo da
 * las bandas y el nombre corto; los números son los del test.
 */
fun perfilDeTest(t: TestAplicadoPsico, instrumentos: List<InstrumentoPsico>? = null): PerfilTestPsico? {
    val r = t.respuestas
    if (r != null) {
        // Sin filas con número (renombradas o vacías), se grafica como cualquier otro test.
        perfilInstrumento(t, r, instrumentos?.firstOrNull { it.id == r.instrumento })?.let { return it }
    }
    val tipo = t.test?.tipoPuntaje.orEmpty()
    if (tipo.isBlank() || tipo == "cualitativo") return null
    val filas = t.puntajes.filter { it.escala.isNotBlank() }
    val norm = ESCALAS_NORMALIZADAS_PSICO[tipo]
    if (norm != null) {
        val barras = filas.mapNotNull { p ->
            val crudo = if (norm.percentil) p.percentil.ifBlank { p.transformado } else p.transformado
            val v = numeroDePuntaje(crudo) ?: return@mapNotNull null
            val banda = bandaDe(norm.bandas, v)
            BarraPerfilPsico(p.escala.trim(), v, norm.min, norm.max, "${norm.prefijo} ${numeroPsico(v)}",
                p.categoria.trim().ifBlank { banda?.nombre.orEmpty() }, banda?.nivel)
        }
        if (barras.isEmpty()) return null
        // Un valor fuera del rango típico agranda el eje en vez de cortarse.
        val min = minOf(norm.min, barras.minOf { it.valor })
        val max = maxOf(norm.max, barras.maxOf { it.valor })
        return PerfilTestPsico(
            testAplicadoId = t.id, titulo = t.nombreCorto, fecha = t.fecha, unidad = norm.unidad,
            eje = EjePerfilPsico(min, max), media = norm.media, bandas = norm.bandas,
            barras = barras.map { it.copy(min = min, max = max) },
        )
    }
    // Puntaje directo sin autocálculo: sin media ni bandas (no hay baremo).
    val pares = filas.mapNotNull { p -> numeroDePuntaje(p.directo)?.let { p to it } }
    if (pares.isEmpty()) return null
    val max = maxOf(pares.maxOf { it.second }, 1.0)
    val min = minOf(0.0, pares.minOf { it.second })
    return PerfilTestPsico(
        testAplicadoId = t.id, titulo = t.nombreCorto, fecha = t.fecha, unidad = "Puntaje directo",
        eje = EjePerfilPsico(min, max),
        barras = pares.map { (p, v) -> BarraPerfilPsico(p.escala.trim(), v, min, max, numeroPsico(v), p.categoria.trim()) },
    )
}

// ── Geometría (fracciones 0..1 del ancho), igual que geometriaPerfil ──────────

data class FilaGeometriaPsico(val etiqueta: String, val texto: String, val categoria: String, val desde: Float, val hasta: Float, val nivel: Int?)
data class BandaGeometriaPsico(val desde: Float, val hasta: Float, val nombre: String, val nivel: Int)
data class TickPsico(val f: Float, val texto: String)

data class GeometriaPerfilPsico(
    val filas: List<FilaGeometriaPsico>,
    val bandas: List<BandaGeometriaPsico>,
    val media: Float?,
    val ticks: List<TickPsico>,
    /** Sin eje común: cada barra en % del máximo de su escala. */
    val porcentaje: Boolean,
)

private fun recortar(f: Double): Float = f.coerceIn(0.0, 1.0).toFloat()

fun geometriaPerfil(p: PerfilTestPsico): GeometriaPerfilPsico {
    val eje = p.eje
    if (eje != null) {
        val span = (eje.max - eje.min).takeIf { it != 0.0 } ?: 1.0
        fun f(v: Double) = recortar((v - eje.min) / span)
        val paso = when {
            span <= 12 -> 1.0
            span <= 30 -> 5.0
            span <= 60 -> 10.0
            else -> 20.0
        }
        val ticks = mutableListOf<TickPsico>()
        var v = kotlin.math.ceil(eje.min / paso) * paso
        while (v <= eje.max + 1e-9) { ticks += TickPsico(f(v), numeroPsico(v)); v += paso }
        return GeometriaPerfilPsico(
            filas = p.barras.map { FilaGeometriaPsico(it.escala, it.texto, it.categoria, 0f, f(it.valor), it.nivel) },
            // Cada banda va de su "desde" al "desde" de la siguiente (sin huecos entre enteros).
            bandas = p.bandas.mapIndexed { i, bd -> BandaGeometriaPsico(f(bd.desde), f(p.bandas.getOrNull(i + 1)?.desde ?: eje.max), bd.nombre, bd.nivel) },
            media = p.media?.let { f(it) },
            ticks = ticks,
            porcentaje = false,
        )
    }
    return GeometriaPerfilPsico(
        filas = p.barras.map {
            val span = (it.max - it.min).takeIf { s -> s != 0.0 } ?: 1.0
            FilaGeometriaPsico(it.escala, it.texto, it.categoria, 0f, recortar((it.valor - it.min) / span), it.nivel)
        },
        bandas = emptyList(), media = null,
        ticks = listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map { TickPsico(it, "${(it * 100).toInt()}%") },
        porcentaje = true,
    )
}

/** Leyenda en texto (vista equivalente accesible): unidad, media y bandas. */
fun leyendaPerfil(p: PerfilTestPsico): String = listOf(
    if (p.eje == null) "Cada barra, en % del máximo de su escala" else p.unidad,
    p.media?.let { "línea punteada = media (${numeroPsico(it)})" }.orEmpty(),
    if (p.bandas.isNotEmpty()) "bandas: " + p.bandas.joinToString(", ") { "${it.nombre} ${numeroPsico(it.desde)}–${numeroPsico(it.hasta)}" } else "",
).filter { it.isNotBlank() }.joinToString(" · ")

/** Colores por nivel (validados para daltonismo en la web) y la barra neutra, ARGB. */
val COLORES_NIVEL_PSICO: List<Long> = listOf(0xFF1BAF7A, 0xFFD4A72C, 0xFFEB6834, 0xFFD9480F, 0xFFB91C1C)
const val COLOR_BARRA_PSICO: Long = 0xFF3B5BBF
fun colorBarraPsico(nivel: Int?): Long = if (nivel == null) COLOR_BARRA_PSICO else COLORES_NIVEL_PSICO[nivel.coerceIn(0, 4)]

// ── Lectura de perfiles que manda el servidor (anexo del informe) ────────────

private fun JsonObject?.txtP(k: String): String? =
    (this?.get(k) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.takeIf { it != "null" }

private fun JsonObject?.numP(k: String): Double? = txtP(k)?.trim()?.replace(',', '.')?.toDoubleOrNull()

private fun JsonObject?.listaP(k: String): List<JsonElement> = (this?.get(k) as? JsonArray).orEmpty()

/** Un PerfilTest del servidor (anexoPerfiles.perfiles). Sin barras válidas → null. */
internal fun leerPerfilTest(o: JsonObject?): PerfilTestPsico? {
    if (o == null) return null
    val barras = o.listaP("barras").mapNotNull { x ->
        val bo = x as? JsonObject ?: return@mapNotNull null
        val v = bo.numP("valor") ?: return@mapNotNull null
        BarraPerfilPsico(
            escala = bo.txtP("escala").orEmpty(), valor = v, min = bo.numP("min") ?: 0.0, max = bo.numP("max") ?: 0.0,
            texto = bo.txtP("texto") ?: numeroPsico(v), categoria = bo.txtP("categoria").orEmpty(),
            nivel = bo.numP("nivel")?.toInt()?.coerceIn(0, 4),
        )
    }
    if (barras.isEmpty()) return null
    val eje = (o["eje"] as? JsonObject)?.let { e ->
        val mn = e.numP("min"); val mx = e.numP("max")
        if (mn != null && mx != null) EjePerfilPsico(mn, mx) else null
    }
    return PerfilTestPsico(
        testAplicadoId = o.txtP("testAplicadoId").orEmpty(),
        titulo = o.txtP("titulo") ?: "Test",
        fecha = o.txtP("fecha").orEmpty().take(10),
        unidad = o.txtP("unidad").orEmpty(),
        eje = eje,
        media = o.numP("media"),
        bandas = o.listaP("bandas").mapNotNull { x ->
            val bo = x as? JsonObject ?: return@mapNotNull null
            val d = bo.numP("desde") ?: return@mapNotNull null
            val h = bo.numP("hasta") ?: return@mapNotNull null
            BandaPerfilPsico(d, h, bo.txtP("nombre").orEmpty(), (bo.numP("nivel") ?: 0.0).toInt().coerceIn(0, 4))
        },
        barras = barras,
        instrumento = o.txtP("instrumento"),
    )
}

// ── Retest (GET /retest) ─────────────────────────────────────────────────────

data class AplicacionResumenPsico(val id: String, val fecha: String = "", val forma: String? = null, val baremo: String? = null, val global: String = "")

data class FilaComparacionPsico(
    val escala: String,
    val antes: Double? = null,
    val despues: Double? = null,
    /** después − antes */
    val diferencia: Double? = null,
    val categoriaAntes: String = "",
    val categoriaDespues: String = "",
    /** 'mejora' | 'empeora' | 'igual' — SOLO si el servidor lo indica (mismo instrumento libre). */
    val sentido: String? = null,
    /** Supera el cambio clínicamente relevante (hoy solo PHQ-9: 5 puntos). */
    val relevante: Boolean? = null,
)

data class ComparacionPsico(
    val anterior: AplicacionResumenPsico,
    val actual: AplicacionResumenPsico,
    val diasEntre: Int? = null,
    val unidad: String = "",
    val filas: List<FilaComparacionPsico> = emptyList(),
    /** "Compara con cautela" (otra forma, otro baremo, otra variante). */
    val avisos: List<String> = emptyList(),
)

data class RetestPsico(
    val actual: TestAplicadoPsico?,
    /** Del mismo test y paciente, de la más reciente a la más vieja. */
    val anteriores: List<TestAplicadoPsico>,
    /** null = no hay aplicaciones anteriores. */
    val comparacion: ComparacionPsico?,
)

private fun leerResumen(o: JsonObject?): AplicacionResumenPsico? {
    val id = o.txtP("id") ?: return null
    return AplicacionResumenPsico(id, o.txtP("fecha").orEmpty().take(10), o.txtP("forma")?.ifBlank { null },
        o.txtP("baremo")?.ifBlank { null }, o.txtP("global").orEmpty())
}

internal fun leerComparacion(o: JsonObject?): ComparacionPsico? {
    if (o == null) return null
    val anterior = leerResumen(o["anterior"] as? JsonObject) ?: return null
    val actual = leerResumen(o["actual"] as? JsonObject) ?: return null
    return ComparacionPsico(
        anterior = anterior,
        actual = actual,
        diasEntre = o.numP("diasEntre")?.toInt(),
        unidad = o.txtP("unidad").orEmpty(),
        filas = o.listaP("filas").mapNotNull { x ->
            val f = x as? JsonObject ?: return@mapNotNull null
            val escala = f.txtP("escala")?.ifBlank { null } ?: return@mapNotNull null
            FilaComparacionPsico(
                escala = escala,
                antes = f.numP("antes"),
                despues = f.numP("despues"),
                diferencia = f.numP("diferencia"),
                categoriaAntes = f.txtP("categoriaAntes").orEmpty(),
                categoriaDespues = f.txtP("categoriaDespues").orEmpty(),
                sentido = f.txtP("sentido")?.takeIf { it in setOf("mejora", "empeora", "igual") },
                relevante = when (f.txtP("relevante")) { "true" -> true; "false" -> false; else -> null },
            )
        },
        avisos = o.listaP("avisos").mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.contentOrNull?.ifBlank { null } },
    )
}

/** `{ ok, actual, anteriores, comparacion }` del retest. */
internal fun parsearRetestPsico(o: JsonObject?): RetestPsico = RetestPsico(
    actual = leerTestAplicado(o?.get("actual") as? JsonObject),
    anteriores = o.listaP("anteriores").mapNotNull { leerTestAplicado(it as? JsonObject) },
    comparacion = leerComparacion(o?.get("comparacion") as? JsonObject),
)

/** La columna "Cambio" solo si alguna fila trae sentido (como la web). */
fun mostrarCambioRetest(c: ComparacionPsico?): Boolean = c?.filas?.any { it.sentido != null } == true

/** "+3" / "-7" / "0" / "—". */
fun textoDiferenciaPsico(d: Double?): String = when {
    d == null -> "—"
    d > 0 -> "+${numeroPsico(d)}"
    else -> numeroPsico(d)
}

/**
 * "▼ mejora · clínicamente relevante". La flecha va por el signo de la
 * diferencia (en Rosenberg o APGAR, mejorar es subir); la palabra la da el
 * servidor. null si el servidor no indicó sentido.
 */
fun textoSentidoPsico(f: FilaComparacionPsico): String? {
    val s = f.sentido ?: return null
    val flecha = when {
        s == "igual" -> "="
        (f.diferencia ?: 0.0) > 0 -> "▲"
        else -> "▼"
    }
    return "$flecha $s${if (f.relevante == true) " · clínicamente relevante" else ""}"
}

/** Opción del selector de aplicaciones anteriores: "01/07/2026 · 15 / 27 · Depresión…". */
fun etiquetaAplicacionPsico(t: TestAplicadoPsico): String =
    listOf(fechaDmyPsico(t.fecha), t.global.puntaje, t.global.categoria).filter { it.isNotBlank() }.joinToString(" · ")
