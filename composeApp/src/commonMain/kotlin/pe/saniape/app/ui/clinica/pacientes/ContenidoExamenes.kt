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
    pacienteId: String,
    acciones: AccionesNativas,
    onAbrirSubida: (categoria: String, solicitudId: String?) -> Unit,
    recargaToken: Int,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
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
    val derivaciones = solicitudes?.filter { it.tipo == "Derivacion" } ?: emptyList()
    val puedeExamenes = ctx.can("examenes")
    val puedeDerivaciones = ctx.can("derivaciones")

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // ── Exámenes externos (Plus) ──
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

        // ── Derivaciones (Premium) ──
        SeccionExamenes(
            titulo = "↗ Derivaciones",
            subtitulo = "Deriva al paciente a otra especialidad de la clínica.",
            habilitado = puedeDerivaciones, botonTexto = "+ Derivar",
            onNuevo = { nuevo = "Derivacion" },
            cargando = solicitudes == null, vacio = derivaciones.isEmpty(),
            textoVacio = "Sin derivaciones.",
            textoBloqueado = "Las derivaciones son del plan Premium.",
        ) {
            derivaciones.forEach { s ->
                FilaSolicitud(s, acciones,
                    onResultado = {},
                    onCancelar = { scope.launch { SolicitudesRepo.cambiarEstado(s.id, "Cancelada"); recargar() } },
                    onEliminar = { scope.launch { SolicitudesRepo.eliminarSolicitud(s.id); recargar() } },
                    onAbrir = {},
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

    // Modal: nueva solicitud (examen o derivación)
    nuevo?.let { tipo ->
        ModalNuevaSolicitud(
            tipo = tipo, especialidades = especialidades.filter { it.usaSesiones || true },
            onCancelar = { nuevo = null },
            onGuardar = { desc, espId ->
                nuevo = null
                scope.launch {
                    SolicitudesRepo.crearSolicitud(pacienteId, tipo, desc, ctx.miTerapeutaId, espId); recargar()
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

@Composable
private fun ModalNuevaSolicitud(
    tipo: String, especialidades: List<EspecialidadClinica>,
    onCancelar: () -> Unit, onGuardar: (descripcion: String, especialidadId: String?) -> Unit,
) {
    val c = Sania.colors
    val esExamen = tipo == "Examen"
    var descripcion by remember { mutableStateOf("") }
    var especialidad by remember { mutableStateOf<EspecialidadClinica?>(null) }
    var abierto by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(if (esExamen) "🔬 Solicitar examen" else "↗ Derivar paciente", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(if (esExamen) "EXAMEN SOLICITADO" else "MOTIVO DE LA DERIVACIÓN",
                    color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 5.dp))
                OutlinedTextField(value = descripcion, onValueChange = { descripcion = it },
                    placeholder = { Text(if (esExamen) "Ej. Ecografía abdominal…" else "Ej. Evaluar por cardiología", color = c.textoSuave) },
                    minLines = 2, modifier = Modifier.fillMaxWidth())
                if (!esExamen) {
                    Spacer(Modifier.height(10.dp))
                    Text("ESPECIALIDAD DE DESTINO", color = c.textoSuave, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 5.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                            .clickable { abierto = !abierto }.padding(horizontal = 12.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(especialidad?.nombre ?: "Seleccionar…", color = c.texto, fontSize = Sania.txt.cuerpo)
                        Text("▾", color = c.navy, fontSize = 12.sp)
                    }
                    if (abierto) {
                        Column(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                            .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))) {
                            especialidades.forEach { e ->
                                Text(e.nombre, color = c.texto, fontSize = Sania.txt.cuerpo,
                                    modifier = Modifier.fillMaxWidth().clickable { especialidad = e; abierto = false }
                                        .padding(horizontal = 12.dp, vertical = 11.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val valido = descripcion.isNotBlank() && (esExamen || especialidad != null)
            Box(Modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(if (valido) c.navy else c.borde)
                .clickable(enabled = valido) { onGuardar(descripcion.trim(), especialidad?.id) }
                .padding(horizontal = 18.dp, vertical = 10.dp)) {
                Text("Guardar", color = if (valido) c.sobreNavy else c.textoSuave, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar", color = c.textoSuave) } },
        containerColor = c.superficie,
    )
}

@Composable
private fun ModalResultado(
    solicitud: SolicitudFicha,
    onCancelar: () -> Unit, onSubirArchivo: () -> Unit, onGuardar: (nota: String?) -> Unit,
) {
    val c = Sania.colors
    var nota by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("📋 Resultado del examen", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(solicitud.descripcion, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp))
                Text("CONCLUSIÓN / NOTA", color = c.textoSuave, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 5.dp))
                OutlinedTextField(value = nota, onValueChange = { nota = it },
                    placeholder = { Text("Ej. Hígado normal, sin lesiones…", color = c.textoSuave) },
                    minLines = 3, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Box(Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                    .clickable { onSubirArchivo() }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text("📎 Adjuntar archivo (PDF/imagen)", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text("Puedes guardar solo la nota; el archivo es opcional.", color = c.textoSuave,
                    fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = {
            Box(Modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.navy)
                .clickable { onGuardar(nota.trim().ifBlank { null }) }.padding(horizontal = 18.dp, vertical = 10.dp)) {
                Text("Guardar resultado", color = c.sobreNavy, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar", color = c.textoSuave) } },
        containerColor = c.superficie,
    )
}
