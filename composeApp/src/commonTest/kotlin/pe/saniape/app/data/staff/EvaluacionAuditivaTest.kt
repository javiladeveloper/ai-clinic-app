package pe.saniape.app.data.staff

import pe.saniape.app.ui.clinica.audiologia.ViaAudiometria
import pe.saniape.app.ui.clinica.audiologia.tocarAudiograma
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Gemelo de lib/__tests__/evaluacion-auditiva.test.ts (web, 24/11/2026). */
class EvaluacionAuditivaTest {
    private fun curva(vararg p: Pair<Int, Int>): Curva = p.associate { (f, db) -> f to Umbral(db) }

    @Test fun gradosOms() {
        assertEquals(GradoPerdida.NORMAL, gradoOMS(19.9))
        assertEquals(GradoPerdida.LEVE, gradoOMS(20.0))
        assertEquals(GradoPerdida.MODERADA, gradoOMS(35.0))
        assertEquals(GradoPerdida.MOD_SEVERA, gradoOMS(50.0))
        assertEquals(GradoPerdida.SEVERA, gradoOMS(65.0))
        assertEquals(GradoPerdida.PROFUNDA, gradoOMS(80.0))
        assertEquals(GradoPerdida.TOTAL, gradoOMS(95.0))
        assertNull(gradoOMS(null))
    }

    @Test fun promedioYTipo() {
        val neuro = AudiometriaOido(curva(500 to 30, 1000 to 35, 2000 to 50, 4000 to 65), curva(500 to 30, 1000 to 35, 2000 to 45, 4000 to 60))
        assertEquals(45.0, promedioTonal(neuro.aerea))
        assertEquals(TipoPerdida.NEUROSENSORIAL, tipoSugerido(neuro))
        val conductiva = AudiometriaOido(curva(500 to 45, 1000 to 40, 2000 to 40, 4000 to 45), curva(500 to 10, 1000 to 10, 2000 to 15, 4000 to 15))
        assertEquals(TipoPerdida.CONDUCTIVA, tipoSugerido(conductiva))
        val mixta = AudiometriaOido(curva(500 to 70, 1000 to 70, 2000 to 75, 4000 to 80), curva(500 to 40, 1000 to 45, 2000 to 50, 4000 to 60))
        assertEquals(TipoPerdida.MIXTA, tipoSugerido(mixta))
        // Sin ósea no se puede decir; falta una frecuencia del promedio → null.
        assertNull(tipoSugerido(AudiometriaOido(neuro.aerea)))
        assertNull(promedioTonal(curva(500 to 30, 1000 to 35)))
    }

    @Test fun pasosYAudifonos() {
        val noApto = DatosAuditivos(otoscopia = Otoscopia(apto = false))
        assertEquals(listOf(PasoAuditivo.ANAMNESIS, PasoAuditivo.OTOSCOPIA), pasosDisponibles(noApto))
        val neuro = AudiometriaOido(curva(500 to 30, 1000 to 35, 2000 to 50, 4000 to 65), curva(500 to 30, 1000 to 35, 2000 to 45, 4000 to 60))
        val d = DatosAuditivos(audiometria = mapOf(Oido.OD to neuro, Oido.OI to AudiometriaOido()))
        assertTrue(sugiereAudifonos(d))
        assertEquals(5, pasosDisponibles(d).size)
        assertFalse(sugiereAudifonos(DatosAuditivos()))
        assertEquals("Pérdida moderada neurosensorial (promedio 45 dB HL)", fraseOido(d, Oido.OD))
    }

    @Test fun idaYVueltaDelJson() {
        val d = DatosAuditivos(
            anamnesis = Anamnesis(listOf("Oye menos"), "ambos", "2 años", true, null, false, ""),
            otoscopia = Otoscopia(OtoscopiaOido(listOf("Conducto permeable")), OtoscopiaOido(), true, ""),
            audiometria = mapOf(Oido.OD to AudiometriaOido(mapOf(500 to Umbral(30), 4000 to Umbral(120, nr = true))), Oido.OI to AudiometriaOido()),
            logo = mapOf(Oido.OD to Logo(45, 80, 75), Oido.OI to Logo()),
            conclusion = Conclusion(ConclusionOido(GradoPerdida.MODERADA, TipoPerdida.NEUROSENSORIAL), ConclusionOido(), "texto", "rec"),
            demostracion = Demostracion(true, "RIC", "ambos", "bien"),
        )
        assertEquals(d, normalizarDatos(datosAJson(d)))
    }

    @Test fun tocarElAudiograma() {
        // 500 Hz (columna 2) a 30 dB, en unidades del lienzo 340 × 372.
        val x500 = 38f + 1.5f * ((340f - 38f - 12f) / 8)
        val y30 = 26f + (40f / 130f) * (372f - 26f - 22f)
        val v = tocarAudiograma(AudiometriaOido(), ViaAudiometria.AEREA, false, x500, y30)!!
        assertEquals(Umbral(30), v.aerea[500])
        // Tocar el mismo punto lo quita.
        assertEquals(emptyMap(), tocarAudiograma(v, ViaAudiometria.AEREA, false, x500, y30)!!.aerea)
        // Fuera de la cuadrícula: nada.
        assertNull(tocarAudiograma(v, ViaAudiometria.AEREA, false, 5f, 5f))
    }

    @Test fun nombreDeLaEspecialidad() {
        assertTrue(esAudiologia("Audiología"))
        assertTrue(esAudiologia("Venta de audífonos"))
        assertFalse(esAudiologia("Otorrinolaringología"))
    }
}
