package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La colegiatura por rubro: gemela de lib/colegiatura.ts (mismos casos que
 * lib/__tests__/colegiatura.test.ts). Una sola función para receta, informe y
 * consulta guiada; nunca "CMP" fijo.
 */
class ColegiaturaTest {

    private fun sigla(vararg esps: EspecialidadColegio, pais: String? = null) = colegioDe(esps.toList(), pais)?.sigla
    private fun r(rubro: String?, nombre: String? = null) = EspecialidadColegio(rubro, nombre)

    @Test fun cadaRubroConSuColegio() {
        assertEquals("C.Ps.P.", sigla(r("psicologia")))
        assertEquals("CMP", sigla(r("medicina_general")))
        assertEquals("CMP", sigla(r("ginecologia")))
        assertEquals("COP", sigla(r("odontologia")))
        assertEquals("CTMP", sigla(r("fisioterapia")))
        assertEquals("CNP", sigla(r("nutricion")))
        assertEquals("CMVP", sigla(r("veterinaria")))
    }

    @Test fun esteticaCapilarOtroOSinEspecialidadesNoSeAdivina() {
        assertNull(sigla(r("estetica")))
        assertNull(sigla(r("capilar")))
        assertNull(sigla(r("otro")))
        assertNull(sigla())
        assertNull(colegioDe(null))
    }

    @Test fun elPsiquiatraEsMedico() {
        assertEquals("CMP", sigla(r("medicina_general", "Psiquiatría")))
        assertEquals("CMP", sigla(r("psicologia"), r("medicina_general")))
    }

    @Test fun medicoYDentistaCmpSoloDentalCop() {
        assertEquals("CMP", sigla(r("odontologia"), r("medicina_general")))
        assertEquals("COP", sigla(r("odontologia"), r("estetica")))
    }

    @Test fun sinRubroGuardadoLoDeduceDelNombre() {
        assertEquals("C.Ps.P.", sigla(r(null, "Psicología")))
        assertEquals("CTMP", sigla(r(null, "Fisioterapia")))
    }

    @Test fun fueraDePeruNoInventaUnColegioPeruano() {
        assertNull(sigla(r("psicologia"), pais = "BO"))
        assertEquals("C.Ps.P.", sigla(r("psicologia"), pais = "PE"))
    }

    @Test fun mostrarSoloElNumeroLlevaElColegio() {
        val psico = colegioDe(listOf(r("psicologia")))
        assertEquals("C.Ps.P. 12345", colegiaturaParaMostrar("12345", psico))
        assertEquals("C.Ps.P. 999", colegiaturaParaMostrar(" C.Ps.P. 999 ", psico))
        assertEquals("CPsP 999", colegiaturaParaMostrar("CPsP 999", psico))
        assertEquals("12345", colegiaturaParaMostrar("12345", null))
        assertEquals("", colegiaturaParaMostrar("", psico))
        assertEquals("", colegiaturaParaMostrar(null, psico))
    }

    @Test fun elAtajoPorRubrosNuncaPoneCmpFijo() {
        assertEquals("CNP 321", colegiaturaDe("321", listOf("nutricion")))
        assertEquals("C.Ps.P. 321", colegiaturaDe("321", listOf("psicologia")))
        assertEquals("COP 321", colegiaturaDe("321", listOf("odontologia")))
        // Sin rubro conocido: el número tal cual (antes la receta le ponía "CMP").
        assertEquals("321", colegiaturaDe("321", emptyList()))
        assertEquals("321", colegiaturaDe("321", listOf("estetica")))
        // La obstetra que escribió su sigla: tal cual.
        assertEquals("COP 1234", colegiaturaDe("COP 1234", listOf("ginecologia")))
    }

    // ── Obstetra ≠ médico (mismos casos que la web, d9f6107a) ──

    private val obstetra = listOf(r("ginecologia", "Obstetricia"))

    @Test fun obstetriciaASecasEsDelColegioDeObstetras() {
        val c = colegioDe(obstetra)
        assertEquals("COP", c?.sigla)
        assertTrue(c?.nombre.orEmpty().contains("Obstetras"))
        assertEquals("COP 1234", colegiaturaParaMostrar("1234", c))
        assertEquals("COP 1234", colegiaturaDeEspecialidades("1234", obstetra))
    }

    @Test fun elGinecoObstetraYElGinecologoSiguenSiendoMedicos() {
        assertEquals("CMP", sigla(r("ginecologia", "Ginecología y Obstetricia")))
        assertEquals("CMP", sigla(r("ginecologia", "Ginecología")))
        assertEquals("CMP", sigla(r("ginecologia", "Obstetricia"), r("ginecologia", "Ginecología")))
        assertEquals("CMP", sigla(r("ginecologia", "Obstetricia"), r("medicina_general", "Medicina general")))
    }

    @Test fun obstetraQueAdemasHaceEsteticaSigueSiendoObstetra() {
        assertEquals("COP", sigla(r("ginecologia", "Obstetricia"), r("estetica", "Estética")))
    }

    @Test fun conSoloElRubroNoSeDistingue() {
        assertEquals("CMP 1234", colegiaturaDe("1234", listOf("ginecologia")))
        assertEquals("CTMP 1234", colegiaturaDeEspecialidades("1234", listOf(r("fisioterapia", "Fisioterapia"))))
    }

    @Test fun esObstetraYEspecialidadObstetra() {
        assertTrue(esObstetra(obstetra))
        assertTrue(esObstetra(listOf(r(null, "Obstetra"))))
        assertFalse(esObstetra(emptyList()))
        assertFalse(esObstetra(null))
        assertFalse(esObstetra(listOf(r("estetica", "Estética"))))
        assertTrue(esEspecialidadObstetra(null, "Obstetricia y psicoprofilaxis"))
        assertFalse(esEspecialidadObstetra("ginecologia", "Ginecología y obstetricia"))
        assertFalse(esEspecialidadObstetra("fisioterapia", "Fisio obstétrica"))
    }

    @Test fun firmanteDocUsaLasEspecialidadesConNombre() {
        val f = FirmanteDoc("o", "Obst. A", "1234", "Activo", rubros = listOf("ginecologia"),
            nombresEspecialidades = listOf("Obstetricia"), especialidades = obstetra)
        assertEquals("COP 1234", f.colegiatura())
        // Sin especialidades con nombre (servidor viejo): por rubros, como antes.
        assertEquals("CMP 1234", f.copy(especialidades = emptyList()).colegiatura())
    }
}
