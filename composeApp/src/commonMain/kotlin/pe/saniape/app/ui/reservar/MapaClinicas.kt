package pe.saniape.app.ui.reservar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import pe.saniape.app.data.ClinicaDir

/**
 * Mapa de clínicas (OpenStreetMap, gratis). expect/actual: en Android usa osmdroid.
 * Muestra pines de las clínicas con coordenadas y, si hay, la ubicación del paciente.
 */
@Composable
expect fun MapaClinicas(
    clinicas: List<ClinicaDir>,
    miUbicacion: Pair<Double, Double>?,
    // Cambia este valor para pedir al mapa que recentre en mi ubicación (botón 📍).
    recentrarEnMi: Int,
    onTocarClinica: (ClinicaDir) -> Unit,
    modifier: Modifier,
)