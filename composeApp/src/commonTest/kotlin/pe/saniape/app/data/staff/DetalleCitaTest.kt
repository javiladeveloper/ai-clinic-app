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
        // Cargando o sin red: el detalle muestra su aviso, no bloques.
        assertTrue(bloquesDetalleCita("Sesión", false, null, verClinico = true).isEmpty())
    }

    // ── Observaciones siempre visibles + editables ──

    @Test
    fun observacionesVaciasSeVenIgual() {
        val b = bloquesDetalleCita("Sesión", false, DatosDetalleCita(), verClinico = true)
        assertEquals(1, b.size)
        val obs = assertIs<BloqueDetalleCita.Observaciones>(b[0])
        assertEquals("", obs.texto)
        assertFalse(obs.mostrarTexto)
        assertEquals("Sin observaciones", obs.textoVacio)
        assertEquals("Agregar", obs.accion)
        assertNull(obs.direccion)
        // Solo espacios = vacías.
        assertEquals("Sin observaciones", bloqueObservaciones("   \n ").textoVacio)
    }

    @Test
    fun observacionesConTextoSeEditan() {
        val obs = bloqueObservaciones("Trae RX de columna")
        assertTrue(obs.mostrarTexto)
        assertNull(obs.textoVacio)
        assertEquals("Editar", obs.accion)
    }

    @Test
    fun soloLaDireccionDiceSinOtrasObservaciones() {
        val obs = bloqueObservaciones("Av. Bolognesi 123.")
        assertEquals("Av. Bolognesi 123", obs.direccion)
        assertFalse(obs.mostrarTexto)
        assertEquals("Sin otras observaciones", obs.textoVacio)
        assertEquals("Editar", obs.accion)
        // Dirección + algo más: se ve el texto y no hay "Sin otras…".
        val mixta = bloqueObservaciones("Av. Bolognesi 123\nTocar el timbre 2 veces")
        assertTrue(mixta.mostrarTexto)
        assertNull(mixta.textoVacio)
    }

    @Test
    fun accionYPermisoComoLaWeb() {
        assertEquals("Agregar", accionObservaciones(null))
        assertEquals("Agregar", accionObservaciones("  "))
        assertEquals("Editar", accionObservaciones("x"))
        assertTrue(puedeEditarObservaciones(puedeCitas = true))
        assertFalse(puedeEditarObservaciones(puedeCitas = false))
        assertFalse(puedeEditarObservaciones(puedeCitas = true, soloLectura = true))
    }

    @Test
    fun normalizaYArmaElCuerpo() {
        assertNull(normalizarNotasCita(null))
        assertNull(normalizarNotasCita("  \n  "))
        assertEquals("Línea 1\nLínea 2", normalizarNotasCita("  Línea 1\r\nLínea 2 \n"))
        val c = cuerpoNotasCita("c-1", "  Av. Grau 1 ")
        assertEquals("""{"citaId":"c-1","notas":"Av. Grau 1"}""", c.toString())
        // Vacío = borrar: notas null explícito.
        assertEquals("""{"citaId":"c-1","notas":null}""", cuerpoNotasCita("c-1", "").toString())
        assertEquals(2000, OBSERVACIONES_MAX)
    }

    @Test
    fun parseaLaRespuestaOk() {
        assertEquals(ResultadoNotasCita.Ok("Av. Grau 1"),
            parsearRespuestaNotasCita(200, """{"ok":true,"notas":"Av. Grau 1"}"""))
        assertEquals(ResultadoNotasCita.Ok(null), parsearRespuestaNotasCita(200, """{"ok":true,"notas":null}"""))
        assertEquals(ResultadoNotasCita.Ok(null), parsearRespuestaNotasCita(200, """{"ok":true}"""))
    }

    @Test
    fun parseaLosErroresDelServidor() {
        assertEquals(ResultadoNotasCita.Error("Cita no encontrada", "CITA_NO_ENCONTRADA"),
            parsearRespuestaNotasCita(404, """{"error":"Cita no encontrada","codigo":"CITA_NO_ENCONTRADA"}"""))
        assertEquals(ResultadoNotasCita.Error("Modo solo lectura: no puedes modificar datos", "SOLO_LECTURA"),
            parsearRespuestaNotasCita(403, """{"error":"Modo solo lectura: no puedes modificar datos","codigo":"SOLO_LECTURA"}"""))
        val firmada = parsearRespuestaNotasCita(409, """{"error":"La atención ya está firmada","codigo":"ATENCION_FIRMADA"}""")
        assertEquals("ATENCION_FIRMADA", (firmada as ResultadoNotasCita.Error).codigo)
        assertEquals("NOTAS_MUY_LARGAS", (parsearRespuestaNotasCita(400,
            """{"error":"Las observaciones no pueden pasar de 2000 caracteres","codigo":"NOTAS_MUY_LARGAS"}""")
            as ResultadoNotasCita.Error).codigo)
    }

    @Test
    fun servidorViejoSinEndpointNoRompe() {
        // Un servidor sin el endpoint responde 404/405 con HTML o vacío: error genérico.
        assertEquals(ResultadoNotasCita.Error(ERROR_GUARDAR_OBSERVACIONES),
            parsearRespuestaNotasCita(404, "<!DOCTYPE html><html>404</html>"))
        assertEquals(ResultadoNotasCita.Error(ERROR_GUARDAR_OBSERVACIONES), parsearRespuestaNotasCita(405, ""))
        assertEquals(ResultadoNotasCita.Error(ERROR_GUARDAR_OBSERVACIONES), parsearRespuestaNotasCita(500, null))
        assertEquals("Tu sesión expiró. Vuelve a entrar.",
            (parsearRespuestaNotasCita(401, "") as ResultadoNotasCita.Error).mensaje)
        // 200 que no es JSON tampoco cuenta como guardado.
        assertIs<ResultadoNotasCita.Error>(parsearRespuestaNotasCita(200, "<html></html>"))
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
        // Igual que la web: lo decide el permiso `sesiones`.
        assertTrue(veClinicoEnDetalleCita(puedeSesiones = true))
        assertFalse(veClinicoEnDetalleCita(puedeSesiones = false))
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
