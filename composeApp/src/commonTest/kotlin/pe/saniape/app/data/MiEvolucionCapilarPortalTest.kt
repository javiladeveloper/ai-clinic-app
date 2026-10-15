package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import pe.saniape.app.data.staff.ZonaCapilar

/**
 * 🔬 Mi evolución capilar (portal): el parseo de /api/paciente/mi-evolucion-capilar
 * (contrato en la web: docs/app-contrato-portal-paciente.md §8; mismos datos que
 * `lib/__tests__/tricoscopia-api.test.ts`) y lo que se muestra.
 */
class MiEvolucionCapilarPortalTest {

    private val respuesta = """{"evaluaciones":[
        {"id":"e2","fecha":"2026-12-01","escala":"hamilton_norwood","escala_nombre":"Hamilton-Norwood","grado":"III",
         "zonas":[{"zona":"frontal","nombre":"Frontal","densidad":120,"miniaturizacion_pct":20},
                  {"zona":"vertex","nombre":"Coronilla / vértex","densidad":"95","miniaturizacion_pct":null}],
         "clinica":{"nombre":"RENOVA CAPILAR","logo_url":"https://x/logo.png","color_principal":"#0f766e"},"profesional":"Dra. Ruiz"},
        {"id":"e1","fecha":"2026-08-01","escala":"hamilton_norwood","escala_nombre":"Hamilton-Norwood","grado":"III",
         "zonas":[{"zona":"frontal","nombre":"Frontal","densidad":110,"miniaturizacion_pct":25},
                  {"zona":"vertex","densidad":80},{"zona":"nuca","densidad":1},{"zona":"occipital","densidad":null,"miniaturizacion_pct":null}],
         "clinica":null,"profesional":null}
    ]}"""

    @Test fun parseaCronologicoYTolerante() {
        val d = parsearMiEvolucionCapilar(respuesta)!!
        assertEquals(listOf("e1", "e2"), d.evaluaciones.map { it.id })
        val e1 = d.evaluaciones[0]
        // "nuca" no es una zona; la occipital sin densidad ni miniaturización no tiene nada que mostrar.
        assertEquals(listOf(ZonaCapilar.FRONTAL, ZonaCapilar.VERTEX), e1.zonas.map { it.zona })
        assertEquals("Coronilla / vértex", e1.zonas[1].nombre)
        assertNull(e1.clinicaNombre)
        val e2 = d.evaluaciones[1]
        assertEquals(95.0, e2.zonas[1].densidad)
        assertEquals("RENOVA CAPILAR", e2.clinicaNombre)
        assertEquals("Dra. Ruiz", e2.profesional)
        assertEquals("Hamilton-Norwood III", textoClasificacionPortal(e2))
    }

    @Test fun descartaLoQueNoTieneNadaQueMostrar() {
        val d = parsearMiEvolucionCapilar("""{"evaluaciones":[
            {"id":"a","fecha":"2026-01-01","escala":"rara","grado":"II","zonas":[{"zona":"frontal","signos":["eritema"]}]},
            {"id":"b","fecha":"2026-01-02","escala":"rara","grado":"II","zonas":[{"zona":"frontal","densidad":100}]},
            {"fecha":"2026-01-03","escala":"ludwig"}
        ]}""")!!
        assertEquals(listOf("b"), d.evaluaciones.map { it.id })
        // Escala desconocida → sin grado.
        assertNull(d.evaluaciones[0].escala)
        assertNull(d.evaluaciones[0].grado)
        assertFalse(parsearMiEvolucionCapilar("""{"evaluaciones":[]}""")!!.tieneDatos)
        assertNull(parsearMiEvolucionCapilar("no es json"))
    }

    @Test fun resumenYGraficoPorZona() {
        val items = parsearMiEvolucionCapilar(respuesta)!!.evaluaciones
        val r = resumenDensidadPortal(items)
        assertEquals(listOf(ZonaCapilar.FRONTAL, ZonaCapilar.VERTEX), r.map { it.zona })
        assertEquals(120.0, r[0].valor); assertEquals(10.0, r[0].cambio); assertEquals("2026-08-01", r[0].desde)
        assertEquals(15.0, r[1].cambio)
        assertEquals(listOf(ZonaCapilar.FRONTAL, ZonaCapilar.VERTEX), zonasConGraficoPortal(items, miniaturizacion = false))
        assertEquals(listOf(ZonaCapilar.FRONTAL), zonasConGraficoPortal(items, miniaturizacion = true))
        assertEquals(listOf(25.0, 20.0), serieCapilarPortal(items, ZonaCapilar.FRONTAL, miniaturizacion = true).map { it.valor })
        assertTrue(hayMiniaturizacionPortal(items))
        assertEquals("e2", clasificacionPortal(items)?.id)
        // Una sola visita: sin cambio que mostrar.
        assertNull(resumenDensidadPortal(items.take(1))[0].cambio)
    }
}
