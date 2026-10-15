package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Gemelo de lib/__tests__/dermatologia-medica.test.ts y frases-pediatria.test.ts
 * (web, 2026-10-15): qué atención es de dermatología o pediatría, y qué
 * frecuentes CIE-10 trae la app consigo.
 */
class AtencionVariantesTest {

    @Test fun dermatologiaPorElNombre() {
        assertTrue(esDermatologia("Dermatología"))
        assertTrue(esDermatologia("Dermatología estética"))
        assertTrue(esDermatologia("DERMATÓLOGO"))
        assertFalse(esDermatologia("Medicina General"))
        assertFalse(esDermatologia(null))
    }

    @Test fun pediatriaPorElNombreOdontopediatriaNo() {
        assertTrue(esPediatria("Pediatría"))
        assertTrue(esPediatria("PEDIATRIA"))
        assertFalse(esPediatria("Odontopediatría"))
        assertFalse(esPediatria(null))
    }

    @Test fun laAtencionEsDeLaEspecialidadDeLaCitaOSiTodasLoSon() {
        val esps = listOf(EspecialidadNombre("d", "Dermatología"), EspecialidadNombre("m", "Medicina General"))
        assertTrue(atencionEsDermatologia(esps, "d"))
        assertFalse(atencionEsDermatologia(esps, "m"))
        assertFalse(atencionEsDermatologia(esps, null))
        assertTrue(atencionEsDermatologia(listOf(EspecialidadNombre("d", "Dermatología")), null))

        val ped = listOf(EspecialidadNombre("p", "Pediatría"), EspecialidadNombre("m", "Medicina General"))
        assertTrue(atencionEsPediatria(ped, "p"))
        assertFalse(atencionEsPediatria(ped, "m"))
        assertFalse(atencionEsPediatria(ped, null))
        assertTrue(atencionEsPediatria(listOf(EspecialidadNombre("p", "Pediatría")), null))
        // Una inactiva no cuenta para "todas".
        assertTrue(atencionEsPediatria(listOf(EspecialidadNombre("p", "Pediatría"), EspecialidadNombre("x", "Fisioterapia", "Inactiva")), null))
    }

    @Test fun mandanDentalGinecoYPsiquiatria() {
        val esps = listOf(
            EspecialidadNombre("d", "Dermatología", rubro = "medicina_general"),
            EspecialidadNombre("p", "Pediatría", rubro = "medicina_general"),
            EspecialidadNombre("g", "Ginecología pediátrica", rubro = "ginecologia"),
        )
        assertEquals(VariantesAtencion(derma = true), variantesAtencion(esps, FlagsConsulta(especialidadId = "d"), psiq = false))
        assertEquals(VariantesAtencion(pediatria = true), variantesAtencion(esps, FlagsConsulta(especialidadId = "p"), psiq = false))
        assertEquals(VariantesAtencion(), variantesAtencion(esps, FlagsConsulta(especialidadId = "g"), psiq = false))
        assertEquals(VariantesAtencion(), variantesAtencion(esps, FlagsConsulta(especialidadId = "d", dental = true), psiq = false))
        assertEquals(VariantesAtencion(), variantesAtencion(esps, FlagsConsulta(especialidadId = "d"), psiq = true))
    }

    @Test fun sinEspecialidadesUsaLosFlagsDelServidorYUnoViejoNoLosManda() {
        assertEquals(VariantesAtencion(), variantesAtencion(emptyList(), FlagsConsulta(especialidadId = "d"), psiq = false))
        assertEquals(
            VariantesAtencion(derma = true, pediatria = false),
            variantesAtencion(emptyList(), FlagsConsulta(especialidadId = "d", derma = true, pediatria = false), psiq = false),
        )
    }

    @Test fun losFlagsNuevosSonOpcionalesEnElJson() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val viejo = json.decodeFromString(FlagsConsulta.serializer(), """{"dental":false,"especialidadId":"d","psiq":false}""")
        assertNull(viejo.derma)
        assertNull(viejo.pediatria)
        val nuevo = json.decodeFromString(FlagsConsulta.serializer(), """{"especialidadId":"d","derma":true,"pediatria":false}""")
        assertEquals(true, nuevo.derma)
        assertEquals(false, nuevo.pediatria)
    }

    @Test fun frecuentesCie10LocalesSoloDermatologia() {
        val derma = frecuentesCie10Locales(dental = false, psiq = false, derma = true)!!
        assertEquals(FRECUENTES_DERMATOLOGIA.size, derma.size)
        assertEquals(DiagnosticoCie("L70.0", "Acné vulgar"), derma.first())
        assertEquals(DiagnosticoCie("L91.0", "Cicatriz queloide"), derma.last())
        // Gana dental y psiquiatría (frecuentesCie10 de la web); medicina la manda el servidor.
        assertNull(frecuentesCie10Locales(dental = true, psiq = false, derma = true))
        assertNull(frecuentesCie10Locales(dental = false, psiq = true, derma = true))
        assertNull(frecuentesCie10Locales(dental = false, psiq = false, derma = false))
    }

    @Test fun frecuentesDeDermatologiaIgualQueLaWeb() {
        assertEquals(29, FRECUENTES_DERMATOLOGIA.size)
        assertEquals(FRECUENTES_DERMATOLOGIA.size, FRECUENTES_DERMATOLOGIA.map { it.first }.toSet().size)
        assertEquals(
            listOf("L70.0", "L71.9", "L20.9", "L21.9", "L23.9", "L30.9", "L40.0", "L50.9", "L29.9", "B35.1", "B35.3", "B35.4", "B36.0", "B07.X", "B08.1",
                "B02.9", "B86.X", "L81.1", "L80.X", "L82.X", "L57.0", "D22.9", "D23.9", "L72.1", "C44.9", "L63.9", "L64.9", "L60.0", "L91.0"),
            FRECUENTES_DERMATOLOGIA.map { it.first },
        )
    }
}
