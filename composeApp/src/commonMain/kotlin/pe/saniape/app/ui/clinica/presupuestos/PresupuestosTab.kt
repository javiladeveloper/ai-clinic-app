package pe.saniape.app.ui.clinica.presupuestos

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.DatosPresupuesto
import pe.saniape.app.data.staff.LimitesPresupuesto
import pe.saniape.app.data.staff.MOTIVOS_RECHAZO
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.data.staff.PlantillaPresupuesto
import pe.saniape.app.data.staff.Presupuesto
import pe.saniape.app.data.staff.PresupuestosRepo
import pe.saniape.app.data.staff.RefNombre
import pe.saniape.app.data.staff.ServicioPresupuesto
import pe.saniape.app.data.staff.TratDePresupuesto
import pe.saniape.app.data.staff.VIGENCIA_DIAS_DEFECTO
import pe.saniape.app.data.staff.diaEnZona
import pe.saniape.app.data.staff.diasDesdeEnvio
import pe.saniape.app.data.staff.diasEntre
import pe.saniape.app.data.staff.enlaceWhatsAppSede
import pe.saniape.app.data.staff.esAutorPresupuesto
import pe.saniape.app.data.staff.estadoVisiblePresupuesto
import pe.saniape.app.data.staff.faltaConvertir
import pe.saniape.app.data.staff.fechaHoraAceptacion
import pe.saniape.app.data.staff.fechaLarga
import pe.saniape.app.data.staff.formatearDinero
import pe.saniape.app.data.staff.hoyEnIso
import pe.saniape.app.data.staff.htmlPresupuesto
import pe.saniape.app.data.staff.mensajeRechazoPresupuesto
import pe.saniape.app.data.staff.presupuestoDeRespuesta
import pe.saniape.app.data.staff.puedeArmarPresupuesto
import pe.saniape.app.data.staff.puedeFijarPrecioPresupuesto
import pe.saniape.app.data.staff.servicioDe
import pe.saniape.app.data.staff.sumarDias
import pe.saniape.app.data.staff.textoCantidad
import pe.saniape.app.data.staff.textoDiasEnvio
import pe.saniape.app.data.staff.textoPresupuestoWhatsApp
import pe.saniape.app.data.staff.textoRecordatorioWhatsApp
import pe.saniape.app.data.staff.totalesDe
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.PrefillCita
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania

/**
 * Pestaña 🧾 Presupuestos de la ficha (TODAS las especialidades). Gemelo de
 * PresupuestosTab.tsx:
 *  · Lista con su estado (Borrador, Enviado, Vencido, Aceptado, Rechazado),
 *    total y ahorro, días desde el envío y qué tratamientos salieron de él.
 *  · ＋ Nuevo (también llega abierto desde "🧾 Arma un presupuesto" del
 *    formulario de crear tratamiento, vinculado a la cita).
 *  · Enviar: 📱 WhatsApp (urlWhatsApp, nunca wa.me?text=) o 🖨 PDF (la hoja
 *    nativa en el visor con imprimir / compartir).
 *  · ✓ Acepta en persona → crear tratamientos → adelanto → agendar.
 *  · Rechazar con motivo, reabrir, extender la vigencia, 📱 recordar, borrar borrador.
 *  · Plan Básico o vencido: se VE con 🔒 y "Mejorar plan" (403 PLAN_REQUERIDO).
 * Carga SOLO cuando la pestaña se abre. Sin listas perezosas: vive dentro del
 * `verticalScroll` de la ficha.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PresupuestosTab(
    ctx: ContextoStaff,
    pacienteId: String,
    pacienteNombre: String,
    pacienteDocumento: String?,
    /** Teléfono para avisos (el del apoderado si los recibe). */
    telefono: String?,
    /** País de la sede del paciente (el número de WhatsApp). */
    pais: String?,
    /** Moneda de la sede del paciente (la del presupuesto nuevo). */
    moneda: String,
    /** Zona de la sede del paciente ("hoy", vencido, días desde el envío). */
    zona: String?,
    /** Ficha dada de baja: se mira, no se arma. */
    inactivo: Boolean,
    /** Viene de crear tratamiento tras la valoración: el nuevo queda vinculado a esa cita. */
    citaId: String? = null,
    especialidadId: String? = null,
    abrirNuevo: Boolean = false,
    onAbiertoNuevo: () -> Unit = {},
    /** Se crearon tratamientos (o un cobro): la ficha recarga. */
    onCambioFuerte: () -> Unit = {},
    /** "📅 Agendar…" tras convertir: el formulario de crear cita, prellenado. */
    onAgendar: ((PrefillCita) -> Unit)? = null,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val acciones = recordarAcciones()
    val hoy = hoyEnIso(zona)
    val planOk = ctx.can("presupuestos")
    val puede: (String) -> Boolean = { ctx.puede(it) }
    val puedeArmar = puedeArmarPresupuesto(puede) && !inactivo && planOk
    val userId = remember { PresupuestosRepo.usuarioId() }

    var token by remember { mutableStateOf(0) }
    var cargando by remember(pacienteId) { mutableStateOf(true) }
    var items by remember(pacienteId) { mutableStateOf<List<Presupuesto>?>(null) }
    var tratamientos by remember(pacienteId) { mutableStateOf<List<TratDePresupuesto>>(emptyList()) }
    var error by remember(pacienteId) { mutableStateOf<String?>(null) }
    // El servidor dijo PLAN_REQUERIDO (el contexto aún no lo sabía): candado igual.
    var bloqueado by remember { mutableStateOf(false) }
    var profesionales by remember { mutableStateOf<List<RefNombre>>(emptyList()) }

    // Catálogo y paquetes guardados: solo al abrir el editor o la aceptación.
    var servicios by remember { mutableStateOf<List<ServicioPresupuesto>?>(null) }
    var paquetes by remember { mutableStateOf<List<PlantillaPresupuesto>>(emptyList()) }

    var editor by remember { mutableStateOf<Pair<Presupuesto?, String?>?>(null) }   // (inicial, citaId)
    var enviar by remember { mutableStateOf<Presupuesto?>(null) }
    var aceptar by remember { mutableStateOf<Presupuesto?>(null) }
    var rechazar by remember { mutableStateOf<Pair<Presupuesto, String>?>(null) }
    var borrar by remember { mutableStateOf<Presupuesto?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    val unToque = remember { UnToque() }

    LaunchedEffect(pacienteId, token, planOk) {
        if (!planOk) { cargando = false; return@LaunchedEffect }
        val r = PresupuestosRepo.listar(pacienteId)
        if (r.items != null) { items = r.items; tratamientos = r.tratamientos; error = null; bloqueado = false }
        else if (r.codigo == "PLAN_REQUERIDO") bloqueado = true
        else if (items == null) error = r.error
        else Toaster.error(r.error ?: "No se pudieron actualizar los presupuestos")
        cargando = false
    }
    LaunchedEffect(Unit) {
        profesionales = runCatching { PacientesRepo.terapeutasActivos() }.getOrDefault(emptyList())
    }
    val necesitaCatalogo = editor != null || aceptar != null
    LaunchedEffect(necesitaCatalogo) {
        if (!necesitaCatalogo || servicios != null) return@LaunchedEffect
        servicios = runCatching { PacientesRepo.procedimientos().map(::servicioDe) }.getOrDefault(emptyList())
    }
    LaunchedEffect(editor != null) {
        if (editor != null) paquetes = runCatching { PresupuestosRepo.plantillas() }.getOrDefault(emptyList())
    }
    // Desde "🧾 Arma un presupuesto": el editor nuevo, una vez.
    LaunchedEffect(abrirNuevo, puedeArmar) {
        if (abrirNuevo && puedeArmar) { editor = null to citaId; onAbiertoNuevo() }
    }

    fun recargar() { token++ }
    fun cambioFuerte() { recargar(); onCambioFuerte() }
    fun nombreProf(id: String?) = id?.let { i -> profesionales.firstOrNull { it.id == i }?.nombre }

    fun accion(p: Presupuesto, a: String, motivo: String? = null, despues: (Boolean) -> Unit = {}) {
        if (ocupado) return
        ocupado = true
        scope.launch {
            val r = PresupuestosRepo.accion(p.id, a, motivo)
            ocupado = false
            if (!r.registrada) Toaster.error(mensajeRechazoPresupuesto(r, "No se pudo guardar")) else recargar()
            despues(r.registrada)
        }
    }

    /** WhatsApp con el detalle (urlWhatsApp) y, si era borrador, se marca enviado. */
    fun porWhatsApp(p: Presupuesto) {
        if (!unToque.permitir()) return
        val texto = textoPresupuestoWhatsApp(p, ctx.clinicaNombre, pacienteNombre, nombreProf(p.terapeutaId), ctx.tienePortal)
        val url = enlaceWhatsAppSede(telefono, texto, pais)
        if (url == null) { Toaster.error("El paciente no tiene un celular válido"); return }
        acciones.abrirUrl(url)
        if (p.estado == "Borrador") accion(p, "enviar")
        enviar = null
    }
    /** 🖨 La hoja (PDF) para ver o compartir: NO marca nada (como el enlace 🖨 PDF de la web). */
    fun verPdf(p: Presupuesto) {
        if (!unToque.permitir()) return
        acciones.abrirHtml(
            htmlPresupuesto(p, ctx.clinicaNombre, ctx.clinicaDireccion, ctx.logoUrl, ctx.colorPrincipal, pacienteNombre,
                pacienteDocumento, nombreProf(p.terapeutaId), zona),
            "Presupuesto",
        )
    }
    /** Desde "📤 ¿Cómo se lo envías?": el PDF y, si era borrador, se marca enviado. */
    fun porPdf(p: Presupuesto) {
        if (!unToque.permitir()) return
        if (p.estado == "Borrador") accion(p, "enviar")
        enviar = null
        acciones.abrirHtml(
            htmlPresupuesto(p, ctx.clinicaNombre, ctx.clinicaDireccion, ctx.logoUrl, ctx.colorPrincipal, pacienteNombre,
                pacienteDocumento, nombreProf(p.terapeutaId), zona),
            "Presupuesto",
        )
    }
    fun recordar(p: Presupuesto) {
        if (!unToque.permitir()) return
        val url = enlaceWhatsAppSede(telefono, textoRecordatorioWhatsApp(p, hoy, ctx.clinicaNombre, pacienteNombre), pais)
        if (url == null) { Toaster.error("El paciente no tiene un celular válido"); return }
        acciones.abrirUrl(url)
        accion(p, "recordado")
    }
    fun extender(p: Presupuesto) {
        if (ocupado) return
        ocupado = true
        scope.launch {
            val nueva = sumarDias(hoy, VIGENCIA_DIAS_DEFECTO)
            val r = PresupuestosRepo.extenderVigencia(p.id, nueva)
            ocupado = false
            if (!r.registrada) Toaster.error(mensajeRechazoPresupuesto(r)) else { Toaster.exito("Vigente hasta el ${fechaLarga(nueva)}"); recargar() }
        }
    }

    suspend fun guardar(d: DatosPresupuesto, enviarDespues: Boolean): String? {
        val (inicial, cita) = editor ?: return null
        val r = if (inicial != null) PresupuestosRepo.editar(inicial.id, d)
        else PresupuestosRepo.crear(pacienteId, d, cita, especialidadId)
        if (!r.registrada) return mensajeRechazoPresupuesto(r)
        val guardado = presupuestoDeRespuesta(r)
        // Editar lo que el paciente ve de uno ya enviado lo vuelve a borrador: se ofrece reenviarlo.
        val volvio = (r.cuerpo?.get("volvioABorrador") as? JsonPrimitive)?.booleanOrNull == true
        Toaster.exito(
            when {
                volvio -> "Presupuesto actualizado: volvió a borrador, envíaselo de nuevo"
                inicial != null -> "Presupuesto actualizado"
                else -> "Presupuesto guardado"
            },
        )
        editor = null
        recargar()
        if ((enviarDespues || volvio) && guardado != null) enviar = guardado
        return null
    }
    suspend fun guardarPaquete(nombre: String, d: DatosPresupuesto): String? {
        val dias = diasEntre(hoy, d.vigenciaHasta).coerceIn(1, LimitesPresupuesto.VIGENCIA_MAX_DIAS)
        val r = PresupuestosRepo.guardarPlantilla(nombre, d, dias, especialidadId)
        if (r.registrada) paquetes = runCatching { PresupuestosRepo.plantillas() }.getOrDefault(paquetes)
        return if (r.registrada) null else mensajeRechazoPresupuesto(r, "No se pudo guardar el paquete")
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("🧾 Presupuestos", color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("El plan con su precio de paquete: se envía, el paciente lo acepta y pasa a tratamientos.",
                    color = c.textoSuave, fontSize = 12.sp)
            }
        }
        if (!planOk || bloqueado) {
            CandadoPresupuestos { acciones.abrirUrl("${Supabase.SITE_URL}/suscripcion") }
            return@Column
        }
        if (puedeArmar) BotonPresu("＋ Nuevo presupuesto", lleno = true, modifier = Modifier.fillMaxWidth()) { editor = null to null }

        val lista = items
        when {
            cargando && lista == null -> listOf(110, 90).forEach { h ->
                Box(Modifier.fillMaxWidth().height(h.dp).clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.chipBg))
            }
            lista == null -> Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error ?: "No se pudieron cargar los presupuestos", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("↻ Reintentar", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp).clickable(role = Role.Button) { error = null; cargando = true; recargar() }.padding(8.dp))
            }
            lista.isEmpty() -> TarjetaPresu {
                Text("Aún no tiene presupuestos. Arma uno después de la valoración: varios servicios, precio de paquete y vigencia; " +
                    "el paciente puede aceptarlo aquí mismo o desde su app.", color = c.textoSuave, fontSize = 13.sp)
            }
            else -> lista.forEach { p ->
                androidx.compose.runtime.key(p.id) {
                    TarjetaPresupuesto(
                        p = p, hoy = hoy, zona = zona, puedeArmar = puedeArmar, puedeConvertir = ctx.puede("sesiones"),
                        profesional = nombreProf(p.terapeutaId),
                        trats = tratamientos.filter { it.presupuestoId == p.id && it.estado != "Eliminado" },
                        onEnviar = { enviar = p }, onWhatsApp = { porWhatsApp(p) }, onRecordar = { recordar(p) },
                        onPdf = { verPdf(p) }, onAceptar = { aceptar = p }, onEditar = { editor = p to p.citaId },
                        onExtender = { extender(p) }, onRechazar = { rechazar = p to "" },
                        onReabrir = { accion(p, "reabrir") }, onBorrar = { borrar = p },
                    )
                }
            }
        }
    }

    editor?.let { (inicial, _) ->
        val monedaPresu = inicial?.moneda ?: moneda
        val serv = servicios
        if (serv == null) {
            // El catálogo llega en un instante; mientras, no se abre un editor vacío.
            AlertDialog(onDismissRequest = { editor = null }, confirmButton = {}, text = { Text("Cargando servicios…", color = c.textoSuave) }, containerColor = c.superficie)
        } else androidx.compose.runtime.key(inicial?.id ?: "nuevo") {
            EditorPresupuesto(
                inicial = inicial, moneda = monedaPresu, otraMoneda = monedaPresu != ctx.moneda, hoy = hoy,
                servicios = serv, paquetes = paquetes,
                puedePrecio = puedeFijarPrecioPresupuesto(puede, esAutorPresupuesto(inicial, userId, ctx.miTerapeutaId)),
                onCerrar = { editor = null },
                onGuardar = { d, env -> guardar(d, env) },
                onGuardarPaquete = { n, d -> guardarPaquete(n, d) },
            )
        }
    }

    enviar?.let { p ->
        val t = totalesDe(p)
        AlertDialog(
            onDismissRequest = { enviar = null },
            title = { Text("📤 ¿Cómo se lo envías?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("«${p.titulo}» por ${formatearDinero(t.total, p.moneda)}." + if (ctx.tienePortal) " También le aparecerá en su app para aceptarlo." else "",
                        color = c.textoSuave, fontSize = 13.sp)
                    BotonPresu("📱 Por WhatsApp (con el detalle)", lleno = true, modifier = Modifier.fillMaxWidth()) { porWhatsApp(p) }
                    BotonPresu("🖨 Imprimir / PDF con logo y firma", modifier = Modifier.fillMaxWidth()) { porPdf(p) }
                    if (p.estado == "Borrador") {
                        BotonPresu("Ya se lo entregué (marcar enviado)", habilitado = !ocupado, modifier = Modifier.fillMaxWidth()) {
                            accion(p, "enviar") { ok -> if (ok) { Toaster.exito("Marcado como enviado"); enviar = null } }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { enviar = null }) { Text("Cerrar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }

    rechazar?.let { (p, motivo) ->
        AlertDialog(
            onDismissRequest = { if (!ocupado) rechazar = null },
            title = { Text("No aceptó el presupuesto", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Queda en el historial y sale de los pendientes. Puedes reabrirlo después.", color = c.textoSuave, fontSize = 13.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MOTIVOS_RECHAZO.forEach { m -> ChipPresu(m, motivo == m) { rechazar = p to m } }
                    }
                    CampoPresu(motivo, { rechazar = p to it }, placeholder = "Motivo (opcional)", max = LimitesPresupuesto.MOTIVO)
                }
            },
            confirmButton = {
                TextButton(enabled = !ocupado, onClick = { accion(p, "rechazar", motivo) { ok -> if (ok) rechazar = null } }) {
                    Text(if (ocupado) "Guardando…" else "Registrar", color = c.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { rechazar = null }) { Text("Cancelar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }

    borrar?.let { p ->
        AlertDialog(
            onDismissRequest = { if (!ocupado) borrar = null },
            title = { Text("¿Borrar el borrador?", fontWeight = FontWeight.Bold) },
            text = { Text("«${p.titulo}»", color = c.textoSuave) },
            confirmButton = {
                TextButton(enabled = !ocupado, onClick = {
                    if (ocupado) return@TextButton
                    ocupado = true
                    scope.launch {
                        val r = PresupuestosRepo.borrar(p.id)
                        ocupado = false
                        if (!r.registrada) Toaster.error(mensajeRechazoPresupuesto(r))
                        else { Toaster.exito("Borrador eliminado"); borrar = null; recargar() }
                    }
                }) { Text(if (ocupado) "Borrando…" else "Sí, borrar", color = c.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { borrar = null }) { Text("No", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }

    aceptar?.let { p ->
        AceptarPresupuesto(
            presupuesto = p, pacienteId = pacienteId, pacienteNombre = pacienteNombre, profesionales = profesionales,
            servicios = servicios.orEmpty(), puedeConvertir = ctx.puede("sesiones"), puedeCobrar = ctx.puede("pagos"),
            onCerrar = { aceptar = null }, onCambio = { cambioFuerte() }, onAgendar = onAgendar,
        )
    }
}

/** Un presupuesto de la lista con sus acciones según el estado. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaPresupuesto(
    p: Presupuesto,
    hoy: String,
    zona: String?,
    puedeArmar: Boolean,
    puedeConvertir: Boolean,
    profesional: String?,
    trats: List<TratDePresupuesto>,
    onEnviar: () -> Unit,
    onWhatsApp: () -> Unit,
    onRecordar: () -> Unit,
    onPdf: () -> Unit,
    onAceptar: () -> Unit,
    onEditar: () -> Unit,
    onExtender: () -> Unit,
    onRechazar: () -> Unit,
    onReabrir: () -> Unit,
    onBorrar: () -> Unit,
) {
    val c = Sania.colors
    val visible = estadoVisiblePresupuesto(p.estado, p.vigenciaHasta, hoy)
    val t = totalesDe(p)
    val dias = diasDesdeEnvio(diaEnZona(p.enviadoAt, zona), hoy)
    val (fg, bg) = coloresEstadoPresupuesto(visible)
    TarjetaPresu {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(p.titulo, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    PildoraPresu(visible, fg, bg)
                    if (faltaConvertir(p)) PildoraPresu("Falta crear tratamientos", c.pend, c.pendBg)
                }
                Text(
                    listOfNotNull(
                        profesional?.let { "Por $it" },
                        if (p.enviadoAt != null) "Enviado ${textoDiasEnvio(dias)}" else "Creado ${fechaLarga(p.createdAt.take(10))}",
                        if (p.estado != "Aceptado" && p.estado != "Rechazado") "vence ${fechaLarga(p.vigenciaHasta)}" else null,
                        p.recordadoAt?.let { "recordado ${textoDiasEnvio(diasDesdeEnvio(diaEnZona(it, zona), hoy))}" },
                    ).joinToString(" · "),
                    color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(formatearDinero(t.total, p.moneda), color = c.navy, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (t.ahorro > 0) Text("ahorra ${formatearDinero(t.ahorro, p.moneda)}", color = c.ok, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.padding(top = 8.dp)) {
            p.items.forEach { it ->
                Text("• ${it.nombre}${it.detalle?.let { d -> " ($d)" } ?: ""} ${textoCantidad(it)}".trimEnd(), color = c.texto, fontSize = 12.sp)
            }
        }
        p.notasInternas?.let { Text("🔒 $it", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp)) }
        if (p.estado == "Aceptado" && p.aceptadoAt != null) {
            Text(
                "✓ Aceptado el ${fechaHoraAceptacion(p.aceptadoAt, zona)} " +
                    if (p.aceptadoCanal == "portal") "por el paciente desde su app" else "en persona${p.aceptadoPorNombre?.let { " · registró $it" } ?: ""}",
                color = c.ok, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (p.estado == "Rechazado") {
            Text("✕ No lo aceptó${p.motivoRechazo?.let { ": $it" } ?: ""}", color = c.error, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        if (trats.isNotEmpty()) {
            Text(
                "→ ${if (trats.size == 1) "Tratamiento" else "${trats.size} tratamientos"}: " +
                    trats.joinToString(" · ") { "${it.procedimientoNombre ?: "Tratamiento"} (${formatearDinero(it.precioAcordado ?: 0.0, p.moneda)})" },
                color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
            )
        }
        FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (p.estado == "Borrador" && puedeArmar) BotonPresu("📤 Enviar", lleno = true, onClick = onEnviar)
            if (p.estado == "Enviado") {
                BotonPresu("📱 WhatsApp", onClick = onWhatsApp)
                if (visible == "Enviado" && dias != null && dias >= 2) BotonPresu("📱 Recordar", onClick = onRecordar)
            }
            BotonPresu("🖨 PDF", onClick = onPdf)
            if (puedeArmar && (p.estado == "Borrador" || p.estado == "Enviado")) BotonPresu("✓ Acepta en persona", ok = true, onClick = onAceptar)
            if (puedeArmar && faltaConvertir(p) && puedeConvertir) BotonPresu("🩺 Crear tratamientos", ok = true, onClick = onAceptar)
            if (puedeArmar && (p.estado == "Borrador" || p.estado == "Enviado")) BotonPresu("✏️ Editar", onClick = onEditar)
            if (puedeArmar && visible == "Vencido") BotonPresu("↻ Extender $VIGENCIA_DIAS_DEFECTO días", onClick = onExtender)
            if (puedeArmar && p.estado == "Enviado") BotonPresu("No lo aceptó", onClick = onRechazar)
            if (puedeArmar && p.estado == "Rechazado") BotonPresu("↺ Reabrir", onClick = onReabrir)
            if (puedeArmar && p.estado == "Borrador") BotonPresu("🗑", peligro = true, onClick = onBorrar)
        }
    }
}
