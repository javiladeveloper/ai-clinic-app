package pe.saniape.app.ui.clinica.pacientes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.FotoEvolutiva
import pe.saniape.app.data.staff.FotosRepo
import pe.saniape.app.data.staff.SesionFicha
import pe.saniape.app.data.staff.SolicitudesRepo
import pe.saniape.app.ui.ArchivoSeleccionado
import pe.saniape.app.ui.comprimirImagen
import pe.saniape.app.ui.recordarCamaraFoto
import pe.saniape.app.ui.recordarSelectorArchivo
import pe.saniape.app.ui.theme.Sania

private val MOMENTOS = listOf("Antes", "Durante", "Despues")
private fun momentoLabel(m: String?): String = when (m) {
    "Despues" -> "Después"; else -> m ?: ""
}

/**
 * Galería de fotos evolutivas (antes/durante/después) de UN tratamiento. Espeja
 * FotosEvolutivas.tsx de la web: thumbnails con badge de momento + visibilidad, subir
 * (selector nativo → modal momento/sesión/nota/visible), lightbox a pantalla completa,
 * alternar visible-al-paciente y borrar. Solo se muestra con permiso fotosEvolutivas (Premium).
 *
 * Reusa la subida/firmado de archivos (SolicitudesRepo) y el CRUD de FotosRepo.
 */
@Composable
fun GaleriaFotos(
    pacienteId: String,
    tratamientoId: String,
    sesiones: List<SesionFicha>,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()

    var fotos by remember { mutableStateOf<List<FotoEvolutiva>?>(null) }
    var urls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }   // path -> url firmada
    var subiendo by remember { mutableStateOf(false) }
    var pendiente by remember { mutableStateOf<ArchivoSeleccionado?>(null) }    // archivo elegido, esperando datos
    var lightbox by remember { mutableStateOf<FotoEvolutiva?>(null) }
    var pedirArchivo by remember { mutableStateOf(false) }

    suspend fun urlDe(path: String): String? {
        urls[path]?.let { return it }
        val u = SolicitudesRepo.urlFirmada(path) ?: return null
        urls = urls + (path to u)
        return u
    }

    fun recargar() {
        scope.launch { fotos = runCatching { FotosRepo.fotosDe(pacienteId) }.getOrDefault(emptyList()) }
    }
    LaunchedEffect(tratamientoId) { recargar() }

    val abrirSelector = recordarSelectorArchivo { archivo ->
        pedirArchivo = false
        // Solo imágenes (igual que la web).
        if (archivo.mime?.startsWith("image/") == true || archivo.nombre.substringAfterLast('.', "")
                .lowercase() in listOf("jpg", "jpeg", "png", "webp", "heic")) {
            pendiente = archivo
        }
    }
    LaunchedEffect(pedirArchivo) { if (pedirArchivo) abrirSelector() }
    // Tomar la foto con la CÁMARA (nativa) — el momento natural en consultorio.
    val abrirCamara = recordarCamaraFoto { archivo -> pendiente = archivo }

    val delTratamiento = fotos?.filter { it.tratamientoId == tratamientoId } ?: emptyList()

    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("📷 FOTOS / EVOLUCIÓN", color = c.textoSuave, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            // Tomar con la cámara (lo natural en consultorio) o elegir de la galería.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("📸 Tomar foto", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = !subiendo) { abrirCamara() })
                Text("🖼 Galería", color = c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = !subiendo) { pedirArchivo = true })
            }
        }
        Spacer(Modifier.height(8.dp))

        when {
            fotos == null -> Box(Modifier.fillMaxWidth().padding(8.dp), Alignment.Center) {
                CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp)
            }
            delTratamiento.isEmpty() -> Text(
                "Sin fotos aún. Sube el “antes” y el “después” para ver la evolución.",
                color = c.textoSuave, fontSize = 12.sp)
            else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(delTratamiento, key = { it.id }) { f ->
                    Thumb(f, urlProvider = { urlDe(it) },
                        onAbrir = { scope.launch { urlDe(f.archivoUrl); lightbox = f } },
                        onVisible = {
                            scope.launch {
                                val ok = FotosRepo.cambiarVisible(f.id, !f.visiblePaciente)
                                if (ok) pe.saniape.app.ui.Toaster.exito(if (!f.visiblePaciente) "Visible para el paciente" else "Oculta al paciente")
                                else pe.saniape.app.ui.Toaster.error("No se pudo cambiar")
                                recargar()
                            }
                        },
                        onBorrar = { scope.launch {
                            val ok = FotosRepo.borrarFoto(f.id)
                            if (ok) pe.saniape.app.ui.Toaster.exito("Foto eliminada") else pe.saniape.app.ui.Toaster.error("No se pudo eliminar")
                            recargar()
                        } })
                }
            }
        }
    }

    // Modal de subida (momento + sesión opcional + nota + visibilidad).
    pendiente?.let { archivo ->
        ModalSubirFoto(
            archivo = archivo, sesiones = sesiones, subiendo = subiendo,
            onCancelar = { if (!subiendo) pendiente = null },
            onGuardar = { momento, sesionId, nota, visible ->
                scope.launch {
                    subiendo = true
                    // Comprimir ANTES de subir (1600px / JPEG 70, como la web): una foto de
                    // cámara de ~5-8 MB baja a ~200-500 KB → no satura el storage.
                    val listo = comprimirImagen(archivo)
                    val subido = SolicitudesRepo.subirArchivo(
                        pacienteId, listo.nombre, listo.bytes, listo.mime, "foto")
                    if (subido != null) {
                        val (path, tipo) = subido
                        FotosRepo.registrarFoto(
                            pacienteId, tratamientoId, sesionId, listo.nombre, path, tipo,
                            momento, nota, visible)
                        pe.saniape.app.ui.Toaster.exito("Foto subida")
                    } else {
                        pe.saniape.app.ui.Toaster.error("No se pudo subir la foto")
                    }
                    subiendo = false; pendiente = null; recargar()
                }
            },
        )
    }

    // Lightbox tipo CARRUSEL: flechas ‹ › (circular) + contador N/M, sin cerrar entre fotos.
    lightbox?.let { f ->
        val idx = delTratamiento.indexOfFirst { it.id == f.id }
        val varias = delTratamiento.size > 1
        fun irA(destino: Int) {
            if (delTratamiento.isEmpty()) return
            val d = delTratamiento[((destino % delTratamiento.size) + delTratamiento.size) % delTratamiento.size]
            scope.launch { urlDe(d.archivoUrl); lightbox = d }
        }
        Dialog(onDismissRequest = { lightbox = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color(0xD9000000)).clickable { lightbox = null },
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)) {
                    val url = urls[f.archivoUrl]
                    if (url != null) {
                        AsyncImage(model = url, contentDescription = f.nombre,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)))
                    } else {
                        CircularProgressIndicator(color = Color.White)
                    }
                    Spacer(Modifier.height(12.dp))
                    val detalle = listOfNotNull(
                        momentoLabel(f.momento).ifBlank { null },
                        f.notas?.takeIf { it.isNotBlank() },
                        f.createdAt?.take(10),
                        if (varias) "${idx + 1}/${delTratamiento.size}" else null,
                    ).joinToString(" · ")
                    Text(detalle, color = Color(0xE6FFFFFF), fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Cerrar ✕", color = Color(0xB3FFFFFF), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { lightbox = null })
                }
                // Flechas del carrusel (solo si hay más de una foto del tratamiento).
                if (varias) {
                    Box(Modifier.align(Alignment.CenterStart).padding(start = 6.dp).size(40.dp)
                        .clip(RoundedCornerShape(20.dp)).background(Color(0x33FFFFFF))
                        .clickable { irA(idx - 1) }, contentAlignment = Alignment.Center) {
                        Text("‹", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).size(40.dp)
                        .clip(RoundedCornerShape(20.dp)).background(Color(0x33FFFFFF))
                        .clickable { irA(idx + 1) }, contentAlignment = Alignment.Center) {
                        Text("›", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun Thumb(
    f: FotoEvolutiva,
    urlProvider: suspend (String) -> String?,
    onAbrir: () -> Unit,
    onVisible: () -> Unit,
    onBorrar: () -> Unit,
) {
    val c = Sania.colors
    var url by remember(f.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(f.id) { url = urlProvider(f.archivoUrl) }

    val momColor = when (f.momento) {
        "Antes" -> c.textoSuave; "Durante" -> c.info; "Despues" -> c.ok; else -> c.textoSuave
    }
    Box(Modifier.size(92.dp)) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(c.chipBg).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable { onAbrir() }, contentAlignment = Alignment.Center) {
            if (url != null) {
                AsyncImage(model = url, contentDescription = f.nombre,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                        .clip(RoundedCornerShape(Sania.shape.sm.dp)))
            } else {
                Text("🖼", fontSize = 22.sp)
            }
        }
        // Badge momento (arriba-izq)
        f.momento?.let {
            Box(Modifier.padding(3.dp).clip(RoundedCornerShape(6.dp)).background(momColor)
                .padding(horizontal = 5.dp, vertical = 1.dp).align(Alignment.TopStart)) {
                Text(momentoLabel(it), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
        // Borrar (arriba-der)
        Box(Modifier.padding(3.dp).size(18.dp).clip(RoundedCornerShape(9.dp))
            .background(Color(0x8C000000)).clickable { onBorrar() }.align(Alignment.TopEnd),
            contentAlignment = Alignment.Center) {
            Text("✕", color = Color.White, fontSize = 10.sp)
        }
        // Visible al paciente (abajo-izq)
        Box(Modifier.padding(3.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x8C000000))
            .clickable { onVisible() }.padding(horizontal = 5.dp, vertical = 1.dp).align(Alignment.BottomStart)) {
            Text(if (f.visiblePaciente) "👁 Visible" else "🔒 Privada",
                color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ModalSubirFoto(
    archivo: ArchivoSeleccionado,
    sesiones: List<SesionFicha>,
    subiendo: Boolean,
    onCancelar: () -> Unit,
    onGuardar: (momento: String, sesionId: String?, nota: String?, visible: Boolean) -> Unit,
) {
    val c = Sania.colors
    var momento by remember { mutableStateOf("Antes") }
    var sesionId by remember { mutableStateOf<String?>(null) }
    var sesionAbierta by remember { mutableStateOf(false) }
    var nota by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val sesSel = sesiones.find { it.id == sesionId }

    DialogoForm(
        titulo = "Agregar foto",
        subtitulo = "📎 ${archivo.nombre}",
        textoAccion = if (subiendo) "Subiendo…" else "Guardar foto",
        accionHabilitada = !subiendo,
        onCancelar = onCancelar,
        onAccion = { onGuardar(momento, sesionId, nota.trim().ifBlank { null }, visible) },
    ) {
        TarjetaForm(titulo = "Momento", icono = "📷") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MOMENTOS.forEach { m ->
                    val sel = momento == m
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(Sania.shape.sm.dp))
                            .background(if (sel) c.navy else c.superficie)
                            .border(1.dp, if (sel) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                            .clickable { momento = m }.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(momentoLabel(m), color = if (sel) c.sobreNavy else c.textoSuave,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (sesiones.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                EtqForm("Vincular a sesión (opcional)")
                Box {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                            .clickable { sesionAbierta = !sesionAbierta }.padding(horizontal = 12.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(sesSel?.let { "Sesión ${it.numero} · ${it.fecha}" } ?: "Sin sesión",
                            color = if (sesSel != null) c.texto else c.textoSuave, fontSize = 13.sp)
                        Text("▾", color = c.textoSuave)
                    }
                    if (sesionAbierta) {
                        Column(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                            .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))) {
                            Text("Sin sesión", color = c.texto, fontSize = Sania.txt.cuerpo,
                                modifier = Modifier.fillMaxWidth().clickable { sesionId = null; sesionAbierta = false }
                                    .padding(horizontal = 12.dp, vertical = 10.dp))
                            sesiones.forEach { s ->
                                Text("Sesión ${s.numero} · ${s.fecha}", color = c.texto, fontSize = Sania.txt.cuerpo,
                                    modifier = Modifier.fillMaxWidth().clickable { sesionId = s.id; sesionAbierta = false }
                                        .padding(horizontal = 12.dp, vertical = 10.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            EtqForm("Nota (opcional)")
            OutlinedTextField(value = nota, onValueChange = { nota = it },
                placeholder = { Text("Ej. Inflamación de rodilla derecha", color = c.textoSuave) },
                modifier = Modifier.fillMaxWidth(), singleLine = true)

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { visible = !visible }) {
                Box(Modifier.size(20.dp).clip(RoundedCornerShape(4.dp))
                    .background(if (visible) c.navy else c.superficie)
                    .border(1.dp, if (visible) c.navy else c.borde, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center) {
                    if (visible) Text("✓", color = c.sobreNavy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Text("Mostrar esta foto al paciente en su portal", color = c.texto, fontSize = 12.sp)
            }
        }
    }
}
