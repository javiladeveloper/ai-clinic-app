package pe.saniape.app.ui.clinica.pacientes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.AtencionRepo
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.InformeMedicoApp
import pe.saniape.app.data.staff.InformesMedicosRepo
import pe.saniape.app.data.staff.TIPOS_DOC_MEDICO
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.iconoTipoDoc
import pe.saniape.app.data.staff.nombreTipoDoc
import pe.saniape.app.data.staff.puedeEmitirDocumentos
import pe.saniape.app.data.staff.puedeFirmarComo
import pe.saniape.app.data.staff.resumenInforme
import pe.saniape.app.ui.AccionesNativas
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.atencion.DialogoInformeMedico
import pe.saniape.app.ui.fechaDMA
import pe.saniape.app.ui.theme.Sania

// ─────────────────────────────────────────────────────────────────────────────
// 📄 Informes, descansos y órdenes (ficha → pestaña de recetas). Gemelo de
// components/informes/DocumentosMedicosPaciente.tsx: carga SOLO al abrir la
// pestaña; lista, reimprime (recepción también) y anula; nunca borra. Emiten el
// profesional (a su nombre) y el Admin; /api/staff/informe/* y la RLS lo exigen.
// ─────────────────────────────────────────────────────────────────────────────

/** Mínimo de letras del motivo de anulación (el servidor exige 3). */
internal const val MIN_MOTIVO_ANULACION = 3

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContenidoDocumentosMedicos(
    ctx: ContextoStaff,
    pacienteId: String,
    pacienteNombre: String,
    fichaInactiva: Boolean,
    acciones: AccionesNativas,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val puedeEmitir = puedeEmitirDocumentos(ctx.rol, ctx.miTerapeutaId, ctx.puede("sesiones")) && !fichaInactiva
    var docs by remember(pacienteId) { mutableStateOf<List<InformeMedicoApp>?>(null) }
    var fallo by remember(pacienteId) { mutableStateOf(false) }
    var recarga by remember(pacienteId) { mutableStateOf(0) }
    LaunchedEffect(pacienteId, recarga) {
        val r = InformesMedicosRepo.listar(pacienteId)
        fallo = r == null
        docs = r ?: emptyList()
    }
    var form by remember { mutableStateOf<String?>(null) }
    var anular by remember { mutableStateOf<InformeMedicoApp?>(null) }
    var motivo by remember { mutableStateOf("") }
    var anulando by remember { mutableStateOf(false) }
    // "🖨" en curso: un doble toque no abre dos visores.
    var abriendo by remember { mutableStateOf<String?>(null) }

    fun imprimir(id: String, titulo: String) {
        if (abriendo != null) return
        abriendo = id
        scope.launch {
            try {
                AtencionRepo.htmlImprimible("informe", id)?.let { acciones.abrirHtml(it, titulo) }
                    ?: Toaster.error("No se pudo abrir el documento. Revisa tu conexión.")
            } finally {
                abriendo = null
            }
        }
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
    ) {
        Text("📄 Informes, descansos y órdenes", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (puedeEmitir) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TIPOS_DOC_MEDICO.forEachIndexed { i, t ->
                    val forma = RoundedCornerShape(Sania.shape.pill.dp)
                    Box(
                        Modifier.heightIn(min = 34.dp).clip(forma).background(if (i == 0) c.navy else c.superficie)
                            .border(1.dp, if (i == 0) c.navy else c.borde, forma)
                            .clickable { form = t }.padding(horizontal = 12.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${iconoTipoDoc(t)} ${nombreTipoDoc(t)}", color = if (i == 0) c.sobreNavy else c.navy,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val lista = docs
        when {
            lista == null -> Box(Modifier.fillMaxWidth().padding(8.dp), Alignment.Center) {
                CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp)
            }
            fallo -> Text("No se pudieron cargar los documentos. Revisa tu conexión.", color = c.textoSuave, fontSize = 12.sp)
            lista.isEmpty() -> Text(
                "Sin documentos emitidos. Cada uno sale numerado, con los datos del establecimiento y del " +
                    "profesional, listo para imprimir, firmar y sellar.",
                color = c.textoSuave, fontSize = 12.sp,
            )
            else -> lista.forEach { inf ->
                FilaDocumentoMedico(
                    inf = inf,
                    abriendo = abriendo == inf.id,
                    puedeAnular = !inf.anulado && puedeEmitir && puedeFirmarComo(ctx.rol, ctx.miTerapeutaId, inf.terapeutaId),
                    onImprimir = { imprimir(inf.id, inf.numeroTexto) },
                    onAnular = { motivo = ""; anular = inf },
                )
                HorizontalDivider(color = c.borde)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "No se editan ni se borran: si uno salió mal, anúlalo (queda en el historial con el motivo) y emite otro." +
                if (!puedeEmitir && !fichaInactiva) " Los emite el profesional que atiende o el Admin; aquí puedes reimprimirlos." else "",
            color = c.textoSuave, fontSize = 10.sp,
        )
    }

    form?.let { tipo ->
        DialogoInformeMedico(
            ctx = ctx,
            tipoInicial = tipo,
            pacienteId = pacienteId,
            pacienteNombre = pacienteNombre,
            prefill = null,
            hoy = hoyClinicaIso(),
            onCancelar = { form = null },
            onEmitido = { id, numero ->
                form = null
                recarga++
                // Como la web: emitido → la hoja para imprimir.
                if (id.isNotBlank()) imprimir(id, numero)
            },
        )
    }

    anular?.let { inf ->
        DialogoForm(
            titulo = "Anular ${inf.numeroTexto}",
            subtitulo = nombreTipoDoc(inf.tipo),
            textoAccion = if (anulando) "Anulando…" else "Anular",
            accionHabilitada = !anulando && motivo.trim().length >= MIN_MOTIVO_ANULACION,
            onCancelar = { if (!anulando) anular = null },
            onAccion = {
                anulando = true
                scope.launch {
                    val r = AtencionRepo.anularInforme(inf.id, motivo)
                    anulando = false
                    if (r.registrada) {
                        Toaster.exito("Documento anulado. Queda en el historial.")
                        anular = null
                        recarga++
                    } else {
                        Toaster.error(r.rechazo?.error ?: "No se pudo anular")
                    }
                }
            },
        ) {
            Text(
                "El documento queda en el historial marcado como ANULADO (no se borra). Si ya lo entregaste impreso, " +
                    "pídele al paciente que no lo use.",
                color = c.texto, fontSize = 13.sp,
            )
            Spacer(Modifier.height(10.dp))
            pe.saniape.app.ui.clinica.atencion.CampoDialogo("Motivo", motivo, { motivo = it.take(300) }, "Ej. Error en las fechas")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilaDocumentoMedico(
    inf: InformeMedicoApp,
    abriendo: Boolean,
    puedeAnular: Boolean,
    onImprimir: () -> Unit,
    onAnular: () -> Unit,
) {
    val c = Sania.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${iconoTipoDoc(inf.tipo)} ${inf.numeroTexto}", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("${nombreTipoDoc(inf.tipo)} · ${fechaDMA(inf.fecha)}", color = c.textoSuave, fontSize = 12.sp)
            val (texto, fg, bg) = if (inf.anulado) Triple("Anulado", c.error, c.errorBg) else Triple("Emitido", c.ok, c.okBg)
            Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(texto, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            if (inf.ocultarDiagnostico) {
                Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.chipBg).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("Dx reservado", color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        val resumen = resumenInforme(inf)
        if (resumen.isNotBlank()) {
            Text(
                resumen, color = if (inf.anulado) c.textoSuave else c.texto, fontSize = 13.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis, textDecoration = if (inf.anulado) TextDecoration.LineThrough else null,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        val sub = listOfNotNull(
            inf.profesionalNombre?.let { n -> n + (inf.profesionalColegiatura?.let { " · $it" } ?: "") },
            inf.motivoAnulacion?.takeIf { inf.anulado }?.let { "Motivo: $it" },
        ).joinToString(" · ")
        if (sub.isNotBlank()) Text(sub, color = c.textoSuave, fontSize = 11.sp)
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).border(1.dp, c.navy, RoundedCornerShape(Sania.shape.sm.dp))
                    .clickable(enabled = !abriendo) { onImprimir() }.padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(if (abriendo) "Abriendo…" else if (inf.anulado) "🖨 Ver" else "🖨 Imprimir",
                    color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            if (puedeAnular) {
                Box(
                    Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).border(1.dp, c.error, RoundedCornerShape(Sania.shape.sm.dp))
                        .clickable { onAnular() }.padding(horizontal = 10.dp, vertical = 5.dp),
                ) { Text("Anular", color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
