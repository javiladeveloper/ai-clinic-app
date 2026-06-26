package pe.saniape.app.ui.reservar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import pe.saniape.app.data.ClinicaDir

/**
 * Mapa OSM con osmdroid (gratis, sin API key). Pines de clínicas + ubicación del
 * paciente. Centra en la ubicación del paciente si la hay; si no, en las clínicas.
 */
@Composable
actual fun MapaClinicas(
    clinicas: List<ClinicaDir>,
    miUbicacion: Pair<Double, Double>?,
    onTocarClinica: (ClinicaDir) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            // osmdroid requiere un User-Agent (política de uso de tiles de OSM).
            Configuration.getInstance().userAgentValue = ctx.packageName

            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(13.0)

                // Centro: ubicación del paciente o primera clínica con coords.
                val centro = miUbicacion?.let { GeoPoint(it.first, it.second) }
                    ?: clinicas.firstOrNull { it.lat != null && it.lng != null }
                        ?.let { GeoPoint(it.lat!!, it.lng!!) }
                    ?: GeoPoint(-18.0066, -70.2463) // Tacna por defecto
                controller.setCenter(centro)
            }
        },
        update = { map ->
            map.overlays.clear()

            // Pin de "mi ubicación"
            miUbicacion?.let { (lat, lng) ->
                val yo = Marker(map).apply {
                    position = GeoPoint(lat, lng)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Tu ubicación"
                }
                map.overlays.add(yo)
            }

            // Pines de clínicas
            clinicas.filter { it.lat != null && it.lng != null }.forEach { cl ->
                val m = Marker(map).apply {
                    position = GeoPoint(cl.lat!!, cl.lng!!)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = cl.nombre
                    subDescription = cl.direccion ?: ""
                    setOnMarkerClickListener { _, _ ->
                        onTocarClinica(cl)
                        true
                    }
                }
                map.overlays.add(m)
            }
            map.invalidate()
        },
    )
}