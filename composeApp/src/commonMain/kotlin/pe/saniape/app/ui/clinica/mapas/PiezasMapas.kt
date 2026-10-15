package pe.saniape.app.ui.clinica.mapas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.argbDeHex
import pe.saniape.app.ui.theme.Sania

/**
 * Piezas chicas compartidas por 🦶 Evaluación del pie y 💉 Aplicaciones (los
 * gemelos de los botones, chips y segmentados de la web). Todo con alto mínimo
 * de 44 dp para el dedo; sin listas perezosas (viven dentro del scroll de la
 * ficha o de un DialogoForm).
 */

/** "#rrggbb" → Color (hex de la web: igual en modo oscuro e impreso). */
internal fun colorHex(hex: String): Color = Color(argbDeHex(hex))

@Composable
internal fun TarjetaMapa(contenido: @Composable ColumnScope.() -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
        content = contenido,
    )
}

@Composable
internal fun BotonMapa(texto: String, lleno: Boolean = false, habilitado: Boolean = true, onClick: () -> Unit) {
    val c = Sania.colors
    val fondo = if (lleno) c.navy else c.superficie
    Text(texto, color = if (lleno) c.sobreNavy else if (habilitado) c.texto else c.textoSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.heightIn(min = 44.dp)
            .clip(RoundedCornerShape(Sania.shape.sm.dp)).background(fondo)
            .then(if (lleno) Modifier else Modifier.border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)))
            .clickable(enabled = habilitado, onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp))
}

/** Chip tocable (activo = navy). */
@Composable
internal fun ChipMapa(texto: String, activo: Boolean, colorActivo: Color? = null, onClick: () -> Unit) {
    val c = Sania.colors
    val fondo = if (activo) colorActivo ?: c.navy else c.superficie
    Box(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(Sania.shape.pill.dp)).background(fondo)
            .border(1.dp, if (activo) fondo else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
            .semantics { selected = activo; role = Role.Button }
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, color = if (activo) c.sobreNavy else c.texto, fontSize = 12.sp,
            fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal)
    }
}

/** Botones de opción única; tocar la elegida otra vez la quita (null) si [permiteVacio]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SegmentadoMapa(
    opciones: List<Pair<String, String>>,
    valor: String?,
    onChange: (String?) -> Unit,
    colores: Map<String, Color> = emptyMap(),
    permiteVacio: Boolean = true,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        opciones.forEach { (id, nombre) ->
            val activo = valor == id
            ChipMapa(nombre, activo, colores[id]) { onChange(if (activo && permiteVacio) null else id) }
        }
    }
}

@Composable
internal fun PildoraMapa(texto: String, color: Color, fondo: Color) {
    Text(texto, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(fondo).padding(horizontal = 8.dp, vertical = 2.dp))
}

@Composable
internal fun TituloSeccionMapa(t: String) {
    Text(t.uppercase(), color = Sania.colors.navy, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
}

@Composable
internal fun EtiquetaMapa(t: String) {
    Text(t.uppercase(), color = Sania.colors.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

@Composable
internal fun CajaErroresMapa(errores: List<String>) {
    if (errores.isEmpty()) return
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.errorBg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) { errores.forEach { Text(it, color = c.error, fontSize = 12.sp) } }
}

/** Aviso con borde (rojo = alerta, ámbar = advertencia, verde = guardado). */
@Composable
internal fun AvisoMapa(texto: String, color: Color, fondo: Color, negrita: Boolean = false) {
    Text(texto, color = Sania.colors.texto, fontSize = 12.sp, fontWeight = if (negrita) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(fondo)
            .border(1.dp, color, RoundedCornerShape(Sania.shape.sm.dp)).padding(horizontal = 10.dp, vertical = 8.dp))
}

/** Fila de "lo que hay": botones que se acomodan en varias líneas. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilaBotonesMapa(contenido: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { contenido() }
}

/** Fila con texto a la izquierda y algo a la derecha (o debajo si no cabe). */
@Composable
internal fun FilaEtiquetada(texto: String, contenido: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(texto, color = Sania.colors.texto, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
        Row { contenido() }
    }
}
