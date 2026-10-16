package pe.saniape.app.data

// ─────────────────────────────────────────────────────────────────────────────
// Los MISMOS casos que supabase/fixes/sueltas-prueba-gemelo.sql (web), el que
// prueba public.acordado_suelta y public.costo_sesion_al_cobrar en la base.
// Generado del .sql tal cual: si la web agrega un caso, se agrega aquí.
// "pagada" = la sesión tiene un pago ligado con monto > 0.
// ─────────────────────────────────────────────────────────────────────────────

internal data class S(
    val id: String?, val estado: String?, val costo: Double?, val numero: Int?, val fecha: String?, val pagada: Boolean,
)

internal data class CasoAcordado(
    val nombre: String, val pactado: Double, val precioAcordado: Double?, val precioPorSesion: Double?,
    val totalSesiones: Int?, val sesiones: List<S>, val incluir: String?, val esperado: Double,
)

internal data class CasoCosto(
    val nombre: String, val modalidad: String, val precioAcordado: Double?, val precioPorSesion: Double?,
    val totalSesiones: Int?, val monto: Double, val esperado: Double,
)

// 26 casos de acordado_suelta + 5 de costo_sesion_al_cobrar
internal val CASOS_ACORDADO_FIXTURE = listOf(
    CasoAcordado("OMER: sesión 1 atendida", pactado = 100.0, precioAcordado = null, precioPorSesion = 100.0, totalSesiones = 1,
        sesiones = listOf(
            S("8b3d4bd9-6288-5d5e-b422-5af1173016df", "Completada", 100.0, 1, null, false),
            S("e3c6a636-44e0-5ebe-aef6-65ba2becad4b", "Planificada", 100.0, 2, null, false)), incluir = null, esperado = 100.0),
    CasoAcordado("OMER: sesiones 1 y 2 atendidas → 200", pactado = 100.0, precioAcordado = null, precioPorSesion = 100.0, totalSesiones = 1,
        sesiones = listOf(
            S("7c35c586-5488-50fc-8824-ed1f5c49c756", "Completada", 100.0, 1, null, false),
            S("c82b3b54-79e1-5856-be95-06a1b24513d4", "Completada", 100.0, 2, null, false)), incluir = null, esperado = 200.0),
    CasoAcordado("OMER: al cerrar la 2ª, cuenta", pactado = 100.0, precioAcordado = null, precioPorSesion = 100.0, totalSesiones = 1,
        sesiones = listOf(
            S("a93bbf9f-c49e-5d79-ad11-6cb76bdfb028", "Completada", 100.0, 1, null, false),
            S("f2cad14a-ad27-52d6-b081-7c718318b9c1", "Planificada", 100.0, 2, null, false)), incluir = "f2cad14a-ad27-52d6-b081-7c718318b9c1", esperado = 200.0),
    CasoAcordado("programadas sin pagar no suman", pactado = 100.0, precioAcordado = null, precioPorSesion = 100.0, totalSesiones = 1,
        sesiones = listOf(
            S("05e718c8-0134-562b-bd85-113317861666", "Completada", 100.0, null, null, false),
            S("b8f0e690-fb9b-5dad-ad8b-8c19bbae688c", "Planificada", 100.0, null, null, false),
            S("6b44f700-f80a-590e-9b73-33cd99e5bede", "Planificada", 100.0, null, null, false),
            S("15d21984-13cc-54ea-a3e8-42610d522892", "Reprogramada", 100.0, null, null, false)), incluir = null, esperado = 100.0),
    CasoAcordado("adelantada (pago ligado) suma", pactado = 100.0, precioAcordado = null, precioPorSesion = 100.0, totalSesiones = 1,
        sesiones = listOf(
            S("c7b8ae29-1972-580b-8624-c6a256d566cc", "Completada", 100.0, null, null, false),
            S("7b62b2c7-eaa8-5c17-9f57-8a4ee6641bec", "Planificada", 100.0, null, null, true)), incluir = null, esperado = 200.0),
    CasoAcordado("Reprogramada con pago suma", pactado = 100.0, precioAcordado = null, precioPorSesion = 100.0, totalSesiones = 1,
        sesiones = listOf(
            S("76e95a4a-cf91-50e8-a943-57e400b1282e", "Completada", 100.0, null, null, false),
            S("0fac62b0-8423-5f75-b16b-88cc4c620c84", "Reprogramada", 100.0, null, null, true)), incluir = null, esperado = 200.0),
    CasoAcordado("Cancelada y No asistió con pago no suman", pactado = 100.0, precioAcordado = null, precioPorSesion = 100.0, totalSesiones = 1,
        sesiones = listOf(
            S("aabdffd8-a247-51bb-91d7-d404938c3f7b", "Completada", 100.0, null, null, true),
            S("96976e94-9c83-5281-b2ab-d200dd608d25", "Cancelada", 100.0, null, null, true),
            S("b4299531-7a23-5ee8-925b-f898edd1182d", "No asistió", 100.0, null, null, true)), incluir = null, esperado = 100.0),
    CasoAcordado("cancelada que se \"cierra\" no suma", pactado = 100.0, precioAcordado = null, precioPorSesion = 100.0, totalSesiones = 1,
        sesiones = listOf(
            S("362d2e4a-a0ec-5816-a370-7d84879f88fe", "Completada", 100.0, null, null, false),
            S("7f9dd8bb-478a-59ec-b9e9-8dd147a00f32", "Cancelada", 100.0, null, null, false)), incluir = "7f9dd8bb-478a-59ec-b9e9-8dd147a00f32", esperado = 100.0),
    CasoAcordado("costos distintos", pactado = 100.0, precioAcordado = null, precioPorSesion = 100.0, totalSesiones = 1,
        sesiones = listOf(
            S("d6c8a866-3b58-53d8-bf08-0d22cb624a45", "Completada", 100.0, null, null, false),
            S("91c09e57-0a11-58e6-82a0-e980198ca638", "Completada", 150.0, null, null, false),
            S("14553a9c-bbd5-5adc-986b-2618dc9a2da8", "Completada", 80.0, null, null, false)), incluir = null, esperado = 330.0),
    CasoAcordado("costo 0 o vacío = precio de la sesión", pactado = 100.0, precioAcordado = null, precioPorSesion = 100.0, totalSesiones = 1,
        sesiones = listOf(
            S("c4cbadf9-6fe1-5a9d-802b-37ff64fba9cf", "Completada", 0.0, null, null, false),
            S("8d7bdeee-e471-5120-9b77-4e64b520beb7", "Completada", null, null, null, false)), incluir = null, esperado = 200.0),
    CasoAcordado("PodoSalud: sin precio por sesión", pactado = 0.0, precioAcordado = null, precioPorSesion = null, totalSesiones = null,
        sesiones = listOf(
            S("f208ff90-53d3-5f95-a7aa-d0496b1b42fe", "Completada", 50.0, null, null, true),
            S("910d36d2-8b2f-5cd6-9b6a-75b9d5029efd", "Completada", 50.0, null, null, false),
            S("787273f2-a70b-50a2-9ceb-0350821efc07", "Planificada", 50.0, null, null, false)), incluir = null, esperado = 100.0),
    CasoAcordado("García: negociado 1500, lista 1800 (planificada)", pactado = 1500.0, precioAcordado = 1500.0, precioPorSesion = 1800.0, totalSesiones = 1,
        sesiones = listOf(
            S("187e516e-e7ba-5fab-ae5f-6e58d26e5f16", "Planificada", 1800.0, null, null, false)), incluir = null, esperado = 1500.0),
    CasoAcordado("García: negociado 1500, una atendida", pactado = 1500.0, precioAcordado = 1500.0, precioPorSesion = 1800.0, totalSesiones = 1,
        sesiones = listOf(
            S("17455949-0337-5214-93a5-e92402a56100", "Completada", 1800.0, null, null, false)), incluir = null, esperado = 1500.0),
    CasoAcordado("García: negociado 1500, dos atendidas", pactado = 1500.0, precioAcordado = 1500.0, precioPorSesion = 1800.0, totalSesiones = 1,
        sesiones = listOf(
            S("09542be7-0318-5eb2-9e91-a35d52277b79", "Completada", 1800.0, null, null, false),
            S("085e6b8f-0bd9-5e5d-833f-6a637d6e0062", "Completada", 1800.0, null, null, false)), incluir = null, esperado = 3000.0),
    CasoAcordado("serie de 1800 con sesiones de 380: 4 atendidas", pactado = 1800.0, precioAcordado = 1800.0, precioPorSesion = 380.0, totalSesiones = 1,
        sesiones = listOf(
            S("6e3a3ca7-a4c4-5eba-8de2-ba8085e92ff7", "Completada", 380.0, 1, null, false),
            S("b0e3a105-c6f3-51c7-a033-b86193be99d3", "Completada", 380.0, 2, null, false),
            S("bee4c3ed-ad48-5e36-88d3-65bf13c7c548", "Completada", 380.0, 3, null, false),
            S("44ede207-37c7-5834-9f9c-ecc0c04e90fe", "Completada", 380.0, 4, null, false)), incluir = null, esperado = 1800.0),
    CasoAcordado("serie de 1800: 5 atendidas", pactado = 1800.0, precioAcordado = 1800.0, precioPorSesion = 380.0, totalSesiones = 1,
        sesiones = listOf(
            S("837f9260-8a99-5df5-925d-4088b384fe2a", "Completada", 380.0, 1, null, false),
            S("4da5ef4f-3102-5902-a5f8-b5c8853111de", "Completada", 380.0, 2, null, false),
            S("c084d0d1-1ff1-5429-b1ca-af60a4df5b41", "Completada", 380.0, 3, null, false),
            S("667f690e-df00-5895-8737-6814c780e5cc", "Completada", 380.0, 4, null, false),
            S("6b732693-13c8-5ef0-9044-e6e93a7edd5b", "Completada", 380.0, 5, null, false)), incluir = null, esperado = 1900.0),
    CasoAcordado("OMER ampliada 3 por 900, 1 atendida", pactado = 900.0, precioAcordado = 900.0, precioPorSesion = 300.0, totalSesiones = 3,
        sesiones = listOf(
            S("5ecf5399-0202-50e7-9cd5-d9ee4ddad1fd", "Completada", 300.0, 1, null, false),
            S("695d6983-fb68-509c-a4b9-ae4e29799b20", "Planificada", 300.0, 2, null, false)), incluir = null, esperado = 900.0),
    CasoAcordado("DALU 1e6f4d90: ampliada +1 por S/0 (cortesía), 2 atendidas de 100", pactado = 100.0, precioAcordado = 100.0, precioPorSesion = 100.0, totalSesiones = 2,
        sesiones = listOf(
            S("600ac709-a44b-539b-bf21-5bf993357fd1", "Completada", 100.0, 1, null, false),
            S("4c876565-50a8-5a84-a833-e926ebbade11", "Completada", 100.0, 2, null, false)), incluir = null, esperado = 100.0),
    CasoAcordado("ampliada por 2: la 3ª excede el pacto", pactado = 200.0, precioAcordado = 200.0, precioPorSesion = 100.0, totalSesiones = 2,
        sesiones = listOf(
            S("8472b7ae-898c-5667-ba58-107b523287dd", "Completada", 100.0, 1, null, false),
            S("27077efe-2857-5c28-90e8-c2129336de91", "Completada", 100.0, 2, null, false),
            S("a3caff55-ca6d-565e-acd0-bb7a6958c992", "Completada", 100.0, 3, null, false)), incluir = null, esperado = 300.0),
    CasoAcordado("pacto por 2: excede la de mayor número (150)", pactado = 200.0, precioAcordado = 200.0, precioPorSesion = 100.0, totalSesiones = 2,
        sesiones = listOf(
            S("79a380ef-1ae7-53a5-9a05-f9c4277de237", "Completada", 100.0, 1, null, false),
            S("a73e9a35-7b85-5ef9-a68f-ef7be16e17c4", "Completada", 100.0, 2, null, false),
            S("08268d32-69ba-55bb-a413-ef0070519b41", "Completada", 150.0, 3, null, false)), incluir = null, esperado = 350.0),
    CasoAcordado("pacto por 2: excede la de mayor número (100)", pactado = 200.0, precioAcordado = 200.0, precioPorSesion = 100.0, totalSesiones = 2,
        sesiones = listOf(
            S("228d4600-0590-5317-8675-b245c483abe1", "Completada", 150.0, 1, null, false),
            S("be2fce93-1639-531e-910b-8770f644d644", "Completada", 100.0, 2, null, false),
            S("ec40368a-14a0-544a-816d-6dbb388d8143", "Completada", 100.0, 3, null, false)), incluir = null, esperado = 300.0),
    CasoAcordado("pacto por 2: sin número va al final; desempata la fecha", pactado = 200.0, precioAcordado = 200.0, precioPorSesion = 100.0, totalSesiones = 2,
        sesiones = listOf(
            S("2a65554e-c0d5-50eb-ac0a-9a8b0ed9be9e", "Completada", 120.0, null, "2026-10-01", false),
            S("914a686a-7146-5f58-9cd7-14bd8d520e17", "Completada", 100.0, 1, null, false),
            S("c6bf0880-7940-5900-b915-706b2e2de162", "Completada", 100.0, 2, null, false)), incluir = null, esperado = 320.0),
    CasoAcordado("pacto por 2: la que se cierra excede", pactado = 200.0, precioAcordado = 200.0, precioPorSesion = 100.0, totalSesiones = 2,
        sesiones = listOf(
            S("f2e736cd-9dc3-5d09-995a-418bb085a85d", "Completada", 100.0, 1, null, false),
            S("fad35f36-97ae-5c69-a733-da31cc1c7fdf", "Completada", 100.0, 2, null, false),
            S("2f8f0ef7-2b5b-586b-bda6-ef4e3cb5974a", "Planificada", 100.0, 3, null, false)), incluir = "2f8f0ef7-2b5b-586b-bda6-ef4e3cb5974a", esperado = 300.0),
    CasoAcordado("DALU 03e70ebf: pacto 200 por 2, costos 0 y 100", pactado = 200.0, precioAcordado = 200.0, precioPorSesion = 100.0, totalSesiones = 2,
        sesiones = listOf(
            S("752d8b88-5b7a-5199-af41-fbda48481129", "Completada", 0.0, 1, null, false),
            S("f1156b9c-85d3-55cc-b9d9-ebce7618026c", "Completada", 100.0, 2, null, false)), incluir = null, esperado = 200.0),
    CasoAcordado("suelta recién creada", pactado = 100.0, precioAcordado = null, precioPorSesion = 100.0, totalSesiones = 1,
        sesiones = listOf(), incluir = null, esperado = 100.0),
    CasoAcordado("decimales: pacto 100 por 3, 4 sin costo", pactado = 100.0, precioAcordado = 100.0, precioPorSesion = null, totalSesiones = 3,
        sesiones = listOf(
            S("31051c91-706b-5b43-bc76-1c59d6e03c9c", "Completada", null, 1, null, false),
            S("b8e6b353-c930-5704-a87c-1f7fe8ea8d4c", "Completada", null, 2, null, false),
            S("245eb14e-d86e-50d3-bcd7-ef35fb52fc54", "Completada", null, 3, null, false),
            S("37a9f0c4-ef6a-5814-bd49-d810ff0927a4", "Completada", null, 4, null, false)), incluir = null, esperado = 133.33),
)
internal val CASOS_COSTO_FIXTURE = listOf(
    CasoCosto("suelta: parcial 60 de 100 → 100", "Sesión suelta", precioAcordado = null, precioPorSesion = 100.0, totalSesiones = 1, monto = 60.0, esperado = 100.0),
    CasoCosto("suelta: cobro mayor 120 → 120", "Sesión suelta", precioAcordado = null, precioPorSesion = 100.0, totalSesiones = 1, monto = 120.0, esperado = 120.0),
    CasoCosto("suelta negociada 1500: parcial 1000 → 1500", "Sesión suelta", precioAcordado = 1500.0, precioPorSesion = 1800.0, totalSesiones = 1, monto = 1000.0, esperado = 1500.0),
    CasoCosto("suelta sin precio: lo cobrado", "Sesión suelta", precioAcordado = null, precioPorSesion = null, totalSesiones = null, monto = 60.0, esperado = 60.0),
    CasoCosto("paquete: lo cobrado, como siempre", "Paquete", precioAcordado = null, precioPorSesion = 100.0, totalSesiones = 5, monto = 60.0, esperado = 60.0),
)
