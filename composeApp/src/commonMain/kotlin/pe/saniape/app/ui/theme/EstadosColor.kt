package pe.saniape.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Colores semánticos de ESTADOS y TIPOS — ÚNICA fuente de verdad, reutilizable en
 * toda la app (Agenda, Pacientes, Sesiones, etc.). Si un estado se muestra en dos
 * pantallas, se ve idéntico porque ambas leen de aquí.
 *
 * Espeja la web (globals.css / Badge variants):
 *   Pendiente=amber, Confirmada=green, Completada=blue, Cancelada=red.
 *   Consulta=purple, Evaluación=teal, Sesión=navy.
 */
data class ColorEstado(val fg: Color, val bg: Color)

object EstadosColor {

    /** Color de un estado de CITA. */
    @Composable
    fun cita(estado: String?): ColorEstado {
        val c = Sania.colors
        return when (estado) {
            "Confirmada" -> ColorEstado(c.ok, c.okBg)        // verde
            "Completada" -> ColorEstado(c.info, c.infoBg)    // azul (distinto de Confirmada)
            "Pendiente" -> ColorEstado(c.pend, c.pendBg)     // ámbar
            "Cancelada" -> ColorEstado(c.error, c.errorBg)   // rojo
            else -> ColorEstado(c.navy, c.chipBg)
        }
    }

    /** Etiqueta legible de un estado de cita ("Pendiente" → "Sin confirmar"). */
    fun etiquetaCita(estado: String?): String =
        if (estado == "Pendiente") "Sin confirmar" else (estado ?: "")

    /** Color de un estado de SESIÓN (para el módulo Sesiones / ficha del paciente). */
    @Composable
    fun sesion(estado: String?): ColorEstado {
        val c = Sania.colors
        return when (estado) {
            "Completada" -> ColorEstado(c.ok, c.okBg)        // verde (sesión hecha)
            "En progreso" -> ColorEstado(c.info, c.infoBg)   // azul
            "Planificada" -> ColorEstado(c.textoSuave, c.chipBg)
            "Reprogramada" -> ColorEstado(c.pend, c.pendBg)  // ámbar
            "No asistió", "Cancelada" -> ColorEstado(c.error, c.errorBg)
            else -> ColorEstado(c.navy, c.chipBg)
        }
    }

    /** Color de un estado de PACIENTE (Nuevo/Consultado/Evaluado/En tratamiento/Alta/Inactivo). */
    @Composable
    fun paciente(estado: String?): ColorEstado {
        val c = Sania.colors
        return when (estado) {
            "Alta" -> ColorEstado(c.ok, c.okBg)
            "En tratamiento", "Evaluado" -> ColorEstado(c.info, c.infoBg)
            "Consultado", "Nuevo" -> ColorEstado(c.pend, c.pendBg)
            "Inactivo" -> ColorEstado(c.error, c.errorBg)
            else -> ColorEstado(c.navy, c.chipBg)
        }
    }

    /** Color del TIPO de cita (acento). Consulta=morado, Evaluación=teal, Sesión=navy. */
    @Composable
    fun tipo(tipo: String?): ColorEstado {
        val c = Sania.colors
        return when (tipo) {
            "Evaluación" -> ColorEstado(c.teal, c.tealBg)
            "Sesión" -> ColorEstado(c.navy, c.chipBg)
            "Consulta" -> ColorEstado(c.purple, c.purpleBg)
            else -> ColorEstado(c.navy, c.chipBg)
        }
    }

    /** Icono del tipo de cita. */
    fun iconoTipo(tipo: String?): String = when (tipo) {
        "Evaluación" -> "🔍"
        "Sesión" -> "🏃"
        "Consulta" -> "💬"
        else -> "📅"
    }
}