package pe.saniape.app.ui.clinica.comisiones

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.ComisionesRepo
import pe.saniape.app.data.staff.HistoricoComisiones
import pe.saniape.app.data.staff.PersonaComision
import pe.saniape.app.data.staff.ReglasComisiones
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.soles
import pe.saniape.app.ui.CargandoLista
import pe.saniape.app.ui.clinica.finanzas.ChipFin
import pe.saniape.app.ui.clinica.finanzas.RotuloFin
import pe.saniape.app.ui.clinica.finanzas.TarjetaFin
import pe.saniape.app.ui.clinica.finanzas.fechaCortaFin
import pe.saniape.app.ui.theme.Sania

/**
 * 📊 Histórico de pagos (solo Admin), gemelo de HistoricoComisiones de la web:
 * "¿cuánto pagamos en agosto?" y "¿cuánto le pagamos a Katherine este año?".
 * Junta lo liquidado en Sania con los egresos de comisión registrados a mano
 * en caja (los suma el servidor). Se puede compartir como CSV.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TabHistorico(personal: List<PersonaComision>) {
    val c = Sania.colors
    val acciones = pe.saniape.app.ui.recordarAcciones()
    val hoy = remember { hoyClinicaIso() }
    val opciones = remember(hoy) { ReglasComisiones.opcionesHistorico(hoy) }
    var periodo by remember { mutableStateOf(hoy.take(7)) }
    var terapeutaId by remember { mutableStateOf<String?>(null) }
    var origen by remember { mutableStateOf<String?>(null) }
    var datos by remember { mutableStateOf<HistoricoComisiones?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var cargando by remember { mutableStateOf(true) }
    var recarga by remember { mutableIntStateOf(0) }

    LaunchedEffect(periodo, terapeutaId, origen, recarga) {
        cargando = true
        val rango = ReglasComisiones.rangoHistorico(periodo)
        val (d, e) = ComisionesRepo.historico(rango?.first, rango?.second, terapeutaId, origen)
        cargando = false
        if (d != null) { datos = d; error = null } else error = e
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Sania.dim.lg)) {
        // Filtros
        Selector(opciones.firstOrNull { it.valor == periodo }?.etiqueta ?: periodo, opciones.map { it.valor to it.etiqueta }) { periodo = it ?: "todo" }
        Spacer(Modifier.height(Sania.dim.sm))
        Selector(
            personal.firstOrNull { it.id == terapeutaId }?.nombre ?: "Todos los profesionales",
            listOf<Pair<String?, String>>(null to "Todos los profesionales") + personal.map { it.id to it.nombre },
        ) { terapeutaId = it }
        Spacer(Modifier.height(Sania.dim.sm))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ChipFin("Todo origen", activo = origen == null) { origen = null }
            ChipFin("Liquidado en Sania", activo = origen == "sistema") { origen = "sistema" }
            ChipFin("Registrado en caja", activo = origen == "caja") { origen = "caja" }
        }
        Spacer(Modifier.height(Sania.dim.md))

        val d = datos
        when {
            cargando && d == null -> CargandoLista(filas = 3, conAvatar = false, conMargen = false)
            error != null && d == null -> Text(error ?: "", color = c.textoSuave, fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable { recarga++ }.padding(vertical = 24.dp))
            d == null || d.filas.isEmpty() -> TarjetaFin {
                Text("No hay comisiones pagadas con esos filtros.\nPrueba con otro período o quita el filtro de profesional.",
                    color = c.textoSuave, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
            }
            else -> {
                // Total del filtro + desglose por profesional
                TarjetaFin(Modifier.padding(bottom = Sania.dim.md)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            RotuloFin("Total pagado")
                            Text(soles(d.total), color = c.navy, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "${d.filas.size} pago${if (d.filas.size == 1) "" else "s"}" +
                                if (periodo != "todo") " · ${opciones.firstOrNull { it.valor == periodo }?.etiqueta ?: periodo}" else "",
                            color = c.textoSuave, fontSize = 12.sp,
                        )
                    }
                    BotonCompartir {
                        acciones.compartirArchivo(
                            nombre = "comisiones-historico-${periodo}.csv",
                            contenido = ReglasComisiones.csvHistorico(d.filas),
                            mime = "text/csv",
                            titulo = "Compartir histórico de comisiones",
                        )
                    }
                    if (d.porProfesional.size > 1) {
                        Spacer(Modifier.height(Sania.dim.sm))
                        d.porProfesional.forEach { p ->
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                Text(p.nombre, color = c.texto, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(soles(p.monto), color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Barra(if (d.total > 0) (p.monto / d.total).toFloat() else 0f, c.navy)
                        }
                    }
                }
                // Por mes: solo cuando el filtro abarca varios.
                if (d.porMes.size > 1) {
                    val tope = d.porMes.maxOf { it.monto }
                    TarjetaFin(Modifier.padding(bottom = Sania.dim.md)) {
                        RotuloFin("Por mes")
                        d.porMes.forEach { m ->
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(ReglasComisiones.nombreMes(m.mes), color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.width(110.dp))
                                Box(Modifier.weight(1f)) { Barra(if (tope > 0) (m.monto / tope).toFloat() else 0f, c.lav, alto = 12) }
                                Text(soles(m.monto), color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End, modifier = Modifier.width(96.dp))
                            }
                        }
                    }
                }
                // El detalle
                TarjetaFin {
                    RotuloFin("Detalle")
                    d.filas.forEach { f ->
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(f.profesional, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    buildString {
                                        append(fechaCortaFin(f.fecha)); append(" · "); append(f.esquema)
                                        f.nivel?.let { append(" · $it") }
                                        if (f.origen == "sistema") f.detalle?.let { append(" ($it)") }
                                    },
                                    color = c.textoSuave, fontSize = 12.sp,
                                )
                                if (f.origen == "caja") {
                                    Text("CAJA${f.metodo?.let { " · ${it.uppercase()}" } ?: ""}", color = c.textoSuave, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 2.dp).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.pill.dp)).padding(horizontal = 6.dp, vertical = 1.dp))
                                }
                            }
                            Text(soles(f.monto), color = c.ok, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Sania.dim.xl))
    }
}

@Composable
private fun Barra(fraccion: Float, color: androidx.compose.ui.graphics.Color, alto: Int = 6) {
    val fondo = Sania.colors.borde
    val f = fraccion.coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().padding(top = 3.dp).height(alto.dp).drawBehind {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(fondo, cornerRadius = r)
        if (f > 0f) drawRoundRect(color, size = Size(size.width * f, size.height), cornerRadius = r)
    })
}

@Composable
private fun BotonCompartir(onClick: () -> Unit) {
    val c = Sania.colors
    Text("⬆ Compartir CSV", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.chipBg)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp))
}

/** Caja tocable con menú desplegable (filtros del histórico). */
@Composable
private fun <T> Selector(valor: String, opciones: List<Pair<T, String>>, onElegir: (T) -> Unit) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie)
                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).clickable { abierto = true }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(valor, color = c.texto, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("▾", color = c.textoSuave, fontSize = 14.sp)
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }, modifier = Modifier.heightIn(max = 360.dp).background(c.superficie)) {
            opciones.forEach { (v, t) ->
                DropdownMenuItem(text = { Text(t, color = c.texto) }, onClick = { abierto = false; onElegir(v) })
            }
        }
    }
}

