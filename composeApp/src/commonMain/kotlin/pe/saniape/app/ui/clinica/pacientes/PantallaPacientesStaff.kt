package pe.saniape.app.ui.clinica.pacientes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.PacienteStaff

/**
 * Contenedor del módulo Pacientes (staff): alterna entre la lista y la ficha.
 * Es el que engancha el tab "Pacientes" de ClinicaConTabs.
 */
@Composable
fun PantallaPacientesStaff(ctx: ContextoStaff) {
    var fichaDe by remember { mutableStateOf<PacienteStaff?>(null) }

    val seleccionado = fichaDe
    if (seleccionado != null) {
        PantallaFichaPaciente(ctx = ctx, pacienteInicial = seleccionado, onCerrar = { fichaDe = null })
    } else {
        PantallaPacientes(ctx = ctx, onAbrirFicha = { fichaDe = it })
    }
}