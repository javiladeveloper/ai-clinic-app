package pe.saniape.app.data.staff

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.round

// ─────────────────────────────────────────────────────────────────────────────
// SALA DE ESPERA, TRIAJE Y SIGNOS VITALES — reglas puras de PRESENTACIÓN.
//
// GEMELAS de la web (si cambia una, cambia la otra):
//   · lib/atencion-medica.ts   → MEDICIONES_TRIAJE, leerCamposTriaje,
//                                columnasDeMediciones, etapaLlegada,
//                                enSalaDeEspera, minutosEsperando
//   · lib/historia-clinica.ts  → RANGO_VITAL, aNumero, tallaEnCm, calcularImc,
//                                clasificarImc, alertaVital, validarAtencion
//                                (solo la parte de vitales)
//   · lib/historial-vitales.ts → TomaVital, aTomaVital, datosDeToma,
//                                seriesVitales, puntosCon, escalaY, nivelImc,
//                                alertaPresion
//   · lib/recetas.ts           → formatearNumeroReceta, estaVigente
//
// Lo que ESCRIBE (llegada, triaje) lo hace la web en /api/staff/atencion/*:
// aquí solo se decide qué se ve y de qué color, y se valida el formulario para
// no mandar (ni encolar sin señal) un triaje que el servidor rechazaría.
// ─────────────────────────────────────────────────────────────────────────────

// ── Interruptores por clínica (los resuelve /api/staff/contexto) ─────────────

/** Mediciones que una clínica puede tomar en su triaje, en el orden de siempre. */
val MEDICIONES_TRIAJE: List<String> = listOf(
    "presion", "frecuencia_cardiaca", "frecuencia_respiratoria", "temperatura",
    "saturacion_o2", "peso", "talla", "perimetro_abdominal",
)

/**
 * `camposTriaje` del contexto → las mediciones de la clínica en el orden de
 * siempre. Ausente, vacío o con basura = TODAS (nunca un triaje sin campos).
 */
fun leerCamposTriaje(v: List<String>?): List<String> {
    if (v == null) return MEDICIONES_TRIAJE
    val set = v.toSet()
    val out = MEDICIONES_TRIAJE.filter { it in set }
    return out.ifEmpty { MEDICIONES_TRIAJE }
}

/** Las columnas de `atenciones_clinicas` que llena cada medición ('presion' son dos). */
fun columnasDeMediciones(m: List<String>): List<String> =
    m.flatMap { if (it == "presion") listOf("presion_sistolica", "presion_diastolica") else listOf(it) }

/**
 * Qué especialidades de la clínica tienen algo (flujo médico / recetas). Mismo
 * molde que `MapaReceta` de la web y que [MapaDental]: `solo` = todas.
 */
data class MapaClinico(val ids: List<String> = emptyList(), val solo: Boolean = false) {
    val activo: Boolean get() = solo || ids.isNotEmpty()
}

/**
 * Módulos clínicos de la clínica (`modulosClinicos` de /api/staff/contexto).
 * Default TODO apagado: un backend que todavía no los manda deja la app
 * exactamente como estaba (DALU y RENOVA los tienen apagados también).
 */
data class ModulosClinicos(
    /** Cola, triaje, consulta guiada y HC de norma. */
    val flujoMedico: Boolean = false,
    /** "💊 Recetas". */
    val recetas: Boolean = false,
    /** Recetas encendidas en una clínica NO médica (modo "indicaciones"). */
    val recetasOptIn: Boolean = false,
    /** Signos vitales antes de la atención, en TODA la clínica. */
    val triaje: Boolean = false,
    /** Qué mide la clínica en su triaje. */
    val camposTriaje: List<String> = MEDICIONES_TRIAJE,
    /** Especialidades con flujo médico (`mapaMedico`; vacío con el flujo apagado). */
    val mapaMedico: MapaClinico = MapaClinico(),
    /** Especialidades que ven "💊 Recetas" (`mapaReceta`). */
    val mapaReceta: MapaClinico = MapaClinico(),
) {
    /**
     * ¿La agenda de hoy lleva sala de espera? Como /citas web: con el triaje de
     * la clínica o con alguna especialidad en el flujo médico.
     */
    val salaEspera: Boolean get() = triaje || mapaMedico.activo
}

/** ¿Esta especialidad lleva registro de atención médica? (especialidadConHistoriaMedica). */
fun especialidadConHistoriaMedica(mapa: MapaClinico, especialidadId: String?): Boolean {
    if (mapa.solo) return true
    return !especialidadId.isNullOrBlank() && especialidadId in mapa.ids
}

/**
 * ¿La cita va por la consulta guiada? (gemela de `esMedicaGuiada` de /citas web).
 * Nunca una cita dental. Manda la especialidad de la cita, luego la del servicio
 * de su tratamiento; sin ninguna, "solo médico" o alguna especialidad del
 * profesional en el mapa.
 */
fun citaEsMedicaGuiada(
    mapa: MapaClinico,
    esDental: Boolean,
    especialidadId: String?,
    especialidadServicioId: String?,
    especialidadesProfesional: List<String>?,
): Boolean {
    if (!mapa.activo || esDental) return false
    val esp = listOf(especialidadId, especialidadServicioId).firstOrNull { !it.isNullOrBlank() }
    if (esp != null) return especialidadConHistoriaMedica(mapa, esp)
    if (mapa.solo) return true
    return especialidadesProfesional.orEmpty().any { it in mapa.ids }
}

/**
 * ¿La ficha de este paciente muestra "💊 Recetas"? (gemela de `pacienteRecibeRecetas`).
 * En una clínica que solo receta, siempre; en una mixta, si el paciente tiene
 * algo de una especialidad que receta, ya tiene recetas, o quien mira receta.
 */
fun pacienteRecibeRecetas(
    mapa: MapaClinico,
    especialidadIds: List<String?> = emptyList(),
    especialidadesDeQuienMira: List<String>? = null,
    tieneRecetas: Boolean = false,
): Boolean {
    if (mapa.solo) return true
    if (mapa.ids.isEmpty()) return false
    if (tieneRecetas) return true
    return especialidadIds.any { it != null && it in mapa.ids } ||
        especialidadesDeQuienMira.orEmpty().any { it in mapa.ids }
}

// ── La llegada de cada cita del día ──────────────────────────────────────────

/** Lo que la agenda necesita de la atención de una cita de hoy (lib/llegadas-dia.ts). */
data class AtencionLlegada(
    val id: String?,
    val citaId: String,
    val llegadaAt: String? = null,
    val triajeAt: String? = null,
    val triajePorNombre: String? = null,
    val consultaInicioAt: String? = null,
    val atendidaAt: String? = null,
    val presionSistolica: Double? = null,
    val presionDiastolica: Double? = null,
    val temperatura: Double? = null,
    val saturacionO2: Double? = null,
    val frecuenciaCardiaca: Double? = null,
    val peso: Double? = null,
    val talla: Double? = null,
    val imc: Double? = null,
)

/** Columnas de la lectura liviana del día (COLUMNAS_LLEGADA de la web). */
const val COLUMNAS_LLEGADA =
    "id, cita_id, llegada_at, triaje_at, triaje_por_nombre, consulta_inicio_at, atendida_at, " +
        "presion_sistolica, presion_diastolica, temperatura, saturacion_o2, frecuencia_cardiaca, peso, talla, imc"

private fun JsonObject.txt(k: String): String? =
    (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" && it.isNotBlank() }

/** NUMERIC puede llegar como número o como texto: los dos valen. */
private fun JsonObject.num(k: String): Double? = txt(k)?.toDoubleOrNull()?.takeIf { it.isFinite() }

/** Fila cruda → AtencionLlegada. Null si no trae la cita (no se puede pintar en ninguna tarjeta). */
fun aAtencionLlegada(o: JsonObject): AtencionLlegada? {
    val cita = o.txt("cita_id") ?: return null
    val peso = o.num("peso")
    val talla = o.num("talla")
    return AtencionLlegada(
        id = o.txt("id"),
        citaId = cita,
        llegadaAt = o.txt("llegada_at"),
        triajeAt = o.txt("triaje_at"),
        triajePorNombre = o.txt("triaje_por_nombre"),
        consultaInicioAt = o.txt("consulta_inicio_at"),
        atendidaAt = o.txt("atendida_at"),
        presionSistolica = o.num("presion_sistolica"),
        presionDiastolica = o.num("presion_diastolica"),
        temperatura = o.num("temperatura"),
        saturacionO2 = o.num("saturacion_o2"),
        frecuenciaCardiaca = o.num("frecuencia_cardiaca"),
        peso = peso,
        talla = talla,
        imc = o.num("imc") ?: calcularImc(peso, talla),
    )
}

/** Etapa de llegada: Por llegar → Llegó → Triaje ✓ → En consulta → Atendido. */
enum class EtapaLlegada(val nombre: String) {
    POR_LLEGAR("Por llegar"),
    LLEGO("Llegó"),
    TRIAJE("Triaje ✓"),
    EN_CONSULTA("En consulta"),
    ATENDIDO("Atendido"),
}

/**
 * Etapa de una cita del día (gemela de `etapaLlegada`). null = no aplica
 * (cancelada, eliminada o no vino): no se pinta indicador. "Atendido" lo dice la
 * CITA (si se revierte, vuelve a la cola), no `atendida_at`.
 */
fun etapaLlegada(estadoCita: String?, noAsistio: Boolean, a: AtencionLlegada?): EtapaLlegada? {
    if (estadoCita == "Cancelada" || estadoCita == "Eliminada" || estadoCita == "No asistió" || noAsistio) return null
    if (estadoCita == "Completada") return EtapaLlegada.ATENDIDO
    if (a?.consultaInicioAt != null) return EtapaLlegada.EN_CONSULTA
    if (a?.llegadaAt != null || a?.triajeAt != null) {
        return if (a.triajeAt != null) EtapaLlegada.TRIAJE else EtapaLlegada.LLEGO
    }
    return EtapaLlegada.POR_LLEGAR
}

/** ¿Está en la sala de espera? (llegó y todavía no entra a consulta). */
fun enSalaDeEspera(e: EtapaLlegada?): Boolean = e == EtapaLlegada.LLEGO || e == EtapaLlegada.TRIAJE

/** Minutos desde la llegada (para ver quién espera más). null si no hay o no se entiende. */
fun minutosEsperando(llegadaAt: String?, ahora: Instant): Int? {
    if (llegadaAt.isNullOrBlank()) return null
    val t = parsearInstante(llegadaAt) ?: return null
    val min = round((ahora - t).inWholeSeconds / 60.0).toInt()
    return maxOf(0, min)
}

/** "2026-09-27T14:12:00.123456+00:00" (PostgREST) o "…Z" → Instant. */
internal fun parsearInstante(iso: String): Instant? = runCatching { Instant.parse(iso.trim().replace(' ', 'T')) }.getOrNull()

/** " · recién" / " · 12 min" / " · 5 h 26 min" (una espera larga en horas, no "326 min"). */
fun textoEspera(min: Int?): String = when {
    min == null -> ""
    min == 0 -> " · recién"
    min < 60 -> " · $min min"
    else -> " · ${min / 60} h" + (if (min % 60 != 0) " ${min % 60} min" else "")
}

/** Los valores clave del triaje para la tarjeta: "PA 138/86 · T° 36.5 · SpO₂ 97%" (máx. 3). */
fun resumenTriaje(a: AtencionLlegada?): String {
    if (a == null) return ""
    return listOfNotNull(
        if (a.presionSistolica != null && a.presionDiastolica != null) "PA ${decimal(a.presionSistolica)}/${decimal(a.presionDiastolica)}" else null,
        a.temperatura?.let { "T° ${fijo(it, 1)}" },
        a.saturacionO2?.let { "SpO₂ ${decimal(it)}%" },
        a.peso?.let { "${decimal(it)} kg" },
        a.imc?.let { "IMC ${fijo(it, 1)}" },
    ).take(3).joinToString(" · ")
}

/**
 * Lo que la agenda muestra de una cita: lo que dijo el servidor con lo que se
 * registró EN ESTE TELÉFONO encima, mientras el servidor no lo tenga (sin señal
 * la operación queda en la cola; sin esto el paciente "volvía" a Por llegar en
 * la siguiente recarga). Cuando el servidor ya trae ese momento (o uno más
 * nuevo), manda el servidor.
 */
fun fusionarLlegada(servidor: AtencionLlegada?, local: AtencionLlegada?): AtencionLlegada? {
    if (local == null) return servidor
    if (servidor == null) return local
    val triajeLocalManda = local.triajeAt != null &&
        (servidor.triajeAt == null || servidor.triajeAt.take(19) < local.triajeAt.take(19))
    val base = servidor.copy(llegadaAt = servidor.llegadaAt ?: local.llegadaAt)
    return if (!triajeLocalManda) base else base.copy(
        triajeAt = local.triajeAt,
        triajePorNombre = local.triajePorNombre ?: servidor.triajePorNombre,
        presionSistolica = local.presionSistolica, presionDiastolica = local.presionDiastolica,
        temperatura = local.temperatura, saturacionO2 = local.saturacionO2,
        frecuenciaCardiaca = local.frecuenciaCardiaca, peso = local.peso, talla = local.talla, imc = local.imc,
    )
}

/** ¿El servidor ya tiene lo que se registró aquí? (entonces la marca local sobra). */
fun servidorAlDia(servidor: AtencionLlegada?, local: AtencionLlegada): Boolean {
    if (servidor == null) return false
    if (local.llegadaAt != null && servidor.llegadaAt == null && servidor.triajeAt == null) return false
    if (local.triajeAt != null && (servidor.triajeAt == null || servidor.triajeAt.take(19) < local.triajeAt.take(19))) return false
    return true
}

// ── Signos vitales: rangos, lectura por color, IMC ───────────────────────────

/** Rango fisiológicamente POSIBLE (atajan un error de tipeo; gemelos del CHECK de la base). */
data class RangoVital(val min: Double, val max: Double, val nombre: String, val unidad: String)

val RANGO_VITAL: Map<String, RangoVital> = mapOf(
    "presion_sistolica" to RangoVital(40.0, 300.0, "Presión sistólica", "mmHg"),
    "presion_diastolica" to RangoVital(20.0, 200.0, "Presión diastólica", "mmHg"),
    "frecuencia_cardiaca" to RangoVital(20.0, 250.0, "Frecuencia cardiaca", "lpm"),
    "frecuencia_respiratoria" to RangoVital(4.0, 80.0, "Frecuencia respiratoria", "rpm"),
    "temperatura" to RangoVital(30.0, 45.0, "Temperatura", "°C"),
    "saturacion_o2" to RangoVital(50.0, 100.0, "Saturación de O₂", "%"),
    "peso" to RangoVital(0.3, 400.0, "Peso", "kg"),
    "talla" to RangoVital(20.0, 250.0, "Talla", "cm"),
)

/** Perímetro abdominal: aparte porque es opcional y más nuevo (gemelo de RANGO_PERIMETRO). */
val RANGO_PERIMETRO = RangoVital(30.0, 250.0, "Perímetro abdominal", "cm")

/** Rango de cualquier columna del triaje (incluido el perímetro). */
fun rangoDe(columna: String): RangoVital? = if (columna == "perimetro_abdominal") RANGO_PERIMETRO else RANGO_VITAL[columna]

/** Las ocho columnas vitales "de norma" (sin el perímetro), en el orden de la web. */
val CAMPOS_VITALES: List<String> = listOf(
    "presion_sistolica", "presion_diastolica", "frecuencia_cardiaca", "frecuencia_respiratoria",
    "temperatura", "saturacion_o2", "peso", "talla",
)

/** "37,5" → 37.5; vacío → null; basura → NaN (lo reporta la validación). */
fun aNumero(v: String?): Double? {
    if (v == null) return null
    val t = v.trim().replace(',', '.')
    if (t.isEmpty()) return null
    return t.toDoubleOrNull()?.takeIf { it.isFinite() } ?: Double.NaN
}

/** Talla en cm: si escriben "1.70" (metros) se entiende 170. */
fun tallaEnCm(v: Double?): Double? {
    if (v == null || v.isNaN()) return v
    return if (v > 0 && v < 3) round(v * 1000) / 10 else v
}

/** IMC con 2 decimales (peso en kg, talla en cm). */
fun calcularImc(pesoKg: Double?, tallaCm: Double?): Double? {
    if (pesoKg == null || tallaCm == null || pesoKg.isNaN() || tallaCm.isNaN() || pesoKg <= 0 || tallaCm <= 0) return null
    val m = tallaCm / 100
    val imc = pesoKg / (m * m)
    return if (imc.isFinite() && imc < 999) round(imc * 100) / 100 else null
}

/** Clasificación OMS del IMC en adultos (en menores no se clasifica). */
fun clasificarImc(imc: Double?, edad: Int?): String? {
    if (imc == null) return null
    if (edad != null && edad < 18) return null
    return when {
        imc < 18.5 -> "Bajo peso"
        imc < 25 -> "Normal"
        imc < 30 -> "Sobrepeso"
        imc < 35 -> "Obesidad I"
        imc < 40 -> "Obesidad II"
        else -> "Obesidad III"
    }
}

enum class NivelAlerta { ATENCION, ALERTA }

data class AlertaVital(val nivel: NivelAlerta, val texto: String)

/**
 * Lectura rápida de un signo vital (umbrales de ADULTO; ayuda visual, no
 * diagnóstico). En menores solo se leen temperatura y saturación. Sin valor o
 * fuera de lo posible → null (eso lo marca el rango).
 */
fun alertaVital(campo: String, valor: Double?, edad: Int?): AlertaVital? {
    if (valor == null || valor.isNaN()) return null
    val r = RANGO_VITAL[campo] ?: return null
    if (valor < r.min || valor > r.max) return null
    val menor = edad != null && edad < 18
    when (campo) {
        "temperatura" -> return when {
            valor >= 39 -> AlertaVital(NivelAlerta.ALERTA, "Fiebre alta")
            valor >= 38 -> AlertaVital(NivelAlerta.ALERTA, "Fiebre")
            valor >= 37.5 -> AlertaVital(NivelAlerta.ATENCION, "Febrícula")
            valor < 35 -> AlertaVital(NivelAlerta.ALERTA, "Hipotermia")
            else -> null
        }
        "saturacion_o2" -> return when {
            valor < 90 -> AlertaVital(NivelAlerta.ALERTA, "Muy baja")
            valor < 94 -> AlertaVital(NivelAlerta.ATENCION, "Baja")
            else -> null
        }
    }
    if (menor) return null
    return when (campo) {
        "presion_sistolica" -> when {
            valor >= 180 -> AlertaVital(NivelAlerta.ALERTA, "Crisis hipertensiva")
            valor >= 140 -> AlertaVital(NivelAlerta.ALERTA, "Elevada")
            valor >= 130 -> AlertaVital(NivelAlerta.ATENCION, "Algo elevada")
            valor < 90 -> AlertaVital(NivelAlerta.ATENCION, "Baja")
            else -> null
        }
        "presion_diastolica" -> when {
            valor >= 120 -> AlertaVital(NivelAlerta.ALERTA, "Crisis hipertensiva")
            valor >= 90 -> AlertaVital(NivelAlerta.ALERTA, "Elevada")
            valor >= 80 -> AlertaVital(NivelAlerta.ATENCION, "Algo elevada")
            valor < 60 -> AlertaVital(NivelAlerta.ATENCION, "Baja")
            else -> null
        }
        "frecuencia_cardiaca" -> when {
            valor > 100 -> AlertaVital(NivelAlerta.ATENCION, "Taquicardia")
            valor < 50 -> AlertaVital(NivelAlerta.ATENCION, "Bradicardia")
            else -> null
        }
        "frecuencia_respiratoria" -> when {
            valor > 20 -> AlertaVital(NivelAlerta.ATENCION, "Taquipnea")
            valor < 12 -> AlertaVital(NivelAlerta.ATENCION, "Bradipnea")
            else -> null
        }
        else -> null
    }
}

/** La peor de las dos lecturas de la presión (se leen juntas). */
fun alertaPresion(ps: Double?, pd: Double?, edad: Int?): AlertaVital? {
    val s = alertaVital("presion_sistolica", ps, edad)
    val d = alertaVital("presion_diastolica", pd, edad)
    if (s?.nivel == NivelAlerta.ALERTA) return s
    if (d?.nivel == NivelAlerta.ALERTA) return d
    return s ?: d
}

/** Nivel de la clasificación del IMC: Obesidad* = alerta; Bajo peso/Sobrepeso = atención. */
fun nivelImc(clasif: String?): NivelAlerta? {
    if (clasif == null || clasif == "Normal") return null
    return if (clasif.startsWith("Obesidad")) NivelAlerta.ALERTA else NivelAlerta.ATENCION
}

// ── Formulario de triaje ─────────────────────────────────────────────────────

/** ¿El número de esta columna está fuera de lo posible (o no es número)? Para pintar el campo en rojo. */
fun valorFueraDeRango(columna: String, texto: String?): Boolean {
    var n = aNumero(texto) ?: return false
    if (columna == "talla") n = tallaEnCm(n) ?: return false
    if (n.isNaN()) return true
    val r = rangoDe(columna) ?: return false
    return n < r.min || n > r.max
}

/**
 * Validación del triaje ANTES de enviar (misma regla que `validarAtencion` +
 * "Registra al menos una medición." de TriajeModal). Solo mira las columnas
 * de las mediciones que toma la clínica.
 */
fun validarTriaje(valores: Map<String, String>, campos: List<String>): List<String> {
    val columnas = columnasDeMediciones(campos)
    if (columnas.none { !valores[it].isNullOrBlank() }) return listOf("Registra al menos una medición.")
    val e = mutableListOf<String>()
    for (k in CAMPOS_VITALES) {
        if (k !in columnas) continue
        var n = aNumero(valores[k]) ?: continue
        if (k == "talla") n = tallaEnCm(n) ?: continue
        val r = RANGO_VITAL.getValue(k)
        if (n.isNaN()) e += "${r.nombre}: escribe solo el número."
        else if (n < r.min || n > r.max) e += "${r.nombre}: ${decimal(n)} ${r.unidad} está fuera de lo posible (${decimal(r.min)}–${decimal(r.max)})."
    }
    if ("perimetro_abdominal" in columnas) {
        val pa = aNumero(valores["perimetro_abdominal"])
        if (pa != null) {
            val r = RANGO_PERIMETRO
            if (pa.isNaN()) e += "${r.nombre}: escribe solo el número."
            else if (pa < r.min || pa > r.max) e += "${r.nombre}: ${decimal(pa)} cm está fuera de lo posible (${decimal(r.min)}–${decimal(r.max)})."
        }
    }
    if ("presion_sistolica" in columnas) {
        val ps = aNumero(valores["presion_sistolica"])
        val pd = aNumero(valores["presion_diastolica"])
        if ((ps == null) != (pd == null)) e += "Presión arterial: completa sistólica y diastólica."
        else if (ps != null && pd != null && !ps.isNaN() && !pd.isNaN() && pd >= ps) {
            e += "Presión arterial: la diastólica debe ser menor que la sistólica."
        }
    }
    return e
}

/**
 * `vitales` para POST /api/staff/atencion/triaje: SOLO las columnas de las
 * mediciones de la clínica (lo demás no se toca: una clínica que mide peso y
 * talla no borra la presión que el médico anotó). Vacío = null; talla en cm.
 */
fun cuerpoVitalesTriaje(valores: Map<String, String>, campos: List<String>): JsonObject = buildJsonObject {
    for (k in columnasDeMediciones(campos)) {
        var n = aNumero(valores[k])
        if (k == "talla") n = tallaEnCm(n)
        if (n == null || n.isNaN()) put(k, JsonNull) else put(k, n)
    }
}

/** Lo que se acaba de registrar, para pintarlo YA en la agenda (también sin señal). */
fun atencionLocalDeTriaje(citaId: String, valores: Map<String, String>, campos: List<String>, ahoraIso: String, previa: AtencionLlegada?): AtencionLlegada {
    val cols = columnasDeMediciones(campos).toSet()
    fun v(k: String, anterior: Double?): Double? {
        if (k !in cols) return anterior
        val n = aNumero(valores[k]).let { if (k == "talla") tallaEnCm(it) else it }
        return n?.takeUnless { it.isNaN() }
    }
    val peso = v("peso", previa?.peso)
    val talla = v("talla", previa?.talla)
    return AtencionLlegada(
        id = previa?.id, citaId = citaId,
        llegadaAt = previa?.llegadaAt ?: ahoraIso,
        triajeAt = ahoraIso,
        triajePorNombre = previa?.triajePorNombre,
        consultaInicioAt = previa?.consultaInicioAt,
        atendidaAt = previa?.atendidaAt,
        presionSistolica = v("presion_sistolica", previa?.presionSistolica),
        presionDiastolica = v("presion_diastolica", previa?.presionDiastolica),
        temperatura = v("temperatura", previa?.temperatura),
        saturacionO2 = v("saturacion_o2", previa?.saturacionO2),
        frecuenciaCardiaca = v("frecuencia_cardiaca", previa?.frecuenciaCardiaca),
        peso = peso, talla = talla,
        imc = calcularImc(peso, talla),
    )
}

/** Edad en años desde la fecha de nacimiento (o la edad guardada). Gemela de `edadDe`. */
fun edadDe(fechaNacimiento: String?, edad: Int?, hoyIso: String): Int? {
    val f = fechaNacimiento?.take(10)
    if (f != null && Regex("""^\d{4}-\d{2}-\d{2}$""").matches(f)) {
        val n = runCatching { LocalDate.parse(f) }.getOrNull()
        val h = runCatching { LocalDate.parse(hoyIso.take(10)) }.getOrNull()
        if (n != null && h != null) {
            var e = h.year - n.year
            if (h.monthNumber < n.monthNumber || (h.monthNumber == n.monthNumber && h.dayOfMonth < n.dayOfMonth)) e--
            return if (e in 0..129) e else null
        }
    }
    return edad
}

// ── Historial de signos vitales (ficha) ──────────────────────────────────────

/** Una toma de signos vitales (TomaVital de la web). */
data class TomaVital(
    val id: String,
    val citaId: String?,
    val fecha: String,
    val hora: String?,
    val triajeAt: String?,
    /** triaje_por_nombre (congelado) o, si no hubo triaje, quien atendió. */
    val tomadoPor: String?,
    val presionSistolica: Double? = null,
    val presionDiastolica: Double? = null,
    val frecuenciaCardiaca: Double? = null,
    val frecuenciaRespiratoria: Double? = null,
    val temperatura: Double? = null,
    val saturacionO2: Double? = null,
    val peso: Double? = null,
    val talla: Double? = null,
    val imc: Double? = null,
    val perimetroAbdominal: Double? = null,
)

/** Columnas numéricas de una toma (CAMPOS_NUMERICOS de la web). */
val CAMPOS_NUMERICOS_TOMA: List<String> = listOf(
    "presion_sistolica", "presion_diastolica", "frecuencia_cardiaca", "frecuencia_respiratoria",
    "temperatura", "saturacion_o2", "peso", "talla", "imc", "perimetro_abdominal",
)

/** SELECT_TOMAS_VITALES de la web. */
val SELECT_TOMAS_VITALES: String =
    "id, cita_id, fecha, hora, triaje_at, triaje_por_nombre, " + CAMPOS_NUMERICOS_TOMA.joinToString(", ") +
        ", terapeuta:terapeutas(nombre)"

/** Tope de tomas (las más recientes). */
const val MAX_TOMAS = 200

/** Fila cruda → TomaVital. */
fun aTomaVital(o: JsonObject): TomaVital? {
    val id = o.txt("id") ?: return null
    val t = o["terapeuta"]
    val quienAtendio = when (t) {
        is JsonObject -> t.txt("nombre")
        is JsonArray -> (t.firstOrNull() as? JsonObject)?.txt("nombre")
        else -> null
    }
    val peso = o.num("peso")
    val talla = o.num("talla")
    return TomaVital(
        id = id,
        citaId = o.txt("cita_id"),
        fecha = (o.txt("fecha") ?: "").take(10),
        hora = o.txt("hora"),
        triajeAt = o.txt("triaje_at"),
        tomadoPor = o.txt("triaje_por_nombre") ?: quienAtendio,
        presionSistolica = o.num("presion_sistolica"),
        presionDiastolica = o.num("presion_diastolica"),
        frecuenciaCardiaca = o.num("frecuencia_cardiaca"),
        frecuenciaRespiratoria = o.num("frecuencia_respiratoria"),
        temperatura = o.num("temperatura"),
        saturacionO2 = o.num("saturacion_o2"),
        peso = peso,
        talla = talla,
        imc = o.num("imc") ?: calcularImc(peso, talla),
        perimetroAbdominal = o.num("perimetro_abdominal"),
    )
}

/** Un dato de la última toma, listo para pintar ("PA 138/86" con su color). */
data class DatoToma(val clave: String, val etiqueta: String, val valor: String, val alerta: AlertaVital?)

/** Los datos de una toma: "PA 138/86", "T° 36.5 °C", "IMC 29.1 Sobrepeso"… (gemela de `datosDeToma`). */
fun datosDeToma(t: TomaVital, edad: Int?): List<DatoToma> {
    val d = mutableListOf<DatoToma>()
    if (t.presionSistolica != null && t.presionDiastolica != null) {
        d += DatoToma("pa", "PA", "${decimal(t.presionSistolica)}/${decimal(t.presionDiastolica)}", alertaPresion(t.presionSistolica, t.presionDiastolica, edad))
    }
    t.frecuenciaCardiaca?.let { d += DatoToma("frecuencia_cardiaca", "FC", "${decimal(it)} lpm", alertaVital("frecuencia_cardiaca", it, edad)) }
    t.frecuenciaRespiratoria?.let { d += DatoToma("frecuencia_respiratoria", "FR", "${decimal(it)} rpm", alertaVital("frecuencia_respiratoria", it, edad)) }
    t.temperatura?.let { d += DatoToma("temperatura", "T°", "${fijo(it, 1)} °C", alertaVital("temperatura", it, edad)) }
    t.saturacionO2?.let { d += DatoToma("saturacion_o2", "SatO₂", "${decimal(it)}%", alertaVital("saturacion_o2", it, edad)) }
    t.peso?.let { d += DatoToma("peso", "Peso", "${decimal(it)} kg", null) }
    t.imc?.let { imc ->
        val c = clasificarImc(imc, edad)
        val nivel = nivelImc(c)
        d += DatoToma("imc", "IMC", fijo(imc, 1), if (c != null && nivel != null) AlertaVital(nivel, c) else null)
    }
    t.perimetroAbdominal?.let { d += DatoToma("perimetro_abdominal", "P. abd.", "${decimal(it)} cm", null) }
    return d
}

/** Punto de los gráficos. Presión: ambas o ninguna (una sola no dice nada). */
data class PuntoVital(val id: String, val fecha: String, val sistolica: Double?, val diastolica: Double?, val peso: Double?, val imc: Double?)

/** Serie cronológica para los gráficos. Una toma sin el dato deja hueco, no cero. */
fun seriesVitales(tomas: List<TomaVital>): List<PuntoVital> = tomas.map { t ->
    val pa = t.presionSistolica != null && t.presionDiastolica != null
    PuntoVital(
        id = t.id, fecha = t.fecha,
        sistolica = if (pa) t.presionSistolica else null,
        diastolica = if (pa) t.presionDiastolica else null,
        peso = t.peso,
        imc = t.imc?.let { round(it * 10) / 10 },
    )
}

/** ¿Hay al menos [min] puntos con este dato? ('sistolica' | 'peso' | 'imc'). */
fun puntosCon(serie: List<PuntoVital>, campo: String, min: Int = 2): Boolean =
    serie.count {
        when (campo) {
            "sistolica" -> it.sistolica != null
            "peso" -> it.peso != null
            "imc" -> it.imc != null
            else -> false
        }
    } >= min

/** Eje Y con marcas redondas (gemela de `escalaY`): presión 60–160 cada 20; peso ±3; IMC ±1.5. */
data class EscalaY(val lo: Double, val hi: Double, val ticks: List<Double>)

fun escalaY(tipo: String, valores: List<Double?>): EscalaY {
    val v = valores.filterNotNull().filter { it.isFinite() }
    val min = v.minOrNull() ?: 0.0
    val max = v.maxOrNull() ?: 0.0
    val lo: Double
    val hi: Double
    val paso: Double
    if (tipo == "presion") {
        paso = 20.0
        lo = minOf(60.0, floor(min / paso) * paso)
        hi = maxOf(160.0, ceil(max / paso) * paso)
    } else {
        val margen = if (tipo == "peso") 3.0 else 1.5
        val a = maxOf(0.0, min - margen)
        val b = max + margen
        paso = listOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0).firstOrNull { (b - a) / it <= 5 } ?: 100.0
        lo = floor(a / paso) * paso
        hi = ceil(b / paso) * paso
    }
    val ticks = mutableListOf<Double>()
    var t = lo
    while (t <= hi + 1e-9) {
        ticks += round(t * 10) / 10
        t += paso
    }
    return EscalaY(lo, hi, ticks)
}

// ── Recetas (staff, solo lectura) ────────────────────────────────────────────

/** "N° 000123" — el correlativo lo asigna la base; esto solo lo muestra. */
fun formatearNumeroReceta(n: Int?): String {
    if (n == null || n < 1) return "N° —"
    return "N° ${n.toString().padStart(6, '0')}"
}

/** Vigente = emitida y hoy ≤ válida hasta (el último día cuenta). */
fun recetaVigente(estado: String?, validaHasta: String?, hoyIso: String): Boolean {
    if (estado == "Anulada") return false
    val v = validaHasta?.take(10) ?: return false
    if (v.length < 10) return false
    return hoyIso.take(10) <= v
}

// ── Espacio de documentos (lib/documentos-espacio.ts, lib/plan.ts) ───────────

/** "1 GB" / "500 MB" (textoEspacioMB). */
fun textoEspacioMB(mb: Int): String = if (mb >= 1024 && mb % 1024 == 0) "${mb / 1024} GB" else "$mb MB"

/** "820 KB", "12,5 MB", "1,02 GB" (formatearBytes, con coma decimal como es-PE). */
fun formatearBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    if (kb < 1024) return "${maxOf(0, round(kb).toLong())} KB"
    val mb = kb / 1024
    if (mb < 1024) return "${decimal(round(mb * 10) / 10).replace('.', ',')} MB"
    val gb = mb / 1024
    return "${(round(gb * 100) / 100).let { if (it == floor(it)) it.toLong().toString() else it.toString() }.replace('.', ',')} GB"
}

/** Mensaje cuando el archivo no entra (mensajeSinEspacio): claro y con salida. */
fun mensajeSinEspacio(limiteMb: Int): String =
    "Se llenó el espacio de documentos de tu plan (${textoEspacioMB(limiteMb)}). " +
        "Borra documentos que ya no necesites o pasa a Premium o Plus para espacio sin límite."

// ── Números (sin String.format, que no existe en common) ────────────────────

/** n con [dec] decimales fijos ("36.5", "37.0"), como toFixed. */
fun fijo(n: Double, dec: Int = 1): String {
    val f = 10.0.pow(dec)
    val escalado = round(abs(n) * f).toLong()
    val div = f.toLong()
    val signo = if (n < 0 && escalado != 0L) "-" else ""
    return if (dec == 0) "$signo$escalado" else "$signo${escalado / div}.${(escalado % div).toString().padStart(dec, '0')}"
}

/** Entero sin decimales ("82"); si no, un decimal sin ".0" ("82.5"). */
fun decimal(n: Double): String = if (n == floor(n) && abs(n) < 1e15) n.toLong().toString() else fijo(n, 1).removeSuffix(".0")
