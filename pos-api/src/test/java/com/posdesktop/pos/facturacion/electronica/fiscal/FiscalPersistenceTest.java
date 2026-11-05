package com.posdesktop.pos.facturacion.electronica.fiscal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.posdesktop.pos.facturacion.electronica.application.FacturacionElectronicaService;
import com.posdesktop.pos.facturacion.electronica.config.*;
import com.posdesktop.pos.facturacion.electronica.domain.*;
import com.posdesktop.pos.facturacion.electronica.infraestructura.dataico.DataicoApiException;
import com.posdesktop.pos.facturacion.electronica.integration.SaleFiscalSnapshotMapper;
import com.posdesktop.pos.invoicing.domain.FiscalInvoice;
import com.posdesktop.pos.invoicing.infrastructure.xml.DianInvoiceDraftGenerator;
import com.posdesktop.pos.repositorio.relacional.*;
import com.posdesktop.pos.ventas.service.FacturacionVentaService;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.client.*;
import org.springframework.http.*;

/** PostgreSQL real y desechable: nunca admite URL de la base de trabajo o de produccion. */
@EnabledIfSystemProperty(named="pos.fiscal.test.url",matches="jdbc:postgresql://127\\.0\\.0\\.1:55439/posdesktop_fiscal_test")
class FiscalPersistenceTest {
    @TempDir static Path keys;
    private static AnnotationConfigApplicationContext context;
    private static JdbcTemplate jdbc;
    private static FacturacionElectronicaService remote;
    private static final UUID LEGACY_SALE=UUID.fromString("aaaaaaaa-1111-4111-8111-111111111111");
    @BeforeAll static void start() {
        DataSource ds=source(); jdbc=new JdbcTemplate(ds);
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("23").load().migrate();
        jdbc.update("INSERT INTO ventas(id,numero_venta,fecha_venta,estado,origen,total) VALUES (?,'VTA-LEGACY-FISCAL','2026-10-29 10:00:00','REGISTRADA','MANUAL',100) ON CONFLICT DO NOTHING",LEGACY_SALE);
        jdbc.update("INSERT INTO facturas_electronicas(venta_id,fecha_emision,numero_factura,cliente_nombre,cliente_identificacion,total,estado_dian,estado_cliente,estado_correo,identificador_dataico) VALUES (?,'2026-10-29 10:00:00','LEGACY1','CLIENTE HISTORICO','1234',100,'DIAN_ACEPTADO','REGISTRADO','NO_SOLICITADO','legacy-remote') ON CONFLICT DO NOTHING",LEGACY_SALE);
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        // La base es desechable; una nueva ejecucion usa otro @TempDir de claves.
        jdbc.update("UPDATE dian_configuraciones SET datos_json=NULL,secretos_cifrados=NULL,version=version+1");
        context=new AnnotationConfigApplicationContext(Config.class);
        remote=context.getBean(FacturacionElectronicaService.class);
    }
    @AfterAll static void close() {if(context!=null)context.close();}
    @BeforeEach void resetProvider() {
        reset(remote); jdbc.update("UPDATE facturacion_configuracion SET proveedor='DATAICO',version=version+1 WHERE id=1");
        jdbc.update("INSERT INTO dataico_consecutivos(prefijo,siguiente_numero) VALUES ('FISCALTEST',1) ON CONFLICT DO NOTHING");
        jdbc.update("UPDATE dataico_configuracion SET habilitado=NULL,account_id=NULL,version=version+1 WHERE id=1");
    }
    private static DataSource source() {
        String url=System.getProperty("pos.fiscal.test.url");
        if(!"jdbc:postgresql://127.0.0.1:55439/posdesktop_fiscal_test".equals(url))throw new IllegalStateException("Solo base desechable.");
        return new DriverManagerDataSource(url,"fiscal_test","fiscal_test_only");
    }
    @Configuration @EnableTransactionManagement @org.springframework.data.jpa.repository.config.EnableJpaAuditing
    @EnableJpaRepositories(basePackageClasses=FiscalDocumentRepository.class)
    @Import({FiscalJson.class,FiscalSettingsService.class,FiscalEmissionTransactions.class,DataicoFiscalSender.class,
            FacturacionVentaService.class,FiscalDraftService.class,FiscalSignatureService.class,SaleFiscalSnapshotMapper.class,DataicoConfiguracionService.class})
    static class Config {
        @Bean DataSource dataSource(){return source();}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source){
            var emf=new LocalContainerEntityManagerFactoryBean();emf.setDataSource(source);emf.setPackagesToScan("com.posdesktop.pos.modelo.relacional");
            emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            emf.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));return emf;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory emf){return new JpaTransactionManager(emf);}
        @Bean FacturacionElectronicaService remote(){return mock(FacturacionElectronicaService.class);}
        @Bean DataicoProperties properties(){return new DataicoProperties(true,DataicoProperties.DEFAULT_BASE_URL,"11111111-1111-4111-8111-111111111111",null,"token-ficticio-test","FISCALTEST","18760000001","1",false,"PRUEBAS",Duration.ofSeconds(1),Duration.ofSeconds(1));}
        @Bean DataicoTokenCipher dataicoTokenCipher(){return new DataicoTokenCipher(keys.resolve("dataico.key").toString());}
        @Bean FiscalSecretVault vault(FiscalJson json){return new FiscalSecretVault(keys.resolve("fiscal.key").toString(),json);}
        @Bean DianInvoiceDraftGenerator generator(){return new DianInvoiceDraftGenerator();}
    }
    private record Sale(UUID saleId,UUID customerId){ }
    private Sale seed(){
        UUID customer=UUID.randomUUID(),sale=UUID.randomUUID();
        jdbc.update("INSERT INTO clientes_electronicos(id,tipo_identificacion,tipo_persona,identificacion,razon_social,primer_nombre,primer_apellido,direccion,ciudad_codigo_dane,departamento_codigo_dane,pais_codigo) VALUES (?,'CC','NATURAL',?,'CLIENTE PRUEBA','Cliente','Prueba','Calle ficticia','001','05','CO')",customer,Integer.toUnsignedString(customer.hashCode()));
        jdbc.update("INSERT INTO ventas(id,numero_venta,fecha_venta,estado,origen,subtotal,total) VALUES (?,?,'2026-10-30 10:30:00','REGISTRADA','MANUAL',100,100)",sale,"VTA-"+sale.toString().substring(0,10));
        jdbc.update("INSERT INTO detalle_venta(venta_id,tipo_detalle,orden,descripcion,cantidad,precio_unitario,subtotal,total) VALUES (?,'MANUAL',1,'ARTICULO ORIGINAL',1,100,100,100)",sale);
        return new Sale(sale,customer);
    }
    private FacturacionVentaService workflow(){return context.getBean(FacturacionVentaService.class);}
    private FiscalSettingsService settings(){return context.getBean(FiscalSettingsService.class);}
    private static ResultadoEmisionDocumento result(DocumentoElectronico doc){return new ResultadoEmisionDocumento("dataico","REMOTE-"+doc.numero(),doc.numero(),"DIAN_ACEPTADO","CUFE-PRUEBA",null,null,"Recibida",null);}
    private void accept(){when(remote.emitir(eq("dataico"),any())).thenAnswer(i->result(i.getArgument(1)));}

    @Test void migracionConservaHistorialYProveedorAunqueCambieSeleccion(){
        assertEquals("DATAICO",jdbc.queryForObject("SELECT proveedor FROM facturas_electronicas WHERE venta_id=?",String.class,LEGACY_SALE));
        settings().saveProvider(new FiscalSettingsService.SaveProvider("DIAN_DIRECT",settings().provider().version()));
        var cached=workflow().emitir(LEGACY_SALE,UUID.randomUUID());assertEquals("legacy-remote",cached.identificadorExterno());verifyNoInteractions(remote);
        var s=seed();assertThrows(UnsupportedOperationException.class,()->workflow().emitir(s.saleId,s.customerId));verifyNoInteractions(remote);
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM facturacion_operaciones WHERE venta_id=?",Integer.class,s.saleId));
    }
    @Test void dobleSolicitudConcurrenteEnviaUnaSolaVezYRepiteResultadoConfirmado() throws Exception {
        var s=seed();CountDownLatch sending=new CountDownLatch(1),finish=new CountDownLatch(1);
        when(remote.emitir(eq("dataico"),any())).thenAnswer(i->{sending.countDown();if(!finish.await(10,TimeUnit.SECONDS))throw new IllegalStateException();return result(i.getArgument(1));});
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var first=executor.submit(()->workflow().emitir(s.saleId,s.customerId));assertTrue(sending.await(10,TimeUnit.SECONDS));
            assertThrows(FiscalConflictException.class,()->workflow().emitir(s.saleId,s.customerId));finish.countDown();
            var response=first.get(10,TimeUnit.SECONDS);assertEquals(response,workflow().emitir(s.saleId,s.customerId));
        } finally {finish.countDown();}
        verify(remote,times(1)).emitir(eq("dataico"),any());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM documentos_fiscales WHERE venta_id=?",Integer.class,s.saleId));
    }
    @Test void timeoutConservaReservaYConsultaSinHacerOtroPost(){
        var s=seed();when(remote.emitir(eq("dataico"),any())).thenThrow(new DataicoApiException("Timeout",new ResourceAccessException("Timeout")));
        assertThrows(DataicoApiException.class,()->workflow().emitir(s.saleId,s.customerId));
        var operation=context.getBean(FiscalOperationRepository.class).findById(s.saleId).orElseThrow();assertEquals("INCIERTO",operation.getEstado());
        var doc=context.getBean(FiscalDocumentRepository.class).findById(operation.getDocumentoId()).orElseThrow();
        long reserved=Long.parseLong(doc.getNumero().substring("FISCALTEST".length()));
        assertTrue(jdbc.queryForObject("SELECT siguiente_numero FROM dataico_consecutivos WHERE prefijo='FISCALTEST'",Long.class)>reserved);
        assertThrows(FiscalConflictException.class,()->workflow().emitir(s.saleId,s.customerId));
        when(remote.consultarFactura("dataico",doc.getNumero())).thenReturn(new ResultadoEmisionDocumento("dataico","remote-after-timeout",Long.toString(reserved),"DIAN_ACEPTADO",null,null,null,"Recibida",null));
        assertEquals("remote-after-timeout",workflow().consultarIntento(s.saleId).identificadorExterno());verify(remote,times(1)).emitir(eq("dataico"),any());
    }
    @Test void rechazoSeguroPermiteReintentoExplicitoConElSnapshotOriginal(){
        var s=seed();var unauthorized=HttpClientErrorException.create(HttpStatus.UNAUTHORIZED,"Unauthorized",HttpHeaders.EMPTY,new byte[0],java.nio.charset.StandardCharsets.UTF_8);
        when(remote.emitir(eq("dataico"),any())).thenThrow(new DataicoApiException("401",unauthorized));assertThrows(DataicoApiException.class,()->workflow().emitir(s.saleId,s.customerId));
        assertEquals("ERROR_SEGURO",context.getBean(FiscalOperationRepository.class).findById(s.saleId).orElseThrow().getEstado());
        jdbc.update("UPDATE detalle_venta SET descripcion='CAMBIO POSTERIOR' WHERE venta_id=?",s.saleId);
        accept();workflow().reintentar(s.saleId,s.customerId);
        var captured=org.mockito.ArgumentCaptor.forClass(DocumentoElectronico.class);verify(remote,times(2)).emitir(eq("dataico"),captured.capture());
        assertEquals(captured.getAllValues().get(0),captured.getAllValues().get(1));assertEquals("ARTICULO ORIGINAL",captured.getValue().lineas().getFirst().descripcion());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM documentos_fiscales WHERE venta_id=?",Integer.class,s.saleId));
    }
    @Test void evidenciaEsInmutableYProveedorNoSePuedeReasignarEnSql(){
        var s=seed();accept();workflow().emitir(s.saleId,s.customerId);
        assertThrows(RuntimeException.class,()->jdbc.update("UPDATE documentos_fiscales SET snapshot_json='{}' WHERE venta_id=?",s.saleId));
        assertThrows(RuntimeException.class,()->jdbc.update("DELETE FROM documentos_fiscales WHERE venta_id=?",s.saleId));
        assertThrows(RuntimeException.class,()->jdbc.update("UPDATE facturacion_operaciones SET proveedor='DIAN_DIRECT' WHERE venta_id=?",s.saleId));
        assertThrows(RuntimeException.class,()->jdbc.update("UPDATE facturas_electronicas SET proveedor='DIAN_DIRECT' WHERE venta_id=?",s.saleId));
    }
    @Test void configuracionInvalidaNoReservaNiContactaProveedor(){
        var s=seed();jdbc.update("UPDATE dataico_configuracion SET habilitado=false WHERE id=1");
        Long before=jdbc.queryForObject("SELECT siguiente_numero FROM dataico_consecutivos WHERE prefijo='FISCALTEST'",Long.class);
        assertThrows(FiscalPreflightException.class,()->workflow().emitir(s.saleId,s.customerId));verifyNoInteractions(remote);
        assertEquals(before,jdbc.queryForObject("SELECT siguiente_numero FROM dataico_consecutivos WHERE prefijo='FISCALTEST'",Long.class));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM documentos_fiscales WHERE venta_id=?",Integer.class,s.saleId));
    }
    @Test void correccionExplicitaDelProveedorConservaAmbosIntentos(){
        var s=seed();Long before=jdbc.queryForObject("SELECT siguiente_numero FROM dataico_consecutivos WHERE prefijo='FISCALTEST'",Long.class);long suggested=before+5;
        var error=HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR,"Error",HttpHeaders.EMPTY,new byte[0],java.nio.charset.StandardCharsets.UTF_8);
        when(remote.emitir(eq("dataico"),any())).thenThrow(new DataicoApiException("El siguiente número 'FISCALTEST"+suggested+"'",error)).thenAnswer(i->result(i.getArgument(1)));
        workflow().emitir(s.saleId,s.customerId);
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM documentos_fiscales WHERE venta_id=?",Integer.class,s.saleId));
        assertEquals(suggested+1,jdbc.queryForObject("SELECT siguiente_numero FROM dataico_consecutivos WHERE prefijo='FISCALTEST'",Long.class));
        var operation=context.getBean(FiscalOperationRepository.class).findById(s.saleId).orElseThrow();var record=context.getBean(FiscalDocumentRepository.class).findById(operation.getDocumentoId()).orElseThrow();
        var payload=context.getBean(FiscalJson.class).read(record.getSnapshotJson(),DataicoSalePayload.class);
        assertEquals(Long.toString(suggested),payload.document().numero());assertTrue(payload.providerRequestJson().contains(Long.toString(suggested)));verify(remote,times(2)).emitir(eq("dataico"),any());
    }
    @Test void cambiarCuentaNoRedirigeReintentoYProveedorDelDocumentoPermaneceDataico(){
        var s=seed();var error=HttpClientErrorException.create(HttpStatus.UNAUTHORIZED,"Unauthorized",HttpHeaders.EMPTY,new byte[0],java.nio.charset.StandardCharsets.UTF_8);
        when(remote.emitir(eq("dataico"),any())).thenThrow(new DataicoApiException("401",error));assertThrows(DataicoApiException.class,()->workflow().emitir(s.saleId,s.customerId));
        jdbc.update("UPDATE dataico_configuracion SET account_id='22222222-2222-4222-8222-222222222222' WHERE id=1");
        assertThrows(FiscalPreflightException.class,()->workflow().reintentar(s.saleId,s.customerId));verify(remote,times(1)).emitir(eq("dataico"),any());
        jdbc.update("UPDATE dataico_configuracion SET account_id=NULL WHERE id=1");settings().saveProvider(new FiscalSettingsService.SaveProvider("DIAN_DIRECT",settings().provider().version()));
        accept();workflow().reintentar(s.saleId,s.customerId);assertEquals("DATAICO",context.getBean(FiscalOperationRepository.class).findById(s.saleId).orElseThrow().getProveedor());
    }
    @Test void certificadoInvalidoNoSobrescribeConfiguracionNiSecretos(){
        var before=settings().dian("PRUEBAS");
        assertThrows(IllegalArgumentException.class,()->settings().saveDian("PRUEBAS",new FiscalSettingsService.SaveDian(before.version(),configuration(),"clave-nueva","pin-nuevo",Base64.getEncoder().encodeToString(new byte[]{1,2,3}),"incorrecta")));
        assertEquals(before,settings().dian("PRUEBAS"));
    }
    @Test void ambientesYSecretosSeparadosYGuardadoConVersionOptimista(){
        var config=configuration();var settings=settings();var initial=settings.dian("PRUEBAS");
        var saved=settings.saveDian("PRUEBAS",new FiscalSettingsService.SaveDian(initial.version(),config,"technical-key-test","pin-test",null,null));
        assertTrue(saved.technicalKeyConfigured());assertTrue(saved.softwarePinConfigured());
        String encrypted=jdbc.queryForObject("SELECT secretos_cifrados FROM dian_configuraciones WHERE ambiente='PRUEBAS'",String.class);
        assertFalse(encrypted.contains("technical-key-test"));assertFalse(encrypted.contains("pin-test"));
        var publicJson=context.getBean(FiscalJson.class).write(saved);assertFalse(publicJson.contains("pin-test"));assertFalse(publicJson.contains("technical-key-test"));
        assertThrows(FiscalConflictException.class,()->settings.saveDian("PRUEBAS",new FiscalSettingsService.SaveDian(initial.version(),config,null,null,null,null)));
        var production=settings.dian("PRODUCCION");assertNull(production.configuration());assertFalse(production.softwarePinConfigured());
        assertThrows(IllegalStateException.class,()->context.getBean(FiscalSecretVault.class).decrypt(encrypted,"PRODUCCION"));
        var preserved=settings.saveDian("PRUEBAS",new FiscalSettingsService.SaveDian(saved.version(),config,"","",null,null));assertTrue(preserved.softwarePinConfigured());
    }
    @Test void borradorPersisteSnapshotYXmlSinConsumirNumeroNiMarcarEnvio(){
        var settings=settings();var loaded=settings.dian("PRUEBAS");var saved=settings.saveDian("PRUEBAS",new FiscalSettingsService.SaveDian(loaded.version(),configuration(),"technical-key-test","pin-test",null,null));
        var s=seed();var drafts=context.getBean(FiscalDraftService.class);
        var request=new FiscalDraftService.Request(s.customerId,"PRUEBAS",saved.version(),1,"R-99-PN","ZZ","1","10",LocalDate.of(2026,10,30),
                List.of(new FiscalDraftService.LineClassification(1,"94",List.of(new FiscalInvoice.Tax(FiscalInvoice.Treatment.EXCLUDED,"01","IVA",BigDecimal.ZERO)))),List.of(),List.of());
        Long before=jdbc.queryForObject("SELECT siguiente_numero FROM dataico_consecutivos WHERE prefijo='FISCALTEST'",Long.class);
        var draft=drafts.generate(s.saleId,request);var replay=drafts.generate(s.saleId,request);
        assertEquals(draft.id(),replay.id());assertFalse(draft.readyToTransmit());assertTrue(draft.xml().contains("ARTICULO ORIGINAL"));
        jdbc.update("UPDATE detalle_venta SET descripcion='DESPUES' WHERE venta_id=?",s.saleId);
        var record=context.getBean(FiscalDocumentRepository.class).findById(draft.id()).orElseThrow();assertTrue(record.getSnapshotJson().contains("ARTICULO ORIGINAL"));assertEquals(draft.xml(),record.getXml());
        assertFalse(record.getSnapshotJson().contains("technical-key-test"));assertFalse(record.getSnapshotJson().contains("pin-test"));
        assertEquals(before,jdbc.queryForObject("SELECT siguiente_numero FROM dataico_consecutivos WHERE prefijo='FISCALTEST'",Long.class));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM facturas_electronicas WHERE venta_id=?",Integer.class,s.saleId));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM facturacion_operaciones WHERE venta_id=?",Integer.class,s.saleId));verifyNoInteractions(remote);
    }
    @Test void firmaCopiaLocalConservaOriginalYNoEmiteNiConsumeConsecutivos()throws Exception {
        var loaded=settings().dian("PRUEBAS");
        Path file=keys.resolve("local-signature.p12");
        String executable=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"keytool.exe":"keytool").toString();
        var builder=new ProcessBuilder(executable,"-genkeypair","-alias","test","-keyalg","RSA","-keysize","2048","-dname","CN=SOLO TEST POSDesktop, C=CO","-startdate","-1d","-validity","90","-storetype","PKCS12","-keystore",file.toString(),"-storepass:env","POS_FISCAL_TEST_PASS","-noprompt").redirectErrorStream(true);
        builder.environment().put("POS_FISCAL_TEST_PASS","test-only-password");var process=builder.start();String output=new String(process.getInputStream().readAllBytes());assertEquals(0,process.waitFor(),output);
        String certificate=Base64.getEncoder().encodeToString(java.nio.file.Files.readAllBytes(file));
        var saved=settings().saveDian("PRUEBAS",new FiscalSettingsService.SaveDian(loaded.version(),configuration(),"tech-test","pin-test",certificate,"test-only-password"));
        var s=seed();
        var request=new FiscalDraftService.Request(s.customerId,"PRUEBAS",saved.version(),1,"R-99-PN","ZZ","1","10",LocalDate.of(2026,10,30),
                List.of(new FiscalDraftService.LineClassification(1,"94",List.of(new FiscalInvoice.Tax(FiscalInvoice.Treatment.EXCLUDED,"01","IVA",BigDecimal.ZERO)))),List.of(),List.of());
        var draft=context.getBean(FiscalDraftService.class).generate(s.saleId,request);
        var signatures=context.getBean(FiscalSignatureService.class);var repo=context.getBean(FiscalDocumentRepository.class);
        Long before=jdbc.queryForObject("SELECT siguiente_numero FROM dataico_consecutivos WHERE prefijo='FISCALTEST'",Long.class);
        // La firma debe usar el snapshot, aunque luego cambie la venta de origen.
        jdbc.update("UPDATE detalle_venta SET descripcion='CAMBIO POSTERIOR' WHERE venta_id=?",s.saleId);
        var signed=signatures.sign(draft.id());assertTrue(signed.signatureValid());assertFalse(signed.trustVerified());assertFalse(signed.readyToTransmit());
        var evidence=repo.findById(signed.id()).orElseThrow();assertTrue(evidence.getXml().contains("ARTICULO ORIGINAL"));assertFalse(evidence.getXml().contains("CAMBIO POSTERIOR"));
        assertEquals(draft.xml(),repo.findById(draft.id()).orElseThrow().getXml());assertTrue(signatures.verify(signed.id()).valid());
        assertFalse(evidence.getSnapshotJson().contains("test-only-password"));assertFalse(evidence.getSnapshotJson().contains(certificate));
        assertThrows(IllegalArgumentException.class,()->signatures.sign(signed.id()));
        assertEquals(before,jdbc.queryForObject("SELECT siguiente_numero FROM dataico_consecutivos WHERE prefijo='FISCALTEST'",Long.class));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM facturacion_operaciones WHERE venta_id=?",Integer.class,s.saleId));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM facturas_electronicas WHERE venta_id=?",Integer.class,s.saleId));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM documentos_fiscales WHERE venta_id=?",Integer.class,s.saleId));verifyNoInteractions(remote);
    }
    private static DianConfiguration configuration(){
        var party=new FiscalInvoice.Party("31","860324218","1","EMPRESA FICTICIA","R-99-PN","ZZ","No aplica",new FiscalInvoice.Address("Calle ficticia","05001","Medellin","05","Antioquia","CO"));
        return new DianConfiguration("Software ficticio",UUID.fromString("11111111-1111-4111-8111-111111111111"),null,"860324218","1",party,"1",new FiscalInvoice.Numbering("PRUEBA","18760000001",LocalDate.of(2026,1,1),LocalDate.of(2026,12,31),1,100),1);
    }
}
