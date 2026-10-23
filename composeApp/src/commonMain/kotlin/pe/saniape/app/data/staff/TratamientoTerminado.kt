package pe.saniape.app.data.staff

import kotlin.math.abs

// SESIÓN COMPLETADA CON EL TRATAMIENTO TERMINADO (QA psicología 2026-10-23,
// captura 149). Gemelo de `lib/tratamiento-terminado.ts` y de
// `paqueteCobrableEnAgenda` / `montoAbonoPaquete` de `lib/cobro-sesion-agenda.ts`
// (web). La fila de la última sesión quedaba «Completada · ⚠ Paquete · debe S/ 400»
// sin acciones: ese es el momento de decidir qué sigue — «＋ Ampliar» o «✓ Dar de
// alta» — y de cobrar el saldo del paquete. Ampliar y alta llevan a la ficha del
// paciente, donde viven esos flujos (el mismo modal de ampliar y la misma
// confirmación de alta, con sus permisos). Aquí solo se decide si se OFRECEN.

/** Lo que hace falta del tratamiento de la sesión. */
data class TratamientoParaTerminar(
    val estado: String?,
    val totalSesiones: Int?,
    val sesionesCompletadas: Int?,
)

/**
 * ¿Ya no le quedan sesiones al plan y sigue abierto (sin alta)? Con un total de
 * sesiones (los de Consulta / Unidades no tienen contador) y todas hechas.
 * "Completado" cuenta: al completar la última sesión el tratamiento pasa SOLO a
 * ese estado, y es justo cuando hay que ofrecer ampliar o dar de alta.
 */
fun tratamientoTerminado(t: TratamientoParaTerminar?): Boolean {
    if (t == null || (t.estado != "Activo" && t.estado != "Completado")) return false
    val total = t.totalSesiones ?: 0
    return total > 0 && (t.sesionesCompletadas ?: 0) >= total
}

/** Qué ofrecer en la fila de una sesión terminada. */
data class AccionesTerminado(val ampliar: Boolean, val alta: Boolean) {
    val alguna: Boolean get() = ampliar || alta
}

private val NINGUNA = AccionesTerminado(ampliar = false, alta = false)

/**
 * Qué ofrecer en la fila de una sesión. Solo una SESIÓN Completada cuyo
 * tratamiento terminó, a quien gestiona tratamientos (permiso `sesiones`, el
 * mismo de los botones de la ficha y de /api/staff/tratamiento/{accion,alta}).
 * La evaluación psicológica no se amplía así, pero sí se da de alta. Con el
 * [numero] de la sesión conocido, solo la ÚLTIMA del plan ofrece las acciones.
 */
fun accionesSesionTerminada(
    tipo: String?,
    /** Estado de la cita o de la sesión. */
    estado: String?,
    tratamiento: TratamientoParaTerminar?,
    puedeSesiones: Boolean,
    /** Paciente dado de baja: su ficha no admite cambios. */
    pacienteInactivo: Boolean = false,
    esEvaluacionPsico: Boolean = false,
    numero: Int? = null,
): AccionesTerminado {
    if (tipo != null && tipo != "Sesión") return NINGUNA
    if (estado != "Completada") return NINGUNA
    if (!puedeSesiones || pacienteInactivo) return NINGUNA
    if (!tratamientoTerminado(tratamiento)) return NINGUNA
    val total = tratamiento?.totalSesiones ?: 0
    if (numero != null && numero > 0 && numero < total) return NINGUNA
    return AccionesTerminado(ampliar = !esEvaluacionPsico, alta = true)
}

/**
 * ¿Muestra la agenda el 💰 del SALDO DEL PAQUETE en esta cita? Solo en una
 * Sesión YA ATENDIDA (Completada) de un paquete con deuda: las pendientes siguen
 * sin 💰 (decisión DALU 2026-10-14). NO cobra la sesión: es el «Registrar pago»
 * de la ficha, un abono al tratamiento (POST /api/staff/pago/registrar sin
 * sesionId). Paquete = la modalidad del tratamiento o lo que dice el servidor.
 */
fun paqueteCobrableEnAgenda(
    tipo: String?, tratamientoId: String?, estado: String?, modalidadTratamiento: String?,
    estadoPago: EstadoPagoCita?,
): Boolean {
    if (tipo != "Sesión" || tratamientoId.isNullOrBlank() || estado != "Completada") return false
    if (!(modalidadTratamiento == "Paquete" || estadoPago?.esPaquete == true)) return false
    if (estadoPago == null || (estadoPago.deuda ?: 0.0) <= 0.0) return false
    return estadoPago.estado == "debe" || estadoPago.estado == "parcial"
}

/**
 * Monto del abono al paquete: lo que se pidió, sin pasar de lo que se debe.
 * null = monto inválido (≤ 0, más de dos decimales) o sobre la deuda.
 */
fun montoAbonoPaquete(pedido: Double, deuda: Double?): Double? {
    if (!pedido.isFinite()) return null
    val c = aCentimos(pedido)
    if (c <= 0 || abs(pedido * 100 - c) > 1e-6) return null
    val tope = aCentimos(deuda ?: 0.0)
    if (tope > 0 && c > tope) return null
    return c / 100.0
}

/** Qué abrir en la ficha al llegar desde la agenda / sesiones. */
enum class AccionTratamientoFicha { AMPLIAR, ALTA }

/**
 * La ficha del paciente con el flujo de ESE tratamiento abierto (gemelo de
 * `?tratamiento=<id>&accion=ampliar|alta` de la web): el mismo modal de ampliar
 * o la misma confirmación de alta (saldo, controles, encuesta).
 */
data class AperturaAccionTratamiento(val tratamientoId: String, val accion: AccionTratamientoFicha)

/**
 * ¿La ficha abre la acción pedida? Solo con permiso `sesiones`, ficha activa y
 * el tratamiento todavía Activo o Completado (sin alta). La ficha lo comprueba
 * otra vez al llegar: entre la agenda y la ficha pudo cambiar.
 */
fun puedeAbrirAccionTratamiento(estadoTratamiento: String?, puedeSesiones: Boolean, fichaInactiva: Boolean): Boolean =
    puedeSesiones && !fichaInactiva && (estadoTratamiento == "Activo" || estadoTratamiento == "Completado")
