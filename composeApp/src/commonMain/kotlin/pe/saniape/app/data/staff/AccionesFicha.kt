package pe.saniape.app.data.staff

// EL «⋯» DE LA CABECERA DE LA FICHA DEL PACIENTE (pedido del dueño, 24–25/10/2026).
// Antes: ✏ suelto + ⋯ que abría una columna dentro de la ficha (empujaba todo hacia
// abajo y no se cerraba al tocar afuera), con «Dar de baja» de un toque, sin
// confirmar. Ahora un solo ⋯ (DropdownMenu): Paciente (📅 Agendar, ↻ Reactivar,
// ✏ Editar, 📂 Historia clínica, 📋 Filiación) · Portal (invitar por WhatsApp,
// copiar enlace) · «Dar de baja» en rojo al final, confirmado. Mismo orden que la
// cabecera web (`lib/acciones-ficha-paciente.ts`, GRUPOS_MENU_FICHA).
// Solo reparte: quién lo ve (permisos, plan con portal) lo decide la ficha.
//
// 10/11/2026 (Jonathan): «que salgan todas las opciones» de la web. Consentimiento,
// QR de datos y Fusionar todavía no existen en la app (siguen en la web).

enum class AccionFicha { Agendar, Reactivar, Editar, Historia, Filiacion, InvitarWhatsApp, CopiarPortal, DarDeBaja }

val GRUPOS_MENU_FICHA: List<GrupoMenu<AccionFicha>> = listOf(
    GrupoMenu("Paciente", listOf(AccionFicha.Agendar, AccionFicha.Reactivar, AccionFicha.Editar, AccionFicha.Historia, AccionFicha.Filiacion)),
    GrupoMenu("Portal", listOf(AccionFicha.InvitarWhatsApp, AccionFicha.CopiarPortal)),
    GrupoMenu(GRUPO_PELIGRO, listOf(AccionFicha.DarDeBaja)),
)

/**
 * El menú de la cabecera. [inactivo] = ficha dada de baja (se ofrece Reactivar en
 * vez de Dar de baja y no se agenda); [puedeInvitar] = plan con portal y el paciente
 * tiene correo o teléfono; [conTelefono] = además se puede invitar por WhatsApp.
 * [conFiliacion] = paciente médico con historia clínica visible: «📋 Filiación».
 * [conHistoria] = permiso de historia clínica: «📂 Historia clínica».
 * [puedeAgendar] = permiso de agendar: «📅 Agendar cita».
 * [conDatos] = permiso 'datos_personales': Editar, Reactivar, Portal y Dar de baja
 * (sin él, la ficha es clínica: el médico igual ve Historia y Filiación).
 */
fun accionesMenuFicha(
    inactivo: Boolean, puedeInvitar: Boolean, conTelefono: Boolean, conFiliacion: Boolean = false,
    conHistoria: Boolean = false, puedeAgendar: Boolean = false, conDatos: Boolean = true,
): PlanAcciones<AccionFicha> {
    val disponibles = buildList {
        if (puedeAgendar && !inactivo) add(AccionFicha.Agendar)
        if (conDatos && inactivo) add(AccionFicha.Reactivar)
        if (conDatos) add(AccionFicha.Editar)
        if (conHistoria) add(AccionFicha.Historia)
        if (conFiliacion && !inactivo) add(AccionFicha.Filiacion)
        if (conDatos && puedeInvitar) {
            if (conTelefono) add(AccionFicha.InvitarWhatsApp)
            add(AccionFicha.CopiarPortal)
        }
        if (conDatos && !inactivo) add(AccionFicha.DarDeBaja)
    }
    return repartirAcciones(emptyList(), GRUPOS_MENU_FICHA, disponibles, rojas = listOf(AccionFicha.DarDeBaja))
}
