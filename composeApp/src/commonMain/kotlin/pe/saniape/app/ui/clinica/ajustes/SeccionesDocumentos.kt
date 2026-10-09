package pe.saniape.app.ui.clinica.ajustes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.http.HttpMethod
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.staff.AjustesRepo
import pe.saniape.app.ui.AlertaConTeclado
import pe.saniape.app.ui.CargandoLista
import pe.saniape.app.ui.ManejarAtras
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.equipo.Pastilla
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.theme.Sania

// ─── ✍ Compromiso y consentimiento ──────────────────────────────────────────

private data class Borrador(val id: String, val nombre: String, val contenido: String, val salud: Boolean)

/** Lo que el paciente lee y firma antes de empezar (plantillas_consentimiento). Escrituras: Admin. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SeccionConsentimientos(d: JsonObject, onVolver: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val esAdmin = d.b("esAdmin")
    val cat = d.o("catalogos") ?: JsonObject(emptyMap())
    var datos by remember { mutableStateOf<JsonObject?>(null) }
    var recarga by remember { mutableIntStateOf(0) }
    var editando by remember { mutableStateOf<Borrador?>(null) }
    var errorCarga by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(recarga) {
        val (r, e) = AjustesRepo.leerObjeto("${AjustesRepo.BASE}/consentimientos")
        if (r != null) { datos = r; errorCarga = null }
        else if (datos == null) errorCarga = e ?: "No se pudieron leer los documentos"
        else Toaster.error(e ?: "No se pudieron leer los documentos")
    }
    val plantillas = datos?.objetos("plantillas") ?: emptyList()
    val nombrePsico = cat.t("nombrePsico")
    val faltaPsico = datos?.b("ofrecerPsico") == true && plantillas.none { it.t("nombre") == nombrePsico }

    fun accion(cuerpo: JsonObject, exito: String) {
        scope.launch {
            val r = guardarAjuste(porDefecto = "No se pudo cambiar") { AjustesRepo.enviar(HttpMethod.Post, "${AjustesRepo.BASE}/consentimientos", cuerpo) }
            if (r.registrada) { if (exito.isNotEmpty()) Toaster.exito(exito); recarga++ }
        }
    }

    ManejarAtras(activo = editando != null) { editando = null }
    editando?.let { b ->
        EditorConsentimiento(b, cat, datos?.o("ejemplo"), onVolver = { editando = null }) { recarga++; editando = null }
        return
    }

    SubPantalla("Compromiso y consentimiento", onVolver) {
        Ayuda("Es lo que el paciente lee y firma antes de empezar: qué autoriza, y las condiciones de tus paquetes. Se firma desde la ficha del paciente — él lo lee y firma con el dedo en su propio celular, escaneando un código.")
        when {
            datos == null && errorCarga != null -> ErrorCarga(errorCarga) { errorCarga = null; recarga++ }
            datos == null -> CargandoLista()
            plantillas.isEmpty() -> Tarjeta("Todavía no tienes ninguno") {
                Ayuda("Puedes partir de un ejemplo completo —pensado para un centro que vende paquetes de sesiones— y ajustarlo a tu clínica.")
                if (esAdmin) {
                    Boton("Empezar con el ejemplo") { editando = Borrador("", "Compromiso y consentimiento", cat.t("plantillaEjemplo"), false) }
                    if (faltaPsico) Boton("🧠 Modelo: evaluación psicológica", primario = false) { editando = Borrador("", nombrePsico, cat.t("plantillaPsico"), false) }
                    Boton("Escribir desde cero", primario = false) { editando = Borrador("", "", "", false) }
                }
            }
            else -> {
                plantillas.forEach { p ->
                    Tarjeta {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(p.t("nombre"), color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            if (p.b("por_defecto")) Pastilla("EN USO", c.ok, c.okBg)
                            if (!p.b("activo")) Pastilla("INACTIVO", c.textoSuave, c.borde)
                        }
                        Text(p.t("contenido").take(90) + "…", color = c.textoSuave, fontSize = 12.sp, maxLines = 2)
                        FilaInterruptor(
                            "🩺 Declaración de salud del paciente",
                            if (p.b("incluir_declaracion_salud")) "Al firmar, revisa o completa talla, peso, alergias, medicación, accidentes… (opcional). Queda en el documento y en su ficha."
                            else "Actívala si quieres que el paciente declare sus datos de salud al firmar.",
                            p.b("incluir_declaracion_salud"), habilitado = esAdmin,
                        ) { v ->
                            accion(buildJsonObject { put("accion", "salud"); put("id", p.t("id")); put("activo", v) },
                                if (v) "El paciente revisará sus datos de salud al firmar" else "Ya no se pedirá la declaración de salud")
                        }
                        if (esAdmin) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!p.b("por_defecto") && p.b("activo")) Boton("Usar este", primario = false, modifier = Modifier.weight(1f)) {
                                accion(buildJsonObject { put("accion", "por-defecto"); put("id", p.t("id")) }, "Es la que se usará al firmar")
                            }
                            Boton("✏ Editar", primario = false, modifier = Modifier.weight(1f)) {
                                editando = Borrador(p.t("id"), p.t("nombre"), p.t("contenido"), p.b("incluir_declaracion_salud"))
                            }
                            Boton(if (p.b("activo")) "Desactivar" else "Activar", primario = false, modifier = Modifier.weight(1f)) {
                                accion(buildJsonObject { put("accion", "activo"); put("id", p.t("id")); put("activo", !p.b("activo")) }, "")
                            }
                        }
                    }
                }
                if (esAdmin) {
                    Boton("+ Otro documento", primario = false) { editando = Borrador("", "", "", false) }
                    if (faltaPsico) Boton("🧠 Modelo: evaluación psicológica", primario = false) { editando = Borrador("", nombrePsico, cat.t("plantillaPsico"), false) }
                }
            }
        }
        Aviso("La firma queda registrada con la fecha, la hora y una copia exacta del texto que el paciente aceptó — si más adelante editas las cláusulas, lo ya firmado no cambia. Es una firma manuscrita capturada electrónicamente: sirve como constancia, no equivale a una firma digital certificada ante notario.", "info")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorConsentimiento(b: Borrador, cat: JsonObject, ejemplo: JsonObject?, onVolver: () -> Unit, onGuardado: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var nombre by remember { mutableStateOf(b.nombre) }
    var contenido by remember { mutableStateOf(b.contenido) }
    var salud by remember { mutableStateOf(b.salud) }
    var previa by remember { mutableStateOf<String?>(null) }
    var guardando by remember { mutableStateOf(false) }

    val sinGuardar = !guardando && (nombre != b.nombre || contenido != b.contenido || salud != b.salud)
    SubPantalla(if (b.id.isEmpty()) "Nuevo documento" else "Editar documento", onVolver, volver = "← Consentimientos", sinGuardar = sinGuardar) {
        Tarjeta {
            Campo("Nombre del documento", nombre, { nombre = it }, placeholder = "Ej. Compromiso y consentimiento", max = 150)
            Row(verticalAlignment = Alignment.CenterVertically) {
                EtqForm("Texto que lee y acepta el paciente")
                Text(if (previa != null) "Volver a editar" else "Ver cómo queda", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(start = 8.dp).clickableSimple {
                        if (previa != null) previa = null
                        else scope.launch {
                            val r = AjustesRepo.enviar(HttpMethod.Put, "${AjustesRepo.BASE}/consentimientos", buildJsonObject {
                                put("contenido", contenido); ejemplo?.let { put("datos", it) }
                            })
                            previa = r.cuerpo?.s("texto") ?: contenido
                        }
                    })
            }
            if (previa != null) {
                Text(previa ?: "", color = c.texto, fontSize = 13.sp, lineHeight = 19.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(c.fondo).border(1.dp, c.borde, RoundedCornerShape(8.dp)).padding(12.dp))
            } else {
                Campo(null, contenido, { contenido = it }, multilinea = true, lineas = 14, placeholder = "Escribe aquí tus cláusulas…", max = 30000)
            }
        }
        Tarjeta("Se rellenan solos con los datos del paciente") {
            Ayuda("Toca para insertar al final del texto:")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                cat.objetos("marcadores").forEach { m ->
                    Text(m.t("clave"), color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).border(1.dp, c.navyLight, RoundedCornerShape(20.dp))
                            .clickableSimple { previa = null; contenido += m.t("clave") }.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
        }
        Tarjeta {
            FilaInterruptor("Incluir declaración de salud del paciente",
                "Antes de firmar, el paciente revisa o completa talla, peso, enfermedades, síntomas, alergias, medicación y antecedentes de accidentes o cirugías. Todo opcional. Queda escrito al final del documento firmado y se actualiza su ficha.",
                salud) { salud = it }
        }
        Boton(if (guardando) "Guardando…" else "Guardar documento", habilitado = !guardando) {
            if (nombre.isBlank()) { Toaster.error("Ponle un nombre al documento"); return@Boton }
            if (contenido.trim().length < 40) { Toaster.error("El texto está muy corto"); return@Boton }
            guardando = true
            scope.launch {
                val r = guardarAjuste {
                    AjustesRepo.enviar(HttpMethod.Post, "${AjustesRepo.BASE}/consentimientos", buildJsonObject {
                        put("accion", "guardar"); if (b.id.isNotEmpty()) put("id", b.id)
                        put("nombre", nombre); put("contenido", contenido); put("salud", salud)
                    })
                }
                guardando = false
                if (r.registrada) { Toaster.exito(if (b.id.isEmpty()) "Documento creado" else "Guardado. Lo ya firmado no cambia."); onGuardado() }
            }
        }
    }
}

// ─── 🧠 Plantilla del informe psicológico ───────────────────────────────────

/** Sin motivo ni conclusiones no se puede emitir el informe: no se ocultan (lib/informe-psicologico-plantilla). */
private val SECCIONES_OBLIGATORIAS = setOf("motivo", "conclusiones")
private const val PIE_SUGERIDO = "CONFIDENCIAL: este informe contiene información psicológica protegida por el secreto profesional (Código de Ética del Colegio de Psicólogos del Perú) y por la Ley N.° 29733 de Protección de Datos Personales. Es de uso exclusivo para el fin solicitado; no se reproduce ni se entrega a terceros sin autorización del evaluado o su representante."

private data class SeccionInforme(val clave: String, val titulo: String, val visible: Boolean)

/** Cómo sale el informe de la evaluación psicológica (mismo endpoint que la web). Solo Admin edita. */
@Composable
internal fun SeccionInformePsico(d: JsonObject, onVolver: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val esAdmin = d.b("esAdmin")
    var cargado by remember { mutableStateOf(false) }
    // false = la carga falló: no se ofrece guardar (pisaría la plantilla con una vacía).
    var cargadoOk by remember { mutableStateOf(false) }
    var errorCarga by remember { mutableStateOf<String?>(null) }
    var intento by remember { mutableIntStateOf(0) }
    var guardada by remember { mutableStateOf<Triple<List<SeccionInforme>, String, String>?>(null) }
    var secciones by remember { mutableStateOf<List<SeccionInforme>>(emptyList()) }
    var encabezado by remember { mutableStateOf("") }
    var pie by remember { mutableStateOf("") }
    var guardando by remember { mutableStateOf(false) }
    var confirmarRestablecer by remember { mutableStateOf(false) }

    fun aplicar(p: JsonObject?) {
        p ?: return
        secciones = p.objetos("secciones").map { SeccionInforme(it.t("clave"), it.t("titulo"), it.b("visible")) }
        encabezado = p.t("encabezado"); pie = p.t("pie")
        guardada = Triple(secciones, encabezado, pie)
    }
    LaunchedEffect(intento) {
        val (r, e) = AjustesRepo.leerObjeto("/api/staff/evaluacion-psico/plantilla")
        val p = r?.o("plantilla")
        if (p != null) { aplicar(p); cargadoOk = true; errorCarga = null }
        else errorCarga = e ?: "No se pudo leer la plantilla"
        cargado = true
    }
    val sinGuardar = cargadoOk && !guardando && guardada != Triple(secciones, encabezado, pie)
    fun guardar(restablecer: Boolean) {
        guardando = true
        scope.launch {
            val r = guardarAjuste {
                AjustesRepo.enviar(HttpMethod.Post, "/api/staff/evaluacion-psico/plantilla",
                    if (restablecer) buildJsonObject { put("restablecer", true) }
                    else buildJsonObject {
                        put("plantilla", buildJsonObject {
                            put("encabezado", encabezado); put("pie", pie)
                            put("secciones", buildJsonArray {
                                secciones.forEach { s -> add(buildJsonObject { put("clave", s.clave); put("titulo", s.titulo); put("visible", s.visible) }) }
                            })
                        })
                    })
            }
            guardando = false
            if (r.registrada) { aplicar(r.cuerpo?.o("plantilla")); Toaster.exito(if (restablecer) "Plantilla estándar restablecida" else "Plantilla del informe guardada") }
        }
    }

    if (confirmarRestablecer) AlertaConTeclado(
        onDismissRequest = { confirmarRestablecer = false },
        title = { Text("¿Volver a la plantilla estándar?", fontWeight = FontWeight.Bold) },
        text = { Text("Los informes ya emitidos no cambian.") },
        confirmButton = { TextButton(onClick = { confirmarRestablecer = false; guardar(true) }) { Text("Restablecer", color = c.navy, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { confirmarRestablecer = false }) { Text("Cancelar", color = c.textoSuave) } },
        containerColor = c.superficie,
    )

    SubPantalla("Informe psicológico", onVolver, sinGuardar = sinGuardar) {
        Ayuda("Cómo sale el informe de la evaluación psicológica: el nombre y el orden de las secciones, cuáles se omiten, y los textos fijos. Los datos de filiación y la firma con el C.Ps.P. van siempre. Cada informe toma la plantilla al armarse: cambiarla no modifica los informes ya emitidos.")
        if (!cargado) { CargandoLista(); return@SubPantalla }
        if (!cargadoOk) { ErrorCarga(errorCarga) { cargado = false; intento++ }; return@SubPantalla }
        Tarjeta {
            Campo("Encabezado (bajo el título «Informe psicológico»)", encabezado, { encabezado = it }, multilinea = true, lineas = 2, max = 500, habilitado = esAdmin,
                placeholder = "Ej.: Área de Psicología — Documento confidencial")
        }
        Tarjeta("Secciones") {
            secciones.forEachIndexed { idx, s ->
                val obligatoria = s.clave in SECCIONES_OBLIGATORIAS
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, c.borde, RoundedCornerShape(10.dp)).padding(8.dp)) {
                    Campo(null, s.titulo, { v -> secciones = secciones.toMutableList().also { it[idx] = s.copy(titulo = v) } }, max = 120, habilitado = esAdmin)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (obligatoria) "Obligatoria" else if (s.visible) "Se incluye" else "Se omite", color = if (s.visible) c.texto else c.textoSuave,
                            fontSize = 12.sp, modifier = Modifier.weight(1f))
                        if (esAdmin) {
                            if (!obligatoria) androidx.compose.material3.Switch(checked = s.visible, onCheckedChange = { v -> secciones = secciones.toMutableList().also { it[idx] = s.copy(visible = v) } })
                            Text("▲", color = if (idx > 0) c.texto else c.borde, modifier = Modifier.padding(horizontal = 8.dp).clickableSimple {
                                if (idx > 0) secciones = secciones.toMutableList().also { val t = it[idx]; it[idx] = it[idx - 1]; it[idx - 1] = t }
                            })
                            Text("▼", color = if (idx < secciones.size - 1) c.texto else c.borde, modifier = Modifier.padding(horizontal = 8.dp).clickableSimple {
                                if (idx < secciones.size - 1) secciones = secciones.toMutableList().also { val t = it[idx]; it[idx] = it[idx + 1]; it[idx + 1] = t }
                            })
                        }
                    }
                }
            }
            Ayuda("La impresión diagnóstica justo después de las conclusiones comparte su número, como en el informe estándar.")
        }
        Tarjeta {
            Campo("Pie (al final, después de la firma)", pie, { pie = it }, multilinea = true, lineas = 3, max = 1000, habilitado = esAdmin,
                placeholder = "Ej.: advertencia de confidencialidad (secreto profesional, Ley 29733)…")
            if (esAdmin && pie.isBlank()) Text("+ Usar advertencia de confidencialidad sugerida", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickableSimple { pie = PIE_SUGERIDO }.padding(4.dp))
        }
        if (esAdmin) {
            Boton(if (guardando) "Guardando…" else "Guardar plantilla", habilitado = !guardando) { guardar(false) }
            Boton("Restablecer la estándar", primario = false, habilitado = !guardando) { confirmarRestablecer = true }
        } else Ayuda("Solo el Admin puede cambiar la plantilla.")
    }
}

// ─── 📄 Historia clínica y consentimiento informado ─────────────────────────

/** IPRESS y plantillas de consentimiento informado por procedimiento (NTS 139-MINSA). Escrituras: Admin. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SeccionHistoriaClinica(d: JsonObject, onVolver: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val esAdmin = d.b("esAdmin")
    var datos by remember { mutableStateOf<JsonObject?>(null) }
    var recarga by remember { mutableIntStateOf(0) }
    var categoria by remember { mutableStateOf("") }
    var renipress by remember { mutableStateOf("") }
    var elegirCategoria by remember { mutableStateOf(false) }
    var asociar by remember { mutableStateOf<JsonObject?>(null) }
    var editando by remember { mutableStateOf<JsonObject?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    var errorCarga by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(recarga) {
        val (r, e) = AjustesRepo.leerObjeto("${AjustesRepo.BASE}/historia-clinica")
        if (r != null) {
            if (datos == null) { categoria = r.o("ipress")?.t("categoria") ?: ""; renipress = r.o("ipress")?.t("renipress") ?: "" }
            datos = r; errorCarga = null
        } else if (datos == null) errorCarga = e ?: "No se pudo leer la configuración"
        else Toaster.error(e ?: "No se pudo leer la configuración")
    }
    fun accion(cuerpo: JsonObject, exito: (JsonObject?) -> String) {
        ocupado = true
        scope.launch {
            val r = guardarAjuste { AjustesRepo.enviar(HttpMethod.Post, "${AjustesRepo.BASE}/historia-clinica", cuerpo) }
            ocupado = false
            if (r.registrada) { exito(r.cuerpo).takeIf { it.isNotEmpty() }?.let { Toaster.exito(it) }; recarga++ }
        }
    }

    val dd = datos
    ManejarAtras(activo = editando != null) { editando = null }
    editando?.let { p ->
        if (dd != null) {
            EditorPlantillaCI(p, dd.objetos("secciones"), onVolver = { editando = null }) { editando = null; recarga++ }
            return
        }
    }
    val servicios = dd?.objetos("servicios") ?: emptyList()
    if (elegirCategoria && dd != null) DialogoLista("Categoría", listOf("" to "Sin categorizar todavía") + dd.objetos("categorias").map { it.t("valor") to it.t("nombre") },
        { categoria = it; elegirCategoria = false }, { elegirCategoria = false })
    asociar?.let { p ->
        DialogoLista("Servicio asociado", listOf("" to "Sin servicio asociado") +
            servicios.filter { it.t("estado") == "Activo" || it.t("id") == p.t("procedimiento_id") }.map { it.t("id") to it.t("nombre") },
            { v ->
                asociar = null
                accion(buildJsonObject { put("accion", "asociar"); put("id", p.t("id")); put("procedimientoId", v) }) { if (v.isNotEmpty()) "Plantilla asociada al servicio" else "Plantilla sin servicio" }
            }, { asociar = null })
    }

    SubPantalla("Historia clínica", onVolver) {
        if (dd == null && errorCarga != null) { ErrorCarga(errorCarga) { errorCarga = null; recarga++ }; return@SubPantalla }
        if (dd == null) { CargandoLista(); return@SubPantalla }
        Tarjeta("Identificación del establecimiento (IPRESS)") {
            Ayuda("La NTS 139-MINSA pide la categoría del establecimiento en el encabezado de la historia clínica y del consentimiento. La asigna la DIRESA al categorizarte; el código RENIPRESS lo da SUSALUD al inscribirte.")
            Selector("Categoría", dd.objetos("categorias").firstOrNull { it.t("valor") == categoria }?.t("nombre") ?: "", "Sin categorizar todavía", habilitado = esAdmin) { elegirCategoria = true }
            Campo("Código RENIPRESS", renipress, { renipress = it }, placeholder = "Ej. 00012345", teclado = androidx.compose.ui.text.input.KeyboardType.Number, habilitado = esAdmin, max = 20)
            if (esAdmin) Boton("Guardar", habilitado = !ocupado, primario = false) {
                accion(buildJsonObject { put("accion", "ipress"); put("categoria", categoria); put("renipress", renipress) }) { "Guardado" }
            }
        }
        val tipicos = dd.a("tipicos").mapNotNull { (it as? JsonPrimitive)?.content }
        if (tipicos.isNotEmpty()) Tarjeta("Procedimientos típicos") {
            Ayuda("${tipicos.joinToString(", ")}. Se agregan como servicios (si no los tienes) con su consentimiento asociado donde corresponde.")
            if (esAdmin) Boton(if (ocupado) "Cargando…" else "🩹 Cargar procedimientos típicos", primario = false, habilitado = !ocupado) {
                ocupado = true
                scope.launch {
                    val r = guardarAjuste { AjustesRepo.enviar(HttpMethod.Post, "/api/staff/servicio/tipicos", JsonObject(emptyMap())) }
                    ocupado = false
                    if (r.registrada) {
                        val b = r.cuerpo
                        Toaster.exito("${b?.i("creados") ?: 0} servicio(s) nuevo(s), ${b?.i("existentes") ?: 0} ya existían; ${b?.i("vinculados") ?: 0} consentimiento(s) asociado(s). Revisa precios en Servicios.")
                        recarga++
                    }
                }
            }
        }
        Tarjeta("Consentimiento informado por procedimiento") {
            Ayuda("Se imprime para firma manuscrita y huella del paciente (la norma no acepta la firma electrónica del paciente para esto). La biblioteca es un punto de partida en lenguaje sencillo: revísala con tu criterio clínico antes de usarla.")
            // Lo que el código hace de verdad (asegurarConsentimientosDeCita en la web):
            // agendar desde la agenda NO lo genera. Gemelo de CUANDO_SE_GENERA_CONSENTIMIENTO.
            Ayuda("Asocia cada plantilla a su servicio: el consentimiento se genera solo, pendiente de firma, cuando el médico indica ese procedimiento en la consulta guiada o abre su atención (▶ Atender). Agendarlo desde la agenda no lo genera: aparece al atenderlo.")
            if (esAdmin) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val faltan = dd.i("faltanBiblioteca") ?: 0
                if (faltan > 0) Boton("📚 Agregar biblioteca ($faltan)", primario = false, habilitado = !ocupado, modifier = Modifier.weight(1f)) {
                    accion(buildJsonObject { put("accion", "importar") }) { b -> "${b?.i("creadas") ?: 0} plantilla(s) agregada(s). Revísalas y adáptalas a tu práctica." }
                }
                Boton("+ Nueva", habilitado = !ocupado, modifier = Modifier.weight(1f)) { editando = JsonObject(emptyMap()) }
            }
            val plantillas = dd.objetos("plantillas")
            if (plantillas.isEmpty()) Ayuda("Aún no tienes plantillas.")
            plantillas.forEach { p ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, c.borde, RoundedCornerShape(10.dp)).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(p.t("procedimiento"), color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        if (!p.s("clave_biblioteca").isNullOrBlank()) Pastilla("biblioteca", c.purple, c.purpleBg)
                        if (p.b("revisar")) Pastilla("Revisar por la clínica", c.pend, c.pendBg)
                        if (!p.b("activo")) Pastilla("Inactiva", c.textoSuave, c.borde)
                    }
                    Selector(null, servicios.firstOrNull { it.t("id") == p.t("procedimiento_id") }?.t("nombre") ?: "", "Sin servicio asociado", habilitado = esAdmin && !ocupado) { asociar = p }
                    if (esAdmin) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Boton("✏ Editar", primario = false, modifier = Modifier.weight(1f)) { editando = p }
                        Boton(if (p.b("activo")) "Desactivar" else "Activar", primario = false, habilitado = !ocupado, modifier = Modifier.weight(1f)) {
                            accion(buildJsonObject { put("accion", "activo"); put("id", p.t("id")); put("activo", !p.b("activo")) }) { "" }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorPlantillaCI(p: JsonObject, secciones: List<JsonObject>, onVolver: () -> Unit, onGuardado: () -> Unit) {
    val scope = rememberCoroutineScope()
    val id = p.s("id")
    val originales = remember(p) { (listOf("procedimiento") + secciones.map { it.t("clave") }).associateWith { p.s(it) ?: "" } }
    var valores by remember { mutableStateOf(originales) }
    var guardando by remember { mutableStateOf(false) }
    SubPantalla(if (id == null) "Nueva plantilla" else "Editar plantilla", onVolver, volver = "← Historia clínica", sinGuardar = !guardando && valores != originales) {
        Tarjeta {
            Campo("Procedimiento", valores["procedimiento"] ?: "", { v -> valores = valores + ("procedimiento" to v) }, max = 200)
            secciones.forEach { s ->
                val k = s.t("clave")
                Campo(s.t("titulo"), valores[k] ?: "", { v -> valores = valores + (k to v) }, multilinea = true, lineas = if (k == "descripcion") 3 else 2,
                    placeholder = s.t("ayuda"), max = 5000)
            }
            Ayuda("Los riesgos propios de cada paciente (edad, enfermedades, medicación) se escriben al emitir el consentimiento.")
        }
        Boton(if (guardando) "Guardando…" else "Guardar", habilitado = !guardando) {
            guardando = true
            scope.launch {
                val r = guardarAjuste {
                    AjustesRepo.enviar(HttpMethod.Post, "${AjustesRepo.BASE}/historia-clinica", buildJsonObject {
                        put("accion", "guardar"); id?.let { put("id", it) }
                        put("cambios", buildJsonObject { valores.forEach { (k, v) -> put(k, v) } })
                    })
                }
                guardando = false
                if (r.registrada) { Toaster.exito("Plantilla guardada. Los consentimientos ya emitidos no cambian."); onGuardado() }
            }
        }
    }
}
