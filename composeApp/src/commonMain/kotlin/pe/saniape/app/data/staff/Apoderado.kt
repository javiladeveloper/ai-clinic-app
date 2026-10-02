package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Lo que el formulario del paciente edita del apoderado (columnas `apoderado_*`,
 * `requiere_apoderado` y `apoderado_recibe_avisos` de `pacientes`). Los textos van
 * tal cual se escriben; [Apoderado.payload] los limpia al guardar.
 */
data class DatosApoderado(
    val nombre: String = "",
    val dni: String = "",
    val parentesco: String = "",
    val telefono: String = "",
    /** Adulto que necesita representante legal (el menor lo necesita siempre). */
    val requiere: Boolean = false,
    /** Los recordatorios de WhatsApp van al teléfono del apoderado. */
    val recibeAvisos: Boolean = false,
) {
    /** ¿Hay algo que guardar? (para no tocar la ficha cuando no se llenó nada). */
    val tieneAlgo: Boolean
        get() = requiere || recibeAvisos ||
            listOf(nombre, dni, parentesco, telefono).any { it.isNotBlank() }
}

/**
 * Reglas del apoderado — GEMELAS de la web (aprobado 2026-10-01). La app solo
 * registra y muestra; consentimientos, HC y recordatorios los decide el servidor.
 *
 *  - Menor = edad < 18, desde fecha_nacimiento (o la edad guardada si no hay fecha).
 *  - Necesita apoderado = menor || requiere_apoderado.
 *  - Falta apoderado = lo necesita y no tiene nombre o DNI.
 */
object Apoderado {

    const val MAYORIA_DE_EDAD = 18

    val PARENTESCOS = listOf("Madre", "Padre", "Tutor legal", "Abuelo(a)", "Hermano(a)", "Otro")

    /** Menor de edad (sin fecha ni edad = no se sabe → no se marca como menor). */
    fun esMenor(fechaNacimiento: String?, edad: Int?, hoyIso: String = hoyClinicaIso()): Boolean {
        val e = edadDe(fechaNacimiento, edad, hoyIso) ?: return false
        return e < MAYORIA_DE_EDAD
    }

    fun necesita(esMenor: Boolean, requiereApoderado: Boolean): Boolean = esMenor || requiereApoderado

    fun falta(necesita: Boolean, nombre: String?, dni: String?): Boolean =
        necesita && (nombre.isNullOrBlank() || dni.isNullOrBlank())

    /** Badge junto al nombre: "Menor", "Con apoderado" (adulto representado) o nada. */
    fun badge(esMenor: Boolean, requiereApoderado: Boolean): String? = when {
        esMenor -> "Menor"
        requiereApoderado -> "Con apoderado"
        else -> null
    }

    /** Atajo para lo que viene de la base (lista, agenda, ficha). */
    fun badgeDe(fechaNacimiento: String?, edad: Int?, requiereApoderado: Boolean, hoyIso: String = hoyClinicaIso()): String? =
        badge(esMenor(fechaNacimiento, edad, hoyIso), requiereApoderado)

    const val AVISO_FALTA = "Falta el apoderado (nombre y DNI)"

    /**
     * Columnas del apoderado para el INSERT/UPDATE de `pacientes` (nunca clinica_id).
     * Los textos vacíos van como null: así borrar un campo en el formulario lo borra
     * en la base.
     */
    fun payload(d: DatosApoderado): JsonObject = buildJsonObject {
        fun txt(k: String, v: String) {
            val t = v.trim()
            put(k, if (t.isBlank()) JsonPrimitive(null as String?) else JsonPrimitive(t))
        }
        txt("apoderado_nombre", d.nombre)
        txt("apoderado_dni", d.dni)
        txt("apoderado_parentesco", d.parentesco)
        txt("apoderado_telefono", d.telefono)
        put("requiere_apoderado", d.requiere)
        put("apoderado_recibe_avisos", d.recibeAvisos)
    }
}
