package pe.saniape.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.EvaluacionCapilarPortal
import pe.saniape.app.data.MiEvolucionCapilar
import pe.saniape.app.data.clasificacionPortal
import pe.saniape.app.data.hayMiniaturizacionPortal
import pe.saniape.app.data.resumenDensidadPortal
import pe.saniape.app.data.serieCapilarPortal
import pe.saniape.app.data.staff.numTrico
import pe.saniape.app.data.textoClasificacionPortal
import pe.saniape.app.data.zonasConGraficoPortal
import pe.saniape.app.ui.clinica.capilar.SiluetaCapilar
import pe.saniape.app.ui.clinica.capilar.colorZona
import pe.saniape.app.ui.clinica.historia.GraficoLineaHc
import pe.saniape.app.ui.clinica.historia.fechaHc
import pe.saniape.app.ui.theme.Sania

/**
 * 🔬 Mi evolución capilar del portal del paciente (gemelo de
 * MiEvolucionCapilar.tsx de la web): la clasificación de su última
 * tricoscopía con la silueta, la densidad por zona con su cambio desde la
 * primera visita y el gráfico por zona (eje de tiempo real).
 *
 * UN ítem del LazyColumn de Salud (nada de listas perezosas anidadas). Sin
 * evaluaciones visibles no agrega nada: el portal de un paciente de otra
 * especialidad no cambia.
 */
internal fun LazyListScope.seccionEvolucionCapilar(datos: MiEvolucionCapilar, espacioArriba: Boolean) {
    if (!datos.tieneDatos) return
    item(key = "capilar-evolucion") {
        Column {
            if (espacioArriba) Spacer(Modifier.height(Sania.dim.sm))
            Text("🔬 MI EVOLUCIÓN CAPILAR", color = Sania.colors.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Sania.dim.md))
            TarjetaEvolucionCapilar(datos.evaluaciones)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaEvolucionCapilar(items: List<EvaluacionCapilarPortal>) {
    val c = Sania.colors
    val ultima = items.last()
    val clasificada = remember(items) { clasificacionPortal(items) }
    val clasif = clasificada?.let(::textoClasificacionPortal)
    val resumen = remember(items) { resumenDensidadPortal(items) }
    val hayMini = remember(items) { hayMiniaturizacionPortal(items) }
    var mini by rememberSaveable { mutableStateOf(false) }
    val verMini = mini && hayMini
    val zonas = remember(items, verMini) { zonasConGraficoPortal(items, verMini) }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(Sania.dim.tarjeta),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (clasificada != null) {
                SiluetaCapilar(clasificada.escala, clasificada.grado, tam = 48.dp, titulo = clasif)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                clasif?.let { Text(it, color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                Text(
                    listOfNotNull("Última evaluación: ${fechaHc(ultima.fecha)}", ultima.clinicaNombre?.let { "🏥 $it" })
                        .joinToString(" · "),
                    color = c.textoSuave, fontSize = 12.sp,
                )
            }
        }
        if (resumen.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                resumen.forEach { r ->
                    Column(
                        Modifier.widthIn(min = 120.dp).clip(RoundedCornerShape(12.dp)).background(c.chipBg)
                            .border(1.dp, c.borde, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(r.zona.corto.uppercase(), color = c.textoSuave, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text("${numTrico(r.valor)} pelos/cm²", color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        r.cambio?.let { d ->
                            Text(
                                when {
                                    d > 0 -> "↑ +${numTrico(d)} desde ${fechaHc(r.desde)}"
                                    d < 0 -> "↓ −${numTrico(-d)} desde ${fechaHc(r.desde)}"
                                    else -> "sin cambio desde ${fechaHc(r.desde)}"
                                },
                                color = if (d > 0) c.ok else if (d < 0) c.error else c.textoSuave, fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }
        if (zonas.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(if (verMini) "MINIATURIZACIÓN (%)" else "DENSIDAD (PELOS/CM²)", color = c.textoSuave, fontSize = 10.sp,
                fontWeight = FontWeight.Bold)
            if (hayMini) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChipMedida("Densidad", !verMini) { mini = false }
                    ChipMedida("Miniaturización", verMini) { mini = true }
                }
            }
            zonas.forEach { z ->
                Spacer(Modifier.height(10.dp))
                GraficoLineaHc(
                    titulo = z.nombre, unidad = if (verMini) "%" else "pelos/cm²",
                    puntos = serieCapilarPortal(items, z, verMini), color = colorZona(z), ejeTiempo = true,
                )
            }
            if (verMini) Text("Menos miniaturización = pelo más grueso y sano.", color = c.textoSuave, fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp))
        } else {
            Text("Tu gráfico aparece desde tu segunda evaluación.", color = c.textoSuave, fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun ChipMedida(texto: String, activo: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Text(texto, color = if (activo) c.sobreNavy else c.texto, fontSize = 12.sp,
        fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
            .background(if (activo) c.navy else c.superficie)
            .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp))
}
