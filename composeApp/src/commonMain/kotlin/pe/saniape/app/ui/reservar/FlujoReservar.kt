package pe.saniape.app.ui.reservar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import pe.saniape.app.data.ClinicaDir
import pe.saniape.app.ui.ManejarAtras

/**
 * Flujo de reserva con navegación interna (sin tocar las tabs):
 *   Directorio (lista + mapa) → Mini-landing de la clínica → Formulario de reserva.
 * El botón Atrás del flujo retrocede paso a paso.
 */
private sealed class Paso {
    data object Directorio : Paso()
    data class Landing(val clinica: ClinicaDir) : Paso()
    data class Formulario(val clinica: ClinicaDir) : Paso()
}

@Composable
fun FlujoReservar() {
    var paso by remember { mutableStateOf<Paso>(Paso.Directorio) }

    // Botón Atrás: retrocede dentro del flujo (no cierra la app) salvo en el directorio.
    ManejarAtras(activo = paso !is Paso.Directorio) {
        paso = when (val p = paso) {
            is Paso.Formulario -> Paso.Landing(p.clinica)
            is Paso.Landing -> Paso.Directorio
            else -> Paso.Directorio
        }
    }

    when (val p = paso) {
        is Paso.Directorio -> PantallaDirectorio(
            onElegirClinica = { paso = Paso.Landing(it) },
        )
        is Paso.Landing -> PantallaLandingClinica(
            clinica = p.clinica,
            onReservar = { paso = Paso.Formulario(p.clinica) },
            onAtras = { paso = Paso.Directorio },
        )
        is Paso.Formulario -> PantallaFormularioReserva(
            clinica = p.clinica,
            onAtras = { paso = Paso.Landing(p.clinica) },
        )
    }
}