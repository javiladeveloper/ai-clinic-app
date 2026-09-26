package pe.saniape.app.data

/**
 * Almacén de preferencias simples (modo activo de la cuenta). expect/actual:
 * en Android usa SharedPreferences. El modo NO es seguridad (la RLS lo valida);
 * solo recuerda en qué portal estaba el usuario.
 */
expect object Preferencias {
    /** "clinica" | "paciente" | null (no elegido aún). */
    fun modoActivo(): String?
    fun setModoActivo(modo: String?)
    /** Tema elegido: "sistema" | "claro" | "oscuro" | null (= sistema). */
    fun tema(): String?
    fun setTema(tema: String?)
    /**
     * Marca de la ÚLTIMA clínica activa (logo URL + nombre), para que la intro al
     * reabrir la app ya muestre el branding de la clínica (no el de Sania) antes de
     * que cargue el contexto. NO es seguridad: solo cosmético. Se limpia al cerrar sesión.
     */
    fun logoClinica(): String?
    fun setLogoClinica(url: String?)
    fun nombreClinica(): String?
    fun setNombreClinica(nombre: String?)

    /**
     * versionName cuyas "Novedades" ya se mostraron (o se saltaron). NO se limpia
     * al cerrar sesión: es del teléfono, no de la cuenta. Ver [Novedades].
     */
    fun ultimaNovedadVista(): String?
    fun setUltimaNovedadVista(version: String)

    /**
     * true si la app se instaló por primera vez y nunca se actualizó (en ese
     * caso no hay "novedades" que contar). Android: firstInstallTime ==
     * lastUpdateTime. iOS: no hay dato del sistema; se infiere de que no haya
     * ninguna preferencia guardada todavía.
     */
    fun esInstalacionNueva(): Boolean
}
