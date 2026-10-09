package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DetalleCitaTest {

    // ── Detección de dirección ──

    @Test
    fun reconoceDireccionesDeDomicilio() {
        assertTrue(pareceDireccion("Av. Bolognesi 123"))
        assertTrue(pareceDireccion("Calle Zela 450, 2do piso"))
        assertTrue(pareceDireccion("Jr. Arica N° 712"))
        assertTrue(pareceDireccion("Asoc. Las Begonias Mz B Lt 4"))
        assertTrue(pareceDireccion("Urb. Bacigalupo Mz. C lote 12"))
        assertTrue(pareceDireccion("Dirección: frente al mercado Grau"))
        assertTrue(pareceDireccion("Domicilio: Pasaje Los Olivos s/n"))
        assertTrue(pareceDireccion("https://maps.app.goo.gl/AbCd123"))
    }

    @Test
    fun noConfundeTextoClinicoConDireccion() {
        assertFalse(pareceDireccion("Dolor lumbar hace 3 días"))
        assertFalse(pareceDireccion("Se cayó en la calle hace 2 semanas"))
        assertFalse(pareceDireccion("Camina 5 km diarios"))
        assertFalse(pareceDireccion("Traer RX y resonancia"))
        assertFalse(pareceDireccion("Paciente avisa que llega tarde"))
        assertFalse(pareceDireccion("Mejorar la dirección de la marcha"))
        assertFalse(pareceDireccion(""))
    }

    @Test
    fun extraeLaDireccionSinEtiquetaDeUnTextoConVariasLineas() {
        val t = "Atención a domicilio\nDirección: Av. Pinto 1450 (ref. frente al parque)\nTocar el timbre 2 veces"
        assertEquals("Av. Pinto 1450 (ref. frente al parque)", direccionEnTexto(t))
        // Los " · " con que la web une el motivo y las notas también separan tramos.
        assertEquals("Calle Deustua 220", direccionEnTexto("Motivo: lumbalgia · Calle Deustua 220."))
        assertNull(direccionEnTexto("Motivo: lumbalgia · trae RX"))
    }

    @Test
    fun laBusquedaEnMapsVaSinReferenciaYCodificada() {
        assertEquals("Av. Pinto 1450", consultaMaps("Av. Pinto 1450 (ref. frente al parque)"))
        assertEquals("Av. Grau 450", consultaMaps("Av. Grau 450, ref: casa verde"))
        assertEquals("Calle Zela 450", consultaMaps("Calle Zela 450"))
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=Av.%20Pinto%201450",
            urlMapsDe("Av. Pinto 1450 (ref. parque)", "Av. Pinto 1450 (ref. parque)"),
        )
        // Si ya vino un enlace de Maps, se abre ese.
        assertEquals("https://maps.app.goo.gl/AbCd123",
            urlMapsDe("Ubicación: https://maps.app.goo.gl/AbCd123", "https://maps.app.goo.gl/AbCd123"))
    }

    // ── Textos ──

    @Test
    fun verMasYBreve() {
        assertFalse(necesitaVerMas("Corta"))
        assertTrue(necesitaVerMas("a\nb\nc\nd\ne"))
        assertTrue(necesitaVerMas("x".repeat(221)))
        assertEquals("Movilización · Láser", breve("Movilización\nLáser"))
        assertNull(breve("   "))
        val largo = breve("palabra ".repeat(40), 50)!!
        assertTrue(largo.endsWith("…") && largo.length <= 51)
    }

    // ── Armado de bloques y permisos ──

    private val completo = DatosDetalleCita(
        notas = "Domicilio: Av. Bolognesi 123",
        diagnosticoCita = null,
        diagnosticoTratamiento = "Lumbalgia mecánica",
        sesionNumero = 4,
        sesionNotas = "Movilización lumbar",
        sesionMejorias = "Menos dolor al sentarse",
    )

    @Test
    fun sinDatosNoSePintaNada() {
        assertTrue(bloquesDetalleCita("Sesión", false, null, verClinico = true).isEmpty())
        assertTrue(bloquesDetalleCita("Sesión", false, DatosDetalleCita(), verClinico = true).isEmpty())
    }

    @Test
    fun recepcionSoloVeLasObservaciones() {
        val b = bloquesDetalleCita("Sesión", false, completo, verClinico = false)
        assertEquals(1, b.size)
        val obs = assertIs<BloqueDetalleCita.Observaciones>(b[0])
        assertEquals("Av. Bolognesi 123", obs.direccion)
        assertTrue(obs.urlMaps!!.startsWith("https://www.google.com/maps/search/?api=1&query=Av.%20Bolognesi"))
    }

    @Test
    fun sesionMuestraSusNotasYNoElDiagnosticoDelTratamiento() {
        val b = bloquesDetalleCita("Sesión", false, completo, verClinico = true)
        assertEquals(2, b.size)
        val n = assertIs<BloqueDetalleCita.NotasSesion>(b[1])
        assertEquals(4, n.numero)
        assertEquals("Movilización lumbar", n.tecnicas)
        assertEquals("Menos dolor al sentarse", n.evolucion)
    }

    @Test
    fun evaluacionMuestraElDiagnostico() {
        val datos = completo.copy(notas = "Trae RX", sesionNotas = null, sesionMejorias = null)
        val b = bloquesDetalleCita("Evaluación", true, datos, verClinico = true)
        assertEquals(listOf("Observaciones", "Diagnostico"), b.map { it::class.simpleName })
        assertEquals("Lumbalgia mecánica", (b[1] as BloqueDetalleCita.Diagnostico).texto)
        assertNull((b[0] as BloqueDetalleCita.Observaciones).direccion)
        // El de la propia cita manda sobre el del tratamiento, en cualquier tipo.
        val propio = bloquesDetalleCita("Consulta", false, datos.copy(diagnosticoCita = "Cervicalgia"), verClinico = true)
        assertEquals("Cervicalgia", (propio[1] as BloqueDetalleCita.Diagnostico).texto)
    }

    @Test
    fun quienVeLoClinico() {
        assertTrue(veClinicoEnDetalleCita(true, esAdmin = true, miTerapeutaId = null, modoClinico = false))
        assertTrue(veClinicoEnDetalleCita(true, esAdmin = false, miTerapeutaId = "t1", modoClinico = false))
        assertTrue(veClinicoEnDetalleCita(true, esAdmin = false, miTerapeutaId = null, modoClinico = true))
        // Recepción: gestiona pacientes, sin ficha de profesional.
        assertFalse(veClinicoEnDetalleCita(true, esAdmin = false, miTerapeutaId = null, modoClinico = false))
        assertFalse(veClinicoEnDetalleCita(false, esAdmin = true, miTerapeutaId = "t1", modoClinico = true))
    }

    // ── Lectura ──

    @Test
    fun columnasYParseo() {
        assertEquals("notas", columnasDetalleCita(false))
        assertTrue(columnasDetalleCita(true).contains("diagnostico"))
        val o = Json.parseToJsonElement(
            """{"notas":"Av. Grau 1","diagnostico":null,"tratamiento":{"diagnostico":"Lumbalgia"},""" +
                """"sesion":{"numero":3,"notas":"Láser","mejorias":""}}""",
        ) as JsonObject
        val d = parsearDetalleCita(o)
        assertEquals("Av. Grau 1", d.notas)
        assertNull(d.diagnosticoCita)
        assertEquals("Lumbalgia", d.diagnosticoTratamiento)
        assertEquals(3, d.sesionNumero)
        assertEquals("Láser", d.sesionNotas)
        assertNull(d.sesionMejorias)
    }
}
