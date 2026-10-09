package pe.saniape.app.ui.clinica.ajustes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.staff.AjustesRepo
import pe.saniape.app.ui.AlertaConTeclado
import pe.saniape.app.ui.CargandoLista
import pe.saniape.app.ui.Gestion
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.equipo.Pastilla
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.DialogoHora
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.theme.Sania

/** Lo que hay de un catálogo: los datos, o el motivo por el que no se pudo leer. */
private class CargaCatalogo(val datos: JsonObject?, val error: String?)

/** Lee un catálogo de /api/staff/configuracion/catalogo (se relee al cambiar [recarga]). */
@Composable
private fun recordarCatalogo(tabla: String, recarga: Int): CargaCatalogo {
    var datos by remember { mutableStateOf<JsonObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tabla, recarga) {
        error = null
        val (r, e) = AjustesRepo.catalogo(tabla)
        when {
            r != null -> datos = r
            // Un fallo no se muestra como "vacío": sin datos, error con "Reintentar".
            datos == null -> error = e ?: "No se pudo cargar"
            else -> Toaster.error(e ?: "No se pudo actualizar")
        }
    }
    return CargaCatalogo(datos, error)
}

/** Cargando, o el error con "Reintentar" (los catálogos se releen subiendo [recarga]). */
@Composable
private fun CargandoOError(carga: CargaCatalogo?, onReintentar: () -> Unit) {
    if (carga?.error != null) ErrorCarga(carga.error, onReintentar) else CargandoLista()
}

/** Confirmación de borrado (texto + acción). */
@Composable
private fun ConfirmarBorrar(titulo: String, texto: String, onSi: () -> Unit, onNo: () -> Unit) {
    val c = Sania.colors
    AlertaConTeclado(
        onDismissRequest = onNo,
        title = { Text(titulo, fontWeight = FontWeight.Bold) },
        text = { Text(texto) },
        confirmButton = { TextButton(onClick = onSi) { Text("Eliminar", color = c.error, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onNo) { Text("Cancelar", color = c.textoSuave) } },
        containerColor = c.superficie,
    )
}

/** Fila de lista con acciones de texto a la derecha. */
@Composable
private fun FilaItem(titulo: String, detalle: String? = null, apagado: Boolean = false, izquierda: (@Composable () -> Unit)? = null, acciones: @Composable () -> Unit) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, c.borde, RoundedCornerShape(10.dp)).background(c.superficie)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        izquierda?.let { it(); Spacer(Modifier.width(8.dp)) }
        Column(Modifier.weight(1f)) {
            Text(titulo, color = if (apagado) c.textoSuave else c.texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            detalle?.takeIf { it.isNotBlank() }?.let { Text(it, color = c.textoSuave, fontSize = 11.5.sp) }
        }
        acciones()
    }
}

/** Pastilla con nombre: SOLO la ✕ borra (como la web), no un toque en cualquier parte. */
@Composable
private fun PastillaBorrable(nombre: String, onBorrar: () -> Unit) {
    val c = Sania.colors
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(c.fondo).border(1.dp, c.borde, RoundedCornerShape(20.dp))
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(nombre, color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text("✕", color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(CircleShape).clickableSimple(onBorrar).padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

@Composable
private fun AccionTexto(texto: String, color: androidx.compose.ui.graphics.Color = Sania.colors.textoSuave, onClick: () -> Unit) {
    Text(texto, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickableSimple(onClick).padding(horizontal = 6.dp, vertical = 6.dp))
}

// ─── 🦷 Hallazgos del odontograma ───────────────────────────────────────────

@Composable
internal fun SeccionHallazgos(onVolver: () -> Unit, volver: String = "← Ajustes") {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var recarga by remember { mutableIntStateOf(0) }
    val carga = recordarCatalogo("hallazgos_dentales", recarga)
    val datos = carga.datos
    var editando by remember { mutableStateOf<JsonObject?>(null) }
    var borrar by remember { mutableStateOf<JsonObject?>(null) }
    val procs = datos?.objetos("procedimientos") ?: emptyList()
    val items = datos?.objetos("items") ?: emptyList()

    borrar?.let { h ->
        ConfirmarBorrar("¿Eliminar «${h.t("nombre")}» del catálogo?", "Si ya tiene registros en odontogramas de pacientes no se puede: desactívalo en vez de eliminarlo.", onSi = {
            borrar = null
            scope.launch {
                val r = guardarAjuste(Gestion.ELIMINANDO, "No se pudo eliminar") { AjustesRepo.borrarDeCatalogo("hallazgos_dentales", h.t("id")) }
                if (r.registrada) { pe.saniape.app.data.staff.OdontogramaRepo.limpiarCache(); Toaster.exito("Hallazgo eliminado"); recarga++ }
            }
        }, onNo = { borrar = null })
    }
    editando?.let { h -> DialogoHallazgo(h, procs, items.size, onCerrar = { editando = null }) { editando = null; recarga++ } }

    SubPantalla("Hallazgos del odontograma", onVolver, volver = volver) {
        Ayuda("El color con que se pinta el diente. El procedimiento sugerido arma el presupuesto automáticamente.")
        if (datos == null) { CargandoOError(carga) { recarga++ }; return@SubPantalla }
        if (items.isEmpty()) Ayuda("Aún no registras ningún hallazgo. Ej.: Caries, Fractura, Corona existente.")
        items.forEach { h ->
            val activo = h.t("estado") == "Activo"
            val det = listOfNotNull(
                h.s("procedimiento_id")?.let { id -> "Procedimiento sugerido: ${procs.firstOrNull { it.t("id") == id }?.t("nombre") ?: "—"}" },
                "Marca el diente como ausente".takeIf { h.b("marca_ausente") },
                "De toda la boca (se cobra una vez)".takeIf { h.b("por_boca") },
            ).joinToString(" · ")
            FilaItem(h.t("nombre"), det, apagado = !activo, izquierda = {
                Box(Modifier.size(14.dp).clip(CircleShape).background(colorDeHex(h.t("color")) ?: c.error).border(1.dp, c.borde, CircleShape))
            }) {
                AccionTexto(if (activo) "Activo" else "Inactivo", if (activo) c.ok else c.textoSuave) {
                    scope.launch {
                        val r = guardarAjuste(porDefecto = "No se pudo actualizar") {
                            AjustesRepo.editarEnCatalogo("hallazgos_dentales", h.t("id"), buildJsonObject { put("estado", if (activo) "Inactivo" else "Activo") })
                        }
                        if (r.registrada) { pe.saniape.app.data.staff.OdontogramaRepo.limpiarCache(); recarga++ }
                    }
                }
                AccionTexto("Editar") { editando = h }
                AccionTexto("✕", c.error) { borrar = h }
            }
        }
        Boton("+ Agregar hallazgo", primario = false) { editando = JsonObject(emptyMap()) }
    }
}

@Composable
private fun DialogoHallazgo(h: JsonObject, procs: List<JsonObject>, total: Int, onCerrar: () -> Unit, onHecho: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val id = h.s("id")
    var nombre by remember { mutableStateOf(h.t("nombre")) }
    var color by remember { mutableStateOf(h.s("color") ?: "#dc2626") }
    var proc by remember { mutableStateOf(h.s("procedimiento_id")) }
    var ausente by remember { mutableStateOf(h.b("marca_ausente")) }
    var porBoca by remember { mutableStateOf(h.b("por_boca")) }
    var orden by remember { mutableStateOf((h.i("orden") ?: total).toString()) }
    var elegirProc by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    if (elegirProc) DialogoLista("Procedimiento sugerido", listOf("" to "— ninguno —") + procs.map { it.t("id") to it.t("nombre") },
        { proc = it.ifBlank { null }; elegirProc = false }, { elegirProc = false })
    DialogoForm(
        titulo = if (id == null) "Nuevo hallazgo" else "Editar hallazgo", subtitulo = null,
        textoAccion = if (guardando) "Guardando…" else if (id == null) "Agregar" else "Guardar",
        accionHabilitada = !guardando && nombre.isNotBlank(), onCancelar = onCerrar,
        onAccion = {
            guardando = true
            scope.launch {
                val datos = buildJsonObject {
                    put("nombre", nombre); put("color", color); put("procedimientoId", proc ?: "")
                    put("marcaAusente", ausente); put("porBoca", porBoca); put("orden", orden.toIntOrNull() ?: 0)
                    put("estado", h.s("estado") ?: "Activo")
                }
                val r = guardarAjuste { if (id == null) AjustesRepo.crearEnCatalogo("hallazgos_dentales", datos) else AjustesRepo.editarEnCatalogo("hallazgos_dentales", id, datos) }
                guardando = false
                if (r.registrada) pe.saniape.app.data.staff.OdontogramaRepo.limpiarCache()
                if (r.registrada) { Toaster.exito(if (id == null) "Hallazgo agregado" else "Hallazgo actualizado"); onHecho() }
            }
        },
    ) {
        Campo("Nombre", nombre, { nombre = it }, placeholder = "Ej. Caries", max = 120)
        Spacer(Modifier.size(8.dp))
        EtqForm("Color")
        SelectorColor(color, { color = it })
        Spacer(Modifier.size(8.dp))
        Selector("Procedimiento sugerido", procs.firstOrNull { it.t("id") == proc }?.t("nombre") ?: "", "— ninguno —") { elegirProc = true }
        Spacer(Modifier.size(8.dp))
        FilaInterruptor("Marca el diente como ausente", null, ausente, habilitado = !porBoca) { ausente = it }
        FilaInterruptor("Es de toda la boca (se cobra una vez)", "Va en la barra de «Encías y boca» y no en cada pieza.", porBoca) { porBoca = it; if (it) ausente = false }
        Campo("Orden", orden, { orden = it.filter { ch -> ch.isDigit() } }, teclado = KeyboardType.Number, max = 3)
        Text("", color = c.textoSuave)
    }
}

// ─── 🩺 Módulos clínicos ────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SeccionModulos(d: JsonObject, onVolver: () -> Unit, onCambio: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val esAdmin = d.b("esAdmin")
    val m = d.o("modulos") ?: JsonObject(emptyMap())
    val elegidos = m.o("elegidos") ?: JsonObject(emptyMap())
    val mediciones = d.o("catalogos")?.objetos("mediciones") ?: emptyList()
    var flujoMedico by remember { mutableStateOf(m.b("flujoMedico")) }
    var triaje by remember { mutableStateOf(m.b("triaje")) }
    var recetas by remember { mutableStateOf(m.b("recetas")) }
    var campos by remember { mutableStateOf(m.textos("camposTriaje").toSet()) }
    var indicaciones by remember { mutableStateOf(d.o("config")?.t("indicacionesFijas") ?: "") }
    var indicacionesGuardadas by remember { mutableStateOf(indicaciones) }
    var ocupado by remember { mutableStateOf<String?>(null) }
    val clinicaMedica = m.b("clinicaMedica")
    fun def(v: Boolean) = if (v) "encendido" else "apagado"
    fun nota(clave: String, activo: Boolean) = if (elegidos.bn(clave) == null) "Predeterminado para tu rubro: ${def(activo)}."
        else "Elegido por la clínica (para tu rubro, lo predeterminado es ${def(clinicaMedica)})."

    fun guardar(seccion: String, datos: JsonObject, aviso: String, alFallar: () -> Unit = {}, alGuardar: () -> Unit = {}) {
        ocupado = seccion
        scope.launch {
            val r = guardarAjuste { AjustesRepo.guardarSeccion(seccion, datos) }
            ocupado = null
            if (r.registrada) { alGuardar(); Toaster.exito(aviso); onCambio() } else alFallar()
        }
    }
    fun modulo(nombre: String, v: Boolean, aplicar: (Boolean) -> Unit) {
        val antes = !v
        aplicar(v)
        guardar("modulo", buildJsonObject { put("modulo", nombre); put("activo", v) }, if (v) "Encendido" else "Apagado") { aplicar(antes) }
    }

    SubPantalla("Módulos clínicos", onVolver) {
        Tarjeta {
            FilaInterruptor("Flujo de atención médica",
                "Consulta guiada con signos vitales, diagnóstico CIE-10, receta, exámenes, procedimientos con consentimiento informado y control, e historia clínica según la NTS 139-MINSA. En la agenda de hoy: ▶ Atender. Aplica a las especialidades de medicina, odontología y ginecología.",
                flujoMedico, habilitado = esAdmin && ocupado == null) { modulo("flujoMedico", it) { v -> flujoMedico = v } }
            Ayuda(nota("flujoMedico", flujoMedico))
        }
        Tarjeta {
            FilaInterruptor("Tomar signos vitales antes de la atención (triaje)",
                "En la agenda de hoy: 🔔 Llegó → 🩺 Triaje → atención, con un filtro «En sala de espera». Los valores quedan ligados a la cita y al historial del paciente. Aplica a todas las citas de la clínica, de cualquier especialidad.",
                triaje, habilitado = esAdmin && ocupado == null) { modulo("triaje", it) { v -> triaje = v } }
            Ayuda(nota("triaje", triaje))
            if (triaje) {
                EtqForm("Qué mide tu clínica")
                mediciones.forEach { md ->
                    val k = md.t("clave")
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickableSimple {
                        if (!esAdmin || ocupado != null) return@clickableSimple
                        val nuevo = if (k in campos) campos - k else campos + k
                        if (nuevo.isEmpty()) { Toaster.error("Elige al menos una medición"); return@clickableSimple }
                        val antes = campos
                        campos = nuevo
                        guardar("triaje-campos", buildJsonObject { put("campos", buildJsonArray { nuevo.forEach { add(JsonPrimitive(it)) } }) }, "Mediciones del triaje guardadas") { campos = antes }
                    }) {
                        Checkbox(checked = k in campos, onCheckedChange = null, enabled = esAdmin, colors = CheckboxDefaults.colors(checkedColor = c.navy))
                        Text(md.t("nombre"), color = c.texto, fontSize = 13.sp)
                    }
                }
            }
        }
        Tarjeta {
            FilaInterruptor("Emitir recetas",
                if (clinicaMedica) "Receta médica numerada según el DS 014-2011-SA (art. 56), imprimible, con firma y sello."
                else "Pestaña 💊 Recetas en la ficha y «📝 Receta» en cada tratamiento, para dar indicaciones al final de las sesiones o controles. Legalmente solo médicos, odontólogos y obstetras prescriben medicamentos: a los demás profesionales el formulario se lo advierte y no deja emitir controlados.",
                recetas, habilitado = esAdmin && ocupado == null) { modulo("recetas", it) { v -> recetas = v } }
            Ayuda(nota("recetas", recetas))
            if (recetas) {
                Campo("Indicaciones impresas en todas las hojas", indicaciones, { indicaciones = it }, multilinea = true, lineas = 5, habilitado = esAdmin,
                    placeholder = "El primer día evite esfuerzos físicos.\nSiga únicamente las indicaciones de su fisioterapeuta.", max = 4000)
                val lineas = indicaciones.lines().count { it.isNotBlank() }
                Ayuda("Una por línea ($lineas ${if (lineas == 1) "indicación" else "indicaciones"}). Salen a la derecha en el formato A5 horizontal, con tu logo y tus contactos abajo. Déjalo vacío si no usas.")
                if (esAdmin) Boton("Guardar indicaciones", primario = false, habilitado = ocupado == null && indicaciones.trim() != indicacionesGuardadas.trim()) {
                    val texto = indicaciones
                    guardar("indicaciones-fijas", buildJsonObject { put("texto", texto) }, "Indicaciones guardadas: saldrán en las próximas hojas",
                        alGuardar = { indicacionesGuardadas = texto })
                }
            }
        }
        if (!esAdmin) Ayuda("Solo el administrador puede cambiarlos.")
    }
}

// ─── 🗂️ Campos personalizados del paciente ──────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SeccionCamposPaciente(d: JsonObject, onVolver: () -> Unit, onCambio: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val cat = d.o("catalogos") ?: JsonObject(emptyMap())
    val tipos = cat.objetos("tiposCampo")
    var recarga by remember { mutableIntStateOf(0) }
    val carga = recordarCatalogo("campos_paciente", recarga)
    val datos = carga.datos
    var editando by remember { mutableStateOf<JsonObject?>(null) }
    var ocultos by remember { mutableStateOf(d.o("config")?.textos("fichaOcultar")?.toSet() ?: emptySet()) }
    var guardandoOcultos by remember { mutableStateOf(false) }
    val items = datos?.objetos("items") ?: emptyList()

    editando?.let { campo -> DialogoCampo(campo, tipos, onCerrar = { editando = null }) { editando = null; recarga++ } }

    SubPantalla("Campos del paciente", onVolver) {
        Ayuda("Añade campos propios a la ficha del paciente (ej. densidad folicular, fototipo de piel, tipo de maloclusión). Aparecen en cada ficha para registrarlos.")
        Boton("+ Nuevo campo") { editando = JsonObject(emptyMap()) }
        when {
            datos == null -> CargandoOError(carga) { recarga++ }
            items.isEmpty() -> pe.saniape.app.ui.clinica.EstadoVacio("🗂️", "Aún no tienes campos personalizados", "Créalos para registrar la información propia de tu rubro en cada ficha.")
            else -> items.forEach { campo ->
                val tipo = tipos.firstOrNull { it.t("v") == campo.t("tipo") }
                val opciones = campo.textos("opciones").size
                FilaItem(campo.t("nombre"), "${tipo?.t("icon") ?: ""} ${tipo?.t("label") ?: campo.t("tipo")}" + if (campo.t("tipo") == "opciones" && opciones > 0) " · $opciones opciones" else "",
                    apagado = campo.t("estado") == "Inactivo") {
                    if (campo.t("estado") == "Inactivo") Pastilla("Inactivo", c.textoSuave, c.borde)
                    AccionTexto("✏ Editar") { editando = campo }
                }
            }
        }
        Tarjeta("Campos estándar del formulario") {
            Ayuda("Apaga los que tu clínica no usa: dejan de aparecer al registrar pacientes. Lo ya guardado se conserva.")
            cat.objetos("camposFicha").forEach { cf ->
                val k = cf.t("clave")
                FilaInterruptor(cf.t("label"), cf.t("desc"), k !in ocultos, habilitado = !guardandoOcultos) { visible ->
                    val antes = ocultos
                    val nuevos = if (visible) ocultos - k else ocultos + k
                    ocultos = nuevos
                    guardandoOcultos = true
                    scope.launch {
                        val r = try {
                            guardarAjuste { AjustesRepo.guardarSeccion("campos-estandar", buildJsonObject { put("ocultos", buildJsonArray { nuevos.forEach { add(JsonPrimitive(it)) } }) }) }
                        } finally { guardandoOcultos = false }
                        if (r.registrada) { Toaster.exito("Formulario actualizado"); onCambio() } else ocultos = antes
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogoCampo(campo: JsonObject, tipos: List<JsonObject>, onCerrar: () -> Unit, onHecho: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val id = campo.s("id")
    var nombre by remember { mutableStateOf(campo.t("nombre")) }
    var tipo by remember { mutableStateOf(campo.s("tipo") ?: "texto") }
    var opciones by remember { mutableStateOf(campo.textos("opciones").joinToString(", ")) }
    var ayuda by remember { mutableStateOf(campo.t("ayuda")) }
    var guardando by remember { mutableStateOf(false) }
    var borrar by remember { mutableStateOf(false) }
    if (borrar) ConfirmarBorrar("¿Eliminar el campo «$nombre»?", "Los valores guardados en las fichas se conservan pero dejan de mostrarse.", onSi = {
        borrar = false
        scope.launch {
            val r = guardarAjuste(Gestion.ELIMINANDO, "No se pudo eliminar") { AjustesRepo.borrarDeCatalogo("campos_paciente", id ?: "") }
            if (r.registrada) { Toaster.exito("Campo eliminado"); onHecho() }
        }
    }, onNo = { borrar = false })
    DialogoForm(
        titulo = if (id == null) "Nuevo campo" else "Editar campo", subtitulo = null,
        textoAccion = if (guardando) "Guardando…" else if (id == null) "Crear" else "Guardar",
        accionHabilitada = !guardando && nombre.isNotBlank(), onCancelar = onCerrar,
        onAccion = {
            if (tipo == "opciones" && opciones.isBlank()) { Toaster.error("Escribe las opciones separadas por coma"); return@DialogoForm }
            guardando = true
            scope.launch {
                val datos = buildJsonObject { put("nombre", nombre); put("tipo", tipo); put("opciones", opciones); put("ayuda", ayuda) }
                val r = guardarAjuste { if (id == null) AjustesRepo.crearEnCatalogo("campos_paciente", datos) else AjustesRepo.editarEnCatalogo("campos_paciente", id, datos) }
                guardando = false
                if (r.registrada) { Toaster.exito(if (id == null) "Campo creado" else "Campo actualizado"); onHecho() }
            }
        },
    ) {
        Campo("Nombre del campo", nombre, { nombre = it }, placeholder = "Ej. Densidad folicular", max = 120)
        Spacer(Modifier.size(8.dp))
        EtqForm("Tipo de dato")
        ChipsEleccion(tipos.map { it.t("v") to "${it.t("icon")} ${it.t("label")}" }, tipo) { tipo = it }
        if (tipo == "opciones") {
            Spacer(Modifier.size(8.dp))
            Campo("Opciones (separadas por coma)", opciones, { opciones = it }, placeholder = "Ej. Tipo I, Tipo II, Tipo III")
        }
        Spacer(Modifier.size(8.dp))
        Campo("Ayuda (opcional)", ayuda, { ayuda = it }, placeholder = "Texto que orienta a quien llena la ficha", max = 300)
        if (id != null) {
            Spacer(Modifier.size(12.dp))
            Text("Eliminar campo", color = c.error, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickableSimple { borrar = true }.padding(4.dp))
        }
    }
}

// ─── 💳 Métodos de pago ─────────────────────────────────────────────────────

@Composable
internal fun SeccionMetodosPago(onVolver: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var recarga by remember { mutableIntStateOf(0) }
    val carga = recordarCatalogo("metodos_pago", recarga)
    val datos = carga.datos
    var nuevo by remember { mutableStateOf("") }
    var icono by remember { mutableStateOf("") }
    var borrar by remember { mutableStateOf<JsonObject?>(null) }
    val items = datos?.objetos("items") ?: emptyList()

    borrar?.let { m ->
        ConfirmarBorrar("¿Eliminar el método «${m.t("nombre")}»?", "Los pagos ya registrados con este método se conservan.", onSi = {
            borrar = null
            scope.launch {
                val r = guardarAjuste(Gestion.ELIMINANDO, "No se pudo eliminar") { AjustesRepo.borrarDeCatalogo("metodos_pago", m.t("id")) }
                if (r.registrada) pe.saniape.app.data.staff.CatalogosCobroRepo.limpiar()
                if (r.registrada) { Toaster.exito("Método eliminado"); recarga++ }
            }
        }, onNo = { borrar = null })
    }

    SubPantalla("Métodos de pago", onVolver) {
        Ayuda("Los métodos con que tu clínica cobra. Ajústalos a tu país (agrega Bizum, OXXO, crédito interno; desactiva los que no uses). Aparecen al registrar pagos y en el cierre de caja.")
        if (datos == null) { CargandoOError(carga) { recarga++ }; return@SubPantalla }
        items.forEach { m ->
            val activo = m.t("estado") == "Activo"
            FilaItem("${m.s("icono")?.takeIf { it.isNotBlank() } ?: "💰"} ${m.t("nombre")}", apagado = !activo) {
                AccionTexto(if (activo) "Activo" else "Inactivo", if (activo) c.ok else c.textoSuave) {
                    scope.launch {
                        val r = guardarAjuste { AjustesRepo.editarEnCatalogo("metodos_pago", m.t("id"), buildJsonObject { put("estado", if (activo) "Inactivo" else "Activo") }) }
                        if (r.registrada) { pe.saniape.app.data.staff.CatalogosCobroRepo.limpiar(); recarga++ }
                    }
                }
                AccionTexto("Eliminar", c.error) { borrar = m }
            }
        }
        Tarjeta("Nuevo método") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Campo(null, icono, { icono = it }, placeholder = "💳", max = 2, modifier = Modifier.width(72.dp))
                Campo(null, nuevo, { nuevo = it }, placeholder = "Ej. Bizum, OXXO, Crédito", max = 60, modifier = Modifier.weight(1f))
            }
            Boton("+ Agregar", habilitado = nuevo.isNotBlank()) {
                if (items.any { it.t("nombre").equals(nuevo.trim(), true) }) { Toaster.error("Ya existe un método con ese nombre"); return@Boton }
                scope.launch {
                    val r = guardarAjuste(porDefecto = "No se pudo agregar") { AjustesRepo.crearEnCatalogo("metodos_pago", buildJsonObject { put("nombre", nuevo); put("icono", icono) }) }
                    if (r.registrada) pe.saniape.app.data.staff.CatalogosCobroRepo.limpiar()
                    if (r.registrada) { nuevo = ""; icono = ""; recarga++ }
                }
            }
        }
    }
}

// ─── 💰 Cobro de la consulta ────────────────────────────────────────────────

@Composable
internal fun SeccionCobroConsulta(d: JsonObject, onVolver: () -> Unit, onCambio: () -> Unit) {
    val scope = rememberCoroutineScope()
    val esAdmin = d.b("esAdmin")
    var activo by remember { mutableStateOf(d.o("config")?.b("cobroExplicito") == true) }
    var guardando by remember { mutableStateOf(false) }
    SubPantalla("Cobro de la consulta", onVolver) {
        Tarjeta {
            FilaInterruptor("El cobro lo hace recepción o el administrador",
                "Cuando está encendido, terminar la atención no marca la consulta como pagada: la cita queda «por cobrar» y el ingreso se registra cuando recepción o el administrador la cobran, eligiendo el método real (efectivo, Yape, tarjeta…). Así la caja cuadra con lo que de verdad entró.",
                activo, habilitado = esAdmin && !guardando) { v ->
                guardando = true
                scope.launch {
                    val r = guardarAjuste { AjustesRepo.guardarSeccion("cobro-consulta", buildJsonObject { put("activo", v) }) }
                    guardando = false
                    if (r.registrada) {
                        activo = v
                        Toaster.exito(if (v) "Listo: ahora el cobro lo hace recepción o el administrador" else "Volviste al cobro automático al terminar la atención")
                        onCambio()
                    }
                }
            }
            Ayuda("Apagado, al terminar la atención se asume pagada en efectivo (útil solo si quien atiende también cobra).")
            if (!esAdmin) Ayuda("Solo el administrador puede cambiarlo.")
        }
    }
}

// ─── ⚙️ Equipamiento ────────────────────────────────────────────────────────

@Composable
internal fun SeccionEquipamiento(d: JsonObject, onVolver: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val cat = d.o("catalogos") ?: JsonObject(emptyMap())
    var recarga by remember { mutableIntStateOf(0) }
    val carga = recordarCatalogo("equipos", recarga)
    val datos = carga.datos
    var editando by remember { mutableStateOf<JsonObject?>(null) }
    var borrar by remember { mutableStateOf<JsonObject?>(null) }
    val items = datos?.objetos("items") ?: emptyList()

    borrar?.let { e ->
        ConfirmarBorrar("¿Eliminar «${e.t("nombre")}»?", "El asistente dejará de mencionarlo.", onSi = {
            borrar = null
            scope.launch {
                val r = guardarAjuste(Gestion.ELIMINANDO, "No se pudo eliminar") { AjustesRepo.borrarDeCatalogo("equipos", e.t("id")) }
                if (r.registrada) { Toaster.exito("Equipo eliminado"); recarga++ }
            }
        }, onNo = { borrar = null })
    }
    editando?.let { e -> DialogoEquipo(e, cat, onCerrar = { editando = null }) { editando = null; recarga++ } }

    SubPantalla("Equipamiento", onVolver) {
        Ayuda("Las máquinas y equipos con que cuenta tu clínica, y para qué sirven. El asistente los usa para responder a los pacientes — si alguien pregunta «¿tienen algo para el dolor de rodilla?», la respuesta sale de aquí.")
        if (datos == null) { CargandoOError(carga) { recarga++ }; return@SubPantalla }
        if (items.isEmpty()) Ayuda("Aún no registras ningún equipo. Ej.: ultrasonido terapéutico, magnetoterapia, láser.")
        items.forEach { e ->
            val activo = e.b("activo")
            val det = listOfNotNull(e.s("descripcion")?.takeIf { it.isNotBlank() }, e.s("sirve_para")?.takeIf { it.isNotBlank() }?.let { "Sirve para: $it" }).joinToString(" · ")
            FilaItem("⚙️ ${e.t("nombre")}", det, apagado = !activo) {
                AccionTexto(if (activo) "Activo" else "Oculto", if (activo) c.ok else c.textoSuave) {
                    scope.launch {
                        val r = guardarAjuste { AjustesRepo.editarEnCatalogo("equipos", e.t("id"), buildJsonObject { put("activo", !activo) }) }
                        if (r.registrada) recarga++
                    }
                }
                AccionTexto("Editar") { editando = e }
                AccionTexto("✕", c.error) { borrar = e }
            }
        }
        Boton("+ Agregar equipo", primario = false) { editando = JsonObject(emptyMap()) }
        datos.o("paquete")?.takeIf { items.any { it.b("activo") } }?.let { p ->
            Tarjeta("Así lo verá el asistente") {
                Text(p.t("texto"), color = c.texto, fontSize = 13.sp)
                val fuera = p.textos("fuera")
                if (fuera.isNotEmpty()) Aviso("⚠ Ya no caben: ${fuera.joinToString(", ")}. Acorta los «sirve para» u oculta equipos para que el asistente los conozca todos.")
                Ayuda("Los cambios le llegan al asistente cuando se sincroniza la configuración del bot.")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogoEquipo(e: JsonObject, cat: JsonObject, onCerrar: () -> Unit, onHecho: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val id = e.s("id")
    val tope = cat.i("topeChip") ?: 30
    var nombre by remember { mutableStateOf(e.t("nombre")) }
    var descripcion by remember { mutableStateOf(e.t("descripcion")) }
    var chips by remember { mutableStateOf(e.t("sirve_para").split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }) }
    var chipTxt by remember { mutableStateOf("") }
    var guardando by remember { mutableStateOf(false) }
    fun agregar(t: String) {
        val v = t.trim().trimEnd('.').take(tope)
        if (v.isNotEmpty() && chips.none { it.equals(v, true) } && chips.size < 8) chips = chips + v
        chipTxt = ""
    }
    DialogoForm(
        titulo = if (id == null) "Nuevo equipo" else "Editar equipo", subtitulo = null,
        textoAccion = if (guardando) "Guardando…" else if (id == null) "+ Agregar equipo" else "Guardar cambios",
        accionHabilitada = !guardando && nombre.isNotBlank(), onCancelar = onCerrar,
        onAccion = {
            guardando = true
            // Lo tipeado y no confirmado también cuenta: nadie pierde el último chip.
            val sirve = (chips + listOfNotNull(chipTxt.trim().takeIf { it.isNotEmpty() })).joinToString(", ")
            scope.launch {
                val datos = buildJsonObject { put("nombre", nombre); put("descripcion", descripcion); put("sirvePara", sirve) }
                val r = guardarAjuste { if (id == null) AjustesRepo.crearEnCatalogo("equipos", datos) else AjustesRepo.editarEnCatalogo("equipos", id, datos) }
                guardando = false
                if (r.registrada) { Toaster.exito(if (id == null) "Equipo agregado" else "Equipo actualizado"); onHecho() }
            }
        },
    ) {
        Campo("Nombre", nombre, { nombre = it }, placeholder = "Ej. Ultrasonido terapéutico", max = 120)
        Spacer(Modifier.size(8.dp))
        Campo("Descripción corta", descripcion, { descripcion = it }, placeholder = "Ej. equipo de 1 y 3 MHz, cabezal doble", max = 300)
        Spacer(Modifier.size(8.dp))
        EtqForm("¿Para qué dolencias sirve? (cortas, de a una)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            chips.forEach { ch ->
                Text("$ch  ✕", color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(c.chipBg).clickableSimple { chips = chips - ch }.padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Campo(null, chipTxt, { v -> if (v.contains(',')) agregar(v.replace(",", "")) else chipTxt = v }, placeholder = "Ej. dolor lumbar", max = tope, modifier = Modifier.weight(1f))
            Boton("+", primario = false, habilitado = chipTxt.isNotBlank(), modifier = Modifier.width(52.dp)) { agregar(chipTxt) }
        }
        if (chipTxt.length >= tope) Ayuda("Máximo $tope letras por dolencia — corta, tipo «dolor lumbar». Si son varias, agrégalas de a una.", c.pend)
        Spacer(Modifier.size(6.dp))
        ChipsEleccion(cat.textos("dolencias").filter { s -> chips.none { it.equals(s, true) } }.take(8).map { it to "+ $it" }, null) { agregar(it) }
    }
}

// ─── 🛎️ Preguntas de mostrador ──────────────────────────────────────────────

@Composable
internal fun SeccionMostrador(d: JsonObject, onVolver: () -> Unit, onCambio: () -> Unit) {
    val scope = rememberCoroutineScope()
    val cat = d.o("catalogos") ?: JsonObject(emptyMap())
    val campos = cat.objetos("camposMostrador")
    val tope = cat.i("topeMostrador") ?: 200
    val guardado = d.o("config")?.o("mostrador") ?: JsonObject(emptyMap())
    var valores by remember { mutableStateOf(campos.associate { it.t("clave") to (guardado.s(it.t("clave")) ?: "") }) }
    var base by remember { mutableStateOf(valores) }
    var guardando by remember { mutableStateOf(false) }
    var botSync by remember { mutableStateOf<String?>(null) }
    var verSync by remember { mutableStateOf(false) }
    val hayCambios = valores != base

    SubPantalla("Preguntas de mostrador", onVolver, sinGuardar = hayCambios && !guardando) {
        Ayuda("Lo que la gente pregunta por WhatsApp antes de agendar. Escribe la respuesta como se la dirías tú en el mostrador, en una frase. Lo que dejes vacío, el asistente lo responde con honestidad («eso te lo confirman en la clínica»).")
        campos.forEach { cm ->
            val k = cm.t("clave")
            Tarjeta(cm.t("titulo")) {
                Ayuda(cm.t("ayuda"))
                if (k == "duracion") Ayuda("Sani responde con el tiempo configurado en Duraciones de cita. Este texto manual se conserva.")
                Campo(null, valores[k] ?: "", { v -> valores = valores + (k to v) }, multilinea = true, lineas = 2, max = tope, placeholder = "Ej: ${cm.t("ejemplo")}")
            }
        }
        Boton(if (guardando) "Guardando…" else "Guardar respuestas", habilitado = hayCambios && !guardando) {
            guardando = true; verSync = false
            // Limpias, en una línea y sin separadores del bot (la web hace lo mismo y el servidor lo valida).
            val limpio = buildJsonObject {
                valores.forEach { (k, v) ->
                    val t = v.replace(Regex("[|=]"), " ").replace(Regex("\\s+"), " ").trim().take(tope)
                    if (t.isNotEmpty()) put(k, t)
                }
            }
            scope.launch {
                val r = guardarAjuste { AjustesRepo.guardarConfigSani("mostrador", limpio) }
                guardando = false
                if (r.registrada) {
                    valores = campos.associate { it.t("clave") to (limpio.s(it.t("clave")) ?: "") }; base = valores
                    botSync = r.cuerpo?.s("botSync"); verSync = true
                    Toaster.exito(mensajeGuardadoSani(botSync)); onCambio()
                }
            }
        }
        if (!hayCambios) {
            val n = base.values.count { it.isNotBlank() }
            if (n > 0) Ayuda("$n de ${campos.size} respondidas")
        }
        if (verSync) EstadoSani(botSync) { botSync = it }
    }
}

// ─── 🏷️ Categorías de finanzas ──────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SeccionCategorias(onVolver: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var recarga by remember { mutableIntStateOf(0) }
    val carga = recordarCatalogo("categorias_movimiento", recarga)
    val datos = carga.datos
    var tipo by remember { mutableStateOf("Egreso") }
    var nueva by remember { mutableStateOf("") }
    val delTipo = (datos?.objetos("items") ?: emptyList()).filter { it.t("tipo") == tipo }

    SubPantalla("Categorías de finanzas", onVolver) {
        Ayuda("Define tus categorías de ingreso y egreso para que todo tu equipo registre igual y los reportes cuadren. Igual puedes escribir una libre al momento.")
        ChipsEleccion(listOf("Egreso" to "💸 Egresos", "Ingreso" to "💰 Ingresos"), tipo) { tipo = it }
        if (datos == null) { CargandoOError(carga) { recarga++ }; return@SubPantalla }
        Tarjeta {
            if (delTipo.isEmpty()) Ayuda("Sin categorías de ${tipo.lowercase()} aún.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                delTipo.forEach { cat ->
                    PastillaBorrable(cat.t("nombre")) {
                        scope.launch {
                            val r = guardarAjuste(Gestion.ELIMINANDO, "No se pudo eliminar") { AjustesRepo.borrarDeCatalogo("categorias_movimiento", cat.t("id")) }
                            if (r.registrada) recarga++
                        }
                    }
                }
            }
            Campo(null, nueva, { nueva = it }, placeholder = "Nueva categoría de ${tipo.lowercase()} (ej. ${if (tipo == "Egreso") "Insumos, Publicidad" else "Venta de productos"})", max = 80)
            Boton("+ Agregar", habilitado = nueva.isNotBlank()) {
                if (delTipo.any { it.t("nombre").equals(nueva.trim(), true) }) { Toaster.error("Ya existe esa categoría"); return@Boton }
                scope.launch {
                    val r = guardarAjuste(porDefecto = "No se pudo agregar") { AjustesRepo.crearEnCatalogo("categorias_movimiento", buildJsonObject { put("nombre", nueva); put("tipo", tipo) }) }
                    if (r.registrada) { nueva = ""; recarga++ }
                }
            }
        }
    }
}

// ─── 🖼️ Imágenes clínicas ───────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SeccionImagenesClinicas(d: JsonObject, onVolver: () -> Unit, onCambio: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val contextos = d.o("catalogos")?.objetos("contextosImagen") ?: emptyList()
    var fotos by remember { mutableStateOf(d.o("config")?.b("fotosEvolutivasActiva") != false) }
    var fotosGuardado by remember { mutableStateOf(fotos) }
    var recarga by remember { mutableIntStateOf(0) }
    val carga = if (d.o("plan")?.b("fotosEvolutivas") == true) recordarCatalogo("tipos_imagen", recarga) else null
    val datos = carga?.datos
    var contexto by remember { mutableStateOf("evolucion") }
    var nuevo by remember { mutableStateOf("") }
    val delContexto = (datos?.objetos("items") ?: emptyList()).filter { it.t("contexto") == contexto }

    SubPantalla("Imágenes clínicas", onVolver) {
        if (d.o("plan")?.b("fotosEvolutivas") != true) {
            CandadoPlan("Las imágenes clínicas son del plan Premium", "Registra la evolución del paciente con fotos por tratamiento y define los tipos de imagen de tu rubro.")
            return@SubPantalla
        }
        Tarjeta {
            FilaInterruptor("📷 Fotos evolutivas (antes / después)", "Registra la evolución del paciente con fotos por tratamiento. Son privadas: tú decides cuáles mostrarle.", fotos) { fotos = it }
            Boton("Guardar", primario = false, habilitado = fotos != fotosGuardado) {
                scope.launch {
                    val r = guardarAjuste { AjustesRepo.guardarSeccion("fotos-evolutivas", buildJsonObject { put("activo", fotos) }) }
                    if (r.registrada) { fotosGuardado = fotos; Toaster.exito("Guardado"); onCambio() }
                }
            }
        }
        Tarjeta("Tipos de imagen") {
            Ayuda("Los tipos de imagen que tu clínica usa. Aparecen al subir fotos en la ficha del paciente. Ajústalos a tu rubro (ej. odontograma y radiografías para dental, antes/después para estética).")
            ChipsEleccion(contextos.map { it.t("v") to it.t("label") }, contexto) { contexto = it }
            Ayuda(contextos.firstOrNull { it.t("v") == contexto }?.t("ayuda") ?: "")
            if (datos == null) CargandoOError(carga) { recarga++ }
            else {
                if (delContexto.isEmpty()) Ayuda("Sin tipos en esta categoría.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    delContexto.forEach { t ->
                        PastillaBorrable(t.t("nombre")) {
                            scope.launch {
                                val r = guardarAjuste(Gestion.ELIMINANDO, "No se pudo eliminar") { AjustesRepo.borrarDeCatalogo("tipos_imagen", t.t("id")) }
                                if (r.registrada) recarga++
                            }
                        }
                    }
                }
                Campo(null, nuevo, { nuevo = it }, placeholder = when (contexto) { "documento" -> "Ej. Odontograma, Radiografía"; "servicio" -> "Ej. Inicial, Final"; else -> "Ej. Antes, Después" }, max = 80)
                Boton("+ Agregar", habilitado = nuevo.isNotBlank()) {
                    if (delContexto.any { it.t("nombre").equals(nuevo.trim(), true) }) { Toaster.error("Ya existe ese tipo"); return@Boton }
                    scope.launch {
                        val r = guardarAjuste(porDefecto = "No se pudo agregar") { AjustesRepo.crearEnCatalogo("tipos_imagen", buildJsonObject { put("nombre", nuevo); put("contexto", contexto) }) }
                        if (r.registrada) { nuevo = ""; recarga++ }
                    }
                }
            }
        }
    }
}

// ─── 🏢 Perfil comercial ────────────────────────────────────────────────────

@Composable
internal fun SeccionComercial(d: JsonObject, onVolver: () -> Unit, onCambio: () -> Unit) {
    val scope = rememberCoroutineScope()
    val cfg = d.o("config") ?: JsonObject(emptyMap())
    val monedas = (d.o("catalogos")?.objetos("monedas") ?: emptyList()).map { it.t("valor") to it.t("etiqueta") }
    var razon by remember { mutableStateOf(cfg.t("razonSocial")) }
    var ruc by remember { mutableStateOf(cfg.t("ruc")) }
    var direccion by remember { mutableStateOf(cfg.t("direccion")) }
    // País, moneda y zona horaria de la clínica (clinicas.*): las cambia SOLO el
    // Admin con PATCH /api/staff/clinica/regional (el servidor las protege).
    // Tocar un chip NO guarda: se elige, "Guardar país, moneda y zona" y una
    // confirmación con las consecuencias (documento, prefijo, "hoy", moneda).
    var monedaGuardada by remember { mutableStateOf(cfg.t("moneda").ifBlank { "PEN" }) }
    var paisGuardado by remember { mutableStateOf(cfg.t("pais").ifBlank { "PE" }) }
    var zonaGuardada by remember { mutableStateOf(cfg.t("zona").ifBlank { "America/Lima" }) }
    var moneda by remember { mutableStateOf(monedaGuardada) }
    var pais by remember { mutableStateOf(paisGuardado) }
    var zona by remember { mutableStateOf(zonaGuardada) }
    var guardandoRegional by remember { mutableStateOf(false) }
    var confirmar by remember { mutableStateOf<List<String>?>(null) }
    val puedeCambiarMoneda = d.b("esAdmin") && !d.b("soloLectura") &&
        (pe.saniape.app.data.staff.StaffContextoRepo.actual?.esAdmin ?: true)
    var guardando by remember { mutableStateOf(false) }

    /** Al elegir un país se proponen su moneda y su zona (se pueden cambiar antes de guardar). */
    fun elegirPais(nuevo: String) {
        if (nuevo == pais) return
        pais = nuevo
        pe.saniape.app.data.staff.paisPorCodigo(nuevo)?.let { moneda = it.moneda; zona = it.zonas.first() }
    }

    val cambios: Map<String, String> = buildMap {
        if (pais != paisGuardado) put("pais", pais)
        if (moneda != monedaGuardada) put("moneda", moneda)
        if (zona != zonaGuardada) put("zona", zona)
    }

    /** Aplica lo que respondió el servidor y refresca el contexto regional de la app. */
    suspend fun aplicarRegional(cuerpo: JsonObject?) {
        cuerpo?.t("pais")?.ifBlank { null }?.let { paisGuardado = it }
        cuerpo?.t("moneda")?.ifBlank { null }?.let { monedaGuardada = it }
        cuerpo?.t("zona")?.ifBlank { null }?.let { zonaGuardada = it }
        pais = paisGuardado; moneda = monedaGuardada; zona = zonaGuardada
        runCatching { pe.saniape.app.data.staff.StaffContextoRepo.cargar() }
        onCambio()
    }

    /**
     * Guarda lo elegido (ya confirmado). Con cobros registrados la moneda no
     * cambia (409 MONEDA_CON_COBROS): se guardan el país y la zona solos y se
     * dice claro que la moneda quedó como estaba.
     */
    fun guardarRegional() {
        val envio = cambios
        if (envio.isEmpty() || guardandoRegional) return
        guardandoRegional = true
        scope.launch {
            val r = pe.saniape.app.ui.conIndicador {
                AjustesRepo.cambiarRegionalClinica(buildJsonObject { envio.forEach { (k, v) -> put(k, v) } })
            }
            if (r.registrada) {
                aplicarRegional(r.cuerpo)
                Toaster.exito("País, moneda y zona actualizados")
            } else if (r.rechazo?.codigo == "MONEDA_CON_COBROS" && ("pais" in envio || "zona" in envio)) {
                val r2 = guardarAjuste(porDefecto = "No se pudo cambiar el país ni la zona horaria.") {
                    AjustesRepo.cambiarRegionalClinica(buildJsonObject { envio.filterKeys { it != "moneda" }.forEach { (k, v) -> put(k, v) } })
                }
                if (r2.registrada) {
                    aplicarRegional(r2.cuerpo)
                    Toaster.exito("País y zona guardados. La moneda sigue en $monedaGuardada: ya hay cobros registrados y cambiarla no convierte los montos.")
                }
            } else {
                Toaster.error(r.rechazo?.error ?: "No se pudo cambiar el país, la moneda o la zona horaria.")
            }
            guardandoRegional = false
        }
    }

    confirmar?.let { lista ->
        AlertaConTeclado(
            onDismissRequest = { confirmar = null },
            title = { Text("¿Cambiar país, zona horaria o moneda?", fontWeight = FontWeight.Bold) },
            text = { Text("Esto cambia en toda la clínica:\n\n" + lista.joinToString("\n\n") { "• $it" }) },
            confirmButton = { TextButton(onClick = { confirmar = null; guardarRegional() }) { Text("Sí, cambiar", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirmar = null }) { Text("Cancelar", color = Sania.colors.textoSuave) } },
            containerColor = Sania.colors.superficie,
        )
    }

    SubPantalla("Perfil comercial", onVolver) {
        Tarjeta {
            Campo("Razón social", razon, { razon = it }, max = 200)
            Campo("RUC / NIT / Tax ID", ruc, { ruc = it }, placeholder = "Ej. 20123456789", teclado = KeyboardType.Number, max = 30)
            Campo("Dirección física", direccion, { direccion = it }, placeholder = "Av. Principal 123, Ciudad", max = 300)
        }
        Boton(if (guardando) "Guardando…" else "Guardar perfil comercial", habilitado = !guardando) {
            guardando = true
            scope.launch {
                val r = guardarAjuste {
                    AjustesRepo.guardarSeccion("comercial", buildJsonObject { put("razonSocial", razon); put("ruc", ruc); put("direccion", direccion) })
                }
                guardando = false
                if (r.registrada) { Toaster.exito("Guardado"); onCambio() }
            }
        }
        // País, moneda y zona horaria (multipaís). Solo el Admin.
        Tarjeta {
            val bloqueados = !puedeCambiarMoneda || guardandoRegional
            val paises = pe.saniape.app.data.staff.PAISES_SOPORTADOS.map { it.codigo to it.nombre }
                .let { l -> if (l.none { it.first == pais }) l + (pais to pais) else l }
            val zonas = pe.saniape.app.data.staff.zonasDePais(pais, zona).map { it to it }
            // País y moneda guardados que no coinciden: se dice siempre.
            pe.saniape.app.data.staff.avisoPaisMoneda(paisGuardado, monedaGuardada)?.let {
                Text(it, color = Sania.colors.pend, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            EtqForm("País")
            ChipsEleccion(paises, pais, deshabilitados = if (bloqueados) paises.map { it.first }.toSet() else emptySet()) { elegirPais(it) }
            EtqForm("Moneda principal")
            ChipsEleccion(monedas, moneda, deshabilitados = if (bloqueados) monedas.map { it.first }.toSet() else emptySet()) { moneda = it }
            EtqForm("Zona horaria")
            ChipsEleccion(zonas, zona, deshabilitados = if (bloqueados) zonas.map { it.first }.toSet() else emptySet()) { zona = it }
            Ayuda(
                if (puedeCambiarMoneda) "El país define el documento de tus pacientes (DNI, CI…), el prefijo de los teléfonos y los métodos de pago sugeridos. Al elegirlo se proponen su moneda y su zona horaria; puedes cambiarlas. Cambiar la moneda no convierte los montos ya registrados. Si tienes sedes en otros países, cada sede tiene lo suyo (Ajustes → Sedes)."
                else "Solo el administrador puede cambiar el país, la moneda y la zona horaria."
            )
        }
        if (puedeCambiarMoneda && cambios.isNotEmpty()) {
            Boton(if (guardandoRegional) "Guardando…" else "Guardar país, moneda y zona", habilitado = !guardandoRegional) {
                confirmar = pe.saniape.app.data.staff.consecuenciasCambioRegional(
                    paisGuardado, monedaGuardada, zonaGuardada, pais, moneda, zona)
            }
            Boton("Descartar cambios", primario = false, habilitado = !guardandoRegional) {
                pais = paisGuardado; moneda = monedaGuardada; zona = zonaGuardada
            }
        }
    }
}

// ─── ⏰ Horarios de atención ────────────────────────────────────────────────

internal data class DiaAtencion(val dia: String, val activo: Boolean, val apertura: String, val cierre: String)

/** Editor de la semana (lo usan los horarios de la clínica y los de cada sede). */
@Composable
internal fun EditorSemana(dias: List<DiaAtencion>, habilitado: Boolean = true, onCambio: (List<DiaAtencion>) -> Unit) {
    val c = Sania.colors
    var hora by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }   // (índice, apertura)
    hora?.let { (i, apertura) ->
        DialogoHora(if (apertura) dias[i].apertura else dias[i].cierre, onElegir = { v ->
            onCambio(dias.toMutableList().also { it[i] = if (apertura) it[i].copy(apertura = v) else it[i].copy(cierre = v) })
        }, onCerrar = { hora = null })
    }
    dias.forEachIndexed { i, dd ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (dd.activo) c.superficie else c.fondo)
                .border(1.dp, c.borde, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(checked = dd.activo, enabled = habilitado, onCheckedChange = { v -> onCambio(dias.toMutableList().also { it[i] = dd.copy(activo = v) }) },
                colors = SwitchDefaults.colors(checkedTrackColor = c.navy))
            Spacer(Modifier.width(8.dp))
            Text(dd.dia, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(78.dp))
            if (dd.activo) {
                Text(pe.saniape.app.ui.hora12(dd.apertura), color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickableSimple { if (habilitado) hora = i to true }.padding(6.dp))
                Text("a", color = c.textoSuave, fontSize = 12.sp)
                Text(pe.saniape.app.ui.hora12(dd.cierre), color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickableSimple { if (habilitado) hora = i to false }.padding(6.dp))
            } else Text("CERRADO", color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

internal fun diasDe(lista: List<JsonObject>): List<DiaAtencion> = lista.map { DiaAtencion(it.t("dia"), it.b("activo"), it.t("apertura").ifBlank { "08:00" }, it.t("cierre").ifBlank { "18:00" }) }
internal fun diasJson(dias: List<DiaAtencion>) = buildJsonArray {
    dias.forEach { dd -> add(buildJsonObject { put("dia", dd.dia); put("activo", dd.activo); put("apertura", dd.apertura); put("cierre", dd.cierre) }) }
}

@Composable
internal fun SeccionHorarios(d: JsonObject, onVolver: () -> Unit, onCambio: () -> Unit) {
    val scope = rememberCoroutineScope()
    var dias by remember { mutableStateOf(diasDe(d.o("config")?.objetos("horarios") ?: emptyList())) }
    var guardando by remember { mutableStateOf(false) }
    SubPantalla("Horarios de atención", onVolver) {
        Ayuda("Configura las horas en las que tu clínica recibe pacientes. Esto delimitará los bloques de tiempo disponibles en el calendario.")
        EditorSemana(dias) { dias = it }
        Boton(if (guardando) "Guardando…" else "Actualizar calendario", habilitado = !guardando) {
            guardando = true
            scope.launch {
                val r = guardarAjuste { AjustesRepo.guardarSeccion("horarios", buildJsonObject { put("horarios", diasJson(dias)) }) }
                guardando = false
                if (r.registrada) { Toaster.exito("Guardado"); onCambio() }
            }
        }
    }
}

// ─── 👥 Pacientes a la misma hora ───────────────────────────────────────────

@Composable
internal fun SeccionSimultaneas(d: JsonObject, onVolver: () -> Unit, onCambio: () -> Unit) {
    val scope = rememberCoroutineScope()
    var max by remember { mutableStateOf(d.o("config")?.n("maxSimultaneas")?.toInt()?.toString() ?: "") }
    var guardando by remember { mutableStateOf(false) }
    SubPantalla("Pacientes a la misma hora", onVolver) {
        Ayuda("Cuántos pacientes puede atender un mismo profesional en el mismo horario, cuando el paciente reserva desde la app. Déjalo vacío si no quieres límite: en fisioterapia es normal atender a varios a la vez (uno con el aparato mientras se atiende a otro).")
        Tarjeta {
            Campo("Máximo por profesional", max, { max = it.filter { ch -> ch.isDigit() } }, placeholder = "Sin límite", teclado = KeyboardType.Number, max = 2)
        }
        Boton(if (guardando) "Guardando…" else "Guardar", habilitado = !guardando) {
            if ((max.toIntOrNull() ?: 0) > 20) { Toaster.error("Máximo 20"); return@Boton }
            guardando = true
            scope.launch {
                val r = guardarAjuste { AjustesRepo.guardarSeccion("simultaneas", buildJsonObject { put("max", max) }) }
                guardando = false
                if (r.registrada) { Toaster.exito("Guardado"); onCambio() }
            }
        }
        Ayuda("Si el profesional que eligió el paciente llega al tope, la app le ofrece otro profesional de la misma especialidad u otra hora — no lo deja sin cita.")
    }
}
