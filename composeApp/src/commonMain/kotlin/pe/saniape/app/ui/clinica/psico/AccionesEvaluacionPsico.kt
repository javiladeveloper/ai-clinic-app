package pe.saniape.app.ui.clinica.psico

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.EvaluacionPsicoRepo
import pe.saniape.app.data.staff.mensajePsico
import pe.saniape.app.data.staff.puedeAgregarCitaEvaluacion
import pe.saniape.app.data.staff.puedeVerEvaluacionPsico
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.conIndicador
import pe.saniape.app.ui.theme.Sania

/**
 * "🧠 Evaluación" y "+ Agregar cita de evaluación" en la tarjeta de un
 * tratamiento cuyo servicio es evaluación psicológica (gemelo del bloque de la
 * ficha web). El contenido lo ven el Admin y quien atiende (la base lo exige
 * igual); agregar citas es agenda, sin cambiar el precio.
 *
 * Quien no es el responsable del tratamiento pero atendió alguna de sus citas
 * también la abre: se averigua aparte (una consulta chica, solo en este caso).
 */
@Composable
fun AccionesEvaluacionPsico(
    tratamientoId: String,
    terapeutaId: String?,
    estado: String?,
    rol: String?,
    miTerapeutaId: String?,
    puedeCitas: Boolean,
    puedeSesiones: Boolean,
    fichaInactiva: Boolean,
    recargaToken: Int = 0,
    onAbrir: () -> Unit,
    onCitaAgregada: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var atienden by remember(tratamientoId) { mutableStateOf<List<String>>(emptyList()) }
    var agregando by remember { mutableStateOf(false) }
    val directo = puedeVerEvaluacionPsico(rol, miTerapeutaId, terapeutaId)
    LaunchedEffect(tratamientoId, recargaToken, directo, miTerapeutaId) {
        if (!directo && miTerapeutaId != null) atienden = EvaluacionPsicoRepo.profesionalesDelTratamiento(tratamientoId)
    }
    val ve = directo || puedeVerEvaluacionPsico(rol, miTerapeutaId, terapeutaId, atienden)
    val agrega = puedeAgregarCitaEvaluacion(puedeCitas, puedeSesiones, estado, fichaInactiva)
    if (!ve && !agrega) return

    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ve) BotonPsico("🧠 Evaluación", color = Sania.colors.purple, relleno = true, modifier = Modifier.weight(1f), onClick = onAbrir)
            if (agrega) {
                BotonPsico(if (agregando) "Agregando…" else "+ Agregar cita de evaluación", habilitado = !agregando,
                    modifier = Modifier.weight(1f)) {
                    agregando = true
                    scope.launch {
                        val r = conIndicador { EvaluacionPsicoRepo.agregarCita(tratamientoId) }
                        agregando = false
                        if (r.registrada) {
                            Toaster.exito("Se agregó una cita a la evaluación (mismo precio)")
                            onCitaAgregada()
                        } else Toaster.error(r.mensajePsico())
                    }
                }
            }
        }
    }
}
