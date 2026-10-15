package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import pe.saniape.app.data.staff.SIGNOS_ALARMA_EMB

/**
 * 🤰 Mi embarazo (portal): el parseo de /api/paciente/mi-embarazo (contrato en
 * la web: docs/app-contrato-portal-paciente.md §9; mismos datos que
 * `lib/__tests__/embarazo-portal.test.ts`) y la EG recalculada de la caché.
 */
class MiEmbarazoPortalTest {

    // La respuesta que la web arma para el test de respuestaMiEmbarazo (HOY = 2026-08-23).
    private val respuesta = """{"embarazos":[{"id":"e1","fpp":"2026-12-06","inicio":"2026-03-01","metodoDatacion":"fur",
        "egHoyDias":175,"egHoy":"25 sem","trimestre":2,"diasParaFpp":105,"grupoSanguineo":"O","rh":"+",
        "clinica":{"nombre":"Qhali Gyn","logo_url":null,"color_principal":"#7c3aed"},"profesional":"Dra. Quispe",
        "controles":[
          {"id":"b","fecha":"2026-08-16","egDias":168,"eg":"24 sem","peso":"66.0","paSistolica":110,"paDiastolica":70,"alturaUterina":"24.0","lcf":145},
          {"id":"a","fecha":"2026-07-19","egDias":140,"eg":"20 sem","peso":64,"paSistolica":null,"paDiastolica":null,"alturaUterina":null,"lcf":null}
        ],"proximoControl":"2026-09-13"},
        {"fpp":"2026-12-06"}],
      "signosAlarma":["Sangrado por la vagina","Fiebre"]}"""

    @Test fun parseaEgFppControlesYProximo() {
        val d = parsearMiEmbarazo(respuesta)!!
        assertEquals(1, d.embarazos.size)
        val e = d.embarazos[0]
        assertEquals("2026-12-06", e.fpp)
        assertEquals("2026-03-01", e.inicio)
        assertEquals(175, e.egHoyDias)
        assertEquals("25 sem", e.egHoy)
        assertEquals(2, e.trimestre)
        assertEquals(105, e.diasParaFpp)
        assertEquals("2026-09-13", e.proximoControl)
        assertEquals("Qhali Gyn", e.clinicaNombre)
        assertEquals("Dra. Quispe", e.profesional)
        // Cronológicos aunque lleguen al revés; números aunque lleguen como texto.
        assertEquals(listOf("2026-07-19", "2026-08-16"), e.controles.map { it.fecha })
        assertEquals(ControlPortalEmb("a", "2026-07-19", 140, "20 sem", 64.0, null, null, null, null), e.controles[0])
        assertEquals(66.0, e.controles[1].peso)
        assertEquals(24.0, e.controles[1].alturaUterina)
        assertEquals(145, e.controles[1].lcf)
        assertEquals(listOf("Sangrado por la vagina", "Fiebre"), d.signosAlarma)
    }

    @Test fun laCacheRecalculaLaEgSinIrHaciaAtras() {
        val e = parsearMiEmbarazo(respuesta)!!.embarazos[0]
        assertEquals("2026-08-23", e.hoyServidor)
        // Una semana después (lo pintado venía de la caché): 26 sem, faltan 98 días.
        val luego = e.vigente("2026-08-30")
        assertEquals(182, luego.egHoyDias)
        assertEquals("26 sem", luego.egHoy)
        assertEquals(98, luego.diasParaFpp)
        // El teléfono va un día atrás de la sede: manda el servidor.
        assertEquals(175, e.vigente("2026-08-22").egHoyDias)
    }

    @Test fun vaciaYServidorSinLaLista() {
        val d = parsearMiEmbarazo("""{"embarazos":[{"id":"e1","fpp":"2026-12-06","diasParaFpp":105,"controles":[]}]}""")!!
        // Sin "inicio" (servidor viejo): FPP − 280 días.
        assertEquals("2026-03-01", d.embarazos[0].inicio)
        // Sin la lista educativa: la del catálogo (la misma de la web).
        assertEquals(SIGNOS_ALARMA_EMB.map { it.educativo }, d.signosAlarma)
        assertTrue(parsearMiEmbarazo("""{"embarazos":[],"signosAlarma":[]}""")!!.embarazos.isEmpty())
        assertNull(parsearMiEmbarazo("no es json"))
    }
}
