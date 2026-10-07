package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import pe.saniape.app.ui.clinica.CHIPS_DEFAULT
import pe.saniape.app.ui.clinica.EspecialidadChips
import pe.saniape.app.ui.clinica.chipsDeClinica
import pe.saniape.app.ui.clinica.chipsDeEspecialidad

/** Gemelo de `lib/__tests__/chips-pool.test.ts` (web) en lo que la app usa. Mismos casos. */
class ChipsPoolTest {

    // ── Clave plegada ──

    @Test fun claveChipPliegaTildesMayusculasEspaciosYPuntuacionFinal() {
        assertEquals("liberacion miofascial", claveChip("Liberación  miofascial."))
        assertEquals("liberacion miofascial", claveChip("LIBERACION miofascial"))
        assertEquals("ejercicio terapeutico", claveChip("  Ejercicio Terapéutico ;"))
    }

    @Test fun claveChipEnieYDieresis() {
        assertEquals(claveChip("muneca"), claveChip("Muñeca"))
        assertEquals("pinguino", claveChip("Pingüino"))
        // Texto descompuesto (letra + marca combinante) = el compuesto.
        assertEquals(claveChip("Liberación"), claveChip("Liberación"))
    }

    @Test fun normalizadorDeTecnicasPliegaIgual() {
        assertEquals(TecnicasNormalizar.clave("Liberacion"), TecnicasNormalizar.clave("Liberación"))
        assertEquals("senal", TecnicasNormalizar.clave("Señal"))
        assertEquals("ultrasonido", TecnicasNormalizar.clave("ÚLTRASONIDO"))
        // partir no repite "Liberación"/"Liberacion".
        assertEquals(1, TecnicasNormalizar.partir("Liberación miofascial + Liberacion miofascial").size)
    }

    // ── Mezcla propio + base + pool ──

    @Test fun combinarPropiasLuegoBaseLuegoPoolSinRepetirPorClave() {
        val r = combinarChips(
            propias = listOf(ChipFila("Compresa", 997.0), ChipFila("Liberacion miofascial", 655.0), ChipFila("TENS", 1118.0)),
            base = listOf("Contractura", "TENS"),
            pool = listOf(ChipFila("Ultrasonido", 2.0), ChipFila("Liberación miofascial", 1.0), ChipFila("Magnetoterapia", 3.0)),
        )
        assertEquals(
            listOf("TENS", "Compresa", "Liberacion miofascial", "Contractura", "Ultrasonido", "Magnetoterapia"),
            r.map { it.texto },
        )
        assertEquals(FuenteChip.PROPIA, r.first { it.texto == "Liberacion miofascial" }.fuente)
        assertEquals(FuenteChip.POOL, r.first { it.texto == "Ultrasonido" }.fuente)
    }

    @Test fun clinicaNuevaSoloPool() {
        val r = combinarChips(pool = listOf(ChipFila("B", 2.0), ChipFila("A", 1.0)))
        assertEquals(listOf(ChipSugerido("A", FuenteChip.POOL), ChipSugerido("B", FuenteChip.POOL)), r)
    }

    @Test fun combinarRespetaElLimite() {
        val pool = (0 until 50).map { ChipFila("Técnica ${'A' + (it % 26)}$it", it.toDouble()) }
        assertEquals(10, combinarChips(pool = pool, limite = 10).size)
    }

    @Test fun sinPesoSeRespetaElOrdenDeLlegada() {
        val r = combinarChips(propias = listOf(ChipFila("Z"), ChipFila("A"), ChipFila("M")))
        assertEquals(listOf("Z", "A", "M"), r.map { it.texto })
    }

    @Test fun chipsConBasePoneLaBaseDelanteDelPoolYCorta() {
        val delServidor = listOf(
            ChipSugerido("Dolor lumbar", FuenteChip.PROPIA),
            ChipSugerido("Del servidor", FuenteChip.BASE),
            ChipSugerido("Rigidez", FuenteChip.POOL),
            ChipSugerido("Hinchazon", FuenteChip.POOL),
            ChipSugerido("Calambre", FuenteChip.POOL),
        )
        val r = chipsConBase(delServidor, base = listOf("Hinchazón", "Ardor"), extras = 2)
        // La base de quien llama manda (la del servidor se descarta); "Hinchazon" del
        // pool no se repite con "Hinchazón"; se corta en base + extras.
        assertEquals(listOf("Dolor lumbar", "Hinchazón", "Ardor", "Rigidez"), r)
    }

    @Test fun chipsConBaseDelServidorUsaLaBaseQueMandoElServidor() {
        val delServidor = listOf(
            ChipSugerido("Lumbalgia", FuenteChip.PROPIA),
            ChipSugerido("Hinchazón", FuenteChip.BASE),
            ChipSugerido("Ardor", FuenteChip.BASE),
            ChipSugerido("Rigidez", FuenteChip.POOL),
            ChipSugerido("Calambre", FuenteChip.POOL),
        )
        assertEquals(listOf("Lumbalgia", "Hinchazón", "Ardor", "Rigidez"), chipsConBaseDelServidor(delServidor, extras = 2))
        // Sin base (fallback de las tablas): solo lo propio y el pool, hasta `extras`.
        assertEquals(listOf("Lumbalgia", "Rigidez"),
            chipsConBaseDelServidor(delServidor.filter { it.fuente != FuenteChip.BASE }, extras = 2))
        assertEquals(emptyList(), chipsConBaseDelServidor(emptyList()))
    }

    @Test fun filtrarEnMemoriaSinTildesYSinLoPuesto() {
        val textos = listOf("Liberación miofascial", "TENS", "Liberación articular", "Compresa")
        assertEquals(listOf("Liberación miofascial", "Liberación articular"), filtrarChips(textos, "liberacion"))
        assertEquals(listOf("Liberación articular"), filtrarChips(textos, "LIBER", excluir = listOf("Liberacion miofascial")))
        assertEquals(listOf("TENS", "Compresa"), filtrarChips(textos, "", excluir = listOf("liberación miofascial", "Liberación articular")))
        assertEquals(2, filtrarChips(textos, "", max = 2).size)
    }

    // ── Respuesta del endpoint ──

    @Test fun parseaLaRespuestaDelEndpoint() {
        val cuerpo = """
            {"campo":"tecnica","especialidadId":"58c5","rubros":["fisioterapia"],"items":[
              {"texto":"TENS","fuente":"propia"},
              {"texto":"Contractura","fuente":"base"},
              {"texto":"Ultrasonido","fuente":"pool"},
              {"texto":"  ","fuente":"pool"},
              {"fuente":"pool"},
              {"texto":"Raro","fuente":"otra"}
            ]}
        """.trimIndent()
        val r = parsearChipsEndpoint(cuerpo)!!
        assertEquals(listOf("TENS", "Contractura", "Ultrasonido", "Raro"), r.map { it.texto })
        assertEquals(listOf(FuenteChip.PROPIA, FuenteChip.BASE, FuenteChip.POOL, FuenteChip.POOL), r.map { it.fuente })
    }

    @Test fun respuestaQueNoEsLaDelContratoEsNull() {
        assertNull(parsearChipsEndpoint("<html>404</html>"))
        assertNull(parsearChipsEndpoint("""{"error":"campo inválido","codigo":"CAMPO_INVALIDO"}"""))
        assertNull(parsearChipsEndpoint("[]"))
        assertEquals(emptyList(), parsearChipsEndpoint("""{"items":[]}"""))
    }

    // ── Chips sobre texto libre ──

    @Test fun chipPuestoSoloComoTerminoCompleto() {
        assertFalse(chipPuesto("Alteración postural, Lumbalgia", "Postural"))
        assertTrue(chipPuesto("Alteración postural, lumbalgia", "Lumbalgia"))
    }

    @Test fun quitarQuitaElTerminoExactoNuncaUnaSubcadena() {
        assertEquals("Alteración postural", quitarChip("Alteración postural, Postural", "Postural"))
        assertEquals("Alteración postural; Lumbalgia", quitarChip("Postural, Alteración postural; Lumbalgia", "postural"))
        assertEquals("", quitarChip("Lumbalgia", "Lumbalgia"))
        assertEquals("Pie plano", quitarChip("Heloma (callo), Pie plano", "Heloma (callo)"))
        assertEquals("Dolor\nRigidez", quitarChip("Dolor\nLumbalgia\nRigidez", "Lumbalgia"))
    }

    @Test fun alternarAgregaOQuita() {
        assertEquals("Lumbalgia", alternarChip("", "Lumbalgia"))
        assertEquals("Dolor, Lumbalgia", alternarChip("Dolor,", "Lumbalgia"))
        assertEquals("Dolor", alternarChip("Dolor, lumbalgia", "Lumbalgia"))
    }

    // ── Qué se registra ──

    @Test fun registrableAceptaTecnicasLargasDeLaClinica() {
        assertTrue(esTextoRegistrable("Liberación miofascial trapecio superior y supraespinoso", CampoChip.TECNICA))
    }

    @Test fun registrableRechazaNombreDelPacienteDniYMarcasDelSistema() {
        assertFalse(esTextoRegistrable("Sesión con Rosa Pari", CampoChip.TECNICA, "Rosa Pari Condori"))
        assertFalse(esTextoRegistrable("DNI 45871236", CampoChip.DIAGNOSTICO))
        assertFalse(esTextoRegistrable("🔁 Control 1 (1 mes) — protocolo automático [control:1]", CampoChip.TECNICA))
        assertFalse(esTextoRegistrable("Cerrada sin registrar la atención", CampoChip.TECNICA))
        assertFalse(esTextoRegistrable("correo@x.com", CampoChip.DIAGNOSTICO))
        assertFalse(esTextoRegistrable("Control 12/03/2026", CampoChip.DIAGNOSTICO))
    }

    @Test fun palabrasVaciasOCortasDelNombreNoVetanTextosClinicos() {
        assertTrue(esTextoRegistrable("Masaje para la espalda", CampoChip.TECNICA, "Ana Para Santa Quilla"))
        assertFalse(esTextoRegistrable("Masaje Quilla", CampoChip.TECNICA, "Ana Para Santa Quilla"))
    }

    @Test fun registrableRespetaElLargoPorCampo() {
        val largo = "a".repeat(100)
        assertFalse(esTextoRegistrable(largo, CampoChip.TECNICA))
        assertTrue(esTextoRegistrable(largo, CampoChip.DIAGNOSTICO))
    }

    @Test fun textosARegistrarLimpiaDeduplicaPorClaveYCortaEn20() {
        val r = textosARegistrar(listOf("TENS", " tens ", "Liberación", "Liberacion", "x", "Masaje Rosa"), CampoChip.TECNICA, "Rosa Pari")
        assertEquals(listOf("TENS", "Liberación"), r)
        assertEquals(20, textosARegistrar((1..30).map { "Técnica número ${'a' + it % 26}${'a' + it / 26}" }, CampoChip.TECNICA).size)
    }

    @Test fun trocearDiagnosticoComoLaWeb() {
        assertEquals(listOf("Lumbalgia", "Contractura"), trocearDiagnostico("Lumbalgia, Contractura; ab\n"))
    }

    // ── ChipsClinicos.kt (gemelo de lib/chipsClinicos.ts) ──

    @Test fun semillaPorNombreDeEspecialidad() {
        assertTrue("Lesión Deportiva" in chipsDeEspecialidad("Kinesiología").tipos)
        assertTrue("Lesión Deportiva" in chipsDeEspecialidad("TERAPIA FÍSICA Y REHABILITACIÓN").tipos)
        assertTrue("Lesión Deportiva" in chipsDeEspecialidad("Masoterapia").tipos)
        // 'ortodon', no 'orto': ortopedia no cae en ortodoncia.
        assertEquals(CHIPS_DEFAULT, chipsDeEspecialidad("Traumatología y ortopedia"))
        assertTrue("Apiñamiento" in chipsDeEspecialidad("Ortodoncia").tipos)
        assertTrue("Dolor pélvico" in chipsDeEspecialidad("Ginecología").sintomas)
        assertTrue("Control prenatal" in chipsDeEspecialidad("Obstetricia").tipos)
        assertTrue("Infertilidad" in chipsDeEspecialidad("Fertilidad").tipos)
        assertTrue("Trastorno bipolar" in chipsDeEspecialidad("Psiquiatría").tipos)
        assertTrue("Facial" in chipsDeEspecialidad("Estética").tipos)
        // Estética va al final: "Estética dental" y "Estética capilar" se quedan con la suya.
        assertTrue("Caries" in chipsDeEspecialidad("Estética dental").tipos)
        assertTrue("Calvicie" in chipsDeEspecialidad("Estética capilar").tipos)
        assertEquals(CHIPS_DEFAULT, chipsDeEspecialidad(null))
    }

    @Test fun losPersonalizadosMandanSobreLosDelCodigo() {
        val fisio = EspecialidadChips("Fisioterapia", chipsTipos = listOf("Columna", "Rodilla"))
        assertEquals(listOf("Columna", "Rodilla"), chipsDeEspecialidad(fisio).tipos)
        // Sin síntomas propios: los del código.
        assertTrue("Contractura" in chipsDeEspecialidad(fisio).sintomas)
        // Lista vacía = no personalizó.
        assertTrue("Caries" in chipsDeEspecialidad(EspecialidadChips("Odontología", chipsTipos = emptyList())).tipos)
    }

    @Test fun unionDeLaClinicaSinDuplicados() {
        val u = chipsDeClinica(listOf(
            EspecialidadChips("Fisioterapia", chipsSintomas = listOf("Dolor", "Rigidez")),
            EspecialidadChips("Medicina general"),
        ))
        assertEquals(listOf("Dolor", "Rigidez", "Fiebre", "Mareo", "Náuseas", "Tos"), u.sintomas)
        assertEquals(CHIPS_DEFAULT, chipsDeClinica(emptyList()))
    }

    @Test fun unionDeLaClinicaDeduplicaPorClavePlegada() {
        val u = chipsDeClinica(listOf(
            EspecialidadChips("Fisioterapia", chipsSintomas = listOf("Inflamacion", "DOLOR")),
            EspecialidadChips("Medicina general", chipsSintomas = listOf("Inflamación", "Dolor", "Tos")),
        ))
        assertEquals(listOf("Inflamacion", "DOLOR", "Tos"), u.sintomas)
    }

    @Test fun faltaFuncionReconoceBaseSinMigracion() {
        assertTrue(esFaltaFuncion("PGRST202: Could not find the function public.registrar_chips"))
        assertTrue(esFaltaFuncion("could not find the function"))
        assertFalse(esFaltaFuncion("Unable to resolve host"))
        assertFalse(esFaltaFuncion(null))
    }
}
