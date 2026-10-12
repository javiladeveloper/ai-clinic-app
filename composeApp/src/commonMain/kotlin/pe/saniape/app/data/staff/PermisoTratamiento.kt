package pe.saniape.app.data.staff

/**
 * Quién puede llamar a `/api/staff/tratamiento/accion` (gemelo de la validación del
 * servidor; la app no muestra acciones que el servidor rechazaría con 403):
 *  - `corregir`: solo Admin.
 *  - `estado` = Eliminado: permiso `pacientes` o Admin.
 *  - `crear` / `editar` / `ampliar` / `no_volvio` / `estado` (resto): permiso `sesiones`.
 * Admin siempre puede. [permisos] son las claves que el rol tiene en `ctx.puede(...)`.
 */
fun puedeAccionTratamiento(accion: String, estado: String?, permisos: Set<String>, esAdmin: Boolean): Boolean {
    if (esAdmin) return true
    return when (accion) {
        "corregir" -> false
        "estado" -> if (estado == "Eliminado") "pacientes" in permisos else "sesiones" in permisos
        else -> "sesiones" in permisos
    }
}

/** Atajo con el contexto del staff. */
fun ContextoStaff.puedeAccionTratamiento(accion: String, estado: String? = null): Boolean =
    puedeAccionTratamiento(
        accion, estado,
        permisos = listOf("pacientes", "sesiones").filter { puede(it) }.toSet(),
        esAdmin = esAdmin,
    )

const val MSJ_SIN_PERMISO_TRATAMIENTO = "No tienes permiso para gestionar tratamientos"

/**
 * Texto claro cuando el servidor responde 403 / SIN_PERMISO a una acción de tratamiento;
 * null = no aplica (se usa el mensaje del servidor tal cual).
 */
fun mensajeSinPermisoTratamiento(endpoint: String, status: Int, codigo: String?): String? =
    if (endpoint.endsWith("/api/staff/tratamiento/accion") && (status == 403 || codigo == "SIN_PERMISO"))
        MSJ_SIN_PERMISO_TRATAMIENTO else null
