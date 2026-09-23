package pe.saniape.app.ui.clinica.odontologia

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.OdontogramaRepo
import pe.saniape.app.data.staff.diagnosticoDesdeHallazgos
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.theme.Sania

/**
 * El paso ANTES de completar un diagnóstico en odontología.
 *
 * "El médico lo atendió y está con la aplicación, pone completar y ¿qué
 * sucede?" (Jonathan, 21/09/2026). Primero el odontograma: el dentista acaba de
 * revisar la boca y lo que tiene fresco son los hallazgos, no la conclusión.
 * Al continuar se abre el modal de siempre con el diagnóstico YA REDACTADO a
 * partir de lo marcado ("Caries en piezas 26, 27 y 36"), para corregir o
 * aceptar.
 *
 * Solo citas dentales: la agenda lo monta únicamente con `vm.esDental(cita)`
 * (por cita: en una clínica con fisio y odontología, la de fisio no pasa por acá).
 * Gemelo de `components/odontologia/RevisionPrevia.tsx` en la web.
 */
@Composable
fun RevisionPrevia(
    pacienteId: String,
    pacienteNombre: String?,
    citaId: String,
    onContinuar: (diagnosticoSugerido: String) -> Unit,
    onCancelar: () -> Unit,
) {
    val c = Sania.colors
    // Se recalcula cada vez que el odontograma avisa un cambio: el dentista ve
    // crecer el texto mientras marca, así entiende de dónde sale.
    var version by remember { mutableIntStateOf(0) }
    var sugerido by remember { mutableStateOf("") }
    LaunchedEffect(version) {
        sugerido = diagnosticoDesdeHallazgos(
            OdontogramaRepo.hallazgos(pacienteId), OdontogramaRepo.catalogo(),
        )
    }

    DialogoForm(
        titulo = "🦷 Revisión",
        subtitulo = pacienteNombre?.let { "Marca lo que encontraste en ${it.split(' ').first()}" }
            ?: "Marca lo que encontraste",
        textoAccion = "Continuar →",
        // "Salir sin completar" y no "Cancelar": lo marcado ya se GUARDÓ (cada
        // toque escribe en la base), así que "Cancelar" haría creer que se
        // deshace, y no se deshace. Solo cierra sin pasar al diagnóstico.
        onCancelar = onCancelar,
        onAccion = { onContinuar(sugerido) },
        textoCancelar = "Salir sin completar",
    ) {
        Text(
            "Se guarda al momento. Al continuar se redacta el diagnóstico con estos hallazgos — podrás corregirlo.",
            color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp),
        )

        OdontogramaVista(
            pacienteId = pacienteId,
            citaId = citaId,
            mostrarPresupuesto = false,
            onCambio = { version++ },
        )

        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                .padding(12.dp),
        ) {
            Text("DIAGNÓSTICO QUE SE PROPONDRÁ", color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            if (sugerido.isBlank()) {
                Text(
                    "Sin hallazgos marcados — lo escribes tú en el paso siguiente.",
                    color = c.textoSuave, fontSize = 13.sp, fontStyle = FontStyle.Italic,
                )
            } else {
                Text(sugerido, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
