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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.http.HttpMethod
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.staff.AjustesRepo
import pe.saniape.app.ui.ManejarAtras
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.theme.Sania

// ─── El flujo como lo guarda la web (configuracion.flujo_preset / especialidades.flujo_preset) ───

internal data class Flujo(
    val usaConsulta: Boolean = true,
    val usaEvaluacion: Boolean = true,
    val labelConsulta: String = "Consulta",
    val labelEvaluacion: String = "Evaluación",
    val labelSesiones: String = "Sesiones",
    val labelAlta: String = "Alta",
) {
    /** "dos" | "una" | "ninguna" (cómo empieza la atención). */
    val modo: String get() = if (usaConsulta && usaEvaluacion) "dos" else if (!usaConsulta && !usaEvaluacion) "ninguna" else "una"
    /** Con UNA cita de entrada, qué tipo interno la representa (se conserva el que ya tenía). */
    val entrada: String get() = if (!usaConsulta && usaEvaluacion) "Evaluación" else "Consulta"
    val nombreEntrada: String get() = if (entrada == "Evaluación") labelEvaluacion else labelConsulta
    fun conNombreEntrada(v: String) = if (entrada == "Evaluación") copy(labelEvaluacion = v) else copy(labelConsulta = v)

    /** Los tipos de cita que ofrece, en el orden en que los vive el paciente. */
    val tipos: List<String> get() = listOfNotNull("Consulta".takeIf { usaConsulta }, "Evaluación".takeIf { usaEvaluacion }, "Sesión")
    /** "Así lo vive tu paciente" (el alta no es una cita: no tiene minutos). */
    val recorrido: List<Pair<String, String?>> get() = listOfNotNull(
        (labelConsulta to "Consulta").takeIf { usaConsulta }, (labelEvaluacion to "Evaluación").takeIf { usaEvaluacion },
        labelSesiones to "Sesión", labelAlta to null,
    )
    fun nombreTipo(t: String) = when (t) { "Consulta" -> labelConsulta; "Evaluación" -> labelEvaluacion; else -> if (labelSesiones == "Sesiones") "Sesión" else labelSesiones }

    fun json() = buildJsonObject {
        put("usa_consulta", usaConsulta); put("usa_evaluacion", usaEvaluacion)
        put("label_consulta", labelConsulta); put("label_evaluacion", labelEvaluacion)
        put("label_sesiones", labelSesiones); put("label_alta", labelAlta)
    }

    companion object {
        fun de(o: JsonObject?): Flujo = if (o == null) Flujo() else Flujo(
            o.bn("usa_consulta") ?: true, o.bn("usa_evaluacion") ?: true,
            o.s("label_consulta") ?: "Consulta", o.s("label_evaluacion") ?: "Evaluación",
            o.s("label_sesiones") ?: "Sesiones", o.s("label_alta") ?: "Alta",
        )

        /** Gemelo de flujoDeEspecialidad (web): sin las dos banderas, la especialidad sigue a la clínica. */
        fun deEspecialidad(base: Flujo, propio: JsonObject?): Flujo {
            if (propio == null) return base
            val c = propio.bn("usa_consulta") ?: return base
            val e = propio.bn("usa_evaluacion") ?: return base
            fun txt(k: String, d: String) = propio.s(k)?.takeIf { it.isNotBlank() } ?: d
            return Flujo(c, e, txt("label_consulta", base.labelConsulta), txt("label_evaluacion", base.labelEvaluacion),
                txt("label_sesiones", base.labelSesiones), txt("label_alta", base.labelAlta))
        }
    }
}

/** Cambia el modo conservando los nombres (misma lógica de pantalla que la web). */
internal fun Flujo.conModo(m: String, guardado: Flujo): Flujo = when (m) {
    "dos" -> copy(usaConsulta = true, usaEvaluacion = true)
    "ninguna" -> copy(usaConsulta = false, usaEvaluacion = false)
    else -> {
        val ent = if (modo == "una") entrada else guardado.entrada
        val nombre = if (ent == "Evaluación") (if (usaEvaluacion) labelEvaluacion else labelConsulta) else (if (usaConsulta) labelConsulta else labelEvaluacion)
        if (ent == "Evaluación") copy(usaConsulta = false, usaEvaluacion = true, labelEvaluacion = nombre)
        else copy(usaConsulta = true, usaEvaluacion = false, labelConsulta = nombre)
    }
}

/** Nombres vacíos caen al nombre guardado (o al de siempre). */
internal fun Flujo.limpio(guardado: Flujo) = copy(
    labelConsulta = labelConsulta.trim().ifBlank { guardado.labelConsulta.ifBlank { "Consulta" } },
    labelEvaluacion = labelEvaluacion.trim().ifBlank { guardado.labelEvaluacion.ifBlank { "Evaluación" } },
    labelSesiones = labelSesiones.trim().ifBlank { guardado.labelSesiones.ifBlank { "Sesiones" } },
    labelAlta = labelAlta.trim().ifBlank { guardado.labelAlta.ifBlank { "Alta" } },
)

private fun duracionesDe(o: JsonObject?): Map<String, Int> = listOf("Consulta", "Evaluación", "Sesión").associateWith { (o?.n(it) ?: 0.0).toInt() }

/** Misma validación de pantalla que la web (validarDuracion); el servidor la vuelve a hacer. */
private fun errorMinutos(v: String, min: Int, max: Int): String? {
    val t = v.trim()
    if (t.isEmpty()) return "Escribe cuántos minutos dura"
    val n = t.toIntOrNull() ?: return if (t.toDoubleOrNull() != null) "Usa minutos enteros, sin decimales" else "Tiene que ser un número de minutos"
    if (n < min) return "El mínimo son $min minutos"
    if (n > max) return "El máximo son ${max / 60} horas"
    return null
}

private fun formatoMinutos(m: Int): String = when {
    m < 60 -> "$m min"
    m % 60 == 0 -> "${m / 60} h"
    else -> "${m / 60} h ${m % 60} min"
}

// ─── 🔀 Flujo de atención ───────────────────────────────────────────────────

/**
 * Cómo empieza la atención, cómo se llama cada paso y cuánto dura. Con
 * especialidades activas, cada una se configura por separado (la "base" rige lo
 * que llega sin especialidad: el bot, la reserva web). Sin especialidades, el
 * flujo de la clínica con sus plantillas rápidas por rubro.
 */
@Composable
internal fun SeccionFlujo(d: JsonObject, onVolver: () -> Unit, onCambio: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val esps = d.objetos("especialidades")
    val flujoClinica = Flujo.de(d.o("flujo"))
    val baseId = d.s("baseFlujoId")
    var abierta by remember { mutableStateOf<String?>(null) }
    var verHallazgos by remember { mutableStateOf(false) }
    var elegirBase by remember { mutableStateOf(false) }
    var cambiandoBase by remember { mutableStateOf(false) }
    // El flujo de la clínica (sin especialidades) tiene cambios sin guardar.
    var selectorSucio by remember { mutableStateOf(false) }

    ManejarAtras(activo = abierta != null || verHallazgos) { if (verHallazgos) verHallazgos = false else abierta = null }
    if (verHallazgos) { SeccionHallazgos({ verHallazgos = false }, volver = "← Flujo de atención"); return }
    val esp = esps.firstOrNull { it.t("id") == abierta }
    if (esp != null) {
        EditorFlujoEspecialidad(d, esp, esBase = esp.t("id") == baseId, flujoClinica,
            onVolver = { abierta = null }, onGuardado = onCambio, onHallazgos = { verHallazgos = true })
        return
    }

    if (elegirBase) DialogoLista("Las citas sin especialidad siguen el flujo de", esps.map { it.t("id") to "${it.t("icono")} ${it.t("nombre")}" }, { id ->
        elegirBase = false
        if (id == baseId) return@DialogoLista
        cambiandoBase = true
        scope.launch {
            val r = guardarAjuste { AjustesRepo.enviar(HttpMethod.Put, "${AjustesRepo.BASE}/flujo", buildJsonObject { put("accion", "base"); put("especialidadId", id) }) }
            cambiandoBase = false
            if (r.registrada) { Toaster.exito("Lo que llega sin especialidad sigue ahora el flujo de ${r.cuerpo?.s("nombre") ?: ""}"); onCambio() }
        }
    }, { elegirBase = false })

    SubPantalla("Flujo de atención", onVolver, sinGuardar = selectorSucio) {
        if (esps.isEmpty()) {
            SelectorFlujoClinica(d, flujoClinica, onCambio) { selectorSucio = it }
        } else {
            val varias = esps.size > 1
            Ayuda((if (varias) "Cómo empieza la atención en cada especialidad, cómo se llama cada paso y cuánto dura. Cada una se configura por separado."
                else "Cómo empieza la atención, cómo se llama cada paso y cuánto dura.") + " Es la forma de nombrarlos: tus datos y tus precios no se tocan.")
            val propias = d.o("duracionesEspecialidad") ?: JsonObject(emptyMap())
            esps.forEach { e ->
                val f = if (e.t("id") == baseId) flujoClinica else Flujo.deEspecialidad(flujoClinica, e.o("flujoPreset"))
                val tiempos = (propias.o(e.t("id"))?.size ?: 0) > 0
                FilaNav("${e.t("icono")}  ${e.t("nombre")}", f.recorrido.joinToString(" → ") { it.first } + if (tiempos) " · tiempos propios" else "") { abierta = e.t("id") }
            }
            if (varias) Tarjeta {
                Text("Las citas sin especialidad (bot, reserva web) siguen el flujo de", color = c.texto, fontSize = 13.sp)
                Selector(null, esps.firstOrNull { it.t("id") == baseId }?.let { "${it.t("icono")} ${it.t("nombre")}" } ?: "", habilitado = !cambiandoBase) { elegirBase = true }
            }
            EntradaBotSani(d, flujoClinica)
            Tarjeta("Tiempos por defecto") {
                Ayuda("Los usa cada especialidad que no tiene los suyos. Es una sugerencia: quien agenda puede cambiarlo en esa cita.")
                DuracionesClinica(d, flujoClinica, onCambio)
            }
        }
    }
}

/** Flujo de la clínica cuando no hay especialidades (el selector de siempre, con plantillas por rubro). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectorFlujoClinica(d: JsonObject, guardado: Flujo, onCambio: () -> Unit, onSinGuardar: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val cat = d.o("catalogos") ?: JsonObject(emptyMap())
    var borrador by remember(guardado) { mutableStateOf(guardado) }
    var guardando by remember { mutableStateOf(false) }
    var botSync by remember { mutableStateOf<String?>(null) }
    var verSync by remember { mutableStateOf(false) }
    val limpio = borrador.limpio(guardado)
    val sinCambios = limpio == guardado
    val invalido = borrador.modo == "una" && borrador.nombreEntrada.isBlank()
    LaunchedEffect(sinCambios, guardando) { onSinGuardar(!sinCambios && !guardando) }

    Ayuda("Define cómo empieza la atención en tu clínica y cómo se llama cada paso. Es la forma de nombrarlos: tus datos y tus precios no se tocan.")
    FormularioFlujo(borrador, guardado, cat.textos("nombresEntrada"), d.o("duraciones"), null, null) { borrador = it }
    Boton(if (guardando) "Guardando…" else if (sinCambios) "Guardado" else "Guardar cambios", habilitado = !guardando && !sinCambios && !invalido) {
        guardando = true; verSync = false
        scope.launch {
            val r = guardarAjuste { AjustesRepo.guardarConfigSani("flujo_preset", limpio.json()) }
            guardando = false
            if (r.registrada) { botSync = r.cuerpo?.s("botSync"); verSync = true; Toaster.exito(mensajeGuardadoSani(botSync)); onCambio() }
        }
    }
    if (verSync) EstadoSani(botSync) { botSync = it }
    EntradaBotSani(d, guardado)
    Tarjeta("Plantillas rápidas por rubro") {
        val presets = cat.objetos("presetsFlujo")
        ChipsEleccion(presets.map { it.t("id") to it.t("nombre") }, null) { id ->
            presets.firstOrNull { it.t("id") == id }?.let { borrador = Flujo.de(it.o("preset")) }
            Toaster.info("Plantilla aplicada. Revisa los nombres y guarda.")
        }
        Ayuda("Rellenan el formulario de arriba; después ajustas y guardas.")
    }
    Tarjeta("Cuánto dura cada una") {
        Ayuda("El tiempo que se reserva en la agenda. Es una sugerencia: quien agenda puede cambiarlo en esa cita.")
        DuracionesClinica(d, guardado, onCambio)
    }
}

/**
 * Modo + nombres + (opcional) minutos por tipo + recorrido. Lo comparten el
 * selector de la clínica y el editor de cada especialidad.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormularioFlujo(
    borrador: Flujo,
    guardado: Flujo,
    nombresEntrada: List<String>,
    duracionesClinica: JsonObject?,
    minutos: Map<String, String>?,
    onMinutos: ((Map<String, String>) -> Unit)?,
    onCambio: (Flujo) -> Unit,
) {
    val c = Sania.colors
    val porDefecto = duracionesDe(duracionesClinica)
    Tarjeta("¿Cómo empieza la atención?") {
        pe.saniape.app.ui.clinica.equipo.OpcionElegible("Dos citas: consulta y después evaluación",
            detalle = "Primero una consulta y luego una evaluación con diagnóstico · ej. fisioterapia", elegida = borrador.modo == "dos") { onCambio(borrador.conModo("dos", guardado)) }
        pe.saniape.app.ui.clinica.equipo.OpcionElegible("Una sola cita de entrada",
            detalle = "Una primera cita que evalúa y define el tratamiento; tú le pones el nombre · ej. odontología, estética, medicina", elegida = borrador.modo == "una") { onCambio(borrador.conModo("una", guardado)) }
        pe.saniape.app.ui.clinica.equipo.OpcionElegible("Directo a sesiones",
            detalle = "Sin cita previa: el paciente empieza sus sesiones de una · ej. psicología", elegida = borrador.modo == "ninguna") { onCambio(borrador.conModo("ninguna", guardado)) }
    }
    Tarjeta("Cómo se llama cada paso") {
        when (borrador.modo) {
            "dos" -> {
                Campo("Primera cita", borrador.labelConsulta, { onCambio(borrador.copy(labelConsulta = it)) }, placeholder = "Consulta", max = 100)
                Campo("Evaluación", borrador.labelEvaluacion, { onCambio(borrador.copy(labelEvaluacion = it)) }, placeholder = "Evaluación", max = 100)
            }
            "una" -> {
                Campo("La cita de entrada se llama", borrador.nombreEntrada, { onCambio(borrador.conNombreEntrada(it)) }, placeholder = "Evaluación", max = 100)
                ChipsEleccion(nombresEntrada.map { it to it }, borrador.nombreEntrada.trim()) { onCambio(borrador.conNombreEntrada(it)) }
                Ayuda("Al completarla, el profesional registra el diagnóstico y define el tratamiento.")
            }
            else -> Ayuda("Sin cita de entrada: el paciente entra directo a ${borrador.labelSesiones.ifBlank { "sesiones" }.lowercase()}.", c.pend)
        }
        Campo("Las sesiones se llaman", borrador.labelSesiones, { onCambio(borrador.copy(labelSesiones = it)) }, placeholder = "Sesiones", max = 100)
        Campo("El cierre se llama", borrador.labelAlta, { onCambio(borrador.copy(labelAlta = it)) }, placeholder = "Alta", max = 100)
    }
    if (minutos != null && onMinutos != null) Tarjeta("Cuánto dura cada una") {
        Ayuda("Vacío = el tiempo por defecto.")
        borrador.tipos.forEach { t ->
            val v = minutos[t] ?: ""
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(borrador.nombreTipo(t), color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Campo(null, v, { nv -> onMinutos(minutos + (t to nv.filter { it.isDigit() })) }, placeholder = (porDefecto[t] ?: 0).toString(),
                    teclado = KeyboardType.Number, max = 3, modifier = Modifier.width(96.dp))
                Text(" min", color = c.textoSuave, fontSize = 12.sp)
            }
            Ayuda(if (v.isBlank()) "Por defecto (${formatoMinutos(porDefecto[t] ?: 0)})" else v.toIntOrNull()?.let { formatoMinutos(it) } ?: "")
        }
    }
    Tarjeta("Así lo vive tu paciente") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            borrador.recorrido.forEachIndexed { i, (nombre, tipo) ->
                Column(Modifier.clip(RoundedCornerShape(10.dp)).background(c.navy).padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(nombre.ifBlank { "—" }, color = c.sobreNavy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    tipo?.let { t ->
                        val m = minutos?.get(t)?.toIntOrNull() ?: porDefecto[t] ?: 0
                        Text("$m min", color = c.sobreNavy.copy(alpha = 0.75f), fontSize = 10.sp)
                    }
                }
                if (i < borrador.recorrido.size - 1) Text("→", color = c.textoSuave)
            }
        }
    }
}

/** Editor del flujo y los minutos de UNA especialidad. */
@Composable
private fun EditorFlujoEspecialidad(
    d: JsonObject, esp: JsonObject, esBase: Boolean, flujoClinica: Flujo,
    onVolver: () -> Unit,
    /** Algo quedó guardado (aunque sea en parte): el padre relee los ajustes. */
    onGuardado: () -> Unit,
    onHallazgos: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val cat = d.o("catalogos") ?: JsonObject(emptyMap())
    val min = cat.i("duracionMin") ?: 5
    val max = cat.i("duracionMax") ?: 720
    val guardado = if (esBase) flujoClinica else Flujo.deEspecialidad(flujoClinica, esp.o("flujoPreset"))
    val minsGuardados = remember(esp) {
        val p = d.o("duracionesEspecialidad")?.o(esp.t("id"))
        listOf("Consulta", "Evaluación", "Sesión").associateWith { t -> p?.n(t)?.toInt()?.toString() ?: "" }
    }
    var borrador by remember { mutableStateOf(guardado) }
    var mins by remember { mutableStateOf(minsGuardados) }
    var guardando by remember { mutableStateOf(false) }
    var botSync by remember { mutableStateOf<String?>(null) }
    var verSync by remember { mutableStateOf(false) }
    val limpio = borrador.limpio(guardado)
    val flujoCambio = limpio != guardado
    val minsCambiaron = borrador.tipos.any { (mins[it] ?: "").trim() != (minsGuardados[it] ?: "") }
    val errores = borrador.tipos.mapNotNull { t -> (mins[t] ?: "").takeIf { it.isNotBlank() }?.let { errorMinutos(it, min, max) }?.let { t to it } }

    SubPantalla("${esp.t("icono")} ${esp.t("nombre")}", onVolver, volver = "← Flujo de atención", sinGuardar = !guardando && (flujoCambio || minsCambiaron)) {
        if (esBase) Aviso("Las citas que llegan sin especialidad (bot, reserva web) siguen este flujo.", "info")
        FormularioFlujo(borrador, guardado, cat.textos("nombresEntrada"), d.o("duraciones"), mins, { mins = it }) { borrador = it }
        errores.forEach { (t, e) -> Aviso("${borrador.nombreTipo(t)}: $e", "error") }
        Boton(if (guardando) "Guardando…" else if (flujoCambio || minsCambiaron) "Guardar cambios" else "Guardado",
            habilitado = !guardando && (flujoCambio || minsCambiaron) && errores.isEmpty()) {
            guardando = true; verSync = false
            scope.launch {
                var algoGuardado = false
                try {
                    if (flujoCambio) {
                        val r = guardarAjuste {
                            AjustesRepo.enviar(HttpMethod.Put, "${AjustesRepo.BASE}/flujo", buildJsonObject {
                                put("accion", "especialidad"); put("especialidadId", esp.t("id")); put("flujo", limpio.json())
                            })
                        }
                        if (!r.registrada) return@launch
                        algoGuardado = true
                        // El flujo lo lee Sani: se muestra si quedó actualizado o pendiente.
                        botSync = r.cuerpo?.s("botSync"); verSync = true
                    }
                    if (minsCambiaron) {
                        // Se mandan los TRES tipos: los que el flujo no usa van vacíos.
                        val r = guardarAjuste(porDefecto = "No se pudieron guardar las duraciones") {
                            AjustesRepo.enviar(HttpMethod.Put, "${AjustesRepo.BASE}/duraciones-especialidad", buildJsonObject {
                                put("especialidadId", esp.t("id"))
                                put("duraciones", buildJsonObject {
                                    listOf("Consulta", "Evaluación", "Sesión").forEach { t ->
                                        val v = if (t in borrador.tipos) (mins[t] ?: "").trim().toIntOrNull() else null
                                        put(t, v?.let { JsonPrimitive(it) } ?: JsonNull)
                                    }
                                })
                            })
                        }
                        if (!r.registrada) return@launch
                        algoGuardado = true
                    }
                    Toaster.exito("${esp.t("nombre")}: guardado")
                } finally {
                    guardando = false
                    // Aunque las duraciones fallen, el flujo ya quedó: el padre relee lo guardado.
                    if (algoGuardado) onGuardado()
                }
            }
        }
        if (verSync) EstadoSani(botSync) { botSync = it }
        if (flujoCambio || minsCambiaron) Boton("Descartar", primario = false, habilitado = !guardando) { borrador = guardado; mins = minsGuardados }
        if (esp.b("esDental")) FilaNav("🦷  Hallazgos del odontograma", "Colores y procedimiento sugerido") { onHallazgos() }
    }
}

/** "¿Qué agenda Sani por WhatsApp?" — solo con dos citas de entrada (consulta y evaluación). */
@Composable
private fun EntradaBotSani(d: JsonObject, flujo: Flujo) {
    if (!flujo.usaConsulta || !flujo.usaEvaluacion) return
    val scope = rememberCoroutineScope()
    var actual by remember { mutableStateOf(d.o("config")?.s("entradaBot") ?: "Consulta") }
    var guardando by remember { mutableStateOf(false) }
    var botSync by remember { mutableStateOf<String?>(null) }
    var verSync by remember { mutableStateOf(false) }
    fun elegir(v: String) {
        if (guardando || v == actual) return
        guardando = true; verSync = false
        scope.launch {
            val r = guardarAjuste { AjustesRepo.guardarConfigSani("sani_entrada_bot", JsonPrimitive(v)) }
            guardando = false
            if (r.registrada) { actual = v; botSync = r.cuerpo?.s("botSync"); verSync = true; Toaster.exito(mensajeGuardadoSani(botSync)) }
        }
    }
    Tarjeta("¿Qué agenda Sani por WhatsApp?") {
        pe.saniape.app.ui.clinica.equipo.OpcionElegible("La ${flujo.labelConsulta.lowercase()}", detalle = "Como tu flujo: primero la consulta.", elegida = actual == "Consulta") { elegir("Consulta") }
        pe.saniape.app.ui.clinica.equipo.OpcionElegible("Directo la ${flujo.labelEvaluacion.lowercase()}",
            detalle = "Sani ofrece y agenda solo la ${flujo.labelEvaluacion.lowercase()}, con su precio.", elegida = actual == "Evaluación") { elegir("Evaluación") }
        Ayuda("Solo cambia lo que ofrece Sani. En recepción sigues agendando las dos.")
        if (verSync) EstadoSani(botSync) { botSync = it }
    }
}

/** Los minutos por defecto de la clínica (configuracion, por tipo interno de cita). */
@Composable
private fun DuracionesClinica(d: JsonObject, flujo: Flujo, onCambio: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val cat = d.o("catalogos") ?: JsonObject(emptyMap())
    val min = cat.i("duracionMin") ?: 5
    val max = cat.i("duracionMax") ?: 720
    val guardadas = duracionesDe(d.o("duraciones"))
    var valores by remember { mutableStateOf(guardadas.mapValues { it.value.toString() }) }
    var guardando by remember { mutableStateOf(false) }
    var botSync by remember { mutableStateOf<String?>(null) }
    var verSync by remember { mutableStateOf(false) }
    val errores = valores.mapValues { errorMinutos(it.value, min, max) }
    val sinCambios = guardadas.all { (k, v) -> valores[k] == v.toString() }

    listOf("Consulta", "Evaluación", "Sesión").forEach { t ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(flujo.nombreTipo(t), color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Campo(null, valores[t] ?: "", { nv -> valores = valores + (t to nv.filter { it.isDigit() }) }, teclado = KeyboardType.Number, max = 3,
                modifier = Modifier.width(96.dp))
            Text(" min", color = c.textoSuave, fontSize = 12.sp)
        }
        errores[t]?.let { Text(it, color = c.error, fontSize = 11.sp) } ?: Ayuda(formatoMinutos(valores[t]?.toIntOrNull() ?: 0))
    }
    Boton(if (guardando) "Guardando…" else "Guardar tiempos", habilitado = !guardando && !sinCambios && errores.values.all { it == null }) {
        guardando = true; verSync = false
        scope.launch {
            val r = guardarAjuste {
                AjustesRepo.enviar(HttpMethod.Put, "${AjustesRepo.BASE}/duraciones", buildJsonObject {
                    valores.forEach { (k, v) -> put(k, v.toInt()) }
                })
            }
            guardando = false
            if (r.registrada) { botSync = r.cuerpo?.s("botSync"); verSync = true; onCambio() }
        }
    }
    if (verSync) EstadoSani(botSync) { botSync = it }
    Ayuda("Es el tiempo con el que se abre el formulario al agendar y con el que el asistente ofrece horarios. Quien agenda puede cambiarlo en cada cita, y las citas ya agendadas conservan el tiempo con el que se crearon.")
}
