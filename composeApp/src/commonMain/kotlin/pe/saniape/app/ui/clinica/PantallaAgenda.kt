package pe.saniape.app.ui.clinica

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.CitaStaff
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.ui.hora12
import pe.saniape.app.ui.clinica.agenda.AccionCita
import pe.saniape.app.ui.clinica.agenda.AgendaViewModel
import pe.saniape.app.ui.clinica.agenda.componentes.AccionTarjeta
import pe.saniape.app.ui.clinica.agenda.componentes.FiltrosAgenda
import pe.saniape.app.ui.clinica.agenda.componentes.TarjetaCita
import pe.saniape.app.ui.clinica.agenda.componentes.TiraDias
import pe.saniape.app.ui.clinica.agenda.modales.ConfirmacionAccion
import pe.saniape.app.ui.clinica.agenda.modales.ModalCompletar
import pe.saniape.app.ui.clinica.agenda.modales.ModalEditarCita
import pe.saniape.app.ui.clinica.agenda.modales.ModalPasarEvaluacion
import pe.saniape.app.ui.clinica.agenda.modales.ResumenClinicoSheet
import pe.saniape.app.ui.clinica.pacientes.PantallaFichaPaciente
import pe.saniape.app.data.staff.PacienteStaff
import pe.saniape.app.data.staff.PacientesRepo
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import pe.saniape.app.ui.theme.Sania

/**
 * Agenda del staff. Pantalla DELGADA: solo observa el [AgendaViewModel] y dispara
 * intents. La lógica vive en el ViewModel; los componentes (TarjetaCita, TiraDias,
 * banners, modales) están en archivos propios. Escalable y fácil de mantener.
 */
@Composable
fun PantallaAgenda(ctx: ContextoStaff) {
    val c = Sania.colors
    val vm: AgendaViewModel = viewModel(key = ctx.clinicaId) { AgendaViewModel(ctx) }

    // Sub-pantalla: crear cita (con o sin pre-llenado de → Evaluación)
    var creandoCita by remember { mutableStateOf(false) }
    var prefillEval by remember { mutableStateOf<PrefillCita?>(null) }
    // Modales (la cita objetivo, o null)
    var completar by remember { mutableStateOf<CitaStaff?>(null) }
    var confirmar by remember { mutableStateOf<Pair<CitaStaff, AccionCita>?>(null) }
    var editar by remember { mutableStateOf<CitaStaff?>(null) }
    var pasarEval by remember { mutableStateOf<CitaStaff?>(null) }
    // Resumen clínico (popup al tocar el nombre) + ficha completa que abre desde ahí.
    var resumenPacienteId by remember { mutableStateOf<String?>(null) }
    var fichaPaciente by remember { mutableStateOf<PacienteStaff?>(null) }
    var cargandoFicha by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (creandoCita || prefillEval != null) {
        PantallaCrearCita(
            ctx = ctx, fechaInicial = vm.fechaSel,
            prefill = prefillEval,
            onListo = { creandoCita = false; prefillEval = null; vm.refrescar() },
            onCancelar = { creandoCita = false; prefillEval = null },
        )
        return
    }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Barra con marca de la clínica (logo white-label) + "Agenda" + "+ Nueva"
            Row(
                Modifier.fillMaxWidth().background(c.navyDark)
                    .padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LogoMarcaChica(ctx)
                    Spacer(Modifier.width(10.dp))
                    Text("Agenda", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
                }
                // Agendar (crear cita) solo con permiso 'agendar' (recepción/admin). El profesional
                // que solo atiende ve su agenda pero no agenda.
                if (ctx.puede("agendar")) {
                    Box(
                        Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                            .background(c.sobreNavy.copy(alpha = 0.15f))
                            .clickable { creandoCita = true }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) { Text("+ Nueva", color = c.sobreNavy, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
            }

            // La tira de días solo aplica en modo "día" (no en la lista/historial).
            if (!vm.modoLista) {
                TiraDias(hoy = vm.hoy, seleccionado = vm.fechaSel, onSeleccionar = { vm.seleccionarDia(it) })
            }

            FiltrosAgenda(
                busqueda = vm.busqueda, onBusqueda = { vm.cambiarBusqueda(it) },
                filtroEstado = vm.filtroEstado, onEstado = { vm.cambiarFiltroEstado(it) },
                filtroTipo = vm.filtroTipo, onTipo = { vm.cambiarFiltroTipo(it) },
                especialidades = vm.especialidades,
                filtroEspecialidad = vm.filtroEspecialidad, onEspecialidad = { vm.cambiarFiltroEspecialidad(it) },
                verHistorial = vm.verHistorial, onVerHistorial = { vm.alternarHistorial() },
            )

            vm.mensaje?.let {
                Text(it, color = c.navy, fontSize = Sania.txt.pequeno,
                    modifier = Modifier.padding(horizontal = Sania.dim.lg, vertical = 4.dp))
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Sania.dim.xl),
            ) {
                // Banners (mañana / vencidas / derivaciones)
                vm.banners?.let { b ->
                    item {
                        BannersAgendaUI(
                            banners = b,
                            onVerCitaManana = { vm.seleccionarDia(it.fecha) },
                            onCerrarVencida = { cita, vino ->
                                if (vino) {
                                    if (cita.tipo == "Evaluación" || cita.tipo == "Sesión") completar = cita
                                    else vm.ejecutar(AccionCita.Completar, cita)
                                } else vm.ejecutar(AccionCita.Cancelar, cita)
                            },
                            onReagendarVencida = { editar = it },   // abre el modal de fecha/hora
                            // Agendar la evaluación de la derivación: form pre-llenado con el
                            // paciente + tipo Evaluación + especialidad de destino. El profesional
                            // queda opcional (en rayos X puede no haber uno propio). Marca procesada.
                            onAgendarDerivacion = { d ->
                                vm.marcarDerivacion(d.id)
                                prefillEval = PrefillCita(
                                    tipo = "Evaluación",
                                    pacienteId = d.pacienteId,
                                    pacienteNombre = d.pacienteNombre,
                                    fecha = vm.fechaSel, hora = "09:00",
                                    terapeutaId = null,
                                    especialidadId = d.especialidadDestinoId,
                                )
                            },
                            onMarcarDerivacion = { vm.marcarDerivacion(it.id) },
                        )
                    }
                }

                // Aviso de citas sin profesional asignado (origen web sin terapeuta).
                if (vm.citasSinProfesional.isNotEmpty()) {
                    item {
                        BannerSinProfesional(
                            citas = vm.citasSinProfesional,
                            onAsignar = { editar = it },
                        )
                    }
                }

                // Aviso sutil al cambiar de día / refrescar: mantiene la lista visible
                // (no parpadea a spinner completo).
                if (vm.recargando) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = Sania.dim.lg, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Actualizando…", color = c.textoSuave, fontSize = Sania.txt.mini)
                        }
                    }
                }

                when {
                    vm.cargando -> item {
                        Box(Modifier.fillMaxWidth().padding(Sania.dim.xxl), Alignment.Center) {
                            CircularProgressIndicator(color = c.navy)
                        }
                    }
                    vm.citasFiltradas.isEmpty() -> item {
                        Box(Modifier.fillMaxWidth().padding(Sania.dim.lg)) {
                            when {
                                vm.citas.isEmpty() && vm.verHistorial -> pe.saniape.app.ui.clinica.EstadoVacio(
                                    emoji = "🗂", titulo = "Historial vacío",
                                    subtitulo = "Aún no hay citas registradas.",
                                )
                                vm.citas.isEmpty() -> pe.saniape.app.ui.clinica.EstadoVacio(
                                    emoji = "🌤", titulo = "Sin citas este día",
                                    subtitulo = if (ctx.puede("agendar")) "Agenda la primera cita del día." else "No hay nada programado.",
                                    textoAccion = if (ctx.puede("agendar")) "+ Nueva cita" else null,
                                    onAccion = { creandoCita = true },
                                )
                                else -> pe.saniape.app.ui.clinica.EstadoVacio(
                                    emoji = "🔍", titulo = "Sin resultados",
                                    subtitulo = "No hay citas con esos filtros.",
                                )
                            }
                        }
                    }
                    else -> items(vm.citasFiltradas, key = { it.id }) { cita ->
                        Box(Modifier.padding(horizontal = Sania.dim.lg, vertical = Sania.dim.sm / 2)) {
                            TarjetaCita(
                                cita = cita,
                                puedeVerCosto = ctx.puede("pagos"),
                                accionando = vm.accionando,
                                onAccion = { accion ->
                                    when (accion) {
                                        AccionTarjeta.Confirmar -> vm.ejecutar(AccionCita.Confirmar, cita)
                                        AccionTarjeta.Completar ->
                                            if (cita.tipo == "Evaluación" || cita.tipo == "Sesión") completar = cita
                                            else vm.ejecutar(AccionCita.Completar, cita)
                                        AccionTarjeta.Cancelar -> confirmar = cita to AccionCita.Cancelar
                                        AccionTarjeta.Revertir -> confirmar = cita to AccionCita.Revertir
                                        AccionTarjeta.Editar -> editar = cita
                                        AccionTarjeta.PasarEvaluacion -> pasarEval = cita
                                        AccionTarjeta.Repetir -> prefillEval = repetirDesde(cita)
                                    }
                                },
                                onVerResumen = { resumenPacienteId = it },
                            )
                        }
                    }
                }

                // Paginación (solo en modo lista/historial).
                if (vm.modoLista && !vm.cargando && (vm.pagina > 0 || vm.hayMasPaginas)) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(Sania.dim.lg),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BotonPagina("← Anterior", habilitado = vm.pagina > 0) { vm.paginaAnterior() }
                            Text("Página ${vm.pagina + 1}", color = c.textoSuave, fontSize = Sania.txt.pequeno)
                            BotonPagina("Siguiente →", habilitado = vm.hayMasPaginas) { vm.paginaSiguiente() }
                        }
                    }
                }
            }
        }
    }

    // ── Modales ──
    completar?.let { cita ->
        ModalCompletar(
            cita = cita, especialidades = vm.especialidades,
            onCancelar = { completar = null },
            onConfirmar = { obs, diag, espId ->
                completar = null
                vm.ejecutar(AccionCita.Completar, cita, obs, diag, espId)
            },
        )
    }
    confirmar?.let { (cita, accion) ->
        ConfirmacionAccion(
            cita = cita, accion = accion,
            onCancelar = { confirmar = null },
            onConfirmar = { confirmar = null; vm.ejecutar(accion, cita) },
        )
    }
    editar?.let { cita ->
        ModalEditarCita(
            cita = cita,
            onCancelar = { editar = null },
            onGuardar = { fecha, hora -> vm.reprogramar(cita, fecha, hora) { editar = null } },
        )
    }
    pasarEval?.let { cita ->
        ModalPasarEvaluacion(
            cita = cita,
            onCancelar = { pasarEval = null },
            onElegir = { fecha, hora ->
                // Igual que la web: abre el formulario de Evaluación pre-llenado.
                // Al guardar se completa la consulta origen (citaOrigenId) y se crea la cita.
                prefillEval = PrefillCita(
                    tipo = "Evaluación",
                    pacienteId = cita.pacienteId,
                    pacienteNombre = cita.pacienteNombre,
                    fecha = fecha, hora = hora,
                    terapeutaId = cita.terapeutaId,
                    citaOrigenId = cita.id,
                )
                pasarEval = null
            },
        )
    }

    // Popup de resumen clínico (tocar el nombre en una tarjeta). "Ver ficha" carga el
    // paciente por id y abre la ficha completa como overlay (misma pantalla que la web).
    resumenPacienteId?.let { pid ->
        ResumenClinicoSheet(
            pacienteId = pid,
            onCerrar = { resumenPacienteId = null },
            onVerFicha = {
                resumenPacienteId = null
                cargandoFicha = true
                scope.launch {
                    fichaPaciente = PacientesRepo.porId(pid)
                    cargandoFicha = false
                }
            },
        )
    }
    if (cargandoFicha) {
        Box(Modifier.fillMaxSize().background(c.fondo.copy(alpha = 0.6f)), Alignment.Center) {
            CircularProgressIndicator(color = c.navy)
        }
    }
    fichaPaciente?.let { pac ->
        Box(Modifier.fillMaxSize().background(c.fondo)) {
            PantallaFichaPaciente(ctx = ctx, pacienteInicial = pac, onCerrar = { fichaPaciente = null })
        }
    }
}

/** Aviso de citas sin profesional asignado (origen web). Tocar una abre el editor para asignar. */
@Composable
private fun BannerSinProfesional(citas: List<CitaStaff>, onAsignar: (CitaStaff) -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Sania.dim.lg, vertical = Sania.dim.sm)
            .clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.pendBg)
            .padding(Sania.dim.md),
    ) {
        Text("⚠ ${citas.size} cita${if (citas.size == 1) "" else "s"} sin profesional asignado",
            color = c.pend, fontSize = Sania.txt.pequeno, fontWeight = FontWeight.Bold)
        Text("No aparecen en ninguna agenda. Asígnales un profesional.",
            color = c.pend, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
        citas.forEach { cita ->
            Row(
                Modifier.fillMaxWidth().clickable { onAsignar(cita) }.padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${hora12(cita.hora)} · ${cita.pacienteNombre ?: "Paciente"} · ${cita.tipo ?: ""}",
                    color = c.texto, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.pend)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) { Text("Asignar", color = c.sobreNavy, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun BotonPagina(texto: String, habilitado: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(if (habilitado) c.navy else c.chipBg)
            .then(if (habilitado) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(texto, color = if (habilitado) c.sobreNavy else c.textoSuave,
            fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
/**
 * Construye el prefill para REPETIR una cita: mismo paciente, profesional, tipo,
 * tratamiento y especialidad, con la fecha propuesta a +7 días de la original (o de
 * hoy si la original ya pasó). El staff solo confirma/ajusta y guarda: 1 toque para
 * citar la próxima sesión/control.
 */
private fun repetirDesde(cita: pe.saniape.app.data.staff.CitaStaff): pe.saniape.app.ui.clinica.PrefillCita {
    val hoy = kotlinx.datetime.Clock.System.now()
        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
    val base = runCatching { kotlinx.datetime.LocalDate.parse(cita.fecha) }.getOrDefault(hoy)
    val desde = if (base < hoy) hoy else base
    val proxima = desde.plus(7, kotlinx.datetime.DateTimeUnit.DAY)
    return pe.saniape.app.ui.clinica.PrefillCita(
        tipo = cita.tipo ?: "Sesión",
        pacienteId = cita.pacienteId,
        pacienteNombre = cita.pacienteNombre,
        fecha = proxima.toString(),
        hora = cita.hora.takeIf { it.isNotBlank() } ?: "09:00",
        terapeutaId = cita.terapeutaId,
        especialidadId = cita.especialidadId,
        tratamientoId = cita.tratamientoId,
    )
}
