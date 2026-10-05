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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import pe.saniape.app.data.staff.AtencionRepo
import pe.saniape.app.data.staff.DatosConsultaApp
import pe.saniape.app.data.staff.DiagnosticoCie
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.theme.Sania

// ─────────────────────────────────────────────────────────────────────────────
// PASO "DIAGNÓSTICO" de la consulta guiada (gemelo de DiagnosticosCie10.tsx):
// uno o varios diagnósticos con su código CIE-10 y su tipo P/D/R (NTS 139,
// 4.2.9). El índice CIE-10 vive en el servidor (/api/staff/cie10): la app no
// carga los 940 KB. Sin conexión se puede escribir el diagnóstico "sin código".
// ─────────────────────────────────────────────────────────────────────────────

/** Tope de diagnósticos por atención (MAX_DIAGNOSTICOS de lib/cie10.ts). */
internal const val MAX_DIAGNOSTICOS = 8

/** Tipos de diagnóstico (TIPOS_DIAGNOSTICO de la web): valor, nombre, ayuda. */
private val TIPOS_DIAGNOSTICO = listOf(
    Triple("P", "Presuntivo", "Sospecha clínica, falta confirmar"),
    Triple("D", "Definitivo", "Confirmado por clínica o exámenes"),
    Triple("R", "Repetido", "Definitivo ya registrado antes, en control"),
)

@Composable
internal fun PasoDiagnostico(vm: AtencionViewModel, d: DatosConsultaApp, soloLectura: Boolean) {
    EditorDiagnosticosCie(
        lista = vm.borrador.diagnosticos,
        onLista = { vm.diagnosticos(it) },
        soloLectura = soloLectura,
        dental = d.flags.dental,
    )
}

/**
 * El buscador CIE-10 con la lista de diagnósticos elegidos (P/D/R, ↑, ✕). Lo usa
 * la consulta guiada y la impresión diagnóstica de la evaluación psicológica:
 * un solo buscador en toda la app.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditorDiagnosticosCie(
    lista: List<DiagnosticoCie>,
    onLista: (List<DiagnosticoCie>) -> Unit,
    soloLectura: Boolean,
    dental: Boolean = false,
    /** La nota de arriba (la de la consulta cita la NTS 139). */
    nota: String = "CIE-10 con tipo: Presuntivo, Definitivo o Repetido (NTS 139, 4.2.9). Sin siglas.",
) {
    val c = Sania.colors
    val lleno = lista.size >= MAX_DIAGNOSTICOS

    var q by remember { mutableStateOf("") }
    var resultados by remember { mutableStateOf<List<DiagnosticoCie>>(emptyList()) }
    var frecuentesRubro by remember { mutableStateOf<List<DiagnosticoCie>>(emptyList()) }
    var buscando by remember { mutableStateOf(false) }
    val consulta = q.trim()
    val editable = !soloLectura && !lleno
    // Los frecuentes del rubro (el servidor los manda con q vacío): una vez.
    LaunchedEffect(dental, editable) {
        if (editable && frecuentesRubro.isEmpty()) frecuentesRubro = AtencionRepo.buscarCie10("", dental)
    }
    // Búsqueda con debounce de 300 ms (cada letra nueva cancela la anterior).
    LaunchedEffect(consulta, dental, editable) {
        resultados = emptyList()
        if (!editable || consulta.length < 2) { buscando = false; return@LaunchedEffect }
        buscando = true
        delay(300)
        resultados = AtencionRepo.buscarCie10(consulta, dental)
        buscando = false
    }

    fun yaTiene(codigo: String?) = codigo != null && lista.any { it.codigo.equals(codigo, ignoreCase = true) }

    fun agregar(dx: DiagnosticoCie?, textoLibre: String? = null) {
        if (soloLectura || lleno) return
        if (dx != null && yaTiene(dx.codigo)) { q = ""; return }
        // Por defecto Presuntivo (lo más prudente); el profesional lo cambia.
        val nuevo = dx?.copy(tipo = "P") ?: DiagnosticoCie(codigo = null, descripcion = textoLibre.orEmpty().trim(), tipo = "P")
        if (nuevo.descripcion.isBlank()) return
        onLista(lista + nuevo)
        q = ""
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(nota, color = c.textoSuave, fontSize = 12.sp)

        // ── Los elegidos ──
        if (lista.isEmpty() && soloLectura) {
            Text("Sin diagnóstico registrado.", color = c.textoSuave, fontSize = 13.sp)
        }
        lista.forEachIndexed { i, dx ->
            FilaDiagnostico(
                n = i + 1, dx = dx, soloLectura = soloLectura, puedeSubir = i > 0,
                onTipo = { t -> onLista(lista.mapIndexed { j, x -> if (j == i) x.copy(tipo = t) else x }) },
                onSubir = {
                    val copia = lista.toMutableList()
                    val tmp = copia[i]; copia[i] = copia[i - 1]; copia[i - 1] = tmp
                    onLista(copia)
                },
                onQuitar = { onLista(lista.filterIndexed { j, _ -> j != i }) },
            )
        }
        if (!soloLectura && lleno) {
            Text("Máximo $MAX_DIAGNOSTICOS diagnósticos por atención.", color = c.pend, fontSize = 12.sp)
        }

        // ── Buscador ──
        if (editable) {
            OutlinedTextField(
                value = q,
                onValueChange = { q = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text(
                        if (lista.isNotEmpty()) "Agregar otro diagnóstico (código o palabras)…"
                        else "Busca por código (J06.9) o por palabras (faringitis aguda)…",
                        fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingIcon = if (buscando) {
                    { CircularProgressIndicator(Modifier.size(18.dp), color = c.navy, strokeWidth = 2.dp) }
                } else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Search),
                colors = coloresCampoForm(),
                shape = RoundedCornerShape(Sania.shape.sm.dp),
            )

            if (consulta.length < 2) {
                val frecuentes = frecuentesRubro.filter { !yaTiene(it.codigo) }
                if (frecuentes.isNotEmpty()) {
                    Text("FRECUENTES", color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        frecuentes.forEach { f -> ChipCie(f) { agregar(f) } }
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie),
                ) {
                    resultados.forEach { r ->
                        val tiene = yaTiene(r.codigo)
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { agregar(r) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(r.codigo.orEmpty(), color = if (tiene) c.textoSuave else c.navy, fontSize = 13.sp,
                                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.widthIn(min = 56.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(r.descripcion, color = if (tiene) c.textoSuave else c.texto, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
                    }
                    if (resultados.isEmpty() && !buscando) {
                        Text("Sin coincidencias en la CIE-10.", color = c.textoSuave, fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
                    }
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { agregar(null, consulta) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text("+ Escribir “$consulta” sin código (codificarlo después)", color = c.textoSuave, fontSize = 12.sp)
                    }
                }
            }
        }

        Text(
            "P = presuntivo · D = definitivo · R = repetido (ya diagnosticado antes). CIE-10: tabla oficial de SUSALUD.",
            color = c.textoSuave, fontSize = 11.sp,
        )
    }
}

/** Un diagnóstico elegido: n°, código (o "sin código"), descripción, P/D/R, ↑ y ✕. */
@Composable
private fun FilaDiagnostico(
    n: Int,
    dx: DiagnosticoCie,
    soloLectura: Boolean,
    puedeSubir: Boolean,
    onTipo: (String) -> Unit,
    onSubir: () -> Unit,
    onQuitar: () -> Unit,
) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    Column(
        Modifier.fillMaxWidth().clip(forma).border(1.dp, c.borde, forma).background(c.superficie)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$n.", color = c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            val codigo = dx.codigo
            if (codigo != null) {
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(c.chipBg).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text(codigo, color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            } else {
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(c.pendBg).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text("sin código", color = c.pend, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.weight(1f))
            if (!soloLectura) {
                if (puedeSubir) {
                    Box(
                        Modifier.size(36.dp).clip(forma).clickable(onClick = onSubir).semantics { contentDescription = "Subir" },
                        contentAlignment = Alignment.Center,
                    ) { Text("↑", color = c.textoSuave, fontSize = 16.sp) }
                }
                Box(
                    Modifier.size(36.dp).clip(forma).clickable(onClick = onQuitar).semantics { contentDescription = "Quitar diagnóstico" },
                    contentAlignment = Alignment.Center,
                ) { Text("✕", color = c.error, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(dx.descripcion, color = c.texto, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        // Selector P / D / R (segmentado).
        Row(Modifier.clip(forma).border(1.dp, c.borde, forma)) {
            TIPOS_DIAGNOSTICO.forEachIndexed { i, (valor, nombre, _) ->
                val activo = dx.tipo == valor
                if (i > 0) Box(Modifier.width(1.dp).height(34.dp).background(c.borde))
                Box(
                    Modifier.heightIn(min = 34.dp).background(if (activo) c.navy else c.superficie)
                        .clickable(enabled = !soloLectura) { onTipo(valor) }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .semantics { contentDescription = nombre },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$valor · $nombre", color = if (activo) c.sobreNavy else c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ChipCie(dx: DiagnosticoCie, onClick: () -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.pill.dp)
    Row(
        Modifier.heightIn(min = 32.dp).widthIn(max = 320.dp).clip(forma).border(1.dp, c.borde, forma)
            .background(c.superficie).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(dx.codigo.orEmpty(), color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.width(5.dp))
        Text(dx.descripcion, color = c.texto, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
