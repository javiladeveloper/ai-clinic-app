package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pe.saniape.app.data.Supabase

// «📋 Filiación ⚠ N» en el ⋯ de la ficha (11/11/2026, paridad con la web): cuántos
// datos le faltan a la hoja de filiación (NTS 139). Gemelo de `faltantesFiliacion`
// de la web (lib/historia-clinica.ts): mismos textos, mismo orden.

/** Lo que mira la regla: la HC (sexo, estado civil…) y los datos de la ficha. */
data class DatosFiliacion(
    val dni: String? = null,
    val fechaNacimiento: String? = null,
    val edad: Int? = null,
    val sexoPaciente: String? = null,
    val direccion: String? = null,
    val telefono: String? = null,
    val ocupacion: String? = null,
    val hc: Map<String, String?> = emptyMap(),
)

fun faltantesFiliacion(d: DatosFiliacion, hoyIso: String): List<String> {
    fun vacio(v: String?) = v.isNullOrBlank()
    val f = mutableListOf<String>()
    if (vacio(d.dni)) f += "Documento de identidad"
    if (vacio(d.fechaNacimiento)) f += "Fecha de nacimiento"
    // El sexo elegido al registrar (pacientes.sexo) también cuenta: es el mismo dato.
    if (vacio(d.hc["sexo"]) && vacio(d.sexoPaciente)) f += "Sexo"
    if (vacio(d.direccion)) f += "Domicilio"
    if (vacio(d.telefono)) f += "Teléfono"
    if (vacio(d.hc["estado_civil"])) f += "Estado civil"
    if (vacio(d.hc["grado_instruccion"])) f += "Grado de instrucción"
    if (vacio(d.ocupacion)) f += "Ocupación"
    if (vacio(d.hc["lugar_nacimiento"])) f += "Lugar de nacimiento"
    // Menor de edad: la NTS (4.2.1) pide padre/madre/tutor con DNI ANTES de atender.
    val edad = edadDe(d.fechaNacimiento, d.edad, hoyIso)
    if (edad != null && edad < 18 && (vacio(d.hc["responsable_nombre"]) || vacio(d.hc["responsable_dni"]))) {
        f += "Padre, madre o tutor (con DNI)"
    }
    return f
}

/** "📋 Filiación" / "📋 Filiación ⚠ 3" (como la cabecera web). */
fun etiquetaFiliacion(faltan: Int): String = if (faltan > 0) "📋 Filiación ⚠ $faltan" else "📋 Filiación (historia clínica)"

private val CAMPOS_HC = listOf("sexo", "estado_civil", "grado_instruccion", "lugar_nacimiento", "responsable_nombre", "responsable_dni")

private fun JsonObject.texto(k: String): String? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() }

/**
 * Los faltantes de la hoja de [paciente] (lectura directa, RLS de la clínica). Si no
 * se pudo leer, null (la ficha muestra «📋 Filiación» sin número: no inventa).
 */
suspend fun cargarFaltantesFiliacion(paciente: PacienteStaff): List<String>? = runCatching {
    val hc = Supabase.client.postgrest["historias_clinicas"]
        .select(Columns.raw(CAMPOS_HC.joinToString(","))) {
            filter { eq("paciente_id", paciente.id) }
            limit(1)
        }.decodeList<JsonObject>().firstOrNull()
    val pac = Supabase.client.postgrest["pacientes"]
        .select(Columns.raw("direccion, sexo")) {
            filter { eq("id", paciente.id) }
            limit(1)
        }.decodeList<JsonObject>().firstOrNull()
    faltantesFiliacion(
        DatosFiliacion(
            dni = paciente.dni, fechaNacimiento = paciente.fechaNacimiento, edad = paciente.edad,
            sexoPaciente = pac?.texto("sexo"), direccion = pac?.texto("direccion"),
            telefono = paciente.telefono, ocupacion = paciente.ocupacion,
            hc = CAMPOS_HC.associateWith { hc?.texto(it) },
        ),
        hoyClinicaIso(),
    )
}.getOrNull()
