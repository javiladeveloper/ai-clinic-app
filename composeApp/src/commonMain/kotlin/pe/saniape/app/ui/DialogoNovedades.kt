package pe.saniape.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import pe.saniape.app.data.Novedades
import pe.saniape.app.ui.theme.Sania

/**
 * "✨ Novedades de la versión X.Y.Z": lo que trae la actualización, en el
 * lenguaje de la clínica. Sale sola UNA vez tras actualizar (ver
 * [Novedades.pendientesAlAbrir]) y se puede volver a ver desde Más.
 * Mismo molde visual que [DialogoActualizacion].
 */
@Composable
fun DialogoNovedades(
    notas: Novedades.NotasVersion,
    onCerrar: () -> Unit,
) {
    val c = Sania.colors
    Dialog(onDismissRequest = onCerrar) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Sania.shape.md.dp))
                .background(c.superficie)
                .padding(Sania.dim.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("✨", fontSize = 40.sp)
            Spacer(Modifier.height(Sania.dim.md))
            Text(
                "Novedades de la versión ${notas.version}",
                color = c.texto, fontSize = Sania.txt.seccion, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Sania.dim.md))
            // La lista puede ser larga: se desplaza dentro del diálogo y el botón
            // queda siempre a la vista.
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                notas.lineas.forEachIndexed { i, l ->
                    if (l.esTitulo) {
                        if (i > 0) Spacer(Modifier.height(Sania.dim.md))
                        Text(
                            l.texto.removeSuffix(":"),
                            color = c.navy, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(Sania.dim.xs))
                    } else {
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text("•", color = c.navy, fontSize = Sania.txt.pequeno, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(Sania.dim.sm))
                            Text(l.texto, color = c.textoSuave, fontSize = Sania.txt.pequeno)
                        }
                    }
                }
            }
            Spacer(Modifier.height(Sania.dim.xl))
            Text(
                "Entendido",
                color = c.sobreNavy, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(c.navy)
                    .clickable { onCerrar() }
                    .padding(vertical = Sania.dim.md),
            )
        }
    }
}
