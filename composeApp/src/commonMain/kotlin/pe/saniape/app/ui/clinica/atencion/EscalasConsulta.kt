package pe.saniape.app.ui.clinica.atencion

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import pe.saniape.app.data.staff.EscalaAplicadaApp
import pe.saniape.app.data.staff.InstrumentoPsico
import pe.saniape.app.data.staff.MAX_ESCALAS
import pe.saniape.app.data.staff.alternarValor
import pe.saniape.app.data.staff.aplicarEscalaLocal
import pe.saniape.app.data.staff.muestraValorOpcion
import pe.saniape.app.data.staff.sanearValores
import pe.saniape.app.data.staff.textoEscala
import pe.saniape.app.data.staff.textoItemPsico
import pe.saniape.app.ui.DialogoConTeclado
import pe.saniape.app.ui.clinica.psico.AlertasTestPsico
import pe.saniape.app.ui.clinica.psico.AvisoPsico
import pe.saniape.app.ui.clinica.psico.ChipPsico
import pe.saniape.app.ui.theme.Sania

// ─────────────────────────────────────────────────────────────────────────────
// "📋 Escalas aplicadas" de la consulta médica (psiquiatría). Gemelo de
// components/atencion-medica/EscalasConsulta.tsx: PHQ-9, GAD-7, ASRS, AUDIT…
// aplicadas DENTRO de la atención. Mismas definiciones que la evaluación
// psicológica (GET /instrumentos). El puntaje que se ve es orientativo: al
// guardar la atención el servidor lo recalcula desde las respuestas.
// ─────────────────────────────────────────────────────────────────────────────

/** Una escala en edición: el instrumento, sus respuestas y su índice en la lista (null = nueva). */
private data class EscalaEnEdicion(val ins: InstrumentoPsico, val valores: List<Int?>, val indice: Int?)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EscalasConsulta(
    valor: List<EscalaAplicadaApp>,
    instrumentos: List<InstrumentoPsico>?,
    soloLectura: Boolean,
    /** Sexo de la filiación (cortes del AUDIT-C). */
    sexo: String?,
    onReintentarInstrumentos: () -> Unit,
    onChange: (List<EscalaAplicadaApp>) -> Unit,
) {
    val c = Sania.colors
    var abierta by remember { mutableStateOf<EscalaEnEdicion?>(null) }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("📋 Escalas aplicadas", color = c.navy, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("Tamizaje con autocálculo. Un resultado positivo no es un diagnóstico.", color = c.textoSuave, fontSize = 11.sp)
        if (valor.isEmpty()) {
            Text("Ninguna en esta atención.", color = c.textoSuave, fontSize = 13.sp)
        } else {
            valor.forEachIndexed { i, e ->
                Column(Modifier.fillMaxWidth()) {
                    Text(textoEscala(e), color = c.texto, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!e.completo) Insignia("Incompleta", c.pend, c.pendBg)
                        if (!soloLectura) {
                            val ins = instrumentos?.firstOrNull { it.id == e.instrumento }
                            if (ins != null) {
                                Text("Editar", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { abierta = EscalaEnEdicion(ins, sanearValores(ins, e.valores), i) }
                                        .padding(vertical = 6.dp))
                            }
                            Text("Quitar", color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onChange(valor.filterIndexed { j, _ -> j != i }) }.padding(vertical = 6.dp))
                        }
                    }
                }
            }
        }
        if (!soloLectura && valor.size < MAX_ESCALAS) {
            when {
                instrumentos == null -> Text(
                    "No se pudieron cargar las escalas. Toca para reintentar.", color = c.navy, fontSize = 12.sp,
                    modifier = Modifier.clickable(onClick = onReintentarInstrumentos).padding(vertical = 4.dp),
                )
                else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    instrumentos.forEach { ins ->
                        ChipPsico("+ ${ins.corto}", activo = false) {
                            abierta = EscalaEnEdicion(ins, ins.items.map { null }, null)
                        }
                    }
                }
            }
        }
    }

    abierta?.let { ed ->
        DialogoEscala(
            ed = ed, sexo = sexo,
            onCerrar = { abierta = null },
            onGuardar = { vista ->
                val lista = if (ed.indice == null) valor + vista else valor.mapIndexed { j, e -> if (j == ed.indice) vista else e }
                onChange(lista.take(MAX_ESCALAS))
                abierta = null
            },
        )
    }
}

/** Los ítems con sus opciones (chips) y, abajo, el puntaje orientativo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogoEscala(
    ed: EscalaEnEdicion,
    sexo: String?,
    onCerrar: () -> Unit,
    onGuardar: (EscalaAplicadaApp) -> Unit,
) {
    val c = Sania.colors
    val ins = ed.ins
    var valores by remember { mutableStateOf(ed.valores) }
    val vista = aplicarEscalaLocal(ins, valores, sexo)

    DialogoConTeclado(onCerrar) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).heightIn(max = 760.dp)
                .clip(RoundedCornerShape(Sania.shape.lg.dp)).background(c.fondo),
        ) {
            Row(Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(ins.nombre.ifBlank { ins.corto }, color = c.sobreNavy, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                Text("✕", color = c.sobreNavy, fontSize = 18.sp, modifier = Modifier.clickable(onClick = onCerrar).padding(6.dp))
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (ins.consigna.isNotBlank()) Text(ins.consigna, color = c.texto, fontSize = 14.sp)
                if (ins.sinTextoItems) {
                    AvisoPsico("El texto de los ítems no se reproduce aquí (la licencia no permite modificar el formulario). " +
                        "Aplica el formulario oficial impreso y marca lo que respondió el paciente.", c.pend, c.pendBg)
                }
                AlertasTestPsico(vista.alertas)
                ins.items.forEachIndexed { i, it ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(textoItemPsico(ins, it) + if (!it.puntua) "  (no suma)" else "",
                            color = c.texto, fontSize = 14.sp, fontWeight = if (ins.sinTextoItems) FontWeight.Bold else FontWeight.Normal)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ins.opcionesDe(it).forEach { o ->
                                val texto = if (muestraValorOpcion(ins, it)) "${o.texto} · ${o.valor}" else o.texto
                                ChipPsico(texto, valores.getOrNull(i) == o.valor) { valores = alternarValor(valores, i, o.valor) }
                            }
                        }
                    }
                    if (i < ins.items.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
                }
                Text(listOf(ins.fuente, ins.licencia).filter { it.isNotBlank() }.joinToString(" "), color = c.textoSuave, fontSize = 11.sp)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
            Column(Modifier.fillMaxWidth().background(c.superficie).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(textoEscala(vista), color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Puntaje orientativo: se recalcula en el servidor al guardar la atención.", color = c.textoSuave, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { BotonChico("Cancelar", c.textoSuave, c.superficie, borde = c.borde, onClick = onCerrar) }
                    Box(Modifier.weight(1.4f)) {
                        BotonChico(if (ed.indice == null) "Agregar a la atención" else "Guardar", c.sobreNavy, c.navy,
                            habilitado = valores.any { it != null }) { onGuardar(vista) }
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}
