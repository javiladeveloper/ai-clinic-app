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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import pe.saniape.app.data.staff.HitosPaciente
import pe.saniape.app.data.staff.PacienteStaff
import pe.saniape.app.data.staff.TratamientoPaciente
import pe.saniape.app.ui.theme.Sania

/** Un paso del recorrido (bolita + label). */
private data class Paso(val label: String, val done: Boolean, val activo: Boolean)

/**
 * Recorrido del paciente — barra de pasos ADAPTATIVA por tipo (multi-especialidad):
 *  - Tratamiento por sesiones (fisio/psico):   Consulta → Evaluación → Sesiones (N/M) → Alta
 *  - Tratamiento sin sesiones (medicina/nutri): Consulta → Evaluación → Control → Alta
 *  - Sin tratamiento aún: Consulta → Evaluación → (Tratamiento) → Alta
 * Las acciones de sesión viven en TarjetaTratamiento; aquí solo el mapa de avance + "+ Sesión".
 */
@Composable
fun FlujoGuiado(
    paciente: PacienteStaff,
    hitos: HitosPaciente?,
    puedeSesiones: Boolean,
    puedeCitas: Boolean,
    onNuevaConsulta: () -> Unit,
    onNuevaEvaluacion: () -> Unit,
    onNuevoTratamiento: () -> Unit,
    onCrearSesion: (TratamientoPaciente) -> Unit,
) {
    val c = Sania.colors
    if (paciente.estado == "Inactivo") return

    // Tratamientos "vivos" en el recorrido (activos o completados sin alta).
    val activos = paciente.tratamientos.filter { it.estado == "Activo" || it.estado == "Completado" }
    val consultaDone = hitos?.consultaDone == true
    val evalDone = hitos?.evaluacionDone == true

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Una tarjeta de recorrido por tratamiento vivo (o una sola sin tratamiento).
        val items: List<TratamientoPaciente?> = if (activos.isNotEmpty()) activos else listOf(null)
        items.forEach { trat ->
            TarjetaRecorrido(
                trat = trat, consultaDone = consultaDone, evalDone = evalDone,
                puedeSesiones = puedeSesiones, onCrearSesion = onCrearSesion,
            )
        }

        // Nuevo ciclo de atención
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
                .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
                .padding(14.dp),
        ) {
            Text(if (activos.isNotEmpty()) "NUEVO CICLO DE ATENCIÓN" else "ACCIONES RÁPIDAS",
                color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)

            // Hints según el estado del paciente
            when {
                paciente.estado == "Nuevo" -> Hint("💬", "Paciente nuevo", "Empieza con una consulta o evaluación", c.chipBg, c.texto)
                paciente.estado == "Evaluado" && activos.isEmpty() ->
                    Hint("💊", "Evaluación completada", "Crea un tratamiento para iniciar", c.pendBg, c.pend)
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (puedeCitas) {
                    BtnCiclo("💬 Consulta", Modifier.weight(1f), onNuevaConsulta)
                    BtnCiclo("🔍 Evaluación", Modifier.weight(1f), onNuevaEvaluacion)
                }
                BtnCiclo("💊 Tratamiento", Modifier.weight(1f), onNuevoTratamiento)
            }
        }
    }
}

@Composable
private fun TarjetaRecorrido(
    trat: TratamientoPaciente?,
    consultaDone: Boolean,
    evalDone: Boolean,
    puedeSesiones: Boolean,
    onCrearSesion: (TratamientoPaciente) -> Unit,
) {
    val c = Sania.colors
    val usaSesiones = trat?.let { !it.esConsulta } ?: true
    val sesComp = trat?.sesionesCompletadas ?: 0
    val sesTot = trat?.totalSesiones ?: 0
    val altaTrat = trat?.estado == "Alta"
    val completo = trat?.estado == "Completado" || (sesTot > 0 && sesComp >= sesTot)

    // Tercer paso adaptativo: "Sesiones N/M" si usa sesiones; "Control" si es consulta médica.
    val pasoTercero = when {
        trat == null -> Paso("Tratamiento", done = false, activo = false)
        usaSesiones -> {
            val etq = if (sesComp > sesTot) "$sesTot/$sesTot +${sesComp - sesTot}" else "$sesComp/$sesTot ses."
            Paso(etq, done = completo, activo = !completo && !altaTrat)
        }
        else -> Paso("Control", done = trat.proximoControl != null, activo = !altaTrat)
    }
    val pasos = listOf(
        Paso("Consulta", done = consultaDone, activo = false),
        Paso("Evaluación", done = evalDone, activo = false),
        pasoTercero,
        Paso("Alta", done = altaTrat, activo = false),
    )

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .padding(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("RECORRIDO DEL PACIENTE", color = c.textoSuave, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                Text(
                    trat?.procedimiento ?: "Sin tratamiento aún",
                    color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (trat != null && puedeSesiones && usaSesiones && trat.estado == "Activo") {
                Box(
                    Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.navy)
                        .clickable { onCrearSesion(trat) }.padding(horizontal = 12.dp, vertical = 6.dp),
                ) { Text("+ Sesión", color = c.sobreNavy, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }

        Spacer(Modifier.height(12.dp))
        // Barra de pasos
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            pasos.forEachIndexed { i, paso ->
                Column(
                    Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val bg = when { paso.done -> c.ok; paso.activo -> c.navy; else -> c.superficie }
                    val fg = when { paso.done || paso.activo -> c.sobreNavy; else -> c.textoSuave }
                    val borde = when { paso.done -> c.ok; paso.activo -> c.navy; else -> c.borde }
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(bg)
                            .border(2.dp, borde, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (paso.done) "✓" else "${i + 1}", color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    Text(
                        paso.label, color = if (paso.done) c.ok else if (paso.activo) c.navy else c.textoSuave,
                        fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (i < pasos.size - 1) {
                    Box(
                        Modifier.weight(1f).height(2.dp).padding(top = 14.dp)
                            .background(if (pasos[i].done) c.ok else c.borde),
                    )
                }
            }
        }

        // Barra de progreso de sesiones (solo si usa sesiones)
        if (trat != null && usaSesiones && sesTot > 0) {
            Spacer(Modifier.height(12.dp))
            val frac = (sesComp.toFloat() / sesTot).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.chipBg)) {
                Box(Modifier.fillMaxWidth(frac).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.navy))
            }
        }
    }
}

@Composable
private fun Hint(icono: String, titulo: String, sub: String, bg: Color, fg: Color) {
    val c = Sania.colors
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(bg).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icono, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(titulo, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = c.textoSuave, fontSize = 10.sp)
        }
    }
}

@Composable
private fun BtnCiclo(texto: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable { onClick() }.padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = c.texto, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
}
