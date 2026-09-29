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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.MapaDental
import pe.saniape.app.data.staff.diagnosticoDesdeHallazgos
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.theme.Sania

/**
 * La revisión del odontograma que COMPLETA la evaluación dental.
 *
 * "El médico lo atendió y está con la aplicación, pone completar y ¿qué
 * sucede?" (Jonathan, 21/09/2026). Primero el odontograma: el dentista acaba de
 * revisar la boca y lo que tiene fresco son los hallazgos, no la conclusión.
 * El diagnóstico se redacta solo a partir de lo marcado ("Caries en piezas 26,
 * 27 y 36").
 *
 * UN SOLO PASO (29/09/2026, lo mismo que la web desde el 28/09): antes había
 * "Continuar →" y después OTRA ventana para confirmar el diagnóstico. Ahora
 * "✓ Completar evaluación" hace todo aquí: crea el tratamiento con el
 * presupuesto a la vista (si aún no existe), resuelve quién atendió y completa
 * la cita. La ventana de completar solo aparece si falta algo que no se puede
 * saber solo (varios odontólogos, o ningún hallazgo del que sacar diagnóstico).
 * Eso lo decide la agenda en [onCompletar].
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
    /** Qué especialidades son dentales: el presupuesto ofrece solo esos servicios. */
    mapaDental: MapaDental,
    /** Nombre de quien atendió, ya resuelto; null = se preguntará al completar. */
    atendio: String?,
    /** Completando (crea tratamiento + completa): el botón no acepta otro toque. */
    guardando: Boolean,
    /** El diagnóstico redactado y el presupuesto a la vista (para crear lo marcado). */
    onCompletar: (diagnostico: String, registro: RegistroPresupuesto) -> Unit,
    onCancelar: () -> Unit,
    titulo: String = "🦷 Revisión",
    // "Salir sin completar" y no "Cancelar": lo marcado ya se GUARDÓ (cada
    // toque escribe en la base), así que "Cancelar" haría creer que se deshace,
    // y no se deshace. Desde la tarjeta (🦷 Odontograma) es "Cerrar".
    textoCancelar: String = "Salir sin completar",
) {
    val c = Sania.colors
    // Se recalcula cada vez que el odontograma avisa un cambio: el dentista ve
    // crecer el texto mientras marca, así entiende de dónde sale. Con los datos
    // que el odontograma YA trajo: antes cada marca pedía otra vez hallazgos y
    // catálogo a la red solo para redactar esta línea (el doble de consultas).
    var sugerido by remember { mutableStateOf("") }
    // El presupuesto de adentro registra aquí "crear lo marcado". Estable: el
    // presupuesto escribe en él.
    val registro = remember { RegistroPresupuesto() }

    DialogoForm(
        titulo = titulo,
        subtitulo = pacienteNombre?.let { "Marca lo que encontraste en ${it.split(' ').first()}" }
            ?: "Marca lo que encontraste",
        textoAccion = if (guardando) "Guardando…" else "✓ Completar evaluación",
        accionHabilitada = !guardando,
        onCancelar = onCancelar,
        onAccion = { if (!guardando) onCompletar(sugerido, registro) },
        textoCancelar = textoCancelar,
    ) {
        Text(
            "Se guarda al momento. El diagnóstico se redacta solo con lo que marques, y al completar " +
                "se crea el tratamiento con lo marcado en el presupuesto (si ya lo creaste, no se duplica).",
            color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp),
        )

        OdontogramaVista(
            pacienteId = pacienteId,
            citaId = citaId,
            mapaDental = mapaDental,
            registroPresupuesto = registro,
            onDatos = { hallazgos, catalogo -> sugerido = diagnosticoDesdeHallazgos(hallazgos, catalogo) },
        )

        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                .padding(12.dp),
        ) {
            Text("DIAGNÓSTICO", color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            if (sugerido.isBlank()) {
                Text(
                    "Sin hallazgos marcados — al completar te lo pediremos por escrito.",
                    color = c.textoSuave, fontSize = 13.sp, fontStyle = FontStyle.Italic,
                )
            } else {
                Text(sugerido, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Text("ATENDIÓ", color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            if (atendio != null) {
                Text(atendio, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            } else {
                Text(
                    "Hay varios odontólogos: al completar eliges quién lo atendió.",
                    color = c.textoSuave, fontSize = 13.sp, fontStyle = FontStyle.Italic,
                )
            }
        }
    }
}
