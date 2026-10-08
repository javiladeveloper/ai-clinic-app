package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CampaniasActividadPlanTest {

    private fun camp(activo: Boolean = true, ini: String = "2026-10-01", fin: String? = null) =
        CampaniaGestion(id = "1", nombre = "Fiestas", tipo = "porcentaje", valor = 10.0, fechaInicio = ini, fechaFin = fin, activo = activo)

    @Test fun vigenciaInclusiva() {
        assertTrue(campaniaVigente(camp(fin = "2026-10-08"), "2026-10-08"))
        assertEquals(EstadoCampania.VENCIDA, estadoCampania(camp(fin = "2026-10-07"), "2026-10-08"))
        assertEquals(EstadoCampania.PROGRAMADA, estadoCampania(camp(ini = "2026-10-10"), "2026-10-08"))
        assertEquals(EstadoCampania.INACTIVA, estadoCampania(camp(activo = false), "2026-10-08"))
    }

    @Test fun etiquetas() {
        assertEquals("10 sesiones a S/500", etiquetaCampania(CampaniaGestion("1", "x", tipo = "paquete_fijo", cantidad = 10, precio = 500.0)))
        assertEquals("10% de descuento", etiquetaCampania(camp()))
        assertEquals("S/12.50 de descuento", etiquetaCampania(CampaniaGestion("1", "x", tipo = "monto_fijo", valor = 12.5)))
    }

    @Test fun validacionDelFormulario() {
        val base = FormCampania(nombre = "Promo", tipo = "porcentaje", valor = "10", desde = "2026-10-01")
        assertNull(problemaFormCampania(base))
        assertNotNull(problemaFormCampania(base.copy(nombre = " ")))
        assertNotNull(problemaFormCampania(base.copy(valor = "120")))
        assertNotNull(problemaFormCampania(base.copy(alcance = "servicios")))
        assertNotNull(problemaFormCampania(base.copy(tipo = "paquete_fijo", cantidad = "5", precio = "100", alcance = "citas")))
        assertNotNull(problemaFormCampania(base.copy(hasta = "2026-09-01")))
    }

    @Test fun cuerpoSoloConLosCamposDelTipo() {
        val c = cuerpoGuardarCampania(FormCampania(nombre = " Promo ", tipo = "porcentaje", valor = "10,5", cantidad = "9", precio = "99", desde = "2026-10-01"), "abc")
        assertEquals("abc", (c["id"] as JsonPrimitive).content)
        assertEquals("Promo", (c["nombre"] as JsonPrimitive).content)
        assertEquals("10.5", (c["valor"] as JsonPrimitive).content)
        assertTrue(c["precio"] is kotlinx.serialization.json.JsonNull)
        assertTrue(c["cantidad"] is kotlinx.serialization.json.JsonNull)
    }

    @Test fun actividadFiltrosCombinados() {
        fun m(quien: String, accion: String, tabla: String, pac: String?, dni: String?) =
            MovimientoActividad("2026-10-08T18:49:00Z", quien, "x", accion, tabla, pac, dni, null, null)
        val det = listOf(
            m("Ana", "INSERT", "pacientes", "Luis Pérez", "123"),
            m("Ana", "DELETE", "citas", "Rosa", "456"),
            m("Beto", "COMPLETAR", "sesiones", "Luis Pérez", "123"),
        )
        assertEquals(2, filtrarMovimientos(det, "Ana", null, "").size)
        assertEquals(1, filtrarMovimientos(det, null, TipoActividad.PACIENTES, "").size)
        assertEquals(2, filtrarMovimientos(det, null, null, "123").size)
        assertEquals(1, filtrarMovimientos(det, "Beto", null, "luis").size)
        assertEquals("13:49", horaDeMovimiento("2026-10-08T18:49:00Z"))
        assertEquals("2026-10-08", diaDeMovimiento("2026-10-08T18:49:00Z"))
        assertEquals("LP", inicialesActividad("Dr. Luis Pérez"))
    }

    @Test fun actividadParseaLaRespuestaDeLaWeb() {
        val d = ActividadRepo.parsear(
            """{"desde":"2026-10-08","hasta":"2026-10-08","esHoy":true,
            "creados":{"pacientes":2,"citas":3,"sesiones":1,"tratamientos":0,"pagos":1},
            "sesionesCompletadas":4,"eliminados":1,
            "equipo":[{"quien":"Ana","creados":5,"completadas":4,"eliminados":1,"editados":2}],
            "detalle":[{"cuando":"2026-10-08T18:49:00Z","quien":"Ana","que":"Cita creada","accion":"INSERT","tabla":"citas","paciente":"Luis","dni":null,"pacienteId":null,"agendadaPara":"2026-10-20 09:00"}]}""",
        )
        assertNotNull(d)
        assertEquals(7, d.creados.total)
        assertEquals(1, d.equipo.size)
        assertNull(d.detalle[0].dni)
        assertEquals("2026-10-20 09:00", d.detalle[0].agendadaPara)
    }

    @Test fun planParseaYFormatea() {
        val d = PlanRepo.parsear(
            """{"plan":"Basico","nombrePlan":"Básico","vencido":false,"diasRestantes":12,"planVence":"2026-10-20",
            "uso":{"profesionalesActivos":2,"maxProfesionales":2,"maxProfesionalesPorSede":2,"sedesActivas":1,"sedesPagadas":1,"documentosUsadoBytes":2097152,"documentosMaxMB":1024},
            "planes":[{"id":"Basico","nombre":"Básico","descripcion":"d","precio":100,"precioAnual":1000,"precioPorSede":60,"maxProfesionales":2,"incluye":[{"ok":true,"texto":"a"},{"ok":false,"texto":"b"}]}],
            "addonsCatalogo":[{"clave":"agenteSani","nombre":"Sani","precio":150,"requierePlan":null,"descripcion":"x","detalle":["u"],"niveles":[{"id":"inicio","nombre":"Inicio","precio":150,"resumen":"r","detalle":["z"]}]}],
            "addonsActivos":[{"addon":"agenteSani","vence":null,"precio_mensual":"150.00"}]}""",
        )
        assertNotNull(d)
        assertEquals(2, d.uso.maxProfesionales)
        assertEquals(2, d.incluyeActual?.size)
        assertEquals("inicio", nivelContratado(d.addons[0], d.addonsActivos[0].precioMensual)?.id)
        assertEquals("2 MB", formatearBytes(2097152))
        assertEquals("820 KB", formatearBytes(839680))
        assertEquals("1 GB", textoEspacioMB(1024))
        assertEquals("500 MB", textoEspacioMB(500))
    }
}
