package pe.saniape.app.ui.reservar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import pe.saniape.app.data.ClinicaDir
import pe.saniape.app.data.DirectorioRepo
import pe.saniape.app.ui.theme.Sania

/**
 * Directorio de clínicas — mapa de proximidad (osmdroid) + lista. El paciente ve
 * las clínicas cerca de su ubicación y elige dónde reservar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaDirectorio(onElegirClinica: (ClinicaDir) -> Unit) {
    val c = Sania.colors
    var cargando by remember { mutableStateOf(true) }
    var clinicas by remember { mutableStateOf<List<ClinicaDir>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var miUbicacion by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var vista by remember { mutableStateOf(Vista.Mapa) }
    var recentrarTick by remember { mutableStateOf(0) }
    // Clínica enfocada en el mapa (tap simple en su tarjeta).
    var enfocada by remember { mutableStateOf<ClinicaDir?>(null) }
    var enfocarTick by remember { mutableStateOf(0) }

    val solicitarUbicacion = recordarSolicitarUbicacion { miUbicacion = it }

    LaunchedEffect(Unit) {
        try {
            clinicas = DirectorioRepo.clinicas()
        } catch (e: Exception) {
            error = "No se pudo cargar el directorio. Revisa tu conexión."
        } finally {
            cargando = false
        }
    }
    // Pedir ubicación al entrar (para centrar el mapa "cerca de mí").
    LaunchedEffect(Unit) { solicitarUbicacion() }

    // Ordenar por cercanía si tenemos ubicación.
    val ordenadas = remember(clinicas, miUbicacion) {
        val u = miUbicacion
        if (u == null) clinicas
        else clinicas.sortedBy { cl ->
            if (cl.lat != null && cl.lng != null) distanciaKm(u.first, u.second, cl.lat, cl.lng)
            else Double.MAX_VALUE
        }
    }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        when {
            cargando -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = c.navy)
            }
            error != null -> Box(Modifier.fillMaxSize().padding(28.dp), Alignment.Center) {
                Text("⚠ $error", color = c.error, fontSize = 14.sp)
            }
            else -> Column(Modifier.fillMaxSize()) {
                // Encabezado + toggle mapa/lista
                Column(Modifier.fillMaxWidth().padding(horizontal = Sania.dim.lg, vertical = Sania.dim.md)) {
                    Text("Reservar una cita", color = c.navy,
                        fontSize = Sania.txt.titulo, fontWeight = FontWeight.Bold)
                    Text("Elige la clínica donde quieres atenderte.",
                        color = c.textoSuave, fontSize = Sania.txt.pequeno)
                    Spacer(Modifier.height(Sania.dim.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Toggle("🗺️ Mapa", vista == Vista.Mapa) { vista = Vista.Mapa }
                        Toggle("☰ Lista", vista == Vista.Lista) { vista = Vista.Lista }
                    }
                }

                if (vista == Vista.Mapa) {
                    Box(
                        Modifier.fillMaxWidth().height(300.dp)
                            .padding(horizontal = Sania.dim.lg)
                            .clip(RoundedCornerShape(Sania.shape.md.dp)),
                    ) {
                        MapaClinicas(
                            clinicas = ordenadas,
                            miUbicacion = miUbicacion,
                            recentrarEnMi = recentrarTick,
                            enfocarClinica = enfocada,
                            enfocarTick = enfocarTick,
                            onTocarClinica = onElegirClinica,
                            modifier = Modifier.fillMaxSize(),
                        )
                        // Botón flotante "Mi ubicación" (esquina inferior derecha).
                        Box(
                            Modifier.align(Alignment.BottomEnd).padding(Sania.dim.md)
                                .clip(RoundedCornerShape(Sania.shape.pill.dp))
                                .background(c.superficie)
                                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.pill.dp))
                                .clickable {
                                    if (miUbicacion == null) solicitarUbicacion()
                                    recentrarTick++
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text("📍 Mi ubicación", color = c.navy,
                                fontSize = Sania.txt.pequeno, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(Sania.dim.sm))
                }

                // Con ubicación: primero "Cerca de ti" (≤ 50 km) y aparte "Otras ciudades",
                // para que no aparezca una clínica de Lima arriba estando en Tacna.
                val u = miUbicacion
                val distDe: (ClinicaDir) -> Double? = { cl ->
                    if (u != null && cl.lat != null && cl.lng != null)
                        distanciaKm(u.first, u.second, cl.lat, cl.lng) else null
                }
                val cercanas = if (u != null) ordenadas.filter { (distDe(it) ?: Double.MAX_VALUE) <= 50.0 } else ordenadas
                val lejanas = if (u != null) ordenadas.filterNot { (distDe(it) ?: Double.MAX_VALUE) <= 50.0 } else emptyList()

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = Sania.dim.lg),
                    verticalArrangement = Arrangement.spacedBy(Sania.dim.md),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = Sania.dim.sm, bottom = Sania.dim.xl),
                ) {
                    if (u != null && cercanas.isNotEmpty()) {
                        item { TituloSeccion("📍 Cerca de ti") }
                    }
                    items(cercanas) { cl ->
                        TarjetaClinica(cl, distDe(cl),
                            onVerEnMapa = {
                                if (cl.lat != null && cl.lng != null) {
                                    vista = Vista.Mapa; enfocada = cl; enfocarTick++
                                } else onElegirClinica(cl)   // sin coordenadas no hay pin → detalle
                            },
                            onDetalles = { onElegirClinica(cl) })
                    }
                    if (u != null && cercanas.isEmpty() && lejanas.isNotEmpty()) {
                        item {
                            Text("No encontramos clínicas cerca de tu ubicación todavía.",
                                color = c.textoSuave, fontSize = Sania.txt.pequeno)
                        }
                    }
                    if (lejanas.isNotEmpty()) {
                        item { TituloSeccion("🌎 Otras ciudades") }
                    }
                    items(lejanas) { cl ->
                        TarjetaClinica(cl, distDe(cl),
                            onVerEnMapa = {
                                if (cl.lat != null && cl.lng != null) {
                                    vista = Vista.Mapa; enfocada = cl; enfocarTick++
                                } else onElegirClinica(cl)
                            },
                            onDetalles = { onElegirClinica(cl) })
                    }
                    if (ordenadas.isEmpty()) {
                        item {
                            Text("No hay clínicas disponibles por ahora.",
                                color = c.textoSuave, fontSize = Sania.txt.cuerpo)
                        }
                    }
                }
            }
        }
    }
}

private enum class Vista { Mapa, Lista }

@Composable
private fun TituloSeccion(texto: String) {
    Text(texto, color = Sania.colors.navy, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold)
}

@Composable
private fun Toggle(texto: String, activo: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
            .background(if (activo) c.navy else c.superficie)
            .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
            .clickable { onClick() }.padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Text(texto, color = if (activo) c.sobreNavy else c.texto,
            fontSize = Sania.txt.pequeno, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun TarjetaClinica(
    cl: ClinicaDir,
    distanciaKm: Double?,
    onVerEnMapa: () -> Unit,   // tap simple → centrar en el mapa
    onDetalles: () -> Unit,    // doble tap (o el link de abajo) → landing/reservar
) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .combinedClickable(onDoubleClick = { onDetalles() }) { onVerEnMapa() }
            .padding(Sania.dim.tarjeta),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top) {
            Text(cl.nombre, color = c.texto, fontSize = Sania.txt.seccion, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            distanciaKm?.let {
                Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.chipBg)
                    .padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text(distanciaHumana(it), color = c.navy, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        val ubic = listOfNotNull(cl.distrito, cl.ciudad).joinToString(", ")
        if (ubic.isNotBlank()) Text("📍 $ubic", color = c.textoSuave, fontSize = Sania.txt.pequeno)
        cl.direccion?.let { Text(it, color = c.textoSuave, fontSize = 12.sp) }

        if (cl.especialidades.isNotEmpty()) {
            Spacer(Modifier.height(Sania.dim.sm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                cl.especialidades.take(4).forEach { esp ->
                    Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.chipBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text(esp, color = c.navy, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Spacer(Modifier.height(Sania.dim.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Toca para ver en el mapa", color = c.textoSuave, fontSize = 11.sp)
            // Premium sale en el directorio pero SIN reserva online (eso es de Plus).
            Text(if (cl.permiteReserva) "Ver y reservar →" else "Ver clínica →",
                color = c.navy, fontSize = Sania.txt.pequeno, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onDetalles() }.padding(4.dp))
        }
    }
}

// ── Distancia (haversine) ──
private fun rad(grados: Double): Double = grados * kotlin.math.PI / 180.0

private fun distanciaKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371.0
    val dLat = rad(lat2 - lat1)
    val dLng = rad(lng2 - lng1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(rad(lat1)) * cos(rad(lat2)) * sin(dLng / 2) * sin(dLng / 2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}

private fun distanciaHumana(km: Double): String =
    if (km < 1) "${(km * 1000).roundToInt()} m" else "${(km * 10).roundToInt() / 10.0} km"