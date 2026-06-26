package pe.saniape.app.ui.reservar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
    recentrarEnMi: Int,
    onTocarClinica: (ClinicaDir) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val mapa = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(14.0)
        }
    }

    AndroidView(modifier = modifier, factory = { mapa }, update = { map ->
        map.overlays.clear()

        // ── Pin de "mi ubicación" (azul, círculo destacado) ──
        miUbicacion?.let { (lat, lng) ->
            val yo = Marker(map).apply {
                position = GeoPoint(lat, lng)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = puntoUbicacion(context)
                title = "Estás aquí"
                setInfoWindow(null)
            }
            map.overlays.add(yo)
        }

        // ── Pines de clínicas ──
        // Primer tap: muestra el globo (nombre + dirección). Tap de nuevo en el
        // mismo pin (globo ya abierto): abre la landing. Igual que la web.
        clinicas.filter { it.lat != null && it.lng != null }.forEach { cl ->
            val m = Marker(map).apply {
                position = GeoPoint(cl.lat!!, cl.lng!!)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = cl.nombre
                subDescription = cl.direccion ?: ""
                setOnMarkerClickListener { marker, _ ->
                    if (marker.isInfoWindowShown) {
                        marker.closeInfoWindow()
                        onTocarClinica(cl)            // segundo tap → landing
                    } else {
                        map.overlays.filterIsInstance<Marker>().forEach { it.closeInfoWindow() }
                        marker.showInfoWindow()       // primer tap → info
                        map.controller.animateTo(marker.position)
                    }
                    true
                }
            }
            map.overlays.add(m)
        }

        // Centro inicial (solo la primera vez que hay datos).
        if (map.mapCenter.latitude == 0.0) {
            val centro = miUbicacion?.let { GeoPoint(it.first, it.second) }
                ?: clinicas.firstOrNull { it.lat != null && it.lng != null }?.let { GeoPoint(it.lat!!, it.lng!!) }
                ?: GeoPoint(-18.0066, -70.2463)
            map.controller.setCenter(centro)
        }
        map.invalidate()
    })

    // Recentrar en mi ubicación cuando se pulsa el botón 📍 (cambia recentrarEnMi).
    LaunchedEffect(recentrarEnMi) {
        if (recentrarEnMi > 0) {
            miUbicacion?.let { (lat, lng) ->
                mapa.controller.animateTo(GeoPoint(lat, lng))
                mapa.controller.setZoom(16.0)
            }
        }
    }
}

/** Punto azul tipo "mi ubicación" (círculo relleno con borde blanco). */
private fun puntoUbicacion(context: android.content.Context): android.graphics.drawable.Drawable {
    val tam = (24 * context.resources.displayMetrics.density).toInt()
    val bmp = android.graphics.Bitmap.createBitmap(tam, tam, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val r = tam / 2f
    val pBorde = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    val pAzul = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#1d6fa8") }
    canvas.drawCircle(r, r, r, pBorde)
    canvas.drawCircle(r, r, r * 0.72f, pAzul)
    return android.graphics.drawable.BitmapDrawable(context.resources, bmp)
}