package pe.saniape.app.ui.clinica

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.PacienteStaff
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.ui.theme.Sania

/**
 * Buscador GLOBAL de paciente (accesible desde el header en cualquier tab): escribe
 * nombre o DNI y abre la ficha directo. Respeta el scope del profesional (solo carga
 * los pacientes que puede ver). Evita navegar menús para encontrar a alguien.
 */
@Composable
fun PantallaBuscarPaciente(
    ctx: ContextoStaff,
    onAbrirFicha: (PacienteStaff) -> Unit,
    onCerrar: () -> Unit,
) {
    val c = Sania.colors
    var query by remember { mutableStateOf("") }
    var pacientes by remember { mutableStateOf<List<PacienteStaff>?>(null) }

    pe.saniape.app.ui.ManejarAtras(activo = true, onAtras = onCerrar)

    LaunchedEffect(Unit) {
        pacientes = runCatching { PacientesRepo.listar(ctx.miTerapeutaId) }.getOrDefault(emptyList())
    }

    val filtrados = remember(query, pacientes) {
        val q = query.trim()
        val base = pacientes ?: emptyList()
        if (q.isBlank()) base.take(20)   // sin búsqueda: los primeros (recientes)
        else base.filter {
            it.nombre.contains(q, ignoreCase = true) || (it.dni?.contains(q) == true)
        }.take(40)
    }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Barra: campo de búsqueda + cerrar
            Row(
                Modifier.fillMaxWidth().background(c.navyDark)
                    .padding(horizontal = Sania.dim.lg, vertical = Sania.dim.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Buscar por nombre o DNI…", color = c.textoSuave) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = c.texto, unfocusedTextColor = c.texto,
                        cursorColor = c.navy, focusedBorderColor = c.sobreNavy,
                        unfocusedBorderColor = c.sobreNavy.copy(alpha = 0.4f),
                        focusedContainerColor = c.superficie, unfocusedContainerColor = c.superficie,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Sania.dim.sm))
                Text("✕", color = c.sobreNavy, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onCerrar() }.padding(8.dp))
            }

            when {
                pacientes == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = c.navy)
                }
                filtrados.isEmpty() -> Box(Modifier.fillMaxSize().padding(Sania.dim.xl), Alignment.Center) {
                    Text(
                        if (query.isBlank()) "Escribe para buscar un paciente." else "Sin resultados para \"$query\".",
                        color = c.textoSuave, fontSize = Sania.txt.cuerpo,
                    )
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Sania.dim.sm)) {
                    items(filtrados, key = { it.id }) { p ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { onAbrirFicha(p) }
                                .padding(horizontal = Sania.dim.lg, vertical = Sania.dim.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Semáforo (si tiene)
                            val punto = when (p.flag) { "verde" -> "🟢"; "amarillo" -> "🟡"; "rojo" -> "🔴"; else -> "•" }
                            Text(punto, fontSize = 12.sp)
                            Spacer(Modifier.width(Sania.dim.sm))
                            Column(Modifier.weight(1f)) {
                                Text(p.nombre, color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                val sub = listOfNotNull(
                                    p.dni?.let { "DNI $it" },
                                    p.diagnostico?.takeIf { it.isNotBlank() },
                                ).joinToString(" · ")
                                if (sub.isNotBlank()) Text(sub, color = c.textoSuave, fontSize = Sania.txt.mini, maxLines = 1)
                            }
                            Text("›", color = c.textoSuave, fontSize = 18.sp)
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = Sania.dim.lg).background(c.borde))
                    }
                }
            }
        }
    }
}
