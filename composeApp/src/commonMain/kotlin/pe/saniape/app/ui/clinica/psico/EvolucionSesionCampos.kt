package pe.saniape.app.ui.clinica.psico

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pe.saniape.app.data.staff.*
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.theme.Sania

@Composable
fun EvolucionSesionCampos(texto: String, onChange: (String) -> Unit) {
    val b = leerEvolucionPsico(texto)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Al guardar, la evolución se incorpora a la historia clínica y la sesión queda completada.", color = Sania.colors.textoSuave)
        OutlinedTextField(value = b.evolucion, onValueChange = { onChange(textoEvolucionPsico(b.copy(evolucion = it))) },
            label = { Text("Temas trabajados y evolución") }, modifier = Modifier.fillMaxWidth(), minLines = 3, colors = coloresCampoForm())
        OutlinedTextField(value = b.estado, onValueChange = { onChange(textoEvolucionPsico(b.copy(estado = it))) },
            label = { Text("Estado emocional / examen mental") }, modifier = Modifier.fillMaxWidth(), minLines = 2, colors = coloresCampoForm())
        OutlinedTextField(value = b.tareas, onValueChange = { onChange(textoEvolucionPsico(b.copy(tareas = it))) },
            label = { Text("Tareas e indicaciones") }, modifier = Modifier.fillMaxWidth(), minLines = 2, colors = coloresCampoForm())
        OutlinedTextField(value = b.plan, onValueChange = { onChange(textoEvolucionPsico(b.copy(plan = it))) },
            label = { Text("Plan para la próxima sesión") }, modifier = Modifier.fillMaxWidth(), minLines = 2, colors = coloresCampoForm())
    }
}
