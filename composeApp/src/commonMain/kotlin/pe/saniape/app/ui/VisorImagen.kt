package pe.saniape.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage

/**
 * Visor de imágenes DENTRO de la app (jpg/png/webp): pantalla completa sobre
 * fondo oscuro, con pellizco para acercar y doble toque para volver. Antes una
 * foto del portal se abría en el navegador (o no se abría nada). Los PDF siguen
 * abriéndose afuera.
 *
 * [url] null = todavía firmándose (spinner). Si la imagen no carga, lo dice.
 */
@Composable
fun VisorImagen(url: String?, titulo: String?, onCerrar: () -> Unit) {
    var escala by remember(url) { mutableStateOf(1f) }
    var desplazamiento by remember(url) { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xF2000000)), contentAlignment = Alignment.Center) {
            if (url == null) {
                CircularProgressIndicator(color = Color.White)
            } else {
                SubcomposeAsyncImage(
                    model = url,
                    contentDescription = titulo,
                    contentScale = ContentScale.Fit,
                    loading = { Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color.White) } },
                    error = {
                        Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                            Text("No se pudo cargar la imagen. Revisa tu conexión.",
                                color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(url) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                escala = (escala * zoom).coerceIn(1f, 5f)
                                desplazamiento = if (escala == 1f) Offset.Zero else desplazamiento + pan
                            }
                        }
                        .pointerInput(url) {
                            detectTapGestures(onDoubleTap = {
                                if (escala > 1f) { escala = 1f; desplazamiento = Offset.Zero } else escala = 2.5f
                            })
                        }
                        .graphicsLayer(
                            scaleX = escala, scaleY = escala,
                            translationX = desplazamiento.x, translationY = desplazamiento.y,
                        ),
                )
            }
            // Cabecera: título y cerrar (siempre a mano, aunque la foto esté ampliada).
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color(0x99000000))
                .padding(horizontal = 16.dp, vertical = 12.dp)) {
                Box(Modifier.fillMaxWidth()) {
                    titulo?.let {
                        Text(it, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            maxLines = 2, modifier = Modifier.align(Alignment.CenterStart).padding(end = 48.dp))
                    }
                    Box(Modifier.align(Alignment.CenterEnd).size(36.dp).clip(RoundedCornerShape(18.dp))
                        .background(Color(0x33FFFFFF)).clickable { onCerrar() },
                        contentAlignment = Alignment.Center) {
                        Text("✕", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text("Pellizca para acercar · doble toque para volver", color = Color(0x99FFFFFF), fontSize = 11.sp)
            }
        }
    }
}
