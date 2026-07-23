package pe.saniape.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.SaludRepo

private enum class Tab(val titulo: String, val icono: ImageVector) {
    Inicio("Inicio", Icons.Filled.Home),
    Reservar("Mis clínicas", Icons.Filled.DateRange),
    Tratamiento("Salud", Icons.Filled.Favorite),
    Mas("Más", Icons.Filled.Menu),
}

/**
 * Contenedor del portal con navegación inferior nativa (bottom tabs).
 * Cada tab muestra una pantalla; comparten el estado del portal.
 */
@Composable
fun PortalConTabs(
    nombre: String?,
    puedeIrAClinica: Boolean = false,
    onIrAClinica: () -> Unit = {},
    onCerrarSesion: () -> Unit,
) {
    var tab by remember { mutableStateOf(Tab.Inicio) }

    // DNI OBLIGATORIO: la cuenta necesita su DNI para conectar su historial en las
    // clínicas. Hasta que lo dé, se muestra SOLO la pantalla de DNI (sin tabs). "..." =
    // cargando; null = falta (bloquea); un valor = ya lo tiene (deja pasar).
    var dni by remember { mutableStateOf<String?>("...") }
    LaunchedEffect(Unit) { dni = runCatching { SaludRepo.dniCuenta() }.getOrNull() }

    // Botón Atrás en una tab que no es Inicio → vuelve a Inicio (no cierra la app).
    ManejarAtras(activo = tab != Tab.Inicio) { tab = Tab.Inicio }

    // Gate: sin DNI, no se muestran las tabs. Cargando = spinner; falta = pedirlo.
    if (dni == "...") {
        Box(Modifier.fillMaxSize().background(Sand), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(color = Navy)
        }
        return
    }
    if (dni == null) {
        PantallaPedirDni(
            onListo = { nuevo -> dni = nuevo },
            // Omitir: deja pasar (verá lo vinculado por correo/código). Se le vuelve a
            // pedir la próxima vez que abra (no persiste el "omitir").
            onOmitir = { dni = "" },
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Blanco) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icono, contentDescription = t.titulo) },
                        label = { Text(t.titulo, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Navy,
                            selectedTextColor = Navy,
                            indicatorColor = Navy50,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Sand)) {
            when (tab) {
                Tab.Inicio -> PantallaPortal(nombre = nombre, onCerrarSesion = onCerrarSesion)
                Tab.Reservar -> pe.saniape.app.ui.reservar.PantallaMisClinicas()
                Tab.Tratamiento -> PantallaSalud()
                Tab.Mas -> PantallaMas(
                    nombre = nombre,
                    puedeIrAClinica = puedeIrAClinica,
                    onIrAClinica = onIrAClinica,
                    onCerrarSesion = onCerrarSesion,
                )
            }
        }
    }
}