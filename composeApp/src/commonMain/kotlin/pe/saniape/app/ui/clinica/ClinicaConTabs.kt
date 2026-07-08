package pe.saniape.app.ui.clinica

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
    var intento by remember { mutableStateOf(0) }   // para "Reintentar"
    // Sub-pantallas accesibles desde "Más" (módulos sin tab propio).
    var verSesiones by remember { mutableStateOf(false) }
    var verCaja by remember { mutableStateOf(false) }

    LaunchedEffect(intento) {
        cargando = true; error = null
        when (val r = StaffContextoRepo.cargar()) {
            is StaffContextoRepo.Resultado.Ok -> {
                ctx = r.contexto
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

    ManejarAtras(activo = verSesiones || verCaja || tab != TabClinica.Inicio) {
        when {
            verSesiones -> verSesiones = false
            verCaja -> verCaja = false
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

    // Tabs visibles según permisos (Inicio y Más siempre).
    val verAgenda = contexto.puede("citas")
    val verPacientes = contexto.puede("pacientes") || contexto.modoClinico
    val tabs = buildList {
        add(TabClinica.Inicio)
        if (verAgenda) add(TabClinica.Agenda)
        if (verPacientes) add(TabClinica.Pacientes)
        add(TabClinica.Mas)
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = c.superficie) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        // Un tab está "activo" solo si NO hay un overlay (Sesiones/Caja) encima.
                        selected = tab == t && !verSesiones && !verCaja,
                        // Al tocar un tab hay que CERRAR los overlays sin tab propio; si no,
                        // Caja/Sesiones quedaba tapando el contenido y no redirigía (bug conocido).
                        onClick = { verSesiones = false; verCaja = false; tab = t },
                        icon = { Icon(t.icono, contentDescription = t.titulo) },
                        label = { Text(t.titulo, fontSize = 11.sp) },
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
            when (tab) {
                TabClinica.Inicio -> PantallaInicioStaff(
                    ctx = contexto,
                    onIrAgenda = { tab = TabClinica.Agenda },
                    onIrPacientes = { tab = TabClinica.Pacientes },
                    onAbrirCaja = if (contexto.puede("pagos")) ({ verCaja = true }) else null,
                )
                TabClinica.Agenda -> PantallaAgenda(contexto)
                TabClinica.Pacientes -> PantallaPacientesStaff(contexto)
                TabClinica.Mas -> PantallaMasClinica(
                    contexto = contexto,
                    puedeIrAPortal = puedeIrAPortal,
                    onIrAPortal = onIrAPortal,
                    onCerrarSesion = onCerrarSesion,
                    // Recargar el contexto maestro tras cambiar de clínica → header, ✓,
                    // pacientes y permisos pasan todos a la nueva clínica activa.
                    onCambioClinica = { tab = TabClinica.Inicio; intento++ },
                    onAbrirSesiones = if (contexto.puede("sesiones")) ({ verSesiones = true }) else null,
                    onAbrirCaja = if (contexto.puede("pagos")) ({ verCaja = true }) else null,
                )
            }
            // Overlay de módulos sin tab propio (encima del contenido, oculta los tabs).
            if (verSesiones && contexto.puede("sesiones")) {
                Box(Modifier.fillMaxSize().background(c.fondo)) {
                    PantallaSesiones(ctx = contexto)
                }
            }
            if (verCaja && contexto.puede("pagos")) {
                Box(Modifier.fillMaxSize().background(c.fondo)) {
                    PantallaCajaHoy(ctx = contexto)
                }
            }
        }
    }
}