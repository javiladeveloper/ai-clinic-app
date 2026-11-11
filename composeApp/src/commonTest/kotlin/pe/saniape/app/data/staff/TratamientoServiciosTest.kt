package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class TratamientoServiciosTest {
    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    @Test
    fun conVariasLineasSeNombraConTodasEnOrden() {
        val t = obj("""{"procedimiento":{"nombre":"OCT"},"servicios":[
            {"orden":1,"procedimiento":{"nombre":"Campimetría"}},
            {"orden":0,"procedimiento":{"nombre":"OCT"}}]}""")
        assertEquals("OCT + Campimetría", TratamientoServicios.nombre(t, "OCT"))
    }

    @Test
    fun sinLineasOConUnaQuedaElDeSiempre() {
        assertEquals("Limpieza", TratamientoServicios.nombre(obj("""{"servicios":[]}"""), "Limpieza"))
        assertEquals("Limpieza", TratamientoServicios.nombre(obj("""{"servicios":[{"orden":0,"procedimiento":{"nombre":"Otra"}}]}"""), "Limpieza"))
        assertEquals("Limpieza", TratamientoServicios.nombre(obj("""{}"""), "Limpieza"))
        assertEquals(null, TratamientoServicios.nombre(null, null))
    }

    // ── Crear / editar con varios servicios (app 2.71) ──
    private fun proc(id: String, precio: Double, esp: String? = "oft", modo: String? = "simple", tipoCita: String? = null) =
        ProcedimientoRef(id = id, nombre = id.uppercase(), precio = precio, precioPaquete = null, especialidadId = esp,
            usaSesiones = false, tarifarios = emptyList(), modoCobro = modo, tipoCita = tipoCita)

    @Test
    fun candidatosSoloPagoUnicoDeLaMismaEspecialidad() {
        val oct = proc("oct", 300.0)
        val procs = listOf(
            oct, proc("campi", 180.0), proc("lavado", 60.0, esp = "otorrino"),
            proc("terapia", 50.0, modo = "sesiones"), proc("consulta", 0.0), proc("eval", 100.0, tipoCita = "Evaluación"),
        )
        assertEquals(listOf("campi"), candidatosOtroServicio(procs, oct).map { it.id })
        assertEquals(emptyList(), candidatosOtroServicio(procs, null))
    }

    @Test
    fun dentalNoOfreceLaFila() {
        val p = proc("resina", 100.0, esp = "odonto")
        assertEquals(false, ofreceOtrosServicios(TipoTratamientoNuevo.SERVICIO_UNICO, p, MapaDental(ids = listOf("odonto"))))
        assertEquals(true, ofreceOtrosServicios(TipoTratamientoNuevo.SERVICIO_UNICO, proc("oct", 300.0), MapaDental(ids = listOf("odonto"))))
        // Clínica sin odontología: el mapa vacío NO vuelve todo dental.
        assertEquals(true, ofreceOtrosServicios(TipoTratamientoNuevo.SERVICIO_UNICO, proc("oct", 300.0), MapaDental()))
        assertEquals(false, ofreceOtrosServicios(TipoTratamientoNuevo.SESIONES, proc("oct", 300.0), MapaDental()))
    }

    @Test
    fun lineasConPrecioVacioUsanCampaniaOLista() {
        val procs = listOf(proc("oct", 300.0), proc("campi", 180.0), proc("fondo", 100.0))
        val camp = CampaniaApp(id = "c", nombre = "Nov", tipo = "porcentaje", alcance = "servicios", aplicaA = null,
            valor = 10.0, precio = null, cantidad = null, serviciosIds = listOf("fondo"))
        val principal = LineaServicio("oct", precioLista = 300.0, precioAcordado = 280.0, duracion = 20)
        val l = lineasDelFormulario(principal, listOf(
            FilaOtroServicio("campi", "", 15), FilaOtroServicio("fondo", "", 10),
            FilaOtroServicio("", "50", 10),         // sin servicio: no cuenta
            FilaOtroServicio("campi", "1", 10),     // repetido: no cuenta
        ), procs, listOf(camp))
        assertEquals(listOf("oct", "campi", "fondo"), l.map { it.procedimientoId })
        assertEquals(listOf(280.0, 180.0, 90.0), l.map { it.precioAcordado })
        assertEquals(550.0, totalLineas(l))
    }

    @Test
    fun sinFilasElegidasEsElTratamientoDeSiempre() {
        val principal = LineaServicio("oct", precioLista = 300.0, precioAcordado = 300.0, duracion = 20)
        assertEquals(emptyList(), lineasDelFormulario(principal, listOf(FilaOtroServicio()), emptyList()))
    }

    @Test
    fun precioEscritoMandaYElJsonVaEnCamelCase() {
        val l = lineasDelFormulario(
            LineaServicio("oct", precioLista = 300.0, precioAcordado = 300.0, duracion = null),
            listOf(FilaOtroServicio("campi", "150,5", 15)), listOf(proc("campi", 180.0)),
        )
        assertEquals(150.5, l[1].precioAcordado)
        assertEquals(
            """[{"procedimientoId":"oct","precioLista":300.0,"precioAcordado":300.0},{"procedimientoId":"campi","precioLista":180.0,"precioAcordado":150.5,"duracion":15}]""",
            jsonLineas(l).toString(),
        )
    }

    @Test
    fun leeLasLineasGuardadasEnOrden() {
        val t = obj("""{"servicios":[
            {"orden":1,"procedimiento_id":"campi","precio_acordado":180,"duracion":15,"procedimiento":{"nombre":"Campimetría"}},
            {"orden":0,"procedimiento_id":"oct","precio_acordado":300,"duracion":null,"procedimiento":{"nombre":"OCT"}}]}""")
        val l = TratamientoServicios.lineasGuardadas(t)
        assertEquals(listOf("oct", "campi"), l.map { it.procedimientoId })
        assertEquals(listOf(null, 15), l.map { it.duracion })
        assertEquals(480.0, totalLineas(l))
    }
}
