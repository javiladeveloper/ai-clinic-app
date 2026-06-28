package pe.saniape.app.data.staff

/**
 * Contexto del staff resuelto por el servidor (/api/staff/contexto). La app NO
 * recalcula permisos/plan: lee estos flags ya resueltos por los helpers de la web
 * (resolverPermisosV2, resolverPlan). Espeja las reglas de negocio sin duplicarlas.
 */
data class ContextoStaff(
    val clinicaId: String,
    val clinicaNombre: String,
    val logoUrl: String?,
    val colorPrincipal: String?,
    val terminologiaProfesional: String,
    val rol: String?,
    val nombre: String?,
    val permisos: Permisos,
    val plan: String?,
    val planEstado: PlanEstado,
    val miTerapeutaId: String?,
    val usaSesiones: Boolean,
    val clinicas: List<ClinicaRef>,
    val tienePortal: Boolean,
) {
    /** Permiso granular (mismo significado que puede() en la web). */
    fun puede(key: String): Boolean = when (key) {
        "pacientes" -> permisos.pacientes
        "citas" -> permisos.citas
        "sesiones" -> permisos.sesiones
        "pagos" -> permisos.pagos
        "finanzas" -> permisos.finanzas
        "comisiones" -> permisos.comisiones
        "servicios" -> permisos.servicios
        "equipo" -> permisos.equipo
        "ajustes" -> permisos.ajustes
        else -> false
    }

    /** Feature del plan (gatea UI: ia, finanzas, comisiones, reservas...). */
    fun can(feature: String): Boolean = when (feature) {
        "finanzas" -> planEstado.features.finanzas
        "comisiones" -> planEstado.features.comisiones
        "reportes" -> planEstado.features.reportes
        "whatsapp" -> planEstado.features.whatsapp
        "ia" -> planEstado.features.ia
        "reservas" -> planEstado.features.reservas
        "derivaciones" -> planEstado.features.derivaciones
        "examenes" -> planEstado.features.examenes
        "fotosEvolutivas" -> planEstado.features.fotosEvolutivas
        else -> false
    }

    /** ¿Es gestor? (ve lista de pacientes con contacto). */
    val esGestor: Boolean get() = permisos.pacientes

    /** ¿Es Admin? (puede editar/borrar pagos; recepción solo registra). */
    val esAdmin: Boolean get() = rol == "Admin"

    /** Vista clínica: sin permiso de pacientes pero con sesiones/citas. */
    val modoClinico: Boolean get() = !permisos.pacientes && (permisos.sesiones || permisos.citas)

    /**
     * ¿El elemento (cita/sesión de terapeutaIdElemento) está en MI scope?
     * Si soy profesional vinculado, solo lo mío; si no, todo.
     */
    fun enScope(terapeutaIdElemento: String?): Boolean =
        miTerapeutaId == null || terapeutaIdElemento == miTerapeutaId

    /** ¿Puedo filtrar por personal? Solo si no estoy vinculado a una agenda. */
    val puedeFiltrarPorPersonal: Boolean get() = miTerapeutaId == null
}

data class Permisos(
    val pacientes: Boolean,
    val citas: Boolean,
    val sesiones: Boolean,
    val pagos: Boolean,
    val finanzas: Boolean,
    val comisiones: Boolean,
    val servicios: Boolean,
    val equipo: Boolean,
    val ajustes: Boolean,
)

data class PlanEstado(
    val efectivo: String,
    val vencido: Boolean,
    val diasRestantes: Int?,
    val features: PlanFeatures,
)

data class PlanFeatures(
    val finanzas: Boolean,
    val comisiones: Boolean,
    val reportes: Boolean,
    val whatsapp: Boolean,
    val ia: Boolean,
    val reservas: Boolean,
    val derivaciones: Boolean,
    val examenes: Boolean,
    val fotosEvolutivas: Boolean,
)

data class ClinicaRef(val id: String, val nombre: String)
