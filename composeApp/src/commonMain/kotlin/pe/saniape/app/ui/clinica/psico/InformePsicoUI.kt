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
import pe.saniape.app.data.staff.SECCIONES_IA_PSICO
import pe.saniape.app.data.staff.aceptarPropuestaIa
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.ofrecerAnexoPerfiles
import pe.saniape.app.data.staff.seccionAsistidaIa
import pe.saniape.app.data.staff.EvaluacionPsicoRepo
import pe.saniape.app.data.staff.VersionInformePsico
import pe.saniape.app.data.staff.estadoVersionInforme
import pe.saniape.app.data.staff.fechaDmyPsico
import pe.saniape.app.data.staff.idPdfDeInforme
import pe.saniape.app.data.staff.pdfPendienteDeInforme
import pe.saniape.app.data.staff.puedeDescartarBorrador
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
 *
 * Fase 3: casilla "Incluir anexo de perfiles" (los perfiles los calcula el
 * servidor) y "✨ Redactar con IA" SOLO con [conIA] (`ctx.can("ia")`, hoy
 * apagada en todos los planes): propuestas por sección, nunca se emite sin revisión.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SeccionInforme(vm: EvaluacionPsicoViewModel, acciones: AccionesNativas, conIA: Boolean = false) {
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
    var confirmarDescartar by remember { mutableStateOf(false) }
    // IA (fase 3): propuestas en pantalla (no se guardan hasta aceptarlas) y su consentimiento.
    var propuestas by remember { mutableStateOf<Map<String, String>?>(null) }
    var iaModelo by remember { mutableStateOf("") }
    var iaSinConsentimiento by remember { mutableStateOf(false) }
    var pedirConsentimiento by remember { mutableStateOf(false) }
    var confirmoConsentimiento by remember { mutableStateOf(false) }
    var reemplazarIa by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun redactar(confirmar: Boolean) {
        vm.redactarIA(confirmar) { r ->
            when (r) {
                is EvaluacionPsicoViewModel.ResultadoIa.Ok -> {
                    pedirConsentimiento = false; confirmoConsentimiento = false
                    iaModelo = r.propuestas.modelo
                    iaSinConsentimiento = r.sinConsentimientoConfirmado
                    propuestas = r.propuestas.propuestas
                    editando = true
                }
                EvaluacionPsicoViewModel.ResultadoIa.FaltaConsentimiento -> pedirConsentimiento = true
                EvaluacionPsicoViewModel.ResultadoIa.Error -> Unit
            }
        }
    }
    fun quitarPropuesta(clave: String) {
        propuestas = propuestas?.minus(clave)?.ifEmpty { null }
    }
    fun aceptarIa(clave: String, texto: String, reemplazar: Boolean) {
        val actual = vm.informe ?: return
        vm.editarInforme(aceptarPropuestaIa(actual.contenido, clave, texto, iaModelo, hoyClinicaIso(), iaSinConsentimiento, reemplazar))
        quitarPropuesta(clave)
    }

    fun verHtml(informeId: String? = null) {
        scope.launch {
            val html = if (informeId == null) vm.htmlInforme() else vm.htmlDeInforme(informeId)
            if (html == null) Toaster.error("No se pudo abrir el informe. Revisa tu conexión.")
            else acciones.abrirHtml(html, "Informe psicológico")
        }
    }
    /** PDF por id (el del informe mostrado o el de una versión del historial): GET foto?documentoId=. */
    fun verPdf(documentoId: String?) {
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
    // El PDF del informe MOSTRADO, nunca `informePdf` (el vigente) por descarte:
    // con un borrador v2 abierto o con el PDF de la vN pendiente, son otros.
    val pdfPropio = idPdfDeInforme(informe, vm.informes)
    val pdfPendiente = pdfPendienteDeInforme(informe, vm.informes)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AvisoPsico(
            if (emitido) "Emitido${informe.emitidoAt?.let { " el ${fechaDmyPsico(it)}" } ?: ""} · versión ${informe.version}. Queda congelado (no se edita)."
            else if (esVersionNueva) "Borrador de la versión ${informe.version}: corrígelo y emítelo cuando esté listo."
            else "Borrador: revísalo y emítelo cuando esté listo.",
            if (emitido) c.ok else c.pend, if (emitido) c.okBg else c.pendBg, negrita = true,
        )
        if (pdfPendiente) {
            AvisoPsico("El PDF de esta versión no se llegó a generar: el paciente sigue viendo la versión anterior hasta generarlo.",
                c.pend, c.pendBg)
            BotonPsico(if (vm.accionando == "pdf:${informe.id}") "Generando…" else "📄 Generar PDF pendiente", color = c.ok, relleno = true,
                habilitado = vm.accionando == null, modifier = Modifier.fillMaxWidth()) { vm.generarPdfPendiente(informe.id) }
        }
        if (emitido) {
            Text("Un informe emitido no se edita (Código de Ética del CPsP, art. 18: sin enmendaduras). Para corregirlo, emite una nueva versión: esta queda en el historial.",
                color = c.textoSuave, fontSize = 12.sp)
        } else if (esVersionNueva) {
            AvisoPsico("${reemplazo.ifEmpty { "Versión ${informe.version}." }} Al emitirla, la versión anterior queda como reemplazada (no se borra) y el paciente verá solo esta.",
                c.pend, c.pendBg)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonPsico(if (emitido) "🖨 Imprimir" else "🖨 Vista previa") { verHtml() }
            if (pdfPropio != null) BotonPsico("📎 Abrir PDF") { verPdf(pdfPropio) }
            if (editable) {
                BotonPsico(if (editando) "✓ Listo" else "✏ Editar texto", color = c.textoSuave) { editando = !editando }
                BotonPsico("↻ Volver a armar", color = c.textoSuave, habilitado = vm.accionando == null) { confirmarArmar = true }
                if (conIA) {
                    BotonPsico(if (vm.accionando == "ia") "✨ Redactando…" else "✨ Redactar con IA", habilitado = vm.accionando == null) { redactar(false) }
                }
            }
        }
        if (editable) {
            BotonPsico(
                if (vm.accionando == "emitir") "Emitiendo…" else if (esVersionNueva) "Emitir versión ${informe.version}" else "Emitir informe",
                color = c.ok, relleno = true, habilitado = vm.accionando == null, modifier = Modifier.fillMaxWidth(),
            ) { confirmarEmitir = true }
            if (puedeDescartarBorrador(informe)) {
                BotonPsico(if (vm.accionando == "descartar") "Descartando…" else "🗑 Descartar borrador", color = c.textoSuave,
                    habilitado = vm.accionando == null, modifier = Modifier.fillMaxWidth()) { confirmarDescartar = true }
            }
        } else if (!pdfPendiente) {
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

        val secciones = seccionesNumeradasInforme(plantilla)
        val props = propuestas
        if (props != null && editable && conIA) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg)
                    .border(2.dp, c.navy, RoundedCornerShape(Sania.shape.sm.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SubtituloPsico("✨ Propuestas de la IA")
                Text("Borrador de apoyo: revisa, corrige y acepta por sección. Nada se guarda ni se emite sin ti.", color = c.textoSuave, fontSize = 12.sp)
                SECCIONES_IA_PSICO.filter { it in props }.forEach { clave ->
                    val titulo = secciones.firstOrNull { it.clave == clave }?.let { "${it.numero}. ${it.titulo}" } ?: clave
                    PropuestaIaPsico(titulo, props.getValue(clave),
                        onDescartar = { quitarPropuesta(clave) },
                        onAceptar = { texto ->
                            if (vm.informe?.contenido?.secciones?.get(clave).isNullOrBlank()) aceptarIa(clave, texto, false)
                            else reemplazarIa = clave to texto
                        })
                }
                BotonPsico("Descartar todas", color = c.textoSuave, modifier = Modifier.fillMaxWidth()) { propuestas = null }
            }
        }

        // Las secciones de la plantilla del informe: su orden, sus títulos, su
        // numeración y SOLO las visibles (motivo y conclusiones nunca se ocultan).
        secciones.forEach { s ->
            val valor = cont.secciones[s.clave].orEmpty()
            val sello = if (seccionAsistidaIa(cont, s.clave)) "  ✨ asistido por IA" else ""
            if (editando && editable) {
                TextoLargoPsico("${s.numero}. ${s.titulo}$sello", valor, { v -> vm.editarInforme(vm.informe!!.contenido.let { it.copy(secciones = it.secciones + (s.clave to v)) }) }, false)
            } else {
                Column {
                    EtqForm("${s.numero}. ${s.titulo}$sello")
                    Text(valor.ifBlank { "—" }, color = if (valor.isBlank()) c.textoSuave else c.texto, fontSize = 14.sp)
                }
            }
        }

        // Fase 3: anexo "Perfil de puntajes" (gráficos de los tests que entran al informe).
        if (ofrecerAnexoPerfiles(vm.fase3, cont)) {
            val anexo = cont.anexoPerfiles
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CasillaPsico("Incluir anexo de perfiles (gráficos de puntajes con bandas de corte)", anexo?.incluir == true, !editable) {
                    vm.incluirAnexoPerfiles(it)
                }
                if (anexo?.incluir == true) {
                    Text(
                        (if (anexo.perfiles.isNotEmpty()) "${anexo.perfiles.size} gráfico(s): " else "") +
                            "solo los tests que entran al informe y tienen puntajes numéricos. " +
                            if (emitido) "Quedó fijo al emitir." else "Se actualiza al guardar y queda fijo al emitir.",
                        color = c.textoSuave, fontSize = 12.sp,
                    )
                    anexo.perfiles.forEach { p ->
                        Column(Modifier.padding(top = 6.dp)) {
                            TituloPerfilPsico(listOf(p.titulo, fechaDmyPsico(p.fecha)).filter { it.isNotBlank() }.joinToString(" · "))
                            GraficoPerfilPsico(p)
                        }
                    }
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
            HistorialVersionesInforme(vm.informes, ocupado = vm.accionando != null, onVer = { verHtml(it) }, onPdf = { verPdf(it) },
                onGenerarPdf = { vm.generarPdfPendiente(it) })
        }
    }

    reemplazarIa?.let { (clave, texto) ->
        AlertDialog(
            onDismissRequest = { reemplazarIa = null },
            title = { Text("¿Reemplazar el texto?", fontWeight = FontWeight.Bold) },
            text = { Text("Esa sección ya tiene texto. ¿Reemplazarlo por la propuesta?", color = c.texto) },
            confirmButton = { TextButton(onClick = { reemplazarIa = null; aceptarIa(clave, texto, true) }) { Text("Reemplazar", color = c.navy, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { reemplazarIa = null }) { Text("Cancelar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }
    if (pedirConsentimiento) {
        AlertDialog(
            onDismissRequest = { if (vm.accionando != "ia") { pedirConsentimiento = false; confirmoConsentimiento = false } },
            title = { Text("✨ Redactar con IA: consentimiento", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AvisoPsico("⚠ No consta un consentimiento firmado de este paciente que mencione el uso de inteligencia artificial. El Código de Ética del CPsP (art. 45) permite usarla solo como apoyo y con consentimiento.",
                        c.pend, c.pendBg)
                    Text("El modelo de “Consentimiento de evaluación psicológica” de Sania ya lo incluye. A la IA no se envían el nombre ni el DNI: solo edad, sexo y lo registrado en la evaluación.",
                        color = c.textoSuave, fontSize = 12.sp)
                    CasillaPsico("Confirmo que el paciente o su representante autorizó el uso de herramientas de IA como apoyo para redactar este informe.",
                        confirmoConsentimiento, false) { confirmoConsentimiento = it }
                }
            },
            confirmButton = {
                TextButton(onClick = { redactar(true) }, enabled = confirmoConsentimiento && vm.accionando == null) {
                    Text(if (vm.accionando == "ia") "Redactando…" else "Continuar", color = c.navy, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pedirConsentimiento = false; confirmoConsentimiento = false }, enabled = vm.accionando != "ia") { Text("Cancelar", color = c.textoSuave) }
            },
            containerColor = c.superficie,
        )
    }
    if (confirmarDescartar) {
        AlertDialog(
            onDismissRequest = { if (vm.accionando != "descartar") confirmarDescartar = false },
            title = { Text("¿Descartar el borrador de la versión ${informe.version}?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Se borra este borrador y lo que corregiste en él. La versión vigente${vigente?.let { " (v${it.version})" } ?: ""} no cambia y el paciente la sigue viendo.",
                    color = c.texto)
            },
            confirmButton = {
                TextButton(onClick = { vm.descartarBorrador { ok -> confirmarDescartar = false; if (ok) editando = false } },
                    enabled = vm.accionando == null) {
                    Text(if (vm.accionando == "descartar") "Descartando…" else "Descartar", color = c.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmarDescartar = false }, enabled = vm.accionando != "descartar") { Text("Cancelar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
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

/** Una propuesta de la IA: texto editable, "Descartar" y "Aceptar en el informe". */
@Composable
private fun PropuestaIaPsico(titulo: String, texto: String, onDescartar: () -> Unit, onAceptar: (String) -> Unit) {
    val c = Sania.colors
    var v by remember(titulo, texto) { mutableStateOf(texto) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TextoLargoPsico(titulo, v, { v = it }, false)
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonPsico("Descartar", color = c.textoSuave, modifier = Modifier.weight(1f)) { onDescartar() }
            BotonPsico("Aceptar en el informe", color = c.ok, relleno = true, habilitado = v.isNotBlank(), modifier = Modifier.weight(1.4f)) { onAceptar(v) }
        }
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
    ocupado: Boolean,
    onVer: (String) -> Unit,
    onPdf: (String) -> Unit,
    onGenerarPdf: (String) -> Unit,
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
                "borrador", "pendiente" -> c.pend to c.pendBg
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
                v.idPdf?.let { id ->
                    Text("🔒 PDF", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onPdf(id) })
                }
                if (v.pdfPendiente) {
                    Text("📄 Generar PDF", color = if (ocupado) c.textoSuave else c.ok, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(enabled = !ocupado) { onGenerarPdf(v.id) })
                }
            }
        }
    }
}
