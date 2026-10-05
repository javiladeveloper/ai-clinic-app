package pe.saniape.app.ui.clinica.psico

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import pe.saniape.app.data.ClaseArchivo
import pe.saniape.app.data.claseArchivo
import pe.saniape.app.data.staff.FotoPsico
import pe.saniape.app.data.staff.OpcionPsico
import pe.saniape.app.data.staff.EvaluacionPsicoRepo
import pe.saniape.app.ui.AccionesNativas
import pe.saniape.app.ui.ArchivoSeleccionado
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.VisorImagen
import pe.saniape.app.ui.clinica.atencion.CampoTextoClinico
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.recordarCamaraFoto
import pe.saniape.app.ui.recordarSelectorArchivo
import pe.saniape.app.ui.theme.Sania

// Piezas chicas de la evaluación psicológica, con el look de la consulta guiada.

/** Texto largo con dictado (el mismo campo de la consulta guiada, sin frases aprendidas). */
@Composable
internal fun TextoLargoPsico(label: String, valor: String, onChange: (String) -> Unit, soloLectura: Boolean, placeholder: String? = null, minLineas: Int = 3) {
    CampoTextoClinico(
        label = label, valor = valor, onChange = onChange, frases = emptyList(),
        soloLectura = soloLectura, minLineas = minLineas, placeholder = placeholder,
    )
}

/** Campo de una línea. [numerico] abre el teclado de números. */
@Composable
internal fun CampoCortoPsico(
    label: String, valor: String, onChange: (String) -> Unit, soloLectura: Boolean,
    placeholder: String? = null, numerico: Boolean = false, modifier: Modifier = Modifier,
) {
    val c = Sania.colors
    Column(modifier.fillMaxWidth()) {
        if (label.isNotEmpty()) EtqForm(label)
        OutlinedTextField(
            value = valor, onValueChange = onChange, enabled = !soloLectura, singleLine = true,
            placeholder = placeholder?.let { p -> { Text(p, color = c.textoSuave, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            keyboardOptions = KeyboardOptions(
                capitalization = if (numerico) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
                keyboardType = if (numerico) KeyboardType.Decimal else KeyboardType.Text,
            ),
            colors = coloresCampoForm(legibleDeshabilitado = true),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = c.texto),
            shape = RoundedCornerShape(Sania.shape.sm.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Chip que se marca/desmarca (observación, enfoques…). */
@Composable
internal fun ChipPsico(texto: String, activo: Boolean, habilitado: Boolean = true, onClick: () -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.pill.dp)
    Box(
        Modifier.heightIn(min = 34.dp).widthIn(max = 320.dp).clip(forma)
            .background(if (activo) c.navy else c.superficie)
            .border(1.dp, if (activo) c.navy else c.borde, forma)
            .clickable(enabled = habilitado, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, color = if (activo) c.sobreNavy else c.texto, fontSize = 12.sp,
            fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Una opción de varias (segmentado en chips). [permiteVacio] = tocar la elegida la quita. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SelectorChipsPsico(
    label: String, opciones: List<OpcionPsico>, valor: String?, soloLectura: Boolean,
    permiteVacio: Boolean = false, onElegir: (String?) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        if (label.isNotEmpty()) EtqForm(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            opciones.forEach { o ->
                val activo = o.valor == valor
                ChipPsico(o.nombre, activo, habilitado = !soloLectura) {
                    onElegir(if (activo && permiteVacio) null else o.valor)
                }
            }
        }
    }
}

/** Casilla con texto (✓ en navy). */
@Composable
internal fun CasillaPsico(texto: String, marcada: Boolean, soloLectura: Boolean, onCambio: (Boolean) -> Unit) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable(enabled = !soloLectura) { onCambio(!marcada) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).clip(RoundedCornerShape(5.dp))
                .background(if (marcada) c.navy else c.superficie)
                .border(1.dp, if (marcada) c.navy else c.borde, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) { if (marcada) Text("✓", color = c.sobreNavy, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(10.dp))
        Text(texto, color = c.texto, fontSize = 14.sp)
    }
}

/** Botón chico de acción (borde de color, fondo tenue). */
@Composable
internal fun BotonPsico(
    texto: String, color: Color = Sania.colors.navy, habilitado: Boolean = true,
    relleno: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    Box(
        modifier.heightIn(min = 40.dp).clip(forma)
            .background(if (relleno) (if (habilitado) color else c.borde) else color.copy(alpha = 0.10f))
            .border(1.dp, if (habilitado) color else c.borde, forma)
            .clickable(enabled = habilitado, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, color = if (relleno) c.sobreNavy else if (habilitado) color else c.textoSuave,
            fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** Aviso en una franja de color. */
@Composable
internal fun AvisoPsico(texto: String, fg: Color, bg: Color, negrita: Boolean = false) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(bg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(texto, color = fg, fontSize = 13.sp, fontWeight = if (negrita) FontWeight.Bold else FontWeight.Normal) }
}

/** Título de un bloque dentro de la sección. */
@Composable
internal fun SubtituloPsico(texto: String) {
    Text(texto, color = Sania.colors.navy, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
}

// ── Fotos protegidas ─────────────────────────────────────────────────────────

/** URLs para ver (1 h en el servidor; acá se reusan 50 min), por id del documento. */
private object UrlsFotosPsico {
    private var urls: Map<String, Pair<String, Long>> = emptyMap()
    private const val VIGENCIA_MS = 50L * 60 * 1000
    /** La URL, o el error legible (sin acceso, sin red…). */
    suspend fun de(f: FotoPsico): EvaluacionPsicoRepo.VerArchivo {
        val ahora = Clock.System.now().toEpochMilliseconds()
        urls[f.id]?.takeIf { ahora - it.second < VIGENCIA_MS }?.let { return EvaluacionPsicoRepo.VerArchivo.Ok(it.first) }
        val r = EvaluacionPsicoRepo.urlDeDocumento(f.id)
        if (r is EvaluacionPsicoRepo.VerArchivo.Ok) urls = urls + (f.id to (r.url to ahora))
        return r
    }
}

/**
 * Fotos de dibujos, hojas, genograma o adjuntos: miniaturas (🔒 material
 * protegido) con ver y borrar, y los botones de cámara y galería. La cámara es
 * el caso principal en el celular. La galería acepta imagen o PDF.
 * [onArchivo] recibe lo elegido (la compresión la hace el ViewModel al subir).
 */
@Composable
internal fun BloqueFotosPsico(
    fotos: List<FotoPsico>,
    soloLectura: Boolean,
    subiendo: Boolean,
    acciones: AccionesNativas,
    textoCamara: String = "📷 Fotografiar",
    unaSola: Boolean = false,
    onArchivo: (ArchivoSeleccionado) -> Unit,
    onBorrar: (FotoPsico) -> Unit,
) {
    val c = Sania.colors
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var verFoto by remember { mutableStateOf<FotoPsico?>(null) }
    var urlVer by remember { mutableStateOf<String?>(null) }
    var confirmarBorrar by remember { mutableStateOf<FotoPsico?>(null) }
    val abrirCamara = recordarCamaraFoto { onArchivo(it) }
    var pedirGaleria by remember { mutableStateOf(false) }
    val abrirGaleria = recordarSelectorArchivo { a ->
        val clase = claseArchivo(a.mime, a.nombre)
        if (a.mime?.startsWith("image/") == true || clase != ClaseArchivo.OTRO) onArchivo(a)
        else Toaster.error("Elige una imagen (JPG, PNG o WEBP) o un PDF")
    }
    // Como la galería de fotos: el selector se abre tras recomponer, y la bandera
    // se baja ANTES (un selector cancelado no llama al callback).
    LaunchedEffect(pedirGaleria) { if (pedirGaleria) { pedirGaleria = false; abrirGaleria() } }

    fun ver(f: FotoPsico) {
        scope.launch {
            val imagen = claseArchivo(f.tipo, f.path.ifBlank { f.nombre }) == ClaseArchivo.IMAGEN
            if (imagen) { verFoto = f; urlVer = null }
            when (val r = UrlsFotosPsico.de(f)) {
                is EvaluacionPsicoRepo.VerArchivo.Ok -> if (imagen) urlVer = r.url else acciones.abrirUrl(r.url)
                is EvaluacionPsicoRepo.VerArchivo.Error -> { verFoto = null; Toaster.error(r.mensaje) }
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        if (fotos.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) { fotos.forEach { f -> MiniaturaPsico(f, soloLectura, onVer = { ver(f) }, onBorrar = { confirmarBorrar = f }) } }
            Spacer(Modifier.height(8.dp))
        }
        if (!soloLectura) {
            if (subiendo) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = c.navy, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Subiendo…", color = c.textoSuave, fontSize = 12.sp)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val etiqueta = if (unaSola && fotos.isNotEmpty()) "📷 Reemplazar" else textoCamara
                    BotonPsico(etiqueta, modifier = Modifier.weight(1f)) { abrirCamara() }
                    BotonPsico("🖼 Galería / PDF", modifier = Modifier.weight(1f)) { pedirGaleria = true }
                }
            }
        }
        Text("🔒 Material protegido: nunca lo ve el paciente.", color = c.textoSuave, fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp))
    }

    verFoto?.let { f -> VisorImagen(url = urlVer, titulo = f.nombre, onCerrar = { verFoto = null; urlVer = null }) }
    confirmarBorrar?.let { f ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmarBorrar = null },
            title = { Text("¿Borrar \"${f.nombre}\"?", fontWeight = FontWeight.Bold) },
            text = { Text("Se borra el archivo de la evaluación.", color = c.texto) },
            confirmButton = { TextButton(onClick = { confirmarBorrar = null; onBorrar(f) }) { Text("Borrar", color = c.error, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirmarBorrar = null }) { Text("Cancelar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }
}

@Composable
private fun MiniaturaPsico(f: FotoPsico, soloLectura: Boolean, onVer: () -> Unit, onBorrar: () -> Unit) {
    val c = Sania.colors
    val clase = claseArchivo(f.tipo, f.path.ifBlank { f.nombre })
    val url by produceState<String?>(null, f.id) {
        if (clase == ClaseArchivo.IMAGEN) value = (UrlsFotosPsico.de(f) as? EvaluacionPsicoRepo.VerArchivo.Ok)?.url
    }
    Box(Modifier.size(76.dp)) {
        Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(c.chipBg)
                .border(1.dp, c.borde, RoundedCornerShape(8.dp)).clickable(onClick = onVer),
            contentAlignment = Alignment.Center,
        ) {
            val u = url
            if (clase == ClaseArchivo.IMAGEN && u != null) {
                AsyncImage(model = u, contentDescription = f.nombre, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (clase == ClaseArchivo.PDF) "📄" else "🖼", fontSize = 22.sp)
                    Text(f.nombre, color = c.textoSuave, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
        Text("🔒", fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomStart).padding(3.dp))
        if (!soloLectura) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(2.dp).size(22.dp)
                    .clip(RoundedCornerShape(11.dp)).background(Color(0x99000000)).clickable(onClick = onBorrar),
                contentAlignment = Alignment.Center,
            ) { Text("✕", color = Color.White, fontSize = 11.sp) }
        }
    }
}
