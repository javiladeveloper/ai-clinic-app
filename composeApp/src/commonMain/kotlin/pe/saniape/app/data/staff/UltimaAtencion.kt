package pe.saniape.app.data.staff

import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────────────────────
// «🗒 LA ÚLTIMA VEZ» — lo de la atención ANTERIOR del paciente en esta
// especialidad (auditoría de flujos médicos 24/10, punto 1). GEMELO de
// `lib/ultima-atencion.ts` (web): QUÉ atención es «la anterior» lo decide el
// servidor (GET /api/staff/atencion/consulta → `laUltimaVez`, y
// GET /api/staff/paciente/resumen-clinico → `laUltimaVez`). Aquí: la forma del
// JSON y los atajos puros, con las MISMAS reglas que la web:
//   · «↻ Mismos diagnósticos» → [repetirDiagnosticos] (como «Repetido», sin
//     duplicar ni pasar el tope);
//   · «↻ Repetir receta» → itemsParaRepetir (ui/clinica/atencion/UltimaVez.kt):
//     filas editables; no emite nada.
// Médicos: `anterior`. Psicología: `contexto` (el «📋 Contexto del caso» de
// ▶ Atender) y `evolucion` (la última evolución).
// ─────────────────────────────────────────────────────────────────────────────

/** Un medicamento de la receta anterior (RecetaItem de la web). */
@Serializable
data class ItemRecetaPrevia(
    val dci: String = "",
    val marca: String? = null,
    val concentracion: String = "",
    val forma: String = "",
    val via: String = "",
    val dosis: String = "",
    val frecuencia: String = "",
    val duracion: String = "",
    val cantidad: Double = 0.0,
    val unidad: String? = null,
    val indicaciones: String? = null,
)

/** El control que quedó agendado en la atención anterior. */
@Serializable
data class ControlPrevio(val fecha: String = "", val hora: String? = null, val estado: String? = null)

/** La atención anterior (médicos y rubros que no son psicología). */
@Serializable
data class AtencionAnteriorApp(
    val atencionId: String = "",
    val citaId: String? = null,
    val fecha: String = "",
    val profesional: String? = null,
    val diagnosticos: List<DiagnosticoCie> = emptyList(),
    val receta: List<ItemRecetaPrevia> = emptyList(),
    val indicaciones: String? = null,
    val control: ControlPrevio? = null,
)

@Serializable
data class FuenteContextoApp(val titulo: String? = null, val fecha: String? = null)

@Serializable
data class SeccionContextoApp(val clave: String = "", val etiqueta: String = "", val texto: String = "")

/** El contexto del caso tal como lo arma el servidor (lib/contexto-caso.ts). */
@Serializable
data class ContextoCasoApp(val fuente: FuenteContextoApp? = null, val secciones: List<SeccionContextoApp> = emptyList()) {
    /** Al modelo que ya pinta BloqueContextoCaso (▶ Atender, cierre de sesión). */
    fun aContextoCaso(): ContextoCaso = ContextoCaso(
        fuenteTitulo = fuente?.titulo?.takeIf { it.isNotBlank() },
        fuenteFecha = fuente?.fecha?.takeIf { it.isNotBlank() },
        secciones = secciones.filter { it.texto.isNotBlank() }.map { SeccionContextoCaso(it.clave, it.etiqueta, it.texto.trim()) },
    )
}

/** La evolución psicológica por campos (EvolucionSesionPsico de la web). */
@Serializable
data class EvolucionPsicoApp(val evolucion: String = "", val estado: String = "", val tareas: String = "", val plan: String = "") {
    /** (etiqueta, texto) sin los vacíos, para BloqueEvolucionAnterior. */
    fun campos(): List<Pair<String, String>> = listOf(
        "Temas trabajados y evolución" to evolucion,
        "Estado emocional / examen mental" to estado,
        "Tareas e indicaciones" to tareas,
        "Plan para la próxima sesión" to plan,
    ).filter { it.second.isNotBlank() }
}

@Serializable
data class EvolucionAnteriorApp(val fecha: String = "", val evolucion: EvolucionPsicoApp = EvolucionPsicoApp())

/** `laUltimaVez` de la respuesta. null en el JSON = no hay anterior (no aparece el bloque). */
@Serializable
data class LaUltimaVezApp(
    val anterior: AtencionAnteriorApp? = null,
    val contexto: ContextoCasoApp? = null,
    val evolucion: EvolucionAnteriorApp? = null,
    /** Los CIE-10 que ofrece «↻ Mismos diagnósticos» (vacío = sin botón). */
    val diagnosticos: List<DiagnosticoCie> = emptyList(),
) {
    /** ¿Hay algo que mostrar? */
    val tieneDatos: Boolean
        get() = anterior != null || contexto?.secciones?.any { it.texto.isNotBlank() } == true ||
            evolucion?.evolucion?.campos()?.isNotEmpty() == true
}

// ── Reglas puras (gemelas de lib/ultima-atencion.ts y lib/cie10.ts) ──────────

/** Tope de diagnósticos por atención (MAX_DIAGNOSTICOS de la web). */
const val MAX_DIAGNOSTICOS_ATENCION = 8

private val SIN_TILDE = mapOf(
    'á' to 'a', 'à' to 'a', 'ä' to 'a', 'â' to 'a', 'é' to 'e', 'è' to 'e', 'ë' to 'e', 'ê' to 'e',
    'í' to 'i', 'ì' to 'i', 'ï' to 'i', 'î' to 'i', 'ó' to 'o', 'ò' to 'o', 'ö' to 'o', 'ô' to 'o',
    'ú' to 'u', 'ù' to 'u', 'ü' to 'u', 'û' to 'u', 'ñ' to 'n',
)

/** normalizarTexto de la web: minúsculas, sin tildes, espacios simples. */
internal fun normalizarTextoDx(s: String): String =
    s.lowercase().map { SIN_TILDE[it] ?: it }.joinToString("").replace(Regex("\\s+"), " ").trim()

/** codigoPlano de la web: "j06.9" → "J069". */
internal fun codigoPlanoDx(s: String): String = s.uppercase().replace(Regex("[^A-Z0-9]"), "")

private fun claveDx(d: DiagnosticoCie): String =
    d.codigo?.trim()?.takeIf { it.isNotEmpty() }?.let { "c:${codigoPlanoDx(it)}" } ?: "t:${normalizarTextoDx(d.descripcion)}"

/** normalizarDiagnosticos de la web: sin vacíos ni repetidos, con tope. */
internal fun normalizarDiagnosticosDx(lista: List<DiagnosticoCie>): List<DiagnosticoCie> {
    val vistos = mutableSetOf<String>()
    val out = mutableListOf<DiagnosticoCie>()
    for (d in lista) {
        val descripcion = d.descripcion.replace(Regex("\\s+"), " ").trim()
        val codigo = d.codigo?.trim()?.uppercase()?.ifEmpty { null }
        if (descripcion.isEmpty() && codigo == null) continue
        val n = DiagnosticoCie(codigo, descripcion.ifEmpty { codigo.orEmpty() }, if (d.tipo == "D" || d.tipo == "R") d.tipo else "P")
        if (!vistos.add(claveDx(n))) continue
        out += n
        if (out.size >= MAX_DIAGNOSTICOS_ATENCION) break
    }
    return out
}

/** ¿Este diagnóstico anterior ya está en los de hoy? */
fun diagnosticoYaEsta(actuales: List<DiagnosticoCie>, d: DiagnosticoCie): Boolean {
    val k = claveDx(d)
    return normalizarDiagnosticosDx(actuales).any { claveDx(it) == k }
}

/**
 * «↻ Mismos diagnósticos»: los anteriores elegidos se agregan a los de hoy como
 * «Repetido» (R). No duplica ni pasa el tope; lo de hoy no se toca. Devuelve la
 * lista nueva y cuántos se agregaron.
 */
fun repetirDiagnosticos(actuales: List<DiagnosticoCie>, anteriores: List<DiagnosticoCie>): Pair<List<DiagnosticoCie>, Int> {
    val lista = normalizarDiagnosticosDx(actuales).toMutableList()
    val vistos = lista.map(::claveDx).toMutableSet()
    var agregados = 0
    for (d in normalizarDiagnosticosDx(anteriores)) {
        if (lista.size >= MAX_DIAGNOSTICOS_ATENCION) break
        if (!vistos.add(claveDx(d))) continue
        lista += DiagnosticoCie(d.codigo, d.descripcion, "R")
        agregados++
    }
    return lista to agregados
}

/** "Sertralina 50 mg · Tableta — 1 tableta cada 24 horas por 30 días" (lineaMedicamento de la web). */
fun lineaMedicamento(it: ItemRecetaPrevia): String {
    val nombre = listOf(it.dci.trim(), it.concentracion.trim()).filter { s -> s.isNotEmpty() }.joinToString(" ")
    val forma = it.forma.trim()
    val dur = it.duracion.trim()
    val pauta = listOf(
        it.dosis.trim(), it.frecuencia.trim(),
        if (dur.isEmpty()) "" else if (dur.first().isDigit()) "por $dur" else dur,
    ).filter { s -> s.isNotEmpty() }.joinToString(" ")
    return nombre + (if (forma.isNotEmpty()) " · $forma" else "") + (if (pauta.isNotEmpty()) " — $pauta" else "")
}

/** "F32.1 Episodio depresivo moderado (Definitivo)". */
fun lineaDiagnostico(d: DiagnosticoCie): String {
    val tipo = when (d.tipo) { "D" -> "Definitivo"; "R" -> "Repetido"; else -> "Presuntivo" }
    return "${d.codigo?.let { "$it " } ?: ""}${d.descripcion} ($tipo)"
}

/** "2026-10-12" → "12/10/2026" (fechaCorta de la web; la última vez puede ser de otro año). */
fun fechaUltimaVez(iso: String): String {
    val m = Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(iso) ?: return iso
    return "${m.groupValues[3]}/${m.groupValues[2]}/${m.groupValues[1]}"
}
