package pe.saniape.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import pe.saniape.app.ui.theme.Sania

/**
 * ¿Esta plataforma anima los GIF de los ejercicios? expect/actual: Android sí
 * (coil-gif, registrado en SaniaApplication); iOS todavía no, y muestra la
 * postura estática (`postura_inicial_url`).
 */
expect fun plataformaAnimaGif(): Boolean

/**
 * Qué imagen se pide para "ver cómo se hace": el GIF donde se anima; la postura
 * inicial donde no (si falta una, la otra). Pura, para poder testearla.
 */
fun urlAnimacionEjercicio(gifUrl: String?, posturaInicialUrl: String?, animaGif: Boolean): String? {
    val gif = gifUrl?.takeIf { it.isNotBlank() }
    val postura = posturaInicialUrl?.takeIf { it.isNotBlank() }
    return if (animaGif) gif ?: postura else postura ?: gif
}

/** Miniatura de un ejercicio: la postura inicial (PNG liviano); si falta, el GIF. Fondo blanco, como los dibujos. */
@Composable
fun MiniaturaEjercicio(posturaInicialUrl: String?, gifUrl: String?, modifier: Modifier = Modifier) {
    val c = Sania.colors
    val url = posturaInicialUrl?.takeIf { it.isNotBlank() } ?: gifUrl?.takeIf { it.isNotBlank() }
    Box(
        modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(Color.White)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        else Text("🏠", fontSize = 18.sp)
    }
}

/** La animación del ejercicio (GIF de 480 px; en iOS, la postura). Si no carga, lo dice. */
@Composable
fun AnimacionEjercicio(gifUrl: String?, posturaInicialUrl: String?, nombre: String, modifier: Modifier = Modifier) {
    val url = urlAnimacionEjercicio(gifUrl, posturaInicialUrl, plataformaAnimaGif()) ?: return
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = "Animación de $nombre",
            contentScale = ContentScale.Fit,
            loading = {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = Sania.colors.navy, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                }
            },
            error = {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("No se pudo cargar la animación", color = Color(0xFF6B7280), fontSize = 12.sp)
                }
            },
            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(Sania.shape.sm.dp)).background(Color.White),
        )
    }
}

/** Los pasos del ejercicio ("cómo se hace"), numerados. */
@Composable
fun PasosEjercicio(pasos: List<String>, tamano: TextUnit = 12.sp) {
    val c = Sania.colors
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        pasos.forEachIndexed { i, paso ->
            Row {
                Text("${i + 1}.", color = c.textoSuave, fontSize = tamano, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                Text(paso, color = c.texto, fontSize = tamano, modifier = Modifier.weight(1f))
            }
        }
    }
}
