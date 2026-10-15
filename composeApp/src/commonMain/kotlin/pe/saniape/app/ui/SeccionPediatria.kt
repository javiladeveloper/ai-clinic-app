package pe.saniape.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.saniape.app.data.MiCrecimiento
import pe.saniape.app.data.MisVacunas
import pe.saniape.app.data.NinoCrecimientoPortal
import pe.saniape.app.data.NinoVacunasPortal
import pe.saniape.app.data.indicadoresCurvaPortal
import pe.saniape.app.data.puntosPortal
import pe.saniape.app.data.mesesCurvaPortal
import pe.saniape.app.data.staff.MESES_FIN_CORRECCION
import pe.saniape.app.data.staff.textoSemanasDias
import pe.saniape.app.data.staff.EstadoDosis
import pe.saniape.app.data.staff.TablasOms
import pe.saniape.app.data.staff.curvaReferencia
import pe.saniape.app.data.staff.numeroHc
import pe.saniape.app.data.staff.ventanaCurva
import pe.saniape.app.ui.clinica.historia.fechaHc
import pe.saniape.app.ui.clinica.pediatria.ChipPed
import pe.saniape.app.ui.clinica.pediatria.GraficoCrecimiento
import pe.saniape.app.ui.clinica.pediatria.coloresDosis
import pe.saniape.app.ui.clinica.pediatria.coloresNivel
import pe.saniape.app.ui.theme.Sania

/**
 * 👶 Crecimiento de mi hijo/a y 💉 Carné de vacunas del portal del paciente (o
 * de su apoderado), gemelos de MiCrecimiento.tsx y MisVacunas.tsx de la web.
 * Por cada niño vinculado a la cuenta: los últimos valores con su clasificación
 * y la curva de la OMS con sus puntos; el carné por edad con lo que le toca
 * ahora. Sin notas ni observaciones internas (no vienen).
 *
 * UN ítem del LazyColumn de Salud por niño (nada de listas perezosas anidadas).
 * Sin nada que mostrar no agrega nada: el portal de un adulto o de otra
 * especialidad no cambia. Las tablas de la OMS se arman SOLO si hay una curva.
 */
internal fun LazyListScope.seccionCrecimiento(datos: MiCrecimiento, espacioArriba: Boolean) {
    if (!datos.tieneDatos) return
    val varios = datos.ninos.size > 1
    datos.ninos.forEachIndexed { i, n ->
        item(key = "crecimiento-" + n.pacienteId) {
            Column {
                if (espacioArriba || i > 0) Spacer(Modifier.height(Sania.dim.sm))
                Text(
                    "👶 CRECIMIENTO DE ${if (varios) n.nombre.substringBefore(' ').uppercase() else "MI HIJO/A"}",
                    color = Sania.colors.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(Sania.dim.md))
                TarjetaCrecimiento(n)
            }
        }
    }
}

internal fun LazyListScope.seccionVacunas(datos: MisVacunas, espacioArriba: Boolean) {
    if (!datos.tieneDatos) return
    val varios = datos.ninos.size > 1
    datos.ninos.forEachIndexed { i, n ->
        item(key = "vacunas-" + n.pacienteId) {
            Column {
                if (espacioArriba || i > 0) Spacer(Modifier.height(Sania.dim.sm))
                Text(
                    "💉 CARNÉ DE VACUNAS" + if (varios) " — ${n.nombre.substringBefore(' ').uppercase()}" else "",
                    color = Sania.colors.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(Sania.dim.md))
                TarjetaVacunas(n)
            }
        }
    }
}

@Composable
private fun Modifier.tarjetaPed(): Modifier {
    val c = Sania.colors
    return this.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
        .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaCrecimiento(n: NinoCrecimientoPortal) {
    val c = Sania.colors
    val ult = n.ultima
    val ultMed = n.mediciones.lastOrNull()
    // Las tablas, solo si hay sexo (sin él no hay curva), fuera del hilo de la UI.
    var tablas by remember { mutableStateOf<TablasOms?>(null) }
    LaunchedEffect(n.sexo) { if (n.sexo != null) tablas = withContext(Dispatchers.Default) { TablasOms.cargar() } }
    val disponibles = remember(n) { indicadoresCurvaPortal(n) }
    var elegido by rememberSaveable(n.pacienteId) { mutableStateOf<String?>(null) }
    val ind = disponibles.firstOrNull { it.clave == elegido } ?: disponibles.firstOrNull()
    // La ventana se elige con la edad corregida (si el servidor la aplicó); antes del término, sin curva.
    val meses = remember(n) { mesesCurvaPortal(n) }
    val ventana = if (ind != null && meses != null) ventanaCurva(ind, meses) else null

    Column(Modifier.tarjetaPed()) {
        Text(
            listOfNotNull(
                n.nombre, "Última medición: ${ultMed?.let { fechaHc(it.fecha) } ?: "—"}",
                ult?.edadTexto?.ifBlank { null }?.let { e -> e + (ult.edadCorregidaTexto?.let { " (corregida $it)" } ?: "") },
                n.semanasAlNacer?.takeIf { n.prematuro }?.let { "🍼 nació de ${textoSemanasDias(it * 7 + (n.diasAlNacer ?: 0))}" },
                n.clinica?.let { "🏥 ${it.nombre}" },
            ).joinToString(" · "),
            color = c.textoSuave, fontSize = 12.sp,
        )
        ultMed?.let { m ->
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                m.peso?.let { DatoPed("Peso", "${numeroHc(it)} kg") }
                m.talla?.let { DatoPed("Talla", "${numeroHc(it)} cm") }
                m.perimetroCefalico?.let { DatoPed("Perímetro cefálico", "${numeroHc(it)} cm") }
                m.imc?.let { DatoPed("IMC", numeroHc(it)) }
            }
        }
        if (ult?.antesDelTermino == true) {
            Text(
                "Todavía no cumple 40 semanas de edad posmenstrual" + (ult.edadPostmenstrualTexto?.let { " ($it)" } ?: "") +
                    ": las curvas de la OMS empiezan ahí. Mientras tanto, tu pediatra lo sigue con curvas para prematuros.",
                color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp),
            )
        }
        val clasificados = ult?.indicadores.orEmpty().filter { it.clasificacion != null }
        if (clasificados.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                clasificados.forEach { i ->
                    val (fg, bg) = coloresNivel(i.clasificacion?.nivel)
                    Text("${i.nombre}: ${i.clasificacion!!.texto}", color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
        val t = tablas
        if (t != null && n.sexo != null && ind != null && ventana != null) {
            val curva = remember(t, ind, n.sexo, ventana) { curvaReferencia(t, ind, n.sexo, ventana, 80) }
            val puntos = remember(n, ind, ventana) { puntosPortal(n, ind, ventana) }
            if (curva.size > 1) {
                Text("CURVA DE LA OMS", color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                if (disponibles.size > 1) {
                    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        disponibles.forEach { i -> ChipPed(i.sigla, activo = i == ind) { elegido = i.clave } }
                    }
                }
                Spacer(Modifier.height(6.dp))
                GraficoCrecimiento(ind, curva, puntos, ventana)
                Text("La franja verde es lo esperado para su edad" +
                    (if (n.prematuro) " (por nacer prematuro, hasta los $MESES_FIN_CORRECCION meses se usa su edad corregida)" else "") +
                    ". Tu pediatra interpreta la curva en cada control.",
                    color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (n.sexo == null) {
            Text("La curva aparecerá cuando la clínica complete los datos de la ficha.", color = c.textoSuave, fontSize = 12.sp,
                modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun DatoPed(etiqueta: String, valor: String) {
    val c = Sania.colors
    Column(
        Modifier.widthIn(min = 96.dp).clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(etiqueta.uppercase(), color = c.textoSuave, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(valor, color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaVacunas(n: NinoVacunasPortal) {
    val c = Sania.colors
    val r = n.resumen
    var verCompleto by rememberSaveable(n.pacienteId) { mutableStateOf(false) }
    val (fg, bg) = when {
        r.atrasadas > 0 -> c.error to c.errorBg
        r.alDia -> c.ok to c.okBg
        else -> c.pend to c.pendBg
    }
    Column(Modifier.tarjetaPed()) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(bg).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(r.texto.ifBlank { if (r.alDia) "Al día con el esquema nacional" else "Revisa el carné" }, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("${r.aplicadas} de ${r.total} dosis", color = c.texto, fontSize = 12.sp)
        }
        n.ahora.take(6).forEach { d ->
            Text(
                "${d.vacuna} · ${d.dosis} — ${d.estadoTexto}" + (d.fechaRecomendada?.let { " (${fechaHc(it)})" } ?: ""),
                color = c.texto, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
            )
        }
        Text(
            (if (verCompleto) "▾ " else "▸ ") + "Ver el carné completo",
            color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp).heightIn(min = 44.dp).clip(RoundedCornerShape(6.dp))
                .clickable(role = Role.Button) { verCompleto = !verCompleto }.padding(vertical = 12.dp),
        )
        if (verCompleto) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                n.grupos.forEach { g ->
                    Column {
                        Text(g.uppercase(), color = c.textoSuave, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            n.dosis.filter { it.grupo == g }.forEach { d ->
                                val (cf, cb, cbo) = coloresDosis(d.estado)
                                Column(
                                    Modifier.widthIn(max = 260.dp).clip(RoundedCornerShape(Sania.shape.sm.dp)).background(cb)
                                        .border(1.dp, cbo, RoundedCornerShape(Sania.shape.sm.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
                                ) {
                                    Text((if (d.estado == EstadoDosis.APLICADA) "✓ " else "") + "${d.vacuna} · ${d.dosis}",
                                        color = cf, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(d.fechaAplicacion?.let { fechaHc(it) } ?: d.estadoTexto, color = cf, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
                if (n.otras.isNotEmpty()) {
                    Column {
                        Text("OTRAS VACUNAS", color = c.textoSuave, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                        n.otras.forEach { o ->
                            Text("✓ ${o.vacuna} · ${o.dosis} — ${fechaHc(o.fechaAplicacion)}" + (o.lugar?.let { " · $it" } ?: ""),
                                color = c.texto, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        Text("Según el Esquema Nacional de Inmunizaciones (MINSA). Lleva el carné físico a cada control.",
            color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
    }
}
