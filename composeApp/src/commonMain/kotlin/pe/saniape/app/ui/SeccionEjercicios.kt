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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.EjercicioPaciente
import pe.saniape.app.data.PlanEjerciciosPaciente
import pe.saniape.app.data.staff.DIAS_SEMANA
import pe.saniape.app.data.staff.nombreMaterial
import pe.saniape.app.ui.theme.Sania

/**
 * 🏠 Mis ejercicios de apoyo del portal del paciente (gemelo de MisEjercicios.tsx
 * y components/ejercicios/EjerciciosPaciente.tsx de la web): lo que le dejó su
 * fisioterapeuta — animación, dosis, días e indicaciones — y el botón "ya lo hice
 * hoy", que es lo que el fisio ve como cumplimiento en la ficha.
 *
 * Solo pinta: etiqueta, vigencia, textos de la dosis y de los días, "toca hoy" y
 * "hecho hoy" llegan resueltos por el servidor y se muestran tal cual. Marcar lo
 * resuelve quien la monta ([MarcarEjercicio]).
 */

/** Marca (o desmarca) un ejercicio como hecho hoy. true = quedó guardado. */
typealias MarcarEjercicio = suspend (itemId: String, hecho: Boolean, dolor: Int?) -> Boolean

/** Una cara por cada valor de la escala de dolor (0–10), como la web. */
private val CARAS = listOf("😀", "🙂", "🙂", "😐", "😐", "😕", "😕", "😣", "😣", "😖", "😫")

/** Un plan del paciente: de cuándo es, sus indicaciones, el progreso de hoy y sus ejercicios. */
@Composable
fun PlanEjerciciosPacienteVista(plan: PlanEjerciciosPaciente, diaSemana: Int, onMarcar: MarcarEjercicio) {
    val c = Sania.colors
    val tocan = plan.ejercicios.filter { it.tocaHoy }
    val hechos = tocan.count { it.hechoHoy }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column {
            Text(plan.etiqueta, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            val detalle = listOfNotNull(plan.vigencia.ifBlank { null }, plan.indicadoPor, plan.clinica?.let { "🏥 $it" }).joinToString(" · ")
            if (detalle.isNotEmpty()) Text(detalle, color = c.textoSuave, fontSize = 12.sp)
        }

        if (plan.motivo != null || plan.indicaciones != null || plan.precauciones != null) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(Sania.dim.tarjeta),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                plan.motivo?.let { Text("Para: $it", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                plan.indicaciones?.let { Text(it, color = c.texto, fontSize = 13.sp) }
                plan.precauciones?.let {
                    Text("⚠️ $it", color = c.pend, fontSize = 13.sp, modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.pendBg).padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }

        // Progreso de HOY: de los que le tocan, cuántos ya marcó.
        if (tocan.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(horizontal = Sania.dim.tarjeta, vertical = 12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (hechos == tocan.size) "🎉 ¡Listo por hoy!"
                        else "Hoy te ${if (tocan.size == 1) "toca 1 ejercicio" else "tocan ${tocan.size} ejercicios"}",
                        color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    )
                    Text("$hechos/${tocan.size}", color = c.textoSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(Sania.dim.barraProgreso).clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.chipBg)) {
                    Box(Modifier.fillMaxWidth(hechos.toFloat() / tocan.size).height(Sania.dim.barraProgreso)
                        .clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.ok))
                }
            }
        }

        plan.ejercicios.forEachIndexed { i, e -> TarjetaEjercicioPaciente(e, i + 1, diaSemana, onMarcar) }
    }
}

/** Los 7 días con su letra: lleno el que toca, con aro el de hoy. */
@Composable
private fun DiasDeLaSemana(dias: List<Int>, hoy: Int) {
    val c = Sania.colors
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DIAS_SEMANA.forEach { d ->
            val toca = d.n in dias
            Box(
                Modifier.size(28.dp)
                    .then(if (d.n == hoy) Modifier.border(2.dp, c.lav, CircleShape).padding(3.dp) else Modifier.padding(1.dp))
                    .clip(CircleShape).background(if (toca) c.navy else c.superficie)
                    .border(1.dp, if (toca) c.navy else c.borde, CircleShape)
                    .semantics { contentDescription = d.nombre + if (toca) " (toca)" else "" },
                contentAlignment = Alignment.Center,
            ) { Text(d.letra, color = if (toca) c.sobreNavy else c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun InsigniaEjercicio(texto: String, fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color) {
    Text(texto, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp))
}

@Composable
fun TarjetaEjercicioPaciente(e: EjercicioPaciente, numero: Int, diaSemana: Int, onMarcar: MarcarEjercicio) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var abierta by rememberSaveable(e.itemId) { mutableStateOf(false) }
    var pidiendoDolor by remember(e.itemId) { mutableStateOf(false) }
    var guardando by remember(e.itemId) { mutableStateOf(false) }

    fun marcar(hecho: Boolean, dolor: Int?) {
        if (guardando) return
        guardando = true
        scope.launch {
            val ok = onMarcar(e.itemId, hecho, dolor)
            guardando = false
            if (ok) pidiendoDolor = false
        }
    }

    val extras = listOfNotNull(
        if (e.vecesAlDia > 1) "${e.vecesAlDia} veces al día" else null,
        e.lado?.let { if (it == "ambos") "Ambos lados" else "Lado $it" },
        e.carga?.let { "Con $it" },
        e.descansoSeg?.takeIf { it != 0 }?.let { "Descansa $it s entre series" },
    )
    val forma = RoundedCornerShape(Sania.shape.md.dp)
    Column(Modifier.fillMaxWidth().clip(forma).background(c.superficie).border(1.dp, if (e.hechoHoy) c.ok else c.borde, forma)) {
        // Cabecera (plegada): miniatura, nombre, dosis y si toca hoy.
        Row(Modifier.fillMaxWidth().clickable { abierta = !abierta }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MiniaturaEjercicio(e.posturaInicialUrl, e.gifUrl, Modifier.size(width = 80.dp, height = 60.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("$numero. ${e.nombre}", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (e.dosisTexto.isNotEmpty()) Text(e.dosisTexto, color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 1.dp))
                Spacer(Modifier.height(4.dp))
                when {
                    e.hechoHoy -> InsigniaEjercicio("✓ Hecho hoy", c.ok, c.okBg)
                    e.tocaHoy -> InsigniaEjercicio("Toca hoy", c.pend, c.pendBg)
                    e.diasTexto.isNotEmpty() -> InsigniaEjercicio(e.diasTexto, c.textoSuave, c.chipBg)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(if (abierta) "▴" else "▾", color = c.textoSuave, fontSize = 14.sp)
        }

        AnimatedVisibility(abierta) {
            Column {
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AnimacionEjercicio(e.gifUrl, e.posturaInicialUrl, e.nombre)

                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg)
                        .padding(horizontal = 14.dp, vertical = 12.dp)) {
                        if (e.dosisTexto.isNotEmpty()) Text(e.dosisTexto, color = c.texto, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        if (extras.isNotEmpty()) Text(extras.joinToString(" · "), color = c.texto, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                        Spacer(Modifier.height(8.dp))
                        DiasDeLaSemana(e.dias, diaSemana)
                        if (e.diasTexto.isNotEmpty()) Text(e.diasTexto, color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }

                    // Lo que el fisio escribió para ESTE paciente va antes que los pasos.
                    e.indicaciones?.let {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                            .border(1.dp, c.lav, RoundedCornerShape(Sania.shape.sm.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text("TU FISIOTERAPEUTA TE INDICA", color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                            Text(it, color = c.texto, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }

                    if (e.pasos.isNotEmpty()) {
                        Column {
                            Text("CÓMO SE HACE", color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                                modifier = Modifier.padding(bottom = 4.dp))
                            PasosEjercicio(e.pasos, tamano = 14.sp)
                        }
                    }

                    if (e.materiales.isNotEmpty()) {
                        Text("🧰 Necesitas: " + e.materiales.joinToString(", ") { nombreMaterial(it).lowercase() }, color = c.textoSuave, fontSize = 13.sp)
                    }

                    e.dolorMaximo?.let {
                        Text("⚠️ Si el dolor pasa de $it de 10, detente.", color = c.pend, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.pendBg)
                                .padding(horizontal = 12.dp, vertical = 8.dp))
                    }

                    // "Ya lo hice hoy": primero pregunta el dolor (se puede no decir); se puede deshacer.
                    when {
                        e.hechoHoy -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("✓ Hecho hoy" + (e.dolorHoy?.let { " · dolor $it/10" } ?: ""), color = c.ok, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(14.dp))
                            Text(if (guardando) "Guardando…" else "Deshacer", color = c.textoSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable(enabled = !guardando) { marcar(false, null) }.padding(4.dp))
                        }
                        pidiendoDolor -> Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(12.dp)) {
                            Text("¿Cuánto dolor sentiste al hacerlo?", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            // 0–5 arriba y 6–10 abajo: once botones no caben cómodos en una fila.
                            listOf(0..5, 6..10).forEach { fila ->
                                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    fila.forEach { n ->
                                        Column(
                                            Modifier.weight(1f).clip(RoundedCornerShape(Sania.shape.sm.dp))
                                                .border(1.5.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                                                .semantics { contentDescription = "Dolor $n de 10" }
                                                .clickable(enabled = !guardando) { marcar(true, n) }.padding(vertical = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Text(CARAS[n], fontSize = 17.sp)
                                            Text("$n", color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    // La fila de abajo tiene uno menos: se completa para que no se estiren.
                                    if (fila.last == 10) Spacer(Modifier.weight(1f))
                                }
                            }
                            Text(if (guardando) "Guardando…" else "Prefiero no decirlo", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable(enabled = !guardando) { marcar(true, null) }.padding(vertical = 4.dp))
                        }
                        else -> Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.ok)
                                .clickable { pidiendoDolor = true }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("✓ Ya lo hice hoy", color = c.sobreNavy, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}
