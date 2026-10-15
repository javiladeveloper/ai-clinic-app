package pe.saniape.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import pe.saniape.app.data.DatoMedicionPortal
import pe.saniape.app.data.MedicionPortal
import pe.saniape.app.data.MiNutricion
import pe.saniape.app.data.PlanNutricionPortal
import pe.saniape.app.data.datosUltimaMedicion
import pe.saniape.app.data.htmlPlanPortal
import pe.saniape.app.data.metricasGraficoPortal
import pe.saniape.app.data.serieNutricionPortal
import pe.saniape.app.data.staff.MetricaMedicion
import pe.saniape.app.data.staff.horaPlan
import pe.saniape.app.data.staff.macrosDePlan
import pe.saniape.app.data.staff.textoPct
import pe.saniape.app.ui.clinica.historia.GraficoLineaHc
import pe.saniape.app.ui.theme.Sania

/**
 * 🥗 Mi plan de alimentación + ⚖️ Mi evolución del portal del paciente (gemelo
 * de MiNutricion.tsx de la web). El plan vigente completo; los anteriores,
 * plegados. "Descargar PDF" arma la hoja en la app y la abre en el visor con
 * impresión (desde ahí se guarda en PDF o se comparte).
 *
 * Se agrega como ítems del LazyColumn de la pantalla Salud (nada de listas
 * perezosas anidadas). Sin planes ni mediciones no agrega nada: el portal de un
 * paciente de otra especialidad no cambia.
 */
internal fun LazyListScope.seccionNutricion(
    datos: MiNutricion,
    espacioArriba: Boolean,
    verAnteriores: Boolean,
    onAlternarAnteriores: () -> Unit,
    onDescargar: (PlanNutricionPortal) -> Unit,
) {
    if (!datos.tieneDatos) return
    val vigente = datos.planes.firstOrNull()
    val anteriores = datos.planes.drop(1)
    if (vigente != null) {
        item(key = "nutri-etiqueta") {
            Column {
                if (espacioArriba) Spacer(Modifier.height(Sania.dim.sm))
                EtiquetaNutricion("🥗 MI PLAN DE ALIMENTACIÓN")
            }
        }
        item(key = "nutri-plan-" + vigente.id) {
            TarjetaPlanNutricion(vigente, onDescargar = { onDescargar(vigente) })
        }
        if (anteriores.isNotEmpty()) {
            item(key = "nutri-anteriores") {
                CabeceraPlanesAnteriores(anteriores.size, verAnteriores, onAlternarAnteriores)
            }
            if (verAnteriores) {
                items(anteriores, key = { "nutri-plan-" + it.id }) { p ->
                    TarjetaPlanAnterior(p, onDescargar = { onDescargar(p) })
                }
            }
        }
    }
    if (datos.mediciones.isNotEmpty()) {
        item(key = "nutri-evolucion") {
            Column {
                if (espacioArriba || vigente != null) Spacer(Modifier.height(Sania.dim.sm))
                EtiquetaNutricion("⚖️ MI EVOLUCIÓN")
                Spacer(Modifier.height(Sania.dim.md))
                TarjetaEvolucion(datos.mediciones)
            }
        }
    }
}

/** Abre la hoja del plan en el visor de documentos (impresión / PDF / compartir). */
internal fun descargarPlanNutricion(acciones: AccionesNativas, p: PlanNutricionPortal) {
    acciones.abrirHtml(htmlPlanPortal(p), "Plan alimentario")
}

@Composable
private fun EtiquetaNutricion(t: String) {
    Text(t, color = Sania.colors.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
}

/** Tarjeta blanca con borde, como las demás del portal. */
@Composable
private fun Modifier.tarjetaNutricion(): Modifier {
    val c = Sania.colors
    return this.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
        .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
}

/** El plan vigente: todo a la vista. */
@Composable
private fun TarjetaPlanNutricion(p: PlanNutricionPortal, onDescargar: () -> Unit) {
    Column(Modifier.tarjetaNutricion().padding(Sania.dim.tarjeta)) {
        CabeceraPlan(p, onDescargar)
        DetallePlan(p)
    }
}

/** Un plan anterior: plegado (fecha y objetivo); tocar lo abre. */
@Composable
private fun TarjetaPlanAnterior(p: PlanNutricionPortal, onDescargar: () -> Unit) {
    val c = Sania.colors
    var abierto by rememberSaveable(p.id) { mutableStateOf(false) }
    Column(Modifier.tarjetaNutricion()) {
        Column(
            Modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = if (abierto) "Ocultar plan" else "Ver plan") { abierto = !abierto }
                .padding(Sania.dim.tarjeta),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Plan del ${fechaDMA(p.plan.fecha)}", color = c.texto, fontSize = 14.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (abierto) "▲" else "▼", color = c.textoSuave, fontSize = 12.sp)
            }
            p.plan.objetivo?.let {
                Text("🎯 $it", color = c.textoSuave, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        AnimatedVisibility(abierto) {
            Column(Modifier.padding(start = Sania.dim.tarjeta, end = Sania.dim.tarjeta, bottom = Sania.dim.tarjeta)) {
                HorizontalDivider(color = c.borde)
                Spacer(Modifier.height(Sania.dim.sm))
                CabeceraPlan(p, onDescargar)
                DetallePlan(p)
            }
        }
    }
}

@Composable
private fun CabeceraPlan(p: PlanNutricionPortal, onDescargar: () -> Unit) {
    val c = Sania.colors
    Column(Modifier.fillMaxWidth()) {
        Text("Plan del ${fechaDMA(p.plan.fecha)}", color = c.texto, fontSize = Sania.txt.seccion,
            fontWeight = FontWeight.Bold)
        p.profesional?.let { Text("Indicado por $it", color = c.textoSuave, fontSize = 12.sp) }
        p.clinica?.nombre?.let { nombre ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val logo = p.clinica.logoUrl?.takeIf { it.startsWith("https://") }
                if (logo != null) {
                    AsyncImage(
                        model = logo, contentDescription = nombre, contentScale = ContentScale.Fit,
                        modifier = Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(nombre, color = c.textoSuave, fontSize = 12.sp)
                } else {
                    Text("🏥 $nombre", color = c.textoSuave, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(Sania.dim.sm))
        Box(
            Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                .border(1.5.dp, c.lav, RoundedCornerShape(Sania.shape.sm.dp))
                .clickable(role = Role.Button, onClick = onDescargar)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("📄 Descargar PDF", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetallePlan(p: PlanNutricionPortal) {
    val c = Sania.colors
    val plan = p.plan
    val macros = macrosDePlan(plan)
    Column(Modifier.fillMaxWidth().padding(top = Sania.dim.md), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        plan.objetivo?.let {
            Text("🎯 Tu objetivo: $it", color = c.texto, fontSize = 14.sp)
        }
        if (plan.kcal != null || macros != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                plan.kcal?.let { ChipNutricion("🔥 $it kcal al día", c.teal, c.tealBg, negrita = true) }
                macros?.forEach { m ->
                    ChipNutricion("${m.nombre} ${textoPct(m.pct)} %${m.gramos?.let { " · $it g" }.orEmpty()}",
                        c.texto, c.chipBg)
                }
            }
        }
        if (plan.comidas.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)),
            ) {
                plan.comidas.forEachIndexed { i, comida ->
                    if (i > 0) HorizontalDivider(color = c.borde)
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(comida.tiempo.uppercase(), color = c.navy, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            horaPlan(comida.hora)?.let { Text(" · $it", color = c.textoSuave, fontSize = 11.sp) }
                        }
                        // Los alimentos con su cantidad, SIN nutrientes (§13); debajo, el texto libre.
                        // (Las comidas ya vienen de comidasParaMostrar: el detalle automático no se repite.)
                        comida.items.forEach { it ->
                            Text(
                                androidx.compose.ui.text.buildAnnotatedString {
                                    append("• ${it.nombre} ")
                                    pushStyle(androidx.compose.ui.text.SpanStyle(color = c.textoSuave))
                                    append("— ${pe.saniape.app.data.staff.textoCantidad(it)}")
                                    pop()
                                },
                                color = c.texto, fontSize = 14.sp, lineHeight = 19.sp,
                            )
                        }
                        if (comida.detalle.isNotEmpty()) Text(comida.detalle, color = c.texto, fontSize = 14.sp, lineHeight = 19.sp)
                    }
                }
            }
        }
        listOf("✅ Recomendaciones" to plan.recomendaciones, "🚫 Evita" to plan.evitar, "💧 Hidratación" to plan.hidratacion)
            .filter { !it.second.isNullOrBlank() }
            .forEach { (titulo, texto) ->
                Column {
                    Text(titulo.uppercase(), color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(texto.orEmpty(), color = c.texto, fontSize = 14.sp, lineHeight = 19.sp)
                }
            }
    }
}

@Composable
private fun ChipNutricion(texto: String, color: androidx.compose.ui.graphics.Color, fondo: androidx.compose.ui.graphics.Color, negrita: Boolean = false) {
    Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(fondo).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(texto, color = color, fontSize = 12.sp, fontWeight = if (negrita) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun CabeceraPlanesAnteriores(cantidad: Int, abierto: Boolean, onAlternar: () -> Unit) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
            .clickable(role = Role.Button, onClick = onAlternar).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${if (abierto) "▾" else "▸"} Planes anteriores ($cantidad)", color = c.navy,
            fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** ⚖️ Última medición + gráfico de peso / IMC (desde la segunda toma). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaEvolucion(mediciones: List<MedicionPortal>) {
    val c = Sania.colors
    val ultima = mediciones.lastOrNull() ?: return
    val datos = datosUltimaMedicion(mediciones)
    val opciones = metricasGraficoPortal(mediciones)
    var metricaNombre by rememberSaveable { mutableStateOf(MetricaMedicion.PESO.name) }
    val elegida = opciones.firstOrNull { it.name == metricaNombre } ?: opciones.firstOrNull()

    Column(Modifier.tarjetaNutricion().padding(Sania.dim.tarjeta)) {
        Text("Tu última medición: ${fechaDMA(ultima.medicion.fecha)}", color = c.textoSuave, fontSize = 12.sp)
        if (datos.isNotEmpty()) {
            Spacer(Modifier.height(Sania.dim.sm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 2) {
                datos.forEach { DatoMedicion(it, Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(Sania.dim.md))
        if (elegida == null) {
            Text("Tu gráfico aparece desde tu segunda medición.", color = c.textoSuave, fontSize = 12.sp)
        } else {
            if (opciones.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    opciones.forEach { m ->
                        val activa = m == elegida
                        Box(
                            Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                                .background(if (activa) c.navy else c.chipBg)
                                .clickable(role = Role.Tab) { metricaNombre = m.name }
                                .semantics { contentDescription = "Ver ${m.nombre}" }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(m.nombre, color = if (activa) c.sobreNavy else c.textoSuave,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(Sania.dim.sm))
            }
            GraficoLineaHc(
                titulo = if (elegida == MetricaMedicion.PESO) "Peso (kg)" else "IMC",
                unidad = elegida.unidad,
                puntos = serieNutricionPortal(mediciones, elegida),
            )
        }
    }
}

@Composable
private fun DatoMedicion(d: DatoMedicionPortal, modifier: Modifier) {
    val c = Sania.colors
    Column(
        modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(d.etiqueta.uppercase(), color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(d.valor, color = c.texto, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        d.detalle?.let { Text(it, color = c.textoSuave, fontSize = 11.sp) }
    }
}
