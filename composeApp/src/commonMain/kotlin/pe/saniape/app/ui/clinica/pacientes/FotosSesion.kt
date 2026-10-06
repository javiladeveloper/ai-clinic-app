package pe.saniape.app.ui.clinica.pacientes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.FotosRepo
import pe.saniape.app.data.staff.SolicitudesRepo
import pe.saniape.app.ui.ArchivoSeleccionado
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.comprimirImagen
import pe.saniape.app.ui.recordarCamaraFoto
import pe.saniape.app.ui.recordarSelectorArchivo
import pe.saniape.app.ui.theme.Sania

/**
 * Fotos elegidas en el cierre de una sesión. Viven en memoria mientras el
 * diálogo está abierto y se suben TODAS al confirmar (como la web: "Fotos de la
 * sesión", varias, vinculadas a esa sesión + tratamiento, momento "Durante").
 */
class FotosSesionPendientes {
    val lista = mutableStateListOf<ArchivoSeleccionado>()
    /** Mostrar al paciente en su app (privadas por defecto, igual que la web). */
    var visiblePaciente by mutableStateOf(false)
    /** Fotos comprimiéndose (se comprimen al AGREGARLAS): no se deja confirmar hasta que terminen. */
    var preparando by mutableStateOf(0)
    fun copia(): List<ArchivoSeleccionado> = lista.toList()
}

/** ¿Es una imagen que se puede subir como foto? (igual criterio que la galería). */
internal fun esImagenSubible(a: ArchivoSeleccionado): Boolean =
    a.mime?.startsWith("image/") == true ||
        a.nombre.substringAfterLast('.', "").lowercase() in listOf("jpg", "jpeg", "png", "webp", "heic")

/**
 * ¿La clínica tiene encendidas las fotos? Además del plan (`can("fotosEvolutivas")`),
 * la clínica puede APAGARLAS en Configuración (`fotos_evolutivas_activa = 'false'`).
 * Sin valor guardado = encendido (misma regla que la ficha web). null = cargando.
 */
@Composable
fun recordarFotosActivas(puedePlan: Boolean): Boolean? {
    val estado = produceState<Boolean?>(initialValue = if (puedePlan) null else false, puedePlan) {
        value = if (!puedePlan) false else FotosRepo.fotosActivasClinica()
    }
    return estado.value
}

/**
 * Bloque "📷 Fotos de la sesión (opcional)" dentro de los diálogos de completar
 * sesión (ficha y agenda): cámara o galería, varias, con miniatura y quitar. No
 * sube nada por sí mismo: quien confirma llama [subirFotosSesion].
 */
@Composable
fun BloqueFotosSesion(estado: FotosSesionPendientes) {
    val c = Sania.colors
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Se comprime al AGREGAR (1600 px / JPEG 70, como la web), fuera del hilo de la
    // pantalla: así en memoria quedan ~300 KB por foto y no 5-8 MB.
    fun agregar(archivo: ArchivoSeleccionado) {
        estado.preparando++
        scope.launch {
            try {
                val listo = kotlinx.coroutines.withContext(Dispatchers.Default) {
                    runCatching { comprimirImagen(archivo) }.getOrDefault(archivo)
                }
                estado.lista.add(listo)
            } finally {
                estado.preparando--
            }
        }
    }
    val abrirCamara = recordarCamaraFoto { archivo -> agregar(archivo) }
    var pedirGaleria by androidx.compose.runtime.remember { mutableStateOf(false) }
    val abrirGaleria = recordarSelectorArchivo { archivo ->
        if (esImagenSubible(archivo)) agregar(archivo)
        else Toaster.error("Elige una imagen (JPG, PNG o WEBP)")
    }
    // Igual que la galería: el selector se abre tras recomponer (no dentro del clic).
    // La bandera se baja ANTES de abrir: si el usuario cancela el selector, el
    // callback no llega y una bandera en true impedía volver a abrirlo.
    LaunchedEffect(pedirGaleria) {
        if (pedirGaleria) { pedirGaleria = false; abrirGaleria() }
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.fondo)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📷 Fotos de la sesión", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            Text(
                when {
                    estado.preparando > 0 -> "Preparando foto…"
                    estado.lista.isEmpty() -> "opcional"
                    else -> "${estado.lista.size}"
                },
                color = c.textoSuave, fontSize = 11.sp,
            )
        }
        if (estado.lista.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(estado.lista) { i, archivo ->
                    Box(Modifier.size(64.dp)) {
                        AsyncImage(
                            model = archivo.bytes, contentDescription = archivo.nombre,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                                .background(c.chipBg).border(1.dp, c.borde, RoundedCornerShape(8.dp)),
                        )
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(2.dp).size(20.dp)
                                .clip(RoundedCornerShape(10.dp)).background(Color(0x99000000))
                                .clickable { if (i < estado.lista.size) estado.lista.removeAt(i) },
                            contentAlignment = Alignment.Center,
                        ) { Text("✕", color = Color.White, fontSize = 10.sp) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonFoto(if (estado.lista.isEmpty()) "📸 Tomar foto" else "📸 Otra foto", Modifier.weight(1f)) { abrirCamara() }
            BotonFoto("🖼 Galería", Modifier.weight(1f)) { pedirGaleria = true }
        }
        if (estado.lista.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { estado.visiblePaciente = !estado.visiblePaciente }) {
                Box(Modifier.size(20.dp).clip(RoundedCornerShape(4.dp))
                    .background(if (estado.visiblePaciente) c.navy else c.superficie)
                    .border(1.dp, if (estado.visiblePaciente) c.navy else c.borde, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center) {
                    if (estado.visiblePaciente) Text("✓", color = c.sobreNavy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Text("Mostrarlas al paciente en su app", color = c.texto, fontSize = 12.sp)
            }
            Text("Se suben al completar la sesión y quedan en la galería del tratamiento.",
                color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun BotonFoto(texto: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable { onClick() }.padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

/**
 * Sube cada vez que termina una tanda de fotos de sesión: la galería del
 * tratamiento la escucha para mostrarlas sin recargar la ficha.
 */
object FotosSesionVersion {
    var valor by mutableStateOf(0)
}

/** Las subidas siguen aunque se cierre el diálogo o la pantalla. */
private val alcanceSubidas = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Qué hacer con las fotos elegidas según cómo terminó el completar:
 *  · no se completó → se avisa que las fotos NO se guardaron (nunca en silencio);
 *  · quedó en la cola sin señal → se guardan en el teléfono (FotosPendientes) y
 *    se suben solas cuando la sesión ya esté en el servidor; lo que no cabe en
 *    el tope (10 fotos / 20 MB en espera) se avisa;
 *  · completada → se suben en segundo plano.
 *
 * [sesionIdOffline] / [citaIdOffline]: a qué sesión ligarlas si quedan en espera
 * (la ficha conoce la sesión; la agenda, la cita, y la sesión se busca después).
 */
fun fotosTrasCompletar(
    ok: Boolean,
    encolada: Boolean,
    pacienteId: String,
    tratamientoId: String,
    fotos: List<ArchivoSeleccionado>,
    visiblePaciente: Boolean,
    sesionIdOffline: String? = null,
    citaIdOffline: String? = null,
    sesionId: suspend () -> String?,
) {
    if (fotos.isEmpty()) return
    val n = if (fotos.size == 1) "la foto" else "las ${fotos.size} fotos"
    when {
        !ok -> Toaster.error("La sesión no se completó: $n de la sesión no se guardaron.")
        encolada -> alcanceSubidas.launch {
            val r = pe.saniape.app.data.offline.FotosPendientes.guardar(
                pacienteId, tratamientoId, sesionIdOffline, citaIdOffline, fotos, visiblePaciente,
            )
            val (bien, texto) = pe.saniape.app.data.offline.avisoFotosGuardadas(r)
            if (bien) Toaster.exito(texto) else Toaster.error(texto)
        }
        else -> subirFotosSesion(pacienteId, tratamientoId, fotos, visiblePaciente, sesionId)
    }
}

/**
 * Sube las fotos de la sesión (ya comprimidas al agregarlas) y las registra
 * como fotos evolutivas "Durante" del tratamiento, ligadas a la sesión si se
 * conoce. Corre en segundo plano: la sesión ya quedó completada y no se la hace
 * esperar. [sesionId] puede resolverse tarde (la agenda la busca tras completar).
 */
fun subirFotosSesion(
    pacienteId: String,
    tratamientoId: String,
    fotos: List<ArchivoSeleccionado>,
    visiblePaciente: Boolean,
    sesionId: suspend () -> String?,
) {
    if (fotos.isEmpty() || pacienteId.isBlank()) return
    alcanceSubidas.launch {
        val sesion = runCatching { sesionId() }.getOrNull()
        var fallidas = 0
        for (archivo in fotos) {
            val ok = runCatching {
                val subido = SolicitudesRepo.subirArchivo(pacienteId, archivo.nombre, archivo.bytes, archivo.mime, "foto")
                    ?: return@runCatching false
                FotosRepo.registrarFoto(
                    pacienteId, tratamientoId, sesion, archivo.nombre, subido.first, subido.second,
                    "Durante", null, visiblePaciente,
                )
            }.getOrDefault(false)
            if (!ok) fallidas++
        }
        // La galería se refresca solo si entró al menos una.
        if (fallidas < fotos.size) FotosSesionVersion.valor++
        if (fallidas == 0) Toaster.exito(if (fotos.size == 1) "Foto de la sesión guardada" else "${fotos.size} fotos de la sesión guardadas")
        else Toaster.error("La sesión se completó, pero $fallidas foto(s) no se pudieron subir")
    }
}
