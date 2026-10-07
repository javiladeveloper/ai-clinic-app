package pe.saniape.app.ui.clinica

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.ChipsRepo
import pe.saniape.app.data.staff.chipsConBase
import pe.saniape.app.data.staff.chipsConBaseDelServidor
import pe.saniape.app.ui.theme.Sania

/**
 * Los chips de siempre ([base]: los de la especialidad, personalizados o del
 * código) + unos pocos del pool del rubro detrás (gemelo de `useChipsConPool`).
 * Mientras carga, o sin pool, devuelve la base tal cual: nada cambia de lugar.
 * UN viaje por (campo, especialidad) en toda la sesión ([ChipsRepo] cachea; si
 * ya está en caché se pinta en el primer cuadro).
 */
@Composable
fun rememberChipsConPool(
    campo: String,
    especialidadId: String?,
    base: List<String>,
    activo: Boolean = true,
    extras: Int = 4,
): List<String> {
    var crudos by remember(campo, especialidadId) { mutableStateOf(ChipsRepo.enCache(campo, especialidadId)) }
    LaunchedEffect(campo, especialidadId, activo) {
        if (activo && crudos == null) crudos = runCatching { ChipsRepo.cargar(campo, especialidadId) }.getOrNull()
    }
    return remember(crudos, base, extras) {
        val c = crudos
        if (c.isNullOrEmpty()) base else chipsConBase(c, base, extras)
    }
}

/**
 * Chips cuya BASE la arma el servidor (`fuente: "base"`): con especialidad null,
 * la unión de las especialidades activas respetando chips_tipos/chips_sintomas.
 * Así el formulario no pide además las especialidades (un viaje por campo la
 * primera vez, cero después) y no parpadea de los genéricos a los de la clínica:
 * hasta tener la respuesta (o la caché) no muestra nada.
 */
@Composable
fun rememberChipsDelServidor(
    campo: String,
    especialidadId: String? = null,
    activo: Boolean = true,
    extras: Int = 4,
): List<String> {
    var crudos by remember(campo, especialidadId) { mutableStateOf(ChipsRepo.enCache(campo, especialidadId)) }
    LaunchedEffect(campo, especialidadId, activo) {
        if (activo && crudos == null) crudos = runCatching { ChipsRepo.cargar(campo, especialidadId) }.getOrNull()
    }
    return remember(crudos, extras) { crudos?.let { chipsConBaseDelServidor(it, extras) }.orEmpty() }
}

/** Patología y síntomas sugeridos para el paciente: base de las especialidades activas + pool. */
@Composable
fun rememberChipsPaciente(activo: Boolean): ChipsEspecialidad = ChipsEspecialidad(
    tipos = rememberChipsDelServidor(pe.saniape.app.data.staff.CampoChip.PATOLOGIA, null, activo),
    sintomas = rememberChipsDelServidor(pe.saniape.app.data.staff.CampoChip.SINTOMA, null, activo),
)

/**
 * Fila de chips tocables (agrega/quita). Muestra [maxVisibles] y un "+N más"
 * para no tapar el formulario en el celular.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilaChipsSugeridos(
    chips: List<String>,
    puesto: (String) -> Boolean,
    onToca: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxVisibles: Int = 10,
) {
    if (chips.isEmpty()) return
    val c = Sania.colors
    var verTodos by remember { mutableStateOf(false) }
    val visibles = if (verTodos) chips else chips.take(maxVisibles)
    FlowRow(
        modifier = modifier.padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        visibles.forEach { chip ->
            val si = puesto(chip)
            Box(
                Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                    .background(if (si) c.navy else c.chipBg)
                    .border(1.dp, if (si) c.navy else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
                    .clickable { onToca(chip) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text("${if (si) "✓" else "+"} $chip", color = if (si) c.sobreNavy else c.textoSuave,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (chips.size > maxVisibles) {
            Box(
                Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                    .border(1.dp, c.navy, RoundedCornerShape(Sania.shape.pill.dp))
                    .clickable { verTodos = !verTodos }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(if (verTodos) "− menos" else "+ ${chips.size - maxVisibles} más", color = c.navy,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
