package pe.saniape.app.ui.clinica.agenda.modales

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.staff.AgendaRepo
import pe.saniape.app.data.staff.CapacidadMeet
import pe.saniape.app.data.staff.CitaStaff
import pe.saniape.app.data.staff.EstadoMeet
import pe.saniape.app.data.staff.FilaVirtual
import pe.saniape.app.data.staff.MODALIDAD_PRESENCIAL
import pe.saniape.app.data.staff.MODALIDAD_VIRTUAL
import pe.saniape.app.data.staff.ORIGEN_MEET_AUTO
import pe.saniape.app.data.staff.ORIGEN_SERVICIO
import pe.saniape.app.data.staff.ResultadoEnlace
import pe.saniape.app.data.staff.errorPideReconectar
import pe.saniape.app.data.staff.estadoMeetCita
import pe.saniape.app.data.staff.proveedorDeEnlace
import pe.saniape.app.data.staff.validarEnlaceVirtual
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.agenda.componentes.BotonUnirse
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania
import pe.saniape.app.ui.theme.tocable

/** Donde se da el permiso de Google (solo en la web: Google no autoriza dentro de la app). */
private fun urlAjustesCalendario() = "${Supabase.SITE_URL.trimEnd('/')}/configuracion/calendario"

/**
 * Panel de la videollamada en el detalle de la cita (gemelo de `PanelVideollamada`
 * de components/citas/VideollamadaCita.tsx). Solo aparece en las citas VIRTUALES:
 * las presenciales se ven igual que siempre.
 *
 * Todo lo que cambia la cita va por `POST /api/staff/cita/virtual` (el mismo
 * endpoint de la web): ahí se valida el enlace, se decide por el plan y se crea /
 * mueve / borra el Google Meet. Si Google falla, la cita queda virtual con el
 * aviso y "Reintentar Meet". Sin [puedeEditar] solo se ve, se une y se copia.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PanelVideollamada(
    cita: CitaStaff,
    puedeEditar: Boolean,
    onCambio: (FilaVirtual) -> Unit = {},
) {
    if (!cita.esVirtual) return
    val c = Sania.colors
    val acciones = recordarAcciones()
    val scope = rememberCoroutineScope()

    // Lo guardado se ve al instante, aunque la agenda tarde en refrescarse.
    var fila by remember(cita.id) {
        mutableStateOf(
            FilaVirtual(
                id = cita.id, modalidad = cita.modalidad, enlaceVirtual = cita.enlaceVirtual,
                enlaceOrigen = cita.enlaceOrigen,
                // Meet automático sin enlace: "creando…" hasta que el servidor diga su estado.
                meetPendiente = cita.enlaceOrigen == ORIGEN_MEET_AUTO && cita.enlaceVirtual == null,
            ),
        )
    }
    var editando by remember(cita.id) { mutableStateOf(false) }
    var borrador by remember(cita.id) { mutableStateOf("") }
    var error by remember(cita.id) { mutableStateOf<String?>(null) }
    var confirmarQuitar by remember(cita.id) { mutableStateOf(false) }
    var capacidad by remember(cita.id) { mutableStateOf<CapacidadMeet?>(null) }
    var trabajando by remember(cita.id) { mutableStateOf(false) }

    val enCurso = cita.estado == "Pendiente" || cita.estado == "Confirmada"
    val estado = estadoMeetCita(fila)

    // Meet automático sin enlace todavía: su estado (creando / error) vive en el
    // servidor (citas_meet, que la app NO lee). Se pregunta al abrir, solo en ese caso.
    val preguntarEstado = fila.esVirtual && fila.enlaceOrigen == ORIGEN_MEET_AUTO && fila.enlaceVirtual == null
    LaunchedEffect(cita.id, preguntarEstado) {
        if (preguntarEstado) AgendaRepo.estadoVirtualCita(cita.id)?.let { fila = it }
    }

    fun ejecutar(exito: String, llamada: suspend () -> AgendaRepo.RespuestaVirtual) {
        // Doble toque: una sola petición a la vez.
        if (trabajando) return
        trabajando = true
        error = null
        scope.launch {
            try {
                val r = llamada()
                if (r.error != null) {
                    error = r.error
                    return@launch
                }
                r.fila?.let { fila = it; onCambio(it) }
                r.meet?.aviso?.let { Toaster.info(it) }
                val m = r.meet
                if (m?.estado == "error" || m?.estado == "pendiente") {
                    Toaster.error(m.mensaje?.takeIf { it.isNotBlank() } ?: "No se pudo crear el Meet; pega un enlace o reintenta.")
                } else Toaster.exito(exito)
                editando = false
                confirmarQuitar = false
            } finally {
                trabajando = false
            }
        }
    }

    fun abrirEditor() {
        borrador = if (fila.enlaceOrigen == ORIGEN_MEET_AUTO) "" else fila.enlaceVirtual.orEmpty()
        error = null
        editando = true
        if (capacidad == null) scope.launch { capacidad = AgendaRepo.capacidadMeet(cita.terapeutaId) }
    }

    fun guardarEnlace() {
        when (val v = validarEnlaceVirtual(borrador)) {
            is ResultadoEnlace.Error -> error = v.error
            is ResultadoEnlace.Ok -> ejecutar("Enlace guardado") {
                AgendaRepo.configurarVirtual(cita.id, MODALIDAD_VIRTUAL, enlace = v.url, meet = false)
            }
        }
    }

    // Pasada a presencial desde aquí: el panel desaparece (la agenda se refresca).
    if (!fila.esVirtual) return

    Column(
        Modifier.fillMaxWidth().padding(top = Sania.dim.md)
            .clip(RoundedCornerShape(Sania.shape.md.dp)).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .background(c.superficie).padding(Sania.dim.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.purpleBg)
                .padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text("🎥 Virtual", color = c.purple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            val proveedor = fila.enlaceVirtual?.let { proveedorDeEnlace(it) }
                ?: if (fila.enlaceOrigen == ORIGEN_MEET_AUTO) "Google Meet" else "Videollamada"
            Text(proveedor + if (fila.enlaceOrigen == ORIGEN_SERVICIO) " · enlace del servicio" else "",
                color = c.textoSuave, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
        }

        fila.enlaceVirtual?.let { enlace ->
            Spacer(Modifier.height(8.dp))
            if (enCurso) {
                BotonUnirse(enlace) { acciones.abrirUrl(enlace) }
                Spacer(Modifier.height(6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(enlace, color = c.navy, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).tocable { acciones.abrirUrl(enlace) })
                Spacer(Modifier.width(8.dp))
                BotonPanel("Copiar", c.texto) {
                    acciones.copiarTexto(enlace, "Enlace de la videollamada")
                    Toaster.exito("Enlace copiado")
                }
            }
        }

        when (estado) {
            EstadoMeet.CREANDO -> Text("⏳ Creando el Google Meet… (si tarda, aparece solo en unos minutos)",
                color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            EstadoMeet.ERROR -> Column(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(c.pendBg).border(1.dp, c.pend, RoundedCornerShape(Sania.shape.sm.dp))
                    .padding(Sania.dim.md),
            ) {
                Text("No se pudo crear el Meet", color = c.pend, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                fila.meetError?.let { Text(it, color = c.pend, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)) }
                if (errorPideReconectar(fila.meetError)) {
                    Spacer(Modifier.height(6.dp))
                    BotonPanel("🔗 Reconectar Google (web)", c.pend) { acciones.abrirUrl(urlAjustesCalendario()) }
                }
            }
            EstadoMeet.SIN_ENLACE -> Text("Falta el enlace de la videollamada.", color = c.pend, fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp))
            else -> Unit
        }

        if (puedeEditar && enCurso) {
            Spacer(Modifier.height(10.dp))
            if (!confirmarQuitar) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if ((estado == EstadoMeet.ERROR || estado == EstadoMeet.CREANDO) && fila.enlaceOrigen == ORIGEN_MEET_AUTO) {
                        BotonPanel(if (trabajando) "Reintentando…" else "↻ Reintentar Meet", c.navy, habilitado = !trabajando) {
                            ejecutar("Google Meet listo") { AgendaRepo.sincronizarMeet(cita.id) }
                        }
                    }
                    BotonPanel(if (fila.enlaceVirtual != null) "Cambiar enlace" else "Pegar enlace", c.texto, habilitado = !trabajando) {
                        abrirEditor()
                    }
                    BotonPanel("Hacer presencial", c.textoSuave, habilitado = !trabajando) { confirmarQuitar = true }
                }
            } else {
                // Confirmación en la misma hoja (como la web): sin otro diálogo encima.
                Text("¿Pasarla a presencial?" + if (fila.enlaceOrigen == ORIGEN_MEET_AUTO) " Se borra el Meet." else "",
                    color = c.texto, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonPanel(if (trabajando) "Guardando…" else "Sí, presencial", c.error, habilitado = !trabajando) {
                        ejecutar("Cita presencial") { AgendaRepo.configurarVirtual(cita.id, MODALIDAD_PRESENCIAL) }
                    }
                    BotonPanel("No", c.textoSuave, habilitado = !trabajando) { confirmarQuitar = false }
                }
            }
            if (!editando) error?.let {
                Text(it, color = c.error, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }

    if (editando) {
        // El botón Guardar va en la cabecera (DialogoForm): con el teclado abierto se ve.
        DialogoForm(
            titulo = "Enlace de la videollamada",
            subtitulo = cita.pacienteNombre,
            textoAccion = if (trabajando) "Guardando…" else "Guardar",
            accionHabilitada = !trabajando && borrador.isNotBlank(),
            onCancelar = { if (!trabajando) { editando = false; error = null } },
            onAccion = { guardarEnlace() },
        ) {
            val cap = capacidad
            val ofrecerMeet = fila.enlaceOrigen != ORIGEN_MEET_AUTO || fila.enlaceVirtual == null
            if (ofrecerMeet && cap?.disponible == true) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .background(if (trabajando) c.ok.copy(alpha = 0.5f) else c.ok)
                        .tocable(habilitado = !trabajando) {
                            ejecutar("Google Meet listo") { AgendaRepo.configurarVirtual(cita.id, MODALIDAD_VIRTUAL, meet = true) }
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (trabajando) "Creando…" else "🎥 Crear Google Meet automáticamente",
                        color = c.superficie, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text("o pega un enlace:", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
            } else if (ofrecerMeet && cap != null && !cap.disponible) {
                Text(
                    if (cap.motivo == "plan") "🔒 El Google Meet automático es de Premium."
                    else "Para crear el Meet solo, conecta Google en Ajustes → Calendario (en la web).",
                    color = c.textoSuave, fontSize = 12.sp,
                )
                if (cap.motivo != "plan") {
                    Text("🔗 Reconectar Google", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp).tocable { acciones.abrirUrl(urlAjustesCalendario()) })
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = borrador,
                onValueChange = { borrador = it; error = null },
                enabled = !trabajando,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = coloresCampoForm(),
                placeholder = { Text("https://meet.google.com/… o zoom.us/j/…", color = c.textoSuave, fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = c.error, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}

@Composable
private fun BotonPanel(texto: String, color: Color, habilitado: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
            .border(1.dp, color.copy(alpha = if (habilitado) 0.6f else 0.3f), RoundedCornerShape(Sania.shape.sm.dp))
            .tocable(habilitado = habilitado) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(texto, color = if (habilitado) color else color.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
