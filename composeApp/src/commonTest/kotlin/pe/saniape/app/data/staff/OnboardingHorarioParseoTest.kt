package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Onboarding v2 (contexto, Primeros pasos, datos de ejemplo) y horario del profesional (contrato §3–4). */
class OnboardingHorarioParseoTest {

    private fun obj(s: String) = Json.parseToJsonElement(s) as JsonObject

    @Test
    fun contextoTraeLosCamposDelOnboardingV2() {
        val nuevo = StaffContextoRepo.parsear(obj("""
            {"clinicaId":"c1","rol":"Admin","permisos":{},"onboardingCompleto":false,
             "primerosPasos":"minimizado","primerosPasosActivos":true}
        """))
        assertFalse(nuevo.onboardingCompleto)
        assertEquals("minimizado", nuevo.primerosPasos)
        assertTrue(nuevo.primerosPasosActivos)
        // DALU / backend viejo: sin los campos → completo, sin primeros pasos.
        val viejo = StaffContextoRepo.parsear(obj("""{"clinicaId":"c1","rol":"Admin","permisos":{}}"""))
        assertTrue(viejo.onboardingCompleto)
        assertNull(viejo.primerosPasos)
        assertFalse(viejo.primerosPasosActivos)
        val dalu = StaffContextoRepo.parsear(obj("""{"clinicaId":"c1","onboardingCompleto":true,"primerosPasos":null,"primerosPasosActivos":false}"""))
        assertNull(dalu.primerosPasos)
    }

    @Test
    fun estadoDePrimerosPasos() {
        val e = assertNotNull(OnboardingRepo.parsearEstado("""
            {"mostrar":true,"estado":"visible","tareas":[
              {"clave":"profesional","icono":"🩺","titulo":"Un profesional con horario","desc":"Sin horario la agenda no ofrece horas libres.","hecho":true},
              {"clave":"paciente","icono":"👤","titulo":"Tu primer paciente","desc":"…","hecho":false}],
             "hechas":1,"total":7,"completas":false,"tieneEjemplo":true,"slug":"movivida"}
        """))
        assertTrue(e.mostrar)
        assertEquals(2, e.tareas.size)
        assertTrue(e.tareas[0].hecho)
        assertEquals("movivida", e.slug)
        val sin = assertNotNull(OnboardingRepo.parsearEstado("""{"mostrar":false,"estado":null,"tareas":[],"tieneEjemplo":false}"""))
        assertFalse(sin.mostrar)
        assertNull(OnboardingRepo.parsearEstado("<html>"))
    }

    @Test
    fun confirmacionDeBorrarEjemploListaNombres() {
        val r = assertNotNull(OnboardingRepo.parsearEjemplo(
            """{"ok":true,"pacientes":["Rosa Quispe"],"citas":1,"tratamientos":["Terapia física"],"cobros":50,"n":1,"borrado":false}""",
        ))
        assertEquals("Paciente: Rosa Quispe · 1 cita · Tratamiento: Terapia física · Cobros por S/ 50.00. Nada más.", textoConfirmacionEjemplo(r))
        assertEquals("No hay datos de ejemplo.", textoConfirmacionEjemplo(ResumenEjemplo()))
        assertEquals("Pacientes: A, B · 2 citas. Nada más.", textoConfirmacionEjemplo(ResumenEjemplo(pacientes = listOf("A", "B"), citas = 2)))
    }

    @Test
    fun urlDeLaPaginaPublica() {
        assertEquals("https://movivida.saniape.com", urlPaginaClinica("movivida", "https://www.saniape.com"))
        assertEquals("https://movivida.saniape.com", urlPaginaClinica("movivida", "http://10.0.2.2:3000"))
    }

    private val HORARIO = """
        {"terapeuta":{"id":"t1","nombre":"Ana Ruiz","turno":"Mañana (7am - 1pm)","sedeId":null},
         "franjas":[{"id":"f2","dia":"Lun","horaInicio":"14:00:00","horaFin":"18:00","sedeId":null},
                    {"id":"f1","dia":"Lun","horaInicio":"08:00","horaFin":"13:00","sedeId":null},
                    {"id":"f3","dia":"Mié","horaInicio":"08:00","horaFin":"13:00"}],
         "multiSede":false,"sedes":[],"sedeDefectoId":null,
         "rangoTurno":{"inicio":"07:00","fin":"13:00"},"puedeEditar":true,
         "ok":true,"cambios":{"insertadas":1,"actualizadas":0,"borradas":0},"tarea":"horario_guardado"}
    """.trimIndent()

    @Test
    fun parseaElHorario() {
        val h = assertNotNull(HorarioProfesionalRepo.parsear(HORARIO))
        assertEquals("Ana Ruiz", h.terapeuta.nombre)
        assertEquals(listOf("08:00", "14:00"), h.delDia("Lun").map { it.horaInicio })
        assertEquals(emptyList(), h.delDia("Dom"))
        assertEquals("07:00", h.rangoTurno?.inicio)
        assertTrue(h.puedeEditar)
        assertEquals("horario_guardado", h.tarea)
        assertEquals(7, DIAS_HORARIO.size)
    }

    @Test
    fun interpretaErroresDelHorario() {
        assertIs<HorarioProfesionalRepo.R.Ok>(HorarioProfesionalRepo.interpretar(200, HORARIO))
        // Endpoint ausente (la web aún no está en prod): HTML de Next, sin `error`.
        assertIs<HorarioProfesionalRepo.R.NoDisponible>(HorarioProfesionalRepo.interpretar(404, "<!DOCTYPE html>"))
        // Profesional de otra clínica: 404 CON mensaje → error legible.
        val noExiste = HorarioProfesionalRepo.interpretar(404, """{"error":"Profesional no encontrado"}""")
        assertEquals("Profesional no encontrado", (noExiste as HorarioProfesionalRepo.R.Error).mensaje)
        val cruce = HorarioProfesionalRepo.interpretar(409, """{"error":"El bloque de horas se cruza con uno ya existente."}""")
        assertEquals(409, (cruce as HorarioProfesionalRepo.R.Error).status)
        assertEquals("No tienes acceso a este horario.", (HorarioProfesionalRepo.interpretar(403, "") as HorarioProfesionalRepo.R.Error).mensaje)
        // Último bloque sin confirmar: la app pide confirmación y reintenta con vaciar:true.
        val ultimo = HorarioProfesionalRepo.interpretar(400, """{"error":"Es el último bloque: para dejar al profesional sin horario, confírmalo (vaciar: true)."}""")
        assertTrue(HorarioProfesionalRepo.pideVaciar(ultimo))
        assertFalse(HorarioProfesionalRepo.pideVaciar(HorarioProfesionalRepo.interpretar(400, """{"error":"Hora inválida"}""")))
        assertFalse(HorarioProfesionalRepo.pideVaciar(cruce))
    }

    @Test
    fun horasDelHorario() {
        assertEquals("08:00", horaCorta("8:00:00"))
        assertTrue(finDespuesDeInicio("08:00", "13:00"))
        assertFalse(finDespuesDeInicio("13:00", "13:00"))
        assertFalse(finDespuesDeInicio("14:00", "09:30"))
    }
}
