package pe.saniape.app.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.GrupoMenu
import pe.saniape.app.ui.theme.Sania
import pe.saniape.app.ui.theme.tocable

/**
 * «⋯ Más»: el resto de las acciones de una tarjeta, por grupo y con lo peligroso
 * en rojo al final (el reparto lo hace `repartirAcciones`). Es un DropdownMenu
 * (otra capa, como el de la tarjeta de cita y el de /sesiones): no hace crecer
 * la tarjeta al abrirse y se cierra al tocar afuera.
 *
 * Sin grupos no se dibuja nada. [disparador] permite otro botón (p. ej. el ⋯
 * redondo de la barra de la ficha); por defecto, la píldora «⋯ Más».
 */
@Composable
fun <A> MenuMas(
    grupos: List<GrupoMenu<A>>,
    texto: (A) -> String,
    onAccion: (A) -> Unit,
    rojo: (A) -> Boolean = { false },
    habilitado: Boolean = true,
    /** Lo que dice el lector de pantalla del botón. */
    descripcion: String = "Más acciones",
    /** Un ítem puede estar a la vista pero apagado (p. ej. mientras se guarda). */
    itemHabilitado: (A) -> Boolean = { true },
    disparador: @Composable (abrir: () -> Unit) -> Unit = { abrir -> BotonMas(habilitado, descripcion, onClick = abrir) },
) {
    if (grupos.none { it.acciones.isNotEmpty() }) return
    val c = Sania.colors
    var abierto by remember { mutableStateOf(false) }
    Box {
        disparador { if (habilitado) abierto = true }
        DropdownMenu(
            expanded = abierto, onDismissRequest = { abierto = false },
            modifier = Modifier.background(c.superficie),
        ) {
            grupos.filter { it.acciones.isNotEmpty() }.forEachIndexed { i, g ->
                if (i > 0) Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(c.borde))
                if (g.titulo.isNotBlank()) {
                    Text(g.titulo.uppercase(), color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
                g.acciones.forEach { a ->
                    DropdownMenuItem(
                        text = {
                            Text(texto(a), color = if (rojo(a)) c.error else c.texto,
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        },
                        enabled = habilitado && itemHabilitado(a),
                        onClick = { abierto = false; onAccion(a) },
                    )
                }
            }
        }
    }
}

/** La píldora «⋯ Más» (el mismo tamaño que los botones de la tarjeta). */
@Composable
fun BotonMas(
    habilitado: Boolean = true,
    descripcion: String = "Más acciones",
    texto: String = "⋯ Más",
    color: Color = Sania.colors.textoSuave,
    onClick: () -> Unit,
) {
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color, RoundedCornerShape(Sania.shape.sm.dp))
            .semantics { contentDescription = descripcion; role = Role.Button }
            .tocable(habilitado = habilitado) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(texto, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

/**
 * Botón de acción de una tarjeta: lleno para la primaria, contorno tintado para
 * las secundarias (el mismo dibujo que la tarjeta de cita).
 */
@Composable
fun BotonTarjeta(
    texto: String,
    color: Color,
    habilitado: Boolean = true,
    lleno: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = Sania.colors
    // Texto sobre el color lleno: blanco sobre navy; sobre los de estado, el de la tarjeta.
    val sobre = if (color == c.navy) c.sobreNavy else c.superficie
    Box(
        modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(if (lleno) color else color.copy(alpha = 0.12f))
            .border(1.dp, color, RoundedCornerShape(Sania.shape.sm.dp))
            .semantics { role = Role.Button }
            .tocable(habilitado = habilitado) { onClick() }
            .padding(horizontal = if (lleno) 16.dp else 12.dp, vertical = 8.dp),
    ) { Text(texto, color = if (lleno) sobre else color, fontSize = if (lleno) 13.sp else 12.sp, fontWeight = FontWeight.Bold) }
}
