package pe.saniape.app.ui.clinica

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.filtrarOpciones
import pe.saniape.app.data.staff.resaltarCoincidencias
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.theme.Sania

// SELECTOR CON BUSCADOR para las listas largas del catálogo de servicios
// (dueño, 24/10/2026: Centro OMER ~38 servicios). Gemelo de
// components/ui/SelectorBuscable (web): filtra sin tildes y con las palabras en
// cualquier orden (data/staff/BuscarOpciones.kt), resalta lo que coincide y
// muestra el precio de lista en gris a la derecha. Sin LazyColumn: va dentro de
// formularios con verticalScroll (un Lazy ahí colapsa a altura 0).

/** El nombre con lo que coincide con [consulta] en negrita subrayada. */
@Composable
fun TextoResaltado(
    etiqueta: String, consulta: String, modifier: Modifier = Modifier,
    color: Color = Sania.colors.texto, fontSize: TextUnit = Sania.txt.cuerpo,
) {
    val tramos = remember(etiqueta, consulta) { resaltarCoincidencias(etiqueta, consulta) }
    Text(
        buildAnnotatedString {
            tramos.forEach { t ->
                if (t.coincide) withStyle(SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) { append(t.texto) }
                else append(t.texto)
            }
        },
        color = color, fontSize = fontSize, modifier = modifier,
    )
}

/** El campo "🔎 Buscar…" de los selectores. */
@Composable
private fun CampoBuscar(valor: String, placeholder: String, onCambio: (String) -> Unit) {
    OutlinedTextField(
        value = valor, onValueChange = onCambio,
        placeholder = { Text(placeholder, color = Sania.colors.textoSuave, fontSize = 13.sp) },
        singleLine = true, colors = coloresCampoForm(),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Una opción de la lista: nombre resaltado y, a la derecha, el detalle en gris (precio). */
@Composable
private fun FilaOpcion(nombre: String, detalle: String?, consulta: String, elegida: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().background(if (elegida) c.chipBg else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextoResaltado(nombre, consulta, Modifier.weight(1f))
        detalle?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, color = c.textoSuave, fontSize = 12.sp, maxLines = 1)
        }
    }
}

/**
 * Selector que se despliega en el formulario (como el de siempre) con un buscador
 * arriba. [detalle] = texto gris a la derecha de cada opción (el precio de lista).
 */
@Composable
fun <T> SelectorBuscable(
    items: List<T>,
    elegido: T?,
    etiqueta: (T) -> String,
    placeholder: String,
    onElegir: (T) -> Unit,
    detalle: (T) -> String? = { null },
    textoBuscar: String = "🔎 Buscar servicio…",
) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(false) }
    var consulta by remember { mutableStateOf("") }
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    Column {
        Row(
            Modifier.fillMaxWidth().clip(forma).background(c.superficie).border(1.dp, c.borde, forma)
                .clickable(role = Role.Button) { abierto = !abierto; consulta = "" }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(elegido?.let(etiqueta) ?: placeholder, color = c.texto, fontSize = Sania.txt.cuerpo, modifier = Modifier.weight(1f))
            Text(if (abierto) "▴" else "▾", color = c.navy, fontSize = 12.sp)
        }
        if (abierto) {
            Column(
                Modifier.fillMaxWidth().padding(top = 4.dp).clip(forma).background(c.superficie).border(1.dp, c.borde, forma),
            ) {
                if (items.isEmpty()) {
                    Text("(Sin opciones)", color = c.textoSuave, fontSize = Sania.txt.pequeno,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp))
                } else {
                    Column(Modifier.padding(8.dp)) { CampoBuscar(consulta, textoBuscar) { consulta = it } }
                    val lista = remember(items, consulta) { filtrarOpciones(items, consulta, etiqueta) }
                    // Alto acotado con su propio scroll (dentro del scroll del formulario).
                    Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                        if (lista.isEmpty()) {
                            Text("Sin coincidencias", color = c.textoSuave, fontSize = Sania.txt.pequeno,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp))
                        }
                        lista.forEach { item ->
                            FilaOpcion(etiqueta(item), detalle(item), consulta, elegida = item == elegido) {
                                onElegir(item); abierto = false; consulta = ""
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * La misma búsqueda en un diálogo (para selectores que no caben desplegados en
 * su fila: la pieza dental, los pasos encadenados…). [opcionVacia]: una primera
 * opción que elige null ("Crear sin servicio…"); null = no hay.
 * Respeta el teclado (AlertaConTeclado, Android 16).
 */
@Composable
fun <T> DialogoElegirBuscable(
    titulo: String,
    opciones: List<T>,
    etiqueta: (T) -> String,
    onElegir: (T?) -> Unit,
    onCerrar: () -> Unit,
    detalle: (T) -> String? = { null },
    opcionVacia: String? = null,
    textoVacio: String = "Sin coincidencias",
    textoBuscar: String = "🔎 Buscar servicio…",
) {
    val c = Sania.colors
    var consulta by remember { mutableStateOf("") }
    pe.saniape.app.ui.AlertaConTeclado(
        onDismissRequest = onCerrar,
        containerColor = c.superficie,
        title = { Text(titulo, color = c.texto, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                CampoBuscar(consulta, textoBuscar) { consulta = it }
                Spacer(Modifier.padding(top = 8.dp))
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    if (opcionVacia != null && consulta.isBlank()) {
                        FilaOpcion(opcionVacia, null, "", elegida = false) { onElegir(null) }
                    }
                    val lista = remember(opciones, consulta) { filtrarOpciones(opciones, consulta, etiqueta) }
                    if (lista.isEmpty()) Text(textoVacio, color = c.textoSuave, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                    lista.forEach { o -> FilaOpcion(etiqueta(o), detalle(o), consulta, elegida = false) { onElegir(o) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar", color = c.textoSuave) } },
    )
}
