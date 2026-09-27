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
import pe.saniape.app.data.staff.EspecialidadRef
import pe.saniape.app.data.staff.SIN_ESPECIALIDAD
import pe.saniape.app.data.staff.alternarOpcion
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import pe.saniape.app.ui.theme.Sania
import pe.saniape.app.data.staff.FlujoClinica

private val ESTADOS = listOf("Pendiente", "Confirmada", "Completada", "Cancelada")
// Los tipos internos, que NO cambian: son la mecánica del flujo y con lo que se
// guardan las citas. Lo que sí cambia es su etiqueta y cuáles se ofrecen.
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
    flujo: FlujoClinica = FlujoClinica(),
    // Filtro por especialidad, VARIAS a la vez (gemelo del de /citas web). Solo se
    // muestra si [muestraEspecialidad]: 2+ especialidades y agenda de todos (el
    // profesional vinculado ve la suya, sin filtro). Vacío = Todas.
    especialidades: List<EspecialidadRef> = emptyList(),
    muestraEspecialidad: Boolean = false,
    seleccionEspecialidades: List<String> = emptyList(),
    onEspecialidades: (List<String>) -> Unit = {},
    // Ver historial (citas pasadas) — toggle.
    verHistorial: Boolean = false, onVerHistorial: () -> Unit = {},
) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(false) }

    // Cuántos filtros hay activos (para el contador del botón).
    val activos = listOf(filtroEstado, filtroTipo).count { it != null } +
        (if (muestraEspecialidad && seleccionEspecialidades.isNotEmpty()) 1 else 0)

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
                // Solo los tipos que esta clínica usa, con SU nombre: ofrecer
                // "Consulta" a una clínica que solo hace evaluaciones es un
                // filtro que siempre da vacío.
                opciones = listOf<Pair<String?, String>>(null to "Todos los tipos") +
                    TIPOS.filter { flujo.usaTipo(it) }.map { it as String? to flujo.nombreTipo(it) },
                onElegir = onTipo,
            )
            // Especialidad (si la clínica tiene más de una): chips, varias a la vez.
            if (muestraEspecialidad) {
                Spacer(Modifier.height(Sania.dim.sm))
                ChipsEspecialidad(especialidades, seleccionEspecialidades, onEspecialidades)
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

/**
 * Chips del filtro de especialidad: "Todas", cada especialidad con su color y
 * "Sin especialidad" (las citas a las que no se les puede saber). Se envuelven
 * en filas: a 390 px no se desbordan.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipsEspecialidad(
    especialidades: List<EspecialidadRef>,
    seleccion: List<String>,
    onCambio: (List<String>) -> Unit,
) {
    val c = Sania.colors
    val ids = especialidades.map { it.id }
    Column {
        Text("Especialidades", color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ChipOpcion("Todas", marcado = seleccion.isEmpty(), color = null) { onCambio(emptyList()) }
            especialidades.forEach { e ->
                ChipOpcion(
                    (e.icono?.let { "$it " } ?: "") + e.nombre,
                    marcado = e.id in seleccion, color = colorEspecialidad(e.color),
                ) { onCambio(alternarOpcion(seleccion, e.id, ids)) }
            }
            ChipOpcion("Sin especialidad", marcado = SIN_ESPECIALIDAD in seleccion, color = null) {
                onCambio(alternarOpcion(seleccion, SIN_ESPECIALIDAD, ids))
            }
        }
    }
}

/** Un chip marcable. El texto va siempre en el color de texto del tema (legible en claro
 *  y en oscuro); el color de la especialidad va en el punto y en el borde. */
@Composable
private fun ChipOpcion(texto: String, marcado: Boolean, color: Color?, onClick: () -> Unit) {
    val c = Sania.colors
    val acento = color ?: c.navy
    Row(
        Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(Sania.shape.pill.dp))
            .background(if (marcado) acento.copy(alpha = 0.16f) else c.superficie)
            .border(if (marcado) 1.5.dp else 1.dp, if (marcado) acento else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (marcado) {
            Text("✓", color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(5.dp))
        } else if (color != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
        }
        Text(texto, color = c.texto, fontSize = 12.sp,
            fontWeight = if (marcado) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}

/** Color hex de la especialidad ("#16a34a"); null si no hay o no se entiende. */
private fun colorEspecialidad(hex: String?): Color? = hex?.takeIf { it.isNotBlank() }?.let {
    runCatching { Color(("ff" + it.removePrefix("#").take(6)).toLong(16)) }.getOrNull()
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