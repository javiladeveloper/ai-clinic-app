package pe.saniape.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import pe.saniape.app.data.staff.inicialesProfesional
import pe.saniape.app.ui.theme.Sania

/**
 * Avatar redondo del profesional (gemelo de AvatarProfesional.tsx): la foto
 * subida en su ficha (`terapeutas.foto_url`) y, si no hay foto o la imagen
 * falla, las iniciales de siempre. Se recuerda QUÉ url falló: si cambia la foto
 * se vuelve a intentar.
 */
@Composable
fun AvatarProfesional(
    nombre: String?,
    fotoUrl: String?,
    tam: Int = 40,
    fondo: Color = Sania.colors.navy,
    texto: Color = Sania.colors.sobreNavy,
    modifier: Modifier = Modifier,
) {
    var urlFallida by remember { mutableStateOf<String?>(null) }
    val base = modifier.size(tam.dp).clip(CircleShape).background(fondo)
    if (!fotoUrl.isNullOrBlank() && fotoUrl != urlFallida) {
        AsyncImage(
            model = fotoUrl,
            contentDescription = nombre ?: "Profesional",
            contentScale = ContentScale.Crop,
            onError = { urlFallida = fotoUrl },
            modifier = base,
        )
    } else {
        Box(base, contentAlignment = Alignment.Center) {
            Text(
                inicialesProfesional(nombre).ifEmpty { "👤" },
                color = texto, fontWeight = FontWeight.Bold, fontSize = (tam * 0.36f).sp,
            )
        }
    }
}
