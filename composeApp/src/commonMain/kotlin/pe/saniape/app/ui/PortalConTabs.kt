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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp

private enum class Tab(val titulo: String, val icono: ImageVector) {
    Inicio("Inicio", Icons.Filled.Home),
    Reservar("Reservar", Icons.Filled.DateRange),
    Tratamiento("Salud", Icons.Filled.Favorite),
    Mas("Más", Icons.Filled.Menu),
}

/**
 * Contenedor del portal con navegación inferior nativa (bottom tabs).
 * Cada tab muestra una pantalla; comparten el estado del portal.
 */
@Composable
fun PortalConTabs(nombre: String?, onCerrarSesion: () -> Unit) {
    var tab by remember { mutableStateOf(Tab.Inicio) }

    // Botón Atrás en una tab que no es Inicio → vuelve a Inicio (no cierra la app).
    // En la tab Reservar, su propio flujo maneja el back primero (form→landing→dir);
    // cuando ya está en el directorio, este handler lo lleva a Inicio.
    ManejarAtras(activo = tab != Tab.Inicio) { tab = Tab.Inicio }

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
                Tab.Reservar -> pe.saniape.app.ui.reservar.FlujoReservar()
                Tab.Tratamiento -> PantallaSalud()
                Tab.Mas -> PantallaMas(nombre = nombre, onCerrarSesion = onCerrarSesion)
            }
        }
    }
}