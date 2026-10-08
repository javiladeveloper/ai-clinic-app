package pe.saniape.app.ui.clinica.ajustes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.http.HttpMethod
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.staff.AjustesRepo
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.ui.AlertaConTeclado
import pe.saniape.app.ui.CargandoLista
import pe.saniape.app.ui.Gestion
import pe.saniape.app.ui.Reanudacion
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania

// ─── 💬 Tus redes ───────────────────────────────────────────────────────────

private data class Red(val tipo: String, val nombre: String, val icono: String, val detalle: String)

private val REDES = listOf(
    Red("whatsapp", "WhatsApp", "💬", "El canal principal: el paciente escribe, el bot responde y agenda la cita."),
    Red("instagram", "Instagram", "📸", "Mensajes directos y comentarios de tus publicaciones."),
    Red("messenger", "Facebook", "💠", "Mensajes de tu página de Facebook."),
    Red("tiktok", "TikTok", "🎵", "La cuenta de TikTok de tu clínica, para publicar tus videos y promos."),
)
private val TIPOS_OAUTH = setOf("instagram", "messenger", "tiktok")

/**
 * Desde dónde le escriben los pacientes (gemelo de PanelRedes). Ver, liberar o
 * desconectar y elegir la página pendiente es nativo. CONECTAR WhatsApp usa el
 * Embedded Signup de Meta (su SDK de JavaScript): se hace en la web. Instagram,
 * Facebook y TikTok se autorizan en el navegador y, al volver, la lista se
 * actualiza sola.
 */
@Composable
internal fun SeccionTusRedes(d: JsonObject, ctx: ContextoStaff, onVolver: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val acciones = recordarAcciones()
    val esAdmin = d.b("esAdmin")
    var canales by remember { mutableStateOf<List<JsonObject>?>(null) }
    var bloqueo by remember { mutableStateOf<String?>(null) }   // 'inactivo' | 'sin_permiso'
    var sel by remember { mutableStateOf("whatsapp") }
    var pendientes by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var recarga by remember { mutableIntStateOf(0) }
    var ocupado by remember { mutableStateOf<String?>(null) }
    var confirmar by remember { mutableStateOf<Pair<JsonObject, Boolean>?>(null) }   // (canal, liberar)
    var avisoLiberar by remember { mutableStateOf<String?>(null) }
    var errorCarga by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(recarga, Reanudacion.contador) {
        val r = AjustesRepo.leerConEstado("/api/staff/mensajes/canales")
        val el = r.json
        when {
            // Como la web: sin el módulo del bot, se explica; cualquier otro 403 = sin permiso.
            r.status == 403 && r.error == "leadai_inactivo" -> bloqueo = "inactivo"
            r.status == 403 -> bloqueo = "sin_permiso"
            el is JsonArray -> { canales = el.mapNotNull { it as? JsonObject }; bloqueo = null; errorCarga = null }
            el is JsonObject -> { canales = el.objetos("items"); bloqueo = null; errorCarga = null }
            canales == null -> errorCarga = r.error ?: "No se pudieron cargar tus redes"
            else -> Toaster.error(r.error ?: "No se pudieron actualizar tus redes")
        }
    }
    LaunchedEffect(sel, recarga, Reanudacion.contador) {
        pendientes = if (esAdmin && sel in TIPOS_OAUTH) AjustesRepo.leerObjeto("/api/staff/mensajes/canales/$sel/pendientes").first?.objetos("cuentas") ?: emptyList() else emptyList()
    }

    confirmar?.let { (canal, liberar) ->
        val cual = canal.s("nombre")?.takeIf { it.isNotBlank() } ?: canal.t("cuentaExterna")
        AlertaConTeclado(
            onDismissRequest = { confirmar = null },
            title = { Text(if (liberar) "¿Liberar $cual?" else "¿Desconectar $cual?", fontWeight = FontWeight.Bold) },
            text = {
                Text(if (liberar) "El bot dejará de responder y tu número quedará libre para usarlo en otra aplicación o en WhatsApp Business.\n\nPara volver a conectarlo aquí habrá que verificarlo otra vez con Meta."
                    else "El bot deja de responder por este canal y las conversaciones se pausan.\n\nTus conversaciones NO se borran, y puedes volver a conectar la cuenta cuando quieras.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmar = null
                    ocupado = canal.t("id")
                    scope.launch {
                        // Multisede: se dice QUÉ número se libera, para no borrar el de otra sede.
                        val numero = if (liberar && ctx.multiSede && canal.t("tipo") == "whatsapp") "&numero=${pe.saniape.app.ui.urlEncode(canal.t("cuentaExterna"))}" else ""
                        val ruta = "/api/staff/mensajes/canales/${canal.t("id")}" + if (liberar) "?liberar=true$numero" else ""
                        val r = pe.saniape.app.ui.conIndicador(Gestion.ELIMINANDO) { AjustesRepo.enviar(HttpMethod.Delete, ruta, JsonObject(emptyMap())) }
                        ocupado = null
                        when {
                            r.registrada && liberar && r.cuerpo?.bn("numeroLiberado") == false -> avisoLiberar =
                                "Listo: el asistente ya no responde por este número. Como tu número sigue funcionando en tu app de WhatsApp Business, no hay nada que recuperar — ya es tuyo. Si además quieres desvincularlo por completo de la plataforma: en tu app, Configuración → Herramientas comerciales → desvincular."
                            r.registrada -> Toaster.exito(if (liberar) "Número liberado. Ya puedes usarlo en otra aplicación." else "Canal desconectado. Puedes reconectarlo cuando quieras.")
                            r.rechazo?.status == 404 -> Toaster.exito("Ese canal ya estaba desconectado")
                            else -> Toaster.error(r.rechazo?.error ?: "No se pudo completar. Vuelve a intentarlo.")
                        }
                        recarga++
                    }
                }) { Text(if (liberar) "Liberar" else "Desconectar", color = c.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmar = null }) { Text("Cancelar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }

    SubPantalla("Tus redes", onVolver) {
        Ayuda("Conecta los canales por donde te escriben tus pacientes. El asistente responde en todos y las conversaciones te llegan a Mensajes.")
        when {
            bloqueo == "inactivo" -> {
                Tarjeta("💬🤖 Conecta el WhatsApp de tu clínica") {
                    Ayuda("Con el asistente activado, tus pacientes escriben al WhatsApp de siempre y el bot les responde, resuelve dudas y les agenda la cita — también fuera de horario. Aquí conectarías tu número y tus redes.")
                    Ayuda("Es un módulo adicional. Escríbenos para activarlo.")
                }
                return@SubPantalla
            }
            bloqueo == "sin_permiso" -> { Ayuda("No tienes acceso a esta sección."); return@SubPantalla }
            canales == null && errorCarga != null -> { ErrorCarga(errorCarga) { errorCarga = null; recarga++ }; return@SubPantalla }
            canales == null -> { CargandoLista(); return@SubPantalla }
        }
        val todos = canales ?: emptyList()
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            REDES.forEach { r ->
                val n = todos.count { it.t("tipo") == r.tipo }
                val activa = sel == r.tipo
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(if (activa) c.chipBg else c.superficie)
                        .border(1.dp, if (activa) c.navy else c.borde, RoundedCornerShape(12.dp))
                        .clickableSimple { sel = r.tipo }.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text("${r.icono} ${r.nombre}", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(if (n > 0) "● $n conectada${if (n > 1) "s" else ""}" else "Sin conectar", color = if (n > 0) c.ok else c.textoSuave, fontSize = 11.sp)
                }
            }
        }
        avisoLiberar?.let { Aviso(it); Text("Entendido", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickableSimple { avisoLiberar = null }.padding(4.dp)) }
        val red = REDES.first { it.tipo == sel }
        val conectados = todos.filter { it.t("tipo") == sel }
        Tarjeta("${red.icono} ${red.nombre}") {
            Ayuda(red.detalle)
            // Meta devolvió varias páginas y ninguna se guardó todavía: hay que elegir.
            if (esAdmin && pendientes.isNotEmpty()) {
                Aviso("Tienes ${pendientes.size} cuentas. ¿Cuál quieres conectar? Solo la que elijas se conecta.", "info")
                pendientes.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.s("nombre") ?: p.t("cuentaExterna"), color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(p.t("cuentaExterna"), color = c.textoSuave, fontSize = 11.sp)
                        }
                        Boton("Conectar esta", habilitado = ocupado == null, modifier = Modifier) {
                            ocupado = p.t("cuentaExterna")
                            scope.launch {
                                val r = guardarAjuste(porDefecto = "No se pudo conectar. Vuelve a intentarlo.") {
                                    AjustesRepo.enviar(HttpMethod.Post, "/api/staff/mensajes/canales/$sel/elegir", buildJsonObject { put("cuentaExterna", p.t("cuentaExterna")) })
                                }
                                ocupado = null
                                if (r.registrada) Toaster.exito("Cuenta conectada")
                                recarga++
                            }
                        }
                    }
                }
            }
            conectados.forEach { canal ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, c.borde, RoundedCornerShape(10.dp)).padding(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(canal.s("nombre")?.takeIf { it.isNotBlank() } ?: canal.t("cuentaExterna"), color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Conectado el ${fechaLima(canal.t("creadoEn"))} · ${if (canal.b("activo")) "Activo" else "Apagado"}", color = c.textoSuave, fontSize = 11.sp)
                    }
                    if (esAdmin) Text(
                        if (ocupado == canal.t("id")) "…" else if (canal.t("tipo") == "whatsapp") "Liberar mi número" else "Desconectar",
                        color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickableSimple { if (ocupado == null) confirmar = canal to (canal.t("tipo") == "whatsapp") }.padding(6.dp),
                    )
                }
            }
            if (sel == "whatsapp" && conectados.isNotEmpty()) {
                Ayuda("💡 ¿Quieres que Sani deje de responder un tiempo? No toques el número: apágalo con el interruptor en Sani → Cómo se comporta.")
            }
            when {
                !esAdmin -> Ayuda("Solo el administrador de la clínica puede conectar o desconectar redes.")
                sel == "whatsapp" -> {
                    if (conectados.isNotEmpty() && !ctx.multiSede) {
                        Ayuda("Tu clínica ya tiene su número conectado. Si abres otra sede, podrás conectar el número de esa sede aquí.")
                    } else {
                        // El Embedded Signup de Meta funciona con su SDK de JavaScript: se hace en la web.
                        Boton("Conectar WhatsApp en la web ↗", primario = false) { acciones.abrirUrl("${Supabase.SITE_URL}/configuracion?tab=canales") }
                        Ayuda("El asistente de Meta para conectar el número se abre en el navegador (inicia sesión con tu misma cuenta). Al volver, tu número aparece aquí.")
                    }
                }
                sel in TIPOS_OAUTH -> {
                    if (conectados.isNotEmpty()) Ayuda("Para usar otra cuenta de ${red.nombre}, primero desconecta la actual.")
                    else {
                        Boton(if (ocupado == "url") "Abriendo…" else "Conectar ${red.nombre}", primario = false, habilitado = ocupado == null) {
                            ocupado = "url"
                            scope.launch {
                                val (r, e) = AjustesRepo.leerObjeto("/api/staff/mensajes/canales/$sel/url")
                                ocupado = null
                                val url = r?.s("url")
                                if (url != null) acciones.abrirUrl(url) else Toaster.error(e ?: "No se pudo abrir la conexión con ${red.nombre}")
                            }
                        }
                        Ayuda("Se abre el navegador para que autorices con ${red.nombre}. Al volver a la app tu cuenta aparece aquí.${if (sel != "tiktok") " Si administras varias páginas, te dejamos elegir cuál." else ""}")
                    }
                    if (sel == "tiktok") Aviso("📌 TikTok solo permite publicar videos desde aplicaciones como Sania — sus mensajes directos no llegan al panel. Tus pacientes siguen escribiendo por WhatsApp, Instagram y Facebook.", "info")
                }
            }
        }
    }
}

// ─── 💳 Cobros por la app (Mercado Pago) ─────────────────────────────────────

@Composable
internal fun SeccionCobrosOnline(d: JsonObject, onVolver: () -> Unit, onCambio: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val acciones = recordarAcciones()
    val esAdmin = d.b("esAdmin")
    var estado by remember { mutableStateOf<JsonObject?>(null) }
    var recarga by remember { mutableIntStateOf(0) }
    var recargo by remember { mutableStateOf("0") }
    var ocupado by remember { mutableStateOf(false) }
    var confirmarDesconectar by remember { mutableStateOf(false) }
    var errorEstado by remember { mutableStateOf<String?>(null) }
    // Al volver del navegador (autorización de Mercado Pago) se relee solo.
    LaunchedEffect(recarga, Reanudacion.contador) {
        val (r, e) = AjustesRepo.leerObjeto("/api/staff/mp/estado")
        if (r != null) { estado = r; errorEstado = null; recargo = (r.n("recargoPct") ?: 0.0).let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } }
        else if (estado == null) errorEstado = e ?: "No se pudo leer el estado de Mercado Pago"
    }
    fun conectar() {
        ocupado = true
        scope.launch {
            val r = guardarAjuste(porDefecto = "No se pudo iniciar la conexión") { AjustesRepo.enviar(HttpMethod.Post, "/api/staff/mp/conectar", JsonObject(emptyMap())) }
            ocupado = false
            r.cuerpo?.s("url")?.takeIf { r.registrada }?.let {
                acciones.abrirUrl(it)
                Toaster.info("Termina de autorizar en Mercado Pago. Al volver, esta pantalla se actualiza sola.")
            }
        }
    }

    if (confirmarDesconectar) AlertaConTeclado(
        onDismissRequest = { confirmarDesconectar = false },
        title = { Text("¿Desconectar Mercado Pago?", fontWeight = FontWeight.Bold) },
        text = { Text("Tus pacientes dejarán de poder pagar desde la app. Los pagos ya cobrados no se tocan.") },
        confirmButton = {
            TextButton(onClick = {
                confirmarDesconectar = false
                scope.launch {
                    val r = guardarAjuste(Gestion.ELIMINANDO, "No se pudo desconectar") { AjustesRepo.enviar(HttpMethod.Delete, "/api/staff/mp/estado", JsonObject(emptyMap())) }
                    if (r.registrada) { Toaster.exito("Cuenta desconectada"); recarga++; onCambio() }
                }
            }) { Text("Desconectar", color = c.error, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = { confirmarDesconectar = false }) { Text("Cancelar", color = c.textoSuave) } },
        containerColor = c.superficie,
    )

    SubPantalla("Cobros por la app", onVolver) {
        val e = estado
        if (e == null && errorEstado != null) { ErrorCarga(errorEstado) { errorEstado = null; recarga++ }; return@SubPantalla }
        if (e == null) { CargandoLista(); return@SubPantalla }
        Ayuda("Conecta tu cuenta de Mercado Pago y tus pacientes podrán pagar su tratamiento desde la app, con tarjeta o Yape. El dinero llega directo a tu cuenta, no pasa por Sania.")
        val conectado = e.b("conectado") && !e.b("vencido")
        val pct = e.n("comisionPct") ?: 3.5
        val pctTxt = if (pct % 1.0 == 0.0) pct.toInt().toString() else pct.toString()
        if (conectado) {
            Aviso("✓ Tu cuenta está conectada" + (e.s("cuentaMp")?.let { " · Mercado Pago n.º $it" } ?: "") + "\nTus pacientes ya pueden pagar desde la app." +
                when (e.bn("aceptaYape")) { true -> " Tu cuenta acepta Yape."; false -> " Tu cuenta no tiene Yape habilitado: solo podrán pagar con tarjeta."; else -> "" }, "ok")
            e.i("diasParaVencer")?.takeIf { it < 30 }?.let { Aviso("Tu conexión vence en $it día${if (it != 1) "s" else ""}. Vuelve a conectarla para que tus pacientes sigan pudiendo pagar.") }
            Tarjeta {
                Ayuda("De cada pago se descuentan dos cosas, ambas sobre el total cobrado: la comisión de Mercado Pago (la de su pasarela, como con cualquier POS) y el $pctTxt% de Sania. El resto llega a tu cuenta de Mercado Pago.")
                Ayuda("Para que tus pacientes vean el botón de pagar, enciéndelo en «App del paciente».")
            }
            Tarjeta("Recargo por pagar con tarjeta") {
                Ayuda("Se suma al cobro para cubrir las comisiones. Déjalo en 0 si prefieres asumirlas tú.")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Campo(null, recargo, { recargo = it.filter { ch -> ch.isDigit() || ch == '.' } }, teclado = KeyboardType.Decimal, habilitado = esAdmin, max = 5, modifier = Modifier.weight(1f))
                    Text("%", color = c.textoSuave)
                    if (esAdmin) Boton("Guardar", primario = false, habilitado = !ocupado, modifier = Modifier.weight(1f)) {
                        val v = recargo.toDoubleOrNull() ?: 0.0
                        ocupado = true
                        scope.launch {
                            val r = guardarAjuste { AjustesRepo.enviar(HttpMethod.Patch, "/api/staff/mp/estado", buildJsonObject { put("recargoPct", v) }) }
                            ocupado = false
                            if (r.registrada) Toaster.exito(if (v > 0) "Recargo del $recargo% guardado" else "Recargo desactivado")
                        }
                    }
                }
                (recargo.toDoubleOrNull() ?: 0.0).takeIf { it > 0 }?.let { p ->
                    Ayuda("Un abono de S/ 400 se cobrará como ${pe.saniape.app.data.staff.soles(400 * (1 + p / 100))}, y al tratamiento se le abonarán los S/ 400.")
                }
            }
            if (esAdmin) {
                Boton("🔄 Volver a conectar", primario = false, habilitado = !ocupado) { conectar() }
                Boton("Desconectar", peligro = true) { confirmarDesconectar = true }
                Ayuda("«Volver a conectar» usa la cuenta que tengas abierta en Mercado Pago; no permite elegir otra. Para conectar una cuenta distinta, abre Sania en una ventana de incógnito del navegador y conéctala desde Configuración.")
            }
        } else {
            if (e.b("vencido")) Aviso("Tu conexión con Mercado Pago venció. Tus pacientes no pueden pagar por la app hasta que la renueves.", "error")
            Tarjeta {
                Ayuda("• Tus pacientes pagan su saldo desde el celular, sin ir a la clínica\n• Con tarjeta o Yape, según lo que acepte tu cuenta\n• El pago entra solo a la ficha del paciente y a tu caja del día\n• Sania cobra $pctTxt% por pago, y Mercado Pago su comisión de pasarela — como con cualquier POS", c.texto)
            }
            Aviso("Antes de conectar: verifica tu cuenta. Tu cuenta de Mercado Pago debe tener la identidad validada (DNI y foto del rostro, desde su app). Si no la tiene, sus controles rechazan todos los cobros — y el paciente solo ve un «pago rechazado» sin explicación.")
            when {
                e.b("enEspera") -> Aviso("Casi listo: estamos terminando de habilitar los cobros con Mercado Pago. Te avisamos apenas puedas conectar tu cuenta.", "info")
                esAdmin -> Boton(if (ocupado) "Abriendo Mercado Pago…" else "Conectar Mercado Pago", habilitado = !ocupado) { conectar() }
                else -> Ayuda("Solo el administrador puede conectar la cuenta de cobros.")
            }
        }
    }
}

// ─── 🔔 Recordatorios por WhatsApp ──────────────────────────────────────────

@Composable
internal fun SeccionNotificaciones(d: JsonObject, onVolver: () -> Unit, onCambio: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val cfg = d.o("config") ?: JsonObject(emptyMap())
    val horas = (d.o("catalogos")?.objetos("horasRecordatorio") ?: emptyList()).map { it.t("valor") to it.t("etiqueta") }
    var activo by remember { mutableStateOf(cfg.b("recordatoriosActivo")) }
    var hora by remember { mutableStateOf(cfg.t("recordatoriosHora").ifBlank { "7" }) }
    var plantilla by remember { mutableStateOf(cfg.t("plantillaRecordatorioSede")) }
    var guardando by remember { mutableStateOf(false) }
    val multiSede = d.b("multiSede")

    SubPantalla("Recordatorios por WhatsApp", onVolver) {
        if (d.o("plan")?.b("whatsapp") != true) {
            CandadoPlan("Las notificaciones por WhatsApp son del plan Plus", "Envía recordatorios automáticos de citas por WhatsApp con la API oficial de Meta. Disponible en el plan Plus.")
            return@SubPantalla
        }
        Ayuda("Activa o desactiva los recordatorios automáticos de citas para tus pacientes.")
        Tarjeta {
            FilaInterruptor("Recordatorios por WhatsApp", "Envía un mensaje automático con el link para confirmar asistencia.", activo) { activo = it }
        }
        if (activo) {
            Tarjeta("¿A partir de qué hora?") {
                ChipsEleccion(horas, hora) { hora = it }
                Ayuda("El mensaje sale el mismo día de la cita. La mayoría se agenda ese día, así que avisar la víspera no alcanzaría a casi nadie.")
            }
            Tarjeta("Esto es lo que recibe tu paciente") {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.fondo).border(1.dp, c.borde, RoundedCornerShape(10.dp)).padding(10.dp)) {
                    Text("Hola María 👋 Te recordamos tu cita el martes 18 de junio a las 10:00 a. m. Por favor confírmanos si asistirás:", color = c.texto, fontSize = 13.sp)
                    Text("Confirmar · Reprogramar · Cancelar", color = c.ok, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                }
                Ayuda("El texto lo aprueba Meta y no se puede cambiar. Cuando el paciente toca un botón, su respuesta llega a Mensajes.")
            }
            if (multiSede) Tarjeta("Plantilla con la sede (opcional)") {
                Campo(null, plantilla, { plantilla = it }, placeholder = "recordatorio_cita_sede", mayusculas = KeyboardCapitalization.None, max = 512)
                Ayuda("Nombre de una plantilla aprobada en Meta con 4 parámetros, en este orden: {{1}} nombre del paciente, {{2}} fecha, {{3}} hora y {{4}} la sede de la cita. Vacío = la plantilla de siempre, sin la sede.")
            }
        }
        Boton(if (guardando) "Guardando…" else "Guardar", habilitado = !guardando) {
            if (multiSede && plantilla.isNotBlank() && !Regex("^[a-z0-9_]{1,512}$").matches(plantilla.trim())) { Toaster.error("Nombre de plantilla inválido"); return@Boton }
            guardando = true
            scope.launch {
                val r = guardarAjuste {
                    AjustesRepo.guardarSeccion("notificaciones", buildJsonObject {
                        put("activo", activo); put("hora", hora); if (multiSede) put("plantillaSede", plantilla.trim())
                    })
                }
                guardando = false
                if (r.registrada) { Toaster.exito("Guardado"); onCambio() }
            }
        }
    }
}
