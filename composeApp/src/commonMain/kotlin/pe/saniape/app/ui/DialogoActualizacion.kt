package pe.saniape.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import pe.saniape.app.ui.theme.Sania

/**
 * Aviso SUGERIDO de nueva versión: "Hay una actualización disponible" con botón
 * "Actualizar" (abre la tienda) y "Más tarde" (lo cierra). No bloquea la app.
 */
@Composable
fun DialogoActualizacion(
    onActualizar: () -> Unit,
    onMasTarde: () -> Unit,
) {
    val c = Sania.colors
    Dialog(onDismissRequest = onMasTarde) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Sania.shape.md.dp))
                .background(c.superficie)
                .padding(Sania.dim.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🚀", fontSize = 40.sp)
            Spacer(Modifier.height(Sania.dim.md))
            Text(
                "Nueva versión disponible",
                color = c.texto, fontSize = Sania.txt.seccion, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Sania.dim.xs))
            Text(
                "Actualiza Sania para tener las últimas mejoras y correcciones.",
                color = c.textoSuave, fontSize = Sania.txt.pequeno, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Sania.dim.xl))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Sania.dim.sm)) {
                // Más tarde (ghost)
                Text(
                    "Más tarde",
                    color = c.textoSuave, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .clickable { onMasTarde() }
                        .padding(vertical = Sania.dim.md),
                )
                // Actualizar (navy sólido)
                Text(
                    "Actualizar",
                    color = c.sobreNavy, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .background(c.navy)
                        .clickable { onActualizar() }
                        .padding(vertical = Sania.dim.md),
                )
            }
        }
    }
}
