package pe.saniape.app.ui.clinica.pacientes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.LineaRecomendacion
import pe.saniape.app.data.staff.ProductoVenta
import pe.saniape.app.data.staff.Recomendacion
import pe.saniape.app.data.staff.RecomendacionesRepo
import pe.saniape.app.data.staff.fechaLegibleCorta
import pe.saniape.app.data.staff.textoItem
import pe.saniape.app.data.staff.totalComprado
import pe.saniape.app.data.staff.totalLineas
import pe.saniape.app.data.staff.totalRecomendacion
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.agenda.modales.textoSoles
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania

/**
 * 🛍️ Compras de la ficha (24/11/2026, como la web): las proformas de productos
 * recomendados, con su estado (pendiente, compró una parte, compró todo, no compró),
 * el envío por WhatsApp, la proforma para imprimir y «Recomendar productos».
 * Vender queda en la web (la app no tiene punto de venta).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContenidoCompras(pacienteId: String, citaId: String? = null) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val acciones = recordarAcciones()
    var lista by remember(pacienteId) { mutableStateOf<List<Recomendacion>?>(null) }
    var error by remember(pacienteId) { mutableStateOf(false) }
    var intento by remember { mutableIntStateOf(0) }
    var recomendando by remember { mutableStateOf(false) }
    var ocupado by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pacienteId, intento) {
        val r = RecomendacionesRepo.listar(pacienteId)
        error = r == null
        lista = r
    }

    fun enviar(rec: Recomendacion, porWhatsApp: Boolean) {
        if (ocupado != null) return
        ocupado = rec.id
        scope.launch {
            val (r, err) = RecomendacionesRepo.enlace(rec.id)
            ocupado = null
            if (err != null || r == null) { Toaster.error(err ?: "No se pudo preparar el enlace"); return@launch }
            val (url, wa) = r
            if (porWhatsApp && wa != null) acciones.abrirUrl(wa)
            else {
                if (porWhatsApp) Toaster.error("El paciente no tiene teléfono: se abre la proforma para compartirla.")
                acciones.abrirUrl(url)
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🛍️ PRODUCTOS RECOMENDADOS", color = c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("🛍️ Recomendar", color = c.sobreNavy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.navy)
                    .clickable { recomendando = true }.padding(horizontal = 12.dp, vertical = 9.dp))
        }
        Spacer(Modifier.height(10.dp))
        val l = lista
        when {
            error -> Text("No se pudieron leer las proformas. Revisa tu conexión.", color = c.error, fontSize = 13.sp,
                modifier = Modifier.clickable { intento++ })
            l == null -> Text("Cargando…", color = c.textoSuave, fontSize = 13.sp)
            l.isEmpty() -> Text("Todavía no se le recomendaron productos. Con «Recomendar» armas la proforma: el paciente se la lleva o la recibe por WhatsApp.",
                color = c.textoSuave, fontSize = 13.sp)
            else -> l.forEach { rec ->
                val (fg, bg) = when (rec.estado) {
                    "Comprada" -> c.ok to c.okBg
                    "Parcial" -> c.info to c.infoBg
                    "No compró" -> c.textoSuave to c.chipBg
                    else -> c.pend to c.pendBg
                }
                val etiqueta = when (rec.estado) { "Comprada" -> "Compró todo"; "Parcial" -> "Compró una parte"; else -> rec.estado }
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
                        .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fechaLegibleCorta(rec.creada.take(10)), color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text(etiqueta, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp))
                    }
                    rec.items.forEach { i ->
                        Spacer(Modifier.height(6.dp))
                        Row {
                            Text(textoItem(i), color = c.texto, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text(textoSoles(i.cantidad * i.precioUnit), color = c.texto, fontSize = 13.sp)
                        }
                        i.indicacion?.let { Text(it, color = c.textoSuave, fontSize = 12.sp) }
                    }
                    rec.nota?.let { Text("📝 $it", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                    Spacer(Modifier.height(8.dp))
                    val comprado = totalComprado(rec.items)
                    Text("Total ${textoSoles(totalRecomendacion(rec.items))}" + if (comprado > 0) " · compró ${textoSoles(comprado)}" else "",
                        color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    val ocupadoAqui = ocupado == rec.id
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Boton(if (ocupadoAqui) "Preparando…" else "📲 WhatsApp", !ocupadoAqui) { enviar(rec, true) }
                        Boton("🖨 Proforma", !ocupadoAqui) { enviar(rec, false) }
                        val falta = rec.items.any { it.comprado < it.cantidad }
                        if (falta && rec.estado != "No compró") Boton("No compró", true) {
                            scope.launch { if (RecomendacionesRepo.marcar(rec.id, "No compró")) intento++ else Toaster.error("No se pudo guardar") }
                        }
                        if (rec.estado == "No compró") Boton("Reabrir", true) {
                            scope.launch { if (RecomendacionesRepo.marcar(rec.id, "Pendiente")) intento++ else Toaster.error("No se pudo guardar") }
                        }
                    }
                    if (rec.items.any { it.comprado < it.cantidad } && rec.estado != "No compró") {
                        Text("Para venderle lo que falta: «Vender» en la web.", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }

    if (recomendando) {
        DialogoRecomendar(pacienteId, citaId, onCerrar = { recomendando = false }, onGuardado = { recomendando = false; intento++ })
    }
}

@Composable
private fun Boton(texto: String, habilitado: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Text(texto, color = if (habilitado) c.navy else c.textoSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .clickable(enabled = habilitado, onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp))
}

/** Armar la proforma: elegir productos, cantidad, precio e indicación de cada uno, y una nota. */
@Composable
fun DialogoRecomendar(pacienteId: String, citaId: String?, onCerrar: () -> Unit, onGuardado: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var productos by remember { mutableStateOf<List<ProductoVenta>?>(null) }
    var q by remember { mutableStateOf("") }
    var lineas by remember { mutableStateOf<List<LineaRecomendacion>>(emptyList()) }
    var nota by remember { mutableStateOf("") }
    var guardando by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { productos = RecomendacionesRepo.productos() }

    fun guardar() {
        if (guardando || lineas.isEmpty()) return
        guardando = true
        scope.launch {
            val err = RecomendacionesRepo.crear(pacienteId, citaId, lineas, nota)
            guardando = false
            if (err != null) Toaster.error(err)
            else { Toaster.exito("Proforma guardada: ya puedes enviarla o imprimirla."); onGuardado() }
        }
    }

    DialogoForm(
        titulo = "🛍️ Recomendar productos",
        subtitulo = "Total ${textoSoles(totalLineas(lineas))}",
        textoAccion = if (guardando) "Guardando…" else "Guardar proforma",
        accionHabilitada = !guardando && lineas.isNotEmpty() && lineas.all { (it.cantidad.toIntOrNull() ?: 0) > 0 },
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = { guardar() },
    ) {
        lineas.forEachIndexed { i, l ->
            Column(
                Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(l.producto.nombre, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("✕", color = c.error, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { lineas = lineas.filterIndexed { j, _ -> j != i } }.padding(6.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = l.cantidad, onValueChange = { v -> lineas = lineas.mapIndexed { j, x -> if (j == i) x.copy(cantidad = v.filter(Char::isDigit).take(3)) else x } },
                        label = { Text("Cant.") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = coloresCampoForm(), modifier = Modifier.width(90.dp),
                    )
                    OutlinedTextField(
                        value = l.precio, onValueChange = { v -> lineas = lineas.mapIndexed { j, x -> if (j == i) x.copy(precio = v.filter { it.isDigit() || it == '.' || it == ',' }.take(9)) else x } },
                        label = { Text("Precio") }, placeholder = { Text(textoSoles(l.producto.precio), color = c.textoSuave) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = coloresCampoForm(), modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = l.indicacion, onValueChange = { v -> lineas = lineas.mapIndexed { j, x -> if (j == i) x.copy(indicacion = v.take(300)) else x } },
                    placeholder = { Text("Para qué es o cómo usarlo (opcional)", color = c.textoSuave) },
                    colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
        if (lineas.isNotEmpty()) {
            OutlinedTextField(
                value = nota, onValueChange = { nota = it.take(1000) },
                placeholder = { Text("Nota para el paciente (opcional): garantía, cuándo volver, forma de pago…", color = c.textoSuave) },
                colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth(), minLines = 2,
            )
            Spacer(Modifier.height(10.dp))
        }
        EtqForm("Agregar producto")
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            placeholder = { Text("Buscar producto…", color = c.textoSuave) },
            colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        val ps = productos
        when {
            ps == null -> Text("Cargando productos…", color = c.textoSuave, fontSize = 13.sp)
            ps.isEmpty() -> Text("No hay productos activos. Agrégalos en la web (Productos).", color = c.textoSuave, fontSize = 13.sp)
            else -> Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                ps.filter { q.isBlank() || it.nombre.contains(q.trim(), ignoreCase = true) || (it.categoria ?: "").contains(q.trim(), ignoreCase = true) }
                    .forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                                .clickable { lineas = lineas + LineaRecomendacion(p) }.padding(vertical = 9.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(p.nombre, color = c.texto, fontSize = 14.sp)
                                Text(listOfNotNull(p.categoria, p.stock?.let { "$it en stock" }).joinToString(" · "), color = c.textoSuave, fontSize = 11.sp)
                            }
                            Text("${textoSoles(p.precio)}  ＋", color = c.teal, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
            }
        }
        Box(Modifier.height(4.dp))
    }
}
