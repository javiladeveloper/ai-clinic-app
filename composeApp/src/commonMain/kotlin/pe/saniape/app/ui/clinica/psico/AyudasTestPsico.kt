package pe.saniape.app.ui.clinica.psico

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.EvaluacionPsicoRepo
import pe.saniape.app.data.staff.InstrumentoPsico
import pe.saniape.app.data.staff.RespuestasTestPsico
import pe.saniape.app.data.staff.RetestPsico
import pe.saniape.app.data.staff.TestAplicadoPsico
import pe.saniape.app.data.staff.alertasVistaPrevia
import pe.saniape.app.data.staff.alternarValor
import pe.saniape.app.data.staff.etiquetaAplicacionPsico
import pe.saniape.app.data.staff.fechaDmyPsico
import pe.saniape.app.data.staff.instrumentoInicial
import pe.saniape.app.data.staff.itemsFaltantes
import pe.saniape.app.data.staff.mostrarCambioRetest
import pe.saniape.app.data.staff.muestraValorOpcion
import pe.saniape.app.data.staff.numeroPsico
import pe.saniape.app.data.staff.textoDiferenciaPsico
import pe.saniape.app.data.staff.textoItemPsico
import pe.saniape.app.data.staff.textoPuntajeEscala
import pe.saniape.app.data.staff.textoSentidoPsico
import pe.saniape.app.data.staff.valoresIniciales
import pe.saniape.app.ui.DialogoConTeclado
import pe.saniape.app.ui.theme.Sania

// Fase 3 — ayudas por test aplicado (gemelas de AyudasTest.tsx):
//  · "📝 Responder ítems" de un instrumento LIBRE (PHQ-9, GAD-7, AUDIT…): la app
//    arma los valores; el SERVIDOR calcula y llena puntajes y global (editables).
//  · "↔ Comparar con aplicación anterior": antes/después que arma el servidor.
// Los tests comerciales nunca se autocalculan (D. Leg. 822).

/** Banda roja de alertas de riesgo (PHQ-9 ítem 9, SRQ ítem 17): siempre visible, nunca solo color. */
@Composable
internal fun AlertasTestPsico(alertas: List<String>) {
    if (alertas.isEmpty()) return
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.errorBg)
            .border(2.dp, c.error, RoundedCornerShape(Sania.shape.sm.dp)).padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        alertas.forEach { Text("⚠ $it", color = c.error, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
    }
}

/** Lo que calculó el servidor con los ítems: total, subescalas y categoría (o cuántos faltan). */
@Composable
internal fun ResultadoItemsPsico(r: RespuestasTestPsico, corto: String) {
    val c = Sania.colors
    val res = r.resultado
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.fondo)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text("📝 Ítems respondidos · $corto${if (r.editado) " · corregido a mano" else ""}", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        when {
            res == null -> Text("Respuestas guardadas.", color = c.textoSuave, fontSize = 12.sp)
            !res.completo -> Text(
                "Faltan ${res.faltantes.size} ítem${if (res.faltantes.size == 1) "" else "s"} para calcular" +
                    if (res.faltantes.isNotEmpty()) " (${res.faltantes.joinToString(", ")})." else ".",
                color = c.textoSuave, fontSize = 12.sp,
            )
            // Corregido a mano: lo que vale es la tabla; del cálculo original solo se usan las alertas.
            r.editado -> Text("Los puntajes se corrigieron a mano después del cálculo: valen los de la tabla. \"Responder ítems\" de nuevo recalcula.",
                color = c.textoSuave, fontSize = 12.sp)
            else -> {
                res.total?.let { t ->
                    Text("${t.nombre}: ${textoPuntajeEscala(t)}", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                res.subescalas.forEach { s ->
                    Text("${s.nombre}: ${textoPuntajeEscala(s).ifBlank { "—" }}${s.categoria?.let { " · $it" } ?: ""}", color = c.texto, fontSize = 12.sp)
                }
                res.categoria?.let { Text(it, color = c.texto, fontSize = 13.sp) }
            }
        }
    }
}

/** Cabecera navy de los diálogos de esta pantalla. */
@Composable
private fun CabeceraDialogoPsico(titulo: String, onCerrar: () -> Unit) {
    val c = Sania.colors
    Row(Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(titulo, color = c.sobreNavy, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("✕", color = c.sobreNavy, fontSize = 18.sp, modifier = Modifier.clickable(onClick = onCerrar).padding(6.dp))
    }
}

/**
 * "📝 Responder ítems": un bloque por ítem con sus opciones (chips). Guardar
 * con ítems faltantes = guardar avance; completo = el servidor calcula y llena
 * los puntajes. En ASRS no se muestra el texto de los ítems (licencia).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DialogoResponderItems(
    test: TestAplicadoPsico,
    disponibles: List<InstrumentoPsico>,
    guardando: Boolean,
    onCerrar: () -> Unit,
    onGuardar: (InstrumentoPsico, List<Int?>) -> Unit,
) {
    val c = Sania.colors
    val previo = test.respuestas
    val inicial = remember { instrumentoInicial(disponibles, previo) } ?: return
    var ins by remember { mutableStateOf(inicial) }
    var valores by remember { mutableStateOf(valoresIniciales(ins, previo)) }
    val faltan = itemsFaltantes(ins, valores)
    val alertas = alertasVistaPrevia(ins, valores)

    DialogoConTeclado({ if (!guardando) onCerrar() }) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).heightIn(max = 760.dp)
                .clip(RoundedCornerShape(Sania.shape.lg.dp)).background(c.fondo),
        ) {
            CabeceraDialogoPsico("📝 Responder ítems · ${ins.corto}") { if (!guardando) onCerrar() }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (disponibles.size > 1) {
                    Column {
                        Text("Versión", color = c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            disponibles.forEach { d ->
                                ChipPsico(d.nombreVersion, d.id == ins.id, habilitado = !guardando) {
                                    if (d.id != ins.id) { ins = d; valores = valoresIniciales(d, previo) }
                                }
                            }
                        }
                    }
                }
                if (ins.consigna.isNotBlank()) Text(ins.consigna, color = c.texto, fontSize = 14.sp)
                if (ins.sinTextoItems) {
                    AvisoPsico("El texto de los ítems no se reproduce aquí (la licencia no permite modificar el formulario). Aplica el formulario oficial impreso y marca lo que respondió el paciente.",
                        c.pend, c.pendBg)
                }
                if (previo != null && previo.instrumento != ins.id) {
                    AvisoPsico("Cambias de versión: al guardar, se quitan de la tabla las escalas de la versión anterior (las filas que agregaste a mano quedan).",
                        c.pend, c.pendBg)
                }
                AlertasTestPsico(alertas)
                ins.items.forEachIndexed { i, it ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val marcas = listOfNotNull(if (it.inverso) "(inverso)" else null, if (!it.puntua) "(no suma)" else null,
                            if (it.opcional) "(opcional)" else null)
                        Text(
                            textoItemPsico(ins, it) + if (marcas.isNotEmpty()) "  " + marcas.joinToString(" ") else "",
                            color = c.texto, fontSize = 14.sp, fontWeight = if (ins.sinTextoItems) FontWeight.Bold else FontWeight.Normal,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ins.opcionesDe(it).forEach { o ->
                                val texto = if (muestraValorOpcion(ins, it)) "${o.texto} · ${o.valor}" else o.texto
                                ChipPsico(texto, valores.getOrNull(i) == o.valor, habilitado = !guardando) {
                                    valores = alternarValor(valores, i, o.valor)
                                }
                            }
                        }
                    }
                    if (i < ins.items.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
                }
                Text(
                    listOf("Cortes: ${ins.fuente}", ins.licencia, if (ins.poblacion.isNotBlank()) "Población: ${ins.poblacion}." else "",
                        "Un tamizaje positivo no es un diagnóstico.").filter { it.isNotBlank() && it != "Cortes: " }.joinToString(" "),
                    color = c.textoSuave, fontSize = 11.sp,
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (faltan.isEmpty()) "Completo: el servidor calcula el puntaje y llena la tabla (podrás editarla)."
                    else "Faltan ${faltan.size} ítem${if (faltan.size == 1) "" else "s"}: puedes guardar el avance.",
                    color = if (faltan.isEmpty()) c.ok else c.textoSuave, fontSize = 12.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonPsico("Cancelar", color = c.textoSuave, habilitado = !guardando, modifier = Modifier.weight(1f)) { onCerrar() }
                    BotonPsico(
                        if (guardando) "Guardando…" else if (faltan.isEmpty()) "Calcular y llenar puntajes" else "Guardar avance",
                        relleno = true, habilitado = !guardando && valores.any { it != null }, modifier = Modifier.weight(1.4f),
                    ) { onGuardar(ins, valores) }
                }
            }
        }
    }
}

/**
 * "↔ Comparar con aplicación anterior": el servidor busca las aplicaciones
 * anteriores del mismo test y paciente y arma el antes/después por escala.
 * "Mejora/empeora" solo si el servidor lo indica (mismo instrumento libre).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DialogoRetest(test: TestAplicadoPsico, onCerrar: () -> Unit) {
    val c = Sania.colors
    var elegida by remember { mutableStateOf<String?>(null) }
    var datos by remember { mutableStateOf<RetestPsico?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var cargando by remember { mutableStateOf(true) }

    LaunchedEffect(elegida) {
        cargando = true
        when (val r = EvaluacionPsicoRepo.retest(test.id, elegida)) {
            is EvaluacionPsicoRepo.Retest.Ok -> { datos = r.retest; error = null }
            is EvaluacionPsicoRepo.Retest.Error -> error = r.mensaje
        }
        cargando = false
    }

    DialogoConTeclado(onCerrar) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).heightIn(max = 760.dp)
                .clip(RoundedCornerShape(Sania.shape.lg.dp)).background(c.fondo),
        ) {
            CabeceraDialogoPsico("↔ ${test.nombreCorto}: comparar", onCerrar)
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val d = datos
                val comp = d?.comparacion
                when {
                    d == null && error != null -> Text(error!!, color = c.error, fontSize = 13.sp)
                    d == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = c.navy, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Buscando aplicaciones anteriores…", color = c.textoSuave, fontSize = 13.sp)
                    }
                    d.anteriores.isEmpty() -> Text(
                        "No hay una aplicación anterior de ${test.nombreCorto} para este paciente (solo se ven las evaluaciones en las que eres Admin o profesional tratante).",
                        color = c.textoSuave, fontSize = 13.sp,
                    )
                    else -> {
                        Column {
                            Text("Comparar con", color = c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            val actualId = elegida ?: comp?.anterior?.id
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                d.anteriores.forEach { a ->
                                    ChipPsico(etiquetaAplicacionPsico(a).ifBlank { "Aplicación anterior" }, a.id == actualId, habilitado = !cargando) {
                                        if (a.id != actualId) elegida = a.id
                                    }
                                }
                            }
                        }
                        Text(
                            "Actual: ${fechaDmyPsico(test.fecha)}" + (comp?.diasEntre?.let { " · $it días entre aplicaciones" } ?: ""),
                            color = c.texto, fontSize = 13.sp,
                        )
                        if (cargando) Text("Comparando…", color = c.textoSuave, fontSize = 12.sp)
                        error?.let { Text(it, color = c.error, fontSize = 13.sp) }
                        if (comp != null) {
                            comp.avisos.forEach { AvisoPsico("⚠ $it", c.pend, c.pendBg) }
                            if (comp.unidad.isNotBlank()) Text(comp.unidad, color = c.textoSuave, fontSize = 11.sp)
                            val conCambio = mostrarCambioRetest(comp)
                            comp.filas.forEach { f -> FilaRetest(f, conCambio) }
                            if (comp.filas.isEmpty()) Text("Sin escalas con puntajes numéricos para comparar.", color = c.textoSuave, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Una escala del antes/después (en el celular, tarjeta en vez de la tabla de la web). */
@Composable
private fun FilaRetest(f: pe.saniape.app.data.staff.FilaComparacionPsico, conCambio: Boolean) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(f.escala, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ValorRetest("Antes", f.antes, f.categoriaAntes, Modifier.weight(1f))
            ValorRetest("Después", f.despues, f.categoriaDespues, Modifier.weight(1f))
            Column(Modifier.weight(0.8f)) {
                Text("Diferencia", color = c.textoSuave, fontSize = 11.sp)
                Text(textoDiferenciaPsico(f.diferencia), color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (conCambio) {
            val s = textoSentidoPsico(f)
            val color = when (f.sentido) {
                "mejora" -> c.ok
                "empeora" -> c.error
                else -> c.textoSuave
            }
            Text(s ?: "—", color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ValorRetest(titulo: String, v: Double?, categoria: String, modifier: Modifier) {
    val c = Sania.colors
    Column(modifier) {
        Text(titulo, color = c.textoSuave, fontSize = 11.sp)
        Text(v?.let { numeroPsico(it) } ?: "—", color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (categoria.isNotBlank()) Text(categoria, color = c.textoSuave, fontSize = 11.sp)
    }
}
