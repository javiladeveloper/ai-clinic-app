package pe.saniape.app.ui.clinica

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import pe.saniape.app.ui.CitaPendienteDeAbrir
import pe.saniape.app.ui.theme.aparecer
import pe.saniape.app.ui.theme.desaparecer
import pe.saniape.app.ui.theme.entrarDetalle
import pe.saniape.app.ui.theme.salirDetalle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.StaffContextoRepo
import pe.saniape.app.ui.ManejarAtras
import pe.saniape.app.ui.clinica.pacientes.PantallaPacientesStaff
import pe.saniape.app.ui.theme.Sania
import pe.saniape.app.tutoriales.tourAncla
import pe.saniape.app.ui.tutoriales.CentroAyuda
import pe.saniape.app.ui.tutoriales.HostTutorial
import pe.saniape.app.ui.tutoriales.PildoraPrimeraVez
import io.github.jan.supabase.auth.auth

private enum class TabClinica(val titulo: String, val icono: ImageVector) {
    Inicio("Inicio", Icons.Filled.Home),
    Agenda("Agenda", Icons.Filled.DateRange),
    Pacientes("Pacientes", Icons.Filled.Person),
    Mas("Más", Icons.Filled.Menu),
}

/**
 * Panel de clínica (staff) con bottom tabs nativas, respetando permisos del
 * contexto resuelto por el servidor (/api/staff/contexto). NO recalcula reglas.
 */
@Composable
fun ClinicaConTabs(
    puedeIrAPortal: Boolean,
    onIrAPortal: () -> Unit,
    onCerrarSesion: () -> Unit,
) {
    val c = Sania.colors
    var cargando by remember { mutableStateOf(true) }
    var ctx by remember { mutableStateOf<ContextoStaff?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(TabClinica.Inicio) }
    // Fecha (ISO) en la que debe abrir la agenda porque el profesional tocó el
    // aviso de una cita. Se consume una sola vez: si no, cada regreso a la app
    // lo devolvería a esa fecha en vez de a hoy.
    var fechaDeAviso by remember { mutableStateOf<String?>(null) }

    // El aviso de "cita nueva" tiene que dejar al profesional EN la cita, no en
    // Inicio buscándola (reportado 2026-09-13). Llega con la app cerrada
    // (MainActivity) o abierta (onNewIntent); por eso se observa el estado.
    LaunchedEffect(CitaPendienteDeAbrir.actual) {
        if (CitaPendienteDeAbrir.actual != null) {
            fechaDeAviso = CitaPendienteDeAbrir.fechaActual
            tab = TabClinica.Agenda
            CitaPendienteDeAbrir.consumir()
        }
    }
    var intento by remember { mutableStateOf(0) }   // para "Reintentar"
    // Sub-pantallas accesibles desde "Más" (módulos sin tab propio).
    var verSesiones by remember { mutableStateOf(false) }
    var verCaja by remember { mutableStateOf(false) }
    var verFinanzas by remember { mutableStateOf(false) }
    var verEspecialidades by remember { mutableStateOf(false) }
    // Reportes es UNA puerta con tres pestañas (como la web); null = cerrado.
    var vistaReportes by remember { mutableStateOf<pe.saniape.app.ui.clinica.reportes.VistaReportes?>(null) }
    var ultimaVistaReportes by remember { mutableStateOf(pe.saniape.app.ui.clinica.reportes.VistaReportes.MesAMes) }
    val verReportes = vistaReportes != null
    // Profesionales (lista + horario) y el horario propio en solo lectura.
    var verProfesionales by remember { mutableStateOf(false) }
    var verServicios by remember { mutableStateOf(false) }
    var verEquipo by remember { mutableStateOf(false) }
    var verComisiones by remember { mutableStateOf(false) }
    var verRetencion by remember { mutableStateOf(false) }
    var verCampanias by remember { mutableStateOf(false) }
    var verActividad by remember { mutableStateOf(false) }
    var verMiPlan by remember { mutableStateOf(false) }
    var verMiCalendario by remember { mutableStateOf(false) }
    var verAjustes by remember { mutableStateOf(false) }
    // Ajustes guardó algo (marca, terminología, módulos…): al cerrarlo, por la vía que sea, se recarga el contexto.
    var recargarTrasAjustes by remember { mutableStateOf(false) }
    LaunchedEffect(verAjustes) { if (!verAjustes && recargarTrasAjustes) { recargarTrasAjustes = false; intento++ } }
    var horarioDe by remember { mutableStateOf<Pair<String, String>?>(null) }   // (terapeutaId, nombre)
    var horarioVolver by remember { mutableStateOf("← Más") }
    // Más → "🌐 Mi página" (slug leído de la clínica; null = no tiene página).
    var urlPagina by remember { mutableStateOf<String?>(null) }
    var verMiPagina by remember { mutableStateOf(false) }
    // Buscador global de paciente (desde el header) + ficha que abre.
    var verBuscador by remember { mutableStateOf(false) }
    var fichaBuscada by remember { mutableStateOf<pe.saniape.app.data.staff.PacienteStaff?>(null) }
    // La agenda tiene un flujo a pantalla completa abierto (crear cita o "▶ Atender"):
    // sin barra de tabs. Tocar un tab destruía el flujo sin su "¿salir sin guardar?".
    var pantallaCompleta by remember { mutableStateOf(false) }

    LaunchedEffect(intento) {
        cargando = true; error = null
        when (val r = StaffContextoRepo.cargar()) {
            is StaffContextoRepo.Resultado.Ok -> {
                // Multisede: resolver la sede de hoy ANTES de pintar las pantallas,
                // así la primera carga ya sale filtrada (sin cargar dos veces).
                // Sin multisede no hace nada.
                pe.saniape.app.data.staff.SedesAgendaRepo.limpiarCache()
                pe.saniape.app.data.staff.SedeActiva.iniciar(r.contexto)
                ctx = r.contexto
                // Tutoriales: solo pide el catálogo si la clínica tiene Primeros pasos
                // (las nuevas) o hay uno en pausa guardado. DALU no paga nada.
                pe.saniape.app.tutoriales.MotorTutoriales.configurar(
                    clinica = r.contexto.clinicaId,
                    usuario = pe.saniape.app.data.Supabase.client.auth.currentSessionOrNull()?.user?.id,
                    primerosPasos = r.contexto.primerosPasosActivos,
                    admin = r.contexto.esAdmin,
                )
                // Recordar la marca de la clínica activa para que la intro al REABRIR la app
                // muestre su logo (no el de Sania) antes de cargar el contexto.
                pe.saniape.app.data.Preferencias.setLogoClinica(r.contexto.logoUrl)
                pe.saniape.app.data.Preferencias.setNombreClinica(r.contexto.clinicaNombre)
            }
            is StaffContextoRepo.Resultado.NoEsClinica -> error = "Esta cuenta no es de una clínica."
            is StaffContextoRepo.Resultado.Suspendida -> error = "Tu clínica está suspendida. Contacta a Sania."
            is StaffContextoRepo.Resultado.Error -> error = r.mensaje
        }
        cargando = false
    }

    // Notificaciones REALES del celular (FCM): registrar este dispositivo para el staff
    // logueado. No-op mientras FirebaseCfg esté vacío.
    pe.saniape.app.ui.EfectoPushNativo()

    // Con un flujo a pantalla completa abierto, el "atrás" es de ese flujo (tiene
    // su propio ManejarAtras, con la confirmación de salir sin guardar).
    ManejarAtras(activo = !pantallaCompleta && (verSesiones || verCaja || verEspecialidades || verReportes || verProfesionales || verServicios || verFinanzas || verEquipo || verComisiones || verRetencion || verCampanias || verActividad || verMiPlan || verMiCalendario || verAjustes || horarioDe != null || tab != TabClinica.Inicio)) {
        when {
            verRetencion -> verRetencion = false
            verCampanias -> verCampanias = false
            verActividad -> verActividad = false
            verMiPlan -> verMiPlan = false
            verMiCalendario -> verMiCalendario = false
            verAjustes -> verAjustes = false
            verServicios -> verServicios = false
            verEquipo -> verEquipo = false
            verComisiones -> verComisiones = false
            horarioDe != null -> horarioDe = null
            verProfesionales -> verProfesionales = false
            verSesiones -> verSesiones = false
            verCaja -> verCaja = false
            verFinanzas -> verFinanzas = false
            verEspecialidades -> verEspecialidades = false
            verReportes -> vistaReportes = null
            else -> tab = TabClinica.Inicio
        }
    }

    if (cargando) {
        Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = c.navy) }
        }
        return
    }
    val contexto = ctx
    if (contexto == null) {
        Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().padding(Sania.dim.xxl), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠", fontSize = 40.sp)
                    Text(error ?: "No se pudo cargar tu clínica.", color = c.error,
                        fontSize = Sania.txt.cuerpo, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = Sania.dim.lg))
                    Box(
                        Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(Sania.shape.md.dp))
                            .background(c.navy)
                            .clickable { intento++ }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                    ) { Text("Reintentar", color = c.sobreNavy, fontWeight = FontWeight.Bold) }
                }
            }
        }
        return
    }

    // La clínica no terminó el asistente de inicio (onboarding v2): el Admin lo
    // termina en la web; el resto espera (como la web). Las de antes → siempre true.
    if (!contexto.onboardingCompleto) {
        PantallaOnboardingPendiente(
            esAdmin = contexto.esAdmin,
            clinicaNombre = contexto.clinicaNombre,
            onReintentar = { intento++ },
            onCerrarSesion = {
                StaffContextoRepo.limpiar()
                pe.saniape.app.data.Preferencias.setModoActivo(null)
                onCerrarSesion()
            },
        )
        return
    }

    // Multisede sin sede elegida todavía: primero la pregunta, y recién después
    // las pantallas. Así no se carga nada "de todas las sedes" para quien no
    // puede verlo, ni se carga dos veces. Sin multisede esto nunca aplica.
    val sedeEstado by pe.saniape.app.data.staff.SedeActiva.estado.collectAsState()
    if (sedeEstado.multiSede && (sedeEstado.obligatorio || sedeEstado.sinSedes)) {
        Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
            DialogoSede()
        }
        return
    }

    // La palabra de la clínica para "paciente" llega a todos los diálogos y pantallas de abajo.
    androidx.compose.runtime.CompositionLocalProvider(pe.saniape.app.data.staff.LocalTerminologiaPaciente provides contexto.terminologiaPaciente) {
    // Tabs visibles según permisos (Inicio y Más siempre).
    val verAgenda = contexto.puede("citas")
    val verPacientes = contexto.puede("pacientes") || contexto.modoClinico

    fun cerrarOverlays() {
        verSesiones = false; verCaja = false; verEspecialidades = false
        verFinanzas = false
        verProfesionales = false; horarioDe = null
        vistaReportes = null
        verServicios = false
        verEquipo = false
        verComisiones = false
        verRetencion = false
        verCampanias = false; verActividad = false; verMiPlan = false; verMiCalendario = false
        verAjustes = false
    }
    // "Llévame" / "Retomar" de los tutoriales y los botones de Primeros pasos.
    fun irA(pantalla: String) {
        when (pantalla) {
            "Inicio" -> { cerrarOverlays(); tab = TabClinica.Inicio }
            "Agenda" -> if (verAgenda) { cerrarOverlays(); tab = TabClinica.Agenda }
            "Pacientes" -> if (verPacientes) { cerrarOverlays(); tab = TabClinica.Pacientes }
            "Mas" -> { cerrarOverlays(); tab = TabClinica.Mas }
            "Caja" -> if (contexto.puede("pagos")) { cerrarOverlays(); verCaja = true }
            "Sesiones" -> if (contexto.puede("sesiones")) { cerrarOverlays(); verSesiones = true }
            "Especialidades" -> if (contexto.puede("equipo")) { cerrarOverlays(); verEspecialidades = true }
            "Profesionales" -> if (contexto.puede("equipo")) { cerrarOverlays(); verProfesionales = true }
        }
    }
    val motor = pe.saniape.app.tutoriales.MotorTutoriales
    motor.navegador = { irA(it) }
    motor.navegables = buildSet {
        add("Inicio"); add("Mas")
        if (verAgenda) add("Agenda")
        if (verPacientes) add("Pacientes")
        if (contexto.puede("pagos")) add("Caja")
        if (contexto.puede("sesiones")) add("Sesiones")
        if (contexto.puede("equipo")) { add("Especialidades"); add("Profesionales") }
    }
    LaunchedEffect(pe.saniape.app.ui.Reanudacion.contador) { motor.alVolverAlFrente() }
    // "Mi página": el slug se pide recién al abrir Más (una vez por clínica):
    // al arrancar la app no se consulta nada nuevo.
    var slugPedidoDe by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(contexto.clinicaId, tab == TabClinica.Mas) {
        if (tab != TabClinica.Mas || slugPedidoDe == contexto.clinicaId) return@LaunchedEffect
        slugPedidoDe = contexto.clinicaId
        urlPagina = pe.saniape.app.data.staff.OnboardingRepo.slugClinica(contexto.clinicaId)
            ?.let { pe.saniape.app.data.staff.urlPaginaClinica(it) }
    }
    val hayOverlay = verSesiones || verCaja || verEspecialidades || verReportes || verProfesionales || verServicios || verFinanzas || verEquipo || verComisiones || verRetencion || verCampanias || verActividad || verMiPlan || verMiCalendario || verAjustes || horarioDe != null
    val tabs = buildList {
        add(TabClinica.Inicio)
        if (verAgenda) add(TabClinica.Agenda)
        if (verPacientes) add(TabClinica.Pacientes)
        add(TabClinica.Mas)
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        bottomBar = {
            if (!pantallaCompleta) NavigationBar(containerColor = c.superficie) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        // Un tab está "activo" solo si NO hay un overlay (Sesiones/Caja) encima.
                        selected = tab == t && !hayOverlay,
                        modifier = Modifier.tourAncla(
                            when (t) {
                                TabClinica.Inicio -> "nav.inicio"
                                TabClinica.Agenda -> "nav.agenda"
                                TabClinica.Pacientes -> "nav.pacientes"
                                TabClinica.Mas -> "nav.mas"
                            },
                        ),
                        // Al tocar un tab hay que CERRAR los overlays sin tab propio; si no,
                        // Caja/Sesiones quedaba tapando el contenido y no redirigía (bug conocido).
                        onClick = { cerrarOverlays(); tab = t },
                        icon = { Icon(t.icono, contentDescription = t.titulo) },
                        label = { Text(if (t == TabClinica.Pacientes) contexto.terminologiaPaciente.Pacientes else t.titulo, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = c.navy,
                            selectedTextColor = c.navy,
                            indicatorColor = c.chipBg,
                            unselectedIconColor = c.textoSuave,
                            unselectedTextColor = c.textoSuave,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(c.fondo)) {
            // Cambio de pestaña con desvanecido: antes era un corte seco y en cada
            // toque parecía que la app "parpadeaba" a otra cosa. Sin desplazamiento
            // lateral a propósito — las pestañas son hermanas, no un flujo con
            // dirección.
            AnimatedContent(
                targetState = tab,
                transitionSpec = { aparecer() togetherWith desaparecer() },
                label = "tab",
            ) { actual ->
                when (actual) {
                    TabClinica.Inicio -> PantallaInicioStaff(
                        ctx = contexto,
                        onIrAgenda = { tab = TabClinica.Agenda },
                        onIrPacientes = { tab = TabClinica.Pacientes },
                        onAbrirCaja = if (contexto.puede("pagos")) ({ verCaja = true }) else null,
                        onBuscar = if (verPacientes) ({ verBuscador = true }) else null,
                        onIr = { irA(it) },
                    )
                    TabClinica.Agenda -> pe.saniape.app.tutoriales.PantallaTutorial("Agenda") { PantallaAgenda(
                        ctx = contexto,
                        fechaInicial = fechaDeAviso,
                        onFechaConsumida = { fechaDeAviso = null },
                        onPantallaCompleta = { pantallaCompleta = it },
                    ) }
                    TabClinica.Pacientes -> pe.saniape.app.tutoriales.PantallaTutorial("Pacientes") { PantallaPacientesStaff(contexto) }
                    TabClinica.Mas -> PantallaMasClinica(
                        contexto = contexto,
                        puedeIrAPortal = puedeIrAPortal,
                        onIrAPortal = onIrAPortal,
                        onCerrarSesion = onCerrarSesion,
                        // Recargar el contexto maestro tras cambiar de clínica → header, ✓,
                        // pacientes y permisos pasan todos a la nueva clínica activa.
                        onCambioClinica = { pe.saniape.app.data.staff.DashboardRepo.limpiarCache(); pe.saniape.app.data.staff.OdontogramaRepo.limpiarCache(); pe.saniape.app.data.staff.FotosRepo.limpiarCache(); pe.saniape.app.data.staff.ChipsRepo.limpiar(); tab = TabClinica.Inicio; intento++ },
                        onAbrirSesiones = if (contexto.puede("sesiones")) ({ verSesiones = true }) else null,
                        onAbrirCaja = if (contexto.puede("pagos")) ({ verCaja = true }) else null,
                        // Nativo (crear + asistente). Mismo permiso que /api/staff/especialidad/*.
                        onAbrirEspecialidades = if (contexto.puede("equipo")) ({ verEspecialidades = true }) else null,
                        // 📈 Reportes nativo (mes a mes). Mismo permiso que /api/reportes/series.
                        onAbrirReportes = if (contexto.puede("reportes")) ({ vistaReportes = pe.saniape.app.ui.clinica.reportes.VistaReportes.MesAMes }) else null,
                        // (nombre del personal) → lista + horario: solo con permiso "equipo" (como la web).
                        onAbrirProfesionales = if (contexto.puede("equipo")) ({ verProfesionales = true }) else null,
                        // El propio profesional ve SU horario en solo lectura.
                        onAbrirMiHorario = contexto.miTerapeutaId?.takeIf { !contexto.puede("equipo") }?.let { id ->
                            { horarioVolver = "← Más"; horarioDe = id to (contexto.nombre ?: "Mi horario") }
                        },
                        // Su enlace de calendario (ICS): solo quien tiene agenda de profesional.
                        onAbrirMiCalendario = contexto.miTerapeutaId?.let { { verMiCalendario = true } },
                        onAbrirMiPagina = urlPagina?.let { { verMiPagina = true } },
                        // Nativo. Mismo permiso que /api/staff/servicio (sin candado de plan, como la web).
                        onAbrirServicios = if (contexto.puede("servicios")) ({ verServicios = true }) else null,
                        // Nativo. Mismo permiso que el menú de la web y /api/staff/caja|gastos-recurrentes.
                        onAbrirFinanzas = if (contexto.puede("finanzas")) ({ verFinanzas = true }) else null,
                        // Nativo. Mismo permiso que la web /equipo y GET /api/staff/equipo.
                        onAbrirEquipo = if (contexto.puede("equipo")) ({ verEquipo = true }) else null,
                        // Nativo. Mismo permiso que la web /comisiones (el plan lo muestra la pantalla; el servidor lo valida).
                        onAbrirComisiones = if (contexto.puede("comisiones")) ({ verComisiones = true }) else null,
                        // Nativo. Mismo acceso que la web (permiso pacientes o modo clínico); el plan Plus lo muestra la pantalla.
                        onAbrirRetencion = if (contexto.puede("pacientes") || contexto.modoClinico) ({ verRetencion = true }) else null,
                        // Nativos. Campañas: mismo permiso que /api/staff/campania; Actividad: solo Admin (/api/actividad);
                        // Mi plan: "ajustes" y de solo lectura (pagar abre la web).
                        onAbrirCampanias = if (contexto.puede("servicios") || contexto.puede("marketing")) ({ verCampanias = true }) else null,
                        onAbrirActividad = if (contexto.esAdmin) ({ verActividad = true }) else null,
                        onAbrirMiPlan = if (contexto.puede("ajustes")) ({ verMiPlan = true }) else null,
                        // Nativo. Mismo permiso que la web /configuracion y /api/staff/configuracion.
                        onAbrirAjustes = if (contexto.puede("ajustes")) ({ verAjustes = true }) else null,
                    )
                }
            }

            // Overlays de módulos sin tab propio. Entran desde la derecha y salen
            // por donde vinieron: eso es lo que hace entender "esto se abrió
            // encima" y "estoy volviendo", en vez de que la pantalla cambie de
            // golpe sin saber a dónde fue.
            AnimatedVisibility(
                visible = verSesiones && contexto.puede("sesiones"),
                enter = entrarDetalle(), exit = salirDetalle(),
            ) {
                Box(Modifier.fillMaxSize().background(c.fondo)) {
                    pe.saniape.app.tutoriales.PantallaTutorial("Sesiones") { PantallaSesiones(ctx = contexto) }
                }
            }
            AnimatedVisibility(
                visible = verCaja && contexto.puede("pagos"),
                enter = entrarDetalle(), exit = salirDetalle(),
            ) {
                Box(Modifier.fillMaxSize().background(c.fondo)) {
                    pe.saniape.app.tutoriales.PantallaTutorial("Caja") { PantallaCajaHoy(ctx = contexto) }
                }
            }
            AnimatedVisibility(
                visible = verFinanzas && contexto.puede("finanzas"),
                enter = entrarDetalle(), exit = salirDetalle(),
            ) {
                Box(Modifier.fillMaxSize().background(c.fondo)) {
                    pe.saniape.app.ui.clinica.finanzas.PantallaFinanzas(ctx = contexto, onSalir = { verFinanzas = false })
                }
            }
            AnimatedVisibility(
                visible = verEspecialidades && contexto.puede("equipo"),
                enter = entrarDetalle(), exit = salirDetalle(),
            ) {
                Box(Modifier.fillMaxSize().background(c.fondo)) {
                    pe.saniape.app.tutoriales.PantallaTutorial("Especialidades") { pe.saniape.app.ui.clinica.especialidades.PantallaEspecialidades(
                        ctx = contexto,
                        onSalir = { verEspecialidades = false },
                    ) }
                }
            }
            AnimatedVisibility(
                visible = verReportes && contexto.puede("reportes"),
                enter = entrarDetalle(), exit = salirDetalle(),
            ) {
                // Una sola capa: saltar de pestaña cambia el contenido, no apila pantallas.
                // `ultimaVistaReportes` mantiene el contenido mientras corre la animación de salida.
                LaunchedEffect(vistaReportes) { vistaReportes?.let { ultimaVistaReportes = it } }
                val irVista: (pe.saniape.app.ui.clinica.reportes.VistaReportes) -> Unit = { vistaReportes = it }
                Box(Modifier.fillMaxSize().background(c.fondo)) {
                    when (vistaReportes ?: ultimaVistaReportes) {
                        pe.saniape.app.ui.clinica.reportes.VistaReportes.MesAMes -> pe.saniape.app.ui.clinica.reportes.PantallaReportes(
                            ctx = contexto, onSalir = { vistaReportes = null }, onVista = irVista,
                        )
                        pe.saniape.app.ui.clinica.reportes.VistaReportes.Rendimiento -> PantallaPacientesPeriodo(
                            ctx = contexto, onSalir = { vistaReportes = null }, onVista = irVista,
                        )
                        pe.saniape.app.ui.clinica.reportes.VistaReportes.Nuevos -> PantallaPacientesNuevos(
                            ctx = contexto, onSalir = { vistaReportes = null }, onVista = irVista,
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = verProfesionales && contexto.puede("equipo"),
                enter = entrarDetalle(), exit = salirDetalle(),
            ) {
                Box(Modifier.fillMaxSize().background(c.fondo)) {
                    pe.saniape.app.ui.clinica.profesionales.PantallaProfesionales(
                        ctx = contexto,
                        onSalir = { verProfesionales = false },
                        onAbrir = { p -> horarioVolver = "← ${pe.saniape.app.tutoriales.pluralPersonal(contexto.terminologiaProfesional)}"; horarioDe = p.id to p.nombre },
                    )
                }
            }
            AnimatedVisibility(
                visible = verServicios && contexto.puede("servicios"),
                enter = entrarDetalle(), exit = salirDetalle(),
            ) {
                Box(Modifier.fillMaxSize().background(c.fondo)) {
                    pe.saniape.app.tutoriales.PantallaTutorial("Servicios") { pe.saniape.app.ui.clinica.servicios.PantallaServicios(
                        ctx = contexto,
                        onSalir = { verServicios = false },
                    ) }
                }
            }
            AnimatedVisibility(
                visible = verEquipo && contexto.puede("equipo"),
                enter = entrarDetalle(), exit = salirDetalle(),
            ) {
                Box(Modifier.fillMaxSize().background(c.fondo)) {
                    pe.saniape.app.ui.clinica.equipo.PantallaEquipo(ctx = contexto, onSalir = { verEquipo = false })
                }
            }
            AnimatedVisibility(
                visible = verComisiones && contexto.puede("comisiones"),
                enter = entrarDetalle(), exit = salirDetalle(),
            ) {
                Box(Modifier.fillMaxSize().background(c.fondo)) {
                    pe.saniape.app.ui.clinica.comisiones.PantallaComisiones(ctx = contexto, onSalir = { verComisiones = false })
                }
            }
            AnimatedVisibility(
                visible = verRetencion && (contexto.puede("pacientes") || contexto.modoClinico),
                enter = entrarDetalle(), exit = salirDetalle(),
            ) {
                Box(Modifier.fillMaxSize().background(c.fondo)) {
                    pe.saniape.app.tutoriales.PantallaTutorial("Retencion") {
                        pe.saniape.app.ui.clinica.retencion.PantallaRetencion(ctx = contexto, onSalir = { verRetencion = false })
                    }
                }
            }
            AnimatedVisibility(
                visible = verCampanias && (contexto.puede("servicios") || contexto.puede("marketing")),
                enter = entrarDetalle(), exit = salirDetalle(),
            ) {
                Box(Modifier.fillMaxSize().background(c.fondo)) {
                    pe.saniape.app.ui.clinica.campanias.PantallaCampanias(ctx = contexto, onSalir = { verCampanias = false })
                }
            }
            AnimatedVisibility(
                visible = verActividad && contexto.esAdmin,
                enter = entrarDetalle(), exit = salirDetalle(),
            ) {
                Box(Modifier.fillMaxSize().background(c.fondo)) {
                    pe.saniape.app.ui.clinica.actividad.PantallaActividad(ctx = contexto, onSalir = { verActividad = false })
                }
            }
            AnimatedVisibility(
                visible = verMiPlan && contexto.puede("ajustes"),
                enter = entrarDetalle(), exit = salirDetalle(),
            ) {
                Box(Modifier.fillMaxSize().background(c.fondo)) {
                    pe.saniape.app.ui.clinica.plan.PantallaMiPlan(ctx = contexto, onSalir = { verMiPlan = false })
                }
            }
            AnimatedVisibility(
                visible = verMiCalendario && contexto.miTerapeutaId != null,
                enter = entrarDetalle(), exit = salirDetalle(),
            ) {
                Box(Modifier.fillMaxSize().background(c.fondo)) {
                    pe.saniape.app.ui.clinica.calendario.PantallaMiCalendario(onSalir = { verMiCalendario = false })
                }
            }
            AnimatedVisibility(
                visible = verAjustes && contexto.puede("ajustes"),
                enter = entrarDetalle(), exit = salirDetalle(),
            ) {
                Box(Modifier.fillMaxSize().background(c.fondo)) {
                    pe.saniape.app.ui.clinica.ajustes.PantallaAjustes(
                        ctx = contexto, onSalir = { verAjustes = false }, onHuboCambios = { recargarTrasAjustes = true },
                    )
                }
            }
            AnimatedVisibility(visible = horarioDe != null, enter = entrarDetalle(), exit = salirDetalle()) {
                val sel = remember(horarioDe) { horarioDe }
                sel?.let { (id, nombre) ->
                    Box(Modifier.fillMaxSize().background(c.fondo)) {
                        pe.saniape.app.ui.clinica.profesionales.PantallaHorarioProfesional(
                            terapeutaId = id, nombre = nombre, volver = horarioVolver, onSalir = { horarioDe = null },
                        )
                    }
                }
            }
            // Multisede: "¿En qué sede trabajas hoy?" (obligatorio si aún no eligió,
            // o a pedido desde el chip). Sin multisede no pinta nada.
            DialogoSede()
            // Buscador global (header) → al elegir, abre la ficha en overlay.
            AnimatedVisibility(visible = verBuscador, enter = entrarDetalle(), exit = salirDetalle()) {
                Box(Modifier.fillMaxSize().background(c.fondo)) {
                    PantallaBuscarPaciente(
                        ctx = contexto,
                        onAbrirFicha = { fichaBuscada = it; verBuscador = false },
                        onCerrar = { verBuscador = false },
                    )
                }
            }
            AnimatedVisibility(visible = fichaBuscada != null, enter = entrarDetalle(), exit = salirDetalle()) {
                // La ficha se recuerda mientras dura la salida: sin esto, al cerrar
                // el contenido desaparecería de golpe y solo se animaría un hueco.
                val pac = remember(fichaBuscada) { fichaBuscada }
                pac?.let {
                    Box(Modifier.fillMaxSize().background(c.fondo)) {
                        pe.saniape.app.tutoriales.PantallaTutorial("Ficha") { pe.saniape.app.ui.clinica.pacientes.PantallaFichaPaciente(
                            ctx = contexto, pacienteInicial = it, onCerrar = { fichaBuscada = null },
                        ) }
                    }
                }
            }
        }
    }
    // Tutoriales (encima de todo, sin bloquear): píldora "¿Primera vez aquí?",
    // la capa del tutorial en curso y el centro de ayuda.
    PildoraPrimeraVez()
    HostTutorial(capa = 0, principal = true)
    CentroAyuda()
    }
    urlPagina?.let { url -> if (verMiPagina) DialogoMiPagina(url) { verMiPagina = false } }
    }
}
