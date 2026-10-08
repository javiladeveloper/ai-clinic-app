package pe.saniape.app.data.staff

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Equipo y accesos (gemelo de la web /equipo). Lo arma el servidor en
 * `GET /api/staff/equipo`: miembros con sus permisos YA resueltos (rol + ajustes
 * individuales, con `resolverPermisosV2`), roles, personal y sedes. La app no
 * recalcula permisos: solo los muestra y manda los cambios a los endpoints.
 */
@Serializable
data class EquipoDatos(
    /** Admin que puede escribir (el soporte de solo lectura viene en false). */
    val esAdmin: Boolean = false,
    val soloLectura: Boolean = false,
    val yoId: String? = null,
    /** Flag de la web (ROLES_PERSONALIZADOS_HABILITADOS): sin él, los roles son de solo lectura. */
    val rolesPersonalizados: Boolean = false,
    val permisosMeta: List<PermisoMeta> = emptyList(),
    val miembros: List<MiembroEquipo> = emptyList(),
    val roles: List<RolEquipo> = emptyList(),
    val terapeutas: List<PersonalEquipo> = emptyList(),
    val sedes: List<SedeEquipo> = emptyList(),
)

/** Un permiso con el MISMO nombre y explicación que la web (PERMISOS_META). */
@Serializable
data class PermisoMeta(val clave: String, val label: String, val descripcion: String = "")

@Serializable
data class MiembroEquipo(
    val id: String,
    val nombre: String,
    val rol: String,
    val rolId: String? = null,
    val rolNombre: String = rol,
    val esAdmin: Boolean = false,
    val esYo: Boolean = false,
    /** Ajustes individuales tal como están guardados (null = usa los de su rol). */
    val override: JsonObject? = null,
    /** Permisos efectivos (rol + ajustes), resueltos por el servidor. */
    val permisos: Map<String, Boolean> = emptyMap(),
    val atiendePacientes: Boolean = false,
    /** Multisede: null = todas las sedes. */
    val sedesPermitidas: List<String>? = null,
    val terapeuta: PersonalRef? = null,
)

@Serializable
data class PersonalRef(val id: String, val nombre: String)

@Serializable
data class RolEquipo(
    val id: String,
    val nombre: String,
    val descripcion: String? = null,
    val atiendePacientes: Boolean = false,
    val permisos: Map<String, Boolean> = emptyMap(),
    val miembros: Int = 0,
)

/** Registro de personal (terapeutas): para vincular cuentas o invitar a alguien ya registrado. */
@Serializable
data class PersonalEquipo(
    val id: String,
    val nombre: String,
    val email: String? = null,
    val perfilId: String? = null,
    val estado: String = "Activo",
)

@Serializable
data class SedeEquipo(val id: String, val nombre: String, val estado: String = "Activa", val esPrincipal: Boolean = false)

/** ¿Ya se registró alguien con el enlace? `detener` = el servidor ya no lo reconoce (sesión/enlace). */
data class EstadoEnlace(val usado: Boolean, val nombre: String?, val detener: Boolean = false)

/** Enlace de invitación (POST /api/staff/equipo/invitacion). */
@Serializable
data class EnlaceInvitacion(val token: String, val url: String)

/**
 * Reglas de pantalla de Equipo y accesos — gemelas de app/(app)/equipo/page.tsx.
 * Solo deciden qué se MUESTRA; el servidor valida todo otra vez (Admin, misma
 * clínica, último Admin, solo lectura).
 */
object ReglasEquipo {

    /** Misma regex que la web y el servidor (EMAIL_RE). */
    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")

    fun emailValido(email: String): Boolean = EMAIL.matches(email.trim())

    /** Qué puede hacer el que mira sobre este miembro. Sin ser Admin: nada (solo ver). */
    data class Acciones(
        val editar: Boolean,
        val permisos: Boolean,
        val vincular: Boolean,
        val desvincular: Boolean,
        val reenviar: Boolean,
        val revocar: Boolean,
    ) {
        val alguna: Boolean get() = editar || permisos || vincular || desvincular || reenviar || revocar
    }

    fun acciones(m: MiembroEquipo, soyAdmin: Boolean): Acciones {
        if (!soyAdmin) return Acciones(false, false, false, false, false, false)
        // Reenviar/revocar: nunca a un Admin ni a uno mismo (como el menú "⋯" de la web).
        val tieneMenu = !m.esAdmin && !m.esYo
        return Acciones(
            editar = true,
            // Al Admin no se le ajustan permisos: siempre tiene todo.
            permisos = !m.esAdmin,
            vincular = m.terapeuta == null && (m.atiendePacientes || m.esAdmin),
            desvincular = m.terapeuta != null,
            reenviar = tieneMenu,
            revocar = tieneMenu,
        )
    }

    /** "Todas las sedes" o los nombres de las elegidas (como el chip 🏢 de la web). */
    fun resumenSedes(permitidas: List<String>?, sedes: List<SedeEquipo>): String {
        if (permitidas.isNullOrEmpty()) return "Todas las sedes"
        val nombres = permitidas.mapNotNull { id -> sedes.firstOrNull { it.id == id }?.nombre }
        return nombres.joinToString(", ").ifBlank { "Sede desactivada" }
    }

    /** ¿Cambió la elección de sedes? (null = todas; el orden no importa). */
    fun cambiaronSedes(antes: List<String>?, despues: List<String>?): Boolean {
        val a = antes?.takeIf { it.isNotEmpty() }?.sorted()
        val d = despues?.sorted()
        return a != d
    }

    /** El editor de sedes se ofrece solo con multisede y si el miembro no queda como Admin. */
    fun ofreceSedes(multiSede: Boolean, soyAdmin: Boolean, rolElegido: String): Boolean =
        multiSede && soyAdmin && rolElegido != ROL_ADMIN

    /**
     * ¿Al guardar este rol se le quita el Admin? (aviso "debe quedar al menos un
     * administrador"; el servidor es el que lo impide).
     */
    fun dejaDeSerAdmin(m: MiembroEquipo, rolElegido: String?): Boolean = m.esAdmin && rolElegido != null && rolElegido != ROL_ADMIN

    /** El rol con el que queda: el elegido, o el que ya tiene si no se eligió (null). */
    fun rolEfectivo(m: MiembroEquipo, rolElegido: String?): String =
        rolElegido ?: if (m.esAdmin) ROL_ADMIN else (m.rolId ?: m.rolNombre)

    /** Personal que se puede elegir al invitar: activo y sin cuenta (como la web). */
    fun personalInvitable(personal: List<PersonalEquipo>): List<PersonalEquipo> =
        personal.filter { it.perfilId == null && it.estado == "Activo" }

    /** Personal que se puede vincular a una cuenta existente: cualquiera sin cuenta. */
    fun personalVinculable(personal: List<PersonalEquipo>): List<PersonalEquipo> =
        personal.filter { it.perfilId == null }

    /** Rol por defecto del enlace: el primero que atiende pacientes (como InvitarPorEnlace). */
    fun rolPorDefectoEnlace(roles: List<RolEquipo>): String? =
        (roles.firstOrNull { it.atiendePacientes } ?: roles.firstOrNull())?.id

    const val ROL_ADMIN = "Admin"
}
