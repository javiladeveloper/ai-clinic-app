package pe.saniape.app.ui.clinica.odontologia

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.AtencionPuntualRepo
import pe.saniape.app.data.staff.BOCA
import pe.saniape.app.data.staff.CUADRANTES_ADULTO
import pe.saniape.app.data.staff.CUADRANTES_DECIDUO
import pe.saniape.app.data.staff.ItemPuntual
import pe.saniape.app.data.staff.MapaDental
import pe.saniape.app.data.staff.OdontogramaRepo
import pe.saniape.app.data.staff.ProcedimientoRef
import pe.saniape.app.data.staff.ReciboRepo
import pe.saniape.app.data.staff.RespuestaPuntual
import pe.saniape.app.data.staff.ResultadoPuntual
import pe.saniape.app.data.staff.dineroActivo
import pe.saniape.app.data.staff.esServicioDental
import pe.saniape.app.data.staff.montoCorto
import pe.saniape.app.data.staff.nuevoUuid
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.pacientes.ChipsMetodoPago
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.clinica.pacientes.rememberMetodoPagoInicial
import pe.saniape.app.ui.theme.Sania

/**
 * Revisión completa o atención puntual: dos pestañas arriba de la ventana.
 * Las dos escriben el MISMO odontograma. Gemelo del selector de la web
 * (components/odontologia/RevisionPrevia.tsx).
 */
@Composable
fun SelectorModoAtencion(puntual: Boolean, onCambiar: (Boolean) -> Unit) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.fondo)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(3.dp),
    ) {
        listOf(false to "Revisión completa", true to "Atención puntual").forEach { (k, t) ->
            val activo = puntual == k
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape((Sania.shape.sm - 2).dp))
                    .background(if (activo) c.superficie else Color.Transparent)
                    .clickable { onCambiar(k) }.padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(t, color = if (activo) c.navy else c.textoSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

private class FilaPuntual(diente: String, procedimientoId: String, precio: String) {
    val diente = diente
    var procedimientoId by mutableStateOf(procedimientoId)
    var precio by mutableStateOf(precio)
}

private fun etiquetaPieza(d: String) = if (d == BOCA) "Boca" else d

/**
 * ATENCIÓN PUNTUAL — "vino por algo concreto". Tres preguntas: ¿qué piezas?,
 * ¿qué se hizo?, ¿alguna nota? Después, el cobro (solo con permiso de caja) y
 * el recibo. La lógica es de la web (AtencionPuntualRepo).
 * Gemelo de components/odontologia/AtencionPuntual.tsx.
 */
@Composable
fun DialogoAtencionPuntual(
    pacienteId: String,
    pacienteNombre: String?,
    citaId: String,
    mapaDental: MapaDental,
    /** Quién atendió (id), si se sabe. null = el servidor lo resuelve o lo pide. */
    terapeutaId: String?,
    /** Nombre de quien atendió, para mostrarlo. */
    atendio: String?,
    /** Motivo de la cita (notas), para tenerlo a la vista. */
    motivo: String?,
    /** Consulta/evaluación de la cita por cobrar (null = no tiene o ya se cobró). */
    costoConsulta: Double?,
    /** "Evaluación" o "Consulta": cómo se llama la consulta en la casilla. */
    tipoCita: String?,
    puedeCobrar: Boolean,
    onVolverARevision: () -> Unit,
    /** Quedó registrada: la cita ya está completada (refrescar la agenda). */
    onRegistrada: () -> Unit,
    onCerrar: () -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val acciones = pe.saniape.app.ui.recordarAcciones()

    var servicios by remember { mutableStateOf<List<ProcedimientoRef>>(emptyList()) }
    var colores by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(pacienteId) {
        servicios = OdontogramaRepo.procedimientos()
            .filter { esServicioDental(it.especialidadId, mapaDental) }
            .sortedBy { it.nombre.lowercase() }
        // Un punto en las piezas que ya tienen algo: contexto, sin ruido.
        val cat = OdontogramaRepo.catalogo().associateBy { it.id }
        colores = OdontogramaRepo.hallazgos(pacienteId).groupBy { it.diente }.mapValues { (_, hs) ->
            hs.firstOrNull { it.estado != "Realizado" }?.let { cat[it.hallazgoId]?.color } ?: "#1d6fa8"
        }
    }
    fun precioDe(id: String): String = servicios.find { it.id == id }?.precio?.takeIf { it > 0 }?.let { montoCorto(it) } ?: ""

    val filas = remember { mutableStateListOf<FilaPuntual>() }
    var temporales by remember { mutableStateOf(false) }
    var nota by remember { mutableStateOf("") }
    var cobrarConsulta by remember { mutableStateOf(true) }
    var guardando by remember { mutableStateOf(false) }
    var resultado by remember { mutableStateOf<ResultadoPuntual?>(null) }
    val idemRegistro = remember { mutableStateOf<String?>(null) }

    fun alternar(d: String) {
        if (resultado != null) return
        val i = filas.indexOfFirst { it.diente == d }
        if (i >= 0) { filas.removeAt(i); return }
        // Lo común es hacer lo mismo en varias piezas: se propone el último servicio.
        val ultimo = filas.lastOrNull()?.procedimientoId ?: ""
        filas.add(FilaPuntual(d, ultimo, if (ultimo.isNotEmpty()) precioDe(ultimo) else ""))
    }

    val consulta = costoConsulta?.takeIf { it > 0 }
    val totalServicios = filas.sumOf { it.precio.replace(',', '.').toDoubleOrNull() ?: 0.0 }
    val total = totalServicios + (if (consulta != null && cobrarConsulta) consulta else 0.0)
    val listo = filas.isNotEmpty() && filas.all { it.procedimientoId.isNotEmpty() && (it.precio.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0 }

    fun terminar() {
        if (guardando || !listo) return
        guardando = true
        val clave = idemRegistro.value ?: nuevoUuid().also { idemRegistro.value = it }
        scope.launch {
            val r = AtencionPuntualRepo.registrar(
                citaId, filas.map { ItemPuntual(it.diente, it.procedimientoId, it.precio.replace(',', '.').toDouble()) },
                terapeutaId, nota, cobrarConsulta, clave,
            )
            guardando = false
            when (r) {
                is RespuestaPuntual.Ok -> { resultado = r.valor; OdontogramaRepo.limpiarCache(); onRegistrada() }
                is RespuestaPuntual.Error -> Toaster.error(r.mensaje)
            }
        }
    }

    // ── Después de registrar: cobro y recibo ──────────────────────────────
    resultado?.let { res -> CierrePuntual(res, citaId, pacienteId, puedeCobrar, onCerrar, acciones); return }

    DialogoForm(
        titulo = "🦷 Atención puntual",
        subtitulo = pacienteNombre,
        textoAccion = if (guardando) "Guardando…" else "✓ Terminar atención",
        accionHabilitada = listo && !guardando,
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = ::terminar,
        textoCancelar = "Salir sin completar",
    ) {
        SelectorModoAtencion(puntual = true) { if (!it) onVolverARevision() }
        motivo?.takeIf { it.isNotBlank() }?.let {
            Text("Motivo: $it", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp))
        }

        // 1 · Piezas
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { EtqForm("1 · ¿Qué piezas?") }
            val boca = filas.any { it.diente == BOCA }
            Text((if (boca) "✓ " else "") + "Toda la boca", color = if (boca) c.navy else c.textoSuave, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.clickable { alternar(BOCA) }.padding(6.dp))
            Text(if (temporales) "Permanentes" else "Temporales", color = c.navy, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.clickable { temporales = !temporales }.padding(6.dp))
        }
        val cuadrantes = if (temporales) CUADRANTES_DECIDUO else CUADRANTES_ADULTO
        cuadrantes.forEachIndexed { i, q ->
            if (i == 2) Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(c.borde))
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                q.forEach { d ->
                    val sel = filas.any { it.diente == d }
                    Box(
                        Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(7.dp))
                            .background(if (sel) c.navy else c.superficie)
                            .border(1.5.dp, if (sel) c.navy else c.borde, RoundedCornerShape(7.dp))
                            .clickable { alternar(d) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(d, color = if (sel) c.sobreNavy else c.texto, fontSize = 12.sp,
                            fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.SemiBold)
                        val previo = colores[d]
                        if (previo != null && !sel) {
                            Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(5.dp).clip(CircleShape)
                                .background(colorHex(previo)))
                        }
                    }
                }
            }
        }

        if (filas.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Toca las piezas que atendiste. Lo demás del odontograma no se toca: queda sin revisar.",
                color = c.textoSuave, fontSize = 12.sp)
        } else {
            // 2 · Qué se hizo
            Spacer(Modifier.height(14.dp))
            EtqForm("2 · ¿Qué se hizo?")
            filas.forEach { f -> FilaServicio(f, servicios, onServicio = { id -> f.procedimientoId = id; precioDe(id).takeIf { it.isNotEmpty() }?.let { f.precio = it } }, onQuitar = { alternar(f.diente) }) }
            if (servicios.isEmpty()) {
                Text("No hay servicios dentales activos. Créalos en Servicios.", color = c.pend, fontSize = 12.sp)
            }

            // 3 · Nota
            Spacer(Modifier.height(10.dp))
            EtqForm("3 · Nota (opcional)")
            OutlinedTextField(
                value = nota, onValueChange = { nota = it }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                placeholder = { Text("Ej.: pulpitis en 46, se indicó analgésico.", fontSize = 13.sp) },
                colors = coloresCampoForm(), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
            )

            // Total (y la consulta, si tiene precio)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (consulta != null) {
                    Row(Modifier.weight(1f).clickable { cobrarConsulta = !cobrarConsulta }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = cobrarConsulta, onCheckedChange = { cobrarConsulta = it },
                            colors = CheckboxDefaults.colors(checkedColor = c.navy))
                        Text("Cobrar ${if (tipoCita == "Evaluación") "evaluación" else "consulta"} (${dineroActivo(consulta)})",
                            color = c.texto, fontSize = 13.sp)
                    }
                } else Spacer(Modifier.weight(1f))
                Text("Total ", color = c.textoSuave, fontSize = 13.sp)
                Text(dineroActivo(total), color = c.navy, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            if (atendio != null) "Atendió: $atendio" else "Atiende: tú (o el profesional de la cita)",
            color = c.textoSuave, fontSize = 12.sp,
        )
    }
}

@Composable
private fun FilaServicio(f: FilaPuntual, servicios: List<ProcedimientoRef>, onServicio: (String) -> Unit, onQuitar: () -> Unit) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.widthIn(min = 44.dp).clip(RoundedCornerShape(7.dp)).background(c.navy).padding(horizontal = 8.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center) {
            Text(etiquetaPieza(f.diente), color = c.sobreNavy, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie)
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).clickable { abierto = true }
                    .padding(horizontal = 10.dp, vertical = 13.dp),
            ) {
                val nombre = servicios.find { it.id == f.procedimientoId }?.nombre
                Text(nombre ?: "Elige el servicio…", color = if (nombre != null) c.texto else c.textoSuave, fontSize = 13.sp, maxLines = 1)
            }
            // Con buscador y el precio en gris (como el selector de servicio de la web).
            if (abierto) {
                pe.saniape.app.ui.clinica.DialogoElegirBuscable(
                    titulo = "Servicio · ${etiquetaPieza(f.diente)}",
                    opciones = servicios, etiqueta = { it.nombre },
                    detalle = { s -> s.precio.takeIf { it > 0 }?.let { dineroActivo(it) } },
                    onElegir = { s -> abierto = false; s?.let { onServicio(it.id) } },
                    onCerrar = { abierto = false },
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        OutlinedTextField(
            value = f.precio, onValueChange = { v -> f.precio = v.filter { it.isDigit() || it == '.' || it == ',' } },
            modifier = Modifier.width(92.dp), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, textAlign = TextAlign.End),
            colors = coloresCampoForm(),
        )
        Box(Modifier.size(44.dp).clickable { onQuitar() }, contentAlignment = Alignment.Center) {
            Text("×", color = c.textoSuave, fontSize = 20.sp)
        }
    }
}

/** Atención registrada → cobro (si tiene caja) → recibo. */
@Composable
private fun CierrePuntual(
    res: ResultadoPuntual, citaId: String, pacienteId: String, puedeCobrar: Boolean,
    onCerrar: () -> Unit, acciones: pe.saniape.app.ui.AccionesNativas,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var metodo by rememberMetodoPagoInicial(pacienteId)
    var cobrando by remember { mutableStateOf(false) }
    var cobrado by remember { mutableStateOf<Double?>(null) }
    var bajando by remember { mutableStateOf(false) }
    val idemCobro = remember { mutableStateOf<String?>(null) }
    val pendiente = res.porCobrar > 0 && cobrado == null
    val conCobro = pendiente && puedeCobrar

    fun cobrar() {
        if (cobrando || metodo.isBlank()) return
        cobrando = true
        val clave = idemCobro.value ?: nuevoUuid().also { idemCobro.value = it }
        scope.launch {
            when (val r = AtencionPuntualRepo.cobrar(citaId, metodo, clave)) {
                is RespuestaPuntual.Ok -> cobrado = r.valor
                is RespuestaPuntual.Error -> { idemCobro.value = null; Toaster.error(r.mensaje) }
            }
            cobrando = false
        }
    }
    fun recibo(formato: String) {
        if (bajando) return
        bajando = true
        scope.launch {
            val pdf = ReciboRepo.pdf("atencion", citaId, formato)
            bajando = false
            if (pdf == null) Toaster.error("No se pudo generar el recibo. Revisa tu conexión.")
            else acciones.abrirPdf("recibo-atencion-$formato.pdf", pdf)
        }
    }

    DialogoForm(
        titulo = "🦷 Atención puntual",
        subtitulo = null,
        textoAccion = when {
            conCobro && cobrando -> "Cobrando…"
            conCobro -> "Cobrar ${dineroActivo(res.porCobrar)}"
            else -> "Listo"
        },
        accionHabilitada = !cobrando,
        onCancelar = { if (!cobrando) onCerrar() },
        onAccion = { if (conCobro) cobrar() else onCerrar() },
        textoCancelar = if (conCobro) "Dejar por cobrar" else "Cerrar",
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.okBg).padding(12.dp),
        ) {
            Text("✓ Atención registrada", color = c.ok, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(res.resumen, color = c.texto, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            Text("Ya está en su odontograma como realizado.", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(12.dp))
        when {
            conCobro -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { EtqForm("Medio de pago") }
                    Text(dineroActivo(res.porCobrar), color = c.navy, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
                ChipsMetodoPago(metodo) { metodo = it }
            }
            pendiente -> Text(
                "Quedó por cobrar (${dineroActivo(res.porCobrar)}): recepción lo cobra y entrega el recibo.",
                color = c.textoSuave, fontSize = 13.sp,
            )
            cobrado != null -> {
                Text("Cobrado ${dineroActivo(cobrado)} · $metodo", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonRecibo("🧾 Recibo A4", Modifier.weight(1f)) { recibo("a4") }
                    BotonRecibo("🎫 Ticket", Modifier.weight(1f)) { recibo("ticket") }
                }
            }
        }
    }
}

@Composable
private fun BotonRecibo(texto: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie)
            .border(1.5.dp, c.ok, RoundedCornerShape(Sania.shape.sm.dp)).clickable { onClick() }.padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = c.ok, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
}

private fun colorHex(hex: String): Color = runCatching {
    val h = hex.removePrefix("#")
    Color(("FF" + h.padStart(6, '0').take(6)).toLong(16))
}.getOrDefault(Color(0xFF1D6FA8))
