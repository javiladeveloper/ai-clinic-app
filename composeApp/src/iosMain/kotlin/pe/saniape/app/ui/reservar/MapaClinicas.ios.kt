package pe.saniape.app.ui.reservar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKAnnotationView
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKPointAnnotation
import platform.darwin.NSObject
import pe.saniape.app.data.ClinicaDir

/**
 * iOS: mapa de clínicas con MapKit (MKMapView vía UIKitView interop). Muestra un pin por
 * clínica con coordenadas + la ubicación del paciente (punto azul). Equivale al osmdroid
 * de Android. Reacciona a:
 *  - recentrarEnMi: centra en mi ubicación (botón 📍).
 *  - enfocarClinica/enfocarTick: centra en una clínica y abre su globo (tap en la tarjeta).
 *  - tap en un pin → onTocarClinica.
 */
@OptIn(ExperimentalForeignApi::class)
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
    val delegate = remember { MapaDelegate(onTocarClinica) }
    val mapa = remember { MKMapView().also { it.delegate = delegate } }
    val estado = remember { EstadoMapa() }

    UIKitView(
        factory = { mapa },
        modifier = modifier,
        update = { m ->
            m.showsUserLocation = miUbicacion != null

            // Re-sincroniza pines solo si cambió el conjunto de clínicas.
            val firma = clinicas.joinToString(",") { "${it.slug}:${it.lat}:${it.lng}" }
            if (firma != estado.firmaPines) {
                estado.firmaPines = firma
                m.removeAnnotations(m.annotations)
                delegate.limpiar()
                clinicas.forEach { c ->
                    val lat = c.lat
                    val lng = c.lng
                    if (lat != null && lng != null) {
                        val a = MKPointAnnotation()
                        a.setCoordinate(CLLocationCoordinate2DMake(lat, lng))
                        a.setTitle(c.nombre)
                        delegate.registrar(a, c)
                        m.addAnnotation(a)
                    }
                }
            }

            // Región inicial (una sola vez).
            if (!estado.regionPuesta) {
                estado.regionPuesta = true
                val centro = miUbicacion
                    ?: clinicas.firstOrNull { it.lat != null && it.lng != null }?.let { it.lat!! to it.lng!! }
                    ?: (LIMA_LAT to LIMA_LNG)
                m.setRegion(
                    MKCoordinateRegionMakeWithDistance(
                        CLLocationCoordinate2DMake(centro.first, centro.second),
                        12000.0, 12000.0,
                    ),
                    animated = false,
                )
            }

            // Recentrar en mi ubicación (botón 📍).
            if (recentrarEnMi != estado.ultimoRecentrar) {
                estado.ultimoRecentrar = recentrarEnMi
                miUbicacion?.let { (lat, lng) ->
                    m.setRegion(
                        MKCoordinateRegionMakeWithDistance(
                            CLLocationCoordinate2DMake(lat, lng), 3000.0, 3000.0,
                        ),
                        animated = true,
                    )
                }
            }

            // Enfocar una clínica (tap en su tarjeta).
            if (enfocarTick != estado.ultimoEnfoque) {
                estado.ultimoEnfoque = enfocarTick
                val c = enfocarClinica
                val lat = c?.lat
                val lng = c?.lng
                if (c != null && lat != null && lng != null) {
                    m.setRegion(
                        MKCoordinateRegionMakeWithDistance(
                            CLLocationCoordinate2DMake(lat, lng), 1500.0, 1500.0,
                        ),
                        animated = true,
                    )
                    delegate.anotacionDe(c)?.let { m.selectAnnotation(it, animated = true) }
                }
            }
        },
    )
}

private const val LIMA_LAT = -12.0464
private const val LIMA_LNG = -77.0428

/** Estado mutable retenido entre recomposiciones (fuera de Compose state a propósito). */
private class EstadoMapa {
    var firmaPines: String = ""
    var regionPuesta: Boolean = false
    var ultimoRecentrar: Int = Int.MIN_VALUE
    var ultimoEnfoque: Int = Int.MIN_VALUE
}

private class MapaDelegate(
    private val onTocar: (ClinicaDir) -> Unit,
) : NSObject(), MKMapViewDelegateProtocol {

    private val porAnotacion = mutableMapOf<MKPointAnnotation, ClinicaDir>()

    fun limpiar() = porAnotacion.clear()
    fun registrar(a: MKPointAnnotation, c: ClinicaDir) { porAnotacion[a] = c }
    fun anotacionDe(c: ClinicaDir): MKPointAnnotation? =
        porAnotacion.entries.firstOrNull { it.value.slug == c.slug }?.key

    override fun mapView(mapView: MKMapView, didSelectAnnotationView: MKAnnotationView) {
        val ann = didSelectAnnotationView.annotation as? MKPointAnnotation ?: return
        porAnotacion[ann]?.let(onTocar)
    }
}
