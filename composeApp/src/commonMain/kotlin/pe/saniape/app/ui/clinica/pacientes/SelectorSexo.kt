package pe.saniape.app.ui.clinica.pacientes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.ui.theme.Sania

/**
 * Sexo del paciente (opcional; las curvas de la OMS de 👶 Crecimiento son por
 * sexo): control segmentado compacto "Femenino | Masculino", como el de
 * PacienteForm de la web. Tocar el elegido lo quita (vuelve a "sin dato").
 * [valor]: "F" / "M" / null.
 */
@Composable
fun SelectorSexo(valor: String?, onCambio: (String?) -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    Row(Modifier.clip(forma).border(1.dp, c.borde, forma)) {
        listOf("F" to "Femenino", "M" to "Masculino").forEachIndexed { i, (clave, etq) ->
            val activo = valor == clave
            if (i > 0) Box(Modifier.width(1.dp).heightIn(min = 40.dp).background(c.borde))
            Box(
                Modifier.heightIn(min = 40.dp).background(if (activo) c.navy else c.superficie)
                    .semantics { selected = activo }
                    .clickable(role = Role.RadioButton) { onCambio(if (activo) null else clave) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(etq, color = if (activo) c.sobreNavy else c.texto, fontSize = 13.sp,
                    fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
