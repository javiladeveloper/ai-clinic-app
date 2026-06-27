package pe.saniape.app.ui.clinica

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import pe.saniape.app.ui.clinica.agenda.AccionCita
import pe.saniape.app.ui.clinica.agenda.AgendaViewModel
import pe.saniape.app.ui.clinica.agenda.componentes.AccionTarjeta
import pe.saniape.app.ui.clinica.agenda.componentes.TarjetaCita
import pe.saniape.app.ui.clinica.agenda.componentes.TiraDias
import pe.saniape.app.ui.clinica.agenda.modales.ConfirmacionAccion
import pe.saniape.app.ui.clinica.agenda.modales.ModalCompletar
import pe.saniape.app.ui.clinica.agenda.modales.ModalEditarCita
import pe.saniape.app.ui.clinica.agenda.modales.ModalPasarEvaluacion
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

    // Sub-pantalla: crear cita
    var creandoCita by remember { mutableStateOf(false) }
    // Modales (la cita objetivo, o null)
    var completar by remember { mutableStateOf<CitaStaff?>(null) }
    var confirmar by remember { mutableStateOf<Pair<CitaStaff, AccionCita>?>(null) }
    var editar by remember { mutableStateOf<CitaStaff?>(null) }
    var pasarEval by remember { mutableStateOf<CitaStaff?>(null) }

    if (creandoCita) {
        PantallaCrearCita(
            ctx = ctx, fechaInicial = vm.fechaSel,
            onListo = { creandoCita = false; vm.refrescar() },
            onCancelar = { creandoCita = false },
        )
        return
    }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Barra con "+ Nueva"
            Row(
                Modifier.fillMaxWidth().background(c.navyDark)
                    .padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Agenda", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
                Box(
                    Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                        .background(c.sobreNavy.copy(alpha = 0.15f))
                        .clickable { creandoCita = true }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) { Text("+ Nueva", color = c.sobreNavy, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }

            TiraDias(hoy = vm.hoy, seleccionado = vm.fechaSel, onSeleccionar = { vm.seleccionarDia(it) })

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
                            onAgendarDerivacion = { creandoCita = true },
                            onMarcarDerivacion = { vm.marcarDerivacion(it.id) },
                        )
                    }
                }

                when {
                    vm.cargando -> item {
                        Box(Modifier.fillMaxWidth().padding(Sania.dim.xxl), Alignment.Center) {
                            CircularProgressIndicator(color = c.navy)
                        }
                    }
                    vm.citas.isEmpty() -> item {
                        Box(Modifier.fillMaxWidth().padding(Sania.dim.xxl), Alignment.Center) {
                            Text("No hay citas para este día.", color = c.textoSuave, fontSize = Sania.txt.cuerpo)
                        }
                    }
                    else -> items(vm.citas, key = { it.id }) { cita ->
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
                                    }
                                },
                            )
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
                vm.pasarAEvaluacion(cita, fecha, hora, costo = 0.0, notas = null) { pasarEval = null }
            },
        )
    }
}