package pe.saniape.app.data.staff

// EL «⋯» DE LA CABECERA DE LA FICHA DEL PACIENTE (pedido del dueño, 24–25/10/2026).
// Antes: ✏ suelto + ⋯ que abría una columna dentro de la ficha (empujaba todo hacia
// abajo y no se cerraba al tocar afuera), con «Dar de baja» de un toque, sin
// confirmar. Ahora un solo ⋯ (DropdownMenu): Paciente (✏ Editar, ↻ Reactivar) ·
// Portal (invitar por WhatsApp, copiar enlace) · «Dar de baja» en rojo al final,
// confirmado. Mismo criterio que la cabecera web (`lib/acciones-ficha-paciente.ts`).
// Solo reparte: quién lo ve (permiso 'datos_personales', plan con portal) lo
// decide la ficha.

enum class AccionFicha { Editar, Reactivar, InvitarWhatsApp, CopiarPortal, DarDeBaja }

val GRUPOS_MENU_FICHA: List<GrupoMenu<AccionFicha>> = listOf(
    GrupoMenu("Paciente", listOf(AccionFicha.Editar, AccionFicha.Reactivar)),
    GrupoMenu("Portal", listOf(AccionFicha.InvitarWhatsApp, AccionFicha.CopiarPortal)),
    GrupoMenu(GRUPO_PELIGRO, listOf(AccionFicha.DarDeBaja)),
)

/**
 * El menú de la cabecera. [inactivo] = ficha dada de baja (se ofrece Reactivar en
 * vez de Dar de baja); [puedeInvitar] = plan con portal y el paciente tiene correo
 * o teléfono; [conTelefono] = además se puede invitar por WhatsApp.
 */
fun accionesMenuFicha(inactivo: Boolean, puedeInvitar: Boolean, conTelefono: Boolean): PlanAcciones<AccionFicha> {
    val disponibles = buildList {
        add(AccionFicha.Editar)
        if (inactivo) add(AccionFicha.Reactivar)
        if (puedeInvitar) {
            if (conTelefono) add(AccionFicha.InvitarWhatsApp)
            add(AccionFicha.CopiarPortal)
        }
        if (!inactivo) add(AccionFicha.DarDeBaja)
    }
    return repartirAcciones(emptyList(), GRUPOS_MENU_FICHA, disponibles, rojas = listOf(AccionFicha.DarDeBaja))
}
