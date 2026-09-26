package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * "Novedades de la versión": el archivo novedades/<version>.txt se lee igual en
 * la app que en Play, y el diálogo sale UNA vez tras actualizar — nunca en una
 * instalación nueva ni dos veces para la misma versión.
 */
class NovedadesTest {

    private val archivo = """
        # comentario que no se publica
        Resumen corto para Play.
        ---
        Odontología:
        • Sumar caras.
        - Dictado manos libres.
        Arreglos:
        • Fecha de Lima.
    """.trimIndent()

    @Test
    fun la_app_muestra_el_detalle_con_titulos_y_vinetas() {
        val l = Novedades.parsear(archivo)
        assertEquals(
            listOf(
                Novedades.Linea("Odontología:", esTitulo = true),
                Novedades.Linea("Sumar caras.", esTitulo = false),
                Novedades.Linea("Dictado manos libres.", esTitulo = false),
                Novedades.Linea("Arreglos:", esTitulo = true),
                Novedades.Linea("Fecha de Lima.", esTitulo = false),
            ),
            l,
        )
    }

    @Test
    fun sin_detalle_muestra_el_resumen_y_sin_comentarios() {
        val l = Novedades.parsear("# nota interna\n• Una mejora.\n\nOtra línea suelta.")
        assertEquals(listOf("Una mejora.", "Otra línea suelta."), l.map { it.texto })
        assertTrue(l.none { it.esTitulo })
    }

    @Test
    fun las_notas_de_la_proxima_version_se_embeben() {
        // El archivo real del repo llega a la app vía NovedadesGeneradas.
        val n = Novedades.de("2.15.1")
        assertTrue(n != null && n.lineas.isNotEmpty(), "novedades/2.15.1.txt no quedó embebido")
    }

    @Test
    fun se_muestra_una_vez_tras_actualizar() {
        // Viene de una versión anterior (o de antes de que existiera el aviso).
        assertTrue(Novedades.debeMostrar("2.15.0", "2.15.1", instalacionNueva = false, hayNotas = true))
        assertTrue(Novedades.debeMostrar(null, "2.15.1", instalacionNueva = false, hayNotas = true))
        // Ya la vio.
        assertFalse(Novedades.debeMostrar("2.15.1", "2.15.1", instalacionNueva = false, hayNotas = true))
    }

    @Test
    fun no_se_muestra_en_instalacion_nueva_ni_sin_notas() {
        assertFalse(Novedades.debeMostrar(null, "2.15.1", instalacionNueva = true, hayNotas = true))
        assertFalse(Novedades.debeMostrar("2.15.0", "2.15.1", instalacionNueva = false, hayNotas = false))
    }

    @Test
    fun compara_versiones_por_numero() {
        assertTrue(Novedades.compararVersiones("2.15.10", "2.15.9") > 0)
        assertTrue(Novedades.compararVersiones("2.9.0", "2.15.0") < 0)
        assertEquals(0, Novedades.compararVersiones("2.15.1", "2.15.1"))
    }
}
