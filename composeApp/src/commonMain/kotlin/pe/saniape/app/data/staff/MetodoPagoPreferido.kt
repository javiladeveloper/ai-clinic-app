package pe.saniape.app.data.staff

import pe.saniape.app.data.Preferencias

/**
 * Método de pago con el que arranca cada cobro (gemelo de
 * lib/metodo-pago-preferido.ts de la web). Antes todos arrancaban en "Efectivo"
 * y 1 de cada 4 cobros es Yape/Plin/otro: recepción lo cambiaba cada vez y, si
 * se olvidaba, el arqueo por método salía mal.
 *
 * Solo es un valor por defecto guardado en ESTE teléfono: si no hay nada
 * guardado (o falla el almacenamiento) todo sigue como antes, en "Efectivo".
 */
object MetodoPagoPreferido {
    private const val CLAVE_USUARIO = "metodo-pago:ultimo"
    private const val CLAVE_PACIENTES = "metodo-pago:por-paciente"

    /** Método inicial para un cobro de [pacienteId] entre los [disponibles] de la clínica. */
    fun inicial(pacienteId: String?, disponibles: List<String>): String {
        val delPaciente = pacienteId?.takeIf { it.isNotBlank() }?.let { pid ->
            runCatching { decodificarMetodosPorPaciente(Preferencias.texto(CLAVE_PACIENTES))[pid] }.getOrNull()
        }
        val delUsuario = runCatching { Preferencias.texto(CLAVE_USUARIO) }.getOrNull()
        return elegirMetodoPago(delPaciente, delUsuario, disponibles)
    }

    /** Llamar DESPUÉS de registrar un cobro con éxito (o dejarlo en la cola). */
    fun recordar(pacienteId: String?, metodo: String) {
        if (metodo.isBlank()) return
        runCatching {
            Preferencias.setTexto(CLAVE_USUARIO, metodo)
            if (!pacienteId.isNullOrBlank()) {
                Preferencias.setTexto(
                    CLAVE_PACIENTES,
                    recordarMetodoEnMapa(Preferencias.texto(CLAVE_PACIENTES), pacienteId, metodo),
                )
            }
        }
    }
}
