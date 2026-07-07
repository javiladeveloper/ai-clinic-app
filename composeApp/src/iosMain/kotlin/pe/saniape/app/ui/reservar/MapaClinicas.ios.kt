package pe.saniape.app.ui.reservar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pe.saniape.app.data.ClinicaDir

/**
 * iOS: mapa de clínicas.
 *
 * TODO(iOS Fase 2): implementar con MapKit (MKMapView vía UIKitView interop) mostrando
 * pines por clínica + la ubicación del paciente, con enfoque al tocar una tarjeta.
 *
 * Por ahora (Fase 1) es un placeholder: la lista de clínicas debajo del mapa (en la
 * pantalla del directorio) sigue funcionando; solo el recuadro del mapa muestra un aviso.
 */
@Composable
actual fun MapaClinicas(
    clinicas: List<ClinicaDir>,
    miUbicacion: Pair<Double, Double>?,
    recentrarEnMi: Int,
    enfocarClinica: ClinicaDir?,
    enfocarTick: Int,
    onTocarClinica: (ClinicaDir) -> Unit,
    modifier: Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            "🗺️ Mapa disponible pronto en iOS.\nUsa la lista de clínicas de abajo.",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp),
        )
    }
}
