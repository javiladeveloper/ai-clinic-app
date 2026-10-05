package pe.saniape.app.ui.clinica.psico

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.CAMPOS_FILIACION
import pe.saniape.app.data.staff.EvaluacionPsicoRepo
import pe.saniape.app.data.staff.SECCIONES_INFORME
import pe.saniape.app.data.staff.fechaDmyPsico
import pe.saniape.app.ui.AccionesNativas
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.theme.Sania

/**
 * Informe psicológico: borrador armado desde lo registrado → edición liviana →
 * "Emitir" (congela, genera el PDF y lo deja en los documentos del paciente).
 * Se llama "Informe psicológico", nunca "certificado".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SeccionInforme(vm: EvaluacionPsicoViewModel, acciones: AccionesNativas) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val informe = vm.informe
    val puedeEditar = !vm.soloLectura
    var editando by remember { mutableStateOf(false) }
    var confirmarArmar by remember { mutableStateOf(false) }
    var confirmarEmitir by remember { mutableStateOf(false) }

    fun verHtml() {
        scope.launch {
            val html = vm.htmlInforme()
            if (html == null) Toaster.error("No se pudo abrir el informe. Revisa tu conexión.")
            else acciones.abrirHtml(html, "Informe psicológico")
        }
    }
    fun verPdf() {
        val pdf = vm.informePdf ?: return
        scope.launch {
            val u = EvaluacionPsicoRepo.urlDeInformePdf(pdf)
            if (u == null) Toaster.error("No se pudo abrir el PDF (sin acceso o sin conexión).") else acciones.abrirUrl(u)
        }
    }

    if (informe == null) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("El informe se arma solo con lo que registraste en los seis componentes. Después lo revisas y editas antes de emitirlo.",
                color = c.textoSuave, fontSize = 13.sp)
            if (puedeEditar) BotonPsico(if (vm.accionando == "armar") "Armando…" else "📄 Armar borrador del informe",
                relleno = true, habilitado = vm.accionando == null, modifier = Modifier.fillMaxWidth()) { vm.armarInforme() }
        }
        return
    }

    val emitido = informe.emitido
    val editable = puedeEditar && !emitido
    val cont = informe.contenido

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AvisoPsico(
            if (emitido) "Emitido${informe.emitidoAt?.let { " el ${fechaDmyPsico(it)}" } ?: ""} · versión ${informe.version}. Queda congelado (no se edita)."
            else "Borrador: revísalo y emítelo cuando esté listo.",
            if (emitido) c.ok else c.pend, if (emitido) c.okBg else c.pendBg, negrita = true,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonPsico(if (emitido) "🖨 Imprimir" else "🖨 Vista previa") { verHtml() }
            if (emitido && vm.informePdf != null) BotonPsico("📎 Abrir PDF") { verPdf() }
            if (editable) {
                BotonPsico(if (editando) "✓ Listo" else "✏ Editar texto", color = c.textoSuave) { editando = !editando }
                BotonPsico("↻ Volver a armar", color = c.textoSuave, habilitado = vm.accionando == null) { confirmarArmar = true }
            }
        }
        if (editable) {
            BotonPsico(if (vm.accionando == "emitir") "Emitiendo…" else "Emitir informe", color = c.ok, relleno = true,
                habilitado = vm.accionando == null, modifier = Modifier.fillMaxWidth()) { confirmarEmitir = true }
        }

        // 1. Filiación
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.fondo)
                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SubtituloPsico("1. Datos de filiación")
            CAMPOS_FILIACION.forEach { f ->
                val valor = cont.filiacion[f.valor].orEmpty()
                if (editando && editable) {
                    CampoCortoPsico(f.nombre, valor, { v -> vm.editarInforme(vm.informe!!.contenido.let { it.copy(filiacion = it.filiacion + (f.valor to v)) }) }, false)
                } else if (valor.isNotBlank()) {
                    Text("${f.nombre}: $valor", color = c.texto, fontSize = 13.sp)
                }
            }
        }

        SECCIONES_INFORME.forEach { s ->
            val valor = cont.secciones[s.clave].orEmpty()
            if (editando && editable) {
                TextoLargoPsico("${s.numero}. ${s.titulo}", valor, { v -> vm.editarInforme(vm.informe!!.contenido.let { it.copy(secciones = it.secciones + (s.clave to v)) }) }, false)
            } else {
                Column {
                    EtqForm("${s.numero}. ${s.titulo}")
                    Text(valor.ifBlank { "—" }, color = if (valor.isBlank()) c.textoSuave else c.texto, fontSize = 14.sp)
                }
            }
        }
        if (editando && editable) {
            CampoCortoPsico("Lugar", cont.lugar, { v -> vm.editarInforme(vm.informe!!.contenido.copy(lugar = v)) }, false)
        } else if (cont.lugar.isNotBlank() || cont.fecha.isNotBlank()) {
            Text(listOf(cont.lugar, fechaDmyPsico(cont.fecha)).filter { it.isNotBlank() }.joinToString(", "), color = c.textoSuave, fontSize = 12.sp)
        }
        Text("La fecha del informe es la del día en que se emite. Firma: nombre y C.Ps.P. del profesional.",
            color = c.textoSuave, fontSize = 11.sp)
    }

    if (confirmarArmar) {
        AlertDialog(
            onDismissRequest = { confirmarArmar = false },
            title = { Text("¿Volver a armar el informe?", fontWeight = FontWeight.Bold) },
            text = { Text("Se arma de nuevo con lo registrado y se pierde lo que editaste a mano.", color = c.texto) },
            confirmButton = { TextButton(onClick = { confirmarArmar = false; editando = false; vm.armarInforme() }) { Text("Volver a armar", color = c.navy, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirmarArmar = false }) { Text("Cancelar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }
    if (confirmarEmitir) {
        AlertDialog(
            onDismissRequest = { if (vm.accionando != "emitir") confirmarEmitir = false },
            title = { Text("Emitir informe psicológico", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Al emitirlo:", color = c.texto, fontSize = 14.sp)
                    Text("• Queda congelado: ya no se puede editar (Código de Ética del CPsP, art. 18).", color = c.texto, fontSize = 13.sp)
                    Text("• Se genera el PDF y el paciente lo puede descargar o imprimir desde su app y el portal.", color = c.texto, fontSize = 13.sp)
                    Text("• La evaluación se cierra y el paciente pasa a Evaluado.", color = c.texto, fontSize = 13.sp)
                    Text("Las fotos de los tests y los protocolos nunca se publican.", color = c.textoSuave, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.emitirInforme { ok -> confirmarEmitir = false; if (ok) editando = false }
                }, enabled = vm.accionando == null) { Text(if (vm.accionando == "emitir") "Emitiendo…" else "Emitir", color = c.ok, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmarEmitir = false }, enabled = vm.accionando != "emitir") { Text("Cancelar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }
}
