package pe.saniape.app.ui.clinica.psico

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.CAMPOS_FILIACION
import pe.saniape.app.data.staff.EvaluacionPsicoRepo
import pe.saniape.app.data.staff.VersionInformePsico
import pe.saniape.app.data.staff.estadoVersionInforme
import pe.saniape.app.data.staff.fechaDmyPsico
import pe.saniape.app.data.staff.mostrarHistorialInforme
import pe.saniape.app.data.staff.plantillaDelInforme
import pe.saniape.app.data.staff.seccionesNumeradasInforme
import pe.saniape.app.data.staff.textoReemplazoInforme
import pe.saniape.app.ui.AccionesNativas
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.theme.Sania

/**
 * Informe psicológico: borrador armado desde lo registrado → edición liviana →
 * "Emitir" (congela, genera el PDF y lo deja en los documentos del paciente).
 * Se llama "Informe psicológico", nunca "certificado".
 *
 * Fase 2 (gemela de InformePsico.tsx): un emitido no se edita (CPsP art. 18);
 * para corregirlo, "✎ Emitir nueva versión" abre un borrador v+1 (aunque la
 * evaluación esté cerrada). El editor sigue la plantilla copiada en el
 * contenido (orden, títulos, numeración, ocultas) y muestra su encabezado y pie.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SeccionInforme(vm: EvaluacionPsicoViewModel, acciones: AccionesNativas) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val informe = vm.informe
    // A esta pantalla solo entran el Admin y el tratante (el GET lo filtra): el
    // informe se edita mientras sea borrador, aunque la evaluación esté cerrada
    // (borrador de una versión nueva). Armar el PRIMERO sí pide la evaluación abierta.
    val puedeArmarPrimero = !vm.soloLectura
    var editando by remember { mutableStateOf(false) }
    var confirmarArmar by remember { mutableStateOf(false) }
    var confirmarEmitir by remember { mutableStateOf(false) }
    var confirmarVersion by remember { mutableStateOf(false) }

    fun verHtml(informeId: String? = null) {
        scope.launch {
            val html = if (informeId == null) vm.htmlInforme() else vm.htmlDeInforme(informeId)
            if (html == null) Toaster.error("No se pudo abrir el informe. Revisa tu conexión.")
            else acciones.abrirHtml(html, "Informe psicológico")
        }
    }
    /** PDF por id (el vigente o el de una versión del historial): GET foto?documentoId=. */
    fun verPdf(documentoId: String? = vm.informePdf?.id) {
        val id = documentoId ?: return
        scope.launch {
            when (val r = EvaluacionPsicoRepo.urlDeDocumento(id)) {
                is EvaluacionPsicoRepo.VerArchivo.Ok -> acciones.abrirUrl(r.url)
                is EvaluacionPsicoRepo.VerArchivo.Error -> Toaster.error(r.mensaje)
            }
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
            if (puedeArmarPrimero) BotonPsico(if (vm.accionando == "armar") "Armando…" else "📄 Armar borrador del informe",
                relleno = true, habilitado = vm.accionando == null, modifier = Modifier.fillMaxWidth()) { vm.armarInforme() }
        }
        return
    }

    val emitido = informe.emitido
    val editable = !emitido
    val esVersionNueva = informe.esVersionNueva
    val cont = informe.contenido
    val plantilla = plantillaDelInforme(cont)
    val reemplazo = textoReemplazoInforme(cont.reemplazaA)
    val vigente = vm.informes.firstOrNull { it.vigente }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AvisoPsico(
            if (emitido) "Emitido${informe.emitidoAt?.let { " el ${fechaDmyPsico(it)}" } ?: ""} · versión ${informe.version}. Queda congelado (no se edita)."
            else if (esVersionNueva) "Borrador de la versión ${informe.version}: corrígelo y emítelo cuando esté listo."
            else "Borrador: revísalo y emítelo cuando esté listo.",
            if (emitido) c.ok else c.pend, if (emitido) c.okBg else c.pendBg, negrita = true,
        )
        if (emitido) {
            Text("Un informe emitido no se edita (Código de Ética del CPsP, art. 18: sin enmendaduras). Para corregirlo, emite una nueva versión: esta queda en el historial.",
                color = c.textoSuave, fontSize = 12.sp)
        } else if (esVersionNueva) {
            AvisoPsico("${reemplazo.ifEmpty { "Versión ${informe.version}." }} Al emitirla, la versión anterior queda como reemplazada (no se borra) y el paciente verá solo esta.",
                c.pend, c.pendBg)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonPsico(if (emitido) "🖨 Imprimir" else "🖨 Vista previa") { verHtml() }
            if (emitido && vm.informePdf != null) BotonPsico("📎 Abrir PDF") { verPdf() }
            if (editable) {
                BotonPsico(if (editando) "✓ Listo" else "✏ Editar texto", color = c.textoSuave) { editando = !editando }
                BotonPsico("↻ Volver a armar", color = c.textoSuave, habilitado = vm.accionando == null) { confirmarArmar = true }
            }
        }
        if (editable) {
            BotonPsico(
                if (vm.accionando == "emitir") "Emitiendo…" else if (esVersionNueva) "Emitir versión ${informe.version}" else "Emitir informe",
                color = c.ok, relleno = true, habilitado = vm.accionando == null, modifier = Modifier.fillMaxWidth(),
            ) { confirmarEmitir = true }
        } else {
            BotonPsico(
                if (vm.accionando == "version") "Creando…" else "✎ Emitir nueva versión",
                habilitado = vm.accionando == null, modifier = Modifier.fillMaxWidth(),
            ) { confirmarVersion = true }
        }

        // Encabezado fijo de la plantilla (bajo el título del informe).
        if (plantilla.encabezado.isNotBlank()) {
            Text(plantilla.encabezado, color = c.textoSuave, fontSize = 12.sp, fontStyle = FontStyle.Italic)
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

        // Las secciones de la plantilla del informe: su orden, sus títulos, su
        // numeración y SOLO las visibles (motivo y conclusiones nunca se ocultan).
        seccionesNumeradasInforme(plantilla).forEach { s ->
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
        // Pie fijo de la plantilla (después de la firma; p. ej. confidencialidad).
        if (plantilla.pie.isNotBlank()) {
            Text(plantilla.pie, color = c.textoSuave, fontSize = 11.sp, fontStyle = FontStyle.Italic)
        }

        if (mostrarHistorialInforme(vm.informes)) {
            HistorialVersionesInforme(vm.informes, onVer = { verHtml(it) }, onPdf = { verPdf(it) })
        }
    }

    if (confirmarVersion) {
        AlertDialog(
            onDismissRequest = { if (vm.accionando != "version") confirmarVersion = false },
            title = { Text("Emitir nueva versión del informe", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Se crea un borrador de la versión ${informe.version + 1} con el mismo contenido de la versión ${informe.version}, para que lo corrijas.",
                        color = c.texto, fontSize = 14.sp)
                    Text("• La versión ${informe.version} no se modifica ni se borra, y el paciente la sigue viendo hasta que emitas la nueva.", color = c.texto, fontSize = 13.sp)
                    Text("• Al emitir la nueva, la versión ${informe.version} queda como “Reemplazado por v${informe.version + 1}” en el historial.", color = c.texto, fontSize = 13.sp)
                    Text("• La nueva versión indica a cuál reemplaza y con qué fecha se emitió.", color = c.texto, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.nuevaVersionInforme { ok -> confirmarVersion = false; if (ok) editando = true }
                }, enabled = vm.accionando == null) {
                    Text(if (vm.accionando == "version") "Creando…" else "Crear borrador v${informe.version + 1}", color = c.navy, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmarVersion = false }, enabled = vm.accionando != "version") { Text("Cancelar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }

    if (confirmarArmar) {
        AlertDialog(
            onDismissRequest = { confirmarArmar = false },
            title = { Text(if (esVersionNueva) "¿Volver a armar esta versión?" else "¿Volver a armar el informe?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (esVersionNueva) "Se vuelve a armar esta versión con lo registrado en la evaluación (y la plantilla actual de la clínica). Se pierde lo que copiaste o editaste."
                    else "Se arma de nuevo con lo registrado y se pierde lo que editaste a mano.",
                    color = c.texto,
                )
            },
            confirmButton = { TextButton(onClick = { confirmarArmar = false; editando = false; vm.armarInforme() }) { Text("Volver a armar", color = c.navy, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirmarArmar = false }) { Text("Cancelar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }
    if (confirmarEmitir) {
        AlertDialog(
            onDismissRequest = { if (vm.accionando != "emitir") confirmarEmitir = false },
            title = { Text(if (esVersionNueva) "Emitir la versión ${informe.version}" else "Emitir informe psicológico", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Al emitirlo:", color = c.texto, fontSize = 14.sp)
                    Text("• Queda congelado: ya no se puede editar (Código de Ética del CPsP, art. 18).", color = c.texto, fontSize = 13.sp)
                    Text("• Se genera el PDF y el paciente lo puede descargar o imprimir desde su app y el portal.", color = c.texto, fontSize = 13.sp)
                    if (esVersionNueva) {
                        Text("• La versión ${vigente?.version ?: (informe.version - 1)} queda como reemplazada: no se borra (sigue en el historial), pero el paciente deja de verla.",
                            color = c.texto, fontSize = 13.sp)
                    } else {
                        Text("• La evaluación se cierra y el paciente pasa a Evaluado.", color = c.texto, fontSize = 13.sp)
                    }
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

/**
 * Historial de versiones (solo con más de una): vN · Emitido el … · [Vigente ·
 * lo ve el paciente] / [Reemplazado por vM] / [Borrador] · 🖨 Ver · 🔒 PDF.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistorialVersionesInforme(
    versiones: List<VersionInformePsico>,
    onVer: (String) -> Unit,
    onPdf: (String) -> Unit,
) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SubtituloPsico("Historial de versiones")
        versiones.forEach { v ->
            val (estado, tono) = estadoVersionInforme(v)
            val (fg, bg) = when (tono) {
                "vigente" -> c.ok to c.okBg
                "borrador" -> c.pend to c.pendBg
                else -> c.textoSuave to c.fondo
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("v${v.version}", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (v.estado == "emitido") {
                    Text("Emitido${v.emitidoAt?.let { " el ${fechaDmyPsico(it)}" } ?: ""}", color = c.textoSuave, fontSize = 13.sp)
                }
                Text(estado, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp))
                Text("🖨 Ver", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onVer(v.id) })
                // Material protegido: solo Admin y tratante (GET foto?documentoId=).
                v.pdf?.let { pdf ->
                    Text("🔒 PDF", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onPdf(pdf.id) })
                }
            }
        }
    }
}
