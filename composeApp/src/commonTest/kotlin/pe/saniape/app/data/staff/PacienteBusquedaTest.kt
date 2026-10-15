package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Buscar y registrar al paciente por nombre — los MISMOS casos que
 * `lib/__tests__/paciente-busqueda.test.ts` de la web (gemelo puro).
 */
class PacienteBusquedaTest {

    private data class P(val id: String, val nombre: String)

    // ── normalizarBusqueda ──
    @Test
    fun normalizarQuitaTildesEnieMayusculasSignosYEspacios() {
        assertEquals("maria jose", normalizarBusqueda("  MARÍA   José  "))
        assertEquals("munoz pena", normalizarBusqueda("Muñoz-Peña"))
        assertEquals("o brien", normalizarBusqueda("O'Brien"))
        assertEquals("", normalizarBusqueda(null))
    }

    // ── clasificarEntrada ──
    @Test
    fun clasificarVacio() {
        assertEquals(EntradaBusqueda.Vacio, clasificarEntrada("   "))
        assertEquals(EntradaBusqueda.Vacio, clasificarEntrada("!!"))
    }

    @Test
    fun clasificarDniCompleto() {
        assertEquals(EntradaBusqueda.Documento("44247191", true), clasificarEntrada(" 44247191 "))
    }

    @Test
    fun clasificarDigitosAMedioEscribir() {
        assertEquals(EntradaBusqueda.Documento("4424", false), clasificarEntrada("4424"))
    }

    @Test
    fun clasificarRutChileno() {
        assertEquals(EntradaBusqueda.Documento("12.345.678-k", true), clasificarEntrada("12.345.678-k"))
    }

    @Test
    fun clasificarPasaporte() {
        val e = clasificarEntrada("AB123456")
        assertTrue(e is EntradaBusqueda.Documento && e.completo)
    }

    @Test
    fun clasificarTextoComoNombre() {
        assertEquals(EntradaBusqueda.Nombre(listOf("jorge", "oli")), clasificarEntrada("Jorge  OLÍ"))
    }

    @Test
    fun unaPalabraCortaEsNombreNoPasaporte() {
        assertEquals(EntradaBusqueda.Nombre(listOf("ana")), clasificarEntrada("ana"))
    }

    @Test
    fun esDocumentoCompletoConservaLasReglas() {
        assertTrue(esDocumentoCompleto("1234567"))
        assertFalse(esDocumentoCompleto("123"))
        assertFalse(esDocumentoCompleto("maria"))
    }

    // ── puntuarNombre / rankearPorNombre ──
    private val lista = listOf(
        P("1", "JORGE YOCELYN OLIVERA GAMERO"),
        P("2", "Ana María Quispe Mamani"),
        P("3", "Ana Quispe"),
        P("4", "Mariana Anaya"),
        P("5", "Juana Quispe Flores"),
    )

    @Test
    fun todasLasPalabrasEnCualquierOrden() {
        assertTrue(puntuarNombre("JORGE YOCELYN OLIVERA GAMERO", listOf("jorge", "oli")) > 0)
        assertTrue(puntuarNombre("JORGE YOCELYN OLIVERA GAMERO", listOf("olivera", "jorge")) > 0)
        assertEquals(0, puntuarNombre("JORGE YOCELYN OLIVERA GAMERO", listOf("jorge", "perez")))
    }

    @Test
    fun sinTildesEnLosDosLados() {
        assertTrue(puntuarNombre("María José Núñez", listOf("maria", "nunez")) > 0)
    }

    @Test
    fun identicoPrimeroYPalabraCompletaAntesQueContiene() {
        val r = rankearPorNombre(lista, listOf("ana", "quispe"), 10) { it.nombre }.map { it.id }
        assertEquals("3", r[0])
        assertEquals("2", r[1])
        assertTrue("5" in r)
        assertTrue(r.indexOf("2") < r.indexOf("5"))
        assertFalse("4" in r)
    }

    @Test
    fun inicioDePalabraPuntuaMasQueContiene() {
        assertTrue(puntuarNombre("Mariana Anaya", listOf("ana")) > puntuarNombre("Juana Pérez", listOf("ana")))
    }

    @Test
    fun respetaMaximoYOrdenDeEntradaEnEmpate() {
        val r = rankearPorNombre(listOf("Luis Soto", "Luis Rojas"), listOf("luis"), 1) { it }
        assertEquals(listOf("Luis Soto"), r)
        assertEquals(emptyList(), rankearPorNombre(lista, emptyList(), 5) { it.nombre })
    }

    // ── coincidePorDocumento ──
    @Test
    fun documentoDentroDelGuardadoSinEspacios() {
        assertTrue(coincidePorDocumento("44247191", "4424 7"))
        assertTrue(coincidePorDocumento("AB123456", "ab12"))
        assertFalse(coincidePorDocumento(null, "4424"))
        assertFalse(coincidePorDocumento("44247191", ""))
    }

    // ── nombreParaAlta ──
    @Test
    fun nombreParaAltaEnTituloConTildesYParticulas() {
        assertEquals("Ana María de la Cruz", nombreParaAlta("  ana   maría DE LA cruz "))
        assertEquals("Juan O'Brien", nombreParaAlta("JUAN O'BRIEN"))
        assertEquals("Rosa Pérez-Gómez", nombreParaAlta("rosa pérez-gómez"))
    }

    @Test
    fun nombreParaAltaExigeNombreYApellido() {
        assertNull(nombreParaAlta("Ana"))
        assertNull(nombreParaAlta("Ana Q"))
        assertEquals("Ana Qu", nombreParaAlta("Ana Qu"))
    }

    @Test
    fun nombreParaAltaRechazaDigitosSimbolosYLarguisimos() {
        assertNull(nombreParaAlta("Ana 123"))
        assertNull(nombreParaAlta("Ana <b>Quispe</b>"))
        assertNull(nombreParaAlta("a".repeat(60) + " " + "b".repeat(61)))
        assertNull(nombreParaAlta(""))
    }

    // ── similitudNombres ──
    @Test
    fun mismasPalabrasEnOtroOrden() {
        assertEquals(1.0, similitudNombres("Quispe Ana", "ANA QUISPE"))
    }

    @Test
    fun seMideSobreElMasCorto() {
        assertEquals(1.0, similitudNombres("Ana Quispe", "Ana María Quispe Mamani"))
    }

    @Test
    fun toleraUnaLetraEnPalabrasDe4() {
        assertEquals(0.93, similitudNombres("Ana Quispi", "Ana Quispe"), 0.005)
    }

    @Test
    fun unaSolaPalabraEnComunDeDos() {
        assertEquals(0.5, similitudNombres("Ana Torres", "Ana Quispe"))
    }

    @Test
    fun sinPalabrasEsCero() {
        assertEquals(0.0, similitudNombres("", "Ana"))
    }

    @Test
    fun conPesosPorRarezaElNombreComunPesaPoco() {
        val nombres = listOf("Ana María Torres", "Ana María Rojas", "Ana María Soto", "María Luz Pérez", "Luis Quispe")
        val peso = pesosPorRareza(nombres)
        assertTrue(peso("maria") < peso("quispe"))
        assertTrue(similitudNombres("Ana María Quispe", "Ana María Torres", peso) < 0.6)
        assertTrue(similitudNombres("Ana María Quispe", "Ana María Torres") > 0.6)
    }

    // ── parecidosA ──
    private val fichas = listOf(
        P("a", "ANA MARIA QUISPE MAMANI"),
        P("b", "Ana María Torres"),
        P("c", "Ana Quispi"),
        P("d", "Luis Soto"),
        P("e", "Ana María Rojas"),
        P("f", "Rosa Quispe Huamán"),
    )

    @Test
    fun parecidosElMasParecidoPrimeroYNoLosDeSoloNombreDePila() {
        val r = parecidosA(fichas, "Ana Quispe") { it.nombre }
        assertEquals(listOf("a", "c"), r.map { it.paciente.id })
        assertEquals(1.0, r[0].similitud)
    }

    @Test
    fun nadaParecidoListaVacia() {
        assertEquals(emptyList(), parecidosA(fichas, "Pedro Castillo") { it.nombre })
        assertEquals(emptyList(), parecidosA(fichas, "") { it.nombre })
    }

    @Test
    fun parecidosRespetaElMaximo() {
        val muchos = (0 until 9).map { i -> "Carlos Vega " + "x".repeat(i + 2) }
        assertEquals(3, parecidosA(muchos, "Carlos Vega", max = 3) { it }.size)
    }

    // ── máscaras y edad ──
    @Test
    fun dniEnmascaradoDejaLosUltimos4() {
        assertEquals("••••1234", enmascararDni("45781234"))
        assertEquals("••23", enmascararDni("123"))
        assertNull(enmascararDni("  "))
    }

    @Test
    fun telefonoEnmascaradoDejaLosUltimos3() {
        assertEquals("••• ••• 780", enmascararTelefono("+51 940 202 780"))
        assertNull(enmascararTelefono("123"))
        assertNull(enmascararTelefono(null))
    }

    @Test
    fun edadDeLaFechaDeNacimientoSiNoLaGuardada() {
        assertEquals(35, edadPaciente("1990-10-16", 99, "2026-10-15"))
        assertEquals(36, edadPaciente("1990-10-15", null, "2026-10-15"))
        assertEquals(40, edadPaciente("basura", 40, "2026-10-15"))
        assertNull(edadPaciente(null, null, "2026-10-15"))
    }

    // ── patronIlikeSinTildes y claveDeIntento ──
    @Test
    fun vocalesYEnieComoComodin() {
        assertEquals("%m_r__%", patronIlikeSinTildes("María"))
        assertEquals("%m___z%", patronIlikeSinTildes("MUÑOZ"))
    }

    @Test
    fun elPatronEncuentraLaFormaConTilde() {
        fun aRegex(p: String) = Regex("^" + p.replace("%", ".*").replace("_", ".") + "$", RegexOption.IGNORE_CASE)
        assertTrue(aRegex(patronIlikeSinTildes("munoz")).matches("Muñoz Pérez"))
        assertTrue(aRegex(patronIlikeSinTildes("maria")).matches("ANA MARÍA"))
    }

    @Test
    fun reintentarLaMismaAltaReusaLaClave() {
        var n = 0
        val gen = { "g${++n}" }
        val a = claveDeIntento(null, "Ana  Quispe", gen)
        assertSame(a, claveDeIntento(a, "ANA QUISPE", gen))
        assertEquals(1, n)
    }

    @Test
    fun trasUnAltaExitosaUnHomonimoLlevaOtraClave() {
        var n = 0
        val gen = { "g${++n}" }
        val primera = claveDeIntento(null, "Ana Quispe", gen)
        val segunda = claveDeIntento(null, "Ana Quispe", gen)
        assertNotEquals(primera.clave, segunda.clave)
    }

    @Test
    fun otroNombreConIntentoPendienteClaveNueva() {
        var n = 0
        val gen = { "g${++n}" }
        val a = claveDeIntento(null, "Ana Quispe", gen)
        val b = claveDeIntento(a, "Rosa Quispe", gen)
        assertNotEquals(a.clave, b.clave)
        assertEquals("rosa quispe", b.nombreNorm)
    }

    // ── Solo de la app: respuesta del endpoint, reintentos y lo que se muestra ──
    @Test
    fun interpretaLaRespuestaDelBuscador() {
        val cuerpo = """{"resultados":[{"id":"p1","nombre":"Ana Quispe","estado":"Nuevo","sede_id":null,
            "tiene_dni":true,"dni_mascara":"••••1234","telefono_mascara":"••• ••• 780","edad":34,
            "ultima_cita":"2026-10-12"},{"id":"p2","nombre":"Ana Quispi","estado":"Inactivo","sede_id":"s1",
            "tiene_dni":false,"dni_mascara":null,"telefono_mascara":null,"edad":null,"ultima_cita":null,"similitud":0.93}],
            "truncado":true}"""
        val r = interpretarBusqueda(200, cuerpo)
        assertTrue(r is RespuestaBusqueda.Ok)
        assertTrue(r.truncado)
        assertEquals(2, r.resultados.size)
        val a = r.resultados[0]
        assertTrue(a.tieneDni); assertEquals("••••1234", a.dniMascara); assertEquals(34, a.edad)
        assertEquals("2026-10-12", a.ultimaCita); assertFalse(a.deBaja); assertNull(a.similitud)
        val b = r.resultados[1]
        assertTrue(b.deBaja); assertEquals("s1", b.sedeId); assertEquals(0.93, b.similitud)
    }

    @Test
    fun servidorViejoSinEndpointEsNoDisponible() {
        assertEquals(RespuestaBusqueda.NoDisponible, interpretarBusqueda(404, "<html>Not found</html>"))
        assertEquals(RespuestaBusqueda.NoDisponible, interpretarBusqueda(404, null))
        val e = interpretarBusqueda(403, """{"error":"No tienes permiso para buscar pacientes","codigo":"SIN_PERMISO"}""")
        assertEquals(RespuestaBusqueda.Error("No tienes permiso para buscar pacientes", "SIN_PERMISO"), e)
    }

    @Test
    fun soloUnNoDelServidorDescartaLaClave() {
        assertFalse(conservarIntento(400))
        assertFalse(conservarIntento(403))
        assertTrue(conservarIntento(409))
        assertTrue(conservarIntento(500))
        assertTrue(conservarIntento(0))   // sin respuesta
    }

    @Test
    fun datosDelHomonimoSoloConPermisoDeDatosPersonales() {
        val info = CoincidenciaPaciente("p1", "Ana Quispe", "Nuevo", null, true, "••••1234", "••• ••• 780", 34, "2026-10-12")
        assertEquals("DNI ••••1234 · 34 años · Tel. ••• ••• 780 · última cita 12/10/2026",
            datosHomonimo(true, false, null, info))
        assertEquals("34 años · última cita 12/10/2026", datosHomonimo(false, false, null, info))
        assertEquals("DNI ••••5678", datosHomonimo(true, false, "45675678", null))
        assertEquals("DNI 45675678", datosHomonimo(true, true, "45675678", null))
        assertEquals("sin DNI", datosHomonimo(true, false, null, null))
        assertEquals("", datosHomonimo(false, false, "45675678", null))
    }

    @Test
    fun erroresAlAgregarElDni() {
        val dup = "Ese documento ya está en otra ficha; avisa a un Admin."
        assertEquals(dup, mensajeErrorDni(409, null))
        assertEquals(dup, mensajeErrorDni(null, "duplicate key value violates unique constraint \"uq_paciente_dni_clinica\""))
        assertEquals("Modo solo lectura: no puedes modificar datos.", mensajeErrorDni(403, null))
        assertEquals("Modo solo lectura: no puedes modificar datos.", mensajeErrorDni(null, "new row violates row-level security policy"))
        assertEquals("No se pudo guardar. Intenta de nuevo.", mensajeErrorDni(500, "timeout"))
        assertTrue(MSG_SIN_PERMISO_DNI.endsWith("Pídeselo a un Admin."))
    }

    // ── La revisión de parecidos solo vale si nada cambió mientras se consultaba ──
    @Test
    fun revisionDeParecidosVigenteSoloSinCambios() {
        assertTrue(revisionParecidosVigente("Ana Quispe", " Ana Quispe ", true, null, null))
        // Cambió el texto, se cerró el buscador o ya se eligió a otro: se descarta.
        assertFalse(revisionParecidosVigente("Ana Quispe", "Ana Quispe M", true, null, null))
        assertFalse(revisionParecidosVigente("Ana Quispe", "Ana Quispe", false, null, null))
        assertFalse(revisionParecidosVigente("Ana Quispe", "Ana Quispe", true, null, "p9"))
        assertFalse(revisionParecidosVigente("Ana Quispe", "Ana Quispe", true, "p1", "p9"))
    }

    @Test
    fun endpointDeBuscarSeReintentaTrasUnTiempo() {
        assertTrue(endpointBuscarDisponible(0L, 1_000L))
        val apagado = 1_000L + REINTENTO_ENDPOINT_BUSCAR_MS
        assertFalse(endpointBuscarDisponible(apagado, 1_000L))
        assertFalse(endpointBuscarDisponible(apagado, apagado - 1))
        assertTrue(endpointBuscarDisponible(apagado, apagado))
    }
}
