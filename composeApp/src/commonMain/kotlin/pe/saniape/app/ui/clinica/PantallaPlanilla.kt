package pe.saniape.app.ui.clinica

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.CatalogosCobroRepo
import pe.saniape.app.data.staff.FilaPlanillaApp
import pe.saniape.app.data.staff.PlanillaApp
import pe.saniape.app.data.staff.PlanillaRepo
import pe.saniape.app.data.staff.TIPOS_PERIODO_PLANILLA
import pe.saniape.app.data.staff.asistenciaDeFila
import pe.saniape.app.data.staff.dineroActivo
import pe.saniape.app.data.staff.mesCorrido
import pe.saniape.app.ui.theme.Sania

/**
 * 💵 PLANILLA (26/11/2026) — solo Admin. Ver el periodo, armarlo con la asistencia y pagar.
 * Los sueldos, los adelantos y la revisión de cada descuento se hacen en la web
 * (Asistencia → Planilla): aquí no se escribe ningún monto.
 */
@Composable
fun PantallaPlanilla(hoy: String, onCerrar: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var mes by remember { mutableStateOf(hoy.take(7)) }
    var tipo by remember { mutableStateOf("mensual") }
    var datos by remember { mutableStateOf<PlanillaApp?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var cargando by remember { mutableStateOf(true) }
    var ocupado by remember { mutableStateOf(false) }
    // A quién se le va a pagar (una fila, o todas las pendientes) y con qué.
    var porPagar by remember { mutableStateOf<List<FilaPlanillaApp>?>(null) }
    var metodos by remember { mutableStateOf<List<String>>(emptyList()) }
    var metodo by remember { mutableStateOf("Efectivo") }

    suspend fun cargar() {
        val (p, e) = PlanillaRepo.planilla(mes, tipo)
        datos = p; error = e; cargando = false
    }
    LaunchedEffect(mes, tipo) { cargando = true; cargar() }
    LaunchedEffect(Unit) {
        metodos = CatalogosCobroRepo.metodosPago().map { it.nombre }
        metodos.firstOrNull()?.let { metodo = it }
    }

    fun armar() {
        if (ocupado) return
        ocupado = true
        scope.launch {
            val r = PlanillaRepo.armar(mes, tipo)
            if (r.ok) pe.saniape.app.ui.Toaster.exito(r.mensaje) else pe.saniape.app.ui.Toaster.error(r.mensaje)
            cargar(); ocupado = false
        }
    }

    fun pagar(filas: List<FilaPlanillaApp>) {
        if (ocupado) return
        ocupado = true
        scope.launch {
            var hechos = 0
            var fallo: String? = null
            // Uno por uno: cada pago es atómico con su egreso; si uno falla, los anteriores quedan hechos.
            for (f in filas) {
                val r = PlanillaRepo.pagar(f.id, metodo)
                if (r.ok) hechos++ else { fallo = r.mensaje; break }
            }
            if (fallo == null) pe.saniape.app.ui.Toaster.exito(if (hechos == 1) "Pago registrado" else "$hechos pagos registrados")
            else pe.saniape.app.ui.Toaster.error((if (hechos > 0) "Se registraron $hechos. " else "") + fallo)
            porPagar = null
            cargar(); ocupado = false
        }
    }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = Sania.dim.lg, vertical = Sania.dim.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("←", color = c.sobreNavy, fontSize = 22.sp, modifier = Modifier.clickable { onCerrar() }.padding(end = Sania.dim.lg))
                Text("💵 Planilla", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
            }

            // Mes ‹ › y tipo de periodo.
            Row(Modifier.fillMaxWidth().padding(horizontal = Sania.dim.xl, vertical = Sania.dim.sm), verticalAlignment = Alignment.CenterVertically) {
                Text("‹", color = c.navy, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { mes = mesCorrido(mes, -1) }.padding(horizontal = Sania.dim.lg))
                Text(datos?.periodo?.ifBlank { mes } ?: mes, color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                val puedeAvanzar = mes < hoy.take(7)
                Text("›", color = if (puedeAvanzar) c.navy else c.borde, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = puedeAvanzar) { mes = mesCorrido(mes, 1) }.padding(horizontal = Sania.dim.lg))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = Sania.dim.xl), horizontalArrangement = Arrangement.spacedBy(Sania.dim.sm)) {
                TIPOS_PERIODO_PLANILLA.forEach { (valor, etiqueta) ->
                    Chip(etiqueta, activo = tipo == valor, modifier = Modifier.weight(1f)) { tipo = valor }
                }
            }
            Spacer(Modifier.height(Sania.dim.sm))

            val d = datos
            when {
                cargando && d == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.navy) }
                d == null -> Mensaje(error ?: "No se pudo leer la planilla.")
                else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = Sania.dim.xl), verticalArrangement = Arrangement.spacedBy(Sania.dim.sm)) {
                    if (d.conSueldo == 0) {
                        item { Mensaje("Primero pon el sueldo de cada persona en la web: Asistencia → Planilla → Sueldos.") }
                    } else {
                        item {
                            Tarjeta {
                                DatoPlanilla("Planilla del periodo", dineroActivo(d.totalNeto), c.texto)
                                DatoPlanilla("Ya pagado", dineroActivo(d.totalPagado), c.ok)
                                DatoPlanilla("Por pagar", dineroActivo(d.totalPendiente), if (d.totalPendiente > 0) c.pend else c.textoSuave)
                                Spacer(Modifier.height(Sania.dim.sm))
                                Boton(if (ocupado) "Un momento…" else if (d.filas.isEmpty()) "Armar la planilla" else "↻ Actualizar con la asistencia", c.navy, !ocupado) { armar() }
                                if (d.pendientes.size > 1) {
                                    Spacer(Modifier.height(Sania.dim.sm))
                                    Boton("Pagar a los ${d.pendientes.size} pendientes · ${dineroActivo(d.totalPendiente)}", c.ok, !ocupado) { porPagar = d.pendientes }
                                }
                            }
                        }
                        if (d.filas.isEmpty()) {
                            item { Mensaje("Aún no armas la planilla de este periodo. «Armar la planilla» toma la asistencia y propone los descuentos.") }
                        }
                        items(d.filas, key = { it.id }) { f ->
                            Tarjeta {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(f.nombre, color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.SemiBold)
                                        Text(listOfNotNull(f.cargo, asistenciaDeFila(f)).joinToString(" · "), color = c.textoSuave, fontSize = Sania.txt.pequeno)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(dineroActivo(f.neto), color = c.texto, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                        Text(if (f.pagado) "Pagado" + (f.metodo?.let { " · $it" } ?: "") else "Pendiente",
                                            color = if (f.pagado) c.ok else c.pend, fontSize = Sania.txt.pequeno, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                DatoPlanilla("Sueldo del periodo", dineroActivo(f.sueldoBase), c.textoSuave)
                                if (f.bonos > 0) DatoPlanilla("Bonos", dineroActivo(f.bonos), c.textoSuave)
                                if (f.descuentos > 0) DatoPlanilla("Descuentos", "− " + dineroActivo(f.descuentos), c.error)
                                if (f.adelantos > 0) DatoPlanilla("Adelantos", "− " + dineroActivo(f.adelantos), c.textoSuave)
                                f.nota?.let { Text(it, color = c.textoSuave, fontSize = 12.sp) }
                                if (!f.pagado) {
                                    Spacer(Modifier.height(Sania.dim.sm))
                                    Boton("Pagar ${dineroActivo(f.neto)}", c.ok, !ocupado) { porPagar = listOf(f) }
                                }
                            }
                        }
                        item {
                            Text("Para cambiar un descuento, poner un bono, dar un adelanto o imprimir la constancia, entra a la web: Asistencia → Planilla.",
                                color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(vertical = Sania.dim.sm))
                        }
                    }
                    item { Spacer(Modifier.height(Sania.dim.xl)) }
                }
            }
        }
    }

    porPagar?.let { filas ->
        AlertDialog(
            onDismissRequest = { if (!ocupado) porPagar = null },
            title = { Text(if (filas.size == 1) "Pagar a ${filas[0].nombre}" else "Pagar a ${filas.size} personas", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Total: ${dineroActivo(filas.sumOf { it.neto })}", fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(Sania.dim.sm))
                    Text("¿Con qué se paga?", color = c.textoSuave, fontSize = Sania.txt.pequeno)
                    Spacer(Modifier.height(4.dp))
                    metodos.ifEmpty { listOf("Efectivo") }.chunked(2).forEach { par ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(Sania.dim.sm)) {
                            par.forEach { m -> Chip(m, activo = metodo == m, modifier = Modifier.weight(1f)) { metodo = m } }
                            if (par.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    Text("Queda como egreso en Finanzas, en «Sueldos».", color = c.textoSuave, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(enabled = !ocupado, onClick = { pagar(filas) }) { Text(if (ocupado) "Registrando…" else "Registrar pago") } },
            dismissButton = { TextButton(enabled = !ocupado, onClick = { porPagar = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Tarjeta(contenido: @Composable () -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(Sania.dim.lg),
    ) { contenido() }
}

@Composable
private fun DatoPlanilla(etiqueta: String, valor: String, color: androidx.compose.ui.graphics.Color) {
    val c = Sania.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(etiqueta, color = c.textoSuave, fontSize = Sania.txt.pequeno)
        Text(valor, color = color, fontSize = Sania.txt.pequeno, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Boton(texto: String, fondo: androidx.compose.ui.graphics.Color, activo: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(if (activo) fondo else c.borde)
            .clickable(enabled = activo) { onClick() }.padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = c.sobreNavy, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold) }
}

@Composable
private fun Chip(texto: String, activo: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        modifier.clip(RoundedCornerShape(50)).background(if (activo) c.navy else c.superficie)
            .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(50)).clickable { onClick() }.padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = if (activo) c.sobreNavy else c.texto, fontSize = Sania.txt.pequeno, fontWeight = FontWeight.Bold) }
}

@Composable
private fun Mensaje(texto: String) {
    val c = Sania.colors
    Box(Modifier.fillMaxWidth().padding(Sania.dim.xl), contentAlignment = Alignment.Center) {
        Text(texto, color = c.textoSuave, fontSize = Sania.txt.cuerpo, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
