package pe.saniape.app.ui.clinica.psico

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.EvaluacionPsicoRepo
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.data.staff.TratamientoPaciente
import pe.saniape.app.data.staff.historiaConSeccionPsico
import pe.saniape.app.data.staff.htmlSeccionPsico
import pe.saniape.app.data.staff.motivoSinContenidoPsico
import pe.saniape.app.data.staff.puedeVerEvaluacionPsico
import pe.saniape.app.ui.AccionesNativas
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.theme.Sania

/**
 * "🧠 Historia con informe psicológico" en la pestaña Resumen de la ficha
 * (gemelo de InformePsicoHistoria de la web, contrato §13.3).
 *
 * Solo aparece si el paciente tiene un tratamiento de evaluación psicológica y
 * quien mira es el Admin o el profesional tratante (la base decide igual: a los
 * demás el endpoint les devuelve vacío). Dos casillas APAGADAS por defecto:
 * incluir el informe vigente / incluir los puntajes de los tests (material
 * protegido). Las fotos de dibujos, hojas y protocolos nunca entran. El
 * resultado es la historia clínica del servidor con el bloque psicológico
 * dentro, en el visor con "Imprimir / Guardar PDF".
 */
@Composable
fun HistoriaPsicoTarjeta(
    pacienteId: String,
    pacienteNombre: String,
    tratamientos: List<TratamientoPaciente>,
    procsEvalPsico: Set<String>,
    rol: String?,
    miTerapeutaId: String?,
    acciones: AccionesNativas,
) {
    val psico = tratamientos.filter { it.procedimientoId != null && it.procedimientoId in procsEvalPsico }
    if (psico.isEmpty()) return
    val directo = psico.any { puedeVerEvaluacionPsico(rol, miTerapeutaId, it.terapeutaId) }
    // Quien solo atendió alguna cita del tratamiento también es tratante.
    var porCitas by remember(pacienteId, psico.size) { mutableStateOf(false) }
    LaunchedEffect(pacienteId, psico.size, directo, miTerapeutaId) {
        if (!directo && miTerapeutaId != null) {
            porCitas = psico.any { t ->
                puedeVerEvaluacionPsico(rol, miTerapeutaId, t.terapeutaId, EvaluacionPsicoRepo.profesionalesDelTratamiento(t.id))
            }
        }
    }
    if (!directo && !porCitas) return

    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var conInforme by remember(pacienteId) { mutableStateOf(false) }
    var conTests by remember(pacienteId) { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(c.purpleBg).border(1.dp, c.purple.copy(alpha = 0.35f), RoundedCornerShape(Sania.shape.md.dp))
            .padding(14.dp),
    ) {
        Text("🧠 HISTORIA CON INFORME PSICOLÓGICO", color = c.purple, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Text("Solo la ven el Admin y el profesional tratante. Las fotos de dibujos, hojas y protocolos nunca se incluyen.",
            color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
        Casilla("Incluir el informe psicológico vigente", conInforme) { conInforme = it }
        Casilla("Incluir los puntajes de los tests (material protegido)", conTests) { conTests = it }
        Spacer(Modifier.height(10.dp))
        val listo = (conInforme || conTests) && !cargando
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                .background(if (listo) c.purple else c.borde)
                .clickable(enabled = listo) {
                    scope.launch {
                        cargando = true
                        try {
                            val (base, psicoRes) = coroutineScope {
                                val h = async { PacientesRepo.historiaHtml(pacienteId) }
                                val p = async { EvaluacionPsicoRepo.historia(pacienteId, tests = conTests) }
                                h.await() to p.await()
                            }
                            when (psicoRes) {
                                is EvaluacionPsicoRepo.Historia.Error -> Toaster.error(psicoRes.mensaje)
                                is EvaluacionPsicoRepo.Historia.Ok -> {
                                    val motivo = motivoSinContenidoPsico(psicoRes.evaluaciones, conInforme, conTests)
                                    if (motivo != null) Toaster.error(motivo)
                                    else {
                                        val seccion = htmlSeccionPsico(psicoRes.evaluaciones, conInforme, conTests) { tratId ->
                                            psico.firstOrNull { it.id == tratId }?.procedimiento ?: "Evaluación psicológica"
                                        }
                                        acciones.abrirHtml(historiaConSeccionPsico(base, seccion, pacienteNombre), pacienteNombre)
                                    }
                                }
                            }
                        } finally {
                            cargando = false
                        }
                    }
                }
                .padding(vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when {
                    cargando -> "Generando…"
                    !conInforme && !conTests -> "Marca qué incluir"
                    else -> "Ver historia con informe psicológico"
                },
                color = if (listo) c.sobreNavy else c.textoSuave, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun Casilla(texto: String, marcada: Boolean, onCambio: (Boolean) -> Unit) {
    val c = Sania.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onCambio(!marcada) }.padding(vertical = 5.dp),
    ) {
        Box(
            Modifier.size(20.dp).clip(RoundedCornerShape(4.dp))
                .background(if (marcada) c.purple else c.superficie)
                .border(1.dp, if (marcada) c.purple else c.borde, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) { if (marcada) Text("✓", color = c.sobreNavy, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(8.dp))
        Text(texto, color = c.texto, fontSize = 12.sp)
    }
}
