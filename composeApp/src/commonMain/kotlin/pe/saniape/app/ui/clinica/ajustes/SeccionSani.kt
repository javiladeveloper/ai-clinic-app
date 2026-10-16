package pe.saniape.app.ui.clinica.ajustes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.http.HttpMethod
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.offline.ResultadoEscritura
import pe.saniape.app.data.staff.AjustesRepo
import pe.saniape.app.data.staff.CLAVE_BIENVENIDA
import pe.saniape.app.data.staff.CLAVE_CAPTACION_NUMEROS
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.MAX_BIENVENIDA
import pe.saniape.app.data.staff.ModoPrecioRepetido
import pe.saniape.app.data.staff.OPCIONES_ESCALAR_PRECIO
import pe.saniape.app.data.staff.OPCIONES_HORAS_SEGUIMIENTO
import pe.saniape.app.data.staff.SeguimientoVentana
import pe.saniape.app.data.staff.bienvenidaDivulga
import pe.saniape.app.data.staff.cambioPrecioRepetido
import pe.saniape.app.data.staff.etiquetaHorasSeguimiento
import pe.saniape.app.data.staff.modoPrecioRepetido
import pe.saniape.app.data.staff.numerosCaptacionValidos
import pe.saniape.app.data.staff.seguimientoDeComportamiento
import pe.saniape.app.data.staff.validarBienvenida
import pe.saniape.app.data.staff.vistaPreviaBienvenida
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.theme.Sania

/** Lo que trae `POST /api/staff/leadai/sincronizar?vista=1` en `ajustes` (null = backend viejo: se oculta). */
private data class AjustesConversacion(
    val nombreClinica: String,
    val bienvenidaPorDefecto: String,
    val bienvenidaPropia: String,
    val numeros: Map<String, Int>,
    val trato: String,
)

private fun ajustesDe(o: JsonObject?): AjustesConversacion? {
    val a = o?.o("ajustes") ?: return null
    val porDefecto = (a["bienvenidaPorDefecto"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
    return AjustesConversacion(
        nombreClinica = a.s("nombreClinica").orEmpty(),
        bienvenidaPorDefecto = porDefecto,
        bienvenidaPropia = (a["bienvenidaPropia"] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty(),
        numeros = numerosCaptacionValidos(a["numerosCaptacion"]),
        trato = if (a.s("trato") == "usted") "usted" else "tu",
    )
}

/** "Solo lectura" de soporte: el servidor responde SOLO_LECTURA. */
private fun esSoloLectura(r: ResultadoEscritura) = r.rechazo?.codigo == "SOLO_LECTURA"

/**
 * 🤖 Sani → Ajustes: cómo CONVERSA Sani (gemelo de /mensajes/ajustes de la web,
 * ConversacionSani.tsx). SOLO el Admin (el servidor también lo exige) y con el
 * candado de solo lectura de soporte. Cada perilla se guarda al instante
 * (optimista; si falla, vuelve atrás):
 *  · Seguimiento antes de que cierre la ventana de 24 h (encendido + horas 12–22),
 *    en LeadAI vía /api/staff/mensajes/comportamiento.
 *  · Precio repetido: "responder siempre con amabilidad" o "pasar a una persona
 *    tras N preguntas" (configuracion.sani_captacion_numeros).
 *  · Bienvenida editable con vista previa (la divulgación integrada y el trato de
 *    usted) y "Restablecer el de Sani" (configuracion.sani_bienvenida).
 * Backend viejo: un campo que no llega se oculta (nunca un interruptor que no hace nada).
 */
@Composable
internal fun SeccionSani(d: JsonObject, ctx: ContextoStaff, volver: () -> Unit) {
    val c = Sania.colors
    var recarga by remember { mutableIntStateOf(0) }
    var cargando by remember { mutableStateOf(true) }
    var inactivo by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var seguimiento by remember { mutableStateOf<SeguimientoVentana?>(null) }
    var ajustes by remember { mutableStateOf<AjustesConversacion?>(null) }
    var soloLectura by remember { mutableStateOf(d.b("soloLectura")) }

    LaunchedEffect(recarga) {
        if (!ctx.esAdmin) { cargando = false; return@LaunchedEffect }
        coroutineScope {
            val dComp = async { AjustesRepo.leerConEstado("/api/staff/mensajes/comportamiento") }
            // ?vista=1 devuelve el playbook SIN subirlo, con los ajustes de la conversación.
            val dVista = async { AjustesRepo.enviar(HttpMethod.Post, "/api/staff/leadai/sincronizar?vista=1", JsonObject(emptyMap())) }
            val comp = dComp.await()
            val o = comp.json as? JsonObject
            when {
                o != null -> { seguimiento = seguimientoDeComportamiento(o); inactivo = false; error = null }
                comp.status == 403 && comp.error == "leadai_inactivo" -> inactivo = true
                comp.status == 403 -> { seguimiento = null; error = comp.error }
                else -> { seguimiento = null; error = comp.error }
            }
            val vista = dVista.await()
            ajustes = if (vista.registrada) ajustesDe(vista.cuerpo) else null
        }
        cargando = false
    }

    SubPantalla("🤖 Sani: cómo conversa", volver) {
        when {
            !ctx.esAdmin -> Aviso("Solo el administrador de la clínica cambia cómo conversa Sani.", "info")
            cargando -> pe.saniape.app.ui.CargandoLista()
            inactivo -> Aviso("Sani no está activo en tu clínica. Contrátalo para que atienda tu WhatsApp y aquí podrás ajustar cómo conversa.", "info")
            else -> {
                if (soloLectura) Aviso("Modo de solo lectura (soporte): puedes ver los ajustes, no cambiarlos.", "info")
                error?.let { e ->
                    Aviso(e, "error")
                    Boton("Reintentar", primario = false) { cargando = true; recarga++ }
                }
                seguimiento?.takeIf { it.disponible }?.let { s ->
                    TarjetaSeguimiento(s, ctx, soloLectura, onSoloLectura = { soloLectura = true }) { seguimiento = it }
                }
                val a = ajustes
                if (a != null) {
                    TarjetaPrecioRepetido(a.numeros, soloLectura, onSoloLectura = { soloLectura = true })
                    TarjetaBienvenida(a, soloLectura, onSoloLectura = { soloLectura = true }) { ajustes = it }
                } else if (seguimiento?.disponible != true && error == null) {
                    Aviso("Estos ajustes todavía no están disponibles para tu clínica. Actualiza más tarde.", "info")
                }
            }
        }
    }
}

@Composable
private fun CabeceraSani(emoji: String, titulo: String, detalle: String) {
    val c = Sania.colors
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(c.chipBg), contentAlignment = Alignment.Center) { Text(emoji, fontSize = 18.sp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, color = c.texto, fontSize = Sania.txt.seccion, fontWeight = FontWeight.Bold)
            Text(detalle, color = c.textoSuave, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

/** Seguimiento antes de que cierre la ventana (LeadAI /mi-plan). */
@Composable
private fun TarjetaSeguimiento(
    inicial: SeguimientoVentana,
    ctx: ContextoStaff,
    soloLectura: Boolean,
    onSoloLectura: () -> Unit,
    onCambio: (SeguimientoVentana) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var valor by remember(inicial) { mutableStateOf(inicial) }
    var guardando by remember { mutableStateOf(false) }
    val cita = (if (ctx.flujo.usaConsulta) ctx.flujo.labelConsulta else ctx.flujo.labelEvaluacion).ifBlank { "cita" }.lowercase()

    fun guardar(cambios: JsonObject, nuevo: SeguimientoVentana) {
        if (guardando || soloLectura) return
        val previo = valor
        valor = nuevo
        guardando = true
        scope.launch {
            val r = guardarAjuste { AjustesRepo.enviar(HttpMethod.Patch, "/api/staff/mensajes/comportamiento", cambios) }
            guardando = false
            if (r.registrada) { Toaster.exito("Listo, Sani ya cambió"); onCambio(nuevo) }
            else { valor = previo; if (esSoloLectura(r)) onSoloLectura() }
        }
    }

    Tarjeta {
        CabeceraSani("🤔", "Cuando alguien pregunta y no vuelve a escribir", "Alguien consulta por WhatsApp, Sani le responde y la conversación queda ahí.")
        FilaInterruptor(
            titulo = "Retomar la conversación antes de que se cierre",
            detalle = "A quien recibió tu bienvenida o una respuesta y no volvió a escribir, Sani le manda un solo mensaje, " +
                "escrito con lo que esa persona preguntó, invitándola con calma a separar su $cita. Sin un \"¿te agendo?\" seco.",
            activo = valor.activo,
            habilitado = !guardando && !soloLectura,
        ) { on -> guardar(buildJsonObject { put("seguimientoVentanaActivo", on) }, valor.copy(activo = on)) }
        if (valor.activo) {
            pe.saniape.app.ui.clinica.pacientes.EtqForm("¿Cuándo?")
            val opciones = (OPCIONES_HORAS_SEGUIMIENTO + if (valor.horas !in OPCIONES_HORAS_SEGUIMIENTO) listOf(valor.horas) else emptyList()).sorted()
            ChipsEleccion(
                opciones = opciones.map { it.toString() to "~$it h" },
                elegido = valor.horas.toString(),
                deshabilitados = if (guardando || soloLectura) opciones.map { it.toString() }.toSet() else emptySet(),
            ) { h -> h.toIntOrNull()?.let { n -> guardar(buildJsonObject { put("seguimientoVentanaHoras", n) }, valor.copy(horas = n)) } }
            Ayuda(etiquetaHorasSeguimiento(valor.horas) + ".")
            Ayuda(
                "Siempre dentro de las 24 horas que da WhatsApp (gratis) y nunca entre las 10 p. m. y las 8 a. m.: si la noche lo " +
                    "empuja fuera de plazo, sale antes. No se manda si la persona ya agendó, pidió que no le escriban, la atiende alguien " +
                    "del equipo o Sani está en pausa. Usa una respuesta de tu bolsa de IA.",
            )
        }
    }
}

/** Precio repetido: responder siempre con amabilidad o pasar a una persona tras N preguntas. */
@Composable
private fun TarjetaPrecioRepetido(numeros: Map<String, Int>, soloLectura: Boolean, onSoloLectura: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var modo by remember(numeros) { mutableStateOf(modoPrecioRepetido(numeros)) }
    var guardando by remember { mutableStateOf(false) }
    var botSync by remember { mutableStateOf<String?>(null) }

    fun elegir(escalar: Boolean, tras: Int) {
        if (guardando || soloLectura) return
        val previo = modo
        modo = ModoPrecioRepetido(escalar, tras)
        guardando = true
        botSync = null
        val (clave, n) = cambioPrecioRepetido(escalar, tras)
        scope.launch {
            val r = guardarAjuste { AjustesRepo.guardarConfigSani(CLAVE_CAPTACION_NUMEROS, buildJsonObject { put(clave, n) }) }
            guardando = false
            if (r.registrada) { botSync = r.cuerpo?.s("botSync"); Toaster.exito(mensajeGuardadoSani(botSync)) }
            else { modo = previo; if (esSoloLectura(r)) onSoloLectura() }
        }
    }

    @Composable
    fun Opcion(escalar: Boolean, titulo: String, detalle: String) {
        val activo = modo.escalar == escalar
        val forma = RoundedCornerShape(Sania.shape.md.dp)
        Row(
            Modifier.fillMaxWidth().clip(forma).background(if (activo) c.chipBg else c.superficie)
                .border(1.5.dp, if (activo) c.navy else c.borde, forma)
                .clickable(enabled = !guardando && !soloLectura, role = Role.RadioButton) { elegir(escalar, modo.tras) }
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.padding(top = 2.dp).size(16.dp).clip(CircleShape).background(if (activo) c.navy else c.superficie)
                .border(2.dp, if (activo) c.navy else c.borde, CircleShape))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(titulo, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(detalle, color = c.textoSuave, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }

    Tarjeta {
        CabeceraSani("💬", "Cuando preguntan el precio otra vez", "Casi siempre es que no lo leyó o no le quedó claro. ¿Qué hace Sani a partir de la tercera vez?")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Opcion(false, "Responder siempre con amabilidad",
                "Recomendado. Sani le vuelve a dar el dato con otras palabras y le ofrece agendar, sin hacerla sentir que ya preguntó.")
            Opcion(true, "Pasar a una persona", "Después de varias preguntas sobre el precio, Sani avisa al equipo y deja el chat en pausa.")
        }
        if (modo.escalar) {
            pe.saniape.app.ui.clinica.pacientes.EtqForm("¿Después de cuántas veces?")
            val opciones = (OPCIONES_ESCALAR_PRECIO + if (modo.tras !in OPCIONES_ESCALAR_PRECIO) listOf(modo.tras) else emptyList()).sorted()
            ChipsEleccion(
                opciones = opciones.map { it.toString() to "Tras $it preguntas" },
                elegido = modo.tras.toString(),
                deshabilitados = if (guardando || soloLectura) opciones.map { it.toString() }.toSet() else emptySet(),
            ) { v -> v.toIntOrNull()?.let { elegir(true, it) } }
        }
        Ayuda("Si la persona pide hablar con alguien del equipo, Sani te la pasa siempre, elijas lo que elijas." +
            if (botSync == "pendiente") " Sani lo aprende en unos minutos." else "")
    }
}

/** Mensaje de bienvenida con vista previa y "Restablecer el de Sani". */
@Composable
private fun TarjetaBienvenida(
    a: AjustesConversacion,
    soloLectura: Boolean,
    onSoloLectura: () -> Unit,
    onGuardada: (AjustesConversacion) -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var guardada by remember(a.bienvenidaPropia) { mutableStateOf(a.bienvenidaPropia) }
    var borrador by remember(a.bienvenidaPropia, a.bienvenidaPorDefecto) { mutableStateOf(a.bienvenidaPropia.ifEmpty { a.bienvenidaPorDefecto }) }
    var guardando by remember { mutableStateOf(false) }
    val actual = guardada.ifEmpty { a.bienvenidaPorDefecto }
    val cambio = borrador.trim() != actual.trim()
    val esDefecto = guardada.isEmpty()
    val vista = vistaPreviaBienvenida(borrador, a.nombreClinica, a.trato)

    fun guardar(texto: String) {
        if (guardando || soloLectura) return
        // Guardar exactamente el de por defecto = volver al de por defecto (sigue los cambios del playbook).
        val valor = if (texto.trim() == a.bienvenidaPorDefecto.trim()) "" else texto.trim()
        validarBienvenida(valor)?.let { Toaster.error(it); return }
        guardando = true
        scope.launch {
            val r = guardarAjuste { AjustesRepo.guardarConfigSani(CLAVE_BIENVENIDA, JsonPrimitive(valor)) }
            guardando = false
            if (r.registrada) {
                guardada = valor
                borrador = valor.ifEmpty { a.bienvenidaPorDefecto }
                Toaster.exito(mensajeGuardadoSani(r.cuerpo?.s("botSync")))
                onGuardada(a.copy(bienvenidaPropia = valor))
            } else if (esSoloLectura(r)) onSoloLectura()
        }
    }

    Tarjeta {
        CabeceraSani("👋", "Mensaje de bienvenida", "Lo primero que lee quien te escribe. Escríbelo como lo diría tu recepción.")
        Campo(null, borrador, { borrador = it }, multilinea = true, lineas = 4, max = MAX_BIENVENIDA, habilitado = !soloLectura && !guardando)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Restablecer el de Sani", color = if (guardando || (esDefecto && !cambio) || soloLectura) c.textoSuave else c.navy,
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = !guardando && !(esDefecto && !cambio) && !soloLectura, role = Role.Button) {
                        if (esDefecto) borrador = a.bienvenidaPorDefecto else guardar("")
                    }.padding(vertical = 8.dp),
            )
            Spacer(Modifier.weight(1f))
        }
        if (cambio) {
            Boton(if (guardando) "Guardando…" else "Guardar bienvenida", habilitado = !guardando && borrador.isNotBlank() && !soloLectura) { guardar(borrador) }
        }
        // Vista previa: lo que de verdad le llega al paciente por WhatsApp.
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.chipBg).padding(10.dp)) {
            Text("ASÍ LO VERÁ TU PACIENTE", color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(6.dp))
            Text(
                vista.ifEmpty { "…" }, color = c.texto, fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.fillMaxWidth(0.9f).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 14.dp))
                    .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Ayuda(
            "Sale cuando la persona solo saluda (\"hola\", \"info\"). Si su primer mensaje ya trae una pregunta, Sani le responde directo, " +
                "sin la bienvenida completa." +
                if (!bienvenidaDivulga(borrador) && borrador.isNotBlank()) " WhatsApp exige avisar que responde un asistente virtual: como tu " +
                    "texto no lo dice, Sani agrega una frase corta después del saludo (la ves arriba)." else "",
        )
    }
}
