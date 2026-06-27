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
        // Pin = círculo con el color de la clínica + su inicial (como los avatares).
        // Primer tap: muestra el globo (nombre + dirección + referencia).
        // Tap de nuevo en el mismo pin: abre la landing. Igual que la web.
        clinicas.filter { it.lat != null && it.lng != null }.forEach { cl ->
            val m = Marker(map).apply {
                position = GeoPoint(cl.lat!!, cl.lng!!)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = pinClinica(context, cl.nombre.firstOrNull()?.uppercase() ?: "C", cl.colorPrincipal)
                title = cl.nombre
                // Globo con dirección + referencia (cada clínica pone ambas).
                subDescription = listOfNotNull(
                    cl.direccion?.let { "📍 $it" },
                    cl.referencia?.let { "🧭 $it" },
                ).joinToString("\n").ifBlank { "Toca de nuevo para ver más" }
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

/**
 * Pin de clínica: gota con el color de la clínica + un círculo blanco con la
 * inicial. Coherente con los avatares de la landing (sin descargar el logo).
 */
private fun pinClinica(
    context: android.content.Context,
    inicial: String,
    colorHex: String?,
): android.graphics.drawable.Drawable {
    val d = context.resources.displayMetrics.density
    val w = (40 * d).toInt()
    val h = (52 * d).toInt()
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)

    val color = parseColorAndroid(colorHex) ?: android.graphics.Color.parseColor("#2c3e7a")
    val cx = w / 2f
    val cy = w / 2f
    val r = w / 2f

    val pColor = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    // Cola de la gota
    val path = android.graphics.Path().apply {
        moveTo(cx - r * 0.5f, cy + r * 0.55f)
        lineTo(cx, h - 3 * d)
        lineTo(cx + r * 0.5f, cy + r * 0.55f)
        close()
    }
    canvas.drawPath(path, pColor)
    // Círculo de color
    canvas.drawCircle(cx, cy, r, pColor)
    // Borde blanco
    val pBorde = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2.5f * d
    }
    canvas.drawCircle(cx, cy, r - 1.2f * d, pBorde)
    // Inicial en blanco
    val pTexto = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE
        textSize = 18 * d
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
    }
    val ty = cy - (pTexto.descent() + pTexto.ascent()) / 2
    canvas.drawText(inicial, cx, ty, pTexto)

    return android.graphics.drawable.BitmapDrawable(context.resources, bmp)
}

private fun parseColorAndroid(hex: String?): Int? {
    if (hex.isNullOrBlank()) return null
    return try { android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex") }
    catch (e: Exception) { null }
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