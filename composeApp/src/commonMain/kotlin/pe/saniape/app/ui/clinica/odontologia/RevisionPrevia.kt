package pe.saniape.app.ui.clinica.odontologia

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
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
 * DOS cierres (auditoría de flujos 2026-10-24, como la web): «✓ Acepta hoy»
 * crea los tratamientos de lo marcado si el odontólogo no apretó "Crear
 * tratamiento(s)" (idempotente: no duplica); «Lo pensará · enviar enlace» no
 * crea nada, deja el presupuesto sin aceptar y abre el WhatsApp con el enlace
 * para aceptarlo. Nada se crea sin aceptación explícita. Boca sana (nada que
 * presupuestar): un solo «✓ Completar evaluación».
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
    onCompletar: (diagnostico: String, registro: RegistroPresupuesto, decision: DecisionPresupuesto) -> Unit,
    onCancelar: () -> Unit,
    titulo: String = "🦷 Revisión",
    // "Salir sin completar" y no "Cancelar": lo marcado ya se GUARDÓ (cada
    // toque escribe en la base), así que "Cancelar" haría creer que se deshace,
    // y no se deshace. Desde la tarjeta (🦷 Odontograma) es "Cerrar".
    textoCancelar: String = "Salir sin completar",
    /**
     * Atención puntual (2026-10-12): el paciente vino por algo concreto. Con
     * esto aparecen las pestañas "Revisión completa / Atención puntual" arriba
     * (ver DialogoAtencionPuntual). null = solo la revisión completa.
     */
    onAtencionPuntual: (() -> Unit)? = null,
    /** Permiso `sesiones` para crear el tratamiento del presupuesto. */
    puedeCrearTratamientos: Boolean = true,
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
    // ¿Hay algo que presupuestar? Boca sana / todo ya con tratamiento → un solo
    // «✓ Completar evaluación» (no hay nada que aceptar ni que pensar).
    var hayPresupuesto by remember { mutableStateOf(false) }

    DialogoForm(
        titulo = titulo,
        subtitulo = pacienteNombre?.let { "Marca lo que encontraste en ${it.split(' ').first()}" }
            ?: "Marca lo que encontraste",
        textoAccion = when {
            guardando -> "Guardando…"
            hayPresupuesto -> "✓ Acepta hoy"
            else -> "✓ Completar evaluación"
        },
        accionHabilitada = !guardando,
        onCancelar = onCancelar,
        onAccion = { if (!guardando) onCompletar(sugerido, registro, DecisionPresupuesto.ACEPTA) },
        textoCancelar = textoCancelar,
    ) {
        if (onAtencionPuntual != null) SelectorModoAtencion(puntual = false) { if (it) onAtencionPuntual() }
        Text(
            if (hayPresupuesto) "Se guarda al momento. El diagnóstico se redacta solo con lo que marques. " +
                "«✓ Acepta hoy» crea los tratamientos de lo marcado en el presupuesto (si ya los creaste, no se duplican). " +
                "«Lo pensará» no crea nada: le llega el enlace para aceptarlo y queda en «Presupuestos sin aceptar»."
            else "Se guarda al momento. El diagnóstico se redacta solo con lo que marques.",
            color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp),
        )

        OdontogramaVista(
            pacienteId = pacienteId,
            citaId = citaId,
            mapaDental = mapaDental,
            registroPresupuesto = registro,
            puedeCrearTratamientos = puedeCrearTratamientos,
            onDatos = { hallazgos, catalogo ->
                sugerido = diagnosticoDesdeHallazgos(hallazgos, catalogo)
                hayPresupuesto = hayPresupuestoPendiente(hallazgos)
            },
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

        // «Lo pensará · enviar enlace»: completa sin crear tratamientos y abre el
        // WhatsApp con el enlace para aceptar el presupuesto. La principal (abajo)
        // es «✓ Acepta hoy».
        if (hayPresupuesto) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
                    .border(1.5.dp, c.navy, RoundedCornerShape(Sania.shape.md.dp))
                    .clickable(enabled = !guardando, role = Role.Button) {
                        onCompletar(sugerido, registro, DecisionPresupuesto.LO_PENSARA)
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (guardando) "Guardando…" else "Lo pensará · enviar enlace",
                    color = if (guardando) c.textoSuave else c.navy, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
