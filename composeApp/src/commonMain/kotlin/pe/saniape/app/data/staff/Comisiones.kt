package pe.saniape.app.data.staff

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// ── Lo que devuelve /api/staff/comision-plantillas (gemelo de PlantillasPanel) ──

/** Un nivel de la pirámide. El monto del bono SOLO llega al Admin. */
@Serializable
data class TramoComision(val nombre: String = "", val objetivo: Double = 0.0, val monto_bono: Double? = null)

@Serializable
data class PagoComisionRef(val id: String = "", val monto: Double = 0.0, val fecha: String = "", val nivel: String? = null)

@Serializable
data class UltimoPagoComision(val id: String = "", val monto: Double = 0.0, val fecha: String = "", val hastaCorte: String? = null)

/** Cómo va UNA persona en un esquema. Los montos (aCobrar, pagado…) solo vienen para el Admin. */
@Serializable
data class AvanceComision(
    val terapeutaId: String? = null,
    /** Miembro del equipo sin ficha de profesional (recepción). */
    val perfilId: String? = null,
    val clave: String? = null,
    val nombre: String = "—",
    val logrado: Double = 0.0,
    val paquetesQueCuentan: Int = 0,
    val nivelActual: String? = null,
    val siguienteNivel: String? = null,
    val faltanParaSiguiente: Int = 0,
    val progreso: Double = 0.0,
    val texto: String = "",
    val aCobrar: Double? = null,
    val pagado: PagoComisionRef? = null,
    val ultimoPago: UltimoPagoComision? = null,
    val porcentaje: Double? = null,
    val liquidacion: String? = null,
    val metodoPago: String? = null,
)

@Serializable
data class EsquemaComision(
    val id: String,
    val nombre: String = "",
    val descripcion: String? = null,
    /** 'tramos' (pirámide) | 'porcentaje' | 'bono' (viejo). */
    val tipo: String = "tramos",
    /** 'paquetes' | 'evaluaciones' | 'sesiones' | 'monto'. */
    val unidad: String = "paquetes",
    val minSesiones: Int? = null,
    val maxSesiones: Int? = null,
    val soloPacientesNuevos: Boolean = false,
    val montoPaquete: Double? = null,
    val etiquetaRango: String = "",
    val periodoMeses: Int = 1,
    val porcentaje: Double? = null,
    val desde: String = "",
    val hasta: String = "",
    val tramos: List<TramoComision> = emptyList(),
    val avances: List<AvanceComision> = emptyList(),
)

@Serializable
data class EsquemasComision(
    val plantillas: List<EsquemaComision> = emptyList(),
    val esAdmin: Boolean = false,
    /** El mes que calculó el servidor ('YYYY-MM'): es el que viaja al pagar. */
    val periodoRef: String? = null,
    val esPeriodoActual: Boolean = true,
)

// ── Detalle de una pirámide (/api/staff/comision-plantillas/detalle) ──

@Serializable
data class SesionDetalleComision(val id: String = "", val numero: Int? = null, val fecha: String? = null, val hora: String? = null, val estado: String? = null)

@Serializable
data class PagoDetalleComision(val id: String = "", val fecha: String = "", val monto: Double = 0.0, val metodo: String? = null)

@Serializable
data class TratamientoDetalleComision(
    val id: String = "",
    val servicio: String = "Tratamiento",
    val totalSesiones: Int = 0,
    val sesionesCompletadas: Int = 0,
    val monto: Double = 0.0,
    val estado: String = "—",
    val estadoPago: String? = null,
    val fechaCuenta: String? = null,
    val fechaInicio: String? = null,
    val sesiones: List<SesionDetalleComision> = emptyList(),
    val pagos: List<PagoDetalleComision> = emptyList(),
)

@Serializable
data class PacienteDetalleComision(val id: String = "", val nombre: String = "", val tratamientos: List<TratamientoDetalleComision> = emptyList()) {
    /** Total acordado de sus paquetes (gemelo de totalPaciente de lib/comision-detalle.ts). */
    val total: Double get() = tratamientos.sumOf { it.monto }
}

@Serializable
data class DetalleComision(val desde: String = "", val hasta: String = "", val totalPaquetes: Int = 0, val pacientes: List<PacienteDetalleComision> = emptyList())

// ── Histórico (/api/staff/comision-plantillas/historico, solo Admin) ──

@Serializable
data class FilaHistoricoComision(
    val id: String = "",
    /** 'sistema' = liquidado en Sania · 'caja' = egreso registrado a mano. */
    val origen: String = "sistema",
    val fecha: String = "",
    val monto: Double = 0.0,
    val terapeutaId: String? = null,
    val profesional: String = "—",
    val esquema: String = "",
    val nivel: String? = null,
    val detalle: String? = null,
    val periodo: String? = null,
    val metodo: String? = null,
)

@Serializable
data class MontoMesComision(val mes: String = "", val monto: Double = 0.0)

@Serializable
data class MontoProfesionalComision(val nombre: String = "", val monto: Double = 0.0)

@Serializable
data class HistoricoComisiones(
    val filas: List<FilaHistoricoComision> = emptyList(),
    val total: Double = 0.0,
    val porMes: List<MontoMesComision> = emptyList(),
    val porProfesional: List<MontoProfesionalComision> = emptyList(),
)

/** Personal que se puede asignar a un esquema (profesionales activos). */
data class PersonaComision(val id: String, val nombre: String, val perfilId: String? = null)

/** Miembro del equipo sin ficha de profesional (recepción y otros). */
data class MiembroEquipoComision(val id: String, val nombre: String, val rol: String)

// ── Formulario de esquema (gemelo de FormPlantilla) ──

data class TramoForm(val nombre: String = "", val objetivo: String = "", val montoBono: String = "")

/** Lo pactado con una persona dentro de un esquema. */
data class AjusteForm(val porcentaje: String = "", val liquidacion: String = "aprobar", val metodoPago: String = "")

data class FormEsquema(
    val id: String? = null,
    val tipo: String = "tramos",
    val porcentaje: String = "",
    val nombre: String = "",
    val minSes: String = "",
    val maxSes: String = "",
    val soloNuevos: Boolean = false,
    val montoPaq: String = "",
    val periodoMeses: String = "1",
    val tramos: List<TramoForm> = ReglasComisiones.NIVELES_SUGERIDOS.map { TramoForm(nombre = it) },
    val unidad: String = "paquetes",
    val asignados: List<String> = emptyList(),
    val asignadosEquipo: List<String> = emptyList(),
    val ajustes: Map<String, AjusteForm> = emptyMap(),
) {
    val porEvaluaciones: Boolean get() = tipo == "tramos" && unidad == "evaluaciones"
    fun ajusteDe(id: String): AjusteForm = ajustes[id] ?: AjusteForm()

    companion object {
        /** Estado inicial: vacío (nuevo) o lo que ya tiene el esquema (editar), como la web. */
        fun desde(p: EsquemaComision?): FormEsquema {
            if (p == null) return FormEsquema()
            fun num(d: Double?): String = d?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: ""
            return FormEsquema(
                id = p.id,
                tipo = if (p.tipo == "porcentaje") "porcentaje" else "tramos",
                porcentaje = num(p.porcentaje),
                nombre = p.nombre,
                minSes = p.minSesiones?.toString() ?: "",
                maxSes = p.maxSesiones?.toString() ?: "",
                soloNuevos = p.soloPacientesNuevos,
                montoPaq = num(p.montoPaquete),
                periodoMeses = p.periodoMeses.toString(),
                tramos = if (p.tramos.isNotEmpty()) p.tramos.map { TramoForm(it.nombre, num(it.objetivo), num(it.monto_bono)) }
                else ReglasComisiones.NIVELES_SUGERIDOS.map { TramoForm(nombre = it) },
                unidad = if (p.unidad == "evaluaciones") "evaluaciones" else "paquetes",
                asignados = p.avances.mapNotNull { it.terapeutaId },
                asignadosEquipo = p.avances.mapNotNull { it.perfilId },
                ajustes = p.avances.filter { it.terapeutaId != null }.associate { a ->
                    a.terapeutaId!! to AjusteForm(
                        porcentaje = num(a.porcentaje),
                        liquidacion = if (a.liquidacion == "egreso") "egreso" else "aprobar",
                        metodoPago = a.metodoPago ?: "",
                    )
                },
            )
        }
    }
}

/** Un tramo de la barra de la pirámide, ya calculado. */
data class SegmentoPiramide(val nombre: String, val peso: Float, val llenado: Float, val alcanzado: Boolean, val indice: Int)

data class OpcionPeriodo(val valor: String?, val etiqueta: String)

/**
 * Reglas de PRESENTACIÓN de Comisiones, puras y testeables. Los montos, niveles
 * y lo que se puede pagar los calcula el servidor (lib/comision-tramos.ts): acá
 * solo se arma lo que se muestra y el cuerpo que se envía, igual que la web.
 */
object ReglasComisiones {

    val NIVELES_SUGERIDOS = listOf("Bronce", "Plata", "Oro", "Diamante")
    val METODOS_BASE = listOf("Efectivo", "Yape", "Plin", "BCP", "Transferencia", "Otro")

    private val MESES = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
        "agosto", "septiembre", "octubre", "noviembre", "diciembre")

    /** La persona dentro del esquema (profesional o miembro del equipo). */
    fun claveDe(a: AvanceComision): String = a.clave ?: a.terapeutaId ?: "perfil:${a.perfilId}"

    /** Qué cuenta un esquema, en plural. */
    fun unidadTxt(u: String): String = when (u) { "evaluaciones" -> "evaluaciones"; "sesiones" -> "sesiones"; else -> "paquetes" }

    private fun capitalizar(s: String) = s.replaceFirstChar { it.uppercase() }

    /** 'YYYY-MM' → (año, mes). */
    private fun anioMes(iso: String): Pair<Int, Int>? {
        val p = iso.take(10).split("-")
        val a = p.getOrNull(0)?.toIntOrNull() ?: return null
        val m = p.getOrNull(1)?.toIntOrNull() ?: return null
        return a to m
    }

    /** Mes en curso y los dos anteriores (DALU paga los bonos a la quincena del mes siguiente). null = en curso. */
    fun mesesElegibles(hoyIso: String): List<OpcionPeriodo> {
        val (a, m) = anioMes(hoyIso) ?: return listOf(OpcionPeriodo(null, "Mes en curso"))
        return (0..2).map { atras ->
            var mm = m - atras; var aa = a
            while (mm < 1) { mm += 12; aa -= 1 }
            val etiqueta = "${capitalizar(MESES[mm - 1])} $aa"
            if (atras == 0) OpcionPeriodo(null, "$etiqueta (en curso)")
            else OpcionPeriodo("$aa-${mm.toString().padStart(2, '0')}", etiqueta)
        }
    }

    private fun esBisiesto(a: Int) = (a % 4 == 0 && a % 100 != 0) || a % 400 == 0
    private fun diasDelMes(a: Int, m: Int) = when (m) { 2 -> if (esBisiesto(a)) 29 else 28; 4, 6, 9, 11 -> 30; else -> 31 }

    /** 'hasta' viene EXCLUIDO ('2026-10-01'): para mostrar, el día anterior. */
    fun diaAnterior(iso: String): String {
        val p = iso.take(10).split("-").mapNotNull { it.toIntOrNull() }
        if (p.size != 3) return iso
        var (a, m, d) = Triple(p[0], p[1], p[2] - 1)
        if (d < 1) { m -= 1; if (m < 1) { m = 12; a -= 1 }; d = diasDelMes(a, m) }
        return "$a-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
    }

    /** "1 – 30 de septiembre" (o "1 de julio – 30 de septiembre" si cruza meses). */
    fun rangoHumano(desde: String, hasta: String): String {
        val p1 = desde.take(10).split("-").mapNotNull { it.toIntOrNull() }
        val p2 = diaAnterior(hasta).split("-").mapNotNull { it.toIntOrNull() }
        if (p1.size != 3 || p2.size != 3) return "$desde – $hasta"
        val (y1, m1, d1) = p1; val (y2, m2, d2) = p2
        if (y1 == y2 && m1 == m2) return "$d1 – $d2 de ${MESES[m1 - 1]}"
        val otroAnio = y1 != y2
        return "$d1 de ${MESES[m1 - 1]}${if (otroAnio) " de $y1" else ""} – $d2 de ${MESES[m2 - 1]}${if (otroAnio) " de $y2" else ""}"
    }

    private fun numTxt(n: Double): String = if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()

    /** Subtítulo de la tarjeta del esquema (Admin), como la web. */
    fun subtituloEsquema(p: EsquemaComision): String = buildString {
        append(if (p.unidad == "evaluaciones") "Evaluaciones atendidas" else p.etiquetaRango)
        p.montoPaquete?.let { append(" · solo de ${simboloActivo()} ${numTxt(it)}") }
        if (p.soloPacientesNuevos) append(" · solo pacientes nuevos")
        append(" · ")
        append(if (p.periodoMeses == 1) "cada mes" else "cada ${p.periodoMeses} meses")
    }

    /** Los rangos distintos entre los esquemas (uno solo = se dice una vez arriba). */
    fun rangosDistintos(lista: List<EsquemaComision>): List<Pair<String, String>> =
        lista.map { it.desde to it.hasta }.distinct()

    /** Lo pendiente de pagar DESDE ACÁ: a quien le sale sola al cobrar ya se le pagó en cada cobro. */
    fun totalAPagar(lista: List<EsquemaComision>): Double =
        lista.sumOf { p -> p.avances.sumOf { a -> if (a.pagado != null || a.liquidacion == "egreso") 0.0 else (a.aCobrar ?: 0.0) } }

    /** ¿Se le ofrece "Pagar"? A quien le sale sola al cobrar NO (se le pagaría dos veces). */
    fun puedePagar(a: AvanceComision): Boolean =
        a.liquidacion != "egreso" && a.pagado == null && (a.aCobrar ?: 0.0) > 0.0

    /** Texto del enlace de anular: un corte de arranque en 0 no es "un pago de S/ 0.00". */
    fun textoAnular(u: UltimoPagoComision): String =
        (if (u.monto > 0) "Anular el pago de ${dineroActivo(u.monto)}" else "Deshacer el corte") +
            (u.hastaCorte?.let { " (al $it)" } ?: "")

    /** Lo que se ve grande en la fila del Admin: su % (esquema por porcentaje) o su nivel. */
    fun titularAvance(p: EsquemaComision, a: AvanceComision): String =
        if (p.tipo == "porcentaje") "${numTxt(a.porcentaje ?: 0.0)}%" else (a.nivelActual ?: "—")

    /** Lo que se ve debajo del titular (Admin). */
    fun montoAvance(p: EsquemaComision, a: AvanceComision): String = when {
        a.pagado != null -> "✓ pagado ${dineroActivo(a.pagado.monto)}"
        (a.aCobrar ?: 0.0) > 0 -> dineroActivo(a.aCobrar ?: 0.0)
        p.tipo == "porcentaje" -> "nada acumulado aún"
        else -> "sin bono aún"
    }

    /** "12 paquetes que cuentan · faltan 3 para Oro" (Admin). */
    fun lineaAvance(p: EsquemaComision, a: AvanceComision): String = buildString {
        append("${numTxt(a.logrado)} ${unidadTxt(p.unidad)} ${if (p.unidad == "evaluaciones") "atendidas" else "que cuentan"}")
        a.siguienteNivel?.let { append(" · faltan ${a.faltanParaSiguiente} para $it") }
    }

    /** Lo que ve el profesional bajo su nivel (sin montos). */
    fun lineaProfesional(p: EsquemaComision, a: AvanceComision, esPeriodoActual: Boolean): String =
        if (p.tipo == "tramos") {
            "${numTxt(a.logrado)} ${unidadTxt(p.unidad)}" +
                if (p.unidad == "evaluaciones") " atendidas" else " · ${p.etiquetaRango.lowercase()}"
        } else "${unidadTxt(p.unidad)} ${if (esPeriodoActual) "este período" else "en ese período"}"

    fun titularProfesional(p: EsquemaComision, a: AvanceComision): String =
        if (p.tipo == "tramos") (a.nivelActual ?: "—") else numTxt(a.logrado)

    /**
     * La pirámide como barra segmentada (gemelo de <Piramide>): cada nivel ocupa
     * lo que mide su tramo, y se llena según lo logrado DENTRO de ese tramo.
     */
    fun segmentosPiramide(tramos: List<TramoComision>, logrado: Double): List<SegmentoPiramide> {
        if (tramos.isEmpty()) return emptyList()
        val tope = tramos.last().objetivo.takeIf { it > 0 } ?: return emptyList()
        return tramos.mapIndexed { i, t ->
            val anterior = if (i == 0) 0.0 else tramos[i - 1].objetivo
            val largo = (t.objetivo - anterior).takeIf { it > 0 } ?: 1.0
            val dentro = ((logrado - anterior) / largo).coerceIn(0.0, 1.0)
            SegmentoPiramide(t.nombre, ((t.objetivo - anterior) / tope).toFloat().coerceAtLeast(0.02f), dentro.toFloat(), logrado >= t.objetivo, i)
        }
    }

    /** Validación del formulario ANTES de enviar (mismos mensajes que la web). null = todo bien. */
    fun validar(f: FormEsquema, nombreDe: (String) -> String?): String? {
        if (f.nombre.isBlank()) return "Ponle un nombre al esquema"
        val llenos = f.tramos.filter { it.objetivo.isNotBlank() && it.nombre.isNotBlank() }
        if (f.tipo == "tramos" && llenos.isEmpty()) return "Completa al menos un nivel"
        if (f.tipo == "porcentaje") {
            val pct = f.porcentaje.trim().replace(',', '.').toDoubleOrNull()
            if (pct == null || pct <= 0 || pct > 100) return "Pon el porcentaje del esquema (entre 0 y 100)"
            val malo = f.asignados.firstOrNull { id ->
                val v = f.ajusteDe(id).porcentaje.trim()
                if (v.isEmpty()) false else (v.replace(',', '.').toDoubleOrNull()?.let { it < 0 || it > 100 } ?: true)
            }
            if (malo != null) return "El porcentaje de ${nombreDe(malo) ?: "esa persona"} debe estar entre 0 y 100"
        }
        return null
    }

    private fun numero(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

    /** El cuerpo de POST /api/staff/comision-plantillas, idéntico al de FormPlantilla. */
    fun cuerpoGuardar(f: FormEsquema, idNuevo: String?): JsonObject = buildJsonObject {
        // Editar manda el id; un alta, el id que tendrá (idempotencia). Nunca los dos.
        if (f.id != null) put("id", f.id) else if (idNuevo != null) put("idNuevo", idNuevo)
        val porEval = f.porEvaluaciones
        put("nombre", f.nombre.trim())
        put("tipo", f.tipo)
        put("unidad", if (porEval) "evaluaciones" else "paquetes")
        put("minSesiones", if (porEval) null else numero(f.minSes))
        put("maxSesiones", if (porEval) null else numero(f.maxSes))
        put("soloPacientesNuevos", !porEval && f.soloNuevos)
        put("montoPaquete", if (porEval) null else numero(f.montoPaq))
        put("periodoMeses", f.periodoMeses.trim().toIntOrNull()?.takeIf { it > 0 } ?: 1)
        put("porcentaje", if (f.tipo == "porcentaje") numero(f.porcentaje) else null)
        put("tramos", JsonArray(if (f.tipo == "tramos") {
            f.tramos.filter { it.objetivo.isNotBlank() && it.nombre.isNotBlank() }.map { t ->
                buildJsonObject {
                    put("nombre", t.nombre.trim())
                    put("objetivo", numero(t.objetivo) ?: 0.0)
                    put("montoBono", numero(t.montoBono) ?: 0.0)
                }
            }
        } else emptyList()))
        put("terapeutaIds", JsonArray(f.asignados.map { JsonPrimitive(it) }))
        put("perfilIds", JsonArray((if (porEval) f.asignadosEquipo else emptyList()).map { JsonPrimitive(it) }))
        put("ajustes", JsonObject(f.asignados.associateWith { id ->
            val a = f.ajusteDe(id)
            buildJsonObject {
                put("porcentaje", a.porcentaje.trim().replace(',', '.'))
                put("liquidacion", a.liquidacion)
                put("metodoPago", a.metodoPago)
            }
        }))
    }

    // ── Histórico ──

    /** 'YYYY-MM' → 'Agosto 2026'. */
    fun nombreMes(ym: String): String {
        val (a, m) = anioMes("$ym-01") ?: return ym
        return "${capitalizar(MESES.getOrElse(m - 1) { ym })} $a"
    }

    /** "Todo el histórico", los últimos 14 meses y los años que tocan (como la web). */
    fun opcionesHistorico(hoyIso: String): List<OpcionPeriodo> {
        val (a, m) = anioMes(hoyIso) ?: return listOf(OpcionPeriodo("todo", "Todo el histórico"))
        val meses = (0 until 14).map { i ->
            var mm = m - i; var aa = a
            while (mm < 1) { mm += 12; aa -= 1 }
            "$aa-${mm.toString().padStart(2, '0')}"
        }
        val anios = meses.map { it.take(4) }.distinct().sortedDescending()
        return listOf(OpcionPeriodo("todo", "Todo el histórico")) +
            meses.map { OpcionPeriodo(it, nombreMes(it)) } +
            anios.map { OpcionPeriodo(it, "Todo $it") }
    }

    /** El rango [desde, hasta) del filtro: null = sin filtro de fecha. */
    fun rangoHistorico(periodo: String): Pair<String, String>? {
        if (periodo == "todo") return null
        if (Regex("^\\d{4}$").matches(periodo)) return "$periodo-01-01" to "${periodo.toInt() + 1}-01-01"
        val (a, m) = anioMes("$periodo-01") ?: return null
        val (a2, m2) = if (m == 12) (a + 1) to 1 else a to (m + 1)
        return "$a-${m.toString().padStart(2, '0')}-01" to "$a2-${m2.toString().padStart(2, '0')}-01"
    }

    private fun celda(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == ';' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    /** El CSV del histórico, con las mismas columnas que el "📥 Exportar" de la web. */
    fun csvHistorico(filas: List<FilaHistoricoComision>): String = buildString {
        append("Fecha,Profesional,Esquema,Nivel,Período,Origen,Monto\n")
        filas.forEach { f ->
            val monto = aCentimos(f.monto).let { c -> "${if (c < 0) "-" else ""}${kotlin.math.abs(c) / 100}.${(kotlin.math.abs(c) % 100).toString().padStart(2, '0')}" }
            append(listOf(
                f.fecha, f.profesional, f.esquema, f.nivel ?: "", f.periodo ?: "",
                if (f.origen == "sistema") "Liquidado en Sania" else "Registrado en caja", monto,
            ).joinToString(",") { celda(it) })
            append('\n')
        }
    }
}
