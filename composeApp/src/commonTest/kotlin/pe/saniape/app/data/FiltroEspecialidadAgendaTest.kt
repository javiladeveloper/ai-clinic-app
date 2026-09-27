package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import pe.saniape.app.data.staff.CitaStaff
import pe.saniape.app.data.staff.SIN_ESPECIALIDAD
import pe.saniape.app.data.staff.alternarOpcion
import pe.saniape.app.data.staff.citaPasaFiltroEspecialidad
import pe.saniape.app.data.staff.codificarSeleccion
import pe.saniape.app.data.staff.decodificarSeleccion
import pe.saniape.app.data.staff.especialidadIdDeCita
import pe.saniape.app.data.staff.normalizarSeleccion
import pe.saniape.app.data.staff.opcionDeEspecialidad
import pe.saniape.app.data.staff.pasaFiltroEspecialidad

/** Gemelo de `lib/__tests__/filtro-especialidad-citas.test.ts` (web). Mismos casos. */
class FiltroEspecialidadAgendaTest {
    private val odonto = "e-odo"; private val fisio = "e-fis"; private val medi = "e-med"; private val vieja = "e-inactiva"
    private val ofrecidos = listOf(odonto, fisio, medi)
    private val esps = mapOf(
        "t-dentista" to listOf(odonto),
        "t-fisio" to listOf(fisio),
        "t-mixto" to listOf(fisio, medi),
        "t-sin" to emptyList(),
    )

    private fun cita(id: String, esp: String? = null, servicio: String? = null, ter: String? = null) = CitaStaff(
        id = id, fecha = "2026-09-26", hora = "09:00", estado = "Confirmada", tipo = "Sesión",
        costo = null, duracion = null, origen = null, confirmadaPorPaciente = false, numeroSesion = null,
        terapeutaId = ter, terapeutaNombre = null, pacienteId = "p", pacienteNombre = "P", pacienteTelefono = null,
        tratamientoId = null, procedimiento = null, especialidadId = esp, especialidadServicioId = servicio,
        notaRecepcion = null,
    )

    @Test fun laDeLaCitaMandaSobreTodo() =
        assertEquals(medi, especialidadIdDeCita(medi, fisio, listOf(odonto)))

    @Test fun sinLaDeLaCitaLaDelServicio() =
        assertEquals(fisio, especialidadIdDeCita(null, fisio, listOf(odonto)))

    @Test fun sinCitaNiServicioLaDelProfesionalSiTieneUna() =
        assertEquals(odonto, especialidadIdDeCita(null, null, esps["t-dentista"]))

    @Test fun profesionalConVariasOSinNingunaNoSeAdivina() {
        assertNull(especialidadIdDeCita(null, null, esps["t-mixto"]))
        assertNull(especialidadIdDeCita(null, null, esps["t-sin"]))
        assertNull(especialidadIdDeCita(null, null, null))
    }

    @Test fun desactivadaOSinDatoCaeEnSinEspecialidad() {
        val set = ofrecidos.toSet()
        assertEquals(SIN_ESPECIALIDAD, opcionDeEspecialidad(vieja, set))
        assertEquals(SIN_ESPECIALIDAD, opcionDeEspecialidad(null, set))
        assertEquals(odonto, opcionDeEspecialidad(odonto, set))
    }

    @Test fun seleccionVaciaEsTodasYVariasALaVez() {
        assertTrue(pasaFiltroEspecialidad(SIN_ESPECIALIDAD, emptyList()))
        assertTrue(pasaFiltroEspecialidad(odonto, listOf(odonto, fisio)))
        assertFalse(pasaFiltroEspecialidad(medi, listOf(odonto, fisio)))
    }

    private val citas = listOf(
        cita("a", esp = odonto),
        cita("b", ter = "t-fisio"),
        cita("c", servicio = medi, ter = "t-mixto"),
        cita("d", ter = "t-mixto"),
        cita("e", esp = vieja),
    )
    private fun filtrar(sel: List<String>) =
        citas.filter { citaPasaFiltroEspecialidad(it, sel, esps, ofrecidos.toSet()) }.map { it.id }

    @Test fun filtraComoLaWeb() {
        assertEquals(listOf("a", "b", "c", "d", "e"), filtrar(emptyList()))
        assertEquals(listOf("a"), filtrar(listOf(odonto)))
        assertEquals(listOf("b", "c"), filtrar(listOf(fisio, medi)))
        assertEquals(listOf("d", "e"), filtrar(listOf(SIN_ESPECIALIDAD)))
    }

    @Test fun cadaCitaCaeEnUnaSolaOpcion() {
        val total = (ofrecidos + SIN_ESPECIALIDAD).sumOf { filtrar(listOf(it)).size }
        assertEquals(citas.size, total)
    }

    @Test fun seleccionGuardadaSeLimpia() {
        assertEquals(listOf(odonto), normalizarSeleccion(listOf(odonto, vieja, odonto), ofrecidos))
        assertEquals(emptyList(), normalizarSeleccion(listOf(odonto, fisio, medi, SIN_ESPECIALIDAD), ofrecidos))
    }

    @Test fun alternarMarcaDesmarcaYVuelveATodas() {
        var s = alternarOpcion(emptyList(), fisio, ofrecidos)
        assertEquals(listOf(fisio), s)
        s = alternarOpcion(s, SIN_ESPECIALIDAD, ofrecidos)
        assertEquals(listOf(fisio, SIN_ESPECIALIDAD), s)
        s = alternarOpcion(alternarOpcion(s, fisio, ofrecidos), SIN_ESPECIALIDAD, ofrecidos)
        assertEquals(emptyList(), s)
    }

    @Test fun codificarYDecodificar() {
        assertNull(codificarSeleccion(emptyList()))
        assertEquals(listOf(odonto, SIN_ESPECIALIDAD), decodificarSeleccion(codificarSeleccion(listOf(odonto, SIN_ESPECIALIDAD))))
        assertEquals(emptyList(), decodificarSeleccion(null))
        assertEquals(emptyList(), decodificarSeleccion(" , "))
    }
}
