package pe.saniape.app.ui.clinica.atencion

// Sustancias sujetas a fiscalización sanitaria (DS 023-2001-SA, Anexo N° 2) —
// gemelo de lib/receta-controlados.ts + controlDeSustancia (lib/recetas.ts) de la
// web. Si cambia allá, cambia acá. La app solo lo usa para AVISAR (el chip
// "Lista IVB" en las sugerencias): quien valida al emitir es el servidor.
//
//  · IIA, IIIA, IIIB, IIIC → receta ESPECIAL del MINSA: Sania no la emite.
//  · IIB, IVA, IVB, VI → receta común con aviso (la farmacia la retiene).

internal val LISTAS_RECETA_ESPECIAL = listOf("IIA", "IIIA", "IIIB", "IIIC")

internal val SUSTANCIAS_CONTROLADAS: Map<String, List<String>> = mapOf(
    "IIA" to listOf(
        "cocaina", "dextromoramida", "fentanilo", "levorfanol", "metadona", "morfina", "opio",
        "oxicodona", "petidina", "remifentanilo", "sufentanilo",
    ),
    "IIB" to listOf(
        "codeina", "acetildihidrocodeina", "dextropropoxifeno", "difenoxilato", "difenoxina",
        "dihidrocodeina", "etilmorfina", "folcodina", "nicocodina", "nicodicodina", "norcodeina",
        "hidrocodona", "propiramo",
    ),
    "IIIA" to listOf(
        "anfepramona", "benzfetamina", "catina", "dexanfetamina", "etinamato", "etilanfetamina",
        "fenetilina", "fenproporex", "levometanfetamina", "fentermina", "mefenorex", "mazindol",
        "metilfenidato", "pemolina", "zipeprol",
    ),
    "IIIB" to listOf(
        "allobarbital", "amobarbital", "aprobarbital", "barbital", "buprenorfina", "butalbital",
        "ciclobarbital", "flunitrazepam", "glutetimida", "hexobarbital", "meprobamato", "metabarbital",
        "pentazocina", "pentobarbital", "secbutabarbital", "secobarbital", "vinilbital",
    ),
    "IIIC" to listOf(
        "etclorvinol", "fenobarbital", "glucotimida", "metilfenobarbital", "metiprilona",
        "tiopental", "pipradrol",
    ),
    // IVA: preparados de las sustancias de IIB (se tratan con la regla de IIB).
    "IVA" to emptyList(),
    "IVB" to listOf(
        "acido gamma-hidroxibutirico", "alprazolam", "aminorex", "amitriptilina", "anfebutamona",
        "bentazepam", "bromazepam", "bromperidol", "brotizolam", "bupropion", "buspirona", "camazepam",
        "clobazam", "clomipramina", "clonazepam", "clorazepato", "clordiazepoxido", "clotiazepam",
        "cloxazolam", "clozapina", "delorazepam", "desipramina", "diazepam", "doxepina", "droperidol",
        "efedrina", "ergometrina", "ergotamina", "estazolam", "flubentixol", "fludiazepam", "flufenazina",
        "fluoxetina", "flurazepam", "halazepam", "haloperidol", "haloxazolam", "imipramina", "ketazolam",
        "levomepromazina", "levopromazina", "lofazepato", "loprazolam", "lorazepam", "lormetazepam",
        "loxapina", "maprotilina", "medazepam", "mesocarbo", "moclobemida", "mianserina", "midazolam",
        "nefazodona", "nimetazepam", "nitrazepam", "nordazepam", "norefedrina", "nortriptilina",
        "opipramol", "oxazepam", "oxazolam", "paroxetina", "periciacina", "periciazina", "pimozida",
        "pinazepam", "pipotiacina", "pipradol", "prazepam", "pseudoefedrina", "seudoefedrina",
        "sertralina", "sibutramina", "sulpirida", "temazepam", "tetrazepam", "tilidina",
        "tioproperazina", "tioridazina", "tramadol", "trazodona", "triazolam", "tiazolam",
        "trifluoperazina", "trihexifenidilo", "trihexifenidil", "trimipramina", "valproato",
        "acido valproico", "veraliprida", "viloxacina", "zaleplon", "zopiclona", "zolpidem",
    ),
    "VI" to listOf("nalorfina", "levalorfan", "naloxona", "ciclazocina", "diprenorfina", "apomorfina"),
)

/** La lista en que cae un DCI: [especial] = receta especial (Sania no la emite). */
internal data class ControlSustancia(val lista: String, val sustancia: String, val especial: Boolean)

private val NO_ALFANUM = Regex("[^a-z0-9]+")

/**
 * ¿El DCI es una sustancia controlada? Compara palabras completas sin tildes ni
 * mayúsculas ("Clonazepam 2 mg" → IVB). Primero las de receta especial.
 */
internal fun controlDeSustancia(dci: String?): ControlSustancia? {
    val texto = " ${sinTildes(dci.orEmpty()).replace(NO_ALFANUM, " ")} "
    if (texto.isBlank()) return null
    for (lista in LISTAS_RECETA_ESPECIAL + listOf("IIB", "IVA", "IVB", "VI")) {
        for (s in SUSTANCIAS_CONTROLADAS[lista].orEmpty()) {
            if (" ${sinTildes(s).replace(NO_ALFANUM, " ")} " in texto) {
                return ControlSustancia(lista, s, lista in LISTAS_RECETA_ESPECIAL)
            }
        }
    }
    return null
}
