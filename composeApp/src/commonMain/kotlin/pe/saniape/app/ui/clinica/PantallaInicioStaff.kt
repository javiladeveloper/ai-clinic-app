package pe.saniape.app.ui.clinica

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import pe.saniape.app.ui.theme.Movim
import pe.saniape.app.ui.theme.tarjeta
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.clickable
import pe.saniape.app.ui.theme.tocable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import pe.saniape.app.data.staff.CitaAgenda
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.DashboardRepo
import pe.saniape.app.data.staff.StatsDashboard
import pe.saniape.app.ui.hora12
import pe.saniape.app.ui.theme.Sania
import pe.saniape.app.ui.nombreDeSaludo

/**
 * Inicio del staff: saludo con fecha, PRÓXIMA CITA destacada, stats, avisos tocables
 * (sin confirmar / sin profesional → Agenda), accesos rápidos y la agenda de hoy.
 * Los stats vienen ya filtrados por miTerapeutaId desde /api/dashboard/stats.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PantallaInicioStaff(
    ctx: ContextoStaff,
    onIrAgenda: () -> Unit = {},
    onIrPacientes: () -> Unit = {},
    onAbrirCaja: (() -> Unit)? = null,
    onBuscar: (() -> Unit)? = null,
) {
    val c = Sania.colors
    // Muestra al instante lo último cargado (sobrevive al cambio de tab) y refresca en
    // segundo plano. Spinner de pantalla completa SOLO la primera vez (sin caché).
    var stats by remember { mutableStateOf(DashboardRepo.cache) }
    var cargando by remember { mutableStateOf(DashboardRepo.cache == null) }
    // 🔔 Notificaciones in-app (misma tabla que la campanita web). No es push (eso será FCM).
    var notifs by remember { mutableStateOf<List<pe.saniape.app.data.staff.NotificacionClinica>>(emptyList()) }
    var verNotifs by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Metas de comisión. Se autooculta si no tiene ninguna: solo comisiona quien
    // la clínica configuró, y a los demás no debe salirles un hueco vacío.
    var metas by remember { mutableStateOf<List<pe.saniape.app.data.staff.AvanceMeta>>(emptyList()) }

    // La clave es Reanudacion.contador, no Unit: con Unit esto corría UNA vez por
    // montaje, así que al volver a la app tras agendar una cita la agenda seguía
    // mostrando lo de antes y había que refrescar a mano. Ahora cada vuelta al
    // frente vuelve a consultar.
    LaunchedEffect(pe.saniape.app.ui.Reanudacion.contador) {
        // Refresca aunque haya caché (para traer lo nuevo), pero sin borrar la pantalla:
        // si ya hay stats visibles, el spinner no aparece (cargando ya es false).
        try { DashboardRepo.stats()?.let { stats = it } } catch (_: Exception) {}
        cargando = false
        notifs = runCatching { pe.saniape.app.data.staff.NotificacionesRepo.listar() }.getOrDefault(emptyList())
        metas = pe.saniape.app.data.staff.MetasRepo.mias()
    }

    // Realtime: "Agenda de hoy" se actualiza sola cuando alguien agenda o cambia
    // una cita desde el panel. La pantalla de Agenda ya estaba suscrita, pero el
    // Inicio NO — y es la primera que se ve al abrir la app, así que era justo
    // donde se notaba: llegaba el aviso al celular, se abría Sania y la cita
    // nueva no estaba hasta refrescar a mano (2026-08-11).
    LaunchedEffect(Unit) {
        pe.saniape.app.data.staff.RealtimeAgenda.suscribir(this) {
            // Solo las stats: recargar las notificaciones aquí las marcaría como
            // vistas sin que el usuario las haya visto.
            scope.launch {
                runCatching { DashboardRepo.stats() }.getOrNull()?.let { stats = it }
            }
        }
    }
    val noLeidas = notifs.count { !it.leida }

    // Sin el título: "Lic. Fisio Prueba" saludaba "Hola, Lic. 👋", que no es el
    // nombre de nadie. Pasa con casi todos los profesionales.
    val primerNombre = nombreDeSaludo(ctx.nombre)

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Barra de marca white-label compartida (logo de la clínica + nombre + campana).
            // SIN campana en el celular: para eso está la barra de notificaciones
            // del propio Android. Una campana dentro de la app obliga a abrirla
            // para enterarse, que es justo lo que un aviso debe evitar — y hacía
            // parecer que "las notificaciones llegan" cuando al celular no
            // llegaba nada (decisión del dueño 2026-08-11).
            HeaderMarcaClinica(
                ctx = ctx,
                onBuscar = onBuscar,
                onCaja = onAbrirCaja,
            )

            if (cargando) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = c.navy) }
                return@Column
            }
            val s = stats

            // Pull-to-refresh: deslizar hacia abajo recarga stats + notificaciones.
            var refrescando by remember { mutableStateOf(false) }
            androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                isRefreshing = refrescando,
                onRefresh = {
                    refrescando = true
                    scope.launch {
                        runCatching { DashboardRepo.stats() }.onSuccess { stats = it }
                        notifs = runCatching { pe.saniape.app.data.staff.NotificacionesRepo.listar() }.getOrDefault(notifs)
                        metas = pe.saniape.app.data.staff.MetasRepo.mias()
                        refrescando = false
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = Sania.dim.lg),
                verticalArrangement = Arrangement.spacedBy(Sania.dim.md),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Sania.dim.lg),
            ) {
                item {
                    Column {
                        Text(
                            if (primerNombre != null) "Hola, $primerNombre 👋" else "Hola 👋",
                            color = c.texto, fontSize = Sania.txt.titulo, fontWeight = FontWeight.Bold,
                        )
                        Text(fechaHumanaHoy(), color = c.textoSuave, fontSize = Sania.txt.pequeno)
                    }
                }

                // ── MI META ──
                // Arriba del todo, debajo del saludo: es lo que el profesional
                // viene a mirar cada mañana, igual que "citas hoy". Escondida en
                // un menú nadie la abre, y una meta que no se ve no motiva.
                //
                // Solo el avance, NUNCA el monto del bono: el endpoint ni
                // siquiera lo manda cuando pregunta un profesional (decisión del
                // dueño, 2026-08-14).
                items(metas) { meta ->
                    TarjetaMeta(meta)
                }

                // Avisos del celular apagados. Android deja de preguntar cuando ya
                // se negó una vez, así que sin esto la app se queda muda y no hay
                // NADA que lo explique: se ve como "las notificaciones no llegan"
                // (reporte del dueño 2026-08-11). Tocarlo abre los Ajustes.
                if (pe.saniape.app.ui.EstadoAvisos.apagados) {
                    item {
                        AvisoInicio(
                            "🔕", "Los avisos están apagados",
                            "No recibirás notificaciones de citas. Toca para activarlos",
                            c.error, c.errorBg,
                        ) { pe.saniape.app.ui.abrirAjustesDeNotificaciones() }
                    }
                }

                if (s != null) {
                    // ── PRÓXIMA CITA destacada (la primera de hoy que aún no pasa) ──
                    val proxima = proximaCita(s.agendaHoy)
                    if (proxima != null) {
                        item {
                            Row(
                                Modifier.fillMaxWidth()
                                    .shadow(6.dp, RoundedCornerShape(Sania.shape.md.dp))
                                    .clip(RoundedCornerShape(Sania.shape.md.dp))
                                    .background(c.navy).tocable { onIrAgenda() }
                                    .padding(Sania.dim.lg),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("SIGUIENTE PACIENTE", color = c.sobreNavy.copy(alpha = 0.7f),
                                        fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                                    Spacer(Modifier.height(2.dp))
                                    Text(proxima.paciente, color = c.sobreNavy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text(proxima.procedimiento, color = c.sobreNavy.copy(alpha = 0.8f), fontSize = 12.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(hora12(proxima.hora), color = c.sobreNavy, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text("→ ver agenda", color = c.sobreNavy.copy(alpha = 0.7f), fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    // ── Avisos accionables (tocarlos lleva a la Agenda) ──
                    if (!s.esProfesional && (s.citasSinConfirmar > 0 || s.citasSinProfesional > 0)) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (s.citasSinConfirmar > 0) {
                                    AvisoInicio("❓", "${s.citasSinConfirmar} cita(s) sin confirmar",
                                        "Confírmalas o el paciente puede no llegar", c.pend, c.pendBg) { onIrAgenda() }
                                }
                                if (s.citasSinProfesional > 0) {
                                    AvisoInicio("🧑‍⚕️", "${s.citasSinProfesional} cita(s) sin profesional",
                                        "Asigna quién atenderá", c.error, c.errorBg) { onIrAgenda() }
                                }
                            }
                        }
                    }

                    // ── Stats: profesional (2×2) vs gestor (KPIs) ──
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(Sania.dim.md)) {
                            // Cada cifra lleva a la lista que hay detrás: leer
                            // "3 citas hoy" y tener que bajar a buscar el acceso
                            // es un paso de más en lo que más se mira del día.
                            if (s.esProfesional) {
                                Row(horizontalArrangement = Arrangement.spacedBy(Sania.dim.md)) {
                                    StatCard("Citas hoy", s.citasHoy.toString(), "📅", Modifier.weight(1f), onIrAgenda)
                                    StatCard("Pendientes", s.misCitasPendientes.toString(), "⏳", Modifier.weight(1f), onIrAgenda)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(Sania.dim.md)) {
                                    StatCard("Mis pacientes", s.totalPacientes.toString(), "👥", Modifier.weight(1f), onIrPacientes)
                                    // "Sesiones" es un acumulado histórico, no una
                                    // lista: no hay pantalla a la que llevar, así
                                    // que se queda sin toque en vez de fingir uno.
                                    StatCard("Sesiones", s.misSesionesCompletadas.toString(), "✅", Modifier.weight(1f))
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(Sania.dim.md)) {
                                    StatCard("Total pacientes", s.totalPacientes.toString(), "👥", Modifier.weight(1f), onIrPacientes)
                                    StatCard("Citas hoy", s.citasHoy.toString(), "📅", Modifier.weight(1f), onIrAgenda)
                                }
                            }
                        }
                    }

                    // ── Accesos rápidos (lo que se hace 20 veces al día) ──
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(Sania.dim.md)) {
                            AccesoRapido("📅", "Agenda", Modifier.weight(1f)) { onIrAgenda() }
                            AccesoRapido("👤", "Pacientes", Modifier.weight(1f)) { onIrPacientes() }
                            if (onAbrirCaja != null) {
                                AccesoRapido("💰", "Caja", Modifier.weight(1f)) { onAbrirCaja() }
                            }
                        }
                    }

                    // ── Resumen de la semana (profesional): motiva y da sentido de avance ──
                    if (s.esProfesional && s.misSesionesSemana > 0) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
                                    .background(c.chipBg).padding(Sania.dim.lg),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("🏆", fontSize = 22.sp)
                                Spacer(Modifier.width(Sania.dim.md))
                                Column {
                                    Text("Tu semana", color = c.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${s.misSesionesSemana} sesión${if (s.misSesionesSemana == 1) "" else "es"} completada${if (s.misSesionesSemana == 1) "" else "s"} en los últimos 7 días",
                                        color = c.texto, fontSize = Sania.txt.pequeno, fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }

                    // ── Agenda de hoy ──
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(top = Sania.dim.sm),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("AGENDA DE HOY", color = c.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
                            Text("Ver todo →", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.tocable { onIrAgenda() })
                        }
                    }
                    if (s.agendaHoy.isEmpty()) {
                        item {
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
                                    .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
                                    .padding(Sania.dim.xl),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("🌤", fontSize = 28.sp)
                                Spacer(Modifier.height(6.dp))
                                Text("Sin citas para hoy", color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold)
                                Text("Toca Agenda para programar la semana.", color = c.textoSuave, fontSize = 12.sp)
                            }
                        }
                    } else {
                        items(s.agendaHoy) { cita -> FilaAgenda(cita, onClick = onIrAgenda) }
                    }
                } else {
                    item { Text("No se pudieron cargar tus datos.", color = c.error, fontSize = Sania.txt.cuerpo) }
                }

                item { Spacer(Modifier.height(Sania.dim.xxl)) }
            }
            } // cierre PullToRefreshBox
        }
    }

    // 🔔 Panel de notificaciones (in-app). Al abrirlo se marcan leídas (como la campanita web).
    if (verNotifs) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { verNotifs = false },
            title = { Text("🔔 Notificaciones", fontWeight = FontWeight.Bold) },
            text = {
                if (notifs.isEmpty()) {
                    Text("Sin notificaciones por ahora.", color = c.textoSuave, fontSize = 13.sp)
                } else {
                    LazyColumn(
                        Modifier.fillMaxWidth().height(380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(notifs) { n ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                                    .background(c.chipBg).padding(10.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(n.icono ?: "🔔", fontSize = 16.sp)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(n.titulo, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    n.cuerpo?.takeIf { it.isNotBlank() }?.let {
                                        Text(it, color = c.textoSuave, fontSize = 12.sp)
                                    }
                                    n.createdAt?.take(16)?.replace("T", " · ")?.let {
                                        Text(it, color = c.textoSuave, fontSize = 10.sp,
                                            modifier = Modifier.padding(top = 2.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { verNotifs = false }) {
                    Text("Cerrar", color = c.navy, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = c.superficie,
        )
    }
}

/** "jueves 3 de julio" — la fecha de hoy en humano. */
private fun fechaHumanaHoy(): String {
    val d = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val dias = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
    val meses = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
    val dia = dias[d.dayOfWeek.ordinal]
    return "${dia.replaceFirstChar { it.uppercase() }} ${d.dayOfMonth} de ${meses[d.monthNumber - 1]}"
}

/** La primera cita de hoy que aún no pasó (y no está cancelada). */
private fun proximaCita(agenda: List<CitaAgenda>): CitaAgenda? {
    val ahora = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val horaAhora = "${ahora.hour.toString().padStart(2, '0')}:${ahora.minute.toString().padStart(2, '0')}"
    return agenda
        .filter { it.estado != "Cancelada" && it.estado != "Completada" }
        .sortedBy { it.hora }
        .firstOrNull { it.hora.take(5) >= horaAhora }
}

@Composable
private fun AvisoInicio(
    icono: String, titulo: String, detalle: String,
    fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(bg)
            .border(1.dp, fg.copy(alpha = 0.4f), RoundedCornerShape(Sania.shape.sm.dp))
            .tocable { onClick() }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icono, fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(detalle, color = c.textoSuave, fontSize = 11.sp)
        }
        Text("→", color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AccesoRapido(emoji: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Sania.colors
    Column(
        modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .tocable { onClick() }.padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.height(3.dp))
        Text(label, color = c.texto, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatCard(
    label: String,
    valor: String,
    emoji: String,
    modifier: Modifier = Modifier,
    // Una cifra que interesa lleva a la lista que hay detrás: ver "3 citas hoy"
    // y no poder tocarlo obliga a bajar a buscar el acceso. Sin onClick, la
    // tarjeta se queda como estaba (no todas las cifras tienen a dónde llevar).
    onClick: (() -> Unit)? = null,
) {
    val c = Sania.colors
    Column(
        modifier.tarjeta()
            .let { if (onClick != null) it.tocable { onClick() } else it }
            .padding(Sania.dim.lg),
    ) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.height(6.dp))
        // El número sube desde 0 al aparecer. No es adorno: hace mirar la cifra,
        // que es justo lo que uno viene a ver al abrir la app por la mañana.
        // Solo si es un número; "S/ 1,200" o "—" se muestran tal cual.
        val destino = valor.toIntOrNull()
        if (destino != null) {
            val animado by animateIntAsState(
                targetValue = destino,
                animationSpec = tween(Movim.largo, easing = Movim.salida),
                label = "stat",
            )
            Text(animado.toString(), color = c.navy, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        } else {
            Text(valor, color = c.navy, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Text(label, color = c.textoSuave, fontSize = 12.sp)
    }
}

@Composable
private fun FilaAgenda(cita: CitaAgenda, onClick: () -> Unit = {}) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().tarjeta(Sania.shape.sm)
            .tocable { onClick() }.padding(Sania.dim.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(hora12(cita.hora), color = c.navy, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold)
        Spacer(Modifier.padding(horizontal = 6.dp))
        Column(Modifier.weight(1f)) {
            Text(cita.paciente, color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.SemiBold)
            Text(cita.procedimiento, color = c.textoSuave, fontSize = 12.sp)
        }
        BadgeEstadoCita(cita.estado)
    }
}

@Composable
fun BadgeEstadoCita(estado: String) {
    val c = Sania.colors
    val (fg, bg) = when (estado) {
        "Confirmada", "Completada" -> c.ok to c.okBg
        "Pendiente" -> c.pend to c.pendBg
        "Cancelada" -> c.error to c.errorBg
        else -> c.navy to c.chipBg
    }
    Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg)
        .padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(estado, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * La meta del profesional: cuánto lleva y cuánto le falta.
 *
 * Sin montos a propósito — él ve su avance, no el premio. La barra usa el color
 * de la clínica y se pone verde al cumplir, que es el momento que hay que
 * celebrar.
 */
@Composable
private fun TarjetaMeta(meta: pe.saniape.app.data.staff.AvanceMeta) {
    val c = Sania.colors
    val progreso = (meta.progreso.coerceIn(0, 100)) / 100f
    // La barra crece sola al entrar: hace mirar el número, que es de lo que va.
    val animado by androidx.compose.animation.core.animateFloatAsState(
        targetValue = progreso,
        animationSpec = tween(Movim.largo, easing = Movim.salida),
        label = "meta",
    )
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(if (meta.cumplida) c.okBg else c.superficie)
            .border(
                1.dp,
                if (meta.cumplida) c.ok else c.borde,
                RoundedCornerShape(Sania.shape.md.dp),
            )
            .padding(Sania.dim.lg),
    ) {
        Text(
            (meta.etiqueta ?: "MI META DEL MES").uppercase(),
            color = c.textoSuave, fontSize = 9.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            meta.texto,
            color = if (meta.cumplida) c.ok else c.navy,
            fontSize = 26.sp, fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        // Barra de progreso
        Box(
            Modifier.fillMaxWidth().height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(c.borde),
        ) {
            Box(
                Modifier.fillMaxWidth(animado.coerceAtLeast(0.02f)).height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (meta.cumplida) c.ok else c.navy),
            )
        }
        if (!meta.cumplida && meta.faltan > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                if (meta.faltan == 1) "Te falta 1 para llegar" else "Te faltan ${meta.faltan} para llegar",
                color = c.textoSuave, fontSize = Sania.txt.pequeno,
            )
        }
    }
}
