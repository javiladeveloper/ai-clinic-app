package pe.saniape.app.ui.clinica.comisiones

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import pe.saniape.app.data.staff.AvanceComision
import pe.saniape.app.data.staff.CatalogosCobroRepo
import pe.saniape.app.data.staff.ComisionesRepo
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.EsquemaComision
import pe.saniape.app.data.staff.EsquemasComision
import pe.saniape.app.data.staff.MetodoPago
import pe.saniape.app.data.staff.PersonaComision
import pe.saniape.app.data.staff.ReglasComisiones
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.soles
import pe.saniape.app.tutoriales.PantallaTutorial
import pe.saniape.app.ui.CargandoLista
import pe.saniape.app.ui.Gestion
import pe.saniape.app.ui.ManejarAtras
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.EstadoVacio
import pe.saniape.app.ui.clinica.equipo.escribir
import pe.saniape.app.ui.clinica.finanzas.AvisoFin
import pe.saniape.app.ui.clinica.finanzas.BotonFin
import pe.saniape.app.ui.clinica.finanzas.ChipFin
import pe.saniape.app.ui.clinica.finanzas.ConfirmarFin
import pe.saniape.app.ui.clinica.finanzas.TarjetaFin
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.theme.Sania

private enum class TabCom(val titulo: String) { Esquemas("⚙ Esquemas"), Historico("📊 Histórico de pagos") }

/**
 * Más → 💰 Comisiones, NATIVO (antes abría /comisiones en el navegador). Gemelo
 * de la web: pestaña Esquemas (pirámides por niveles y esquemas por porcentaje,
 * con el avance de cada persona, pagar, anular y el detalle de qué contó) y
 * pestaña Histórico de pagos (Admin).
 *
 * Permiso `comisiones` (lo decide el padre) y plan con comisiones (si no, el
 * mismo aviso de plan que la web). El Admin configura y paga; cualquier otro
 * con el permiso ve SOLO su nivel, sin montos — lo filtra el servidor.
 *
 * El módulo viejo (reglas % por sesión y su lista de comisiones) está oculto
 * también en la web (COMISIONES_EN_CONSTRUCCION): no se trae.
 */
@Composable
fun PantallaComisiones(ctx: ContextoStaff, onSalir: () -> Unit) = PantallaTutorial("Comisiones") {
    val c = Sania.colors
    ManejarAtras(activo = true, onAtras = onSalir)
    var tab by remember { mutableStateOf(TabCom.Esquemas) }
    // El histórico es solo del Admin (el endpoint responde 403 a los demás).
    val tabs = if (ctx.esAdmin) listOf(TabCom.Esquemas, TabCom.Historico) else listOf(TabCom.Esquemas)

    // El personal activo: para asignar a un esquema y para filtrar el histórico.
    var personal by remember { mutableStateOf<List<PersonaComision>>(emptyList()) }
    LaunchedEffect(ctx.clinicaId) { if (ctx.esAdmin) personal = ComisionesRepo.personalActivo() }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("← Más", color = c.sobreNavy, fontSize = Sania.txt.pequeno,
                        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).clickable(onClick = onSalir).padding(vertical = 2.dp))
                    Spacer(Modifier.height(2.dp))
                    Text("💰 Comisiones", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
                    Text("Esquemas y pagos por profesional", color = c.sobreNavy.copy(alpha = 0.7f), fontSize = 12.sp)
                }
                pe.saniape.app.ui.tutoriales.BotonAyuda("Comisiones")
            }
            if (!ctx.can("comisiones")) {
                Box(Modifier.fillMaxSize().padding(Sania.dim.lg)) {
                    EstadoVacio("🔒", "Comisiones es una función Premium",
                        "Define esquemas de comisión por niveles o por porcentaje, y paga a tus profesionales con un toque. Disponible en el plan Premium.")
                }
                return@Column
            }
            if (tabs.size > 1) {
                // Pestañas (subrayado con drawBehind, sin medidas intrínsecas).
                Row(Modifier.fillMaxWidth().background(c.superficie).horizontalScroll(rememberScrollState())) {
                    tabs.forEach { t ->
                        val activo = tab == t
                        val colorLinea = c.navy
                        Text(
                            t.titulo, color = if (activo) c.navy else c.textoSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { tab = t }
                                .drawBehind {
                                    if (activo) drawLine(colorLinea, Offset(0f, size.height - 1.5.dp.toPx()), Offset(size.width, size.height - 1.5.dp.toPx()), strokeWidth = 3.dp.toPx())
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    TabCom.Esquemas -> TabEsquemas(ctx, personal)
                    TabCom.Historico -> TabHistorico(personal)
                }
            }
        }
    }
}

/** Colores de los niveles, en el orden de la pirámide (como la web). */
@Composable
internal fun colorNivel(i: Int): Color {
    val c = Sania.colors
    return listOf(c.teal, c.info, c.navy, c.purple)[i % 4]
}

/** Chips para elegir qué mes mirar (en curso y los dos anteriores). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectorPeriodo(valor: String?, rangos: List<Pair<String, String>>, onCambiar: (String?) -> Unit) {
    val c = Sania.colors
    val opciones = remember { ReglasComisiones.mesesElegibles(hoyClinicaIso()) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        opciones.forEach { o -> ChipFin(o.etiqueta, activo = o.valor == valor) { onCambiar(o.valor) } }
    }
    // Si todos los esquemas cuentan el mismo rango se dice una vez acá.
    if (rangos.size == 1) {
        Text("Período: ${ReglasComisiones.rangoHumano(rangos[0].first, rangos[0].second)}",
            color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun TabEsquemas(ctx: ContextoStaff, personal: List<PersonaComision>) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var periodoSel by remember { mutableStateOf<String?>(null) }
    var recarga by remember { mutableIntStateOf(0) }
    var datos by remember { mutableStateOf<EsquemasComision?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var recargando by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf<EsquemaComision?>(null) }
    var nuevo by remember { mutableStateOf(false) }
    var pagando by remember { mutableStateOf<Pair<EsquemaComision, AvanceComision>?>(null) }
    var anulando by remember { mutableStateOf<Triple<String, String, Double>?>(null) }   // (pagoId, nombre, monto)
    var quitando by remember { mutableStateOf<EsquemaComision?>(null) }
    var ocupado by remember { mutableStateOf(false) }

    LaunchedEffect(ctx.clinicaId, periodoSel, recarga) {
        recargando = true
        val (d, e) = ComisionesRepo.esquemas(periodoSel)
        recargando = false
        if (d != null) { datos = d; error = null }
        else if (datos == null) error = e
        else Toaster.error(e ?: "No se pudo cargar ese período")
    }

    val d = datos
    if (d == null) {
        if (error != null) {
            Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "", color = c.textoSuave, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(Sania.dim.md))
                    BotonFin("Reintentar") { error = null; recarga++ }
                }
            }
        } else CargandoLista(filas = 3, conAvatar = false)
        return
    }

    // ── Diálogos ──
    if (nuevo || editando != null) {
        DialogoEsquema(
            esquema = editando, personal = personal,
            onCerrar = { nuevo = false; editando = null },
            onGuardado = { nuevo = false; editando = null; recarga++ },
        )
    }
    pagando?.let { (p, a) ->
        DialogoPagar(p, a, onCerrar = { if (!ocupado) pagando = null }) { metodo ->
            if (ocupado) return@DialogoPagar
            ocupado = true
            scope.launch {
                try {
                    val r = escribir(porDefecto = "No se pudo pagar") { ComisionesRepo.pagar(p.id, a, metodo, d.periodoRef) }
                    if (r.registrada) {
                        val nivel = (r.cuerpo?.get("nivel") as? JsonPrimitive)?.contentOrNull ?: "Comisión"
                        val enCaja = (r.cuerpo?.get("enCaja") as? JsonPrimitive)?.booleanOrNull != false
                        Toaster.exito(if (enCaja) "$nivel pagado ($metodo) y registrado en caja" else "$nivel pagado (revisa la caja del día)")
                        pagando = null
                        recarga++
                    } else if (r.rechazo?.status == 409) {
                        // Ya se pagó (otro toque u otra persona): se refresca para verlo.
                        pagando = null
                        recarga++
                    }
                } finally { ocupado = false }
            }
        }
    }
    anulando?.let { (pagoId, nombre, monto) ->
        ConfirmarFin(
            titulo = "Anular pago",
            detalle = "Vas a anular el pago de ${soles(monto)} a $nombre. Se quita también el egreso de la caja, y el período vuelve a quedar abierto para pagarlo de nuevo.",
            textoAccion = "Sí, anular",
            onCancelar = { anulando = null },
        ) {
            if (ocupado) return@ConfirmarFin
            ocupado = true
            anulando = null
            scope.launch {
                try {
                    val r = escribir(Gestion.ELIMINANDO, "No se pudo anular") { ComisionesRepo.anular(pagoId) }
                    if (r.registrada) Toaster.exito("Pago anulado y quitado de la caja")
                    recarga++
                } finally { ocupado = false }
            }
        }
    }
    quitando?.let { p ->
        ConfirmarFin(
            titulo = "¿Quitar \"${p.nombre}\"?",
            detalle = "Los profesionales asignados dejarán de acumular. Los pagos ya hechos se conservan en el histórico.",
            textoAccion = "Quitar",
            onCancelar = { quitando = null },
        ) {
            if (ocupado) return@ConfirmarFin
            ocupado = true
            quitando = null
            scope.launch {
                try {
                    val r = escribir(Gestion.ELIMINANDO, "No se pudo quitar") { ComisionesRepo.quitar(p.id) }
                    if (r.registrada) { Toaster.exito("Esquema quitado"); recarga++ }
                } finally { ocupado = false }
            }
        }
    }

    val rangos = ReglasComisiones.rangosDistintos(d.plantillas)
    val rangoEnCadaTarjeta = rangos.size > 1
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Sania.dim.lg)
            .alpha(if (recargando) 0.6f else 1f),
    ) {
        if (!d.esAdmin) {
            // ── Vista del PROFESIONAL: su nivel en cada esquema, sin montos ──
            if (d.plantillas.none { it.avances.isNotEmpty() }) {
                EstadoVacio("💰", "Todavía no estás en ningún esquema",
                    "Cuando la clínica te asigne un esquema de comisión, aquí verás tu nivel y cuánto te falta para el siguiente.")
                return@Column
            }
            SelectorPeriodo(periodoSel, rangos) { periodoSel = it }
            Spacer(Modifier.height(Sania.dim.md))
            d.plantillas.forEach { p ->
                p.avances.forEach { a ->
                    TarjetaFin(Modifier.padding(bottom = Sania.dim.md)) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(p.nombre.uppercase(), color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Text(ReglasComisiones.titularProfesional(p, a), color = c.navy, fontSize = 32.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 6.dp))
                            Text(ReglasComisiones.lineaProfesional(p, a, d.esPeriodoActual), color = c.textoSuave, fontSize = 13.sp, textAlign = TextAlign.Center)
                            if (rangoEnCadaTarjeta) Text("Período: ${ReglasComisiones.rangoHumano(p.desde, p.hasta)}", color = c.textoSuave, fontSize = 11.sp)
                        }
                        if (p.tipo == "tramos") Piramide(p.tramos, a.logrado, a.nivelActual)
                        // En un mes cerrado "te falta" ya no se puede alcanzar.
                        if (a.siguienteNivel != null && d.esPeriodoActual) {
                            Text(
                                "Te ${if (a.faltanParaSiguiente == 1) "falta 1" else "faltan ${a.faltanParaSiguiente}"} para ${a.siguienteNivel}",
                                color = c.texto, fontSize = 13.sp, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            )
                        }
                        if (a.siguienteNivel == null && a.nivelActual != null) {
                            Text("¡Llegaste al nivel máximo! 🏆", color = c.ok, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                        }
                        if (p.unidad != "evaluaciones" && a.terapeutaId != null) {
                            DetallePiramide(p.id, a.terapeutaId!!, periodoSel, a.paquetesQueCuentan)
                        }
                    }
                }
            }
            return@Column
        }

        // ── Vista del ADMIN ──
        Text("Esquemas de comisión", color = c.texto, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("Define el esquema una vez y asígnalo a quien corresponda.", color = c.textoSuave, fontSize = 12.sp)
        Spacer(Modifier.height(Sania.dim.md))
        BotonFin("+ Nuevo esquema", Modifier.fillMaxWidth()) { nuevo = true }
        Spacer(Modifier.height(Sania.dim.md))
        if (d.plantillas.isEmpty()) {
            TarjetaFin {
                Text(
                    "Todavía no hay esquemas. Crea uno con los niveles que paga tu clínica (por ejemplo Bronce / Plata / Oro / Diamante) y asígnalo a tus profesionales.",
                    color = c.textoSuave, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                )
            }
            return@Column
        }
        SelectorPeriodo(periodoSel, rangos) { periodoSel = it }
        Spacer(Modifier.height(Sania.dim.md))

        d.plantillas.forEach { p ->
            TarjetaEsquemaAdmin(
                p = p, periodoSel = periodoSel, rangoEnCadaTarjeta = rangoEnCadaTarjeta, ocupado = ocupado,
                onEditar = { editando = p },
                onQuitar = { quitando = p },
                onPagar = { a -> pagando = p to a },
                onAnular = { a -> a.ultimoPago?.let { u -> anulando = Triple(u.id, a.nombre, u.monto) } },
            )
        }

        val total = ReglasComisiones.totalAPagar(d.plantillas)
        if (total > 0) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.okBg).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Total por pagar ${if (d.esPeriodoActual) "este período" else "de ese período"}:",
                    color = c.textoSuave, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(soles(total), color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(Sania.dim.xl))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaEsquemaAdmin(
    p: EsquemaComision,
    periodoSel: String?,
    rangoEnCadaTarjeta: Boolean,
    ocupado: Boolean,
    onEditar: () -> Unit,
    onQuitar: () -> Unit,
    onPagar: (AvanceComision) -> Unit,
    onAnular: (AvanceComision) -> Unit,
) {
    val c = Sania.colors
    TarjetaFin(Modifier.padding(bottom = Sania.dim.md)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(p.nombre, color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(ReglasComisiones.subtituloEsquema(p), color = c.textoSuave, fontSize = 12.sp)
                if (rangoEnCadaTarjeta) Text("Período: ${ReglasComisiones.rangoHumano(p.desde, p.hasta)}", color = c.textoSuave, fontSize = 11.sp)
            }
            Text("Editar", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onEditar).padding(6.dp))
            Text("Quitar", color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onQuitar).padding(6.dp))
        }
        Spacer(Modifier.height(8.dp))
        if (p.tipo != "tramos") {
            Text(
                if (p.tipo == "porcentaje") "Acumula un porcentaje de cada paquete pagado. Sin niveles ni objetivo." else "Bono único al alcanzar el objetivo.",
                color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        if (p.tramos.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                p.tramos.forEachIndexed { i, t ->
                    val color = colorNivel(i)
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(c.fondo).padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.padding(end = 5.dp).width(8.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
                        Text(t.nombre, color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("  ${numero(t.objetivo)} → ${soles(t.monto_bono ?: 0.0)}", color = c.textoSuave, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (p.avances.isEmpty()) {
            Text("Sin profesionales asignados. Usa Editar para agregarlos.", color = c.textoSuave, fontSize = 12.sp)
        }
        p.avances.forEach { a ->
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(c.fondo).padding(10.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(a.nombre, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(ReglasComisiones.lineaAvance(p, a), color = c.textoSuave, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(ReglasComisiones.titularAvance(p, a), color = c.navy, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(ReglasComisiones.montoAvance(p, a), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = if (a.pagado == null && (a.aCobrar ?: 0.0) > 0) c.ok else c.textoSuave)
                        // Que sea automático no puede ser invisible.
                        if (a.liquidacion == "egreso") {
                            Text("sale sola · ${a.metodoPago ?: "Efectivo"}", color = c.teal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (p.tipo == "tramos") Piramide(p.tramos, a.logrado, a.nivelActual)
                if (p.unidad != "evaluaciones" && a.terapeutaId != null) {
                    DetallePiramide(p.id, a.terapeutaId!!, periodoSel, a.paquetesQueCuentan)
                }
                a.ultimoPago?.let { u ->
                    Text(ReglasComisiones.textoAnular(u), color = c.textoSuave, fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.End).padding(top = 4.dp).clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = !ocupado) { onAnular(a) }.padding(4.dp))
                }
                // A quien se le liquida SOLA no se le paga desde acá: se pagaría dos veces.
                if (a.liquidacion == "egreso") {
                    Text("Se le paga desde Finanzas · egresos por comisión. Acá no se paga para no duplicarlo.",
                        color = c.textoSuave, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                } else if (ReglasComisiones.puedePagar(a)) {
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                        BotonFin("Pagar ${soles(a.aCobrar ?: 0.0)}", color = c.ok, habilitado = !ocupado) { onPagar(a) }
                    }
                }
            }
        }
    }
}

private fun numero(n: Double): String = if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()

/** "Pagar comisión": el monto lo recalcula el servidor; acá se elige con qué se paga. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogoPagar(p: EsquemaComision, a: AvanceComision, onCerrar: () -> Unit, onPagar: (String) -> Unit) {
    val c = Sania.colors
    var metodos by remember { mutableStateOf<List<MetodoPago>>(emptyList()) }
    var metodo by remember { mutableStateOf("Efectivo") }
    LaunchedEffect(Unit) { metodos = CatalogosCobroRepo.metodosPago() }
    val monto = a.aCobrar ?: 0.0
    DialogoForm(
        titulo = "Pagar comisión",
        subtitulo = p.nombre,
        textoAccion = "Pagar ${soles(monto)}",
        onCancelar = onCerrar,
        onAccion = { onPagar(metodo) },
    ) {
        Text(
            "Vas a pagar ${soles(monto)} a ${a.nombre}" +
                (a.nivelActual?.let { " por alcanzar $it" } ?: "") +
                " en ${p.nombre} (período ${ReglasComisiones.rangoHumano(p.desde, p.hasta)}). Se registra como egreso en la caja de hoy.",
            color = c.texto, fontSize = 14.sp,
        )
        Spacer(Modifier.height(Sania.dim.md))
        EtqForm("Forma de pago")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val nombres = metodos.map { it.nombre }.ifEmpty { ReglasComisiones.METODOS_BASE }
            nombres.forEach { m -> ChipFin(m, activo = metodo == m) { metodo = m } }
        }
        if (a.liquidacion != "egreso" && a.metodoPago != null && a.metodoPago != metodo) {
            Spacer(Modifier.height(Sania.dim.sm))
            AvisoFin("Lo pactado con ${a.nombre}: ${a.metodoPago}.", c.info, c.infoBg)
        }
    }
}
