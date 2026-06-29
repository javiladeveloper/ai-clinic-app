package pe.saniape.app.ui.clinica.pacientes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.DocumentoFicha
import pe.saniape.app.data.staff.EspecialidadClinica
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.data.staff.SolicitudFicha
import pe.saniape.app.data.staff.SolicitudesRepo
import pe.saniape.app.ui.AccionesNativas
import pe.saniape.app.ui.theme.Sania

/**
 * Pestaña Exámenes (espeja ExamenesDerivaciones de la web): exámenes externos,
 * derivaciones y documentos del paciente. Gateado por plan (examenes=Plus, derivaciones=Premium)
 * vía ctx.can(). Solo visible con permiso de sesiones (igual que la web).
 */
@Composable
fun ContenidoExamenes(
    ctx: ContextoStaff,
    paciente: pe.saniape.app.data.staff.PacienteStaff,
    acciones: AccionesNativas,
    onAbrirSubida: (categoria: String, solicitudId: String?) -> Unit,
    recargaToken: Int,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val pacienteId = paciente.id
    if (!ctx.puede("sesiones")) {
        Text("No tienes acceso a esta sección.", color = c.textoSuave, fontSize = Sania.txt.cuerpo)
        return
    }

    var solicitudes by remember { mutableStateOf<List<SolicitudFicha>?>(null) }
    var documentos by remember { mutableStateOf<List<DocumentoFicha>?>(null) }
    var especialidades by remember { mutableStateOf<List<EspecialidadClinica>>(emptyList()) }
    var nuevo by remember { mutableStateOf<String?>(null) }            // "Examen" | "Derivacion"
    var resultadoDe by remember { mutableStateOf<SolicitudFicha?>(null) }

    fun recargar() {
        scope.launch {
            solicitudes = runCatching { SolicitudesRepo.solicitudesDe(pacienteId) }.getOrDefault(emptyList())
            documentos = runCatching { SolicitudesRepo.documentosDe(pacienteId) }.getOrDefault(emptyList())
        }
    }
    LaunchedEffect(pacienteId, recargaToken) {
        especialidades = runCatching { PacientesRepo.especialidadesClinica() }.getOrDefault(emptyList())
        recargar()
    }

    val examenes = solicitudes?.filter { it.tipo == "Examen" } ?: emptyList()
    val puedeExamenes = ctx.can("examenes")

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // ── Exámenes externos (Plus) ──
        // (Las derivaciones NO van aquí: nacen de un TRATAMIENTO concreto → se derivan
        //  desde el menú "··· Opciones del tratamiento" en la pestaña Atenciones.)
        SeccionExamenes(
            titulo = "🔬 Exámenes externos",
            subtitulo = "Solicita lab/imágenes; al volver, registra el resultado.",
            habilitado = puedeExamenes, botonTexto = "+ Solicitar",
            onNuevo = { nuevo = "Examen" },
            cargando = solicitudes == null, vacio = examenes.isEmpty(),
            textoVacio = "Sin exámenes solicitados.",
            textoBloqueado = "Los exámenes con adjunto son del plan Plus.",
        ) {
            examenes.forEach { s ->
                FilaSolicitud(s, acciones,
                    onResultado = { resultadoDe = s },
                    onCancelar = { scope.launch { SolicitudesRepo.cambiarEstado(s.id, "Cancelada"); recargar() } },
                    onEliminar = { scope.launch { SolicitudesRepo.eliminarSolicitud(s.id); recargar() } },
                    onAbrir = { path -> scope.launch { SolicitudesRepo.urlFirmada(path)?.let { acciones.abrirUrl(it) } } },
                )
                Spacer(Modifier.height(6.dp))
            }
        }

        // ── Documentos del paciente (Plus) ──
        SeccionExamenes(
            titulo = "📎 Documentos del paciente",
            subtitulo = "Radiografías, tomografías, análisis. PDF o imagen.",
            habilitado = puedeExamenes, botonTexto = "+ Subir",
            onNuevo = { onAbrirSubida("Documento", null) },
            cargando = documentos == null, vacio = (documentos ?: emptyList()).isEmpty(),
            textoVacio = "Sin documentos subidos.",
            textoBloqueado = "Los documentos del paciente son del plan Plus.",
        ) {
            (documentos ?: emptyList()).forEach { d ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (d.tipoArchivo == "pdf") "📄" else "🖼", fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(d.nombre, color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, modifier = Modifier.weight(1f)
                            .clickable { scope.launch { SolicitudesRepo.urlFirmada(d.archivoUrl)?.let { acciones.abrirUrl(it) } } })
                    Text("🗑", fontSize = 14.sp, modifier = Modifier.clickable {
                        scope.launch { SolicitudesRepo.eliminarDocumento(d.id); recargar() }
                    })
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }

    // Modal: solicitar examen externo.
    if (nuevo == "Examen") {
        ModalSolicitarExamen(
            onCancelar = { nuevo = null },
            onGuardar = { desc ->
                nuevo = null
                scope.launch {
                    SolicitudesRepo.crearSolicitud(pacienteId, "Examen", desc, ctx.miTerapeutaId, null); recargar()
                }
            },
        )
    }

    // Modal: registrar resultado de un examen (nota + opción de subir archivo)
    resultadoDe?.let { s ->
        ModalResultado(
            solicitud = s,
            onCancelar = { resultadoDe = null },
            onSubirArchivo = { onAbrirSubida("Resultado", s.id) },
            onGuardar = { nota ->
                resultadoDe = null
                scope.launch { SolicitudesRepo.registrarResultado(s.id, nota, null); recargar() }
            },
        )
    }
}

@Composable
private fun SeccionExamenes(
    titulo: String, subtitulo: String, habilitado: Boolean, botonTexto: String,
    onNuevo: () -> Unit, cargando: Boolean, vacio: Boolean, textoVacio: String, textoBloqueado: String,
    contenido: @Composable () -> Unit,
) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(titulo, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(subtitulo, color = c.textoSuave, fontSize = 10.sp, modifier = Modifier.padding(top = 1.dp))
            }
            if (habilitado) {
                Box(
                    Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.navy)
                        .clickable { onNuevo() }.padding(horizontal = 12.dp, vertical = 6.dp),
                ) { Text(botonTexto, color = c.sobreNavy, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(10.dp))
        when {
            !habilitado -> Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                .background(c.chipBg).padding(12.dp)) {
                Text("🔒 $textoBloqueado", color = c.textoSuave, fontSize = 11.sp)
            }
            cargando -> Box(Modifier.fillMaxWidth().padding(8.dp), Alignment.Center) {
                CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp)
            }
            vacio -> Text(textoVacio, color = c.textoSuave, fontSize = 12.sp)
            else -> contenido()
        }
    }
}

@Composable
private fun FilaSolicitud(
    s: SolicitudFicha, acciones: AccionesNativas,
    onResultado: () -> Unit, onCancelar: () -> Unit, onEliminar: () -> Unit, onAbrir: (String) -> Unit,
) {
    val c = Sania.colors
    val estadoCol = when (s.estado) {
        "Completada" -> c.ok to c.okBg; "Cancelada" -> c.error to c.errorBg; else -> c.pend to c.pendBg
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.descripcion, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(estadoCol.second)
                .padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(
                    when (s.estado) {
                        "Completada" -> if (s.tipo == "Examen") "✓ Con resultado" else "✓ Atendida"
                        "Cancelada" -> "Cancelada"; else -> "Pendiente"
                    }, color = estadoCol.first, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        val sub = listOfNotNull(
            s.fecha.ifBlank { null },
            if (s.tipo == "Derivacion") s.especialidadDestinoNombre?.let { "→ $it" } else null,
            s.terapeutaNombre,
        ).joinToString(" · ")
        if (sub.isNotBlank()) Text(sub, color = c.textoSuave, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))

        // Resultado del examen
        if (s.estado == "Completada" && s.tipo == "Examen" && (!s.resultadoNota.isNullOrBlank() || s.resultadoArchivoUrl != null)) {
            Spacer(Modifier.height(6.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg).padding(8.dp)) {
                s.resultadoNota?.takeIf { it.isNotBlank() }?.let { Text(it, color = c.texto, fontSize = 11.sp) }
                s.resultadoArchivoUrl?.let { path ->
                    Text("📎 Ver resultado adjunto", color = c.navy, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 3.dp).clickable { onAbrir(path) })
                }
            }
        }

        // Acciones (solo Pendiente)
        if (s.estado == "Pendiente") {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (s.tipo == "Examen") {
                    Box(Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.ok)
                        .clickable { onResultado() }.padding(horizontal = 10.dp, vertical = 5.dp)) {
                        Text("📋 Resultado", color = c.sobreNavy, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text("✗ Cancelar", color = c.pend, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onCancelar() })
                Text("🗑", fontSize = 13.sp, modifier = Modifier.clickable { onEliminar() })
            }
        }
    }
}

/** Solicitar un examen externo (solo descripción). */
@Composable
private fun ModalSolicitarExamen(
    onCancelar: () -> Unit, onGuardar: (descripcion: String) -> Unit,
) {
    val c = Sania.colors
    var descripcion by remember { mutableStateOf("") }
    DialogoForm(
        titulo = "Solicitar examen",
        subtitulo = "Examen externo (lab/imágenes)",
        textoAccion = "Solicitar examen",
        accionHabilitada = descripcion.isNotBlank(),
        onCancelar = onCancelar,
        onAccion = { onGuardar(descripcion.trim()) },
    ) {
        TarjetaForm(titulo = "Examen", icono = "🔬") {
            EtqForm("Examen solicitado")
            OutlinedTextField(value = descripcion, onValueChange = { descripcion = it },
                placeholder = { Text("Ej. Ecografía abdominal, hemograma…", color = c.textoSuave) },
                minLines = 2, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ModalResultado(
    solicitud: SolicitudFicha,
    onCancelar: () -> Unit, onSubirArchivo: () -> Unit, onGuardar: (nota: String?) -> Unit,
) {
    val c = Sania.colors
    var nota by remember { mutableStateOf("") }
    DialogoForm(
        titulo = "Resultado del examen",
        subtitulo = solicitud.descripcion,
        textoAccion = "Guardar resultado",
        onCancelar = onCancelar,
        onAccion = { onGuardar(nota.trim().ifBlank { null }) },
    ) {
        TarjetaForm(titulo = "Resultado", icono = "📋") {
            EtqForm("Conclusión / nota")
            OutlinedTextField(value = nota, onValueChange = { nota = it },
                placeholder = { Text("Ej. Hígado normal, sin lesiones…", color = c.textoSuave) },
                minLines = 3, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                .clickable { onSubirArchivo() }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text("📎 Adjuntar archivo (PDF/imagen)", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Text("Puedes guardar solo la nota; el archivo es opcional.", color = c.textoSuave,
                fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * Derivar UN tratamiento a otra especialidad (la derivación nace del tratamiento, no del
 * paciente). [especialidadActual] = la del tratamiento (origen informativo); [destinos] =
 * las OTRAS especialidades de la clínica (excluyendo la actual). Reusable desde el menú
 * "··· Opciones del tratamiento".
 */
@Composable
fun ModalDerivar(
    especialidadActual: String?,
    destinos: List<EspecialidadClinica>,
    onCancelar: () -> Unit,
    onGuardar: (descripcion: String, especialidadDestinoId: String) -> Unit,
) {
    val c = Sania.colors
    var descripcion by remember { mutableStateOf("") }
    var especialidad by remember { mutableStateOf<EspecialidadClinica?>(null) }
    var abierto by remember { mutableStateOf(false) }
    val valido = descripcion.isNotBlank() && especialidad != null
    DialogoForm(
        titulo = "Derivar tratamiento",
        subtitulo = "Pasar este caso a otra especialidad",
        textoAccion = "Derivar",
        accionHabilitada = valido,
        onCancelar = onCancelar,
        onAccion = { especialidad?.let { onGuardar(descripcion.trim(), it.id) } },
    ) {
        TarjetaForm(titulo = "Derivación", icono = "↗") {
            EtqForm("Motivo de la derivación")
            OutlinedTextField(value = descripcion, onValueChange = { descripcion = it },
                placeholder = { Text("Ej. Evaluar por cardiología", color = c.textoSuave) },
                minLines = 2, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(12.dp))
            EtqForm("Derivar de → a")
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Origen (especialidad del tratamiento) — informativo
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .background(c.chipBg).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                        .padding(horizontal = 10.dp, vertical = 11.dp),
                ) {
                    Column {
                        Text("ACTUAL", color = c.textoSuave, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text(especialidadActual ?: "—", color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
                Text("→", color = c.navy, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp))
                // Destino (a escoger)
                Box(Modifier.weight(1f)) {
                    Column {
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                                .background(if (especialidad != null) c.navy.copy(alpha = 0.10f) else c.superficie)
                                .border(if (especialidad != null) 2.dp else 1.dp,
                                    if (especialidad != null) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                                .clickable { abierto = !abierto }.padding(horizontal = 10.dp, vertical = 11.dp),
                        ) {
                            Column {
                                Text("DESTINO", color = c.navy, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                Text(especialidad?.nombre ?: "Elegir…",
                                    color = if (especialidad != null) c.navy else c.textoSuave,
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                        if (abierto) {
                            Column(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                                .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))) {
                                if (destinos.isEmpty()) {
                                    Text("(No hay otras especialidades)", color = c.textoSuave, fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp))
                                }
                                destinos.forEach { e ->
                                    Text(e.nombre, color = c.texto, fontSize = Sania.txt.cuerpo,
                                        modifier = Modifier.fillMaxWidth().clickable { especialidad = e; abierto = false }
                                            .padding(horizontal = 10.dp, vertical = 10.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
