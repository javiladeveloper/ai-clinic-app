package pe.saniape.app.ui.reservar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlin.math.cos
import kotlin.math.sin
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
    enfocarClinica: ClinicaDir?,
    enfocarTick: Int,
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

    // Logos de las clínicas para los pines (white-label). Se descargan una vez con
    // coil; al llegar cada uno, el estado cambia y el update del mapa redibuja el pin.
    var logos by remember { mutableStateOf<Map<String, android.graphics.Bitmap>>(emptyMap()) }
    LaunchedEffect(clinicas) {
        val loader = SingletonImageLoader.get(context)
        for (cl in clinicas) {
            val url = cl.logoUrl
            if (url.isNullOrBlank() || logos.containsKey(cl.slug)) continue
            try {
                // allowHardware(false): el pin se dibuja en un Canvas de software y los
                // hardware bitmaps de coil lo crashean ("Software rendering doesn't support...").
                val res = loader.execute(
                    ImageRequest.Builder(context).data(url).allowHardware(false).build()
                )
                (res as? SuccessResult)?.image?.toBitmap()?.let { bmp ->
                    logos = logos + (cl.slug to bmp)
                }
            } catch (_: Exception) { /* sin logo → queda la inicial */ }
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
        // Pin = logo de la clínica (white-label) o círculo de color con su inicial.
        // Primer tap: muestra el globo (nombre + dirección + referencia).
        // Tap de nuevo en el mismo pin: abre la landing. Igual que la web.
        // Clínicas en el MISMO punto (o casi): se separan unos metros en círculo
        // para que un pin no tape al otro.
        val posiciones = mutableListOf<GeoPoint>()
        clinicas.filter { it.lat != null && it.lng != null }.forEach { cl ->
            var pos = GeoPoint(cl.lat!!, cl.lng!!)
            var intento = 0
            while (intento < 8 && posiciones.any { it.distanceToAsDouble(pos) < 30.0 }) {
                val ang = intento * (Math.PI / 4)
                pos = GeoPoint(cl.lat!! + 0.00040 * sin(ang), cl.lng!! + 0.00040 * cos(ang))
                intento++
            }
            posiciones.add(pos)
            val m = Marker(map).apply {
                position = pos
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = pinClinica(context, cl.nombre.firstOrNull()?.uppercase() ?: "C",
                    cl.colorPrincipal, logos[cl.slug])
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

    // Enfocar una clínica (tap en su tarjeta de la lista): centrar + abrir su globo.
    LaunchedEffect(enfocarTick) {
        val cl = enfocarClinica ?: return@LaunchedEffect
        if (enfocarTick <= 0 || cl.lat == null || cl.lng == null) return@LaunchedEffect
        mapa.overlays.filterIsInstance<Marker>().forEach { it.closeInfoWindow() }
        mapa.controller.animateTo(GeoPoint(cl.lat!!, cl.lng!!))
        mapa.controller.setZoom(16.0)
        mapa.overlays.filterIsInstance<Marker>().firstOrNull { it.title == cl.nombre }?.showInfoWindow()
    }
}

/**
 * Pin de clínica: gota con el color de la clínica y, adentro, el LOGO de la clínica
 * (white-label) sobre fondo blanco; si aún no cargó (o no tiene), la inicial.
 */
private fun pinClinica(
    context: android.content.Context,
    inicial: String,
    colorHex: String?,
    logo: android.graphics.Bitmap? = null,
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

    if (logo != null) {
        // Logo recortado en círculo sobre fondo blanco (los logos suelen ser rectangulares
        // o con transparencia — el blanco asegura que se vean bien).
        val rIn = r - 2.2f * d
        val pBlanco = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
        }
        canvas.drawCircle(cx, cy, rIn, pBlanco)
        canvas.save()
        val clip = android.graphics.Path().apply {
            addCircle(cx, cy, rIn - 0.8f * d, android.graphics.Path.Direction.CW)
        }
        canvas.clipPath(clip)
        // Escalar el logo manteniendo proporción, centrado en el círculo.
        val lado = (rIn - 0.8f * d) * 2f
        val escala = minOf(lado / logo.width, lado / logo.height)
        val lw = logo.width * escala
        val lh = logo.height * escala
        val dst = android.graphics.RectF(cx - lw / 2, cy - lh / 2, cx + lw / 2, cy + lh / 2)
        canvas.drawBitmap(logo, null, dst, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG))
        canvas.restore()
    } else {
        // Borde blanco + inicial (fallback sin logo)
        val pBorde = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 2.5f * d
        }
        canvas.drawCircle(cx, cy, r - 1.2f * d, pBorde)
        val pTexto = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            textSize = 18 * d
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
        val ty = cy - (pTexto.descent() + pTexto.ascent()) / 2
        canvas.drawText(inicial, cx, ty, pTexto)
    }

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