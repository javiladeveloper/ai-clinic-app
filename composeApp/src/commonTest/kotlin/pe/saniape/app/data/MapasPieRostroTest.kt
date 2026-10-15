package pe.saniape.app.data

import pe.saniape.app.data.staff.AplicacionEstetica
import pe.saniape.app.data.staff.Aplicacion
import pe.saniape.app.data.staff.ComandoTrazo
import pe.saniape.app.data.staff.EvaluacionPodologica
import pe.saniape.app.data.staff.FormaMapa
import pe.saniape.app.data.staff.HallazgoMarcado
import pe.saniape.app.data.staff.PieGeometria
import pe.saniape.app.data.staff.RostroGeometria
import pe.saniape.app.data.staff.TIPOS_PRODUCTO
import pe.saniape.app.data.staff.ZONAS_FACIALES
import pe.saniape.app.data.staff.ZONAS_PODO
import pe.saniape.app.data.staff.ZonaMarcadaPodo
import pe.saniape.app.data.staff.htmlEvaluacionPodo
import pe.saniape.app.data.staff.htmlRegistroAplicacion
import pe.saniape.app.data.staff.tamizajeVacio
import pe.saniape.app.data.staff.totalesPorZona
import pe.saniape.app.data.staff.trazosSvg
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Los mapas tocables: paridad de la geometría con el SVG de la web (cada zona
 * del catálogo tiene su dibujo), el toque (centro → su zona, espejo, áreas de
 * toque) y las hojas imprimibles (escapadas, con los textos de la web).
 */
class MapasPieRostroTest {

    // ── Pie ──

    @Test fun cadaZonaDelPieTieneSuDibujo() {
        for (z in ZONAS_PODO) assertNotNull(PieGeometria.formasDeZona(z.id), z.id)
        assertEquals(8, PieGeometria.zonasDe("plantar", "der").size)
        assertEquals(9, PieGeometria.zonasDe("dorsal", "der").size)
        // Los dedos y el dorso se dibujan primero: las uñas quedan encima.
        assertEquals(listOf("dorso_dedos", "dorso"), PieGeometria.zonasDe("dorsal", "der").take(2).map { it.id })
    }

    @Test fun tocarElCentroDeCadaZonaPlantar() {
        assertEquals("talon", PieGeometria.zonaEn("plantar", "der", 45f, 178f))
        assertEquals("hallux", PieGeometria.zonaEn("plantar", "der", 72f, 28f))
        assertEquals("met1", PieGeometria.zonaEn("plantar", "der", 70f, 66f))
        assertEquals("arco", PieGeometria.zonaEn("plantar", "der", 67f, 110f))
        assertNull(PieGeometria.zonaEn("plantar", "der", 2f, 2f))
    }

    @Test fun elPieIzquierdoEsElDerechoEnEspejo() {
        // El hallux derecho está a la derecha (x 72); en el izquierdo, a la izquierda (100 − 72).
        assertEquals("hallux", PieGeometria.zonaEn("plantar", "izq", 28f, 28f))
        assertEquals("met1", PieGeometria.zonaEn("plantar", "izq", 30f, 66f))
        assertEquals("met5", PieGeometria.zonaEn("plantar", "izq", 75f, 72f))
    }

    @Test fun lasUnasGananAlDedoYTienenAreaDeToque() {
        assertEquals("una1", PieGeometria.zonaEn("dorsal", "der", 72f, 18f))
        // Fuera del dibujo de la uña 2 pero dentro de su área de toque (como la web).
        assertEquals("una2", PieGeometria.zonaEn("dorsal", "der", 52f, 23f))
        assertEquals("dorso_dedos", PieGeometria.zonaEn("dorsal", "der", 39f, 33f))
        assertEquals("interdigital", PieGeometria.zonaEn("dorsal", "der", 61f, 40f))
        assertEquals("dorso", PieGeometria.zonaEn("dorsal", "der", 47f, 120f))
    }

    // ── Rostro ──

    @Test fun cadaZonaDelRostroTieneSuDibujoYCadaTipoSuColor() {
        for (z in ZONAS_FACIALES) assertNotNull(RostroGeometria.formaDeZona(z.id), z.id)
        for (t in TIPOS_PRODUCTO) assertTrue(Regex("^#[0-9a-f]{6}$", RegexOption.IGNORE_CASE).matches(RostroGeometria.colorTipo(t.id)))
    }

    @Test fun tocarElRostro() {
        assertEquals("glabela", RostroGeometria.zonaEn("frontal", 120f, 100f))
        // La derecha del PACIENTE queda a la izquierda de quien mira.
        assertEquals("patas_gallo_der", RostroGeometria.zonaEn("frontal", 55f, 122f))
        assertEquals("patas_gallo_izq", RostroGeometria.zonaEn("frontal", 185f, 122f))
        // Zona chica: un toque cerca (fuera del dibujo) la encuentra por su área de toque.
        assertEquals("codigo_barras", RostroGeometria.zonaEn("frontal", 120f, 180f))
        assertNull(RostroGeometria.zonaEn("frontal", 5f, 5f))
    }

    @Test fun elAreaDeToqueRespetaElMinimoPedido() {
        // Con un radio mínimo mayor (rostro dibujado chico), la zona se alcanza desde más lejos.
        assertNull(RostroGeometria.zonaEn("frontal", 144f, 226f))
        assertEquals("comisura_izq", RostroGeometria.zonaEn("frontal", 144f, 226f, radioMin = 16f))
    }

    @Test fun perfilIzquierdoEnEspejo() {
        assertEquals("punta_nasal", RostroGeometria.zonaEn("lateral_der", 198f, 165f))
        assertEquals("punta_nasal", RostroGeometria.zonaEn("lateral_izq", 240f - 198f, 165f))
        assertEquals("angulo_mandibular_izq", RostroGeometria.zonaEn("lateral_izq", 240f - 112f, 236f))
        assertEquals("angulo_mandibular_der", RostroGeometria.zonaEn("lateral_der", 112f, 236f))
    }

    @Test fun elipseRotadaSeTocaEnSuEje() {
        val e = FormaMapa.Elipse(0f, 0f, 10f, 2f, rot = 90f)
        assertTrue(e.contiene(0f, 9f))
        assertFalse(e.contiene(9f, 0f))
    }

    @Test fun trazosSvgAbsolutos() {
        val t = trazosSvg("M86 258 L82 318 M154 258 L158 318")
        assertEquals(listOf(ComandoTrazo.Mover(86f, 258f), ComandoTrazo.Linea(82f, 318f), ComandoTrazo.Mover(154f, 258f), ComandoTrazo.Linea(158f, 318f)), t)
        val s = trazosSvg(PieGeometria.SILUETA)
        assertEquals(ComandoTrazo.Mover(44f, 204f), s.first())
        assertEquals(ComandoTrazo.Cerrar, s.last())
        assertEquals(7, s.count { it is ComandoTrazo.Curva })
        for (d in RostroGeometria.FRONTAL_TRAZOS + RostroGeometria.PERFIL_TRAZOS + RostroGeometria.FRONTAL_CONTORNO) {
            assertTrue(trazosSvg(d).size >= 2, d)
        }
    }

    // ── Hojas imprimibles ──

    @Test fun informePodologicoEscapadoYConRiesgoSinDeterminar() {
        val ev = EvaluacionPodologica(
            "e", "p", fecha = "2026-10-15", tamizaje = tamizajeVacio(true), riesgoIwgdf = null,
            zonas = listOf(ZonaMarcadaPodo("der", "plantar", "talon", listOf(HallazgoMarcado("ulcera", 2)))),
            observaciones = "<script>x</script>",
        )
        val html = htmlEvaluacionPodo(ev, null, "Podo <Black>", null, "http://inseguro/logo.png", "rojo", "Ana", "123", "Dra. Ruiz")
        assertTrue(html.contains("EVALUACIÓN PODOLÓGICA"))
        assertTrue(html.contains("15/10/2026"))
        assertTrue(html.contains("Riesgo de pie diabético sin determinar"))
        assertFalse(html.contains("Control recomendado"))
        assertTrue(html.contains("Úlcera activa"))
        assertTrue(html.contains("Podo &lt;Black&gt;"))
        assertFalse(html.contains("<script>x"))
        assertFalse(html.contains("http://inseguro"))
        assertTrue(html.contains("#2c3e7a"))
        assertTrue(html.contains("scale(-1,1)"))
        assertTrue(html.contains("#dc2626"))
    }

    @Test fun registroDeAplicacionConLoteYVencido() {
        val a = AplicacionEstetica(
            "a", "p", fecha = "2026-10-15", vista = "frontal",
            aplicaciones = listOf(
                Aplicacion("glabela", "Botox", "toxina", 20.0, "U", 5, "aguja", "intramuscular", "C7254AC4", "2027-03", null),
                Aplicacion("labio_superior", "Juvéderm", "acido_hialuronico", 0.5, "ml", null, "canula", null, "J1", "2026-01", null),
            ),
        )
        val html = htmlRegistroAplicacion(a, "GLAM", null, "https://x/logo.png", null, "Ana Pérez", null, "Dra. Ruiz", "Toxina")
        assertTrue(html.contains("REGISTRO DE APLICACIÓN"))
        assertTrue(html.contains("15/10/2026"))
        assertTrue(html.contains("https://x/logo.png"))
        assertTrue(html.contains("Ana Pérez"))
        assertTrue(html.contains("C7254AC4"))
        assertTrue(html.contains("2027-03"))
        assertTrue(html.contains("nw vencido\">2026-01"))
        assertTrue(html.contains("20 U · 0,5 ml"))
        assertTrue(html.contains("Entrecejo (glabela)"))
        assertTrue(html.contains(">Der.<"))
        assertTrue(html.contains(">20 U<"))
        assertEquals("20 U", totalesPorZona(a.aplicaciones).getValue("glabela").texto)
    }
}
