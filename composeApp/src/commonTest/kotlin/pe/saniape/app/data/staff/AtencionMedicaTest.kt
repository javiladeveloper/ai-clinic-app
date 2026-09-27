package pe.saniape.app.data.staff

import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Sala de espera, triaje y signos vitales: gemelas de lib/atencion-medica.ts,
 * lib/historia-clinica.ts y lib/historial-vitales.ts (mismos casos que
 * lib/__tests__/historial-vitales.test.ts y historia-clinica-nts139.test.ts).
 */
class AtencionMedicaTest {

    // ── Interruptores y campos ──

    @Test fun camposTriajeAusentesOVaciosSonTodos() {
        assertEquals(MEDICIONES_TRIAJE, leerCamposTriaje(null))
        assertEquals(MEDICIONES_TRIAJE, leerCamposTriaje(emptyList()))
        assertEquals(MEDICIONES_TRIAJE, leerCamposTriaje(listOf("basura")))
        // Orden de siempre, sin importar cómo vengan.
        assertEquals(listOf("peso", "talla"), leerCamposTriaje(listOf("talla", "peso", "x")))
    }

    @Test fun presionSonDosColumnas() {
        assertEquals(listOf("presion_sistolica", "presion_diastolica", "peso"), columnasDeMediciones(listOf("presion", "peso")))
    }

    @Test fun sinInterruptoresNoHaySalaDeEspera() {
        assertFalse(ModulosClinicos().salaEspera)
        assertTrue(ModulosClinicos(triaje = true).salaEspera)
        assertTrue(ModulosClinicos(flujoMedico = true, mapaMedico = MapaClinico(listOf("med"))).salaEspera)
        // Flujo médico sin especialidades médicas: nada que mostrar (como mapaMedico vacío en la web).
        assertFalse(ModulosClinicos(flujoMedico = true).salaEspera)
    }

    // ── Etapa de llegada ──

    private fun a(llegada: String? = null, triaje: String? = null, consulta: String? = null) =
        AtencionLlegada(id = "a", citaId = "c", llegadaAt = llegada, triajeAt = triaje, consultaInicioAt = consulta)

    @Test fun etapasDeLaSala() {
        assertNull(etapaLlegada("Cancelada", false, a()))
        assertNull(etapaLlegada("No asistió", false, a()))
        assertNull(etapaLlegada("Confirmada", true, a()))
        assertEquals(EtapaLlegada.ATENDIDO, etapaLlegada("Completada", false, a(consulta = "x")))
        assertEquals(EtapaLlegada.EN_CONSULTA, etapaLlegada("Confirmada", false, a(llegada = "x", consulta = "y")))
        assertEquals(EtapaLlegada.TRIAJE, etapaLlegada("Pendiente", false, a(triaje = "x")))
        assertEquals(EtapaLlegada.LLEGO, etapaLlegada("Pendiente", false, a(llegada = "x")))
        assertEquals(EtapaLlegada.POR_LLEGAR, etapaLlegada("Pendiente", false, null))
        assertTrue(enSalaDeEspera(EtapaLlegada.LLEGO))
        assertTrue(enSalaDeEspera(EtapaLlegada.TRIAJE))
        assertFalse(enSalaDeEspera(EtapaLlegada.EN_CONSULTA))
        assertFalse(enSalaDeEspera(null))
    }

    @Test fun minutosEnLaSala() {
        val ahora = Instant.parse("2026-09-27T15:00:00Z")
        assertEquals(25, minutosEsperando("2026-09-27T14:35:00.123456+00:00", ahora))
        assertEquals(0, minutosEsperando("2026-09-27T15:05:00Z", ahora))   // reloj adelantado: nunca negativo
        assertNull(minutosEsperando(null, ahora))
        assertNull(minutosEsperando("no es fecha", ahora))
        assertEquals(" · recién", textoEspera(0))
        assertEquals(" · 12 min", textoEspera(12))
        assertEquals(" · 5 h 26 min", textoEspera(326))
        assertEquals(" · 2 h", textoEspera(120))
        assertEquals("", textoEspera(null))
    }

    @Test fun resumenDelTriajeEnLaTarjeta() {
        val t = AtencionLlegada(id = "a", citaId = "c", presionSistolica = 138.0, presionDiastolica = 86.0,
            temperatura = 36.5, saturacionO2 = 97.0, peso = 82.0)
        assertEquals("PA 138/86 · T° 36.5 · SpO₂ 97%", resumenTriaje(t))
        assertEquals("82 kg", resumenTriaje(AtencionLlegada(id = null, citaId = "c", peso = 82.0)))
    }

    @Test fun loLocalSePintaHastaQueElServidorLoTenga() {
        val local = a(llegada = "2026-09-27T14:00:00Z")
        // Sin fila en el servidor (sin señal): manda lo local.
        assertEquals("2026-09-27T14:00:00Z", fusionarLlegada(null, local)?.llegadaAt)
        assertFalse(servidorAlDia(null, local))
        // El servidor ya tiene la llegada (con su propia hora): manda el servidor.
        val srv = a(llegada = "2026-09-27T13:59:58.000Z")
        assertEquals("2026-09-27T13:59:58.000Z", fusionarLlegada(srv, local)?.llegadaAt)
        assertTrue(servidorAlDia(srv, local))
        // Triaje corregido aquí, servidor con el triaje viejo: manda lo local.
        val triLocal = AtencionLlegada(id = null, citaId = "c", llegadaAt = "x", triajeAt = "2026-09-27T15:00:00Z", peso = 80.0)
        val triSrv = AtencionLlegada(id = "a", citaId = "c", llegadaAt = "x", triajeAt = "2026-09-27T14:00:00Z", peso = 82.0)
        assertEquals(80.0, fusionarLlegada(triSrv, triLocal)?.peso)
        assertFalse(servidorAlDia(triSrv, triLocal))
    }

    // ── Lectura por color, IMC, talla ──

    @Test fun lecturaPorColor() {
        assertEquals(AlertaVital(NivelAlerta.ALERTA, "Fiebre"), alertaVital("temperatura", 38.2, 30))
        assertEquals(AlertaVital(NivelAlerta.ATENCION, "Febrícula"), alertaVital("temperatura", 37.6, 30))
        assertNull(alertaVital("temperatura", 36.5, 30))
        assertEquals(AlertaVital(NivelAlerta.ALERTA, "Muy baja"), alertaVital("saturacion_o2", 88.0, 30))
        assertEquals(AlertaVital(NivelAlerta.ATENCION, "Taquicardia"), alertaVital("frecuencia_cardiaca", 110.0, 30))
        // Fuera de lo posible: no hay lectura (eso lo marca el rango).
        assertNull(alertaVital("temperatura", 60.0, 30))
        // Menor: la presión no se lee con umbrales de adulto; la temperatura sí.
        assertNull(alertaPresion(150.0, 95.0, 12))
        assertEquals(NivelAlerta.ALERTA, alertaVital("temperatura", 39.5, 8)?.nivel)
        // La peor de las dos: diastólica 92 = Elevada.
        assertEquals(NivelAlerta.ALERTA, alertaPresion(138.0, 92.0, 60)?.nivel)
    }

    @Test fun imcTallaYNumeros() {
        assertEquals("Sobrepeso", clasificarImc(26.12, 40))
        assertNull(clasificarImc(26.12, 12))
        assertEquals(170.0, tallaEnCm(1.7))
        assertEquals(170.0, tallaEnCm(170.0))
        assertEquals(36.8, aNumero("36,8"))
        assertNull(aNumero(""))
        assertTrue(aNumero("abc")!!.isNaN())
        assertEquals(29.05, calcularImc(82.0, 168.0))
        assertNull(calcularImc(82.0, null))
        assertEquals(NivelAlerta.ALERTA, nivelImc("Obesidad I"))
        assertEquals(NivelAlerta.ATENCION, nivelImc("Bajo peso"))
        assertNull(nivelImc("Normal"))
    }

    // ── Formulario de triaje ──

    private val todos = MEDICIONES_TRIAJE

    @Test fun validaComoLaWeb() {
        assertEquals(listOf("Registra al menos una medición."), validarTriaje(emptyMap(), todos))
        assertEquals(listOf("Presión arterial: completa sistólica y diastólica."),
            validarTriaje(mapOf("presion_sistolica" to "120"), todos))
        assertEquals(listOf("Presión arterial: la diastólica debe ser menor que la sistólica."),
            validarTriaje(mapOf("presion_sistolica" to "80", "presion_diastolica" to "90"), todos))
        assertEquals(listOf("Frecuencia respiratoria: 180 rpm está fuera de lo posible (4–80)."),
            validarTriaje(mapOf("frecuencia_respiratoria" to "180"), todos))
        assertEquals(listOf("Temperatura: escribe solo el número."), validarTriaje(mapOf("temperatura" to "36.5.1"), todos))
        // Talla en metros vale; perímetro fuera de rango, no.
        assertTrue(validarTriaje(mapOf("talla" to "1,70", "peso" to "70"), todos).isEmpty())
        assertEquals(1, validarTriaje(mapOf("perimetro_abdominal" to "10"), todos).size)
        // Una clínica que solo pesa y talla: la presión escrita no cuenta.
        assertEquals(listOf("Registra al menos una medición."),
            validarTriaje(mapOf("presion_sistolica" to "120"), listOf("peso", "talla")))
    }

    @Test fun cuerpoSoloConLoQueMideLaClinica() {
        val v = cuerpoVitalesTriaje(mapOf("peso" to "70,5", "talla" to "1.68", "presion_sistolica" to "120"), listOf("peso", "talla"))
        assertEquals(setOf("peso", "talla"), v.keys)
        assertEquals(70.5, (v["peso"] as JsonPrimitive).content.toDouble())
        assertEquals(168.0, (v["talla"] as JsonPrimitive).content.toDouble())
        // Vacío = null explícito (la web borra esa medición, igual que el formulario web).
        val w = cuerpoVitalesTriaje(mapOf("peso" to ""), listOf("peso"))
        assertEquals(JsonNull, w["peso"])
    }

    @Test fun triajeLocalCalculaElImc() {
        val l = atencionLocalDeTriaje("c", mapOf("peso" to "82", "talla" to "168"), listOf("peso", "talla"), "2026-09-27T15:00:00Z", null)
        assertEquals(29.05, l.imc)
        assertEquals("2026-09-27T15:00:00Z", l.llegadaAt)   // sin llegada previa, el triaje la marca
        assertTrue(valorFueraDeRango("talla", "300"))
        assertFalse(valorFueraDeRango("talla", "1.70"))
        assertFalse(valorFueraDeRango("peso", ""))
    }

    @Test fun edadDesdeLaFechaDeNacimiento() {
        assertEquals(28, edadDe("1997-09-28", null, "2026-09-27"))
        assertEquals(30, edadDe("1996-09-27", null, "2026-09-27"))
        assertEquals(40, edadDe(null, 40, "2026-09-27"))
    }

    // ── Historial de signos vitales ──

    @Test fun aTomaVitalLeeNumericComoTexto() {
        val o = Json.parseToJsonElement(
            """{"id":"a","fecha":"2026-09-27","peso":"82.00","talla":"168.0","imc":null,"temperatura":"36.5","terapeuta":{"nombre":"Dr. X"}}"""
        ).jsonObject
        val t = aTomaVital(o)!!
        assertEquals(82.0, t.peso)
        assertEquals(36.5, t.temperatura)
        assertEquals(29.05, t.imc)
        assertEquals("Dr. X", t.tomadoPor)
        val o2 = Json.parseToJsonElement(
            """{"id":"b","fecha":"2026-09-27","triaje_por_nombre":"Lic. Y","terapeuta":[{"nombre":"Dr. X"}]}"""
        ).jsonObject
        assertEquals("Lic. Y", aTomaVital(o2)!!.tomadoPor)
    }

    private fun toma(id: String = "x", ps: Double? = null, pd: Double? = null, t: Double? = null, peso: Double? = null, imc: Double? = null) =
        TomaVital(id = id, citaId = null, fecha = "2026-09-27", hora = null, triajeAt = null, tomadoPor = null,
            presionSistolica = ps, presionDiastolica = pd, temperatura = t, peso = peso, imc = imc)

    @Test fun datosDeLaUltimaToma() {
        val d = datosDeToma(toma(ps = 138.0, pd = 92.0, t = 36.5, peso = 82.0, imc = 29.1), 60)
        val pa = d.first { it.clave == "pa" }
        assertEquals("138/92", pa.valor)
        assertEquals(NivelAlerta.ALERTA, pa.alerta?.nivel)
        assertNull(d.first { it.clave == "temperatura" }.alerta)
        assertEquals("36.5 °C", d.first { it.clave == "temperatura" }.valor)
        assertEquals(AlertaVital(NivelAlerta.ATENCION, "Sobrepeso"), d.first { it.clave == "imc" }.alerta)
        assertEquals("82 kg", d.first { it.clave == "peso" }.valor)
    }

    @Test fun seriesConHuecosNoCeros() {
        val s = seriesVitales(listOf(toma(id = "1", ps = 140.0), toma(id = "2", ps = 130.0, pd = 85.0, peso = 80.0)))
        assertNull(s[0].sistolica)
        assertEquals(130.0, s[1].sistolica)
        assertFalse(puntosCon(s, "sistolica"))
        assertTrue(puntosCon(s, "peso", 1))
    }

    @Test fun ejeYConMarcasRedondas() {
        assertEquals(EscalaY(60.0, 160.0, listOf(60.0, 80.0, 100.0, 120.0, 140.0, 160.0)), escalaY("presion", listOf(128.0, 80.0, 132.0, 82.0)))
        assertEquals(200.0, escalaY("presion", listOf(185.0, 125.0)).hi)
        val e = escalaY("peso", listOf(74.0, 73.2))
        assertTrue(e.lo <= 70.0)
        assertTrue(e.hi >= 77.0)
        assertEquals(1, e.ticks.zipWithNext { x, y -> y - x }.toSet().size)
        assertTrue(e.ticks.size <= 7)
    }

    // ── Por cita / por paciente (clínicas mixtas) ──

    @Test fun consultaGuiadaPorCita() {
        val mapa = MapaClinico(listOf("med"))
        assertTrue(citaEsMedicaGuiada(mapa, false, "med", null, null))
        assertFalse(citaEsMedicaGuiada(mapa, false, "fisio", "med", null))   // manda la de la cita
        assertTrue(citaEsMedicaGuiada(mapa, false, null, "med", null))        // luego la del servicio
        assertTrue(citaEsMedicaGuiada(mapa, false, null, null, listOf("fisio", "med")))
        assertFalse(citaEsMedicaGuiada(mapa, true, "med", null, null))        // nunca una dental
        assertFalse(citaEsMedicaGuiada(MapaClinico(), false, "med", null, null))
        assertTrue(citaEsMedicaGuiada(MapaClinico(solo = true), false, null, null, null))
    }

    @Test fun recetasPorPaciente() {
        val mixta = MapaClinico(listOf("med"))
        assertFalse(pacienteRecibeRecetas(MapaClinico(), listOf("med")))
        assertTrue(pacienteRecibeRecetas(MapaClinico(solo = true)))
        assertFalse(pacienteRecibeRecetas(mixta, listOf("fisio")))
        assertTrue(pacienteRecibeRecetas(mixta, listOf("fisio", "med")))
        assertTrue(pacienteRecibeRecetas(mixta, listOf("fisio"), especialidadesDeQuienMira = listOf("med")))
        assertTrue(pacienteRecibeRecetas(mixta, listOf("fisio"), tieneRecetas = true))
    }

    // ── Recetas y documentos ──

    @Test fun numeroYVigenciaDeReceta() {
        assertEquals("N° 000123", formatearNumeroReceta(123))
        assertEquals("N° —", formatearNumeroReceta(null))
        assertTrue(recetaVigente("Emitida", "2026-09-27", "2026-09-27"))   // el último día cuenta
        assertFalse(recetaVigente("Emitida", "2026-09-26", "2026-09-27"))
        assertFalse(recetaVigente("Anulada", "2026-12-31", "2026-09-27"))
    }

    @Test fun recetaDelStaffIncluyeAnuladasYToleraFilasRaras() {
        val o = Json.parseToJsonElement(
            """{"id":"r1","numero":12,"fecha":"2026-09-27","valida_hasta":"2026-10-27","estado":"Anulada",
               "motivo_anulacion":"Error en la dosis","diagnostico":"Faringitis","cie10":"J02.9",
               "prescriptor":{"nombre":"Dr. Pérez","colegiatura":"CMP 45678"},
               "establecimiento":{"nombre":"Clínica X"},
               "items":[{"dci":"Amoxicilina","concentracion":"500 mg","cantidad":21,"unidad":"tabletas"},{"marca":"sin dci"}]}"""
        ).jsonObject
        val r = aRecetaStaff(o)!!
        assertTrue(r.anulada)
        assertEquals("N° 000012", r.numeroTexto)
        assertEquals(1, r.items.size)
        assertEquals("21 tabletas", r.items[0].cantidadTexto)
        assertEquals("Clínica X", r.establecimiento)
        assertEquals("CMP 45678", r.prescriptor?.colegiatura)
        assertNull(aRecetaStaff(Json.parseToJsonElement("""{"numero":1}""").jsonObject))
    }

    @Test fun espacioDeDocumentos() {
        assertEquals("1 GB", textoEspacioMB(1024))
        assertEquals("500 MB", textoEspacioMB(500))
        assertEquals("820 KB", formatearBytes(820L * 1024))
        assertEquals("12,5 MB", formatearBytes((12.5 * 1024 * 1024).toLong()))
        assertEquals("1 GB", formatearBytes(1024L * 1024 * 1024))
        assertTrue(mensajeSinEspacio(1024).startsWith("Se llenó el espacio de documentos de tu plan (1 GB)."))
        assertEquals(
            "Se lleno el espacio de documentos de tu plan (1024 MB). Borra documentos.",
            mensajeLimitePlan("ERROR: LIMITE_PLAN: Se lleno el espacio de documentos de tu plan (1024 MB). Borra documentos.\nCONTEXT: x"),
        )
    }

    @Test fun numerosSinStringFormat() {
        assertEquals("37.0", fijo(37.0, 1))
        assertEquals("36.5", fijo(36.54, 1))
        assertEquals("82", decimal(82.0))
        assertEquals("82.5", decimal(82.5))
        assertEquals("0.3", decimal(0.3))
    }
}
