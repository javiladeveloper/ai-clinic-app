package pe.saniape.app.ui.clinica.agenda.componentes

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import pe.saniape.app.data.staff.TerapeutaRef
import pe.saniape.app.ui.theme.Sania

private val ESTADOS = listOf("Pendiente", "Confirmada", "Completada", "Cancelada")
private val TIPOS = listOf("Consulta", "Evaluación", "Sesión")

/**
 * Filtros de la agenda — diseño móvil limpio (como la web): búsqueda + un botón
 * "Filtros" que despliega dropdowns compactos (Estado, Tipo, Profesional) + toggle
 * "Ver historial". Por defecto solo se ve la búsqueda; los filtros no abruman.
 */
@Composable
fun FiltrosAgenda(
    busqueda: String, onBusqueda: (String) -> Unit,
    filtroEstado: String?, onEstado: (String?) -> Unit,
    filtroTipo: String?, onTipo: (String?) -> Unit,
    // Filtro por profesional (solo gestores sin scope propio).
    terapeutas: List<TerapeutaRef> = emptyList(),
    filtroTerapeuta: String? = null, onTerapeuta: (String?) -> Unit = {},
    puedeFiltrarPorPersonal: Boolean = false,
    // Ver historial (citas pasadas) — toggle.
    verHistorial: Boolean = false, onVerHistorial: () -> Unit = {},
) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(false) }

    // Cuántos filtros hay activos (para el contador del botón).
    val activos = listOf(filtroEstado, filtroTipo, filtroTerapeuta).count { it != null }

    Column(Modifier.fillMaxWidth().padding(horizontal = Sania.dim.lg, vertical = Sania.dim.sm)) {
        // Fila: búsqueda + botón Filtros
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = busqueda, onValueChange = onBusqueda,
                placeholder = { Text("🔍 Buscar paciente…", color = c.textoSuave) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            BotonFiltros(activos = activos, abierto = abierto) { abierto = !abierto }
        }

        if (abierto) {
            Spacer(Modifier.height(Sania.dim.sm))
            // Estado
            DropdownFiltro(
                etiqueta = "Estado",
                valor = filtroEstado?.let { if (it == "Pendiente") "Sin confirmar" else it },
                opciones = listOf<Pair<String?, String>>(null to "Todos los estados") +
                    ESTADOS.map { it as String? to (if (it == "Pendiente") "Sin confirmar" else it) },
                onElegir = onEstado,
            )
            Spacer(Modifier.height(6.dp))
            // Tipo
            DropdownFiltro(
                etiqueta = "Tipo",
                valor = filtroTipo,
                opciones = listOf<Pair<String?, String>>(null to "Todos los tipos") +
                    TIPOS.map { it as String? to it },
                onElegir = onTipo,
            )
            // Profesional (solo gestores)
            if (puedeFiltrarPorPersonal && terapeutas.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                DropdownFiltro(
                    etiqueta = "Profesional",
                    valor = terapeutas.find { it.id == filtroTerapeuta }?.nombre,
                    opciones = listOf<Pair<String?, String>>(null to "👥 Todo el personal") +
                        terapeutas.map { it.id as String? to it.nombre },
                    onElegir = onTerapeuta,
                )
            }
            Spacer(Modifier.height(Sania.dim.sm))
            // Ver historial — toggle
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(if (verHistorial) c.navy else c.superficie)
                    .border(1.dp, if (verHistorial) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                    .clickable { onVerHistorial() }.padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    if (verHistorial) "📅 Solo próximas" else "📜 Ver historial",
                    color = if (verHistorial) c.sobreNavy else c.navy,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun BotonFiltros(activos: Int, abierto: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    val activo = activos > 0 || abierto
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(if (activo) c.navy else c.superficie)
            .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text(
            if (activos > 0) "⚙ Filtros ($activos)" else "⚙ Filtros",
            color = if (activo) c.sobreNavy else c.texto,
            fontSize = 12.sp, fontWeight = FontWeight.Bold,
        )
    }
}

/** Dropdown compacto de un filtro (igual rol que un <select> de la web). */
@Composable
private fun DropdownFiltro(
    etiqueta: String, valor: String?,
    opciones: List<Pair<String?, String>>, onElegir: (String?) -> Unit,
) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(false) }
    Column {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                .clickable { abierto = !abierto }.padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(valor ?: etiqueta, color = if (valor != null) c.texto else c.textoSuave, fontSize = 13.sp,
                fontWeight = if (valor != null) FontWeight.Bold else FontWeight.Normal)
            Text("▾", color = c.navy)
        }
        if (abierto) {
            LazyColumn(
                Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(max = 240.dp)
                    .clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie)
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)),
            ) {
                items(opciones) { (id, label) ->
                    Text(label, color = c.texto, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().clickable { onElegir(id); abierto = false }
                            .padding(horizontal = 12.dp, vertical = 11.dp))
                }
            }
        }
    }
}