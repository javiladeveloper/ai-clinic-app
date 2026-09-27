package pe.saniape.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import pe.saniape.app.data.MedicamentoReceta
import pe.saniape.app.data.RecetaPortal
import pe.saniape.app.ui.theme.Sania

/**
 * 💊 Mis recetas del portal del paciente (gemelo de MisRecetas.tsx de la web).
 *
 * Es una COPIA INFORMATIVA: la farmacia exige la receta impresa con firma y
 * sello (Comunicado DIGEMID 007-2024). Por eso el aviso va arriba de la lista y
 * otra vez dentro de cada receta abierta — quien la muestre en la farmacia lo ve.
 */
@Composable
fun AvisoCopiaReceta(aviso: String) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.pendBg)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("ℹ️", fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        Text(aviso, color = c.pend, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TarjetaReceta(r: RecetaPortal, aviso: String) {
    val c = Sania.colors
    var abierta by rememberSaveable(r.id) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .clickable { abierta = !abierta }
            .padding(Sania.dim.tarjeta),
    ) {
        // Cabecera: número + estado de vigencia.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Receta ${r.numeroTexto}".trim(), color = c.texto,
                    fontSize = Sania.txt.seccion, fontWeight = FontWeight.Bold)
                Text("Emitida el ${fechaDMA(r.fecha)}", color = c.textoSuave, fontSize = 12.sp)
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                .background(if (r.vigente) c.okBg else c.chipBg)
                .padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(if (r.vigente) "Vigente" else "Vencida",
                    color = if (r.vigente) c.ok else c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Profesional y clínica (con su logo, white-label como en el tratamiento).
        r.prescriptor?.let {
            Spacer(Modifier.height(4.dp))
            Text("🩺 ${it.nombre}", color = c.texto, fontSize = 13.sp)
        }
        r.clinica?.let { nombre ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!r.clinicaLogo.isNullOrBlank()) {
                    AsyncImage(
                        model = r.clinicaLogo,
                        contentDescription = nombre,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(nombre, color = c.textoSuave, fontSize = 12.sp)
                } else {
                    Text("🏥 $nombre", color = c.textoSuave, fontSize = 12.sp)
                }
            }
        }
        if (r.validaHasta.isNotBlank()) {
            Text(
                (if (r.vigente) "Válida hasta el " else "Venció el ") + fechaDMA(r.validaHasta),
                color = if (r.vigente) c.ok else c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            )
        }

        // Resumen de los medicamentos (cerrada).
        Spacer(Modifier.height(6.dp))
        Text(
            r.items.joinToString(" · ") { "${it.dci} ${it.concentracion}".trim() },
            color = c.texto, fontSize = 13.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(if (abierta) "Ocultar detalle" else "Ver detalle",
            color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)

        AnimatedVisibility(abierta) {
            Column(Modifier.padding(top = Sania.dim.sm)) {
                HorizontalDivider(color = c.borde)
                Spacer(Modifier.height(Sania.dim.sm))

                // Establecimiento y prescriptor (lo que exige la receta, DS 014-2011-SA art. 56).
                r.clinica?.let { DatoReceta("Establecimiento", it) }
                // Dirección y ciudad juntas ("Av. Bolognesi 123, Tacna"), sin repetir la
                // ciudad si la dirección ya la trae; sin ninguna de las dos, no hay fila.
                val ciudad = r.clinicaCiudad?.takeIf { r.clinicaDireccion?.contains(it, ignoreCase = true) != true }
                listOfNotNull(r.clinicaDireccion, ciudad).joinToString(", ")
                    .takeIf { it.isNotBlank() }?.let { DatoReceta("Dirección", it) }
                r.clinicaTelefono?.let { DatoReceta("Teléfono", it) }
                r.prescriptor?.let { p ->
                    DatoReceta("Prescriptor", listOfNotNull(p.nombre, p.profesion, p.especialidad).joinToString(" · "))
                    if (p.colegiatura.isNotBlank()) DatoReceta("Colegiatura", p.colegiatura)
                }
                DatoReceta("Fecha de emisión", fechaDMA(r.fecha))
                if (r.validaHasta.isNotBlank()) DatoReceta("Válida hasta", fechaDMA(r.validaHasta))
                r.diagnostico?.let { d ->
                    DatoReceta("Diagnóstico", d + (r.cie10?.let { " ($it)" } ?: ""))
                }

                Spacer(Modifier.height(Sania.dim.sm))
                Text("MEDICAMENTOS", color = c.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
                r.items.forEachIndexed { i, it ->
                    Spacer(Modifier.height(6.dp))
                    FilaMedicamentoReceta(i + 1, it)
                }

                r.indicacionesGenerales?.let {
                    Spacer(Modifier.height(Sania.dim.sm))
                    DatoReceta("Indicaciones", it)
                }

                Spacer(Modifier.height(Sania.dim.md))
                AvisoCopiaReceta(aviso)
            }
        }
    }
}

@Composable
internal fun FilaMedicamentoReceta(n: Int, m: MedicamentoReceta) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.fondo)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            "$n. ${m.dci} ${m.concentracion}".trim(),
            color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        )
        val sub = listOfNotNull(m.forma.ifBlank { null }, m.marca?.let { "Marca: $it" }).joinToString(" · ")
        if (sub.isNotBlank()) Text(sub, color = c.textoSuave, fontSize = 12.sp)
        if (m.indicacionTexto.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(m.indicacionTexto, color = c.texto, fontSize = 13.sp)
        }
        Spacer(Modifier.height(4.dp))
        if (m.dosis.isNotBlank()) DatoReceta("Dosis", m.dosis)
        if (m.frecuencia.isNotBlank()) DatoReceta("Frecuencia", m.frecuencia)
        if (m.duracion.isNotBlank()) DatoReceta("Duración", m.duracion)
        if (m.via.isNotBlank()) DatoReceta("Vía", m.via)
        if (m.cantidadTexto.isNotBlank()) DatoReceta("Cantidad", m.cantidadTexto)
        m.indicaciones?.let { DatoReceta("Indicaciones", it) }
    }
}

/** Fila "Etiqueta: valor" del detalle. */
@Composable
internal fun DatoReceta(etiqueta: String, valor: String) {
    val c = Sania.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text("$etiqueta: ", color = c.textoSuave, fontSize = 12.sp)
        Text(valor, color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f, fill = false))
    }
}

/** "2026-09-27" → "27/09/2026". Si no es fecha, tal cual. */
internal fun fechaDMA(iso: String): String {
    val p = iso.take(10).split("-")
    return if (p.size == 3 && p[0].length == 4) "${p[2]}/${p[1]}/${p[0]}" else iso
}
