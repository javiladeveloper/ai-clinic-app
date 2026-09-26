package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import pe.saniape.app.data.staff.MapaFisio
import pe.saniape.app.data.staff.SesionFicha
import pe.saniape.app.data.staff.alternarMejoria
import pe.saniape.app.data.staff.avisoRenovacion
import pe.saniape.app.data.staff.citaEsFisio
import pe.saniape.app.data.staff.colorEvaArgb
import pe.saniape.app.data.staff.curvaDolor
import pe.saniape.app.data.staff.esFaltaSinAviso
import pe.saniape.app.data.staff.esNoVolvio
import pe.saniape.app.data.staff.puedeMarcarNoVolvio
import pe.saniape.app.data.staff.resumenDolor
import pe.saniape.app.data.staff.sumarTecnicasDictadas
import pe.saniape.app.data.staff.textoAvisoRenovacion
import pe.saniape.app.data.staff.textoEva
import pe.saniape.app.data.staff.textoMotivoCierre
import pe.saniape.app.data.staff.tieneMejoria
import pe.saniape.app.data.staff.unirDictado

/**
 * Mejoras de fisioterapia en la app. Gemelos de `mapaFisio`/`citaEsFisio`
 * (lib/flujo.ts), `lib/cierre-sesion.ts`, `lib/renovacion.ts`, `lib/no-volvio.ts`
 * y `lib/faltas-paciente.ts`: mismos casos que la web.
 */
class ClinicaMixtaFisioTest {
    private val mixta = MapaFisio(ids = listOf("e-fisio"), solo = false)
    private val soloFisio = MapaFisio(solo = true)
    private val sinFisio = MapaFisio()

    // ── ¿Es fisio? ──
    @Test fun sinMapaNadaEsFisio() {
        // Backend viejo o clínica dental/estética: la app se comporta como antes.
        assertFalse(citaEsFisio(sinFisio, "e-fisio", "e-fisio", listOf("e-fisio")))
    }

    @Test fun clinicaSoloFisioTodoEsFisio() {
        assertTrue(citaEsFisio(soloFisio))
    }

    @Test fun laCitaYElServicioMandan() {
        assertTrue(citaEsFisio(mixta, especialidadId = "e-fisio"))
        assertFalse(citaEsFisio(mixta, especialidadId = "e-odonto"))
        assertFalse(citaEsFisio(mixta, "e-odonto", "e-fisio"))
        assertTrue(citaEsFisio(mixta, especialidadServicioId = "e-fisio"))
    }

    @Test fun elProfesionalSoloSiTodasSusEspecialidadesSonFisio() {
        assertTrue(citaEsFisio(mixta, especialidadesProfesional = listOf("e-fisio")))
        assertFalse(citaEsFisio(mixta, especialidadesProfesional = listOf("e-fisio", "e-odonto")))
        assertFalse(citaEsFisio(mixta))
    }

    // ── EVA ──
    @Test fun colorEvaDeVerdeARojo() {
        assertEquals(0xFF16A34AL, colorEvaArgb(0))
        assertEquals(0xFFD97706L, colorEvaArgb(5))
        assertEquals(0xFFDC2626L, colorEvaArgb(10))
    }

    @Test fun textoEvaComoLaWeb() {
        assertEquals("EVA 7 → 3", textoEva(7, 3))
        assertEquals("EVA 7 →", textoEva(7, null))
        assertEquals("EVA → 3", textoEva(null, 3))
        assertEquals("", textoEva(null, null))
    }

    private fun ses(n: Int, estado: String, i: Int?, f: Int?) = SesionFicha(
        id = "s$n", numero = n, fecha = "2026-09-0$n", hora = null, estado = estado, costo = null,
        notas = null, mejorias = null, duracion = null, terapeutaNombre = null, motivoEstado = null,
        dolorInicio = i, dolorFin = f,
    )

    @Test fun curvaSoloCompletadasConDatosYResumen() {
        val puntos = curvaDolor(listOf(
            ses(3, "Completada", 5, 2), ses(1, "Completada", 8, 6),
            ses(2, "Completada", null, null), ses(4, "Planificada", 3, 1),
        ))
        assertEquals(listOf(1, 3), puntos.map { it.numero })
        val r = resumenDolor(puntos)!!
        assertEquals(8, r.desde); assertEquals(2, r.hasta); assertEquals(-6, r.cambio)
        assertNull(resumenDolor(emptyList()))
    }

    // ── Mejorías y dictado ──
    @Test fun chipDeMejoriaAlternaSinTocarLoEscrito() {
        assertEquals("Menos dolor", alternarMejoria("", "Menos dolor"))
        assertEquals("camina mejor, Menos dolor", alternarMejoria("camina mejor", "Menos dolor"))
        assertEquals("camina mejor", alternarMejoria("camina mejor, menos dolor", "Menos dolor"))
        assertTrue(tieneMejoria("Más movilidad, Peor", "más movilidad"))
    }

    @Test fun dictadoSeSumaAlTexto() {
        assertEquals("menos dolor al caminar", unirDictado("", " menos dolor al caminar "))
        assertEquals("Sin cambios hoy duele menos", unirDictado("Sin cambios ", "hoy duele menos"))
        assertEquals("previo", unirDictado("previo", "  "))
    }

    @Test fun tecnicasDictadasSeSeparanPorConjunciones() {
        val r = sumarTecnicasDictadas("", "tens y compresa caliente")
        assertEquals(2, r.split(" + ").size)
        // Lo que ya estaba no se duplica.
        assertEquals(r, sumarTecnicasDictadas(r, "tens"))
    }

    // ── Renovación ──
    @Test fun avisoDeRenovacionConUnaODosRestantes() {
        assertEquals(2, avisoRenovacion("Activo", "Paquete", 10, 8))
        assertEquals(1, avisoRenovacion("Activo", "Paquete", 10, 9))
        assertNull(avisoRenovacion("Activo", "Paquete", 10, 7))
        assertNull(avisoRenovacion("Activo", "Paquete", 10, 10))
        assertNull(avisoRenovacion("Completado", "Paquete", 10, 9))
        assertNull(avisoRenovacion("Activo", "Unidades", 10, 9))
        assertNull(avisoRenovacion("Activo", "Consulta", 2, 1))
        assertEquals("Le queda 1 sesión", textoAvisoRenovacion(1))
        assertEquals("Le quedan 2 sesiones", textoAvisoRenovacion(2))
    }

    // ── No volvió ──
    @Test fun motivoDeCierre() {
        assertEquals("No contesta", textoMotivoCierre("no_contesta", null))
        assertEquals("Se mudó — a Lima", textoMotivoCierre("se_mudo", " a Lima "))
        assertNull(textoMotivoCierre("otro", "  "))
        assertEquals("Otro — viaje", textoMotivoCierre("otro", "viaje"))
        assertNull(textoMotivoCierre("inventado", "x"))
    }

    @Test fun quienAdmiteNoVolvio() {
        assertTrue(puedeMarcarNoVolvio("Activo", "Paquete", 10))
        assertTrue(puedeMarcarNoVolvio("Activo", "Sesión suelta", 1))
        assertFalse(puedeMarcarNoVolvio("Suspendido", "Paquete", 10))
        assertFalse(puedeMarcarNoVolvio("Activo", "Unidades", 10))
        assertFalse(puedeMarcarNoVolvio("Activo", "Consulta", 1))
        assertFalse(puedeMarcarNoVolvio("Activo", "Paquete", 0))
        assertTrue(esNoVolvio("Suspendido", true))
        assertFalse(esNoVolvio("Suspendido", false))
        assertFalse(esNoVolvio("Activo", true))
    }

    // ── Faltas sin aviso ──
    @Test fun faltasSinAviso() {
        assertTrue(esFaltaSinAviso("Cancelada", null, noAsistio = true))
        assertTrue(esFaltaSinAviso("Cancelada", "No asistió (cierre automático por antigüedad)", noAsistio = false))
        assertFalse(esFaltaSinAviso("Cancelada", "El paciente avisó", noAsistio = false))
        assertFalse(esFaltaSinAviso("Completada", null, noAsistio = false))
    }
}
