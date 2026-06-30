package pe.saniape.app.ui.clinica.pacientes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import pe.saniape.app.data.staff.CitaHito
import pe.saniape.app.data.staff.TratamientoPaciente
import pe.saniape.app.ui.theme.Sania

/** Un paso del recorrido (bolita + label). */
private data class Paso(val label: String, val done: Boolean, val activo: Boolean)

/**
 * Barra de recorrido ADAPTATIVA por tipo, para incrustar en la cabecera de cada
 * TarjetaTratamiento (multi-especialidad):
 *  - con sesiones (fisio/psico):   Consulta → Evaluación → Sesiones (N/M) → Alta
 *  - sin sesiones (medicina/nutri): Consulta → Evaluación → Control → Alta
 */
@Composable
fun BarraRecorrido(
    trat: TratamientoPaciente,
    consultaDone: Boolean,
    evalDone: Boolean,
    citaConsulta: CitaHito? = null,
    citaEvaluacion: CitaHito? = null,
    puedePagos: Boolean = false,
    onEditarCita: (CitaHito) -> Unit = {},
) {
    val c = Sania.colors
    val usaSesiones = !trat.esConsulta
    val sesComp = trat.sesionesCompletadas
    val sesTot = trat.totalSesiones
    val altaTrat = trat.estado == "Alta"
    val completo = trat.estado == "Completado" || (sesTot > 0 && sesComp >= sesTot)
    // Bolita seleccionada (muestra su nube de referencia). Índice del paso abierto, o null.
    var hitoAbierto by remember { mutableStateOf<Int?>(null) }

    // Tercer paso: con sesiones = progreso N/M; sin sesiones = "Control" (hecho = hubo
    // una consulta/control completado, NO por tener fecha futura agendada).
    val pasoTercero = when {
        usaSesiones -> {
            val etq = if (sesComp > sesTot) "$sesTot/$sesTot +${sesComp - sesTot}" else "$sesComp/$sesTot ses."
            Paso(etq, done = completo, activo = !completo && !altaTrat)
        }
        // El control se considera realizado si hubo la consulta que generó el plan.
        else -> Paso("Control", done = consultaDone || evalDone, activo = !altaTrat && !(consultaDone || evalDone))
    }
    val pasos = listOf(
        Paso("Consulta", done = consultaDone, activo = false),
        Paso("Evaluación", done = evalDone, activo = false),
        pasoTercero,
        Paso("Alta", done = altaTrat, activo = false),
    )
    // Cada bolita puede abrir su nube de referencia si hay una cita-hito asociada:
    //  - paso 0 (Consulta) → citaConsulta
    //  - paso 1 (Evaluación) → citaEvaluacion
    //  - paso 2 (Control, sin sesiones) → la cita que se realizó (consulta o evaluación)
    val citaDelPaso = { i: Int ->
        when {
            i == 0 -> citaConsulta
            i == 1 -> citaEvaluacion
            i == 2 && !usaSesiones -> citaConsulta ?: citaEvaluacion
            else -> null
        }
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        pasos.forEachIndexed { i, paso ->
            val cita = citaDelPaso(i)
            val clickeable = cita != null
            Column(Modifier.width(60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val abierta = hitoAbierto == i
                val bg = when { paso.done -> c.ok; paso.activo -> c.navy; else -> c.superficie }
                val fg = when { paso.done || paso.activo -> c.sobreNavy; else -> c.textoSuave }
                val borde = when { abierta -> c.navy; paso.done -> c.ok; paso.activo -> c.navy; else -> c.borde }
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(bg)
                        .border(if (abierta) 3.dp else 2.dp, borde, CircleShape)
                        .let { if (clickeable) it.clickable { hitoAbierto = if (abierta) null else i } else it },
                    contentAlignment = Alignment.Center,
                ) { Text(if (paso.done) "✓" else "${i + 1}", color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                Text(
                    paso.label, color = if (paso.done) c.ok else if (paso.activo) c.navy else c.textoSuave,
                    fontSize = 8.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            if (i < pasos.size - 1) {
                Box(
                    Modifier.weight(1f).height(2.dp).padding(top = 13.dp)
                        .background(if (pasos[i].done) c.ok else c.borde),
                )
            }
        }
    }

    // Nube flotante de referencia de la bolita tocada (Consulta/Evaluación/Control).
    hitoAbierto?.let { citaDelPaso(it) }?.let { cita ->
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                .background(c.chipBg).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                .padding(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${if (cita.tipo == "Consulta") "💬" else "🔍"} ${cita.tipo}",
                    color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.navy)
                        .clickable { onEditarCita(cita) }.padding(horizontal = 10.dp, vertical = 4.dp),
                ) { Text("✏ Editar", color = c.sobreNavy, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(4.dp))
            FilaRef("Fecha", "${cita.fecha}${cita.hora?.let { " · $it" } ?: ""}")
            cita.terapeutaNombre?.let { FilaRef("Profesional", it) }
            if (puedePagos && cita.costo != null) FilaRef("Precio", if (cita.costo > 0) "S/ ${cita.costo}" else "Gratuita")
            cita.notas?.takeIf { it.isNotBlank() }?.let { FilaRef("Notas", it) }
        }
    }
}

@Composable
private fun FilaRef(etq: String, valor: String) {
    val c = Sania.colors
    Row(Modifier.padding(vertical = 1.dp)) {
        Text("$etq:", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.width(70.dp))
        Text(valor, color = c.texto, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
