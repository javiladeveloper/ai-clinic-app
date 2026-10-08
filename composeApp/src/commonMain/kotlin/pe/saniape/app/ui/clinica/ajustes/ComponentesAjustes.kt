package pe.saniape.app.ui.clinica.ajustes

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.offline.ResultadoEscritura
import pe.saniape.app.data.staff.AjustesRepo
import pe.saniape.app.ui.AlertaConTeclado
import pe.saniape.app.ui.Gestion
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.conIndicador
import pe.saniape.app.ui.theme.Sania

// Piezas comunes de las secciones de Ajustes (mismo estilo que Equipo/Servicios).

/** Ejecuta una escritura con el indicador global; si el servidor la rechaza, muestra su mensaje. */
internal suspend fun guardarAjuste(
    gestion: Gestion = Gestion.GUARDANDO,
    porDefecto: String = "No se pudo guardar. Intenta de nuevo.",
    bloque: suspend () -> ResultadoEscritura,
): ResultadoEscritura {
    val r = conIndicador(gestion) { bloque() }
    if (!r.registrada) Toaster.error(r.rechazo?.error ?: porDefecto)
    return r
}

/** Cabecera navy con "← volver" y el título (como Equipo y Finanzas). */
@Composable
internal fun CabeceraAjustes(volver: String, titulo: String, onVolver: () -> Unit, extra: @Composable () -> Unit = {}) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                volver, color = c.sobreNavy, fontSize = Sania.txt.pequeno,
                modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).clickable { onVolver() }.padding(vertical = 2.dp),
            )
            Spacer(Modifier.height(2.dp))
            Text(titulo, color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        extra()
    }
}

/** Sub-pantalla de una sección: cabecera + cuerpo desplazable. */
@Composable
internal fun SubPantalla(titulo: String, onVolver: () -> Unit, volver: String = "← Ajustes", contenido: @Composable ColumnScope.() -> Unit) {
    val c = Sania.colors
    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            CabeceraAjustes(volver, titulo, onVolver)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Sania.dim.lg),
                verticalArrangement = Arrangement.spacedBy(Sania.dim.md),
            ) {
                contenido()
                Spacer(Modifier.height(Sania.dim.xxl))
            }
        }
    }
}

/** Tarjeta blanca con título opcional. */
@Composable
internal fun Tarjeta(titulo: String? = null, contenido: @Composable ColumnScope.() -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.md.dp)
    Column(
        Modifier.fillMaxWidth().clip(forma).background(c.superficie).border(1.dp, c.borde, forma).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        titulo?.let { Text(it, color = c.texto, fontSize = Sania.txt.seccion, fontWeight = FontWeight.Bold) }
        contenido()
    }
}

@Composable
internal fun Ayuda(t: String, color: Color = Sania.colors.textoSuave) {
    Text(t, color = color, fontSize = 12.sp, lineHeight = 16.sp)
}

/** Campo de texto con etiqueta (esquema estándar de formularios de la app). */
@Composable
internal fun Campo(
    etiqueta: String?,
    valor: String,
    onCambio: (String) -> Unit,
    placeholder: String = "",
    multilinea: Boolean = false,
    lineas: Int = 3,
    teclado: KeyboardType = KeyboardType.Text,
    mayusculas: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    max: Int? = null,
    habilitado: Boolean = true,
    error: String? = null,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val c = Sania.colors
    Column(modifier) {
        etiqueta?.let { EtqForm(it) }
        OutlinedTextField(
            value = valor,
            onValueChange = { v -> onCambio(if (max != null) v.take(max) else v) },
            placeholder = if (placeholder.isNotEmpty()) ({ Text(placeholder, color = c.textoSuave, fontSize = 13.sp) }) else null,
            singleLine = !multilinea,
            minLines = if (multilinea) lineas else 1,
            enabled = habilitado,
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = teclado, capitalization = mayusculas),
            colors = coloresCampoForm(),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
            modifier = Modifier.fillMaxWidth(),
        )
        when {
            error != null -> Text(error, color = c.error, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
            max != null && multilinea -> Text("${valor.length}/$max", color = c.textoSuave, fontSize = 10.sp,
                textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
        }
    }
}

/** Interruptor con título y detalle (fila completa tocable). */
@Composable
internal fun FilaInterruptor(
    titulo: String,
    detalle: String? = null,
    activo: Boolean,
    habilitado: Boolean = true,
    onCambio: (Boolean) -> Unit,
) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
            .clickable(enabled = habilitado) { onCambio(!activo) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, color = if (habilitado) c.texto else c.textoSuave, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            detalle?.let { Text(it, color = c.textoSuave, fontSize = 12.sp, lineHeight = 16.sp) }
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = activo, enabled = habilitado, onCheckedChange = { onCambio(it) },
            colors = SwitchDefaults.colors(checkedTrackColor = c.ok),
        )
    }
}

/** Chips de una sola elección. [deshabilitados] = las opciones que no aplican. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipsEleccion(
    opciones: List<Pair<String, String>>,
    elegido: String?,
    deshabilitados: Set<String> = emptySet(),
    onElegir: (String) -> Unit,
) {
    val c = Sania.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        opciones.forEach { (valor, etiqueta) ->
            val activo = valor == elegido
            val habil = valor !in deshabilitados
            Box(
                Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(if (activo) c.navy else c.superficie)
                    .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                    .clickable(enabled = habil) { onElegir(valor) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    etiqueta, fontSize = 12.sp, fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal,
                    color = when { activo -> c.sobreNavy; !habil -> c.textoSuave.copy(alpha = 0.45f); else -> c.texto },
                )
            }
        }
    }
}

/** Botón a ancho completo. */
@Composable
internal fun Boton(
    texto: String,
    habilitado: Boolean = true,
    primario: Boolean = true,
    peligro: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: () -> Unit,
) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.md.dp)
    val fondo = when { !habilitado -> c.borde; peligro -> c.errorBg; primario -> c.navy; else -> c.superficie }
    val tinta = when { !habilitado -> c.textoSuave; peligro -> c.error; primario -> c.sobreNavy; else -> c.navy }
    Box(
        modifier.clip(forma).background(fondo)
            .border(1.dp, if (peligro) c.error.copy(alpha = 0.4f) else if (primario && habilitado) c.navy else c.borde, forma)
            .clickable(enabled = habilitado, onClick = onClick).padding(vertical = 12.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = tinta, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
}

/** Aviso de color (ámbar = falta algo, verde = listo, rojo = problema, info = lavanda). */
@Composable
internal fun Aviso(texto: String, tipo: String = "pend") {
    val c = Sania.colors
    val (fg, bg) = when (tipo) {
        "ok" -> c.ok to c.okBg
        "error" -> c.error to c.errorBg
        "info" -> c.navy to c.chipBg
        else -> c.pend to c.pendBg
    }
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(bg)
            .border(1.dp, fg.copy(alpha = 0.35f), RoundedCornerShape(Sania.shape.sm.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
    ) { Text(texto, color = if (tipo == "info") c.texto else fg, fontSize = 12.5.sp, lineHeight = 17.sp) }
}

/** Candado de plan: la sección existe pero el plan no la incluye (como PlanLock de la web). */
@Composable
internal fun CandadoPlan(titulo: String, descripcion: String) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.md.dp)
    Column(
        Modifier.fillMaxWidth().clip(forma).background(c.chipBg).border(1.dp, c.borde, forma).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🔒", fontSize = 26.sp)
        Spacer(Modifier.height(4.dp))
        Text(titulo, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(descripcion, color = c.textoSuave, fontSize = 12.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        val acciones = pe.saniape.app.ui.recordarAcciones()
        Text(
            "Ver planes ↗", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
                .clickable { acciones.abrirUrl("${pe.saniape.app.data.Supabase.SITE_URL}/suscripcion") }.padding(6.dp),
        )
    }
}

/** Fila de navegación (lista de secciones / acciones de una sección). */
@Composable
internal fun FilaNav(texto: String, detalle: String? = null, externo: Boolean = false, onClick: () -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    Row(
        Modifier.fillMaxWidth().clip(forma).background(c.superficie).border(1.dp, c.borde, forma)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(texto, color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.SemiBold)
            detalle?.let { Text(it, color = c.textoSuave, fontSize = 11.5.sp, maxLines = 2) }
        }
        Spacer(Modifier.width(8.dp))
        Text(if (externo) "↗" else "→", color = c.textoSuave, fontSize = Sania.txt.cuerpo)
    }
}

/** Línea "Guardado; Sani actualizado" + "Reintentar" si quedó pendiente (EstadoSyncSani de la web). */
@Composable
internal fun EstadoSani(botSync: String?, onEstado: (String?) -> Unit) {
    val c = Sania.colors
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var reintentando by remember { mutableStateOf(false) }
    Column {
        Text(mensajeGuardadoSani(botSync), color = if (botSync == "actualizado" || botSync == "inactivo") c.ok else c.pend, fontSize = 12.sp)
        if (botSync != "actualizado" && botSync != "inactivo") {
            Text(
                if (reintentando) "Actualizando Sani…" else "Reintentar actualización de Sani",
                color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = !reintentando) {
                    reintentando = true
                    scope.launch {
                        val r = guardarAjuste(porDefecto = "No se pudo confirmar la actualización de Sani") { AjustesRepo.reintentarSani() }
                        reintentando = false
                        if (r.registrada) onEstado(r.cuerpo?.s("botSync"))
                    }
                }.padding(vertical = 4.dp),
            )
        }
    }
}

/** Lista para elegir (ciudades, distritos, servicios…), con búsqueda si es larga. */
@Composable
internal fun DialogoLista(
    titulo: String,
    opciones: List<Pair<String, String>>,
    onElegir: (String) -> Unit,
    onCerrar: () -> Unit,
    permitirTextoPropio: Boolean = false,
) {
    val c = Sania.colors
    var filtro by remember { mutableStateOf("") }
    val visibles = remember(filtro, opciones) {
        val f = filtro.trim().lowercase()
        if (f.isEmpty()) opciones else opciones.filter { it.second.lowercase().contains(f) || it.first.lowercase().contains(f) }
    }
    AlertaConTeclado(
        onDismissRequest = onCerrar,
        title = { Text(titulo, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column {
                if (opciones.size > 8 || permitirTextoPropio) {
                    Campo(null, filtro, { filtro = it }, placeholder = "Buscar…")
                    Spacer(Modifier.height(8.dp))
                }
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    if (permitirTextoPropio && filtro.isNotBlank() && visibles.none { it.first.equals(filtro.trim(), true) }) {
                        Text("Usar «${filtro.trim()}»", color = c.navy, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth().clickable { onElegir(filtro.trim()) }.padding(vertical = 10.dp))
                    }
                    visibles.take(300).forEach { (valor, etiqueta) ->
                        Text(etiqueta, color = c.texto, fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth().clickable { onElegir(valor) }.padding(vertical = 10.dp))
                    }
                    if (visibles.isEmpty() && !permitirTextoPropio) Text("Sin resultados", color = c.textoSuave, fontSize = 13.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar", color = c.textoSuave) } },
        containerColor = c.superficie,
    )
}

/** Caja que muestra el valor elegido y abre un selector. */
@Composable
internal fun Selector(etiqueta: String?, valor: String, placeholder: String = "Elegir", habilitado: Boolean = true, onClick: () -> Unit) {
    val c = Sania.colors
    Column(Modifier.fillMaxWidth()) {
        etiqueta?.let { EtqForm(it) }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie)
                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                .clickable(enabled = habilitado, onClick = onClick).padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(valor.ifBlank { placeholder }, color = if (valor.isBlank()) c.textoSuave else c.texto, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("▾", color = c.textoSuave)
        }
    }
}

/** Selector de color: paleta + código #rrggbb. [permitirVacio] = "Neutro". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SelectorColor(valor: String, onCambio: (String) -> Unit, permitirVacio: Boolean = false) {
    val c = Sania.colors
    val paleta = listOf(
        "#2c3e7a", "#1a2550", "#1e40af", "#0e7490", "#0f766e", "#15803d", "#4d7c0f", "#a16207",
        "#c2410c", "#b91c1c", "#be185d", "#7c3aed", "#6b21a8", "#334155", "#111827", "#f7f8fc",
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (permitirVacio) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(c.fondo)
                        .border(if (valor.isBlank()) 3.dp else 1.dp, if (valor.isBlank()) c.navy else c.borde, CircleShape)
                        .clickable { onCambio("") },
                    contentAlignment = Alignment.Center,
                ) { Text("∅", color = c.textoSuave, fontSize = 13.sp) }
            }
            paleta.forEach { hex ->
                val sel = valor.equals(hex, ignoreCase = true)
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(colorDeHex(hex) ?: c.navy)
                        .border(if (sel) 3.dp else 1.dp, if (sel) c.texto else c.borde, CircleShape)
                        .clickable { onCambio(hex) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(colorDeHex(valor) ?: c.fondo).border(1.dp, c.borde, RoundedCornerShape(10.dp)))
            Spacer(Modifier.width(10.dp))
            Campo(null, valor, { v -> onCambio(v.trim().let { if (it.isNotEmpty() && !it.startsWith("#")) "#$it" else it }) },
                placeholder = if (permitirVacio) "Neutro" else "#2c3e7a", max = 7,
                mayusculas = KeyboardCapitalization.None, modifier = Modifier.weight(1f))
        }
    }
}

/** "#rrggbb" → Color (null si no es válido). */
internal fun colorDeHex(hex: String?): Color? {
    val h = hex?.trim()?.removePrefix("#") ?: return null
    if (h.length != 6 || h.any { it !in "0123456789abcdefABCDEF" }) return null
    return Color(("ff$h").toLong(16).toInt())
}

internal fun esHex(v: String) = Regex("^#[0-9a-fA-F]{6}$").matches(v)
