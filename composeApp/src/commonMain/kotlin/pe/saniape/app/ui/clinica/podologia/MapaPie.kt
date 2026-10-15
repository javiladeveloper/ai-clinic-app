package pe.saniape.app.ui.clinica.podologia

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.GRUPOS_HALLAZGO_PODO
import pe.saniape.app.data.staff.PIES_PODO
import pe.saniape.app.data.staff.PIE_IZQ
import pe.saniape.app.data.staff.PieGeometria
import pe.saniape.app.data.staff.ZonaMarcadaPodo
import pe.saniape.app.data.staff.espejoTrazos
import pe.saniape.app.data.staff.nombrePie
import pe.saniape.app.data.staff.nombreVistaPie
import pe.saniape.app.data.staff.pinturaZona
import pe.saniape.app.data.staff.textoHallazgos
import pe.saniape.app.data.staff.zonaPodo
import pe.saniape.app.ui.clinica.mapas.NodoZonaAccesible
import pe.saniape.app.ui.clinica.mapas.colorHex
import pe.saniape.app.ui.clinica.mapas.dibujarForma
import pe.saniape.app.ui.clinica.mapas.dibujarTrazo
import pe.saniape.app.ui.theme.Sania

/**
 * 🦶 Mapa del pie tocable. Gemelo de `components/podologia/MapaPie.tsx`: los dos
 * pies lado a lado COMO SI SE ESTUVIERA FRENTE AL PACIENTE (el derecho a la
 * izquierda de quien mira, el hallux de cada pie hacia el centro), la MISMA
 * geometría (viewBox 100 × 210, ver [PieGeometria]) dibujada en un Canvas.
 *
 * Cada zona se pinta con el color del grupo de su PEOR hallazgo (úlcera = roja
 * siempre) y la opacidad sigue a la severidad. Accesible: cada zona es un nodo
 * con su nombre y hallazgos. Las zonas chicas (uñas, espacios) llevan el área
 * de toque de la web; aun así el editor ofrece "Elegir la zona por nombre".
 * Sin [onSeleccionar] = solo lectura.
 */
@Composable
fun MapaPie(
    zonas: List<ZonaMarcadaPodo>,
    vista: String,
    seleccion: Pair<String, String>? = null,
    onSeleccionar: ((pie: String, zona: String) -> Unit)? = null,
    ancho: Dp = 150.dp,
) {
    val porZona = remember(zonas) { zonas.associateBy { it.clave } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)) {
        PIES_PODO.forEach { pie ->
            PieUno(pie, vista, porZona, seleccion, onSeleccionar, Modifier.weight(1f, fill = false).widthIn(max = ancho))
        }
    }
}

@Composable
private fun PieUno(
    pie: String,
    vista: String,
    porZona: Map<String, ZonaMarcadaPodo>,
    seleccion: Pair<String, String>?,
    onSeleccionar: ((String, String) -> Unit)?,
    modifier: Modifier,
) {
    val c = Sania.colors
    val zonasDibujo = remember(vista, pie) { PieGeometria.zonasDe(vista, pie) }
    val silueta = remember(pie) { if (pie == PIE_IZQ) espejoTrazos(PieGeometria.trazoSilueta, PieGeometria.ANCHO) else PieGeometria.trazoSilueta }
    val deco = remember(pie) { if (pie == PIE_IZQ) PieGeometria.dedosDeco.map { it.espejo(PieGeometria.ANCHO) } else PieGeometria.dedosDeco }
    // El detector de toques vive mientras la vista existe: lee SIEMPRE el último callback.
    val alTocar by rememberUpdatedState(onSeleccionar)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().aspectRatio(PieGeometria.ANCHO / PieGeometria.ALTO)
                .semantics { contentDescription = "${nombrePie(pie)} · vista ${nombreVistaPie(vista).lowercase()}" }
                .then(if (onSeleccionar != null) Modifier.pointerInput(vista, pie) {
                    detectTapGestures { p ->
                        val escala = size.width / PieGeometria.ANCHO
                        PieGeometria.zonaEn(vista, pie, p.x / escala, p.y / escala)?.let { z -> alTocar?.invoke(pie, z) }
                    }
                } else Modifier),
        ) {
            val escalaDp = maxWidth.value / PieGeometria.ANCHO
            Canvas(Modifier.fillMaxSize()) {
                val s = size.width / PieGeometria.ANCHO
                dibujarTrazo(silueta, s, c.borde, relleno = c.chipBg, grosor = 1f)
                if (vista == "plantar") deco.forEach { dibujarForma(it, s, c.chipBg, c.borde, 0.8f) }
                zonasDibujo.forEach { z ->
                    val marcada = porZona["$pie:$vista:${z.id}"]
                    val p = pinturaZona(marcada?.hallazgos)
                    val sel = seleccion?.first == pie && seleccion.second == z.id
                    val colorZ = p?.let { colorHex(it.color) }
                    val rellenoZ = p?.let { colorHex(it.color).copy(alpha = it.opacidad) }
                    z.formas.forEach { f ->
                        dibujarForma(
                            f, s,
                            relleno = rellenoZ ?: c.superficie,
                            borde = if (sel) c.navy else colorZ ?: c.lav,
                            grosor = if (sel) 2.5f else 1f,
                        )
                    }
                }
            }
            // Accesibilidad: un nodo por zona (sin interceptar el dedo).
            zonasDibujo.forEach { z ->
                val marcada = porZona["$pie:$vista:${z.id}"]
                val nombre = zonaPodo(z.id)?.nombre ?: z.id
                NodoZonaAccesible(
                    formas = z.formas, escalaDp = escalaDp,
                    descripcion = "${nombrePie(pie)}, $nombre" + (marcada?.let { ": ${textoHallazgos(it.hallazgos)}" } ?: ""),
                    seleccionada = seleccion?.first == pie && seleccion.second == z.id,
                    onActivar = onSeleccionar?.let { cb -> { cb(pie, z.id) } },
                )
            }
        }
        Text(nombrePie(pie).uppercase(), color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

/** Leyenda de colores (los grupos de hallazgos) y "más intenso = más severo". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LeyendaPie() {
    val c = Sania.colors
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        GRUPOS_HALLAZGO_PODO.forEach { g ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(colorHex(g.color)))
                Spacer(Modifier.width(4.dp))
                Text(g.nombre, color = c.textoSuave, fontSize = 10.sp)
            }
        }
        Text("· más intenso = más severo", color = c.textoSuave, fontSize = 10.sp)
    }
}
