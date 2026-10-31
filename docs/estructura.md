# Obras: estructura y librerías

Obligatorio para todo el código de este servicio. Si algo no encaja, propón el cambio antes de salirte de esta estructura.

## Librerías

Starters de Spring Boot 4.1 (web MVC, security OAuth2 resource server, data JPA, AMQP, validation, actuator), Flyway, MapStruct 1.6.3, `springdoc-openapi-starter-webmvc-ui` 3.2 (Swagger UI en `/docs`, solo perfil `dev`), AWS SDK v2 `s3`, y para pruebas JUnit 5 + AssertJ, Testcontainers 2 (PostgreSQL y RabbitMQ) y ArchUnit 1.5. Sin Lombok: DTO y objetos de valor son `record`.

## Árbol

```
src/main/java/com/constructora/obras/
  ObrasApplication.java
  compartido/                      # lo que usan todos los contextos
    dominio/        ReglaNegocioException.java, Dinero.java (redondeo HALF_UP a 2), Periodo.java (AAAA-MM), Reloj.java (puerto de la hora), Usuario.java (id y roles efectivos), EventoDominio.java, ComandoDominio.java (008: comando de la saga, clave comando.<destino>.<Tipo>),
                    AlmacenArchivos.java (puerto del almacenamiento de objetos: URL firmadas de subida y descarga),
                    Paginacion.java (pagina, tamano, orden, direccion; PAGINACION_INVALIDA), Pagina.java (items, total, pagina, tamano)
    infraestructura/
      RelojSistema.java
      seguridad/    ConfiguracionSeguridad.java (rutas, JwtDecoder con emisor y audiencia), ConvertidorRolesKeycloak.java (realm_access.roles → ROLE_<rol>), RolesEfectivos.java (regla del azp), UsuarioActual.java
      web/          ManejadorErrores.java (ProblemDetail con "codigo" y "errores"), RespuestaProblema.java (401/403 desde los filtros), FiltroRequestId.java, SaludController.java
      mensajeria/   ConfiguracionRabbit.java, Sobre.java, Outbox.java (guarda en outbox_evento con su clave de enrutamiento), PublicadorOutbox.java (envía cada 1 s con la clave guardada), OutboxEventoEntity.java, OutboxEventoJpaRepository.java,
                    ReceptorMensajes.java (cola obras: inbox, reintentos y fallidos), ManejadorMensaje.java (un consumidor por tipo), MensajeInvalidoException.java,
                    (ConfiguracionRabbit enlaza la cola obras con las respuestas al contrato, 002, y a la saga, 008)
                    MensajeProcesadoEntity.java, MensajeProcesadoJpaRepository.java
      archivos/     ConfiguracionS3.java (OBRAS_S3_*; sin bucket o claves arranca igual y las firmas responden 503),
                    AlmacenArchivosS3.java (firma con S3Presigner, sin red)
  obra/                            # un paquete por contexto: obra, avance, planilla, saga, diario (capa 1)
    dominio/        Obra.java, Partida.java, InsumoPrevisto.java, EstadoObra.java, Asignaciones.java, ContratoCopia.java, ObraRegistrada.java (evento), MaterialPrevisto.java (006)
    aplicacion/
      puerto/entrada/   RegistrarObraCasoUso.java, ConsultarObraCasoUso.java, RegistrarRespuestaContratoCasoUso.java, ObraDetalle.java (indicadores y última planilla), FiltroObras.java (estado y búsqueda de GET /obras), ConsultarMaterialPrevistoCasoUso.java
      puerto/salida/    ObraRepositorio.java, PublicadorEventos.java, PlanillasObraRepositorio.java (006: lectura de planillas para avance, última planilla y material previsto)
      servicio/         RegistrarObraServicio.java, ConsultarObraServicio.java, RespuestaContratoServicio.java, ConsultarMaterialPrevistoServicio.java
    infraestructura/
      web/              ObraController.java, dto/RegistrarObraRequest.java, dto/ObraResponse.java, dto/ObraResumenResponse.java, dto/MaterialPrevistoResponse.java, ObraWebMapper.java
      persistencia/     ObraEntity.java, PartidaEntity.java, InsumoPrevistoEntity.java, ObraJpaRepository.java (con Specification para visibilidad y filtros), ObraRepositorioJpa.java, ObraPersistenciaMapper.java
      mensajeria/       PublicadorEventosOutbox.java, ContratoRegistradoConsumidor.java, ContratoRechazadoConsumidor.java
  avance/                          # 005: fotos de avance (usa ObraRepositorio del contexto obra para la visibilidad)
    dominio/        FotoAvance.java (clave fotos/{obraId}/{fotoId}.jpg, partida de la obra)
    aplicacion/
      puerto/entrada/   RegistrarFotoCasoUso.java, ConsultarFotosCasoUso.java
      puerto/salida/    FotoAvanceRepositorio.java
      servicio/         RegistrarFotoServicio.java, ConsultarFotosServicio.java
    infraestructura/
      web/              FotoController.java, dto/UrlSubidaRequest.java, dto/UrlSubidaResponse.java, dto/RegistrarFotoRequest.java, dto/FotoResponse.java, dto/FotoListadaResponse.java, dto/FotoPaginaResponse.java, FotoWebMapper.java
      persistencia/     FotoAvanceEntity.java, FotoAvanceJpaRepository.java (con Specification para los filtros), FotoAvanceRepositorioJpa.java, FotoAvancePersistenciaMapper.java
  planilla/                        # 006: tablas y lectura; 007: dominio Planilla, casos de uso y endpoints; 008: revisión del cliente
    dominio/        EstadoPlanilla.java, PlanillaCorta.java (periodo y estado),
                    Planilla.java (007: crear, actualizar líneas con el límite contra lo contratado, adjuntar fotos del periodo, presentar; estados editables;
                                   008: aprobar y observar por el cliente, marcar APROBADA con el cobro u OBSERVADA por la saga),
                    LineaPlanilla.java (cantidad del periodo y monto), TotalesPlanilla.java (subtotal, anticipo, retención y neto con redondeo HALF_UP a 2),
                    PlanillaAprobada.java, PlanillaObservada.java (008: eventos; origen CLIENTE o SAGA)
    aplicacion/                    # 007 y 008
      puerto/entrada/   ElaborarPlanillaCasoUso.java (crear, guardar, presentar), ConsultarPlanillaCasoUso.java (lista y detalle), PlanillaDetalle.java (líneas con acumulados y porcentajes del contrato),
                        RevisarPlanillaCasoUso.java (008: aprobar y observar)
      puerto/salida/    PlanillaRepositorio.java (además usa ObraRepositorio de obra y FotoAvanceRepositorio de avance: buscar por ids y publicar)
      servicio/         ElaborarPlanillaServicio.java, ConsultarPlanillaServicio.java, PlanillasDeObra.java (visibilidad, residente asignado y armado del detalle),
                        RevisarPlanillaServicio.java (008: cliente de la obra; aprobar crea la saga con SagaAprobacionRepositorio y publica con PublicadorEventos)
    infraestructura/
      web/              PlanillaController.java (POST y GET /obras/{obraId}/planillas, GET y PUT /planillas/{id}, POST /planillas/{id}/presentar, /aprobar y /observar), PlanillaWebMapper.java,
                        dto/CrearPlanillaRequest.java, dto/GuardarPlanillaRequest.java, dto/PresentarPlanillaRequest.java, dto/PlanillaResponse.java, dto/PlanillaResumenResponse.java,
                        dto/ObservarPlanillaRequest.java, dto/AprobacionResponse.java
      persistencia/     PlanillaEntity.java (con planilla_foto), LineaPlanillaEntity.java, PlanillaJpaRepository.java, PlanillasObraRepositorioJpa.java (adaptador de PlanillasObraRepositorio),
                        PlanillaRepositorioJpa.java (adaptador de PlanillaRepositorio; @Version → VERSION_DESACTUALIZADA), PlanillaPersistenciaMapper.java
  saga/                            # 008: saga «aprobar planilla», orquestada por Obras
    dominio/        SagaAprobacion.java (máquina de estados: iniciar, respuestas, vencimiento y respuestas tardías; devuelve los mensajes a publicar),
                    EstadoSaga.java, PasoSaga.java (2, 3 y 4), ComandosSaga.java (ReservarSaldo, CerrarConsumos, EmitirCobro, LiberarReserva, ReabrirConsumos),
                    RespuestaSaga.java (SaldoReservado, SaldoInsuficiente, ConsumosCerrados, CierreFallido, CobroEmitido, CobroFallido), Reaccion.java (mensajes y qué cambió)
    aplicacion/
      puerto/entrada/   OrquestarSagaCasoUso.java (recibir una respuesta, sagas vencidas y vencer una; RespuestaSagaInvalidaException)
      puerto/salida/    SagaAprobacionRepositorio.java (guardar, buscar con bloqueo, vencidas)
      servicio/         OrquestadorSagaServicio.java (además usa PlanillaRepositorio y PublicadorEventos)
    infraestructura/
      persistencia/     SagaAprobacionEntity.java, SagaAprobacionJpaRepository.java (SELECT … FOR UPDATE), SagaAprobacionRepositorioJpa.java (detalle en JSON)
      mensajeria/       ConsumidoresSaga.java (un ManejadorMensaje por respuesta; busca la saga por correlacionId)
      programacion/     VencimientoSagaTarea.java (@Scheduled cada 10 s: pasos sin respuesta en 60 s)
  diario/                          # 103 (capa 1): diario de obra (usa ObraRepositorio del contexto obra para la visibilidad)
    dominio/        Clima.java (SOLEADO, NUBLADO, LLUVIA_LEVE, LLUVIA_FUERTE),
                    EntradaDiario.java (formato de clima y texto de 10 a 2000 sin los espacios de los extremos; obra EN_EJECUCION;
                                        fecha no futura en America/La_Paz ni anterior al inicio de la obra)
    aplicacion/
      puerto/entrada/   RegistrarEntradaDiarioCasoUso.java
      puerto/salida/    EntradaDiarioRepositorio.java (guardarSiNoExiste: false si la base rechaza el id o el día repetido)
      servicio/         RegistrarEntradaDiarioServicio.java (idempotencia por id, 409 por día repetido, también ante la restricción única)
    infraestructura/
      persistencia/     EntradaDiarioEntity.java, EntradaDiarioJpaRepository.java (INSERT … ON CONFLICT DO NOTHING: el choque no aborta la transacción),
                        EntradaDiarioRepositorioJpa.java, EntradaDiarioPersistenciaMapper.java
  semilla/                         # 011: datos de prueba (infraestructura; nada del servicio depende de él, lo verifica ArquitecturaTest)
                    ConfiguracionSemilla.java (@Profile("semilla"): guarda antes de Flyway, CargaSemilla y SemillaRunner),
                    SemillaRunner.java (ApplicationRunner: guarda, carga, resumen y System.exit con el código de salida),
                    FechaRelativa.java (D±n, M±k, M-k/dd hh:mm, D-n hh:mm y meses previstos; zona America/La_Paz),
                    DatosSemilla.java (registros de semilla/obras.json), PlanSemilla.java (filas resueltas con D: montos y
                    totales con LineaPlanilla y TotalesPlanilla, número del cobro, comprobaciones del documento),
                    CargaSemilla.java (JdbcTemplate en una transacción, INSERT … ON CONFLICT DO NOTHING; recargar: TRUNCATE
                    de todas las tablas menos flyway_schema_history, sacadas de pg_tables: incluye entrada_diario de la 103), ModoSemilla.java (cargar, recargar),
                    ResumenSemilla.java (conteos y línea RESUMEN_SEMILLA en JSON para infra), GuardaSemilla.java (perfil prod o base no local)
src/main/resources/
  application.yaml, application-dev.yaml, application-test.yaml,
  application-semilla.yaml (011: sin web, listeners con auto-startup false,
                    obras.mensajeria.publicador-activo y obras.saga.vencimiento-activo en false, semilla.fecha/modo/salir)
  semilla/obras.json (011: copia de la parte de Obras de docs/datos-prueba.md con fechas relativas a D)
  db/migration/V1__obra.sql (002), V2__foto_avance.sql (005), V3__planilla.sql (006: planilla y linea_planilla), V4__planilla_foto.sql (007), V5__saga_aprobacion.sql (008: saga_aprobacion y outbox_evento.clave), V6__entrada_diario.sql (103: entrada_diario, único obra_id y fecha), ...   # una migración por funcionalidad, nunca se edita una ya aplicada
src/test/java/com/constructora/obras/
  obra/dominio/ObraTest.java                    # unitarias del dominio, sin Spring
  obra/aplicacion/RegistrarObraServicioTest.java # con dobles de los puertos
  obra/aplicacion/ConsultarObraServicioTest.java # 006: lista, detalle y material previsto con Reloj fijo y dobles
  planilla/dominio/PlanillaTest.java            # 007: caso fijo (anticipo 10 %, retención 7 %), redondeo, límite en el borde, estados, fotos
  planilla/aplicacion/ElaborarPlanillaServicioTest.java # 007: casos de uso con dobles (residente asignado, visibilidad, cliente sin borradores, fotos publicadas)
  planilla/aplicacion/RevisarPlanillaServicioTest.java  # 008: aprobar y observar con dobles (DoblesSaga: obras, planillas y sagas en memoria, reloj manual y bus simulado)
  saga/aplicacion/DoblesSaga.java, OrquestadorSagaServicioTest.java # 008: máquina de estados con bus simulado (camino feliz, fallo en cada paso, vencimiento, repetidos, tardíos)
  avance/dominio/FotoAvanceTest.java, avance/aplicacion/RegistrarFotoServicioTest.java, ConsultarFotosServicioTest.java (dobles en DoblesAvance)
  compartido/dominio/PaginacionTest.java, PeriodoTest.java
  avance/infraestructura/web/FotoAvanceIT.java    # pruebas de la spec 005 con el doble AlmacenArchivosEnMemoria
  compartido/infraestructura/archivos/AlmacenArchivosS3Test.java # firma real del SDK, sin red
  obra/infraestructura/web/ObraControllerIT.java # @SpringBootTest + Testcontainers + tokens de prueba
  obra/infraestructura/web/ConsultarObraIT.java # 002 y 006: lista con filtros, indicadores, última planilla y detalle
  obra/infraestructura/web/MaterialPrevistoIT.java # 006: material previsto con y sin planilla, roles y periodo
  planilla/infraestructura/web/PlanillaIT.java  # 007: pruebas de API de la spec (planilla abierta, periodos, versión, límite, fotos publicadas, roles y cliente)
  planilla/infraestructura/web/RevisarPlanillaIT.java # 008: aprobar (202, saga y ReservarSaldo en la cola de Finanzas) y observar, errores, roles, azp y CORS
  planilla/infraestructura/persistencia/PlanillaTablasIT.java # 006, 007 y 008: restricciones de V3, V4 y V5 y lectura de planillas
  saga/infraestructura/mensajeria/SagaAprobacionIT.java # 008: saga con RabbitMQ real (Finanzas y Materiales simulados): camino feliz y avance, fallo con compensación, fallidos, vencimiento y respuestas tardías
  soporte/                                      # PruebaIntegracion (base de los *IT), ContenedoresPrueba, TokensPrueba, ControladorPruebaRoles (endpoints solo de prueba), ObrasPrueba, PlanillasPrueba (planillas directo en la base), Esperar,
                                                #   AlmacenArchivosEnMemoria (doble de AlmacenArchivos) y AlmacenArchivosPrueba (lo pone en lugar de S3)
  compartido/infraestructura/seguridad/SeguridadIT.java
  diario/dominio/EntradaDiarioTest.java         # 103: bordes del texto (9, 10, 2000, 2001), clima, hoy en La Paz con UTC del día siguiente, fecha futura y anterior al inicio
  semilla/FechaRelativaTest.java, DatosSemillaTest.java, PlanSemillaTest.java, GuardaSemillaTest.java # 011: unitarias (tabla del diseño, totales y cobros del documento, guardas)
  semilla/SemillaPerfilIT.java                  # 011: la aplicación con el perfil semilla (sin web ni mensajería), dos cargas, recargar, prod y base no local (recargar también vacía entrada_diario)
  diario/aplicacion/DoblesDiario.java, RegistrarEntradaDiarioServicioTest.java # 103: idempotencia, día repetido (también a la vez), permisos, orden de validación
  diario/infraestructura/persistencia/EntradaDiarioTablaIT.java # 103: restricciones de V6 (una entrada por obra y día, clima) y el adaptador (choque sin abortar, rango)
  semilla/DatosPruebaIT.java                    # 011: la API con D = 2026-10-28 (reloj fijo): indicadores, planillas, material previsto, visibilidad y fotos
  ArquitecturaTest.java                         # ArchUnit
```

## Reglas

- `dominio` no importa nada de Spring, JPA, Jackson ni de `infraestructura`. Lo verifica `ArquitecturaTest`.
- `aplicacion` solo depende de `dominio` y de sus puertos. Los servicios llevan `@Transactional`.
- Controladores delgados: validan el DTO (`@Valid`), llaman al caso de uso y mapean con MapStruct.
- Entidades JPA separadas del dominio (`*Entity`), mapeadas con MapStruct en `*PersistenciaMapper`.
- Las reglas del negocio lanzan `ReglaNegocioException(codigo, mensaje)`; `ManejadorErrores` la convierte en `422` (o `409` según el código).
- Autorización con `@PreAuthorize` en el controlador; la visibilidad por obra se valida en el caso de uso con `UsuarioActual`.
- Eventos: el caso de uso llama al puerto `PublicadorEventos`, que guarda en `outbox_evento` dentro de la misma transacción.
- Nombres: clases en español (`RegistrarObraServicio`), sufijos técnicos permitidos (`Controller`, `Entity`, `Repositorio`, `Mapper`, `Test`, `IT`).
- Rutas REST en español según `docs/contratos.md`; JSON `camelCase`; columnas `snake_case`.

## Ejemplo de una funcionalidad de punta a punta

`POST /obras` → `ObraController.registrar(@Valid RegistrarObraRequest)` → `ObraWebMapper` → `RegistrarObraCasoUso.ejecutar(comando)` → `Obra.registrar(...)` valida el presupuesto → `ObraRepositorio.guardar(obra)` + `PublicadorEventos.publicar(ObraRegistrada)` → `201 ObraResponse`.

## Pruebas

- Dominio: unitarias puras, con `Reloj` fijo.
- Servicios: dobles de los puertos (sin Mockito si un doble a mano es más claro).
- API: `*IT` que extienden `PruebaIntegracion`, con Testcontainers; tokens de `TokensPrueba`, firmados con una clave RSA creada al iniciar las pruebas (la aplicación la usa por `obras.seguridad.clave-publica`).
- `./mvnw verify` corre todo.
