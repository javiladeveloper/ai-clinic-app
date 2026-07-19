package pe.saniape.app.ui.clinica.pacientes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.PacienteStaff
import pe.saniape.app.ui.theme.EstadosColor
import pe.saniape.app.ui.theme.Sania

private val ESTADOS = listOf("Nuevo", "Consultado", "Evaluado", "En tratamiento", "Alta")

/**
 * Lista de pacientes del staff. Búsqueda + filtro de estado, semáforo (flag),
 * progreso de tratamiento y contacto (DNI/teléfono solo si es gestor). Tocar abre
 * la ficha. Respeta scope del profesional vinculado.
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PantallaPacientes(
    ctx: ContextoStaff,
    onAbrirFicha: (PacienteStaff) -> Unit,
    // Cambia este valor para forzar una recarga de la lista (ej. al volver de la ficha,
    // para que el progreso de sesiones se actualice sin pull-to-refresh).
    recargarTick: Int = 0,
) {
    val c = Sania.colors
    val vm: PacientesViewModel = viewModel(key = ctx.clinicaId) { PacientesViewModel(ctx) }
    var nuevoAbierto by remember { mutableStateOf(false) }

    // Recargar cuando el contenedor pide (recargarTick > 0 = venimos de la ficha).
    androidx.compose.runtime.LaunchedEffect(recargarTick) {
        if (recargarTick > 0) vm.cargar()
    }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Barra de marca (+ alta de paciente para quien gestiona pacientes)
            Row(
                Modifier.fillMaxWidth().background(c.navyDark)
                    .padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pe.saniape.app.ui.clinica.LogoMarcaChica(ctx)
                Spacer(Modifier.width(10.dp))
                Text("Pacientes", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                if (ctx.puede("pacientes")) {
                    Box(
                        Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.teal)
                            .clickable { nuevoAbierto = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text("+ Nuevo", color = c.sobreNavy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Búsqueda + filtros de estado
            Column(Modifier.fillMaxWidth().padding(horizontal = Sania.dim.lg, vertical = Sania.dim.sm)) {
                OutlinedTextField(
                    value = vm.busqueda, onValueChange = { vm.cambiarBusqueda(it) },
                    placeholder = { Text("🔍 Buscar paciente…", color = c.textoSuave) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Sania.dim.sm))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChipFiltro("Todos", vm.filtroEstado == null) { vm.cambiarFiltroEstado(null) }
                    ESTADOS.forEach { e ->
                        ChipFiltro(e, vm.filtroEstado == e) {
                            vm.cambiarFiltroEstado(if (vm.filtroEstado == e) null else e)
                        }
                    }
                }
            }

            // Pull-to-refresh: deslizar hacia abajo recarga la lista.
            androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                isRefreshing = vm.cargando,
                onRefresh = { vm.cargar() },
                modifier = Modifier.fillMaxSize(),
            ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Sania.dim.xl)) {
                // Aviso sutil al refrescar/volver de ficha: mantiene la lista visible.
                if (vm.recargando) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = Sania.dim.lg, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
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
                    // Falló la carga (red/permiso): NO es lo mismo que "no hay pacientes".
                    vm.cargaFallo -> item {
                        Column(
                            Modifier.fillMaxWidth().padding(Sania.dim.xxl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("No se pudo cargar la lista.", color = c.textoSuave, fontSize = Sania.txt.cuerpo)
                            Spacer(Modifier.height(Sania.dim.md))
                            Box(
                                Modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.navy)
                                    .clickable { vm.cargar() }.padding(horizontal = 20.dp, vertical = 10.dp),
                            ) { Text("Reintentar", color = c.sobreNavy, fontWeight = FontWeight.Bold) }
                        }
                    }
                    vm.filtrados.isEmpty() -> item {
                        Box(Modifier.fillMaxWidth().padding(Sania.dim.lg)) {
                            if (vm.pacientes.isEmpty()) {
                                pe.saniape.app.ui.clinica.EstadoVacio(
                                    emoji = "👥",
                                    titulo = "Aún no hay pacientes",
                                    subtitulo = if (ctx.puede("pacientes")) "Registra el primero para empezar." else null,
                                    textoAccion = if (ctx.puede("pacientes")) "+ Registrar paciente" else null,
                                    onAccion = { nuevoAbierto = true },
                                )
                            } else {
                                pe.saniape.app.ui.clinica.EstadoVacio(
                                    emoji = "🔍",
                                    titulo = "Sin resultados",
                                    subtitulo = "No hay pacientes con esos filtros.",
                                )
                            }
                        }
                    }
                    else -> items(vm.filtrados, key = { it.id }) { p ->
                        Box(Modifier.padding(horizontal = Sania.dim.lg, vertical = Sania.dim.sm / 2)) {
                            TarjetaPaciente(p, verContacto = vm.verContacto) { onAbrirFicha(p) }
                        }
                    }
                }
            }
            } // cierre PullToRefreshBox
        }
    }

    // Alta de paciente (esencial de recepción): DNI con búsqueda + datos mínimos.
    // Al crear (o elegir un duplicado existente) se abre su ficha y se recarga la lista.
    if (nuevoAbierto) {
        ModalNuevoPaciente(
            onCancelar = { nuevoAbierto = false },
            onCreado = { p ->
                nuevoAbierto = false
                vm.cargar()
                // Si se creó SIN señal, su id es temporal: la ficha no podría leer
                // nada del servidor con ese id. Se queda en la lista (ya aparece) y
                // el aviso de "se registrará al volver la señal" ya se mostró.
                if (!pe.saniape.app.data.offline.esTemporal(p.id)) onAbrirFicha(p)
            },
        )
    }
}

@Composable
private fun TarjetaPaciente(p: PacienteStaff, verContacto: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    val flagColor = when (p.flag) {
        "rojo" -> c.error
        "amarillo" -> c.pend
        else -> c.ok
    }
    val estado = EstadosColor.paciente(p.estado)
    val activo = p.tratamientosActivos.firstOrNull()

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .clickable { onClick() }.padding(Sania.dim.tarjeta),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Semáforo (flag)
        Box(Modifier.size(10.dp).clip(CircleShape).background(flagColor))
        Spacer(Modifier.width(Sania.dim.md))

        Column(Modifier.weight(1f)) {
            Text(p.nombre, color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.SemiBold)
            // Línea: motivo / procedimiento del tratamiento activo
            val sub = p.diagnostico ?: activo?.procedimiento
            if (!sub.isNullOrBlank()) {
                Text(sub, color = c.textoSuave, fontSize = 12.sp, maxLines = 1)
            }
            // Contacto (solo gestor): DNI · teléfono
            if (verContacto) {
                val contacto = listOfNotNull(
                    p.dni?.let { "DNI $it" },
                    p.telefono,
                ).joinToString(" · ")
                if (contacto.isNotBlank()) {
                    Text(contacto, color = c.textoSuave, fontSize = 11.sp)
                }
            }
            // Progreso del tratamiento activo (solo especialidades por sesiones; las
            // Consultas no tienen contador, aunque tengan datos de paquete heredados).
            activo?.takeIf { it.usaSesiones && it.totalSesiones > 0 }?.let { t ->
                Spacer(Modifier.height(4.dp))
                BarraProgreso(t.sesionesCompletadas, t.totalSesiones)
            }
        }

        Spacer(Modifier.width(Sania.dim.sm))
        Column(horizontalAlignment = Alignment.End) {
            Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(estado.bg)
                .padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(p.estado ?: "—", color = estado.fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            if (p.tratamientosActivos.isNotEmpty()) {
                Text("${p.tratamientosActivos.size} activo${if (p.tratamientosActivos.size == 1) "" else "s"}",
                    color = c.textoSuave, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun BarraProgreso(hechas: Int, total: Int) {
    val c = Sania.colors
    val frac = if (total > 0) (hechas.toFloat() / total).coerceIn(0f, 1f) else 0f
    Row(verticalAlignment = Alignment.CenterVertically) {
        pe.saniape.app.ui.clinica.BarraProgreso(frac, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(Sania.dim.sm))
        Text("$hechas/$total", color = c.textoSuave, fontSize = Sania.txt.mini)
    }
}

@Composable
private fun ChipFiltro(texto: String, activo: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
            .background(if (activo) c.navy else c.superficie)
            .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(texto, color = if (activo) c.sobreNavy else c.texto, fontSize = 11.sp,
            fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal)
    }
}