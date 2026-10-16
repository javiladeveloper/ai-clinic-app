package pe.saniape.app.data

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// ─────────────────────────────────────────────────────────────────────────────
// SESIONES SUELTAS: el acordado suma lo atendido (Centro OMER, 2026-10-16).
// GEMELO de lib/pagos.ts (web: montoAcordado, detalleSesionesSueltas,
// textoSesionesSueltas, costoSesionTrasCobro) y de public.acordado_suelta /
// costo_sesion_al_cobrar (SQL, migración 20261026100000). Si cambias uno,
// cambia los tres. Diseño: docs/superpowers/specs/2026-10-16-sueltas-acordado-design.md
//
// Solo modalidad 'Sesión suelta' — Paquete / Unidades / Consulta: el pactado de siempre.
//   devengada = estado ∉ {Cancelada, No asistió} y (Completada · o con un pago
//               ligado (sesion_id, monto > 0) · o la que se está cerrando ahora)
//   valor     = costo > 0 ? costo : precio de la sesión
//   precio de la sesión = precio_acordado / max(total_sesiones ?: 1, 1) si hay
//               acordado; si no, precio_por_sesion.
//   · total_sesiones ≤ 1: acordado = max(pactado, Σ valor)
//     (con precio_acordado: valor = min(costo, precio de la sesión)).
//   · total_sesiones ≥ 2: el PACTO cubre sus primeras N devengadas (por número,
//     fecha, id); acordado = pactado + Σ valor de las que lo EXCEDEN. Así
//     "Ampliar +N por S/ 0" es cortesía. El costo 0 NO es cortesía.
// Lo programado sin pagar NO suma (no es deuda hasta que se atiende).
// ─────────────────────────────────────────────────────────────────────────────

/** Lo del tratamiento que necesita el acordado (columnas de COLUMNAS_ACORDADO). */
data class TratPago(
    val modalidad: String?,
    val precioAcordado: Double? = null,
    val precioPaquete: Double? = null,
    val precioPorSesion: Double? = null,
    /** null = la suelta del formulario (cuenta como 1). */
    val totalSesiones: Int? = null,
    val cantidadUnidades: Int? = null,
    val precioUnitario: Double? = null,
)

/** Una sesión del tratamiento: lo mínimo para saber si ya se devengó. */
data class SesionDevengo(
    val id: String?,
    val estado: String?,
    /** null o ≤ 0 = sin costo propio (vale el precio de la sesión). */
    val costo: Double?,
    /** Orden dentro del pacto (total_sesiones ≥ 2): número, después fecha. */
    val numero: Int? = null,
    val fecha: String? = null,
)

/** Un pago del tratamiento: solo importa a qué sesión está ligado. */
data class PagoDevengo(val sesionId: String?, val monto: Double?)

/**
 * Las sesiones y pagos del tratamiento, para el acordado de una SUELTA.
 * Sin contexto, [montoAcordado] da el pactado (la regla de siempre).
 */
data class ContextoSesiones(
    val sesiones: List<SesionDevengo> = emptyList(),
    val pagos: List<PagoDevengo> = emptyList(),
    /**
     * La sesión que se está CERRANDO o COBRANDO ahora: cuenta como devengada
     * aunque siga Planificada (si no, al cerrar la 2ª sesión el tratamiento
     * parece saldado y no se pregunta "¿pagó esta sesión?").
     */
    val incluirSesionId: String? = null,
    /** Sesión que todavía no existe y nace al cerrar: suma su costo (o el precio). Va la última. */
    val sesionNueva: SesionNueva? = null,
)

data class SesionNueva(val costo: Double?)

data class DetalleSueltas(
    /** Lo pactado (piso, o lo que cubre las primeras N): la regla de siempre. */
    val pactado: Double,
    /** Σ del valor de TODAS las sesiones devengadas. */
    val devengado: Double,
    /** Lo que se debe por el tratamiento, a 2 decimales. */
    val acordado: Double,
    /** Completadas (sin contar la que se está cerrando). */
    val atendidas: Int,
    /** No completadas pero ya cobradas (pago ligado). */
    val adelantadas: Int,
    /** La sesión que se está cerrando/cobrando (o la nueva) entró a la cuenta. */
    val incluida: Boolean,
    /** Precio de una sesión sin costo propio. */
    val precioSesion: Double,
    /** Manda el pactado (lo devengado no lo supera / el pacto lo cubre). */
    val porPactado: Boolean,
    /** Pacto por N sesiones (total_sesiones ≥ 2); null en la suelta de siempre. */
    val sesionesPacto: Int?,
    val fueraDelPacto: Int,
    val montoFueraDelPacto: Double,
)

private val ESTADOS_SESION_NO_COBRABLE = setOf("Cancelada", "No asistió")

/** Redondeo a céntimos IGUAL que la web (Math.round: mitades hacia arriba). */
private fun redondear2Js(n: Double): Double = floor(n * 100 + 0.5) / 100

/** ¿Se cobra por sesión, sumando lo atendido? (solo 'Sesión suelta'). */
fun esSesionSuelta(modalidad: String?): Boolean = modalidad?.trim() == "Sesión suelta"

/** Lo PACTADO: precio_acordado ?: paquete / unidades / precio por sesión × nº de sesiones. */
fun montoPactado(t: TratPago): Double {
    t.precioAcordado?.let { return it }
    return when (t.modalidad) {
        "Paquete" -> t.precioPaquete ?: 0.0
        "Unidades" -> (t.cantidadUnidades ?: 0) * (t.precioUnitario ?: 0.0)
        else -> (t.precioPorSesion ?: 0.0) * (t.totalSesiones ?: 0)
    }
}

/** total_sesiones como lo lee la regla (null = 1, como la suelta del formulario). */
private fun totalPacto(t: TratPago): Int = t.totalSesiones ?: 1

/** Precio de UNA sesión de una suelta (para las sesiones sin costo propio). */
fun precioSesionSuelta(t: TratPago): Double =
    t.precioAcordado?.let { it / max(totalPacto(t), 1) } ?: (t.precioPorSesion ?: 0.0)

/** Orden del pacto: número, luego fecha (AAAA-MM-DD), luego id (vacíos de número/fecha al final). */
private val ordenPacto = Comparator<SesionDevengo> { a, b ->
    val na = a.numero
    val nb = b.numero
    if (na != nb) {
        return@Comparator when {
            na == null -> 1
            nb == null -> -1
            else -> na.compareTo(nb)
        }
    }
    val fa = a.fecha?.takeIf { it.isNotEmpty() }?.take(10)
    val fb = b.fecha?.takeIf { it.isNotEmpty() }?.take(10)
    if (fa != fb) {
        return@Comparator when {
            fa == null -> 1
            fb == null -> -1
            else -> if (fa < fb) -1 else 1
        }
    }
    (a.id ?: "").lowercase().compareTo((b.id ?: "").lowercase()).coerceIn(-1, 1)
}

/** El detalle de lo devengado de una SUELTA. Otra modalidad (o sin ctx): el pactado. */
fun detalleSesionesSueltas(t: TratPago, ctx: ContextoSesiones?): DetalleSueltas {
    val pactado = montoPactado(t)
    val precioSesion = precioSesionSuelta(t)
    val ts = totalPacto(t)
    val sesionesPacto = if (ts >= 2) ts else null
    val vacio = DetalleSueltas(
        pactado = pactado, devengado = 0.0, acordado = redondear2Js(pactado), atendidas = 0, adelantadas = 0,
        incluida = false, precioSesion = precioSesion, porPactado = true, sesionesPacto = sesionesPacto,
        fueraDelPacto = 0, montoFueraDelPacto = 0.0,
    )
    if (!esSesionSuelta(t.modalidad) || ctx == null) return vacio
    // Precio por sesión negociado (acordado con total ≤ 1): el costo copiado de la
    // lista no lo supera (García: Láser S/ 1 800 de lista, acordado S/ 1 500).
    val tope = if (t.precioAcordado != null && ts <= 1) precioSesion else null
    fun valor(costo: Double?): Double {
        val c = costo ?: 0.0
        if (c <= 0) return precioSesion
        return if (tope != null) min(c, tope) else c
    }
    val ligadas = ctx.pagos
        .filter { !it.sesionId.isNullOrEmpty() && (it.monto ?: 0.0) > 0 }
        .mapNotNull { it.sesionId }
        .toSet()
    val incluir = ctx.incluirSesionId?.takeIf { it.isNotEmpty() }
    val devengadas = mutableListOf<SesionDevengo>()
    var atendidas = 0
    var adelantadas = 0
    var incluida = false
    for (s in ctx.sesiones) {
        if ((s.estado ?: "") in ESTADOS_SESION_NO_COBRABLE) continue
        val esLaDeAhora = incluir != null && s.id == incluir
        when {
            s.estado == "Completada" -> atendidas++
            !s.id.isNullOrEmpty() && s.id in ligadas -> adelantadas++
            esLaDeAhora -> incluida = true
            else -> continue
        }
        devengadas.add(s)
    }
    // Orden del pacto; la sesión que todavía no existe va al final.
    val valores = devengadas.sortedWith(ordenPacto).map { valor(it.costo) }.toMutableList()
    ctx.sesionNueva?.let {
        valores.add(valor(it.costo))
        incluida = true
    }
    val devengado = redondear2Js(valores.sum())
    if (sesionesPacto == null) {
        return vacio.copy(
            devengado = devengado, acordado = redondear2Js(max(pactado, devengado)), atendidas = atendidas,
            adelantadas = adelantadas, incluida = incluida, porPactado = pactado > devengado + 0.005,
        )
    }
    // Pacto por N: cubre las primeras N (cortesías incluidas); suman las demás.
    val fuera = valores.drop(sesionesPacto)
    val montoFuera = redondear2Js(fuera.sum())
    return vacio.copy(
        devengado = devengado, acordado = redondear2Js(pactado + montoFuera), atendidas = atendidas,
        adelantadas = adelantadas, incluida = incluida, porPactado = fuera.isEmpty(),
        fueraDelPacto = fuera.size, montoFueraDelPacto = montoFuera,
    )
}

/**
 * Monto total acordado (lo que el paciente debe pagar HOY). Con [ctx] una SESIÓN
 * SUELTA suma lo atendido y lo cobrado por adelantado; sin él, o en otra
 * modalidad, el pactado de siempre (lo que usa "Ampliar", que suma sobre el pacto).
 */
fun montoAcordado(t: TratPago, ctx: ContextoSesiones? = null): Double {
    if (ctx == null || !esSesionSuelta(t.modalidad)) return montoPactado(t)
    return detalleSesionesSueltas(t, ctx).acordado
}

/** Estado de pago según lo pagado vs lo acordado (tolerancia de medio centavo). */
fun estadoPagoDe(totalPagado: Double, acordado: Double): String = when {
    acordado > 0 && totalPagado >= acordado - 0.005 -> "Pagado"
    totalPagado > 0 -> "Parcial"
    else -> "Pendiente"
}

/**
 * La línea que explica el acordado de una suelta ("2 sesiones atendidas · S/ 200").
 * [dinero] formatea en la moneda del tratamiento. "" si no es suelta.
 */
fun textoSesionesSueltas(d: DetalleSueltas, dinero: (Double) -> String, esSuelta: Boolean = true): String {
    if (!esSuelta) return ""
    val partes = mutableListOf<String>()
    if (d.atendidas > 0) partes.add("${d.atendidas} ${if (d.atendidas == 1) "sesión atendida" else "sesiones atendidas"}")
    if (d.adelantadas > 0) partes.add("${d.adelantadas} pagada${if (d.adelantadas == 1) "" else "s"} por adelantado")
    if (d.sesionesPacto != null) {
        val pacto = "pactado ${dinero(d.pactado)} por ${d.sesionesPacto} sesiones"
        if (partes.isEmpty()) return "Aún sin sesiones atendidas · $pacto"
        if (d.fueraDelPacto == 0) return "${partes.joinToString(" + ")} · dentro de lo $pacto"
        return "${partes.joinToString(" + ")} · $pacto + ${d.fueraDelPacto} adicional${if (d.fueraDelPacto == 1) "" else "es"} ${dinero(d.montoFueraDelPacto)}"
    }
    if (partes.isEmpty()) {
        return if (d.precioSesion > 0) "Aún sin sesiones atendidas · se cobra ${dinero(d.precioSesion)} por sesión"
        else "Aún sin sesiones atendidas"
    }
    val texto = "${partes.joinToString(" + ")} · ${dinero(d.devengado)}"
    return if (d.porPactado) "$texto (pactado ${dinero(d.pactado)})" else texto
}

/**
 * Costo que debe quedar en una sesión al COBRARLA (o al editar su cobro); null =
 * no tocar. En una suelta queda en max(lo cobrado, precio de la sesión): un pago
 * PARCIAL no le baja el precio y un cobro mayor lo sube. Otras modalidades: lo cobrado.
 *  · [reemplazar] = false (registrar un cobro): solo si la sesión no tenía costo.
 *  · [reemplazar] = true (editar el cobro ligado): siempre se sincroniza.
 * (Hoy lo hace el servidor; está aquí para la paridad y por si la app lo necesita.)
 */
fun costoSesionTrasCobro(t: TratPago?, costoActual: Double?, monto: Double, reemplazar: Boolean = false): Double? {
    val actual = costoActual ?: 0.0
    if (!reemplazar && actual > 0) return null
    var nuevo = monto
    if (t != null && esSesionSuelta(t.modalidad)) {
        val referencia = precioSesionSuelta(t)
        if (referencia > 0) nuevo = max(monto, referencia)
    }
    nuevo = redondear2Js(nuevo)
    return if (kotlin.math.abs(nuevo - actual) < 0.005 && actual > 0) null else nuevo
}
