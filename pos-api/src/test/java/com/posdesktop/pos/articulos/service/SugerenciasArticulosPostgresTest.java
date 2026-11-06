package com.posdesktop.pos.articulos.service;

import static org.assertj.core.api.Assertions.*;

import com.posdesktop.pos.repositorio.relacional.ArticuloRepositorio;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

/** Ejecutar con -Dpos.test.jdbc-url=jdbc:postgresql://localhost:PUERTO/postgres (base de pruebas). */
@EnabledIfSystemProperty(named = "pos.test.jdbc-url", matches = ".+")
class SugerenciasArticulosPostgresTest {
    private static final String SCHEMA = "sugerencias_test_" + UUID.randomUUID().toString().replace("-", "");
    private static JdbcTemplate admin;
    private static JdbcTemplate jdbc;
    private static EntityManagerFactory emf;

    @BeforeAll
    static void iniciar() {
        String url = System.getProperty("pos.test.jdbc-url");
        String usuario = System.getProperty("pos.test.jdbc-user", "postgres");
        String password = System.getProperty("pos.test.jdbc-password", "");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, usuario, password));
        admin.execute("CREATE SCHEMA " + SCHEMA);
        var datasource = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA,
                usuario, password);
        jdbc = new JdbcTemplate(datasource);
        jdbc.execute("""
                CREATE TABLE articulos (id UUID PRIMARY KEY, sku VARCHAR(50) NOT NULL UNIQUE,
                  nombre VARCHAR(150) NOT NULL, descripcion VARCHAR(500), precio_base NUMERIC(19,2) NOT NULL,
                  activo BOOLEAN NOT NULL DEFAULT TRUE, creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
                """);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V24__sugerencias_articulos.sql")).execute(datasource);
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(datasource);
        factory.setPackagesToScan("com.posdesktop.pos.modelo.relacional");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none",
                "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));
        factory.afterPropertiesSet();
        emf = factory.getObject();
    }

    @BeforeEach
    void limpiar() { jdbc.execute("TRUNCATE TABLE articulos"); }

    @AfterAll
    static void cerrar() {
        if (emf != null) emf.close();
        if (admin != null) admin.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
    }

    @Test
    void filtraPorPrecioOPorTextoYPriorizaPrecioSinExcluirOtrosPrecios() {
        insertar("SKU-1", "Café grande", "5000", true);
        insertar("SKU-2", "Café pequeño", "3000", true);
        insertar("SKU-3", "Pan", "3000", true);
        insertar("SKU-4", "Café retirado", "3000", false);
        enTransaccion(service -> {
            assertThat(service.sugerir("", new BigDecimal("3000"))).extracting("nombre")
                    .containsExactly("Café pequeño", "Pan");
            assertThat(service.sugerir("CAFÉ", new BigDecimal("3000"))).extracting("nombre")
                    .containsExactly("Café pequeño", "Café grande");
            assertThat(service.sugerir("grande", null)).extracting("nombre").containsExactly("Café grande");
            assertThat(service.sugerir("%", null)).isEmpty();
            return null;
        });
    }

    @Test
    void aprendeNombreNormalizadoReutilizaElArticuloYNoModificaSuPrecio() {
        var id = enTransaccion(service -> service.aprender("  Caja   azul  ", new BigDecimal("5000")).getId());
        var reutilizado = enTransaccion(service -> service.aprender("CAJA AZUL", new BigDecimal("6000")));
        assertThat(reutilizado.getId()).isEqualTo(id);
        assertThat(reutilizado.getPrecioBase()).isEqualByComparingTo("5000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM articulos", Integer.class)).isEqualTo(1);
        enTransaccion(service -> service.aprender("x".repeat(200), BigDecimal.TEN));
    }

    @Test
    void unaVentaRevertidaNoDejaNombresAprendidos() {
        assertThatThrownBy(() -> enTransaccion(service -> {
            service.aprender("Borrador", BigDecimal.TEN);
            throw new IllegalArgumentException("Venta inválida");
        })).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM articulos", Integer.class)).isZero();
    }

    @Test
    void reutilizaCatalogoExistenteYRespetaInactivos() {
        insertar("INV-1", "  Café   grande  ", "2000", true);
        insertar("INV-2", "Retirado", "1000", false);
        enTransaccion(service -> {
            assertThat(service.sugerir("CAFÉ GRANDE", null)).hasSize(1);
            assertThat(service.aprender("CAFÉ GRANDE", BigDecimal.TEN).getSku()).isEqualTo("INV-1");
            assertThat(service.aprender("Retirado", BigDecimal.TEN)).isNull();
            return null;
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM articulos", Integer.class)).isEqualTo(2);
    }

    @Test
    void dosCajasAprendenElMismoNombreConUnSoloRegistro() throws Exception {
        CountDownLatch inicio = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tareas = List.of("Caja azul", "CAJA AZUL").stream().map(nombre -> executor.submit(() -> {
                assertThat(inicio.await(5, TimeUnit.SECONDS)).isTrue();
                return enTransaccion(service -> service.aprender(nombre, BigDecimal.TEN).getId());
            })).toList();
            inicio.countDown();
            assertThat(tareas.get(0).get(15, TimeUnit.SECONDS)).isEqualTo(tareas.get(1).get(15, TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM articulos", Integer.class)).isEqualTo(1);
    }

    private void insertar(String sku, String nombre, String precio, boolean activo) {
        jdbc.update("INSERT INTO articulos (id, sku, nombre, precio_base, activo) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), sku, nombre, new BigDecimal(precio), activo);
    }

    private <T> T enTransaccion(Function<SugerenciasArticulosService, T> accion) {
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            try {
                var repo = new JpaRepositoryFactory(em).getRepository(ArticuloRepositorio.class);
                T resultado = accion.apply(new SugerenciasArticulosService(repo));
                em.getTransaction().commit();
                return resultado;
            } catch (RuntimeException exception) {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                throw exception;
            }
        }
    }
}
