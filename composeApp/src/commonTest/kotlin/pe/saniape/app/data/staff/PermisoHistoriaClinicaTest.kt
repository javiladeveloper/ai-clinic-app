package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El contexto del staff lee `permisos.historia_clinica`. Sin la clave (backend
 * viejo) es TRUE: ninguna clínica de las de siempre pierde lo clínico. Solo un
 * `false` explícito (recepción de psicología, por ejemplo) lo oculta.
 */
class PermisoHistoriaClinicaTest {

    private fun ctx(permisos: String) = StaffContextoRepo.parsear(
        Json.parseToJsonElement("""{"clinicaId":"c1","rol":"Recepcionista","permisos":$permisos}""").jsonObject,
    )

    @Test
    fun backendSinLaClaveLaMuestra() {
        val c = ctx("""{"pacientes":true,"agendar":true}""")
        assertTrue(c.permisos.historiaClinica)
        assertTrue(c.puede("historia_clinica"))
        assertTrue(verHistoriaClinica(c))
    }

    @Test
    fun sinObjetoDePermisosTambienLaMuestra() {
        val c = StaffContextoRepo.parsear(
            Json.parseToJsonElement("""{"clinicaId":"c1","rol":"Admin"}""").jsonObject,
        )
        assertTrue(verHistoriaClinica(c))
    }

    @Test
    fun recepcionDePsicologiaSinElPermisoNoLaVe() {
        val c = ctx("""{"pacientes":true,"agendar":true,"pagos":true,"datos_personales":true,"historia_clinica":false}""")
        assertFalse(c.permisos.historiaClinica)
        assertFalse(c.puede("historia_clinica"))
        assertFalse(verHistoriaClinica(c))
        // Lo demás no cambia: sigue agendando, cobrando y viendo los datos personales.
        assertTrue(c.puede("agendar"))
        assertTrue(c.puede("pagos"))
        assertTrue(verDatosPersonalesFicha(c))
    }

    @Test
    fun profesionalConElPermisoLaVe() {
        val c = ctx("""{"pacientes":false,"sesiones":true,"citas":true,"historia_clinica":true}""")
        assertTrue(verHistoriaClinica(c))
    }

    @Test
    fun permisosPorDefectoLaIncluyen() {
        val p = Permisos(
            pacientes = false, citas = false, sesiones = false, pagos = false, finanzas = false,
            comisiones = false, servicios = false, equipo = false, ajustes = false,
        )
        assertEquals(true, p.historiaClinica)
    }
}
