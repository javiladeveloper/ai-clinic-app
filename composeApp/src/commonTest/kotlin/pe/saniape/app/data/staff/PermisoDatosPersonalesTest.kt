package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El contexto del staff lee `permisos.datos_personales` (antes se ignoraba y
 * `puede("datos_personales")` era siempre false). La ficha aplica la regla de
 * la web (`accesoClinico = !puede('datos_personales')`).
 */
class PermisoDatosPersonalesTest {

    private fun ctx(permisos: String) = StaffContextoRepo.parsear(
        Json.parseToJsonElement("""{"clinicaId":"c1","rol":"Profesional","permisos":$permisos}""").jsonObject,
    )

    @Test
    fun recepcionVeDatosPersonales() {
        val c = ctx("""{"pacientes":true,"agendar":true,"datos_personales":true}""")
        assertTrue(c.puede("datos_personales"))
        assertTrue(verDatosPersonalesFicha(c))
    }

    @Test
    fun fisiosDeDaluNoVenContacto() {
        // Rol "Fisios" de DALU (roles_clinica, sin overrides): datos_personales=false, pacientes=false.
        val c = ctx("""{"pacientes":false,"sesiones":true,"citas":true,"datos_personales":false}""")
        assertFalse(c.puede("datos_personales"))
        assertFalse(verDatosPersonalesFicha(c))
    }

    @Test
    fun medicoConPacientesPeroSinDatosPersonalesTampoco() {
        val c = ctx("""{"pacientes":true,"sesiones":true,"datos_personales":false}""")
        assertFalse(verDatosPersonalesFicha(c))
    }

    @Test
    fun backendSinLaClaveNoLosMuestra() {
        assertFalse(ctx("""{"pacientes":true}""").puede("datos_personales"))
    }
}
