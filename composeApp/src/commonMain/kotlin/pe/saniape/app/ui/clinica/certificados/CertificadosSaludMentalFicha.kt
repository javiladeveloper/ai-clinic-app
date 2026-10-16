package pe.saniape.app.ui.clinica.certificados

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.AtencionRepo
import pe.saniape.app.data.staff.CertificadoApp
import pe.saniape.app.data.staff.CertificadoRepo
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.ICONO_CERTIFICADO
import pe.saniape.app.data.staff.NOMBRE_CERTIFICADO
import pe.saniape.app.data.staff.mensajeVisibilidad
import pe.saniape.app.data.staff.puedeEmitirDocumentos
import pe.saniape.app.data.staff.puedeFirmarComo
import pe.saniape.app.data.staff.resumenCertificado
import pe.saniape.app.ui.AccionesNativas
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.atencion.CampoDialogo
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.fechaDMA
import pe.saniape.app.ui.theme.Sania

// ─────────────────────────────────────────────────────────────────────────────
// "🧠 Certificados de salud mental" de la ficha (pestaña Recetas y documentos) —
// gemelo de components/certificados/CertificadosSaludMental.tsx. Solo pacientes
// de psiquiatría / psicología (pacienteEsSaludMental, nunca por preset). Carga
// SOLO al abrir la pestaña. Lista, reimprime (recepción también, sin el detalle
// del examen), muestra u oculta en la app del paciente y anula; nunca borra.
// Emiten el profesional (a su nombre) y el Admin; el servidor y la RLS lo exigen.
// Un servidor sin /api/staff/certificado (404) → la tarjeta no aparece.
// ─────────────────────────────────────────────────────────────────────────────

/** Mínimo de letras del motivo de anulación (el servidor exige 3). */
private const val MIN_MOTIVO = 3

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContenidoCertificadosSaludMental(
    ctx: ContextoStaff,
    pacienteId: String,
    pacienteNombre: String,
    fichaInactiva: Boolean,
    acciones: AccionesNativas,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val puedeEmitir = puedeEmitirDocumentos(ctx.rol, ctx.miTerapeutaId, ctx.puede("sesiones")) && !fichaInactiva
    var lista by remember(pacienteId) { mutableStateOf<CertificadoRepo.Lista?>(null) }
    var recarga by remember(pacienteId) { mutableStateOf(0) }
    LaunchedEffect(pacienteId, recarga) { lista = CertificadoRepo.listar(pacienteId) }
    var formAbierto by remember { mutableStateOf(false) }
    var anular by remember { mutableStateOf<CertificadoApp?>(null) }
    var motivo by remember { mutableStateOf("") }
    var anulando by remember { mutableStateOf(false) }
    // "🖨" en curso: un doble toque no abre dos visores.
    var abriendo by remember { mutableStateOf<String?>(null) }
    // "📱 Mostrar / 🙈 Ocultar" en curso (uno a la vez).
    var cambiandoVisible by remember { mutableStateOf<String?>(null) }

    // Servidor sin el certificado todavía: no se ofrece nada.
    if (lista == CertificadoRepo.Lista.NoDisponible) return

    fun esMio(cert: CertificadoApp) = puedeEmitir && puedeFirmarComo(ctx.rol, ctx.miTerapeutaId, cert.terapeutaId)

    fun imprimir(id: String, titulo: String) {
        if (abriendo != null) return
        abriendo = id
        scope.launch {
            try {
                AtencionRepo.htmlImprimible("certificado", id)?.let { acciones.abrirHtml(it, titulo) }
                    ?: Toaster.error("No se pudo abrir el certificado. Revisa tu conexión.")
            } finally {
                abriendo = null
            }
        }
    }

    fun alternarVisible(cert: CertificadoApp) {
        if (cambiandoVisible != null) return
        val visible = !cert.visiblePaciente
        cambiandoVisible = cert.id
        scope.launch {
            try {
                val r = CertificadoRepo.visibilidad(cert.id, visible)
                if (r.registrada) {
                    Toaster.exito(mensajeVisibilidad(visible))
                    (lista as? CertificadoRepo.Lista.Ok)?.let { ok ->
                        lista = CertificadoRepo.Lista.Ok(ok.items.map { if (it.id == cert.id) it.copy(visiblePaciente = visible) else it })
                    }
                    recarga++
                } else {
                    Toaster.error(r.rechazo?.error ?: "No se pudo cambiar")
                }
            } finally {
                cambiandoVisible = null
            }
        }
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
    ) {
        Text("$ICONO_CERTIFICADO Certificados de salud mental", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (puedeEmitir) {
            Spacer(Modifier.height(8.dp))
            val forma = RoundedCornerShape(Sania.shape.pill.dp)
            Box(
                Modifier.heightIn(min = 36.dp).clip(forma).background(c.navy).clickable { formAbierto = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text("$ICONO_CERTIFICADO $NOMBRE_CERTIFICADO", color = c.sobreNavy, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(10.dp))
        when (val l = lista) {
            null -> Box(Modifier.fillMaxWidth().padding(8.dp), Alignment.Center) {
                CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp)
            }
            is CertificadoRepo.Lista.Fallo -> Text(l.mensaje, color = c.textoSuave, fontSize = 12.sp)
            is CertificadoRepo.Lista.Ok -> if (l.items.isEmpty()) {
                Text(
                    "Sin certificados emitidos. El texto se arma solo con los datos del paciente y las casillas del examen; " +
                        "sale numerado, con el logo de la clínica, listo para firmar y sellar.",
                    color = c.textoSuave, fontSize = 12.sp,
                )
            } else {
                l.items.forEach { cert ->
                    FilaCertificado(
                        cert = cert,
                        abriendo = abriendo == cert.id,
                        cambiandoVisible = cambiandoVisible == cert.id,
                        puedeGestionar = !cert.anulado && esMio(cert),
                        onImprimir = { imprimir(cert.id, cert.numeroTexto) },
                        onVisibilidad = { alternarVisible(cert) },
                        onAnular = { motivo = ""; anular = cert },
                    )
                    HorizontalDivider(color = c.borde)
                }
            }
            CertificadoRepo.Lista.NoDisponible -> Unit
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "No se editan ni se borran: si uno salió mal, anúlalo (queda en el historial con el motivo) y emite otro." +
                if (!puedeEmitir && !fichaInactiva) " Los emite el psiquiatra o psicólogo que atiende, o el Admin; aquí puedes reimprimirlos." else "",
            color = c.textoSuave, fontSize = 10.sp,
        )
    }

    if (formAbierto) {
        DialogoCertificadoSaludMental(
            ctx = ctx,
            pacienteId = pacienteId,
            pacienteNombre = pacienteNombre,
            onCancelar = { formAbierto = false },
            onEmitido = { id, numero ->
                formAbierto = false
                recarga++
                // Como la web: emitido → la hoja para imprimir.
                if (id.isNotBlank()) imprimir(id, numero)
            },
        )
    }

    anular?.let { cert ->
        DialogoForm(
            titulo = "Anular ${cert.numeroTexto}",
            subtitulo = NOMBRE_CERTIFICADO,
            textoAccion = if (anulando) "Anulando…" else "Anular",
            accionHabilitada = !anulando && motivo.trim().length >= MIN_MOTIVO,
            onCancelar = { if (!anulando) anular = null },
            onAccion = {
                if (!anulando) {
                    anulando = true
                    scope.launch {
                        try {
                            val r = CertificadoRepo.anular(cert.id, motivo)
                            if (r.registrada) {
                                Toaster.exito("Certificado anulado. Queda en el historial.")
                                anular = null
                                recarga++
                            } else {
                                Toaster.error(r.rechazo?.error ?: "No se pudo anular")
                            }
                        } finally {
                            anulando = false
                        }
                    }
                }
            },
        ) {
            Text(
                "El certificado queda en el historial marcado como ANULADO (no se borra) y desaparece de la app del " +
                    "paciente. Si ya lo entregaste impreso, pídele que no lo use.",
                color = c.texto, fontSize = 13.sp,
            )
            Spacer(Modifier.height(10.dp))
            CampoDialogo("Motivo", motivo, { motivo = it.take(300) }, "Ej. Error en el documento del paciente")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilaCertificado(
    cert: CertificadoApp,
    abriendo: Boolean,
    cambiandoVisible: Boolean,
    puedeGestionar: Boolean,
    onImprimir: () -> Unit,
    onVisibilidad: () -> Unit,
    onAnular: () -> Unit,
) {
    val c = Sania.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$ICONO_CERTIFICADO ${cert.numeroTexto}", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(fechaDMA(cert.fecha), color = c.textoSuave, fontSize = 12.sp)
            if (cert.anulado) Pildora("Anulado", c.error, c.errorBg) else Pildora("Emitido", c.ok, c.okBg)
            if (!cert.anulado && cert.visiblePaciente) Pildora("📱 En su app", c.teal, c.tealBg)
            // Solo llega con el detalle (recepción recibe null): veDetalleCertificado en el servidor.
            if (cert.conclusionConfirmada == true) Pildora("Conclusión confirmada", c.pend, c.pendBg)
        }
        Text(
            resumenCertificado(cert.finalidad),
            color = if (cert.anulado) c.textoSuave else c.texto, fontSize = 13.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textDecoration = if (cert.anulado) TextDecoration.LineThrough else null,
            modifier = Modifier.padding(top = 3.dp),
        )
        val sub = listOfNotNull(
            cert.profesionalNombre?.let { n -> n + (cert.profesionalColegiatura?.let { " · $it" } ?: "") },
            cert.motivoAnulacion?.takeIf { cert.anulado }?.let { "Motivo: $it" },
        ).joinToString(" · ")
        if (sub.isNotBlank()) Text(sub, color = c.textoSuave, fontSize = 11.sp)
        FlowRow(
            Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BotonFila(if (abriendo) "Abriendo…" else if (cert.anulado) "🖨 Ver" else "🖨 Imprimir", c.navy, c.navy, habilitado = !abriendo, onClick = onImprimir)
            if (puedeGestionar) {
                BotonFila(
                    when {
                        cambiandoVisible -> "Guardando…"
                        cert.visiblePaciente -> "🙈 Ocultar de su app"
                        else -> "📱 Mostrar en su app"
                    },
                    c.navy, c.borde, habilitado = !cambiandoVisible, onClick = onVisibilidad,
                )
                BotonFila("Anular", c.error, c.error, onClick = onAnular)
            }
        }
    }
}

@Composable
private fun Pildora(texto: String, fg: Color, bg: Color) {
    Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(texto, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BotonFila(texto: String, color: Color, borde: Color, habilitado: Boolean = true, onClick: () -> Unit) {
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    Box(
        Modifier.heightIn(min = 34.dp).clip(forma).border(1.dp, borde, forma)
            .clickable(enabled = habilitado, onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}
