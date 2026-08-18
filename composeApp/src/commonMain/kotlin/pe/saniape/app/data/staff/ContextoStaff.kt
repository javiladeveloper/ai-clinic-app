package pe.saniape.app.data.staff

/**
 * Contexto del staff resuelto por el servidor (/api/staff/contexto). La app NO
 * recalcula permisos/plan: lee estos flags ya resueltos por los helpers de la web
 * (resolverPermisosV2, resolverPlan). Espeja las reglas de negocio sin duplicarlas.
 */
/**
 * Cómo llama esta clínica a sus citas y cuáles usa. Lo resuelve el servidor
 * (configuracion.flujo_preset) y viene ya listo en /api/staff/contexto.
 *
 * El tipo interno de una cita ("Consulta"/"Evaluación"/"Sesión") NO cambia
 * nunca: es la mecánica del flujo y hay miles de citas históricas que dependen
 * de esos valores. Esto es solo cómo se le muestra a la gente — RENOVA CAPILAR
 * llama "Evaluación" a lo que internamente es una Consulta.
 */
data class FlujoClinica(
    val usaConsulta: Boolean = true,
    val usaEvaluacion: Boolean = true,
    val labelConsulta: String = "Consulta",
    val labelEvaluacion: String = "Evaluación",
    val labelSesiones: String = "Sesiones",
    val labelAlta: String = "Alta",
) {
    /** El nombre que esta clínica le da a un tipo de cita. */
    fun nombreTipo(tipo: String?): String = when (tipo) {
        "Consulta" -> labelConsulta
        "Evaluación" -> labelEvaluacion
        // "Sesiones" es el nombre del PASO; una cita suelta va en singular.
        "Sesión" -> if (labelSesiones == "Sesiones") "Sesión" else labelSesiones
        else -> tipo ?: "Cita"
    }

    /** ¿Esta clínica ofrece este tipo de cita al agendar? */
    fun usaTipo(tipo: String): Boolean = when (tipo) {
        "Consulta" -> usaConsulta
        "Evaluación" -> usaEvaluacion
        else -> true
    }
}

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
    val flujo: FlujoClinica = FlujoClinica(),
    val clinicas: List<ClinicaRef>,
    val tienePortal: Boolean,
) {
    /** Permiso granular (mismo significado que puede() en la web). */
    fun puede(key: String): Boolean = when (key) {
        "pacientes" -> permisos.pacientes
        "citas" -> permisos.citas
        "agendar" -> permisos.agendar
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

    /**
     * Scope para listar PACIENTES (lista y buscador). Si tengo permiso de pacientes
     * ([esGestor]) veo TODOS los de la clínica aunque esté vinculado a un terapeuta
     * (mismo criterio que la web: fisio con permiso pacientes = ve todo, como recepción).
     * Solo si NO soy gestor (modo clínico) se acota a mis pacientes ([miTerapeutaId]).
     * Antes se pasaba siempre miTerapeutaId → un fisio-gestor no veía a nadie en el
     * buscador (los pacientes en Consulta/Evaluación aún no tienen tratamiento suyo).
     */
    val scopePacientes: String? get() = if (esGestor) null else miTerapeutaId
}

data class Permisos(
    val pacientes: Boolean,
    val citas: Boolean,
    val agendar: Boolean = false,   // crear citas (recepción/admin); médicos solo ven
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
