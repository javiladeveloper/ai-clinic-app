package pe.saniape.app.ui.clinica.reumatologia

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.BorradorInfiltracion
import pe.saniape.app.data.staff.Infiltracion
import pe.saniape.app.data.staff.InfiltracionesRepo
import pe.saniape.app.data.staff.SUSTANCIAS_INFILTRACION
import pe.saniape.app.data.staff.anterioresInfiltracion
import pe.saniape.app.data.staff.articulacionReumaDe
import pe.saniape.app.data.staff.fechaEvalPodo
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.resumenInfiltracion
import pe.saniape.app.data.staff.validarInfiltracion
import pe.saniape.app.data.staff.vecesPorArticulacion
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.mapas.BotonMapa
import pe.saniape.app.ui.clinica.mapas.CajaErroresMapa
import pe.saniape.app.ui.clinica.mapas.ChipMapa
import pe.saniape.app.ui.clinica.mapas.EtiquetaMapa
import pe.saniape.app.ui.clinica.mapas.TarjetaMapa
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.DialogoFecha
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.theme.Sania

/**
 * 💉 Pestaña «Infiltraciones» de la ficha. Gemelo de `components/reumatologia/Infiltraciones.tsx`:
 * el mapa articular en modo ELEGIR (varias articulaciones a la vez, QA FISIOSALUD 25/11/2026),
 * el detalle del procedimiento (sustancia, dosis, guía ecográfica, EVA, lote y caducidad) y el
 * historial. Una fila por articulación, con los mismos datos.
 *
 * [citaId] = registrar dentro de esa consulta (desde la atención); null = desde la ficha.
 */

private val COLOR_ELEGIDA = Color(0xFFE11D48)
private val COLOR_HOY = Color(0xFF16A34A)
private val COLOR_ANTES = Color(0xFFF59E0B)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfiltracionesTab(pacienteId: String, puedeEditar: Boolean, citaId: String? = null, fechaCita: String? = null) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val hoy = remember { hoyClinicaIso() }
    var token by remember { mutableStateOf(0) }
    var cargando by remember(pacienteId) { mutableStateOf(true) }
    var items by remember(pacienteId) { mutableStateOf<List<Infiltracion>?>(null) }
    var error by remember(pacienteId) { mutableStateOf<String?>(null) }
    LaunchedEffect(pacienteId, token) {
        val r = InfiltracionesRepo.listar(pacienteId)
        if (r.items != null) { items = r.items; error = null } else if (items == null) error = r.error
        cargando = false
    }
    var b by remember(pacienteId) { mutableStateOf(BorradorInfiltracion()) }
    var fecha by remember(pacienteId) { mutableStateOf(fechaCita?.takeIf { it <= hoy } ?: hoy) }
    var elegirFecha by remember { mutableStateOf(false) }
    var errores by remember { mutableStateOf<List<String>>(emptyList()) }
    var guardando by remember { mutableStateOf(false) }
    var verAnterior by remember { mutableStateOf(false) }
    var confirmarBorrar by remember { mutableStateOf<Infiltracion?>(null) }

    if (cargando && items == null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(36, 280).forEach { h -> Box(Modifier.fillMaxWidth().height(h.dp).clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.chipBg)) }
        }
        return
    }
    error?.let { msg ->
        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(msg, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("↻ Reintentar", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp).clickable { error = null; cargando = true; token++ }.padding(8.dp))
        }
        return
    }
    val lista = items.orEmpty()
    val deEsta = if (citaId != null) lista.filter { it.citaId == citaId } else emptyList()
    val veces = vecesPorArticulacion(lista)
    val anteriores = b.articulaciones.flatMap { anterioresInfiltracion(lista, it, citaId) }.sortedByDescending { it.fecha }
    fun alternar(id: String) {
        b = b.copy(articulaciones = if (id in b.articulaciones) b.articulaciones - id else b.articulaciones + id)
        verAnterior = false
    }
    val colorPor: (String) -> Color? = { id ->
        when {
            id in b.articulaciones -> COLOR_ELEGIDA
            citaId != null && deEsta.any { it.articulacion == id } -> COLOR_HOY
            (veces[id] ?: 0) > 0 -> COLOR_ANTES
            else -> null
        }
    }

    fun registrar() {
        if (guardando) return
        val e = validarInfiltracion(b)
        errores = e
        if (e.isNotEmpty()) return
        guardando = true
        val n = b.articulaciones.size
        scope.launch {
            val r = InfiltracionesRepo.registrar(pacienteId, citaId, if (citaId != null) (fechaCita?.takeIf { it <= hoy } ?: hoy) else fecha, b)
            guardando = false
            if (r.registrada) {
                Toaster.exito(if (n == 1) "Infiltración registrada: ${articulacionReumaDe(b.articulaciones.first())?.nombre ?: ""}" else "$n infiltraciones registradas")
                // Lo que suele repetirse en la misma sesión (sustancia, producto, lote) se queda.
                b = b.copy(articulaciones = emptyList(), dosisMl = "", eva = null, observaciones = "")
                verAnterior = false; errores = emptyList()
                token++
            } else {
                val msg = r.rechazo?.error ?: "No se pudo registrar. Intenta de nuevo."
                errores = listOf(msg); Toaster.error(msg)
            }
        }
    }

    @Composable
    fun Fila(i: Infiltracion, conBorrar: Boolean) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                val pre = if (citaId == null || i.citaId != citaId) "${fechaEvalPodo(i.fecha)} · " else ""
                Text(pre + resumenInfiltracion(i), color = c.texto, fontSize = 13.sp)
                val extra = listOfNotNull(i.eva?.let { "EVA $it" },
                    if (i.lote != null || i.caducidad != null) "lote ${i.lote ?: "—"}${i.caducidad?.let { " (vence ${fechaEvalPodo(it)})" } ?: ""}" else null)
                if (extra.isNotEmpty()) Text(extra.joinToString(" · "), color = c.textoSuave, fontSize = 11.sp)
                i.observaciones?.let { Text(it, color = c.textoSuave, fontSize = 11.sp) }
            }
            if (conBorrar && puedeEditar) Text("✕", color = c.textoSuave, fontSize = 15.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { confirmarBorrar = i }.padding(horizontal = 10.dp, vertical = 6.dp)
                    .semantics { contentDescription = "Eliminar ${resumenInfiltracion(i)}" })
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("💉 Infiltraciones articulares", color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        TarjetaMapa {
            MapaArticular(emptyMap(), completo = true, onTocar = if (puedeEditar) ({ alternar(it) }) else null, colorPor = colorPor,
                ayuda = "Toca cada articulación que vas a infiltrar (puedes marcar varias).")
            FlowRow(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                (listOf(COLOR_ELEGIDA to "Elegidas") + (if (citaId != null) listOf(COLOR_HOY to "En esta sesión") else emptyList()) +
                    listOf(COLOR_ANTES to if (citaId != null) "Infiltrada antes" else "Con infiltraciones")).forEach { (col, t) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(col)); Spacer(Modifier.width(4.dp))
                        Text(t, color = c.textoSuave, fontSize = 11.sp)
                    }
                }
            }
            Text("Manos y pies: toca su bolita para verlos en grande (dedos, base del pulgar, mediopié).", color = c.textoSuave, fontSize = 11.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }

        if (puedeEditar) TarjetaMapa {
            EtiquetaMapa("Articulaciones seleccionadas" + if (b.articulaciones.size > 1) " (${b.articulaciones.size})" else "")
            if (b.articulaciones.isEmpty()) Text("Toca en el mapa (puedes marcar varias)", color = c.textoSuave, fontSize = 13.sp)
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                b.articulaciones.forEach { a ->
                    Text("${articulacionReumaDe(a)?.nombre ?: a}  ✕", color = COLOR_ELEGIDA, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(COLOR_ELEGIDA.copy(alpha = 0.08f))
                            .border(1.dp, COLOR_ELEGIDA.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                            .clickable { alternar(a) }.padding(horizontal = 10.dp, vertical = 8.dp))
                }
            }
            if (b.articulaciones.size > 1) Text("Se registra una infiltración por articulación, con la misma sustancia, dosis y datos de abajo.",
                color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            if (b.articulaciones.isNotEmpty() && anteriores.isNotEmpty()) {
                Text(if (verAnterior) "Ocultar" else "Ver infiltración anterior (${anteriores.size})", color = c.navy, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp).clickable { verAnterior = !verAnterior }.padding(vertical = 6.dp))
                if (verAnterior) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(c.chipBg).padding(horizontal = 10.dp)) {
                    anteriores.take(5).forEach { Fila(it, conBorrar = false) }
                }
            }

            EtiquetaMapa("Sustancia aplicada")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SUSTANCIAS_INFILTRACION.forEach { s -> ChipMapa(s, b.sustancia == s) { b = b.copy(sustancia = s) } }
            }
            OutlinedTextField(b.producto, { b = b.copy(producto = it.take(120)) }, singleLine = true,
                label = { Text(if (b.sustancia == "Otra") "¿Cuál? (obligatorio)" else "Producto o marca (opcional)") },
                colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth().padding(top = 6.dp))

            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(b.dosisMl, { b = b.copy(dosisMl = it.take(6)) }, singleLine = true, label = { Text("Dosis (ml)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = coloresCampoForm(), modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Row(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { b = b.copy(eco = !b.eco) }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(b.eco, { b = b.copy(eco = it) })
                    Text("Guía ecográfica", color = c.texto, fontSize = 13.sp)
                }
            }

            EtiquetaMapa("Dolor (EVA)" + (b.eva?.let { " · $it/10" } ?: ""))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                (0..10).forEach { n ->
                    val activo = b.eva == n
                    Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (activo) c.navy else c.superficie).border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(8.dp))
                        .clickable { b = b.copy(eva = if (activo) null else n) }.semantics { contentDescription = "Dolor: $n de 10" },
                        contentAlignment = Alignment.Center) { Text("$n", color = if (activo) c.sobreNavy else c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(b.lote, { b = b.copy(lote = it.take(60)) }, singleLine = true, label = { Text("Lote") },
                    colors = coloresCampoForm(), modifier = Modifier.weight(1f))
                Column(Modifier.weight(1f)) {
                    EtiquetaMapa("Caducidad")
                    CajaSelectorForm(b.caducidad.takeIf { it.isNotBlank() }?.let(::fechaEvalPodo) ?: "—") { elegirFecha = true }
                }
            }
            if (citaId == null) {
                EtiquetaMapa("Fecha")
                var elegirFechaProc by remember { mutableStateOf(false) }
                CajaSelectorForm(fechaEvalPodo(fecha)) { elegirFechaProc = true }
                if (elegirFechaProc) DialogoFecha(onElegir = { if (it <= hoy) fecha = it; elegirFechaProc = false }, onCerrar = { elegirFechaProc = false }, inicial = fecha)
            }
            OutlinedTextField(b.observaciones, { b = b.copy(observaciones = it.take(2000)) }, label = { Text("Observaciones (opcional)") },
                minLines = 2, colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            CajaErroresMapa(errores)
            Spacer(Modifier.height(8.dp))
            val n = b.articulaciones.size
            BotonMapa(if (guardando) "Registrando…" else if (n > 1) "＋ Registrar $n infiltraciones" else "＋ Registrar infiltración",
                lleno = true, habilitado = !guardando && n > 0 && !(b.sustancia == "Otra" && b.producto.isBlank())) { registrar() }
        }

        if (citaId != null && deEsta.isNotEmpty()) TarjetaMapa {
            EtiquetaMapa("En esta sesión (${deEsta.size})")
            deEsta.forEach { Fila(it, conBorrar = true) }
        }
        TarjetaMapa {
            val historial = if (citaId != null) lista.filter { it.citaId != citaId } else lista
            EtiquetaMapa("Historial (${historial.size})")
            if (historial.isEmpty()) Text("Aún no tiene infiltraciones registradas.", color = c.textoSuave, fontSize = 13.sp)
            else historial.forEach { Fila(it, conBorrar = citaId == null) }
        }
    }

    if (elegirFecha) DialogoFecha(onElegir = { b = b.copy(caducidad = it); elegirFecha = false }, onCerrar = { elegirFecha = false },
        inicial = b.caducidad.takeIf { it.isNotBlank() })
    confirmarBorrar?.let { i ->
        AlertDialog(
            onDismissRequest = { confirmarBorrar = null },
            title = { Text("¿Eliminar la infiltración?") },
            text = { Text(resumenInfiltracion(i)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmarBorrar = null
                    scope.launch {
                        val r = InfiltracionesRepo.borrar(i.id)
                        if (r.registrada) { Toaster.exito("Infiltración eliminada"); token++ } else Toaster.error(r.rechazo?.error ?: "No se pudo eliminar")
                    }
                }) { Text("Eliminar", color = c.error) }
            },
            dismissButton = { TextButton(onClick = { confirmarBorrar = null }) { Text("Cancelar") } },
        )
    }
}
