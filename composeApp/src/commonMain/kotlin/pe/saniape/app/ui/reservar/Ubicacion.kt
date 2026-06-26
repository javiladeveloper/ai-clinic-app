package pe.saniape.app.ui.reservar

import androidx.compose.runtime.Composable

/**
 * Obtiene la ubicación del paciente (lat, lng) si concede el permiso.
 * expect/actual: en Android pide el permiso y usa FusedLocationProvider.
 *
 * [onUbicacion] se llama con la ubicación, o con null si no se pudo/denegó.
 * Devuelve una función para disparar la solicitud (al pulsar "usar mi ubicación").
 */
@Composable
expect fun recordarSolicitarUbicacion(
    onUbicacion: (Pair<Double, Double>?) -> Unit,
): () -> Unit