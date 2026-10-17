package pe.saniape.app.data.staff

// ─────────────────────────────────────────────────────────────────────────────
// APERTURA AUTOMÁTICA DE CAJA (2026-10-16) — gemelo de lo que muestra
// components/finanzas/CierreCaja.tsx con lo que devuelve /api/staff/caja/cierre.
// La apertura NO se edita (la pone la base con lo contado en el cierre
// anterior); un retiro o ingreso de efectivo se registra como AJUSTE con motivo
// (/api/staff/caja/ajuste). La app no recalcula nada: el servidor manda
// `esperado` (ya con la apertura), `esperadoMovimientos` y `esperadoAlActualizar`.
// Un servidor sin la migración no manda estos campos: todo se ve como antes.
// ─────────────────────────────────────────────────────────────────────────────

/** Con cuánto abrió la caja ese día. [desde] null = primera apertura con Sania. */
data class AperturaCajaApp(
    val monto: Double,
    val desde: String?,
    val diasSinCerrar: List<String> = emptyList(),
)

/** Un retiro (monto < 0) o ingreso (monto > 0) de efectivo, con su autor. */
data class AjusteCajaApp(
    val id: String,
    val monto: Double,
    val motivo: String,
    val creadoPorNombre: String?,
    val createdAt: String?,
    /** Hecho después del cierre del día: no entra en su arqueo (va a la apertura de mañana). */
    val despuesDelCierre: Boolean,
)

object CajaApertura {
    /** Motivo mínimo del ajuste (la web pide al menos 3 letras). */
    const val MIN_MOTIVO = 3

    private fun ddmm(f: String): String = if (f.length >= 10) "${f.substring(8, 10)}/${f.substring(5, 7)}" else f

    /** Gemelo de `mensajeCierrePosterior`. */
    fun mensajeCierrePosterior(fecha: String): String =
        "Ya se cerró la caja del ${ddmm(fecha)}, que abrió con lo contado antes: este día ya no se puede cerrar ni recontar. Si hubo un error, regístralo como ajuste de caja hoy."

    /** Gemelo de `mensajeDiasSinCerrar`. */
    fun mensajeDiasSinCerrar(dias: List<String>): String {
        val n = dias.size
        return "Hay $n ${if (n == 1) "día" else "días"} con movimientos sin cerrar (${dias.joinToString(", ") { ddmm(it) }}): su efectivo ya está en la apertura, pero ciérralos en orden para que cada día cuadre."
    }

    /** Gemelo de `etiquetaAjuste`. */
    fun etiquetaAjuste(monto: Double): String = if (monto < 0) "Retiro" else "Ingreso de efectivo"

    /** De dónde sale la apertura, entre paréntesis (como la web). */
    fun origenApertura(a: AperturaCajaApp): String =
        if (a.desde != null) "(lo contado al cerrar el ${ddmm(a.desde)}${if (a.diasSinCerrar.isNotEmpty()) " + los días sin cerrar" else ""})"
        else "(primera apertura con Sania)"

    /** Lo que se explica bajo "Efectivo esperado en caja". */
    fun rotuloEsperado(conApertura: Boolean, hayAjustes: Boolean): String =
        if (conApertura) "(apertura${if (hayAjustes) " + ajustes" else ""} + cobros − gastos en efectivo)"
        else "(cobros en efectivo − gastos en efectivo)"

    /** Validación del ajuste antes de enviar. null = todo bien. */
    fun validarAjuste(monto: Double?, motivo: String): String? = when {
        monto == null || !monto.isFinite() || monto <= 0 -> "Escribe el monto"
        motivo.trim().length < MIN_MOTIVO -> "Escribe el motivo (p. ej. \"Depósito al banco\")"
        else -> null
    }

    /** ¿Recontar cambia el esperado del cierre guardado? (entonces se muestran los dos). */
    fun recuentoCambia(cerrado: Boolean, cerrable: Boolean, esperado: Double, esperadoAlActualizar: Double?): Boolean =
        cerrado && cerrable && esperadoAlActualizar != null && kotlin.math.abs(esperadoAlActualizar - esperado) >= 0.005
}
