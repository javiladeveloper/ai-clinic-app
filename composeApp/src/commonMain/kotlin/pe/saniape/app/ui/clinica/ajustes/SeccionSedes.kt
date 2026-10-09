package pe.saniape.app.ui.clinica.ajustes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.http.HttpMethod
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.staff.AjustesRepo
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.soles
import pe.saniape.app.data.staff.MONEDAS_ELEGIBLES
import pe.saniape.app.data.staff.PAISES_SOPORTADOS
import pe.saniape.app.data.staff.RegionalSedeForm
import pe.saniape.app.data.staff.SedesRegionalRepo
import pe.saniape.app.data.staff.StaffContextoRepo
import pe.saniape.app.data.staff.ZONAS_SOPORTADAS
import pe.saniape.app.data.staff.elegirPaisSede
import pe.saniape.app.data.staff.errorRegionalSede
import pe.saniape.app.data.staff.formatearDinero
import pe.saniape.app.data.staff.paisPorCodigo
import pe.saniape.app.ui.AlertaConTeclado
import pe.saniape.app.ui.CargandoLista
import pe.saniape.app.ui.Gestion
import pe.saniape.app.ui.ManejarAtras
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.equipo.Pastilla
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.reservar.recordarSolicitarUbicacion
import pe.saniape.app.ui.theme.Sania

/** Datos de una sede (FormDatosSede de la web). */
internal data class DatosSede(
    val nombre: String = "", val direccion: String = "", val referencia: String = "", val ciudad: String = "",
    val distrito: String = "", val telefono: String = "", val lat: Double? = null, val lng: Double? = null,
) {
    fun json() = buildJsonObject {
        put("nombre", nombre); put("direccion", direccion); put("referencia", referencia); put("ciudad", ciudad)
        put("distrito", distrito); put("telefono", telefono); put("lat", lat); put("lng", lng)
    }
    companion object {
        fun de(o: JsonObject?) = if (o == null) DatosSede() else DatosSede(
            o.t("nombre"), o.t("direccion"), o.t("referencia"), o.t("ciudad"), o.t("distrito"), o.t("telefono"), o.n("lat"), o.n("lng"),
        )
    }
}

/**
 * 🏢 Sedes / locales (multisede), gemelo de GestionSedes + EditorSede + el
 * asistente de la web. Pacientes compartidos (o por sede si soporte lo activó);
 * cada cita y cobro lleva su sede. El número de WhatsApp de cada sede se
 * conecta en la web (Embedded Signup de Meta); contratar más sedes, en
 * Suscripción (web).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SeccionSedes(d: JsonObject, ctx: ContextoStaff, onVolver: () -> Unit, onCambio: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val acciones = recordarAcciones()
    var datos by remember { mutableStateOf<JsonObject?>(null) }
    var recarga by remember { mutableIntStateOf(0) }
    var editar by remember { mutableStateOf<JsonObject?>(null) }
    var nueva by remember { mutableStateOf(false) }
    var asistente by remember { mutableStateOf(false) }
    var borrar by remember { mutableStateOf<JsonObject?>(null) }
    var volver by remember { mutableStateOf(false) }
    var avisoPlan by remember { mutableStateOf(false) }
    var ocupado by remember { mutableStateOf(false) }

    var errorCarga by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(recarga) {
        val (r, e) = AjustesRepo.leerObjeto("${AjustesRepo.BASE}/sedes")
        when {
            r != null -> { datos = r; errorCarga = null }
            datos == null -> errorCarga = e ?: "No se pudieron leer las sedes"
            else -> Toaster.error(e ?: "No se pudieron leer las sedes")
        }
    }
    val cambio = { recarga++; onCambio() }
    fun accion(cuerpo: JsonObject, exito: String, gestion: Gestion = Gestion.GUARDANDO) {
        ocupado = true
        scope.launch {
            val r = guardarAjuste(gestion) { AjustesRepo.enviar(HttpMethod.Post, "${AjustesRepo.BASE}/sedes", cuerpo) }
            ocupado = false
            if (r.registrada) { Toaster.exito(exito); cambio() } else if (r.rechazo?.codigo == "SEDES_PAGADAS") avisoPlan = true
        }
    }

    val dd = datos
    ManejarAtras(activo = editar != null || nueva || asistente) { editar = null; nueva = false; asistente = false }
    if (dd != null) {
        editar?.let { s -> EditorSede(s, dd, ctx, onVolver = { editar = null }) { editar = null; cambio() }; return }
        if (nueva) { NuevaSede(onVolver = { nueva = false }) { nueva = false; cambio() }; return }
        if (asistente) {
            AsistenteMultisede(dd, onVolver = { asistente = false }, onActivado = { cambio() }) { asistente = false }
            return
        }
    }

    borrar?.let { s ->
        AlertaConTeclado(
            onDismissRequest = { borrar = null },
            title = { Text("Borrar sede", fontWeight = FontWeight.Bold) },
            text = { Text("¿Borrar ${s.t("nombre")}? Solo se puede si todavía no tiene citas, cobros ni personal. Si ya se usó, desactívala: deja de aparecer y su historial se conserva.") },
            confirmButton = { TextButton(onClick = { borrar = null; accion(buildJsonObject { put("accion", "borrar"); put("id", s.t("id")) }, "Sede borrada", Gestion.ELIMINANDO) }) { Text("🗑 Borrar", color = c.error, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { borrar = null }) { Text("Cancelar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }
    if (avisoPlan && dd != null) AlertaConTeclado(
        onDismissRequest = { avisoPlan = false },
        title = { Text("Primero actualiza tu plan", fontWeight = FontWeight.Bold) },
        text = {
            val pagadas = dd.i("sedesPagadas") ?: 1
            Text("Tu suscripción actual cubre $pagadas ${if (pagadas == 1) "sede" else "sedes"}. Para agregar otra: entra a Suscripción, elige el número de sedes (cada sede adicional cuesta ${soles(dd.n("precioPorSede") ?: 0.0)}/mes en tu plan ${dd.t("planNombre")}) y completa el pago. Después vuelve aquí y créala — quedará habilitada al instante.")
        },
        confirmButton = { TextButton(onClick = { avisoPlan = false; acciones.abrirUrl("${Supabase.SITE_URL}/suscripcion") }) { Text("Actualizar mi plan ↗", color = c.navy, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { avisoPlan = false }) { Text("Ahora no", color = c.textoSuave) } },
        containerColor = c.superficie,
    )
    if (volver) AlertaConTeclado(
        onDismissRequest = { if (!ocupado) volver = false },
        title = { Text("Volver a un solo local", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("La agenda y la caja dejan de separarse por sede y nadie tendrá que elegir sede al entrar.")
                listOf(
                    Triple("ocultar", "↩ Ocultar las sedes (recomendado)", "No borra ni mueve nada. Puedes volver a activarlas cuando quieras y todo sigue donde estaba."),
                    Triple("deshacer", "Deshacer por completo", "Borra las sedes. Solo se puede si las otras sedes todavía no tienen citas ni cobros."),
                ).forEach { (modo, titulo, detalle) ->
                    pe.saniape.app.ui.clinica.equipo.OpcionElegible(titulo, detalle = detalle, elegida = modo == "ocultar") {
                        if (ocupado) return@OpcionElegible
                        ocupado = true
                        scope.launch {
                            val r = guardarAjuste(porDefecto = "No se pudo") { AjustesRepo.enviar(HttpMethod.Post, "/api/staff/sedes/desactivar", buildJsonObject { put("modo", modo) }) }
                            ocupado = false
                            if (r.registrada) {
                                volver = false
                                Toaster.exito(if (modo == "ocultar") "Listo: la clínica vuelve a trabajar como un solo local. Tus sedes quedan guardadas." else "Listo: se deshizo la multisede.")
                                cambio()
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { volver = false }) { Text("Cancelar", color = c.textoSuave) } },
        containerColor = c.superficie,
    )

    SubPantalla("Sedes / Locales", onVolver) {
        if (dd == null && errorCarga != null) { ErrorCarga(errorCarga) { errorCarga = null; recarga++ }; return@SubPantalla }
        if (dd == null) { CargandoLista(); return@SubPantalla }
        val esAdmin = dd.b("esAdmin")
        val puede = dd.b("puedeMultiSede")
        val activada = dd.b("activada")
        val sedes = dd.objetos("sedes")
        val activas = sedes.filter { it.t("estado") == "Activa" }
        val precio = soles(dd.n("precioPorSede") ?: 0.0)

        if (!puede && !activada) {
            CandadoPlan("Agrega varias sedes a tu plan", "Gestiona todas tus sedes en un solo sistema: pacientes compartidos entre locales y reportes consolidados por sede. Contrata un plan para activarlo.")
            return@SubPantalla
        }
        if (!activada) {
            Tarjeta {
                Text("Tu clínica trabaja como un solo local.", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Ayuda("Si abres otra sede, actívalas aquí: cada sede tiene su agenda, su caja, su horario y sus servicios; los pacientes se comparten entre todas y tú ves los reportes por sede o juntos. Cada sede adicional cuesta $precio/mes en tu plan ${dd.t("planNombre")}.")
                when {
                    !dd.b("esquemaListo") -> Ayuda("Disponible muy pronto.")
                    esAdmin -> Boton("🏢 Activar varias sedes") { if (dd.b("excedeActivar")) avisoPlan = true else asistente = true }
                    else -> Ayuda("Solo el administrador de la clínica puede activarlo.")
                }
            }
            return@SubPantalla
        }

        Ayuda(if (dd.b("pacientesPorSede")) "Cada sede tiene su agenda, su caja y sus propios pacientes." else "Cada sede tiene su agenda y su caja; los pacientes se comparten entre todas.")
        Tarjeta {
            Text("Pacientes por sede: ${if (dd.b("pacientesPorSede")) "activado" else "desactivado"}", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Ayuda((if (dd.b("pacientesPorSede")) "Cada paciente es de una sede y solo lo ve el personal de esa sede (y el administrador)." else "Los pacientes se comparten entre todas las sedes.") + " Lo activa soporte de Sania.")
            dd.i("sinAsignar")?.let { n ->
                if (n > 0) Aviso("⚠ $n ${if (n == 1) "paciente sin asignar" else "pacientes sin asignar"}. Asígnalos desde la lista de pacientes.")
                else Text("✓ Todos tienen sede", color = c.ok, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (!puede) Aviso("Tu plan actual no incluye varias sedes: la clínica funciona como un solo local hasta que lo renueves.")
        else if (activas.size < 2) Aviso("Hay una sola sede activa: la agenda y la caja se separarán cuando actives o crees otra.")
        if (activas.isNotEmpty()) Aviso("${activas.size} ${if (activas.size == 1) "sede activa" else "sedes activas"}" +
            (if (activas.size > 1) " · ${dd.t("planNombre")} + ${activas.size - 1} × $precio" else "") + " · ≈ ${soles(dd.n("precioTotal") ?: 0.0)}/mes", "info")
        if (esAdmin && puede) Boton("+ Nueva sede") { if (dd.b("excedeNueva")) avisoPlan = true else nueva = true }

        sedes.forEach { s ->
            val activa = s.t("estado") == "Activa"
            val principal = s.b("es_principal")
            Tarjeta {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(s.t("nombre"), color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (principal) Pastilla("Principal", c.navy, c.chipBg)
                    Pastilla(s.t("estado"), if (activa) c.ok else c.textoSuave, if (activa) c.okBg else c.borde)
                    if (!s.s("horarios_atencion").isNullOrBlank()) Text("⏰ horario propio", color = c.textoSuave, fontSize = 11.sp)
                }
                Ayuda(listOf(s.t("direccion"), s.t("distrito"), s.t("ciudad"), s.t("telefono")).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Sin dirección registrada" })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChipsEleccion(buildList {
                        add("config" to "⚙ Configurar")
                        if (esAdmin && !principal && activa) add("principal" to "★ Principal")
                        if (!(principal && activa)) add("estado" to if (activa) "Desactivar" else "Activar")
                        if (esAdmin && !principal) add("borrar" to "🗑 Borrar")
                    }, null, deshabilitados = if (ocupado) setOf("principal", "estado", "borrar") else emptySet()) { a ->
                        when (a) {
                            "config" -> editar = s
                            "principal" -> {
                                ocupado = true
                                scope.launch {
                                    val r = guardarAjuste(porDefecto = "No se pudo marcar como principal") {
                                        AjustesRepo.enviar(HttpMethod.Post, "/api/staff/sedes/principal", buildJsonObject { put("sedeId", s.t("id")) })
                                    }
                                    ocupado = false
                                    if (r.registrada) { Toaster.exito("${s.t("nombre")} es ahora la sede principal"); cambio() }
                                }
                            }
                            "estado" -> accion(buildJsonObject { put("accion", "estado"); put("id", s.t("id")) }, if (activa) "${s.t("nombre")} desactivada" else "${s.t("nombre")} activada")
                            "borrar" -> borrar = s
                        }
                    }
                }
            }
        }

        if (esAdmin) {
            Tarjeta("¿A qué sede va un cobro?") {
                Ayuda("Decide en qué caja cae un pago cuando la cita es de una sede y se cobra en otra.")
                val cobroEn = dd.t("cobroEn")
                listOf("sede_cita" to ("A la sede de la cita" to "El paciente paga donde se atiende (recomendado)."),
                    "sede_caja" to ("A la sede donde se cobra" to "Cae en la caja de quien registra el pago.")).forEach { (v, td) ->
                    pe.saniape.app.ui.clinica.equipo.OpcionElegible(td.first, detalle = td.second, elegida = cobroEn == v) {
                        if (v != cobroEn && !ocupado) accion(buildJsonObject { put("accion", "cobro-en"); put("valor", v) }, "Guardado")
                    }
                }
            }
            Ayuda("¿Algo no funciona como esperabas? Puedes volver a un solo local sin perder nada.")
            Boton("↩ Volver a un solo local", peligro = true) { volver = true }
        }
    }
}

/** Formulario de los datos de una sede (nombre, ciudad, distrito, dirección, referencia, teléfono y GPS). */
@Composable
private fun FormDatosSede(v: DatosSede, conGps: Boolean, onCambio: (DatosSede) -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var elegirCiudad by remember { mutableStateOf(false) }
    var elegirDistrito by remember { mutableStateOf(false) }
    var buscar by remember { mutableStateOf(false) }
    var capturando by remember { mutableStateOf(false) }
    // Se lee el último valor por ref: la respuesta del GPS llega después.
    val actual = androidx.compose.runtime.rememberUpdatedState(v)
    val pedirGps = recordarSolicitarUbicacion { p ->
        capturando = false
        if (p == null) { Toaster.error("No se pudo obtener la ubicación. Activa el GPS y permite el acceso."); return@recordarSolicitarUbicacion }
        val la = kotlin.math.round(p.first * 1e7) / 1e7
        val ln = kotlin.math.round(p.second * 1e7) / 1e7
        onCambio(actual.value.copy(lat = la, lng = ln))
        scope.launch {
            val u = AjustesRepo.ubicacionDePin(la, ln) ?: return@launch
            val b = actual.value
            onCambio(b.copy(direccion = u.s("direccion") ?: b.direccion, ciudad = u.s("ciudad") ?: b.ciudad, distrito = u.s("distrito") ?: b.distrito))
        }
    }
    if (elegirCiudad) DialogoCiudad({ ciudad -> elegirCiudad = false; onCambio(v.copy(ciudad = ciudad, distrito = if (ciudad != v.ciudad) "" else v.distrito)) }, { elegirCiudad = false })
    if (elegirDistrito) DialogoDistrito(v.ciudad, { elegirDistrito = false; onCambio(v.copy(distrito = it)) }, { elegirDistrito = false })
    if (buscar) DialogoBuscarDireccion(v.ciudad, v.lat, v.lng, { item ->
        buscar = false
        onCambio(v.copy(direccion = item.t("direccion"), ciudad = item.s("ciudad")?.takeIf { it.isNotBlank() } ?: v.ciudad,
            distrito = item.s("distrito")?.takeIf { it.isNotBlank() } ?: v.distrito, lat = item.n("lat"), lng = item.n("lng")))
    }, { buscar = false })

    Campo("Nombre de la sede *", v.nombre, { onCambio(v.copy(nombre = it)) }, placeholder = "Ej. Renova Arequipa", max = 80)
    Selector("Ciudad", v.ciudad, "Ej. Arequipa") { elegirCiudad = true }
    Selector("Distrito", v.distrito, "Elige de la lista") { elegirDistrito = true }
    Selector("Dirección (busca y elige)", v.direccion, if (v.ciudad.isBlank()) "Primero indica la ciudad" else "Ej. Av. Ejército 101", habilitado = v.ciudad.isNotBlank()) { buscar = true }
    Campo(null, v.direccion, { onCambio(v.copy(direccion = it)) }, placeholder = "O escríbela a mano", max = 300)
    Campo("Referencia", v.referencia, { onCambio(v.copy(referencia = it)) }, placeholder = "Piso, oficina, al costado de…", max = 300)
    Campo("Teléfono", v.telefono, { onCambio(v.copy(telefono = it)) }, placeholder = "Ej. 987 654 321", teclado = KeyboardType.Phone, max = 40)
    if (conGps) {
        EtqForm("Ubicación en el mapa")
        if (v.lat != null && v.lng != null) {
            Text("✓ GPS (${v.lat}, ${v.lng})", color = c.ok, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("Quitar ubicación", color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickableSimple { onCambio(v.copy(lat = null, lng = null)) }.padding(4.dp))
        } else Boton(if (capturando) "📡 Capturando…" else "📍 Usar mi ubicación actual", primario = false, habilitado = !capturando) { capturando = true; pedirGps() }
        Ayuda("O elige la dirección de la lista de arriba (trae sus coordenadas). El ajuste fino del pin sobre el mapa se hace en la web.")
    }
}

@Composable
private fun NuevaSede(onVolver: () -> Unit, onHecho: () -> Unit) {
    val scope = rememberCoroutineScope()
    var datos by remember { mutableStateOf(DatosSede()) }
    var guardando by remember { mutableStateOf(false) }
    SubPantalla("🏢 Nueva sede", onVolver, volver = "← Sedes") {
        Tarjeta { FormDatosSede(datos, conGps = false) { datos = it } }
        Ayuda("El horario, los servicios y el mapa los configuras después con «Configurar».")
        Boton(if (guardando) "Guardando…" else "Crear sede", habilitado = !guardando && datos.nombre.isNotBlank()) {
            guardando = true
            scope.launch {
                val r = guardarAjuste { AjustesRepo.enviar(HttpMethod.Post, "${AjustesRepo.BASE}/sedes", buildJsonObject { put("accion", "crear"); put("datos", datos.json()) }) }
                guardando = false
                if (r.registrada) { Toaster.exito("Sede creada"); onHecho() }
            }
        }
    }
}

/** Asistente "Activar varias sedes" (solo Admin): la principal, la nueva y el resumen. */
@Composable
private fun AsistenteMultisede(dd: JsonObject, onVolver: () -> Unit, onActivado: () -> Unit, onHecho: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val existente = dd.objetos("sedes").firstOrNull { it.b("es_principal") }
    val otras = dd.objetos("sedes").count { it.t("estado") == "Activa" && !it.b("es_principal") }
    var paso by remember { mutableIntStateOf(1) }
    var principal by remember { mutableStateOf(if (existente != null) DatosSede.de(existente) else DatosSede.de(dd.o("principalSugerida"))) }
    var nueva by remember { mutableStateOf(DatosSede()) }
    var enviando by remember { mutableStateOf(false) }
    var resultado by remember { mutableStateOf<JsonObject?>(null) }

    SubPantalla("🏢 Activar varias sedes", { if (!enviando) { if (resultado != null) onHecho() else onVolver() } }, volver = "← Sedes") {
        Text(listOf("Sede principal", "Nueva sede", "Listo").mapIndexed { i, t -> if (i + 1 == paso) "● $t" else if (i + 1 < paso) "✓ $t" else "○ $t" }.joinToString("   "),
            color = c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        when (paso) {
            1 -> {
                Ayuda("Tu local de hoy será la sede principal. Todo lo que ya tienes —citas, cobros, profesionales y horarios— queda en ella. No se borra nada.", c.texto)
                Ayuda("Revisa sus datos (los tomamos de tu clínica):")
                Tarjeta { FormDatosSede(principal, conGps = false) { principal = it } }
                Ayuda("El horario y el límite de pacientes a la misma hora quedan como los de la clínica. Después puedes darle a cada sede los suyos.")
                Boton("Siguiente →") { if (principal.nombre.isBlank()) Toaster.error("Ponle nombre a la sede principal") else paso = 2 }
            }
            2 -> {
                Ayuda(if (otras > 0) "Ya tienes ${if (otras == 1) "otra sede" else "$otras sedes más"} guardada(s): vuelven tal como estaban. Si quieres, agrega una más (opcional)."
                    else "Ahora tu segunda sede. Solo el nombre es obligatorio; lo demás lo completas luego.", c.texto)
                Tarjeta { FormDatosSede(nueva, conGps = false) { nueva = it } }
                Aviso("Al activar: cada persona elige al entrar en qué sede trabaja, y la agenda y la caja se separan por sede. Solo el administrador ve todas juntas. Si algo no te convence, puedes volver a un solo local sin perder nada.", "info")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Boton("← Atrás", primario = false, habilitado = !enviando, modifier = Modifier.weight(1f)) { paso = 1 }
                    Boton(if (enviando) "Activando…" else "Activar varias sedes", habilitado = !enviando && (nueva.nombre.isNotBlank() || otras > 0), modifier = Modifier.weight(1.4f)) {
                        enviando = true
                        scope.launch {
                            // La activación NO se corta a mitad aunque se cierre la pantalla
                            // (un tab, el sistema): el pedido y el aviso al padre terminan igual.
                            val r = withContext(NonCancellable) {
                                val r = guardarAjuste(porDefecto = "No se pudo activar") {
                                    AjustesRepo.enviar(HttpMethod.Post, "/api/staff/sedes/activar", buildJsonObject {
                                        put("principal", principal.json())
                                        put("nueva", if (nueva.nombre.isNotBlank()) nueva.json() else JsonNull)
                                    })
                                }
                                if (r.registrada) onActivado()
                                r
                            }
                            enviando = false
                            if (r.registrada) {
                                resultado = r.cuerpo ?: JsonObject(emptyMap())
                                r.cuerpo?.s("aviso")?.let { Toaster.info(it) }
                                paso = 3
                            }
                        }
                    }
                }
            }
            else -> {
                Aviso("✓ Varias sedes activadas", "ok")
                val movidos = resultado?.o("movidos") ?: JsonObject(emptyMap())
                val etiquetas = dd.o("etiquetasMovidos") ?: JsonObject(emptyMap())
                val conValor = movidos.keys.filter { (movidos.n(it) ?: 0.0) > 0 }
                if (conValor.isEmpty()) Ayuda("No había citas ni cobros que mover.")
                else {
                    Ayuda("Esto quedó en la sede principal:", c.texto)
                    conValor.forEach { k ->
                        val n = (movidos.n(k) ?: 0.0).toInt()
                        val nombres = (etiquetas[k] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: listOf(k, k)
                        Text("$n ${if (n == 1) nombres.getOrElse(0) { k } else nombres.getOrElse(1) { k }}", color = c.navy, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Ayuda("Siguiente paso: en Equipo, indica en qué sedes trabaja cada persona; y aquí, el horario y los servicios de cada sede.")
                Boton("Listo") { onHecho() }
            }
        }
    }
}

/** Configuración propia de UNA sede: datos, horario (vacío = el de la clínica) y servicios. */
@Composable
private fun EditorSede(s: JsonObject, dd: JsonObject, ctx: ContextoStaff, onVolver: () -> Unit, onHecho: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val acciones = recordarAcciones()
    var pestana by remember { mutableStateOf("datos") }
    var datos by remember { mutableStateOf(DatosSede.de(s)) }
    val horarioClinica = diasDe(dd.objetos("horarioClinica"))
    val propioGuardado = s.s("horarios_atencion")?.takeIf { it.isNotBlank() }
    var horarioPropio by remember { mutableStateOf(propioGuardado != null) }
    var horario by remember {
        mutableStateOf(propioGuardado?.let { txt ->
            runCatching { kotlinx.serialization.json.Json.parseToJsonElement(txt) as kotlinx.serialization.json.JsonArray }.getOrNull()
                ?.mapNotNull { it as? JsonObject }?.let { diasDe(it) }
        } ?: horarioClinica)
    }
    var limite by remember { mutableStateOf(s.i("limite_simultaneas")?.toString() ?: "") }
    var servicios by remember { mutableStateOf<List<JsonObject>?>(null) }
    var todos by remember { mutableStateOf<Boolean?>(null) }
    var elegidos by remember { mutableStateOf<Map<String, Pair<Boolean, String>>>(emptyMap()) }
    var guardando by remember { mutableStateOf(false) }
    // Multipaís: país/moneda/zona propios de la sede ("" = los de la clínica).
    // null = todavía no se leyó (o la base no tiene las columnas): no se muestra.
    var regionalGuardado by remember { mutableStateOf<RegionalSedeForm?>(null) }
    var regional by remember { mutableStateOf(RegionalSedeForm()) }
    var eligiendoRegional by remember { mutableStateOf<String?>(null) }   // "pais" | "moneda" | "zona"
    LaunchedEffect(s.t("id")) {
        SedesRegionalRepo.leer(s.t("id"))?.let { regionalGuardado = it; regional = it }
    }
    // Moneda con la que se muestran los precios de esta sede.
    val monedaSede = regional.moneda.ifBlank { ctx.moneda }

    // Los servicios se descargan solo si se abre su pestaña.
    LaunchedEffect(pestana == "servicios") {
        if (pestana != "servicios" || servicios != null) return@LaunchedEffect
        val (r, e) = AjustesRepo.leerObjeto("${AjustesRepo.BASE}/sedes?sedeId=${s.t("id")}")
        val sv = r?.o("servicios")
        if (sv == null) { Toaster.error(e ?: "No se pudieron leer los servicios"); return@LaunchedEffect }
        val lista = sv.objetos("lista")
        servicios = lista; todos = sv.b("todos")
        elegidos = lista.associate { it.t("procedimiento_id") to (it.b("ofrece") to (if (it.b("precioPropio")) precioEditable(it.n("precio") ?: 0.0) else "")) }
    }

    fun guardar() {
        if (datos.nombre.isBlank()) { pestana = "datos"; Toaster.error("Ponle un nombre a la sede"); return }
        val lim = limite.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
        if (limite.isNotBlank() && (lim == null || lim < 1)) { pestana = "horario"; Toaster.error("El límite debe ser 1 o más (o vacío)"); return }
        if (horarioPropio && horario.any { it.activo && it.apertura >= it.cierre }) { pestana = "horario"; Toaster.error("Revisa el horario: la hora de cierre debe ser después de la de apertura"); return }
        errorRegionalSede(regional)?.let { pestana = "datos"; Toaster.error(it); return }
        // undefined (null) = no se tocan; JsonNull = todos al precio normal; lista = solo esos.
        val serviciosJson: kotlinx.serialization.json.JsonElement? = if (servicios != null && todos != null) {
            if (todos == true) JsonNull
            else {
                val sel = elegidos.filter { it.value.first }
                if (sel.isEmpty()) { pestana = "servicios"; Toaster.error("Elige al menos un servicio o marca «Todos los servicios»"); return }
                buildJsonArray {
                    sel.forEach { (id, v) ->
                        add(buildJsonObject { put("procedimientoId", id); put("precio", v.second.trim().toDoubleOrNull()?.let { JsonPrimitive(it) } ?: JsonNull) })
                    }
                }
            }
        } else null
        guardando = true
        scope.launch {
            val r = guardarAjuste {
                AjustesRepo.enviar(HttpMethod.Post, "${AjustesRepo.BASE}/sedes", buildJsonObject {
                    put("accion", "editar"); put("id", s.t("id")); put("datos", datos.json())
                    put("horario", if (horarioPropio) diasJson(horario) else JsonNull)
                    put("limite", lim?.let { JsonPrimitive(it) } ?: JsonNull)
                    serviciosJson?.let { put("servicios", it) }
                })
            }
            if (r.registrada) {
                // País/moneda/zona: directo en la sede (como la web), solo si cambió algo.
                val antes = regionalGuardado
                val errRegional = if (antes != null && antes != regional) SedesRegionalRepo.guardar(s.t("id"), antes, regional) else null
                guardando = false
                if (errRegional != null) { Toaster.error("Datos guardados, pero no el país/moneda/zona: $errRegional"); return@launch }
                if (antes != null && antes != regional) runCatching { StaffContextoRepo.cargar() }
                Toaster.exito("Sede actualizada"); onHecho()
            } else guardando = false
        }
    }

    SubPantalla("✏ ${s.t("nombre")}", onVolver, volver = "← Sedes") {
        val tabs = listOf("datos" to "📍 Datos", "horario" to "⏰ Horario", "servicios" to "🩺 Servicios") + if (ctx.multiSede) listOf("whatsapp" to "💬 WhatsApp") else emptyList()
        ChipsEleccion(tabs, pestana) { pestana = it }
        when (pestana) {
            "datos" -> {
                Tarjeta { FormDatosSede(datos, conGps = true) { datos = it } }
                if (regionalGuardado != null) Tarjeta("🌎 País, moneda y hora") {
                    val paisClinica = paisPorCodigo(ctx.pais)?.nombre ?: ctx.pais
                    Ayuda("Solo si esta sede está en otro país. Vacío = lo de la clínica ($paisClinica · ${ctx.moneda}).")
                    Selector("País", regional.pais.takeIf { it.isNotBlank() }?.let { paisPorCodigo(it)?.nombre ?: it } ?: "El de la clínica ($paisClinica)") { eligiendoRegional = "pais" }
                    Spacer(Modifier.height(8.dp))
                    Selector("Moneda", regional.moneda.takeIf { it.isNotBlank() }?.let { m -> MONEDAS_ELEGIBLES.firstOrNull { it.first == m }?.second ?: m } ?: "La de la clínica (${ctx.moneda})") { eligiendoRegional = "moneda" }
                    Spacer(Modifier.height(8.dp))
                    Selector("Zona horaria", regional.zona.ifBlank { "La de la clínica (${ctx.zona})" }) { eligiendoRegional = "zona" }
                    val monedaAntes = regionalGuardado?.moneda?.ifBlank { null } ?: ctx.moneda
                    if (monedaSede != monedaAntes) {
                        Spacer(Modifier.height(8.dp))
                        Aviso("Los montos que ya se cobraron en esta sede pasarán a leerse en $monedaSede (no se convierten: " +
                            "${formatearDinero(100.0, monedaAntes)} pasa a ser ${formatearDinero(100.0, monedaSede)}).")
                    }
                }
                when (eligiendoRegional) {
                    "pais" -> DialogoLista("País de la sede", listOf("" to "El de la clínica") + PAISES_SOPORTADOS.map { it.codigo to it.nombre }, { v ->
                        regional = if (v.isBlank()) RegionalSedeForm() else elegirPaisSede(v); eligiendoRegional = null
                    }, { eligiendoRegional = null })
                    "moneda" -> DialogoLista("Moneda de la sede", listOf("" to "La de la clínica (${ctx.moneda})") + MONEDAS_ELEGIBLES, { v ->
                        regional = regional.copy(moneda = v); eligiendoRegional = null
                    }, { eligiendoRegional = null })
                    "zona" -> {
                        val zonas = paisPorCodigo(regional.pais)?.zonas ?: ZONAS_SOPORTADAS
                        val opciones = (if (regional.zona.isNotBlank() && regional.zona !in zonas) listOf(regional.zona) else emptyList()) + zonas
                        DialogoLista("Zona horaria", listOf("" to "La de la clínica (${ctx.zona})") + opciones.map { it to it }, { v ->
                            regional = regional.copy(zona = v); eligiendoRegional = null
                        }, { eligiendoRegional = null })
                    }
                }
            }
            "horario" -> {
                Tarjeta {
                    FilaInterruptor("Usar el horario de la clínica", "Desmárcalo si esta sede abre en otro horario.", !horarioPropio) { usar ->
                        horarioPropio = !usar; if (usar) horario = horarioClinica
                    }
                    EditorSemana(horario, habilitado = horarioPropio) { horario = it }
                }
                Tarjeta {
                    val limClinica = dd.s("limiteClinica")
                    Campo("Pacientes a la misma hora (por profesional)", limite, { limite = it.filter { ch -> ch.isDigit() } }, teclado = KeyboardType.Number, max = 2,
                        placeholder = limClinica?.let { "El de la clínica: $it" } ?: "Sin límite (el de la clínica)")
                    Ayuda("Vacío = el mismo que la clínica.")
                }
            }
            "servicios" -> {
                val lista = servicios
                if (lista == null || todos == null) CargandoLista()
                else Tarjeta {
                    FilaInterruptor("Todos los servicios, al precio normal", null, todos == true) { todos = it }
                    if (todos != true) {
                        Ayuda("Marca lo que se atiende en esta sede. El precio es opcional: vacío = el precio normal.")
                        if (lista.isEmpty()) Ayuda("Aún no tienes servicios.")
                        lista.forEach { sv ->
                            val id = sv.t("procedimiento_id")
                            val (ofrece, precio) = elegidos[id] ?: (false to "")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = ofrece, onCheckedChange = { v -> elegidos = elegidos + (id to (v to precio)) }, colors = CheckboxDefaults.colors(checkedColor = c.navy))
                                Text(sv.t("nombre"), color = if (ofrece) c.texto else c.textoSuave, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Campo(null, precio, { v -> elegidos = elegidos + (id to (ofrece to v.filter { ch -> ch.isDigit() || ch == '.' })) },
                                    placeholder = formatearDinero(sv.n("precioNormal") ?: 0.0, monedaSede), teclado = KeyboardType.Decimal, habilitado = ofrece, max = 9, modifier = Modifier.width(110.dp))
                            }
                        }
                    }
                }
            }
            else -> Tarjeta {
                when {
                    s.b("es_principal") -> Ayuda("La sede principal usa el número de la clínica (Ajustes → Tus redes).")
                    s.b("tiene_whatsapp") -> Text("Esta sede tiene su número conectado ✅", color = c.ok, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    !dd.b("esAdmin") -> Ayuda("Solo el administrador de la clínica puede conectar el número de una sede.")
                    s.t("estado") != "Activa" -> Ayuda("Activa la sede para conectar su número.")
                    else -> {
                        Ayuda("Sin número propio, los recordatorios de esta sede salen por el número de la clínica. Conectar el WhatsApp de una sede usa el asistente de Meta, que se abre en la web.")
                        Boton("Conectar el WhatsApp de esta sede en la web ↗", primario = false) { acciones.abrirUrl("${Supabase.SITE_URL}/configuracion?tab=operacion") }
                    }
                }
                Ayuda("Para liberar o cambiar un número, ve a Ajustes → Tus redes.")
            }
        }
        Boton(if (guardando) "Guardando…" else "Guardar", habilitado = !guardando) { guardar() }
    }
}

/** Un precio para editar: "50" (no "50.0") o "49.90". */
private fun precioEditable(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else ((kotlin.math.round(v * 100) / 100.0).toString())
