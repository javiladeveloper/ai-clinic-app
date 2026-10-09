package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/**
 * Vista previa de la importación de un calendario de Google — la parte PURA.
 *
 * Gemelo de la web: `components/calendario/VistaPreviaCalendario.tsx` (pantalla),
 * `lib/calendario/revision.ts` (qué es cita, seleccionar todos, uniones de
 * pacientes) y la forma de `Decisiones` de `lib/calendario/plan.ts`. La app NO
 * planifica: pinta lo que devuelve POST /api/staff/calendario/vista-previa y le
 * manda las decisiones de la persona; el servidor recalcula todo al importar.
 */

// ── Lectura del JSON ──────────────────────────────────────────────────────────

private fun JsonObject.vTxt(k: String): String? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
private fun JsonObject.vBool(k: String): Boolean = (this[k] as? JsonPrimitive)?.booleanOrNull == true
private fun JsonObject.vEnt(k: String): Int = (this[k] as? JsonPrimitive)?.doubleOrNull?.toInt() ?: 0
private fun JsonObject.vObjs(k: String): List<JsonObject> = (this[k] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

/** Ajustes de una fuente (ConfigFuente de la web). null = no se dijo nada (el servidor pone el valor por defecto). */
data class ConfigFuenteCal(
    /** clave de color → 'Confirmada' | 'Pendiente' | 'ignorar'. */
    val mapaColores: Map<String, String> = emptyMap(),
    /** 'atendida' | 'omitir'. */
    val pasadas: String? = null,
    val procedimientoId: String? = null,
    /** AAAA-MM-DD. */
    val desde: String? = null,
    /** 'crear' | 'ignorar' (se conserva tal cual). */
    val nuevosEventos: String? = null,
    val recordatoriosPacientes: Boolean? = null,
)

fun configFuenteDe(o: JsonObject?): ConfigFuenteCal {
    if (o == null) return ConfigFuenteCal()
    val mapa = (o["mapaColores"] as? JsonObject)?.mapNotNull { (k, v) ->
        (v as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.let { k to it }
    }?.toMap() ?: emptyMap()
    return ConfigFuenteCal(
        mapaColores = mapa,
        pasadas = o.vTxt("pasadas"),
        procedimientoId = o.vTxt("procedimientoId"),
        desde = o.vTxt("desde"),
        nuevosEventos = o.vTxt("nuevosEventos"),
        recordatoriosPacientes = (o["recordatoriosPacientes"] as? JsonPrimitive)?.booleanOrNull,
    )
}

fun ConfigFuenteCal.aJson(): JsonObject = buildJsonObject {
    if (mapaColores.isNotEmpty()) put("mapaColores", buildJsonObject { mapaColores.forEach { (k, v) -> put(k, v) } })
    pasadas?.let { put("pasadas", it) }
    put("procedimientoId", procedimientoId)
    put("desde", desde)
    nuevosEventos?.let { put("nuevosEventos", it) }
    recordatoriosPacientes?.let { put("recordatoriosPacientes", it) }
}

/** Un evento de la muestra de la vista previa (ItemVista de la web). */
data class EventoVista(
    val id: String,
    val titulo: String,
    val fecha: String,
    val hora: String,
    val duracion: Int,
    val color: String,
    val recurrente: Boolean,
    val todoElDia: Boolean,
    val accion: String,
    val motivo: String?,
    val pasada: Boolean,
    val estado: String?,
    val paciente: String?,
    val pacienteId: String?,
    val nombre: String,
    val telefono: String?,
    val direccion: String?,
    val domicilio: Boolean,
    val sugeridoNoCita: Boolean,
    val cruce: Boolean,
    val sinCupo: Boolean,
)

data class FichaExistente(val id: String, val nombre: String, val inactivo: Boolean)
data class FichaCandidata(val id: String, val nombre: String, val telefono: String?, val inactivo: Boolean)
data class GrupoUnido(val clave: String, val nombre: String, val eventos: Int)

/** Un paciente detectado (PacientePlan de la web). */
data class PacienteDetectado(
    val clave: String,
    val nombre: String,
    val telefono: String?,
    val direccion: String?,
    val eventos: Int,
    val existente: FichaExistente?,
    val candidatas: List<FichaCandidata>,
    val usar: String?,
    val revisar: Boolean,
    val motivoRevisar: String?,
    val unidos: List<GrupoUnido> = emptyList(),
)

data class MostradosVista(val revisar: Int, val futuras: Int, val pasadas: Int)

data class ResumenVista(
    val eventos: Int = 0,
    val crearFuturas: Int = 0,
    val crearPasadas: Int = 0,
    val ignorar: Int = 0,
    val revisar: Int = 0,
    val cruces: Int = 0,
    val pacientesNuevos: Int = 0,
    val pacientesExistentes: Int = 0,
    val sinCupo: Int = 0,
    val mostrados: MostradosVista = MostradosVista(0, 0, 0),
)

data class ColorEventos(val clave: String, val eventos: Int)

data class VistaPreviaCal(
    val hoy: String,
    val resumen: ResumenVista,
    val pacientes: List<PacienteDetectado>,
    val colores: List<ColorEventos>,
    val items: List<EventoVista>,
    val nombreCalendario: String?,
    val avisos: List<String>,
)

fun vistaPreviaDe(o: JsonObject): VistaPreviaCal {
    val r = o["resumen"] as? JsonObject ?: JsonObject(emptyMap())
    val m = r["mostrados"] as? JsonObject ?: JsonObject(emptyMap())
    return VistaPreviaCal(
        hoy = o.vTxt("hoy") ?: "",
        resumen = ResumenVista(
            eventos = r.vEnt("eventos"), crearFuturas = r.vEnt("crearFuturas"), crearPasadas = r.vEnt("crearPasadas"),
            ignorar = r.vEnt("ignorar"), revisar = r.vEnt("revisar"), cruces = r.vEnt("cruces"),
            pacientesNuevos = r.vEnt("pacientesNuevos"), pacientesExistentes = r.vEnt("pacientesExistentes"),
            sinCupo = r.vEnt("sinCupo"),
            mostrados = MostradosVista(m.vEnt("revisar"), m.vEnt("futuras"), m.vEnt("pasadas")),
        ),
        pacientes = o.vObjs("pacientes").mapNotNull { p ->
            val clave = p.vTxt("clave") ?: return@mapNotNull null
            PacienteDetectado(
                clave = clave,
                nombre = p.vTxt("nombre") ?: "",
                telefono = p.vTxt("telefono")?.takeIf { it.isNotBlank() },
                direccion = p.vTxt("direccion")?.takeIf { it.isNotBlank() },
                eventos = p.vEnt("eventos"),
                existente = (p["existente"] as? JsonObject)?.let { e ->
                    e.vTxt("id")?.let { FichaExistente(it, e.vTxt("nombre") ?: "", e.vBool("inactivo")) }
                },
                candidatas = p.vObjs("candidatas").mapNotNull { cnd ->
                    cnd.vTxt("id")?.let { FichaCandidata(it, cnd.vTxt("nombre") ?: "", cnd.vTxt("telefono")?.takeIf { t -> t.isNotBlank() }, cnd.vBool("inactivo")) }
                },
                usar = p.vTxt("usar")?.takeIf { it.isNotBlank() },
                revisar = p.vBool("revisar"),
                motivoRevisar = p.vTxt("motivoRevisar"),
                unidos = p.vObjs("unidos").mapNotNull { u -> u.vTxt("clave")?.let { GrupoUnido(it, u.vTxt("nombre") ?: "", u.vEnt("eventos")) } },
            )
        },
        colores = o.vObjs("colores").mapNotNull { c -> c.vTxt("clave")?.let { ColorEventos(it, c.vEnt("eventos")) } },
        items = o.vObjs("items").mapNotNull { i ->
            val id = i.vTxt("id") ?: return@mapNotNull null
            EventoVista(
                id = id, titulo = i.vTxt("titulo") ?: "", fecha = i.vTxt("fecha") ?: "", hora = i.vTxt("hora") ?: "",
                duracion = i.vEnt("duracion"), color = i.vTxt("color") ?: SIN_COLOR, recurrente = i.vBool("recurrente"),
                todoElDia = i.vBool("todoElDia"), accion = i.vTxt("accion") ?: "", motivo = i.vTxt("motivo")?.takeIf { it.isNotBlank() },
                pasada = i.vBool("pasada"), estado = i.vTxt("estado"), paciente = i.vTxt("paciente"), pacienteId = i.vTxt("pacienteId"),
                nombre = i.vTxt("nombre") ?: "", telefono = i.vTxt("telefono")?.takeIf { it.isNotBlank() },
                direccion = i.vTxt("direccion")?.takeIf { it.isNotBlank() }, domicilio = i.vBool("domicilio"),
                sugeridoNoCita = i.vBool("sugeridoNoCita"), cruce = i.vBool("cruce"), sinCupo = i.vBool("sinCupo"),
            )
        },
        nombreCalendario = o.vTxt("nombreCalendario"),
        avisos = (o["avisos"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.contentOrNull } ?: emptyList(),
    )
}

/** Lo que devuelve POST /api/staff/calendario/importar. */
data class SinCupoImportado(val titulo: String, val fecha: String, val hora: String)
data class ResultadoImportacion(
    val creadas: Int,
    val actualizadas: Int,
    val canceladas: Int,
    val pacientesCreados: Int,
    val porRevisar: Int,
    val sinCupo: List<SinCupoImportado>,
    val errores: List<String>,
    val enCurso: Boolean,
)

fun resultadoImportacionDe(o: JsonObject): ResultadoImportacion = ResultadoImportacion(
    creadas = o.vEnt("creadas"), actualizadas = o.vEnt("actualizadas"), canceladas = o.vEnt("canceladas"),
    pacientesCreados = o.vEnt("pacientesCreados"), porRevisar = o.vEnt("porRevisar"),
    sinCupo = o.vObjs("sinCupo").map { SinCupoImportado(it.vTxt("titulo") ?: "", it.vTxt("fecha") ?: "", it.vTxt("hora") ?: "") },
    errores = (o["errores"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList(),
    enCurso = o.vBool("enCurso"),
)

// ── Decisiones (lo que la persona marca) ─────────────────────────────────────

/**
 * Un texto que se puede corregir: `null` (sin envoltorio) = no se tocó (vale el
 * detectado); `Texto(null)` = se borró a propósito (sin dato).
 */
data class Texto(val v: String?)

/** Decisión sobre un paciente detectado (DecisionPaciente de la web). */
data class DecisionPaciente(
    /** Ficha a usar ('<id>') o 'nuevo'. */
    val usar: String? = null,
    val nombre: String? = null,
    val telefono: Texto? = null,
    val direccion: Texto? = null,
    /** "Es la misma persona que…": clave del grupo al que se une. */
    val unirA: String? = null,
)

data class AsignacionEvento(
    val pacienteId: String? = null,
    val nombre: String? = null,
    /** Nombre de la ficha elegida, solo para mostrarlo (no viaja al servidor). */
    val etiqueta: String? = null,
)

data class DecisionesCal(
    val noCita: Set<String> = emptySet(),
    val siCita: Set<String> = emptySet(),
    val pacientes: Map<String, DecisionPaciente> = emptyMap(),
    val porEvento: Map<String, AsignacionEvento> = emptyMap(),
)

fun DecisionesCal.aJson(): JsonObject = buildJsonObject {
    put("noCita", buildJsonArray { noCita.forEach { add(JsonPrimitive(it)) } })
    put("siCita", buildJsonArray { siCita.forEach { add(JsonPrimitive(it)) } })
    put("pacientes", buildJsonObject {
        pacientes.forEach { (clave, d) ->
            put(clave, buildJsonObject {
                put("usar", d.usar)
                put("nombre", d.nombre)
                d.telefono?.let { put("telefono", it.v) }
                d.direccion?.let { put("direccion", it.v) }
                put("unirA", d.unirA)
            })
        }
    })
    put("porEvento", buildJsonObject {
        porEvento.forEach { (id, a) -> put(id, buildJsonObject { put("pacienteId", a.pacienteId); put("nombre", a.nombre) }) }
    })
}

/** Cuerpo de vista previa / importar de un calendario de Google (igual que la web). */
fun cuerpoGoogle(fuenteId: String, config: ConfigFuenteCal, decisiones: DecisionesCal): JsonObject = buildJsonObject {
    put("fuenteId", fuenteId)
    put("config", config.aJson())
    put("decisiones", decisiones.aJson())
}

/** Junta un cambio parcial a la decisión de un paciente (setPacientes de la web). */
fun DecisionesCal.conPaciente(clave: String, cambio: (DecisionPaciente) -> DecisionPaciente): DecisionesCal =
    copy(pacientes = pacientes + (clave to cambio(pacientes[clave] ?: DecisionPaciente())))

// ── revision.ts ──────────────────────────────────────────────────────────────

fun esCitaMarcada(i: EventoVista, d: DecisionesCal): Boolean {
    if (i.id in d.noCita) return false
    if (i.id in d.siCita || d.porEvento.containsKey(i.id)) return true
    return i.accion == "crear" || i.accion == "actualizar" || i.accion == "sin_cambios" || i.accion == "protegida" || i.accion == "revisar"
}

/** Solo se puede cambiar lo que todavía no es una cita de Sania (las protegidas no). */
fun esEditable(i: EventoVista): Boolean =
    i.accion == "crear" || i.accion == "ignorar" || i.accion == "revisar" || (i.accion == "omitir" && !i.pasada)

private fun pideSiCita(i: EventoVista) = i.accion != "crear" && i.accion != "revisar"

/** Lo mismo que tocar la casilla de un evento. */
fun alternarMarca(i: EventoVista, d: DecisionesCal): DecisionesCal {
    val n = d.noCita.toMutableSet(); val s = d.siCita.toMutableSet()
    if (esCitaMarcada(i, d)) { s.remove(i.id); n.add(i.id) } else { n.remove(i.id); if (pideSiCita(i)) s.add(i.id) }
    return d.copy(noCita = n, siCita = s)
}

/** "Seleccionar todos / ninguno" de la pestaña: solo los editables cambian. */
fun marcarTodos(items: List<EventoVista>, valor: Boolean, d: DecisionesCal): DecisionesCal {
    val n = d.noCita.toMutableSet(); val s = d.siCita.toMutableSet()
    for (i in items) {
        if (!esEditable(i) || esCitaMarcada(i, d) == valor) continue
        if (valor) { n.remove(i.id); if (pideSiCita(i)) s.add(i.id) } else { s.remove(i.id); n.add(i.id) }
    }
    return d.copy(noCita = n, siCita = s)
}

enum class Cabecera { TODOS, NINGUNO, MEZCLA, VACIO }

/** Estado de la casilla "seleccionar todos" (solo cuenta los editables). */
fun estadoCabecera(items: List<EventoVista>, d: DecisionesCal): Cabecera {
    var si = 0; var no = 0
    for (i in items) {
        if (!esEditable(i)) continue
        if (esCitaMarcada(i, d)) si++ else no++
    }
    if (si == 0 && no == 0) return Cabecera.VACIO
    return if (no == 0) Cabecera.TODOS else if (si == 0) Cabecera.NINGUNO else Cabecera.MEZCLA
}

/** A qué grupo van los eventos de [clave] ("es la misma persona"); hasta 3 saltos, sin ciclos. */
fun resolverUnion(clave: String, dec: Map<String, DecisionPaciente>, existe: (String) -> Boolean): String {
    var actual = clave
    val vistos = mutableSetOf(clave)
    var saltos = 0
    while (true) {
        val sig = dec[actual]?.unirA
        if (sig.isNullOrEmpty() || sig == actual || !existe(sig)) return actual
        if (sig in vistos || saltos >= 3) return clave
        vistos.add(sig)
        actual = sig
        saltos++
    }
}

data class UnidoFila(val clave: String, val nombre: String, val eventos: Int, /** Unido en pantalla, aún sin actualizar. */ val local: Boolean)
data class FilaPaciente(val p: PacienteDetectado, val eventos: Int, val unidos: List<UnidoFila>)

/** Pacientes detectados tal como se ven con las uniones marcadas en pantalla (filasPacientes de la web). */
fun filasPacientes(lista: List<PacienteDetectado>, dec: Map<String, DecisionPaciente>): List<FilaPaciente> {
    val claves = lista.map { it.clave }.toSet()
    val destino = lista.associate { it.clave to resolverUnion(it.clave, dec) { k -> k in claves } }
    fun propias(p: PacienteDetectado) = p.eventos - p.unidos.filter { dec[it.clave]?.unirA.isNullOrEmpty() }.sumOf { it.eventos }
    val filas = LinkedHashMap<String, Pair<PacienteDetectado, Int>>()
    val unidos = HashMap<String, MutableList<UnidoFila>>()
    for (p in lista) {
        if (destino[p.clave] != p.clave) continue
        filas[p.clave] = p to propias(p)
        unidos[p.clave] = p.unidos.filter { !dec[it.clave]?.unirA.isNullOrEmpty() }
            .map { UnidoFila(it.clave, it.nombre, it.eventos, local = false) }.toMutableList()
    }
    for (p in lista) {
        val d = destino[p.clave] ?: continue
        if (d == p.clave) continue
        val f = filas[d] ?: continue
        filas[d] = f.first to (f.second + propias(p))
        unidos.getValue(d).add(UnidoFila(p.clave, p.nombre, propias(p), local = true))
    }
    return filas.map { (k, v) -> FilaPaciente(v.first, v.second, unidos[k].orEmpty()) }
}

// ── Contador del pie y pestañas ──────────────────────────────────────────────

/**
 * Lo marcado o desmarcado a mano que el resumen del servidor todavía no cuenta
 * (mismo cálculo que la web: sin esto el pie decía "0 citas" aunque hubieras
 * marcado una).
 */
fun ajusteManual(items: List<EventoVista>, d: DecisionesCal): Int = items.fold(0) { acc, i ->
    val creaba = i.accion == "crear"
    val ahora = i.id !in d.noCita && (creaba || i.id in d.siCita || d.porEvento.containsKey(i.id))
    when {
        creaba && !ahora -> acc - 1
        !creaba && ahora && (i.accion == "ignorar" || i.accion == "omitir" || i.accion == "revisar") -> acc + 1
        else -> acc
    }
}

fun citasAImportar(v: VistaPreviaCal, d: DecisionesCal): Int =
    maxOf(0, v.resumen.crearFuturas + v.resumen.crearPasadas + ajusteManual(v.items, d))

enum class PestanaEventos(val titulo: String) { REVISAR("Revisa estos"), FUTURAS("Próximas"), PASADAS("Pasadas"), TODAS("Todas") }

/** Eventos de una pestaña, SIEMPRE por fecha y hora (el servidor los agrupa por tipo). */
fun eventosDePestana(items: List<EventoVista>, p: PestanaEventos): List<EventoVista> {
    val lista = items.sortedBy { "${it.fecha} ${it.hora}" }
    return when (p) {
        PestanaEventos.TODAS -> lista
        PestanaEventos.PASADAS -> lista.filter { it.pasada }
        PestanaEventos.FUTURAS -> lista.filter { !it.pasada }
        PestanaEventos.REVISAR -> lista.filter {
            it.accion == "revisar" || it.sugeridoNoCita || it.cruce || it.sinCupo || it.accion == "ignorar" || it.accion == "protegida"
        }
    }
}

/** Texto del botón de importar (Google: con 0 citas igual activa la sincronización). */
fun textoBotonImportar(aCrear: Int, esGoogle: Boolean, importando: Boolean): String = when {
    importando -> "Importando… (puede tardar unos minutos)"
    aCrear > 0 -> "Importar $aCrear ${if (aCrear == 1) "cita" else "citas"}"
    esGoogle -> "Activar sincronización (sin citas por ahora)"
    else -> "Nada para importar"
}

fun puedeImportar(aCrear: Int, esGoogle: Boolean, trabajando: Boolean): Boolean = !trabajando && (aCrear > 0 || esGoogle)

/** Etiqueta de la acción del plan (ETIQUETA_ACCION de la web). */
val ETIQUETA_ACCION: Map<String, String> = mapOf(
    "crear" to "Se crea", "actualizar" to "Se actualiza", "cancelar" to "Se cancela", "sin_cambios" to "Ya importada",
    "ignorar" to "No es cita", "bloqueo" to "Bloqueo", "omitir" to "Se omite", "protegida" to "No se toca",
    "revisar" to "Por revisar", "esperando" to "En espera",
)

/** La columna "Qué pasa" de un evento. */
fun quePasa(i: EventoVista, d: DecisionesCal, config: ConfigFuenteCal): String = when {
    !esCitaMarcada(i, d) -> "No se importa"
    d.porEvento.containsKey(i.id) -> "Se crea"
    i.accion == "revisar" -> "Por revisar"
    i.accion == "crear" || i.id in d.siCita ->
        if (i.pasada && config.pasadas != "omitir") "Atendida (historial)" else if (i.estado == "Pendiente") "Sin confirmar" else "Se crea"
    else -> ETIQUETA_ACCION[i.accion] ?: i.accion
}

/** ¿Se ofrece "Elegir/Cambiar paciente" en este evento? */
fun ofreceElegirPaciente(i: EventoVista, d: DecisionesCal): Boolean =
    esCitaMarcada(i, d) && (i.accion == "revisar" || i.accion == "crear")

fun textoElegirPaciente(i: EventoVista, d: DecisionesCal): String =
    if (i.accion == "revisar" && !d.porEvento.containsKey(i.id)) "Elegir paciente" else "Cambiar paciente"

// ── Pacientes detectados ─────────────────────────────────────────────────────

/** Ficha que se usa hoy para el grupo: '<id>', 'nuevo' o '' (falta decidir). */
fun usarActual(p: PacienteDetectado, d: DecisionPaciente?): String =
    d?.usar?.takeIf { it.isNotEmpty() } ?: p.usar ?: (if (p.revisar) "" else "nuevo")

/** Opciones del selector "Ficha" (vacío = sin opciones: es "Nueva"). */
fun opcionesFicha(p: PacienteDetectado): List<Pair<String, String>> {
    val base = buildList {
        p.existente?.let { add(it.id to "Usar su ficha" + if (it.inactivo) " (DADA DE BAJA)" else "") }
        p.candidatas.forEach { c ->
            add(c.id to "Es “${c.nombre}”" + (c.telefono?.let { " · $it" } ?: "") + if (c.inactivo) " (de baja)" else "")
        }
    }
    if (base.isEmpty()) return emptyList()
    return buildList {
        if (p.revisar) add("" to "Elegir…")
        addAll(base)
        add("nuevo" to "Crear ficha nueva")
    }
}

fun telefonoDe(p: PacienteDetectado, d: DecisionPaciente?): String = d?.telefono?.let { it.v ?: "" } ?: (p.telefono ?: "")
fun direccionDe(p: PacienteDetectado, d: DecisionPaciente?): String = d?.direccion?.let { it.v ?: "" } ?: (p.direccion ?: "")

/**
 * Guarda lo corregido en "Pacientes detectados" (los onBlur de la web): el
 * nombre solo si cambia; celular y dirección solo si difieren de lo que se ve
 * (vacío = sin dato).
 */
fun corregirPaciente(p: PacienteDetectado, d: DecisionesCal, nombre: String, telefono: String, direccion: String): DecisionesCal {
    val actual = d.pacientes[p.clave]
    var nuevo = actual ?: DecisionPaciente()
    val n = nombre.replace(Regex("\\s+"), " ").trim()
    if (n.isNotEmpty() && n != (actual?.nombre ?: p.nombre)) nuevo = nuevo.copy(nombre = if (n == p.nombre) null else n)
    val t = telefono.trim()
    if (t != telefonoDe(p, actual)) nuevo = nuevo.copy(telefono = Texto(t.ifEmpty { null }))
    val dir = direccion.replace(Regex("\\s+"), " ").trim()
    if (dir != direccionDe(p, actual)) nuevo = nuevo.copy(direccion = Texto(dir.ifEmpty { null }))
    if (nuevo == (actual ?: DecisionPaciente())) return d
    return d.copy(pacientes = d.pacientes + (p.clave to nuevo))
}

/** Mayúsculas sin tildes ni signos (normalizarNombre de la web). */
fun normalizarNombre(s: String): String {
    val sinTildes = buildString {
        for (ch in s.uppercase()) append(
            when (ch) {
                'Á', 'À', 'Ä', 'Â' -> 'A'; 'É', 'È', 'Ë', 'Ê' -> 'E'; 'Í', 'Ì', 'Ï', 'Î' -> 'I'
                'Ó', 'Ò', 'Ö', 'Ô' -> 'O'; 'Ú', 'Ù', 'Ü', 'Û' -> 'U'; 'Ñ' -> 'N'; 'Ç' -> 'C'
                else -> ch
            }
        )
    }
    return sinTildes.replace(Regex("[^A-Z\\s]"), "").replace(Regex("\\s+"), " ").trim()
}

/** Distancia de edición con tope (distancia() de lib/importar.ts). */
fun distancia(a: String, b: String): Int {
    if (kotlin.math.abs(a.length - b.length) > 3) return 99
    var prev = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val cur = IntArray(b.length + 1)
        cur[0] = i
        for (j in 1..b.length) {
            cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        }
        prev = cur
    }
    return prev[b.length]
}

/** "Es la misma persona que…": los demás pacientes, los más parecidos primero ("Jeny" junto a "Jenny"). */
fun otrosPorParecido(clave: String, nombre: String, todos: List<Pair<String, String>>): List<Pair<String, String>> {
    val yo = normalizarNombre(nombre)
    return todos.filter { it.first != clave }
        .map { Triple(it.first, it.second, distancia(yo, normalizarNombre(it.second))) }
        .sortedWith(compareBy<Triple<String, String, Int>> { it.third }.thenBy { it.second })
        .map { it.first to it.second }
}

// ── Colores y formato ────────────────────────────────────────────────────────

const val SIN_COLOR = "sin_color"

private val COLORES_GOOGLE: Map<String, Pair<String, String>> = mapOf(
    "1" to ("Lavanda" to "#7986cb"), "2" to ("Salvia (verde claro)" to "#33b679"), "3" to ("Uva" to "#8e24aa"),
    "4" to ("Flamenco" to "#e67c73"), "5" to ("Plátano (amarillo)" to "#f6bf26"), "6" to ("Mandarina (naranja)" to "#f4511e"),
    "7" to ("Pavo real (celeste)" to "#039be5"), "8" to ("Grafito (gris)" to "#616161"), "9" to ("Arándano (azul)" to "#3f51b5"),
    "10" to ("Albahaca (verde)" to "#0b8043"), "11" to ("Tomate (rojo)" to "#d50000"),
)

fun nombreColor(clave: String): String = when {
    clave == SIN_COLOR -> "Color del calendario"
    else -> COLORES_GOOGLE[clave]?.first ?: if (clave.startsWith("#")) clave else "Color $clave"
}

fun hexColor(clave: String): String = when {
    clave == SIN_COLOR -> "#9aa3c7"
    Regex("^#[0-9a-fA-F]{6}$").matches(clave) -> clave
    Regex("^#[0-9a-fA-F]{3}$").matches(clave) -> "#" + clave.drop(1).map { "$it$it" }.joinToString("")
    else -> COLORES_GOOGLE[clave]?.second ?: "#9aa3c7"
}

private val DIAS = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")
private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "set", "oct", "nov", "dic")

/** "mié 8 oct 26". */
fun fechaCortaCal(f: String): String {
    val d = runCatching { kotlinx.datetime.LocalDate.parse(f) }.getOrNull() ?: return "—"
    return "${DIAS[d.dayOfWeek.ordinal]} ${d.dayOfMonth} ${MESES[d.monthNumber - 1]} ${(d.year % 100).toString().padStart(2, '0')}"
}

fun duracionTxt(m: Int): String = when {
    m >= 1440 -> "Todo el día"
    m % 60 == 0 -> "${m / 60} h"
    m > 60 -> "${m / 60} h ${m % 60} min"
    else -> "$m min"
}

// ── Textos del resumen ───────────────────────────────────────────────────────

fun avisoPorRevisar(revisar: Int): String =
    "$revisar eventos no se importan hasta que digas de qué paciente son (solo un nombre, o un nombre que ya tienen varias fichas). Están en “Revisa estos”: toca “Elegir paciente”."

fun avisoCupoCruces(sinCupo: Int, cruces: Int): String? {
    if (sinCupo <= 0 && cruces <= 0) return null
    val a = if (sinCupo > 0) "$sinCupo citas próximas caen fuera del horario del profesional o sin cupo" else ""
    val y = if (sinCupo > 0 && cruces > 0) " y " else ""
    val b = if (cruces > 0) "$cruces se cruzan entre sí" else ""
    return "⚠ $a$y$b. Las demás se importan igual."
}

fun textoPie(aCrear: Int, revisar: Int): String =
    "Se importarán $aCrear citas" + (if (revisar > 0) " (y $revisar por revisar quedan fuera)" else "") +
        ". Después, los cambios de tu Google Calendar llegan solos cada 10 minutos."

fun toastImportacion(r: ResultadoImportacion): String =
    if (r.enCurso) "Van ${r.creadas} ${if (r.creadas == 1) "cita" else "citas"}: sigue importando"
    else "Listo: ${r.creadas} ${if (r.creadas == 1) "cita importada" else "citas importadas"}"

/** Cómo tomar un fallo de "Importar". */
enum class FalloImportar {
    /** Sin respuesta a tiempo (corte, 504) u OCUPADO: el servidor puede seguir importando. */
    POSIBLE_EN_CURSO,
    /** Google revocó el permiso: hay que volver a conectar la cuenta. */
    RECONECTAR,
    OTRO,
}

fun falloImportar(codigo: String?, status: Int): FalloImportar = when {
    codigo == "RECONECTAR" -> FalloImportar.RECONECTAR
    codigo == "SIN_RED" || codigo == "OCUPADO" || status == 504 || status == 408 -> FalloImportar.POSIBLE_EN_CURSO
    else -> FalloImportar.OTRO
}

/** Celular escrito a mano: solo dígitos, espacios y + ( ) - (como acepta el servidor). */
fun soloCaracteresCelular(s: String): String = s.filter { it.isDigit() || it in " +()-" }
