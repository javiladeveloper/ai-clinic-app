package pe.saniape.app.ui.clinica.atencion

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.ui.theme.Sania

// Dosis, frecuencia y duración ESTANDARIZADAS (RecetaPosologia.kt, gemelo de
// components/recetas/PosologiaCampos.tsx, 19/11/2026). Guardan el texto de siempre;
// lo que no encaja va en «Otra…» (texto libre).

private const val OTRA = "__otra__"

@Composable
private fun VolverALaLista(onClick: () -> Unit) {
    Text("usar la lista", color = Sania.colors.navy, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clickable(onClick = onClick).padding(top = 2.dp))
}

@Composable
internal fun CampoDosisReceta(valor: String, forma: String, modifier: Modifier, onCambio: (String) -> Unit) {
    val leido = leerDosis(valor)
    var libre by remember { mutableStateOf(valor.isNotBlank() && leido == null) }
    val cantidad = leido?.first.orEmpty()
    val unidad = leido?.second?.ifEmpty { null } ?: unidadDosisDeForma(forma)
    // La unidad sigue a la forma mientras el médico no la cambió a mano: si la que está
    // es la que tocaba a la forma anterior (o ninguna), pasa a la de la nueva.
    var formaAntes by remember { mutableStateOf(forma) }
    LaunchedEffect(forma) {
        val antes = formaAntes
        formaAntes = forma
        if (libre || cantidad.isEmpty()) return@LaunchedEffect
        val nueva = unidadDosisDeForma(forma)
        val actual = leido?.second.orEmpty()
        if ((actual.isEmpty() || actual == unidadDosisDeForma(antes)) && actual != nueva) onCambio(textoDosis(cantidad, nueva))
    }
    if (libre) {
        Column(modifier) {
            CampoDialogo("Dosis por toma", valor, onCambio, "1 tableta")
            VolverALaLista { libre = false; onCambio("") }
        }
        return
    }
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SelectorDialogo("Dosis", cantidad, CANTIDADES_DOSIS.map { it to it }, Modifier.width(76.dp)) { c ->
                onCambio(if (c.isBlank()) "" else textoDosis(c, unidad))
            }
            SelectorDialogo("Unidad", unidad, UNIDADES_DOSIS.map { it.clave to it.singular } + (OTRA to "Otra…"), Modifier.weight(1f)) { u ->
                if (u == OTRA) { libre = true; return@SelectorDialogo }
                if (u.isNotBlank()) onCambio(textoDosis(cantidad.ifBlank { "1" }, u))
            }
        }
    }
}

@Composable
internal fun CampoFrecuenciaReceta(valor: String, modifier: Modifier, onCambio: (String) -> Unit) {
    val deLista = frecuenciaDeLista(valor)
    var libre by remember { mutableStateOf(valor.isNotBlank() && deLista == null) }
    // «8» o «c/8h» guardado → queda como «cada 8 horas».
    LaunchedEffect(deLista) { if (deLista != null && deLista != valor) onCambio(deLista) }
    if (libre) {
        Column(modifier) {
            CampoDialogo("Frecuencia", valor, onCambio, "cada 8 horas por 3 días, luego…", capitalizacion = KeyboardCapitalization.None)
            VolverALaLista { libre = false; onCambio("") }
        }
        return
    }
    SelectorDialogo("Frecuencia", deLista.orEmpty(), FRECUENCIAS_RECETA.map { it to it } + (OTRA to "Otra…"), modifier) { f ->
        if (f == OTRA) { libre = true; onCambio("") } else onCambio(f)
    }
}

@Composable
internal fun CampoDuracionReceta(valor: String, modifier: Modifier, onCambio: (String) -> Unit) {
    val leido = leerDuracion(valor)
    var libre by remember { mutableStateOf(valor.isNotBlank() && leido == null) }
    var unidad by remember { mutableStateOf(leido?.unidad ?: "días") }
    val continuo = leido?.continuo == true
    if (libre) {
        Column(modifier) {
            CampoDialogo("Duración", valor, onCambio, "7 días", capitalizacion = KeyboardCapitalization.None)
            VolverALaLista { libre = false; onCambio("") }
        }
        return
    }
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CampoDialogo("Duración", if (continuo) "" else leido?.numero.orEmpty(),
                { v -> onCambio(textoDuracion(v.filter { it.isDigit() }.take(3), unidad)) },
                "7", teclado = KeyboardType.Number, modifier = Modifier.width(76.dp))
            SelectorDialogo("", unidad, UNIDADES_DURACION.map { it to it } + (OTRA to "Otra…"), Modifier.weight(1f).padding(top = 18.dp)) { u ->
                if (u == OTRA) { libre = true; return@SelectorDialogo }
                if (u.isBlank()) return@SelectorDialogo
                unidad = u
                leido?.numero?.takeIf { it.isNotBlank() && !continuo }?.let { onCambio(textoDuracion(it, u)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = continuo, onCheckedChange = { onCambio(if (it) USO_CONTINUO else "") })
            Text("Uso continuo", color = Sania.colors.textoSuave, fontSize = 12.sp)
        }
    }
}
