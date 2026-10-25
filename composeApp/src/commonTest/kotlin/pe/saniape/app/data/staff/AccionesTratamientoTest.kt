package pe.saniape.app.data.staff

import pe.saniape.app.data.staff.AccionTratamiento.Agendar
import pe.saniape.app.data.staff.AccionTratamiento.Alta
import pe.saniape.app.data.staff.AccionTratamiento.Ampliar
import pe.saniape.app.data.staff.AccionTratamiento.Cancelar
import pe.saniape.app.data.staff.AccionTratamiento.Derivar
import pe.saniape.app.data.staff.AccionTratamiento.Editar
import pe.saniape.app.data.staff.AccionTratamiento.Eliminar
import pe.saniape.app.data.staff.AccionTratamiento.NoVolvio
import pe.saniape.app.data.staff.AccionTratamiento.Reactivar
import pe.saniape.app.data.staff.AccionTratamiento.Receta
import pe.saniape.app.data.staff.AccionTratamiento.SesionPasada
import pe.saniape.app.data.staff.AccionTratamiento.Suspender
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Gemelo de lib/__tests__/acciones-tratamiento.test.ts (web). */
class AccionesTratamientoTest {

    private val todas = AccionTratamiento.entries.toList()
    private val enCurso = TratamientoParaTerminar("Activo", 8, 2)
    private val terminado = TratamientoParaTerminar("Activo", 5, 5)
    private fun t(estado: String) = TratamientoParaTerminar(estado, null, null)

    // ── Momento del plan ──

    @Test
    fun activoConSesionesPorHacerEstaEnCurso() {
        assertEquals(MomentoTratamiento.EN_CURSO, momentoTratamiento(enCurso))
        // Sin contador (Consulta / Unidades): sigue en curso mientras esté Activo.
        assertEquals(MomentoTratamiento.EN_CURSO, momentoTratamiento(TratamientoParaTerminar("Activo", 0, 0)))
    }

    @Test
    fun todasHechasOCompletadoEsTerminado() {
        assertEquals(MomentoTratamiento.TERMINADO, momentoTratamiento(terminado))
        assertEquals(MomentoTratamiento.TERMINADO, momentoTratamiento(TratamientoParaTerminar("Activo", 3, 4)))
        assertEquals(MomentoTratamiento.TERMINADO, momentoTratamiento(TratamientoParaTerminar("Completado", 0, null)))
    }

    @Test
    fun suspendidoYCanceladoPausadoAltaCerrado() {
        assertEquals(MomentoTratamiento.PAUSADO, momentoTratamiento(t("Suspendido")))
        assertEquals(MomentoTratamiento.PAUSADO, momentoTratamiento(t("Cancelado")))
        assertEquals(MomentoTratamiento.CERRADO, momentoTratamiento(TratamientoParaTerminar("Alta", 5, 5)))
        assertEquals(MomentoTratamiento.CERRADO, momentoTratamiento(null))
    }

    // ── A la vista solo lo del momento ──

    @Test
    fun enCursoAgendarLlenoMasEditarYElRestoAlMenuAgrupado() {
        val p = repartirAccionesTratamiento(enCurso, todas)
        assertEquals(Agendar, p.primaria)
        assertEquals(listOf(Editar), p.secundarias)
        assertEquals(
            listOf(
                GrupoMenu("Clínico", listOf(Receta, Derivar)),
                GrupoMenu("Plan", listOf(Ampliar, SesionPasada, Alta)),
                GrupoMenu("Estado", listOf(Reactivar, Suspender, NoVolvio, Cancelar)),
                GrupoMenu(GRUPO_PELIGRO, listOf(Eliminar)),
            ),
            p.menu,
        )
    }

    @Test
    fun terminadoAmpliarYAltaALaVistaYAgendarPasaAlMenu() {
        val p = repartirAccionesTratamiento(terminado, todas)
        // Las mismas tres visibles que la web; Dar de alta es el botón lleno.
        assertEquals(setOf(Ampliar, Alta, Editar), p.visibles.toSet())
        assertEquals(Alta, p.primaria)
        assertEquals(listOf(Ampliar, Editar), p.secundarias)
        assertEquals(listOf(Agendar, SesionPasada), p.menu.first { it.titulo == "Plan" }.acciones)
    }

    @Test
    fun suspendidoOCanceladoReactivarALaVista() {
        for (estado in listOf("Suspendido", "Cancelado")) {
            val p = repartirAccionesTratamiento(t(estado), listOf(Reactivar, Editar, Derivar, Eliminar))
            assertEquals(Reactivar, p.primaria)
            assertEquals(listOf(Editar), p.secundarias)
            assertEquals(listOf(Derivar, Eliminar), p.enMenu)
        }
    }

    @Test
    fun eliminarSiempreAlFinalEnSuPropioGrupo() {
        for (tr in listOf(enCurso, terminado, t("Suspendido"), t("Alta"))) {
            assertEquals(GrupoMenu(GRUPO_PELIGRO, listOf(Eliminar)), repartirAccionesTratamiento(tr, todas).menu.last())
        }
    }

    // ── No se pierde ni se inventa ninguna acción ──

    @Test
    fun cadaDisponibleApareceExactamenteUnaVez() {
        val estados = listOf(enCurso, terminado, t("Completado"), t("Suspendido"), t("Cancelado"), t("Alta"))
        for (tr in estados) {
            val p = repartirAccionesTratamiento(tr, todas)
            assertEquals(todas.sorted(), (p.visibles + p.enMenu).sorted())
            assertTrue(p.visibles.size <= 3)
        }
    }

    @Test
    fun loNoDisponibleNoApareceEnNingunLado() {
        val p = repartirAccionesTratamiento(enCurso, listOf(Editar, Derivar))
        // Sin Agendar (consulta, sin permiso…): no hay botón lleno; Editar va con contorno.
        assertNull(p.primaria)
        assertEquals(listOf(Editar), p.visibles)
        assertEquals(listOf(Derivar), p.enMenu)
        // Terminado pero sin ampliar (evaluación psicológica).
        assertEquals(listOf(Alta, Editar), repartirAccionesTratamiento(terminado, listOf(Alta, Editar)).visibles)
    }

    @Test
    fun sinNadaParaElMenuNoSeDibujaElMenu() {
        assertTrue(repartirAccionesTratamiento(enCurso, listOf(Agendar, Editar)).menu.isEmpty())
        assertTrue(repartirAccionesTratamiento(enCurso, emptyList()).menu.isEmpty())
    }

    @Test
    fun consultaActivaSinAgendarNiEditarTodoAlMenu() {
        // La consulta médica: el alta, la receta y el estado van al ⋯ (la nube Control ya ofrece el alta).
        val p = repartirAccionesTratamiento(TratamientoParaTerminar("Activo", 0, 0), listOf(Alta, Receta, Suspender, Cancelar))
        assertTrue(p.visibles.isEmpty())
        assertEquals(listOf(Receta, Alta, Suspender, Cancelar), p.enMenu)
    }
}
