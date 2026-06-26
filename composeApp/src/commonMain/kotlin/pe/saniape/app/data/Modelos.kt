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

/** Un tratamiento del paciente con progreso y timeline (curado para el portal). */
data class Tratamiento(
    val id: String,
    val procedimiento: String,
    val clinica: String?,
    val estado: String,
    val usaSesiones: Boolean,
    val totalSesiones: Int?,
    val sesionesCompletadas: Int,
    val fechaInicio: String?,
    val sesiones: List<SesionMin>,
)

data class SesionMin(
    val numero: Int,
    val fecha: String,
    val estado: String,
)

/** Cita enriquecida con nombre de profesional/clínica para mostrar en el portal. */
data class CitaPortal(
    val id: String,
    val fecha: String,
    val hora: String,
    val estado: String,
    val tipo: String?,
    val profesional: String?,
    val clinica: String?,
)