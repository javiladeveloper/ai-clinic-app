package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 📝 INDICACIONES PARA CASA (todas las especialidades): lo que el profesional le
 * deja al paciente tras una atención — qué hacer, qué evitar, los signos de
 * alarma y cuándo volver. Tablas `indicaciones_paciente` y
 * `plantillas_indicaciones`, vía /api/staff/indicaciones (ver [IndicacionesRepo]).
 *
 * Gemelo PURO de `lib/indicaciones.ts` de la web: validación (mismos textos),
 * limpieza de "una por línea", el texto del WhatsApp (salida idéntica) y la
 * hoja imprimible (disposición de HojaIndicaciones.tsx).
 *
 * Las plantillas y chips BASE por especialidad NO viven aquí: la única copia
 * es `lib/indicaciones-base.ts` de la web y la app los pide a
 * GET /api/staff/indicaciones/catalogo ([CatalogoIndicaciones]).
 */

/** Límites de texto: gemelos de los CHECK de la base. */
object LimitesIndicaciones {
    const val TITULO = 120
    const val TEXTO = 4000 // indicaciones, evitar, signos de alarma
    const val CONTROL = 500
    const val NOMBRE_PLANTILLA = 80
}

/** Los cuatro campos con chips (las claves del API / la base). */
val CAMPOS_INDICACION: List<String> = listOf("indicaciones", "evitar", "signos_alarma", "control")

/** Lo común de una hoja, una plantilla de la clínica y una plantilla base. */
interface ContenidoIndicacionesFuente {
    val titulo: String
    val indicaciones: String?
    val evitar: String?
    val signosAlarma: String?
    val control: String?
}

/** Una fila de `indicaciones_paciente`. */
data class IndicacionPaciente(
    val id: String,
    val pacienteId: String,
    val fecha: String,
    override val titulo: String,
    override val indicaciones: String? = null,
    override val evitar: String? = null,
    override val signosAlarma: String? = null,
    override val control: String? = null,
    val tratamientoId: String? = null,
    val citaId: String? = null,
    val terapeutaId: String? = null,
    val especialidadId: String? = null,
    /** Lo ve el paciente en su app / portal (solo un `false` explícito la oculta). */
    val visiblePaciente: Boolean = true,
    val createdAt: String? = null,
) : ContenidoIndicacionesFuente

/** Una fila de `plantillas_indicaciones` (las de la clínica). */
data class PlantillaIndicaciones(
    val id: String,
    val nombre: String,
    override val titulo: String,
    override val indicaciones: String? = null,
    override val evitar: String? = null,
    override val signosAlarma: String? = null,
    override val control: String? = null,
    val especialidadId: String? = null,
) : ContenidoIndicacionesFuente

/** Una plantilla BASE del catálogo (sugerencia editable, por rubro). */
data class PlantillaBaseIndicaciones(
    val clave: String,
    val nombre: String,
    override val titulo: String,
    val rubros: List<String> = emptyList(),
    override val indicaciones: String? = null,
    override val evitar: String? = null,
    override val signosAlarma: String? = null,
    override val control: String? = null,
) : ContenidoIndicacionesFuente

/**
 * GET /api/staff/indicaciones/catalogo: plantillas y chips base de la
 * especialidad pedida (o de la clínica entera), y la plantilla que sugiere el
 * servicio (`sugerida` = su clave, o null).
 */
data class CatalogoIndicaciones(
    val rubro: String? = null,
    val rubros: List<String> = emptyList(),
    val plantillas: List<PlantillaBaseIndicaciones> = emptyList(),
    /** Por campo (CAMPOS_INDICACION): los del rubro primero, luego los generales. */
    val chips: Map<String, List<String>> = emptyMap(),
    val sugerida: String? = null,
) {
    /** La plantilla sugerida por el servicio (si está entre las del catálogo). */
    val plantillaSugerida: PlantillaBaseIndicaciones? get() = sugerida?.let { k -> plantillas.firstOrNull { it.clave == k } }
}

/** Lo aprendido de la clínica (GET /sugerencias), por campo. */
data class SugerenciasIndicaciones(
    val indicaciones: List<AprendidoPlan> = emptyList(),
    val evitar: List<AprendidoPlan> = emptyList(),
    val signosAlarma: List<AprendidoPlan> = emptyList(),
    val control: List<AprendidoPlan> = emptyList(),
) {
    fun deCampo(campo: String): List<AprendidoPlan> = when (campo) {
        "indicaciones" -> indicaciones
        "evitar" -> evitar
        "signos_alarma" -> signosAlarma
        "control" -> control
        else -> emptyList()
    }
}

/** Nombre para mostrar de un rubro (gemelo de NOMBRE_RUBRO_INDICACIONES: solo etiquetas, sin contenido). */
fun nombreRubroIndicaciones(rubro: String): String = when (rubro) {
    "estetica" -> "Estética"
    "dermatologia" -> "Dermatología"
    "capilar" -> "Capilar"
    "podologia" -> "Podología"
    "odontologia" -> "Odontología"
    "fisioterapia" -> "Fisioterapia"
    "psicologia" -> "Psicología"
    "medicina_general" -> "Medicina general"
    "pediatria" -> "Pediatría"
    "ginecologia" -> "Ginecología y obstetricia"
    "nutricion" -> "Nutrición"
    else -> rubro.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

// ─── Limpieza de "una por línea" (gemela de limpiarLineas / lineasDe) ─────────

private val RE_VINETA = Regex("""^\s*[-•*·]+\s*""")
private val RE_ESPACIOS = Regex("""[ \t]+""")

/**
 * Limpia un campo de "una por línea": quita viñetas y líneas vacías, colapsa
 * espacios. null si queda vacío. No corta en el límite: eso lo dice la validación.
 */
fun limpiarLineasIndicacion(v: String?): String? {
    val lineas = v.orEmpty().split(Regex("""\r?\n"""))
        .map { it.replace(RE_VINETA, "").replace(RE_ESPACIOS, " ").trim() }
        .filter { it.isNotEmpty() }
    return if (lineas.isEmpty()) null else lineas.joinToString("\n")
}

/** Las líneas de un campo, para pintarlas como viñetas. */
fun lineasIndicacion(v: String?): List<String> = limpiarLineasIndicacion(v)?.split('\n') ?: emptyList()

// ─── Validación (gemela de validarIndicacion / validarPlantillaIndicaciones) ──

private val RE_FECHA_IND = Regex("""^\d{4}-\d{2}-\d{2}$""")

/** Lo que arma el editor (textos tal cual se escribieron). */
data class BorradorIndicacion(
    val pacienteId: String,
    val titulo: String? = null,
    val fecha: String? = null,
    val indicaciones: String? = null,
    val evitar: String? = null,
    val signosAlarma: String? = null,
    val control: String? = null,
    /** Solo al crear: la cita de la que viene (agenda). La base valida que sea del paciente. */
    val citaId: String? = null,
    /** Solo al crear: la especialidad de la hoja (la de la cita o del paciente). */
    val especialidadId: String? = null,
    val visiblePaciente: Boolean = true,
)

private fun validarContenidoIndicacion(
    titulo: String?, indicaciones: String?, evitar: String?, signosAlarma: String?, control: String?, e: MutableList<String>,
) {
    val t = titulo.orEmpty().trim()
    if (t.isEmpty()) e.add("Ponle un título (ej. «Cuidados después del peeling»).")
    else if (t.length > LimitesIndicaciones.TITULO) e.add("El título es muy largo (máx. ${LimitesIndicaciones.TITULO} caracteres).")
    for ((v, nombre) in listOf(indicaciones to "Las indicaciones", evitar to "Lo que debe evitar", signosAlarma to "Los signos de alarma")) {
        if (limpiarLineasIndicacion(v).orEmpty().length > LimitesIndicaciones.TEXTO) {
            e.add("$nombre: texto muy largo (máx. ${LimitesIndicaciones.TEXTO} caracteres).")
        }
    }
    if (limpiarLineasIndicacion(control).orEmpty().length > LimitesIndicaciones.CONTROL) {
        e.add("El control es muy largo (máx. ${LimitesIndicaciones.CONTROL} caracteres).")
    }
    if (t.isNotEmpty() && limpiarLineasIndicacion(indicaciones) == null && limpiarLineasIndicacion(evitar) == null &&
        limpiarLineasIndicacion(signosAlarma) == null && limpiarLineasIndicacion(control) == null) {
        e.add("Escribe al menos una indicación (o qué evitar, los signos de alarma o el control).")
    }
}

/** Errores para mostrar (vacío = válido). Mismos textos que la web. */
fun validarIndicacion(b: BorradorIndicacion): List<String> {
    val e = mutableListOf<String>()
    if (b.pacienteId.isBlank()) e.add("Falta el paciente.")
    if (!b.fecha.isNullOrEmpty() && !RE_FECHA_IND.matches(b.fecha)) e.add("Fecha inválida.")
    validarContenidoIndicacion(b.titulo, b.indicaciones, b.evitar, b.signosAlarma, b.control, e)
    return e
}

/** Errores de "Guardar como plantilla" (sale de una hoja ya guardada). */
fun validarPlantillaIndicaciones(nombre: String, contenido: ContenidoIndicacionesFuente): List<String> {
    val e = mutableListOf<String>()
    val n = nombre.trim()
    if (n.isEmpty()) e.add("Ponle un nombre a la plantilla.")
    else if (n.length > LimitesIndicaciones.NOMBRE_PLANTILLA) e.add("El nombre es muy largo (máx. ${LimitesIndicaciones.NOMBRE_PLANTILLA} caracteres).")
    validarContenidoIndicacion(contenido.titulo, contenido.indicaciones, contenido.evitar, contenido.signosAlarma, contenido.control, e)
    return e
}

// ─── Editor y cuerpos de los pedidos ─────────────────────────────────────────

/** El contenido del editor (textos planos, una por línea). Gemelo de `Contenido` en la web. */
data class ContenidoEditorIndicaciones(
    val titulo: String = "",
    val indicaciones: String = "",
    val evitar: String = "",
    val signosAlarma: String = "",
    val control: String = "",
) {
    fun deCampo(campo: String): String = when (campo) {
        "indicaciones" -> indicaciones
        "evitar" -> evitar
        "signos_alarma" -> signosAlarma
        "control" -> control
        else -> ""
    }

    fun conCampo(campo: String, v: String): ContenidoEditorIndicaciones = when (campo) {
        "indicaciones" -> copy(indicaciones = v)
        "evitar" -> copy(evitar = v)
        "signos_alarma" -> copy(signosAlarma = v)
        "control" -> copy(control = v)
        else -> this
    }
}

/** Contenido inicial del editor desde una hoja, una plantilla (de la clínica o base) o en blanco. */
fun contenidoEditorDesde(f: ContenidoIndicacionesFuente?): ContenidoEditorIndicaciones =
    if (f == null) ContenidoEditorIndicaciones() else ContenidoEditorIndicaciones(
        titulo = f.titulo,
        indicaciones = f.indicaciones.orEmpty(),
        evitar = f.evitar.orEmpty(),
        signosAlarma = f.signosAlarma.orEmpty(),
        control = f.control.orEmpty(),
    )

/** Editor → borrador para validar y enviar (los vacíos van como null, como la web). */
fun borradorIndicacionDesdeEditor(
    pacienteId: String, fecha: String, c: ContenidoEditorIndicaciones, visiblePaciente: Boolean,
    citaId: String? = null, especialidadId: String? = null,
): BorradorIndicacion {
    fun vacio(v: String) = v.takeIf { it.isNotBlank() }
    return BorradorIndicacion(
        pacienteId = pacienteId, titulo = c.titulo.trim(), fecha = fecha,
        indicaciones = vacio(c.indicaciones), evitar = vacio(c.evitar),
        signosAlarma = vacio(c.signosAlarma), control = vacio(c.control),
        citaId = citaId?.takeIf { it.isNotBlank() }, especialidadId = especialidadId?.takeIf { it.isNotBlank() },
        visiblePaciente = visiblePaciente,
    )
}

/**
 * Cuerpo del POST (sin [id]: con pacienteId, y la cita / especialidad si
 * vienen) o del PATCH (con [id]). YA validado. La fecha va SIEMPRE (el "hoy"
 * de la sede): el respaldo del servidor es Lima.
 */
fun cuerpoIndicacion(b: BorradorIndicacion, id: String? = null): JsonObject = buildJsonObject {
    if (id != null) put("id", id) else {
        put("pacienteId", b.pacienteId)
        // La cita y la especialidad solo al crear (la base valida que la cita sea de este paciente).
        b.citaId?.takeIf { it.isNotBlank() }?.let { put("citaId", it) }
        b.especialidadId?.takeIf { it.isNotBlank() }?.let { put("especialidadId", it) }
    }
    b.fecha?.takeIf { it.isNotBlank() }?.let { put("fecha", it) }
    put("titulo", b.titulo.orEmpty().trim())
    fun texto(k: String, v: String?) { if (v.isNullOrBlank()) put(k, JsonNull) else put(k, v) }
    texto("indicaciones", b.indicaciones)
    texto("evitar", b.evitar)
    texto("signosAlarma", b.signosAlarma)
    texto("control", b.control)
    put("visiblePaciente", b.visiblePaciente)
}

/** Cuerpo de "Guardar como plantilla" (sin datos del paciente). */
fun cuerpoPlantillaIndicaciones(nombre: String, i: ContenidoIndicacionesFuente, especialidadId: String?): JsonObject = buildJsonObject {
    put("nombre", nombre.trim())
    put("titulo", i.titulo)
    put("especialidadId", especialidadId?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull)
    put("indicaciones", i.indicaciones?.let { JsonPrimitive(it) } ?: JsonNull)
    put("evitar", i.evitar?.let { JsonPrimitive(it) } ?: JsonNull)
    put("signosAlarma", i.signosAlarma?.let { JsonPrimitive(it) } ?: JsonNull)
    put("control", i.control?.let { JsonPrimitive(it) } ?: JsonNull)
}

// ─── JSON del API → modelo (tolerante) ───────────────────────────────────────

private fun JsonObject.txtInd(k: String): String? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() && it != "null" }

/** Fila del API → modelo. Sin id se descarta. */
fun indicacionDeJson(o: JsonObject): IndicacionPaciente? {
    val id = o.txtInd("id") ?: return null
    return IndicacionPaciente(
        id = id,
        pacienteId = o.txtInd("paciente_id").orEmpty(),
        fecha = o.txtInd("fecha").orEmpty().take(10),
        titulo = o.txtInd("titulo")?.trim() ?: "Indicaciones",
        indicaciones = o.txtInd("indicaciones"),
        evitar = o.txtInd("evitar"),
        signosAlarma = o.txtInd("signos_alarma"),
        control = o.txtInd("control"),
        tratamientoId = o.txtInd("tratamiento_id"),
        citaId = o.txtInd("cita_id"),
        terapeutaId = o.txtInd("terapeuta_id"),
        especialidadId = o.txtInd("especialidad_id"),
        visiblePaciente = (o["visible_paciente"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content != "false",
        createdAt = o.txtInd("created_at"),
    )
}

fun plantillaIndicacionesDeJson(o: JsonObject): PlantillaIndicaciones? {
    val id = o.txtInd("id") ?: return null
    val nombre = o.txtInd("nombre")?.trim() ?: return null
    return PlantillaIndicaciones(
        id = id, nombre = nombre,
        titulo = o.txtInd("titulo")?.trim() ?: nombre,
        indicaciones = o.txtInd("indicaciones"),
        evitar = o.txtInd("evitar"),
        signosAlarma = o.txtInd("signos_alarma"),
        control = o.txtInd("control"),
        especialidadId = o.txtInd("especialidad_id"),
    )
}

private fun listaTextos(v: JsonElement?): List<String> = (v as? JsonArray).orEmpty().mapNotNull { x ->
    (x as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
}

/** GET /catalogo → modelo. null = no es el JSON del catálogo. */
fun catalogoIndicacionesDeJson(o: JsonObject?): CatalogoIndicaciones? {
    if (o == null || o["plantillas"] !is JsonArray) return null
    val plantillas = (o["plantillas"] as JsonArray).mapNotNull { el ->
        val p = el as? JsonObject ?: return@mapNotNull null
        val clave = p.txtInd("clave") ?: return@mapNotNull null
        val titulo = p.txtInd("titulo")?.trim() ?: return@mapNotNull null
        PlantillaBaseIndicaciones(
            clave = clave, nombre = p.txtInd("nombre")?.trim() ?: titulo, titulo = titulo,
            rubros = listaTextos(p["rubros"]),
            indicaciones = p.txtInd("indicaciones"), evitar = p.txtInd("evitar"),
            signosAlarma = p.txtInd("signos_alarma"), control = p.txtInd("control"),
        )
    }
    val chipsObj = o["chips"] as? JsonObject
    return CatalogoIndicaciones(
        rubro = o.txtInd("rubro"),
        rubros = listaTextos(o["rubros"]),
        plantillas = plantillas,
        chips = CAMPOS_INDICACION.associateWith { listaTextos(chipsObj?.get(it)) },
        sugerida = o.txtInd("sugerida"),
    )
}

/**
 * Contrato: cada ítem es `{ texto, usos }` (más usado primero). Un texto suelto
 * (servidor anterior) cuenta como 1 uso: autocompletado, nunca chip.
 */
private fun aprendidos(v: JsonElement?): List<AprendidoPlan> = (v as? JsonArray).orEmpty().mapNotNull { x ->
    when (x) {
        is JsonPrimitive -> x.takeIf { it !is JsonNull && it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { AprendidoPlan(it, 1) }
        is JsonObject -> {
            val t = (x["texto"] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content?.trim()
            val usos = (x["usos"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull()?.toInt()
            t?.takeIf { it.isNotEmpty() }?.let { AprendidoPlan(it, usos) }
        }
        else -> null
    }
}

fun sugerenciasIndicacionesDeJson(o: JsonObject?): SugerenciasIndicaciones = if (o == null) SugerenciasIndicaciones() else
    SugerenciasIndicaciones(
        indicaciones = aprendidos(o["indicaciones"]),
        evitar = aprendidos(o["evitar"]),
        signosAlarma = aprendidos(o["signos_alarma"]),
        control = aprendidos(o["control"]),
    )

/**
 * Chips (base del catálogo ∪ aprendidos con 2+ usos) y autocompletado (todo) de
 * un campo: la MISMA mezcla que el plan alimentario (`frasesConAprendidos` de la
 * web ↔ [frasesCampoPlan]), siempre una por línea.
 */
fun frasesCampoIndicacion(campo: String, catalogo: CatalogoIndicaciones?, sugerencias: SugerenciasIndicaciones?): FrasesCampoPlan =
    frasesCampoPlan(catalogo?.chips?.get(campo).orEmpty(), sugerencias?.deCampo(campo), porLinea = true)

/** Más reciente primero (fecha y, a igual fecha, la última creada). */
fun ordenarIndicaciones(items: List<IndicacionPaciente>): List<IndicacionPaciente> =
    items.sortedWith(compareByDescending<IndicacionPaciente> { it.fecha }.thenByDescending { it.createdAt.orEmpty() })

// ─── Búsqueda del selector de plantillas ─────────────────────────────────────

private val TILDES_IND = mapOf(
    'á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'ñ' to 'n',
    'à' to 'a', 'è' to 'e', 'ì' to 'i', 'ò' to 'o', 'ù' to 'u',
)

/** Gemelo de `plegarTexto`: sin tildes, en minúscula, espacios colapsados. */
fun plegarTextoIndicacion(s: String?): String =
    s.orEmpty().lowercase().map { TILDES_IND[it] ?: it }.joinToString("").replace(Regex("""\s+"""), " ").trim()

/** ¿Alguno de los textos contiene la búsqueda (plegada)? Búsqueda vacía = sí. */
fun coincideBusquedaIndicacion(q: String, vararg textos: String?): Boolean {
    val p = plegarTextoIndicacion(q)
    return p.isEmpty() || textos.any { plegarTextoIndicacion(it).contains(p) }
}

// ─── Presentación ────────────────────────────────────────────────────────────

/** "2026-10-14" → "14/10/2026" (determinista). */
fun fechaIndicacion(iso: String): String {
    val m = Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(iso) ?: return iso
    return "${m.groupValues[3]}/${m.groupValues[2]}/${m.groupValues[1]}"
}

data class SeccionIndicacion(val clave: String, val titulo: String, val icono: String, val items: List<String>)

/** Secciones de la hoja, en orden, con su título para el paciente. Solo las que tienen algo. */
fun seccionesIndicacion(i: ContenidoIndicacionesFuente): List<SeccionIndicacion> = listOf(
    SeccionIndicacion("indicaciones", "Qué hacer en casa", "✅", lineasIndicacion(i.indicaciones)),
    SeccionIndicacion("evitar", "Evite", "🚫", lineasIndicacion(i.evitar)),
    SeccionIndicacion("signos_alarma", "Acuda de inmediato si…", "🚨", lineasIndicacion(i.signosAlarma)),
).filter { it.items.isNotEmpty() }

/**
 * Texto plano para WhatsApp (*negrita*). Salida IDÉNTICA a
 * `textoIndicacionesWhatsApp` de la web. Se abre con `enlaceWhatsAppSede` →
 * `urlWhatsApp` (api.whatsapp.com/send): NUNCA wa.me?text=, que rompe los emojis.
 */
fun textoIndicacionesWhatsApp(
    fecha: String,
    i: ContenidoIndicacionesFuente,
    clinica: String? = null,
    paciente: String? = null,
    profesional: String? = null,
): String {
    val lineas = mutableListOf<String>()
    val nombre = paciente.orEmpty().trim().split(Regex("""\s+""")).first()
    val cli = clinica?.trim().orEmpty()
    lineas.add("📝 *${i.titulo.trim()}*${if (cli.isNotEmpty()) " — $cli" else ""}")
    lineas.add("${if (nombre.isNotEmpty()) "Hola $nombre, estas" else "Estas"} son sus indicaciones del ${fechaIndicacion(fecha)}.")
    for (s in seccionesIndicacion(i)) {
        lineas.add("")
        lineas.add("${s.icono} *${s.titulo}*")
        lineas.addAll(s.items.map { "• $it" })
    }
    limpiarLineasIndicacion(i.control)?.let { lineas += listOf("", "📅 *Control:* ${it.replace("\n", " · ")}") }
    profesional?.trim()?.takeIf { it.isNotEmpty() }?.let { lineas += listOf("", "— $it") }
    return lineas.joinToString("\n")
}

private fun escInd(s: String?): String = s.orEmpty()
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

/** El color de la clínica para la hoja: solo `#rrggbb`; si no, el navy de Sania. */
fun colorHojaIndicaciones(color: String?): String =
    color?.trim()?.takeIf { Regex("^#[0-9a-fA-F]{6}$").matches(it) } ?: "#2c3e7a"

/**
 * La hoja A4 de 📝 Indicaciones para casa (gemela de HojaIndicaciones.tsx),
 * armada en la app (la página web pide la sesión del navegador; el visor nativo
 * imprime / guarda en PDF este HTML). Todo lo que viene de la base se escapa;
 * el logo solo si es https y el color solo si es #rrggbb.
 */
fun htmlIndicaciones(
    fecha: String,
    i: ContenidoIndicacionesFuente,
    clinicaNombre: String?,
    clinicaDireccion: String?,
    logoUrl: String?,
    colorPrincipal: String?,
    pacienteNombre: String?,
    pacienteDocumento: String?,
    profesional: String?,
): String {
    val color = colorHojaIndicaciones(colorPrincipal)
    val secciones = seccionesIndicacion(i)
    val normales = secciones.filter { it.clave != "signos_alarma" }
    val alarma = secciones.firstOrNull { it.clave == "signos_alarma" }
    val control = lineasIndicacion(i.control)
    val logo = logoUrl?.trim()?.takeIf { it.startsWith("https://") }
        ?.let { "<img src=\"${escInd(it)}\" alt=\"\" class=\"logo\">" }.orEmpty()
    fun lista(items: List<String>) = "<ul>" + items.joinToString("") { "<li>${escInd(it)}</li>" } + "</ul>"
    val cuerpo = buildString {
        normales.forEach { s -> append("<section><h2>${escInd(s.titulo)}</h2>${lista(s.items)}</section>") }
        alarma?.let { s -> append("<section class=\"alarma\"><h2>⚠ Acuda de inmediato o llámenos si…</h2>${lista(s.items)}</section>") }
        if (control.isNotEmpty()) append("<div class=\"control\"><b>Control:</b> ${escInd(control.joinToString(" · "))}</div>")
    }
    return """<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Indicaciones para casa — ${escInd(pacienteNombre)}</title>
<style>
@page { size: A4 portrait; margin: 1.5cm; }
* { box-sizing: border-box; }
body { font-family: Lato, 'Segoe UI', Arial, sans-serif; color: #111827; margin: 0 auto; max-width: 800px; padding: 16px; font-size: 10.5pt; line-height: 1.35; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
.cab { display: flex; align-items: center; gap: 12px; padding-bottom: 10px; border-bottom: 2px solid $color; }
.logo { width: 64px; height: 64px; object-fit: contain; }
.cli { flex: 1; min-width: 0; } .cli .n { font-weight: bold; font-size: 13pt; text-transform: uppercase; word-break: break-word; }
.s { color: #4b5563; font-size: 8.5pt; }
.doc { text-align: right; } .doc .n { font-weight: bold; font-size: 11pt; color: $color; }
.pac { padding: 8px 0; border-bottom: 1px solid #e5e7eb; font-size: 9.5pt; }
.pac span { margin-right: 24px; }
h1 { margin: 16px 0 0; font-size: 14pt; color: $color; word-break: break-word; }
section { margin-top: 14px; break-inside: avoid; }
h2 { margin: 0 0 4px; font-size: 9.5pt; text-transform: uppercase; letter-spacing: .03em; color: $color; }
ul { margin: 0; padding-left: 20px; } li { margin: 3px 0; word-break: break-word; }
.alarma { margin-top: 18px; border: 2px solid #dc2626; background: #fef2f2; border-radius: 8px; padding: 10px; }
.alarma h2 { color: #b91c1c; font-size: 10pt; } .alarma li { color: #7f1d1d; font-weight: bold; }
.control { margin-top: 14px; border: 1px solid #d1d5db; border-radius: 8px; padding: 8px 10px; break-inside: avoid; }
.firma { margin-top: 40px; display: flex; justify-content: flex-end; }
.firma div { width: 60mm; text-align: center; font-size: 8.5pt; }
.linea { border-top: 1px solid #1f2937; margin-top: 14mm; padding-top: 2px; }
.pie { margin-top: 24px; padding-top: 6px; border-top: 1px solid #e5e7eb; font-size: 7.5pt; color: #9ca3af; display: flex; justify-content: space-between; gap: 8px; }
</style></head><body>
<div class="cab">$logo<div class="cli"><div class="n">${escInd(clinicaNombre)}</div>${clinicaDireccion?.takeIf { it.isNotBlank() }?.let { "<div class=\"s\">${escInd(it)}</div>" }.orEmpty()}</div>
<div class="doc"><div class="n">INDICACIONES PARA CASA</div><div class="s">${escInd(fechaIndicacion(fecha))}</div></div></div>
<div class="pac"><span><b>Paciente:</b> ${escInd(pacienteNombre?.takeIf { it.isNotBlank() } ?: "—")}</span>${pacienteDocumento?.takeIf { it.isNotBlank() }?.let { "<span><b>Documento:</b> ${escInd(it)}</span>" }.orEmpty()}</div>
<h1>${escInd(i.titulo)}</h1>
$cuerpo
<div class="firma"><div><div class="linea">Firma y sello del profesional</div>${profesional?.takeIf { it.isNotBlank() }?.let { "<b>${escInd(it)}</b>" }.orEmpty()}</div></div>
<div class="pie"><span>Indicaciones personales: ante cualquier duda, consulte a su profesional.</span><span>Emitido con Sania</span></div>
</body></html>"""
}

// ─── Agenda: la regla del aviso "📝 Dejar indicaciones" ──────────────────────

/**
 * ¿Vale la pena PREGUNTAR al catálogo si el servicio sugiere una plantilla?
 * Gemelo de `ofrecerIndicaciones` de /citas (la regla MENOS ruidosa):
 *  · hay paciente y permiso de pacientes o sesiones;
 *  · la cita quedó completada EN el servidor (sin señal quedó en la cola: el
 *    catálogo y la ficha necesitan red);
 *  · no es de nutrición (ya se ofrecen medidas → plan), ni de las que ofrecen
 *    receta / indicaciones médicas, ni de la consulta guiada;
 *  · en ese cierre no salió OTRA entrega (receta, ejercicios, medidas, el plan
 *    de tratamiento). "📅 Agendar siguiente" NO cuenta: convive.
 * La última palabra la tiene [ofrecerIndicacionesTrasCompletar]: solo si el
 * servicio sugiere una plantilla base de su especialidad.
 */
fun candidataIndicacionesTrasCompletar(
    pacienteId: String?,
    puedePacientes: Boolean,
    puedeSesiones: Boolean,
    encolada: Boolean,
    esNutricion: Boolean,
    ofreceReceta: Boolean,
    esMedicaGuiada: Boolean,
    otraEntrega: Boolean,
): Boolean = !pacienteId.isNullOrBlank() && (puedePacientes || puedeSesiones) && !encolada &&
    !esNutricion && !ofreceReceta && !esMedicaGuiada && !otraEntrega

/** Se ofrece solo si era candidata Y el catálogo dio una plantilla sugerida para el servicio. */
fun ofrecerIndicacionesTrasCompletar(candidata: Boolean, sugerida: PlantillaBaseIndicaciones?): Boolean =
    candidata && sugerida != null

/** Cuánto queda a la vista el aviso si nadie lo toca (la web: 12 s). */
const val VIDA_OFERTA_INDICACIONES_MS = 15_000L

/**
 * Cómo se abre la ficha desde la agenda tras "📝 Dejar indicaciones": en la
 * pestaña 📝, con la cita vinculada y la plantilla sugerida precargada.
 */
data class AperturaIndicaciones(
    val citaId: String?,
    /** Clave de la plantilla base que sugirió el servicio (catálogo). */
    val plantillaClave: String?,
    val especialidadId: String?,
    val servicio: String?,
)

/**
 * Estado del flujo dentro de la ficha. "Abrir de una" vale UNA vez (al cambiar
 * de pestaña o al abrirse se apaga). La cita queda vinculada a la PRIMERA hoja
 * que se guarde: después de guardar se olvida (las hojas siguientes ya no son
 * de esa cita), igual que la web.
 */
data class FlujoIndicacionesFicha(
    val citaId: String? = null,
    val abrirNuevo: Boolean = false,
    val plantillaClave: String? = null,
    val especialidadId: String? = null,
    val servicio: String? = null,
) {
    fun alCambiarTab(tab: String): FlujoIndicacionesFicha = copy(abrirNuevo = abrirNuevo && tab == "indicaciones")
    fun consumido(): FlujoIndicacionesFicha = copy(abrirNuevo = false)
    fun trasGuardar(): FlujoIndicacionesFicha = copy(citaId = null, abrirNuevo = false, plantillaClave = null, servicio = null)

    companion object {
        fun desde(a: AperturaIndicaciones?): FlujoIndicacionesFicha = if (a == null) FlujoIndicacionesFicha() else FlujoIndicacionesFicha(
            citaId = a.citaId?.takeIf { it.isNotBlank() },
            abrirNuevo = true,
            plantillaClave = a.plantillaClave,
            especialidadId = a.especialidadId,
            servicio = a.servicio,
        )
    }
}

/** Mensaje cuando el servidor todavía no tiene el endpoint (backend viejo → 404 sin JSON). */
const val INDICACIONES_NO_DISPONIBLE =
    "Las indicaciones para casa aún no están disponibles en el servidor. Intenta más tarde."
