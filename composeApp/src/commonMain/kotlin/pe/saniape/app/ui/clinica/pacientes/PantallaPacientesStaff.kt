package pe.saniape.app.ui.clinica.pacientes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.PacienteStaff

/**
 * Vuelta de la ficha a la lista: [tick] sube en cada vuelta; [pacienteId] es el que se
 * abrió y [huboCambios] si en la ficha se cambió algo (completar, cobrar, editar…).
 */
data class VueltaDeFicha(val tick: Int, val pacienteId: String?, val huboCambios: Boolean)

/**
 * Contenedor del módulo Pacientes (staff): alterna entre la lista y la ficha.
 * Es el que engancha el tab "Pacientes" de ClinicaConTabs.
 */
@Composable
fun PantallaPacientesStaff(ctx: ContextoStaff) {
    var fichaDe by remember { mutableStateOf<PacienteStaff?>(null) }
    // Al volver de la ficha la lista se pone al día (el progreso de sesiones cambió),
    // pero sin bajarla entera cada vez: si se cambió algo, se relee ESE paciente; si
    // la lista ya es vieja, entera (ver PacientesViewModel.alVolverDeFicha).
    var vuelta by remember { mutableStateOf<VueltaDeFicha?>(null) }
    var huboCambios by remember { mutableStateOf(false) }

    val seleccionado = fichaDe
    if (seleccionado != null) {
        pe.saniape.app.tutoriales.PantallaTutorial("Ficha") { PantallaFichaPaciente(
            ctx = ctx,
            pacienteInicial = seleccionado,
            onCerrar = {
                vuelta = VueltaDeFicha((vuelta?.tick ?: 0) + 1, seleccionado.id, huboCambios)
                fichaDe = null
            },
            onHuboCambios = { huboCambios = true },
        ) }
    } else {
        PantallaPacientes(
            ctx = ctx,
            onAbrirFicha = { huboCambios = false; fichaDe = it },
            vueltaDeFicha = vuelta,
        )
    }
}
