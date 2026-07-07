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
}
