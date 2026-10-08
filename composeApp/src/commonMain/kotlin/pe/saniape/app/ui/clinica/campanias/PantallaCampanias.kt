package pe.saniape.app.ui.clinica.campanias

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardOptions
import pe.saniape.app.data.staff.CampaniaGestion
import pe.saniape.app.data.staff.CampaniasRepo
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.EstadoCampania
import pe.saniape.app.data.staff.FormCampania
import pe.saniape.app.data.staff.RegaloForm
import pe.saniape.app.data.staff.ServicioApp
import pe.saniape.app.data.staff.ServiciosRepo
import pe.saniape.app.data.staff.TIPOS_CAMPANIA
import pe.saniape.app.data.staff.cuerpoGuardarCampania
import pe.saniape.app.data.staff.estadoCampania
import pe.saniape.app.data.staff.etiquetaCampania
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.problemaFormCampania
import pe.saniape.app.ui.AlertaConTeclado
import pe.saniape.app.ui.CargandoLista
import pe.saniape.app.ui.Gestion
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.EstadoVacio
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.DialogoFecha
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.TarjetaForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.conIndicador
import pe.saniape.app.ui.fechaDMA
import pe.saniape.app.ui.theme.Sania

/**
 * 🎉 Campañas (Más → Administración). Gemelo de app/(app)/campanias en la web:
 * promociones con vigencia (paquete, precio de oferta, % o monto de descuento),
 * a qué servicios o citas aplican, regalos, activar/desactivar, eliminar y el
 * aviso por notificación a los pacientes con la app. Con permiso "servicios"
 * (lo decide el padre; el servidor valida igual).
 */
@Composable
fun PantallaCampanias(ctx: ContextoStaff, onSalir: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val hoy = remember { hoyClinicaIso() }
    var lista by remember { mutableStateOf<List<CampaniaGestion>?>(null) }
    var servicios by remember { mutableStateOf<List<ServicioApp>>(emptyList()) }
    var fallo by remember { mutableStateOf(false) }
    var recarga by remember { mutableIntStateOf(0) }
    var formAbierto by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf<CampaniaGestion?>(null) }
    var porEliminar by remember { mutableStateOf<CampaniaGestion?>(null) }
    var porAvisar by remember { mutableStateOf<CampaniaGestion?>(null) }
    var confirmarATodos by remember { mutableStateOf<CampaniaGestion?>(null) }
    var avisando by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ctx.clinicaId, recarga) {
        try {
            lista = CampaniasRepo.listar()
            fallo = false
            if (servicios.isEmpty()) servicios = runCatching { ServiciosRepo.listar() }.getOrDefault(emptyList())
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            if (lista == null) fallo = true
        }
    }

    fun alternar(cp: CampaniaGestion) {
        val nuevo = !cp.activo
        scope.launch {
            val r = conIndicador(Gestion.ACTUALIZANDO) { CampaniasRepo.cambiarEstado(cp.id, nuevo) }
            if (r.registrada) {
                Toaster.exito(if (nuevo) "Campaña activada" else "Campaña desactivada")
                lista = lista?.map { if (it.id == cp.id) it.copy(activo = nuevo) else it }
                recarga++
            } else Toaster.error(r.rechazo?.error ?: "No se pudo cambiar el estado")
        }
    }

    fun eliminar(cp: CampaniaGestion) {
        scope.launch {
            val r = conIndicador { CampaniasRepo.eliminar(cp.id) }
            if (r.registrada) {
                Toaster.exito("Campaña eliminada")
                lista = lista?.filter { it.id != cp.id }
                recarga++
            } else Toaster.error(r.rechazo?.error ?: "No se pudo eliminar la campaña")
        }
    }

    fun avisar(cp: CampaniaGestion, segmento: String) {
        avisando = cp.id
        scope.launch {
            val (res, error) = try { conIndicador { CampaniasRepo.avisar(cp.id, segmento) } } finally { avisando = null }
            if (res == null) Toaster.error(error ?: "No se pudo enviar el aviso")
            else if (res.enviados > 0) {
                Toaster.exito("Aviso enviado a ${res.enviados} paciente${if (res.enviados == 1) "" else "s"} con la app (${res.sinApp} sin la app no lo recibieron)")
            } else Toaster.info("Ninguno de esos pacientes tiene la app todavía — el aviso no llegó a nadie")
        }
    }

    porEliminar?.let { cp ->
        AlertaConTeclado(
            onDismissRequest = { porEliminar = null },
            title = { Text("¿Eliminar \"${cp.nombre}\"?") },
            text = { Text("Esto no afecta a los tratamientos ya creados con ella.") },
            confirmButton = {
                TextButton(onClick = { porEliminar = null; eliminar(cp) }) { Text("Eliminar", color = c.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { porEliminar = null }) { Text("Cancelar", color = c.textoSuave) } },
        )
    }

    // Aviso: dos pasos como la web (primero "solo inactivos 60 días", si no, "todos").
    porAvisar?.let { cp ->
        AlertaConTeclado(
            onDismissRequest = { porAvisar = null },
            title = { Text("Avisar \"${cp.nombre}\"") },
            text = {
                Text(
                    "Se envía una notificación a tus pacientes que tienen la app. " +
                        "Máximo 1 aviso promocional por semana. ¿A quiénes?",
                )
            },
            // Opciones apiladas (tres botones en fila no caben en pantallas angostas).
            confirmButton = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { porAvisar = null; avisar(cp, "inactivos") }) {
                        Text("Solo a los que no vienen hace 60 días", color = c.navy, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { porAvisar = null; confirmarATodos = cp }) { Text("A todos mis pacientes con la app", color = c.navy) }
                    TextButton(onClick = { porAvisar = null }) { Text("Cancelar", color = c.textoSuave) }
                }
            },
        )
    }
    // Avisar a TODOS pide una segunda confirmación, como la web.
    confirmarATodos?.let { cp ->
        AlertaConTeclado(
            onDismissRequest = { confirmarATodos = null },
            title = { Text("¿Avisar a TODOS?") },
            text = { Text("Se enviará \"${cp.nombre}\" a todos tus pacientes que tienen la app, no solo a los inactivos.") },
            confirmButton = {
                TextButton(onClick = { confirmarATodos = null; avisar(cp, "todos") }) { Text("Sí, avisar a todos", color = c.navy, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmarATodos = null }) { Text("Cancelar", color = c.textoSuave) } },
        )
    }

    if (formAbierto) {
        FormularioCampania(
            inicial = editando,
            servicios = servicios.filter { it.activo },
            hoy = hoy,
            onCerrar = { formAbierto = false },
            onGuardado = { formAbierto = false; recarga++ },
        )
    }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "← Más", color = c.sobreNavy, fontSize = Sania.txt.pequeno,
                        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).clickable { onSalir() }.padding(vertical = 2.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text("Campañas", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
                    Text("Promociones y descuentos", color = c.sobreNavy.copy(alpha = 0.75f), fontSize = Sania.txt.mini)
                }
                Box(
                    Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.navy)
                        .clickable(enabled = lista != null) { editando = null; formAbierto = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text("+ Nueva", color = c.sobreNavy, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }

            val actual = lista
            when {
                actual == null && fallo -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No se pudieron cargar las campañas. Revisa tu conexión.", color = c.textoSuave, fontSize = 13.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(Sania.dim.md))
                        Box(
                            Modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.navy)
                                .clickable { fallo = false; recarga++ }.padding(horizontal = 20.dp, vertical = 10.dp),
                        ) { Text("Reintentar", color = c.sobreNavy, fontWeight = FontWeight.Bold) }
                    }
                }
                actual == null -> CargandoLista(filas = 4, conAvatar = false)
                else -> {
                    val vigentes = actual.count { estadoCampania(it, hoy) == EstadoCampania.VIGENTE }
                    val nombrePorServicio = remember(servicios) { servicios.associate { it.id to it.nombre } }
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = Sania.dim.lg),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            Text(
                                (if (vigentes > 0) "$vigentes campaña${if (vigentes == 1) "" else "s"} vigente${if (vigentes == 1) "" else "s"} ahora mismo." else "No tienes campañas vigentes.") +
                                    " El bot de WhatsApp puede ofrecer las vigentes como gancho.",
                                color = c.textoSuave, fontSize = Sania.txt.pequeno, modifier = Modifier.padding(top = Sania.dim.md),
                            )
                        }
                        if (actual.isEmpty()) {
                            item {
                                EstadoVacio(
                                    emoji = "🎉", titulo = "Aún no hay campañas",
                                    subtitulo = "Crea un paquete promocional, un precio de oferta o un descuento por temporada.",
                                    textoAccion = "+ Crear la primera", onAccion = { editando = null; formAbierto = true },
                                )
                            }
                        } else {
                            items(actual, key = { it.id }) { cp ->
                                TarjetaCampania(
                                    cp = cp, hoy = hoy, nombrePorServicio = nombrePorServicio, avisando = avisando == cp.id,
                                    onAvisar = { porAvisar = cp },
                                    onEditar = { editando = cp; formAbierto = true },
                                    onAlternar = { alternar(cp) },
                                    onEliminar = { porEliminar = cp },
                                )
                            }
                        }
                        item { Spacer(Modifier.height(Sania.dim.xl)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaCampania(
    cp: CampaniaGestion, hoy: String, nombrePorServicio: Map<String, String>, avisando: Boolean,
    onAvisar: () -> Unit, onEditar: () -> Unit, onAlternar: () -> Unit, onEliminar: () -> Unit,
) {
    val c = Sania.colors
    val estado = estadoCampania(cp, hoy)
    val (textoEstado, colorEstado) = when (estado) {
        EstadoCampania.VIGENTE -> "Vigente" to c.ok
        EstadoCampania.INACTIVA -> "Inactiva" to c.textoSuave
        EstadoCampania.PROGRAMADA -> "Empieza el ${fechaDMA(cp.fechaInicio)}" to c.navy
        EstadoCampania.VENCIDA -> "Vencida" to c.pend
    }
    val alcance = when (cp.alcance) {
        "todos" -> "Todos los servicios"
        "citas" -> "Consulta / evaluación" + (cp.aplicaA?.let { " ($it)" } ?: "")
        else -> cp.serviciosIds.mapNotNull { nombrePorServicio[it] }.joinToString(", ").ifBlank { "Servicios seleccionados" }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .drawBehind { drawRect(colorEstado, size = Size(4.dp.toPx(), size.height)) }
            .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(cp.nombre, color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(colorEstado.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 3.dp)) {
                Text(textoEstado, color = colorEstado, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(etiquetaCampania(cp), color = c.teal, fontSize = Sania.txt.pequeno, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
        Text(
            "$alcance · ${fechaDMA(cp.fechaInicio)}" + (cp.fechaFin?.let { " al ${fechaDMA(it)}" } ?: " (sin fin)"),
            color = c.textoSuave, fontSize = Sania.txt.mini, modifier = Modifier.padding(top = 2.dp),
        )
        if (!cp.descripcion.isNullOrBlank()) {
            Text(cp.descripcion, color = c.textoSuave, fontSize = Sania.txt.mini, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            // Aviso solo de las vigentes (el servidor topa a 1 por semana por clínica).
            if (estado == EstadoCampania.VIGENTE) {
                AccionTexto(if (avisando) "📣 Enviando…" else "📣 Avisar", c.navy, enabled = !avisando, onClick = onAvisar)
            }
            AccionTexto("Editar", c.navy, onClick = onEditar)
            AccionTexto(if (cp.activo) "Desactivar" else "Activar", if (cp.activo) c.textoSuave else c.ok, onClick = onAlternar)
            AccionTexto("Eliminar", c.error, onClick = onEliminar)
        }
    }
}

@Composable
private fun AccionTexto(texto: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        texto, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled) { onClick() }.padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
private fun OpcionTarjeta(titulo: String, detalle: String?, sel: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Sania.colors
    Column(
        modifier.clip(RoundedCornerShape(10.dp))
            .background(if (sel) c.navy.copy(alpha = 0.08f) else c.superficie)
            .border(if (sel) 2.dp else 1.dp, if (sel) c.navy else c.borde, RoundedCornerShape(10.dp))
            .clickable { onClick() }.padding(10.dp),
    ) {
        Text(titulo, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        if (detalle != null) Text(detalle, color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormularioCampania(
    inicial: CampaniaGestion?, servicios: List<ServicioApp>, hoy: String,
    onCerrar: () -> Unit, onGuardado: () -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var f by remember { mutableStateOf(FormCampania.desde(inicial, hoy)) }
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var eligiendoDesde by remember { mutableStateOf(false) }
    var eligiendoHasta by remember { mutableStateOf(false) }
    var cargandoRegalos by remember { mutableStateOf(inicial != null) }
    var falloRegalos by remember { mutableStateOf(false) }
    var reintentoRegalos by remember { mutableIntStateOf(0) }
    var regaloAbierto by remember { mutableStateOf<Int?>(null) }

    // Los regalos viven en otra tabla: se leen al abrir (query plana, como la web).
    LaunchedEffect(inicial?.id, reintentoRegalos) {
        if (inicial == null) return@LaunchedEffect
        cargandoRegalos = true; falloRegalos = false
        val r = runCatching { CampaniasRepo.regalosDe(inicial.id) }.getOrNull()
        // Si no se leyeron, el form NO manda `regalos` (el servidor no los toca): ver FormCampania.regalosCargados.
        if (r != null) f = f.copy(regalos = r, regalosCargados = true) else falloRegalos = true
        cargandoRegalos = false
    }

    if (eligiendoDesde) DialogoFecha(inicial = f.desde, onElegir = { f = f.copy(desde = it) }, onCerrar = { eligiendoDesde = false })
    if (eligiendoHasta) DialogoFecha(inicial = f.hasta.ifBlank { null }, onElegir = { f = f.copy(hasta = it) }, onCerrar = { eligiendoHasta = false })

    fun guardar() {
        if (guardando || cargandoRegalos) return
        val p = problemaFormCampania(f)
        if (p != null) { error = p; return }
        error = null
        guardando = true
        scope.launch {
            val r = try { conIndicador { CampaniasRepo.guardar(cuerpoGuardarCampania(f, inicial?.id)) } } finally { guardando = false }
            if (r.registrada) {
                Toaster.exito(if (inicial == null) "Campaña creada" else "Campaña guardada")
                onGuardado()
            } else error = r.rechazo?.error ?: "No se pudo guardar la campaña"
        }
    }

    val tipoActual = TIPOS_CAMPANIA.first { it.first == f.tipo }
    DialogoForm(
        titulo = if (inicial == null) "Nueva campaña" else "Editar campaña",
        subtitulo = null,
        textoAccion = if (guardando) "Guardando…" else if (inicial == null) "Crear campaña" else "Guardar cambios",
        accionHabilitada = !guardando && !cargandoRegalos,
        onCancelar = onCerrar,
        onAccion = { guardar() },
    ) {
        error?.let {
            Text(
                "⚠ $it", color = c.error, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(c.error.copy(alpha = 0.1f)).padding(12.dp),
            )
            Spacer(Modifier.height(10.dp))
        }
        TarjetaForm("Promoción", "🎉") {
            EtqForm("Nombre")
            OutlinedTextField(
                value = f.nombre, onValueChange = { f = f.copy(nombre = it) }, singleLine = true,
                placeholder = { Text("Ej. Fiestas Patrias") }, colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            EtqForm("Tipo de promoción")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TIPOS_CAMPANIA.chunked(2).forEach { fila ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        fila.forEach { (valor, label, ejemplo) ->
                            OpcionTarjeta(label, ejemplo, f.tipo == valor, Modifier.weight(1f)) { f = f.copy(tipo = valor) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            when (f.tipo) {
                "paquete_fijo" -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        EtqForm("N° de sesiones")
                        OutlinedTextField(
                            value = f.cantidad, onValueChange = { f = f.copy(cantidad = it.filter(Char::isDigit).take(3)) }, singleLine = true,
                            placeholder = { Text("10") }, colors = coloresCampoForm(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        EtqForm("Precio del paquete (S/)")
                        CampoDecimal(f.precio, "500") { f = f.copy(precio = it) }
                    }
                }
                "precio_fijo" -> { EtqForm("Precio de oferta (S/)"); CampoDecimal(f.precio, "50") { f = f.copy(precio = it) } }
                "porcentaje" -> { EtqForm("Descuento (%)"); CampoDecimal(f.valor, "10") { f = f.copy(valor = it) } }
                else -> { EtqForm("Descuento (S/)"); CampoDecimal(f.valor, "30") { f = f.copy(valor = it) } }
            }
        }
        Spacer(Modifier.height(12.dp))
        TarjetaForm("¿A qué aplica?", "🎯") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("todos" to "Todos los servicios", "servicios" to "Servicios elegidos", "citas" to "Consulta / evaluación").forEach { (v, t) ->
                    OpcionTarjeta(t, null, f.alcance == v, Modifier.fillMaxWidth()) { f = f.copy(alcance = v) }
                }
            }
            if (f.alcance == "citas") {
                Spacer(Modifier.height(10.dp))
                Text("Descuenta el precio de la primera cita, sin tocar el precio de los tratamientos.", color = c.textoSuave, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Evaluación" to "Evaluación", "Consulta" to "Consulta", "ambas" to "Las dos").forEach { (v, t) ->
                        OpcionTarjeta(t, null, f.aplicaA == v, Modifier.weight(1f)) { f = f.copy(aplicaA = v) }
                    }
                }
            }
            if (f.alcance == "servicios") {
                Spacer(Modifier.height(10.dp))
                if (servicios.isEmpty()) {
                    Text("No hay servicios activos. Créalos en Servicios primero.", color = c.pend, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        servicios.forEach { s ->
                            val sel = s.id in f.serviciosIds
                            Text(
                                (if (sel) "✓ " else "") + s.nombre,
                                color = if (sel) c.sobreNavy else c.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                                    .background(if (sel) c.navy else c.fondo)
                                    .border(1.dp, if (sel) c.navy else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
                                    .clickable { f = f.copy(serviciosIds = if (sel) f.serviciosIds - s.id else f.serviciosIds + s.id) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        TarjetaForm("Vigencia", "📅") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    EtqForm("Desde")
                    CajaSelectorForm(if (f.desde.isBlank()) "Elegir" else fechaDMA(f.desde)) { eligiendoDesde = true }
                }
                Column(Modifier.weight(1f)) {
                    EtqForm("Hasta (opcional)")
                    CajaSelectorForm(if (f.hasta.isBlank()) "Sin fin" else fechaDMA(f.hasta)) { eligiendoHasta = true }
                    if (f.hasta.isNotBlank()) {
                        Text("Quitar fecha de fin", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { f = f.copy(hasta = "") }.padding(top = 6.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        TarjetaForm("Regalos (opcional)", "🎁") {
            if (cargandoRegalos) Text("Cargando…", color = c.textoSuave, fontSize = 12.sp)
            if (falloRegalos) {
                Text("No se pudieron cargar los regalos — Reintentar", color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { reintentoRegalos++ }.padding(vertical = 8.dp))
                Text("Mientras tanto, los regalos actuales no se tocan al guardar.", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            f.regalos.forEachIndexed { i, r ->
                val nombre = servicios.firstOrNull { it.id == r.procedimientoId }?.nombre
                Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            CajaSelectorForm(nombre ?: "— Elegir servicio —") { regaloAbierto = i }
                        }
                        Text("✕", color = c.error, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { f = f.copy(regalos = f.regalos.filterIndexed { j, _ -> j != i }) }.padding(12.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                        Column(Modifier.weight(1f)) {
                            EtqForm("A los (días hábiles)")
                            OutlinedTextField(
                                value = r.dias, onValueChange = { v -> f = f.copy(regalos = f.regalos.mapIndexed { j, x -> if (j == i) x.copy(dias = v.filter(Char::isDigit).take(3)) else x }) },
                                singleLine = true, colors = coloresCampoForm(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            EtqForm("Precio (S/)")
                            CampoDecimal(r.precio, "0 (gratis)") { v -> f = f.copy(regalos = f.regalos.mapIndexed { j, x -> if (j == i) x.copy(precio = v) else x }) }
                        }
                    }
                }
            }
            Text(
                "+ Agregar regalo", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, c.borde, RoundedCornerShape(8.dp))
                    .clickable(enabled = !cargandoRegalos && f.regalosCargados) { f = f.copy(regalos = f.regalos + RegaloForm()) }.padding(10.dp),
            )
            Text(
                "Al crear un tratamiento con esta campaña, la cita del regalo se agenda sola (N días hábiles después, editable en la ficha).",
                color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        TarjetaForm("Descripción (opcional)", "📝") {
            OutlinedTextField(
                value = f.descripcion, onValueChange = { f = f.copy(descripcion = it) }, minLines = 2,
                placeholder = { Text("Ej. Promo válida para pacientes nuevos") }, colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth(),
            )
            Text("La ven el bot y el equipo.", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "💡 ${tipoActual.second.substringAfter(' ')}: ${tipoActual.third}. El bot de WhatsApp podrá ofrecer esta promo como gancho mientras esté vigente.",
            color = c.textoSuave, fontSize = 12.sp,
        )
    }

    // Selector del servicio de un regalo.
    regaloAbierto?.let { idx ->
        AlertaConTeclado(
            onDismissRequest = { regaloAbierto = null },
            title = { Text("Servicio del regalo") },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().height(320.dp)) {
                    items(servicios, key = { it.id }) { s ->
                        Text(
                            s.nombre, color = c.texto, fontSize = 15.sp,
                            modifier = Modifier.fillMaxWidth().clickable {
                                f = f.copy(regalos = f.regalos.mapIndexed { j, x -> if (j == idx) x.copy(procedimientoId = s.id) else x })
                                regaloAbierto = null
                            }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { regaloAbierto = null }) { Text("Cerrar", color = c.textoSuave) } },
        )
    }
}

@Composable
private fun CampoDecimal(valor: String, placeholder: String, onCambio: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = { t -> onCambio(t.filter { it.isDigit() || it == '.' || it == ',' }.take(9)) },
        singleLine = true, placeholder = { Text(placeholder) }, colors = coloresCampoForm(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
    )
}
