package pe.saniape.app.ui.clinica.agenda.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.ui.theme.Sania

private val ESTADOS = listOf("Pendiente", "Confirmada", "Completada", "Cancelada")
private val TIPOS = listOf("Consulta", "Evaluación", "Sesión")

/** Filtros de la agenda: búsqueda + chips de estado y tipo (como la web). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FiltrosAgenda(
    busqueda: String, onBusqueda: (String) -> Unit,
    filtroEstado: String?, onEstado: (String?) -> Unit,
    filtroTipo: String?, onTipo: (String?) -> Unit,
) {
    val c = Sania.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = Sania.dim.lg, vertical = Sania.dim.sm)) {
        OutlinedTextField(
            value = busqueda, onValueChange = onBusqueda,
            placeholder = { Text("🔍 Buscar paciente o procedimiento…", color = c.textoSuave) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Sania.dim.sm))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Estado (etiqueta "Sin confirmar" para Pendiente)
            ESTADOS.forEach { e ->
                ChipFiltro(if (e == "Pendiente") "Sin confirmar" else e, filtroEstado == e) {
                    onEstado(if (filtroEstado == e) null else e)
                }
            }
            // Separador visual
            Box(Modifier.width(1.dp))
            TIPOS.forEach { t ->
                ChipFiltro(t, filtroTipo == t, esTipo = true) {
                    onTipo(if (filtroTipo == t) null else t)
                }
            }
        }
    }
}

@Composable
private fun ChipFiltro(texto: String, activo: Boolean, esTipo: Boolean = false, onClick: () -> Unit) {
    val c = Sania.colors
    val color = if (esTipo) c.info else c.navy
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
            .background(if (activo) color else c.superficie)
            .border(1.dp, if (activo) color else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(texto, color = if (activo) c.sobreNavy else c.texto, fontSize = 11.sp,
            fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal)
    }
}