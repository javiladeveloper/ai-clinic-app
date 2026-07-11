package pe.saniape.app.data

/**
 * Datos del portal del paciente. Fuente ÚNICA: los endpoints de paciente en la web
 * (vía [SaludRepo]) que validan identidad (email O DNI + vínculos) y acotan por pacIds
 * explícitos. Ya NO lee directo de Supabase por email (eso confiaba en RLS, que en
 * cuentas multi-rol suma en OR y podía cruzar datos entre clínicas).
 */
object PortalRepo {

    // Slug de la clínica habitual del paciente (la de su cita más reciente con slug).
    // Lo usa la pantalla de reservar para precargar "tu clínica de siempre".
    var clinicaHabitualSlug: String? = null
        private set

    /**
     * Excepción de fallo de carga del portal → la UI la captura y muestra "reintentar /
     * vuelve a iniciar sesión", en vez de un "no tienes datos" falso.
     */
    class PortalError : Exception("No se pudieron cargar tus datos")

    /**
     * Citas del paciente (próximas/pasadas) SOLO por el API web, que valida identidad
     * email O DNI y acota por pacIds explícitos (seguro). Se ELIMINÓ el fallback directo
     * por email + RLS: en cuentas multi-rol la RLS suma (OR) y podía cruzar datos entre
     * clínicas. Si el API falla → lanza PortalError (la UI muestra el error, no un vacío).
     */
    suspend fun misCitas(): Pair<List<CitaPortal>, List<CitaPortal>> {
        val r = SaludRepo.misCitas()
        if (r is ResultadoPortal.Ok) {
            val (prox, pas) = r.datos
            clinicaHabitualSlug = prox.firstNotNullOfOrNull { it.clinicaSlug }
                ?: pas.firstNotNullOfOrNull { it.clinicaSlug }
            return prox to pas
        }
        throw PortalError()
    }

    /**
     * Tratamientos del paciente (progreso + timeline) SOLO por el API web (identidad
     * validada). Sin fallback directo (ver misCitas). Si el API falla → PortalError.
     */
    suspend fun misTratamientos(): List<Tratamiento> {
        val r = SaludRepo.tratamientos()
        if (r is ResultadoPortal.Ok) return r.datos
        throw PortalError()
    }
}