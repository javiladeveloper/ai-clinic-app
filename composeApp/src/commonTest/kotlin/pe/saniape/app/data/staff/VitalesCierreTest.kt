package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Gemelo de lib/__tests__/vitales-cierre.test.ts (web). */
class VitalesCierreTest {
    @Test
    fun medicionesSinMedidasCorporales() {
        assertEquals(listOf("presion", "saturacion_o2"), medicionesDeCierre(listOf("presion", "peso", "talla", "saturacion_o2")))
        assertEquals(listOf("presion", "frecuencia_cardiaca", "frecuencia_respiratoria", "temperatura", "saturacion_o2"), medicionesDeCierre(emptyList()))
        assertEquals(listOf("presion", "frecuencia_cardiaca", "saturacion_o2"), medicionesDeCierre(listOf("peso", "talla")))
    }

    @Test
    fun antecedentesRealesDeDalu() {
        assertEquals(listOf("hipertensión"), antecedentesDeRiesgo("Hipertensión"))
        assertEquals(listOf("hipertensión"), antecedentesDeRiesgo("presion alta"))
        assertEquals(listOf("hipertensión", "diabetes"), antecedentesDeRiesgo("HTA, DM2"))
        assertEquals(listOf("diabetes"), antecedentesDeRiesgo("artrosis tipo pico de loro en la columna, diabetico"))
        assertEquals(listOf("cardiaco"), antecedentesDeRiesgo("hospitalizacion, problema cardiaco"))
        assertEquals(listOf("tiroides"), antecedentesDeRiesgo("tiroides, operacion de vesicula, colomna y histeroctomia"))
    }

    @Test
    fun cirugiasYNegacionesNoCuentan() {
        for (t in listOf("vesicula", "cesarea", "apendicitis", "caida en moto , fractura de tobillo", "cirugia de meñiscos", null, "")) {
            assertEquals(emptyList(), antecedentesDeRiesgo(t), "$t")
        }
        assertEquals(emptyList(), antecedentesDeRiesgo("niega HTA"))
        assertEquals(emptyList(), antecedentesDeRiesgo("No diabetes. Operada de vesícula"))
        assertEquals(listOf("hipertensión"), antecedentesDeRiesgo("sin asma; hipertensa"))
    }

    @Test
    fun resumenYHayVitales() {
        val v = mapOf("presion_sistolica" to "120", "presion_diastolica" to "80", "frecuencia_cardiaca" to "72", "saturacion_o2" to "98")
        assertEquals("PA 120/80 · FC 72 · SpO₂ 98%", resumenVitales(v))
        assertEquals("", resumenVitales(emptyMap()))
        assertTrue(hayVitalesCierre(v, listOf("presion")))
        assertFalse(hayVitalesCierre(mapOf("peso" to "70"), listOf("presion", "saturacion_o2")))
    }
}
