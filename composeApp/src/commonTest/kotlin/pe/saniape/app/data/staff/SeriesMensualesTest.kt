package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reportes "Mes a mes": parseo de /api/reportes/series y lo que se arma para pintar. */
class SeriesMensualesTest {

    private val respuesta = """
        {
          "hoy": "2026-10-08",
          "meses": 3,
          "series": {
            "pacientes": [
              {"mes":"2026-08","etiqueta":"Ago","valor":40,"parcial":false},
              {"mes":"2026-09","etiqueta":"Sep","valor":52,"parcial":false},
              {"mes":"2026-10","etiqueta":"Oct","valor":11,"parcial":true,"diasTranscurridos":8,
               "diasDelMes":31,"proyectado":43,"mismoTramoAnterior":7}
            ],
            "citas": [
              {"mes":"2026-08","etiqueta":"Ago","valor":300,"parcial":false},
              {"mes":"2026-09","etiqueta":"Sep","valor":320,"parcial":false},
              {"mes":"2026-10","etiqueta":"Oct","valor":90,"parcial":true,"diasTranscurridos":8,"diasDelMes":31}
            ],
            "sesiones": [],
            "ingresos": [
              {"mes":"2026-08","etiqueta":"Ago","valor":12000.5,"parcial":false},
              {"mes":"2026-09","etiqueta":"Sep","valor":15000,"parcial":false},
              {"mes":"2026-10","etiqueta":"Oct","valor":3000,"parcial":true}
            ],
            "egresos": [
              {"mes":"2026-08","etiqueta":"Ago","valor":5000,"parcial":false},
              {"mes":"2026-09","etiqueta":"Sep","valor":16000.25,"parcial":false},
              {"mes":"2026-10","etiqueta":"Oct","valor":100,"parcial":true}
            ]
          },
          "hitos": [
            {"id":"h1","mes":"2026-09","titulo":"Promos; 2x1","detalle":null,"tipo":"promocion"},
            {"id":"h2","mes":"2026-09","titulo":"Feriado","detalle":"","tipo":"feriado"}
          ]
        }
    """.trimIndent()

    @Test
    fun parseaSeriesYHitos() {
        val s = parsearSeriesReporte(respuesta)
        assertEquals("2026-10-08", s.hoy)
        assertEquals(3, s.pacientes.size)
        val oct = s.pacientes.last()
        assertTrue(oct.parcial)
        assertEquals(11.0, oct.valor)
        assertEquals(43.0, oct.proyectado)
        assertEquals(7.0, oct.mismoTramoAnterior)
        assertEquals(8, oct.diasTranscurridos)
        assertEquals(31, oct.diasDelMes)
        assertNull(s.pacientes.first().proyectado)
        assertTrue(s.sesiones.isEmpty())
        assertEquals(2, s.hitos.size)
        assertNull(s.hitos[0].detalle)
        assertNull(s.hitos[1].detalle, "un detalle vacío no se muestra")
    }

    @Test
    fun respuestaVaciaNoRompe() {
        val s = parsearSeriesReporte("""{"series":{},"hitos":null}""")
        assertTrue(s.pacientes.isEmpty())
        assertTrue(s.hitos.isEmpty())
        assertNull(s.hoy)
        assertNull(titularDe(s.pacientes))
    }

    @Test
    fun variacionSinBaseEsNull() {
        assertNull(variacionTramo(5.0, 0.0))
        assertEquals(57, variacionTramo(11.0, 7.0))
        assertEquals(-50, variacionTramo(5.0, 10.0))
    }

    @Test
    fun titularCallaElPorcentajeConPocosDias() {
        // Día 8: menos de 10 días corridos → no hay %, se muestra el número del tramo anterior.
        val t = titularDe(parsearSeriesReporte(respuesta).pacientes)!!
        assertTrue(t.parcial)
        assertNull(t.variacion)
        assertEquals(7.0, t.mismoTramoAnterior)
        assertEquals(43.0, t.proyectado)
    }

    @Test
    fun titularMuestraElPorcentajeCuandoSignificaAlgo() {
        val serie = listOf(
            PuntoMes("2026-10", "Oct", 30.0, parcial = true, mismoTramoAnterior = 20.0, diasTranscurridos = 15, diasDelMes = 31),
        )
        assertEquals(50, titularDe(serie)!!.variacion)
        // Base chica (< 5): ruido.
        assertNull(titularDe(listOf(serie[0].copy(mismoTramoAnterior = 2.0)))!!.variacion)
        // Mes en cero: "▼100%" asusta sin decir nada.
        assertNull(titularDe(listOf(serie[0].copy(valor = 0.0)))!!.variacion)
    }

    @Test
    fun balanceEsDelUltimoMesCerrado() {
        val s = parsearSeriesReporte(respuesta)
        val b = balanceUltimoCerrado(s.ingresos, s.egresos)!!
        assertEquals("Sep", b.etiqueta)
        assertEquals(-1000.25, b.balance)
        assertNull(balanceUltimoCerrado(listOf(PuntoMes("2026-10", "Oct", 1.0, true)), emptyList()))
    }

    @Test
    fun formatos() {
        assertEquals("1,205", entero(1205.0))
        assertEquals("0", entero(0.0))
        assertEquals("S/ 28,349", solesGrafico(28349.0))
        assertEquals("S/ 28.3k", solesGrafico(28349.0, compacto = true))
        assertEquals("S/ 2k", solesGrafico(2000.0, compacto = true))
        assertEquals("S/ 950", solesGrafico(950.0, compacto = true))
        assertEquals("Oct", etiquetaMes("2026-10"))
        assertEquals("Dic 25", etiquetaMes("2025-12", 2026))
        assertEquals("Jul 2026", mesConAnio("2026-07"))
    }

    @Test
    fun marcasDelEjeSonRedondas() {
        assertEquals(listOf(0.0, 1.0), marcasEje(0.0))
        assertEquals(listOf(0.0, 1.0, 2.0, 3.0), marcasEje(3.0))
        val m = marcasEje(93.0)
        assertEquals(0.0, m.first())
        assertTrue(m.last() >= 93.0)
        assertTrue(m.size in 3..7)
        val dinero = marcasEje(15000.0)
        assertTrue(dinero.last() >= 15000.0)
        assertEquals(dinero[1] - dinero[0], dinero[2] - dinero[1])
    }

    @Test
    fun toqueYEtiquetasDelEje() {
        // 12 meses en 240 px desde x=40: cada mes ocupa 20 px.
        assertEquals(0, indiceTocado(41f, 40f, 240f, 12))
        assertEquals(11, indiceTocado(279f, 40f, 240f, 12))
        assertEquals(5, indiceTocado(150f, 40f, 240f, 12))
        assertNull(indiceTocado(10f, 40f, 240f, 12))
        assertNull(indiceTocado(300f, 40f, 240f, 12))
        // 24 meses a 240 px con etiquetas de 30 px: una de cada 3, siempre la última.
        val paso = pasoEtiquetas(24, 240f, 30f)
        assertEquals(3, paso)
        assertTrue(muestraEtiqueta(23, 24, paso))
        assertFalse(muestraEtiqueta(22, 24, paso))
        assertTrue(muestraEtiqueta(20, 24, paso))
        assertEquals(1, pasoEtiquetas(6, 300f, 30f))
    }

    @Test
    fun csvComoElDeLaWeb() {
        val csv = csvReporteMensual(parsearSeriesReporte(respuesta))
        assertTrue(csv.startsWith("﻿"), "BOM para que Excel respete los acentos")
        val lineas = csv.removePrefix("﻿").split("\n")
        assertEquals("Mes;Estado;Pacientes;Citas atendidas;Sesiones;Ingresos;Egresos;Qué pasó", lineas[0])
        assertEquals("2026-08;Cerrado;40;300;0;12000.5;5000;", lineas[1])
        // El ';' dentro de un hito va entre comillas para no romper la columna.
        assertEquals("2026-09;Cerrado;52;320;0;15000;16000.25;\"Promos; 2x1 / Feriado\"", lineas[2])
        assertEquals("2026-10;En curso;11;90;0;3000;100;", lineas[3])
        assertEquals("reporte-mensual-2026-10-08.csv", nombreCsvReporte("2026-10-08"))
    }

    @Test
    fun numerosDelCsv() {
        assertEquals("93", numeroCsv(93.0))
        assertEquals("1234.5", numeroCsv(1234.5))
        assertEquals("0.05", numeroCsv(0.05))
        assertEquals("-0.5", numeroCsv(-0.5))
        assertEquals("12345678", numeroCsv(12345678.0))
    }

    @Test
    fun tiposDeHito() {
        assertEquals("🎉", emojiHito("promocion"))
        assertEquals("📌", emojiHito("desconocido"))
        assertEquals(4, TIPOS_HITO.size)
    }
}
