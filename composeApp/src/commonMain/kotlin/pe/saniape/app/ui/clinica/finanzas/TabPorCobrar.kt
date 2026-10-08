package pe.saniape.app.ui.clinica.finanzas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.AgendaRepo
import pe.saniape.app.data.staff.FinanzasRepo
import pe.saniape.app.data.staff.MetodoPagoPreferido
import pe.saniape.app.data.staff.PendienteCobro
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.soles
import pe.saniape.app.ui.CargandoLista
import pe.saniape.app.ui.Gestion
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.EstadoVacio
import pe.saniape.app.ui.clinica.pacientes.ChipsMetodoPago
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.rememberMetodoPagoInicial
import pe.saniape.app.ui.conIndicador
import pe.saniape.app.ui.theme.Sania

/**
 * Pestaña "Por cobrar" (gemela de components/finanzas/PorCobrar.tsx): consultas y
 * evaluaciones ATENDIDAS que nadie cobró (últimos 60 días). Atender y cobrar son
 * actos distintos: el profesional atiende, recepción cobra. El cobro va por
 * /api/staff/cita/cobrar (el mismo de la agenda), con un método para toda la lista.
 * Solo con permiso de pagos.
 */
@Composable
internal fun TabPorCobrar() {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var filas by remember { mutableStateOf<List<PendienteCobro>?>(null) }
    var fallo by remember { mutableStateOf(false) }
    var recarga by remember { mutableIntStateOf(0) }
    var cobrando by remember { mutableStateOf<String?>(null) }
    var metodo by rememberMetodoPagoInicial(null)

    LaunchedEffect(recarga) {
        runCatching { FinanzasRepo.porCobrar(hoyClinicaIso()) }
            .onSuccess { filas = it; fallo = false }
            .onFailure { if (it is kotlin.coroutines.cancellation.CancellationException) throw it; fallo = true }
    }

    val lista = filas
    when {
        lista == null && fallo -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No se pudo cargar lo pendiente. Revisa tu conexión.", color = c.textoSuave, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                BotonFin("Reintentar") { recarga++ }
            }
        }
        lista == null -> CargandoLista(filas = 4)
        lista.isEmpty() -> Box(Modifier.fillMaxSize().padding(Sania.dim.lg)) {
            EstadoVacio("✅", "Nada por cobrar", "Todas las consultas y evaluaciones atendidas de los últimos 60 días ya están cobradas.")
        }
        else -> LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = Sania.dim.lg),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            item {
                TarjetaFin(borde = c.pend) {
                    RotuloFin("💰 Por cobrar", color = c.pend)
                    Text("${lista.size} atenci${if (lista.size == 1) "ón" else "ones"} sin cobrar · ${soles(lista.sumOf { it.costo })}",
                        color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
                    EtqForm("Método")
                    ChipsMetodoPago(metodo) { metodo = it }
                }
            }
            items(lista, key = { it.citaId }) { p ->
                TarjetaFin {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.paciente, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(listOfNotNull(fechaCortaFin(p.fecha), p.tipo, p.hora, p.profesional).joinToString(" · "),
                                color = c.textoSuave, fontSize = 11.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(soles(p.costo), color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            BotonFin(if (cobrando == p.citaId) "Cobrando…" else "💵 Cobrar", color = c.ok, habilitado = cobrando == null) {
                                cobrando = p.citaId
                                scope.launch {
                                    val r = conIndicador(Gestion.GUARDANDO) { AgendaRepo.cobrarCita(p.citaId, metodo, "cobrar", null) }
                                    cobrando = null
                                    when {
                                        r.encolada -> { Toaster.info("Sin señal: el cobro se enviará al volver la conexión"); filas = lista.filter { it.citaId != p.citaId } }
                                        r.registrada -> {
                                            MetodoPagoPreferido.recordar(p.pacienteId, metodo)
                                            Toaster.exito(if (r.yaEstaba) "Ya estaba cobrada — ${p.paciente}" else "Cobrado ${soles(p.costo)} — ${p.paciente}")
                                            recarga++
                                        }
                                        else -> Toaster.error(r.rechazo?.error ?: "No se pudo cobrar")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(Sania.dim.xxl)) }
        }
    }
}
