package pe.saniape.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Una cita del paciente (subconjunto de la tabla `citas` de la web). */
@Serializable
data class Cita(
    val id: String,
    val fecha: String,
    val hora: String,
    val estado: String,
    val tipo: String? = null,
    val notas: String? = null,
    @SerialName("terapeuta_id") val terapeutaId: String? = null,
)

/** Datos básicos del paciente autenticado. */
@Serializable
data class PacienteCuenta(
    val id: String,
    val email: String? = null,
    val nombre: String? = null,
)