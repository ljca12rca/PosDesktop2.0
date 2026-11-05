package com.posdesktop.pos;

import static org.junit.jupiter.api.Assertions.*;

import com.posdesktop.pos.integration.PosApiClient;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ElectronicInvoicesViewTest {
    @BeforeAll
    static void iniciarJavaFx() throws Exception {
        CountDownLatch listo = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            listo.countDown();
        });
        assertTrue(listo.await(15, TimeUnit.SECONDS));
    }

    @Test
    void desmarcarCancelaLaImpresionPendienteYNoLaReactivaAlMarcarDeNuevo() throws Exception {
        fx(() -> {
            CheckBox check = new CheckBox();
            check.setSelected(true);
            ElectronicPrintRequest pending = new ElectronicPrintRequest(check);
            assertTrue(pending.getAsBoolean());
            check.setSelected(false);
            assertFalse(pending.getAsBoolean());
            check.setSelected(true);
            assertFalse(pending.getAsBoolean());
            ElectronicPrintRequest next = new ElectronicPrintRequest(check);
            pending.close();
            assertTrue(check.isSelected());
            assertTrue(next.getAsBoolean());
            next.close();
            assertFalse(check.isSelected());
            ElectronicPrintRequest unchecked = new ElectronicPrintRequest(check);
            check.setSelected(true);
            assertFalse(unchecked.getAsBoolean());
            unchecked.close();
            assertTrue(check.isSelected());
            return null;
        });
    }

    @Test
    void nuevaVentaElectronicaEnfocaClienteYVentaNormalEnfocaValor() throws Exception {
        fx(() -> {
            PosDesktopFxApplication app = new PosDesktopFxApplication();
            Field mode = PosDesktopFxApplication.class.getDeclaredField("electronicInvoicingEnabled");
            mode.setAccessible(true);
            var enabled = (javafx.beans.property.BooleanProperty) mode.get(app);
            TextField customer = new TextField();
            TextField price = new TextField();
            Scene scene = new Scene(new VBox(customer, price));
            Method focus = PosDesktopFxApplication.class.getDeclaredMethod("focusNewSale", TextField.class, TextField.class);
            focus.setAccessible(true);
            enabled.set(true);
            price.requestFocus();
            focus.invoke(app, customer, price);
            assertSame(customer, scene.getFocusOwner());
            enabled.set(false);
            focus.invoke(app, customer, price);
            assertSame(price, scene.getFocusOwner());
            return null;
        });
    }

    @Test
    void mensajesElectronicosSoloSeMuestranConLaLuzEncendida() throws Exception {
        fx(() -> {
            PosDesktopFxApplication app = new PosDesktopFxApplication();
            Field mode = PosDesktopFxApplication.class.getDeclaredField("electronicInvoicingEnabled");
            mode.setAccessible(true);
            var enabled = (javafx.beans.property.BooleanProperty) mode.get(app);
            Label status = new Label();
            Method bind = PosDesktopFxApplication.class.getDeclaredMethod("bindSalesStatusVisibility", Label.class);
            bind.setAccessible(true);
            bind.invoke(app, status);
            Method electronic = PosDesktopFxApplication.class.getDeclaredMethod(
                    "setElectronicSalesStatus", Label.class, String.class, String.class);
            electronic.setAccessible(true);
            enabled.set(true);
            electronic.invoke(app, status, "No se pudo cargar Consumidor final.", "status-error");
            assertTrue(status.isVisible());
            enabled.set(false);
            assertFalse(status.isVisible());
            assertFalse(status.isManaged());
            // Una respuesta tardia tampoco debe aparecer en facturacion normal.
            electronic.invoke(app, status, "No se pudo enviar a Dataico.", "status-error");
            assertFalse(status.isVisible());
            enabled.set(true);
            assertTrue(status.isVisible());
            assertTrue(status.isManaged());
            enabled.set(false);
            Method normal = PosDesktopFxApplication.class.getDeclaredMethod("setSalesStatusInfo", Label.class, String.class);
            normal.setAccessible(true);
            normal.invoke(app, status, "Articulo agregado a la tabla.");
            assertTrue(status.isVisible());
            assertTrue(status.isManaged());
            assertFalse(status.getStyleClass().contains("status-error"));
            return null;
        });
    }

    @Test
    void configuracionDataicoCargaGuardaYExigeConfirmacionExplicita() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> body = new AtomicReference<>();
        server.createContext("/configuracion/dataico", exchange -> {
            boolean save = "PUT".equals(exchange.getRequestMethod());
            if (save) body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = ("""
                    {"success":true,"data":{"prefijo":"FEE","resolucion":"18760000001","siguienteNumero":%d,
                      "ambiente":"PRODUCCION","flexible":true,"version":%d,"accountId":"11111111-1111-4111-8111-111111111111",
                      "proveedorHabilitado":true,"tokenConfigurado":true,"baseUrl":"https://api.dataico.com/direct/dataico_api/v2",
                      "connectTimeoutSeconds":5,"readTimeoutSeconds":30}}
                    """).formatted(save ? 1789500105L : 1789500100L, save ? 1L : 0L).getBytes(StandardCharsets.UTF_8);
            try (exchange) {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        try {
            VBox root = fx(() -> {
                PosDesktopFxApplication app = new PosDesktopFxApplication();
                Field client = PosDesktopFxApplication.class.getDeclaredField("posApiClient");
                client.setAccessible(true);
                client.set(app, new PosApiClient("http://127.0.0.1:" + server.getAddress().getPort()));
                Method screen = PosDesktopFxApplication.class.getDeclaredMethod("createDataicoSettingsScreen");
                screen.setAccessible(true);
                VBox view = (VBox) screen.invoke(app);
                Scene scene = new Scene(view, 1150, 760);
                scene.getStylesheets().add(PosDesktopFxApplication.class.getResource("/com/posdesktop/pos/mockfx/mock-theme.css").toExternalForm());
                view.resize(1150, 760);
                view.applyCss();
                view.layout();
                return view;
            });
            esperarConfiguracion(root);
            fx(() -> {
                Button save = (Button) root.lookup("#dataico-save");
                CheckBox confirmation = (CheckBox) root.lookup("#dataico-confirmation");
                assertTrue(save.isDisabled());
                assertEquals("1789500100", ((TextField) root.lookup("#dataico-next-number")).getText());
                assertInstanceOf(PasswordField.class, root.lookup("#dataico-token"));
                assertEquals("", ((PasswordField) root.lookup("#dataico-token")).getText());
                assertEquals("11111111-1111-4111-8111-111111111111", ((TextField) root.lookup("#dataico-account-id")).getText());
                assertTrue(((CheckBox) root.lookup("#dataico-enabled")).isSelected());
                snapshot(root, "dataico-settings-wide", 1150, 760);
                snapshot(root, "dataico-settings-narrow", 760, 760);
                ((ToggleButton) root.lookup("#dataico-show-advanced")).fire();
                snapshot(root, "dataico-settings-advanced", 1150, 1000);
                ((ToggleButton) root.lookup("#dataico-show-advanced")).fire();
                confirmation.setSelected(true);
                assertFalse(save.isDisabled());
                ((TextField) root.lookup("#dataico-next-number")).setText("1789500105");
                assertFalse(confirmation.isSelected());
                assertTrue(save.isDisabled());
                ((PasswordField) root.lookup("#dataico-token")).setText("token_ficticio_de_prueba");
                ((TextField) root.lookup("#dataico-account-id")).setText("22222222-2222-4222-8222-222222222222");
                confirmation.setSelected(true);
                save.fire();
                assertTrue(save.isDisabled());
                return null;
            });
            esperarConfiguracion(root);
            fx(() -> {
                assertTrue(label(root, "#dataico-settings-status").getText().startsWith("Configuración guardada"));
                assertFalse(((CheckBox) root.lookup("#dataico-confirmation")).isSelected());
                assertEquals("", ((PasswordField) root.lookup("#dataico-token")).getText());
                return null;
            });
            assertNotNull(body.get());
            assertTrue(body.get().contains("\"siguienteNumeroActual\":1789500100"));
            assertTrue(body.get().contains("\"siguienteNumero\":1789500105"));
            assertTrue(body.get().contains("\"version\":0"));
            assertTrue(body.get().contains("\"token\":\"token_ficticio_de_prueba\""));
            assertTrue(body.get().contains("\"accountId\":\"22222222-2222-4222-8222-222222222222\""));
            assertTrue(body.get().contains("\"proveedorHabilitado\":true"));
            assertTrue(body.get().contains("\"connectTimeoutSeconds\":5"));
            assertTrue(body.get().contains("\"readTimeoutSeconds\":30"));
        } finally {
            server.stop(0);
        }
    }

    private static void esperarConfiguracion(VBox root) throws Exception {
        for (int i = 0; i < 100; i++) {
            if (fx(() -> !root.lookup("#dataico-reload").isDisabled())) return;
            Thread.sleep(50);
        }
        fail("La configuración no terminó a tiempo");
    }

    @Test
    void configuracionSoloEsAccesibleConLuzEncendidaYCierraAlApagarla() throws Exception {
        fx(() -> {
            PosDesktopFxApplication app = new PosDesktopFxApplication();
            Field sessionField = PosDesktopFxApplication.class.getDeclaredField("authenticatedSession");
            sessionField.setAccessible(true);
            sessionField.set(app, new PosApiClient.AuthSessionResponse(null, null, null, "admin-prueba", "Prueba",
                    java.util.List.of("ADMIN"), java.util.List.of("VENTAS_VIEW")));
            Method configure = PosDesktopFxApplication.class.getDeclaredMethod("configureAuthorizedScreens");
            configure.setAccessible(true);
            configure.invoke(app);
            Field factoriesField = PosDesktopFxApplication.class.getDeclaredField("screenFactories");
            factoriesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var factories = (java.util.Map<String, java.util.function.Supplier<Node>>) factoriesField.get(app);
            var aperturas = new java.util.concurrent.atomic.AtomicInteger();
            factories.put("Configuración Dataico", () -> {
                aperturas.incrementAndGet();
                VBox settings = new VBox();
                settings.setId("settings-prueba");
                return settings;
            });
            VBox ventas = new VBox();
            ventas.setId("ventas-prueba");
            Field cachedSales = PosDesktopFxApplication.class.getDeclaredField("cachedSalesScreen");
            cachedSales.setAccessible(true);
            cachedSales.set(app, ventas);
            Field hostField = PosDesktopFxApplication.class.getDeclaredField("contentHost");
            hostField.setAccessible(true);
            var host = (javafx.scene.layout.StackPane) hostField.get(app);
            Method sidebarMethod = PosDesktopFxApplication.class.getDeclaredMethod("createSidebar");
            sidebarMethod.setAccessible(true);
            VBox sidebar = (VBox) sidebarMethod.invoke(app);
            VBox root = new VBox(sidebar, host);
            new Scene(root, 1200, 800);
            root.applyCss();
            root.layout();
            ToggleButton light = (ToggleButton) sidebar.lookup(".electronic-invoice-toggle");
            ToggleButton settings = sidebar.lookupAll(".nav-button").stream()
                    .filter(node -> node instanceof ToggleButton button && "Configuración Dataico".equals(button.getText()))
                    .map(node -> (ToggleButton) node).findFirst().orElseThrow();
            assertFalse(settings.isVisible());
            assertFalse(settings.isManaged());
            Method show = PosDesktopFxApplication.class.getDeclaredMethod("showScreen", String.class);
            show.setAccessible(true);
            show.invoke(app, "Configuración Dataico");
            assertEquals(0, aperturas.get(), "Ni siquiera debe crear la configuración con la luz apagada");
            assertSame(ventas, host.getChildren().getFirst());

            light.fire();
            assertTrue(settings.isVisible());
            assertTrue(settings.isManaged());
            settings.fire();
            root.applyCss();
            root.layout();
            assertEquals(1, aperturas.get());
            assertNotNull(host.lookup("#settings-prueba"));
            light.fire();
            assertFalse(settings.isVisible());
            assertFalse(settings.isManaged());
            assertNull(host.lookup("#settings-prueba"));
            assertSame(ventas, host.getChildren().getFirst());
            assertEquals("Ventas", ((ToggleButton) settings.getToggleGroup().getSelectedToggle()).getText());

            // La restricción al rol ADMIN se mantiene incluso en modo electrónico.
            sessionField.set(app, new PosApiClient.AuthSessionResponse(null, null, null, "vendedor-prueba", "Prueba",
                    java.util.List.of("VENDEDOR"), java.util.List.of("VENTAS_VIEW")));
            configure.invoke(app);
            assertFalse(factories.containsKey("Configuración Dataico"));
            return null;
        });
    }

    @Test
    void barraGlobalFuncionaDesdeControlesNoEditablesEscenaYDialogos() throws Exception {
        fx(() -> {
            PosDesktopFxApplication app = new PosDesktopFxApplication();
            Button button = new Button("Botón de prueba");
            Label label = new Label("Área no editable");
            CheckBox check = new CheckBox("Check de prueba");
            var table = new javafx.scene.control.TableView<String>();
            TextField text = new TextField();
            VBox root = new VBox(button, label, check, table, text);
            Scene scene = new Scene(root, 800, 600);
            configurarAtajoGlobal(app, scene);
            Field modeField = PosDesktopFxApplication.class.getDeclaredField("electronicInvoicingEnabled");
            modeField.setAccessible(true);
            var mode = (javafx.beans.property.BooleanProperty) modeField.get(app);
            boolean expected = false;
            for (javafx.event.EventTarget target : java.util.List.of(button, label, check, table, text, root, scene)) {
                Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_TYPED, "/", "/", KeyCode.UNDEFINED, false, false, false, false));
                expected = !expected;
                assertEquals(expected, mode.get(), "Debe alternar exactamente una vez desde cualquier control");
            }
            assertEquals("", text.getText());
            assertFalse(check.isSelected());
            // Los modificadores de otros atajos no deben activar esta acción.
            Event.fireEvent(button, new KeyEvent(KeyEvent.KEY_TYPED, "/", "/", KeyCode.UNDEFINED, false, true, false, false));
            assertEquals(expected, mode.get());

            // El mismo manejador queda instalado en los diálogos, aunque no sean textboxes.
            javafx.stage.Stage dialog = new javafx.stage.Stage();
            Method popup = PosDesktopFxApplication.class.getDeclaredMethod("configurePopupEscapeClose", javafx.stage.Stage.class);
            popup.setAccessible(true);
            popup.invoke(app, dialog);
            Label dialogLabel = new Label("Diálogo de prueba");
            dialog.setScene(new Scene(new VBox(dialogLabel)));
            Event.fireEvent(dialogLabel, new KeyEvent(KeyEvent.KEY_TYPED, "/", "/", KeyCode.UNDEFINED, false, false, false, false));
            expected = !expected;
            assertEquals(expected, mode.get());
            dialog.close();

            Field session = PosDesktopFxApplication.class.getDeclaredField("authenticatedSession");
            session.setAccessible(true);
            session.set(app, null);
            Event.fireEvent(root, new KeyEvent(KeyEvent.KEY_TYPED, "/", "/", KeyCode.UNDEFINED, false, false, false, false));
            assertEquals(expected, mode.get(), "No debe cambiar el modo antes de iniciar sesión");
            session.set(app, new PosApiClient.AuthSessionResponse(null, null, null, "prueba", "Prueba",
                    java.util.List.of("ADMIN"), java.util.List.of("VENTAS_VIEW")));
            Field available = PosDesktopFxApplication.class.getDeclaredField("apiAvailable");
            available.setAccessible(true);
            ((javafx.beans.property.BooleanProperty) available.get(app)).set(false);
            Event.fireEvent(root, new KeyEvent(KeyEvent.KEY_TYPED, "/", "/", KeyCode.UNDEFINED, false, false, false, false));
            assertEquals(expected, mode.get(), "Debe respetar el bloqueo de disponibilidad de la API");
            return null;
        });
    }

    private static void configurarAtajoGlobal(PosDesktopFxApplication app, Scene scene) throws Exception {
        Field session = PosDesktopFxApplication.class.getDeclaredField("authenticatedSession");
        session.setAccessible(true);
        session.set(app, new PosApiClient.AuthSessionResponse(null, null, null, "prueba", "Prueba",
                java.util.List.of("ADMIN"), java.util.List.of("VENTAS_VIEW")));
        Field available = PosDesktopFxApplication.class.getDeclaredField("apiAvailable");
        available.setAccessible(true);
        ((javafx.beans.property.BooleanProperty) available.get(app)).set(true);
        Method shortcut = PosDesktopFxApplication.class.getDeclaredMethod("configureGlobalShortcuts", Scene.class);
        shortcut.setAccessible(true);
        shortcut.invoke(app, scene);
    }

    @Test
    void barraAlternaFacturacionSinEscribirElCaracterYActualizaElMenu() throws Exception {
        fx(() -> {
            PosDesktopFxApplication app = new PosDesktopFxApplication();
            Field factoriesField = PosDesktopFxApplication.class.getDeclaredField("screenFactories");
            factoriesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var factories = (java.util.Map<String, java.util.function.Supplier<Node>>) factoriesField.get(app);
            for (String key : java.util.List.of("Ventas", "Cierre", "Separados", "Movimientos")) {
                factories.put(key, VBox::new);
            }
            Method sidebarMethod = PosDesktopFxApplication.class.getDeclaredMethod("createSidebar");
            sidebarMethod.setAccessible(true);
            VBox sidebar = (VBox) sidebarMethod.invoke(app);
            Method screen = PosDesktopFxApplication.class.getDeclaredMethod("createSalesScreen");
            screen.setAccessible(true);
            VBox sales = (VBox) screen.invoke(app);
            VBox root = new VBox(sidebar, sales);
            Scene scene = new Scene(root, 1200, 800);
            configurarAtajoGlobal(app, scene);
            root.applyCss();
            root.layout();
            ToggleButton light = (ToggleButton) sidebar.lookup(".electronic-invoice-toggle");
            TextField price = sales.lookupAll(".text-field").stream().filter(node -> node instanceof TextField field
                    && "Valor unitario".equals(field.getPromptText())).map(node -> (TextField) node).findFirst().orElseThrow();
            assertFalse(light.isSelected());
            Event.fireEvent(price, new KeyEvent(KeyEvent.KEY_TYPED, "/", "/", KeyCode.UNDEFINED, false, false, false, false));
            assertTrue(light.isSelected());
            assertEquals("", price.getText());
            for (Node node : sidebar.lookupAll(".nav-button")) {
                if (node instanceof ToggleButton button && !"Ventas".equals(button.getText())) {
                    assertFalse(button.isVisible());
                    assertFalse(button.isManaged());
                }
            }
            // También funciona cuando la distribución del teclado necesita Shift para escribir /.
            Event.fireEvent(price, new KeyEvent(KeyEvent.KEY_TYPED, "/", "/", KeyCode.UNDEFINED, true, false, false, false));
            assertFalse(light.isSelected());
            assertEquals("", price.getText());
            for (Node node : sidebar.lookupAll(".nav-button")) {
                assertTrue(node.isVisible());
                assertTrue(node.isManaged());
            }
            return null;
        });
    }

    @Test
    void compactaFiltrosYCalculaTotalDelPeriodoConsultadoConEstadosDeCargaVacioYError() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> respuesta = new AtomicReference<>("""
                {"success":true,"data":[
                  {"id":"1","numeroFactura":"FEE1","fechaEmision":"2026-10-16T14:53:00","clienteIdentificacion":"222222222222","clienteNombre":"CONSUMIDOR FINAL","total":6000},
                  {"id":"2","numeroFactura":"FEE2","fechaEmision":"2026-10-16T14:23:00","clienteIdentificacion":"222222222222","clienteNombre":"CONSUMIDOR FINAL","total":3000},
                  {"id":"3","numeroFactura":"FEE3","fechaEmision":"2026-10-16T14:21:00","clienteIdentificacion":"123456789","clienteNombre":"Cliente de prueba","total":3000},
                  {"id":"4","numeroFactura":"FEE4","fechaEmision":"2026-10-16T13:28:00","clienteIdentificacion":"987654321","clienteNombre":"Cliente de prueba","total":3000}
                ]}
                """);
        AtomicReference<String> query = new AtomicReference<>();
        server.createContext("/ventas/facturas-electronicas", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            String json = respuesta.get();
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            try (exchange) {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(json.contains("false") ? 500 : 200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        try {
            VBox root = fx(() -> {
                PosDesktopFxApplication app = new PosDesktopFxApplication();
                Field client = PosDesktopFxApplication.class.getDeclaredField("posApiClient");
                client.setAccessible(true);
                client.set(app, new PosApiClient("http://127.0.0.1:" + server.getAddress().getPort()));
                Method screen = PosDesktopFxApplication.class.getDeclaredMethod("createElectronicInvoicesScreen");
                screen.setAccessible(true);
                VBox view = (VBox) screen.invoke(app);
                Scene scene = new Scene(view, 1150, 690);
                scene.getStylesheets().add(PosDesktopFxApplication.class.getResource("/com/posdesktop/pos/mockfx/mock-theme.css").toExternalForm());
                view.resize(1150, 690);
                view.applyCss();
                view.layout();
                return view;
            });
            esperarConsulta(root);
            fx(() -> {
                assertTrue(label(root, ".electronic-invoice-total-amount").getText().contains("15.000"));
                String fecha = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                assertEquals("Total facturado del " + fecha + " al " + fecha,
                        label(root, ".electronic-invoice-total-period").getText());
                VBox filtros = (VBox) root.lookup(".electronic-invoice-filters");
                FlowPane estados = (FlowPane) filtros.getChildren().get(2);
                assertFalse(estados.isVisible());
                assertFalse(estados.isManaged());
                assertTrue(filtros.getHeight() < 190, "El filtro habitual debe ocupar menos de 190 px");
                snapshot(root, "electronic-invoices-wide", 1150, 690);
                ToggleButton desplegar = (ToggleButton) root.lookup(".electronic-invoice-filters .toggle-button");
                desplegar.fire();
                assertTrue(estados.isVisible());
                assertTrue(estados.isManaged());
                snapshot(root, "electronic-invoices-states", 1150, 690);
                desplegar.fire();
                snapshot(root, "electronic-invoices-narrow", 760, 690);
                for (var row : filtros.getChildren()) {
                    if (row instanceof FlowPane pane && pane.isManaged()) {
                        for (var child : pane.getChildren()) {
                            assertTrue(child.getBoundsInParent().getMaxX() <= pane.getWidth() + 1,
                                    "Los controles deben envolverse sin desbordar la fila");
                        }
                    }
                }
                // Capturar la consulta antes de que el usuario vuelva a editar los controles.
                FlowPane periodo = (FlowPane) filtros.getChildren().get(0);
                DatePicker inicio = (DatePicker) ((VBox) periodo.getChildren().get(0)).getChildren().get(1);
                inicio.setValue(LocalDate.now().minusDays(2));
                boton(root).fire();
                assertEquals("—", label(root, ".electronic-invoice-total-amount").getText());
                inicio.setValue(LocalDate.now().minusYears(1));
                return null;
            });
            esperarConsulta(root);
            assertTrue(query.get().contains(LocalDate.now().minusDays(2).toString()));
            fx(() -> {
                assertFalse(label(root, ".electronic-invoice-total-period").getText()
                        .contains(LocalDate.now().minusYears(1).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
                return null;
            });
            respuesta.set("{\"success\":true,\"data\":[]}");
            fx(() -> { boton(root).fire(); return null; });
            esperarConsulta(root);
            fx(() -> {
                assertTrue(label(root, ".electronic-invoice-total-amount").getText().endsWith("0"));
                assertEquals("0 facturas encontradas", label(root, ".electronic-invoice-summary").getText());
                return null;
            });
            respuesta.set("{\"success\":false,\"message\":\"Error de prueba\"}");
            fx(() -> { boton(root).fire(); return null; });
            esperarConsulta(root);
            fx(() -> {
                assertEquals("—", label(root, ".electronic-invoice-total-amount").getText());
                assertTrue(label(root, ".electronic-invoice-summary").getText().contains("No se pudieron"));
                return null;
            });
            respuesta.set("{\"success\":true,\"data\":[]}");
            fx(() -> {
                root.lookupAll(".electronic-invoice-quick-filter").stream()
                        .map(node -> (Button) node).filter(button -> "Día".equals(button.getText()))
                        .findFirst().orElseThrow().fire();
                return null;
            });
            esperarConsulta(root);
            assertTrue(query.get().contains("fechaInicial=" + LocalDate.now()));
            assertTrue(query.get().contains("fechaFinal=" + LocalDate.now()));
        } finally {
            server.stop(0);
        }
    }

    private static Label label(VBox root, String selector) { return (Label) root.lookup(selector); }
    @Test
    void configuracionFiscalSeparaAmbientesLimpiaSecretosYNoDesborda() throws Exception {
        AtomicReference<String> savedProvider=new AtomicReference<>();
        AtomicReference<Boolean> fail=new AtomicReference<>(false);
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/facturacion/",exchange->{String path=exchange.getRequestURI().getPath();String body;
            if(path.equals("/facturacion/proveedor")) {
                if("PUT".equals(exchange.getRequestMethod()))savedProvider.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
                body="{\"success\":true,\"data\":{\"provider\":\"DATAICO\",\"version\":0,\"directEmissionEnabled\":false}}";
            } else {
                String env=path.endsWith("PRODUCCION")?"PRODUCCION":"PRUEBAS";
                body="{\"success\":true,\"data\":{\"environment\":\""+env+"\",\"version\":0,\"configuration\":null,\"technicalKeyConfigured\":false,\"softwarePinConfigured\":false,\"certificateConfigured\":false}}";
            }
            if(fail.get())body="{\"success\":false,\"message\":\"Error de prueba\"}";
            try(exchange){byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(fail.get()?500:200,bytes.length);exchange.getResponseBody().write(bytes);}
        });server.start();
        try {
            VBox root=fx(()->{
                var pane=new com.posdesktop.pos.fiscal.FiscalSettingsPane(new PosApiClient("http://127.0.0.1:"+server.getAddress().getPort()));
                VBox view=new VBox(pane);Scene scene=new Scene(view,1150,800);scene.getStylesheets().add(PosDesktopFxApplication.class.getResource("/com/posdesktop/pos/mockfx/mock-theme.css").toExternalForm());view.resize(1150,800);view.applyCss();view.layout();return view;
            });
            waitUntil(()->!((Button)root.lookup("#dian-settings-save")).isDisabled()&&!((Button)root.lookup("#fiscal-provider-save")).isDisabled());
            fx(()->{
                ((TextField)root.lookup("#dian-nit")).setText("860324218");((PasswordField)root.lookup("#dian-software-pin")).setText("PIN-LOCAL-PRUEBA");
                ((javafx.scene.control.ComboBox<?>)root.lookup("#fiscal-provider")).getSelectionModel().selectLast();((Button)root.lookup("#fiscal-provider-save")).fire();
                snapshot(root,"fiscal-settings-wide",1150,1800);snapshot(root,"fiscal-settings-narrow",740,2400);
                for(Node node:root.lookupAll(".fiscal-flow")) {FlowPane row=(FlowPane)node;for(Node child:row.getChildren())assertTrue(child.getBoundsInParent().getMaxX()<=row.getWidth()+1,"Los campos fiscales deben envolver a la siguiente fila.");}
                return null;
            });
            waitUntil(()->savedProvider.get()!=null);assertTrue(savedProvider.get().contains("DIAN_DIRECT"));
            fx(()->{((javafx.scene.control.ComboBox<?>)root.lookup("#dian-settings-environment")).getSelectionModel().selectLast();return null;});
            waitUntil(()->((Label)root.lookup("#dian-settings-status")).getText().contains("PRODUCCION cargada"));
            fx(()->{assertEquals("",((TextField)root.lookup("#dian-nit")).getText());assertEquals("",((PasswordField)root.lookup("#dian-software-pin")).getText());return null;});
            fail.set(true);fx(()->{((Button)root.lookup("#dian-settings-reload")).fire();return null;});
            waitUntil(()->((Label)root.lookup("#dian-settings-status")).getText().contains("No se pudo confirmar"));
            assertTrue(fx(()->((Button)root.lookup("#dian-settings-save")).isDisabled()));
        } finally {server.stop(0);}
    }

    @Test
    void documentosFiscalesDistinguenBorradorDeIntentoYRespetanPermisoDeEdicion() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/facturacion/documentos",exchange->{String body="""
            {"success":true,"data":[
              {"id":"1","saleId":"s1","customerId":"c1","provider":"DATAICO","purpose":"ENVIO","environment":"PRUEBAS","number":"TEST1","customer":"Cliente de prueba","total":100,"state":"INCIERTO","createdAt":"2026-10-30T10:00:00"},
              {"id":"2","saleId":"s2","customerId":"c2","provider":"DIAN_DIRECT","purpose":"BORRADOR","environment":"PRUEBAS","number":"TEST2","customer":"Cliente de prueba","total":100,"state":"BORRADOR_SIN_FIRMA","createdAt":"2026-10-30T10:00:00"}
            ]}
            """;try(exchange){byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);}});server.start();
        try {
            VBox root=fx(()->{var pane=new com.posdesktop.pos.fiscal.FiscalDocumentsPane(new PosApiClient("http://127.0.0.1:"+server.getAddress().getPort()),true);VBox view=new VBox(pane);Scene scene=new Scene(view,1150,740);scene.getStylesheets().add(PosDesktopFxApplication.class.getResource("/com/posdesktop/pos/mockfx/mock-theme.css").toExternalForm());return view;});
            waitUntil(()->!((Button)root.lookup("#fiscal-documents-reload")).isDisabled());
            fx(()->{
                var table=(javafx.scene.control.TableView<?>)root.lookup("#fiscal-documents-table");assertEquals(2,table.getItems().size());
                table.getSelectionModel().selectFirst();assertFalse(((Button)root.lookup("#fiscal-attempt-consult")).isDisabled());assertTrue(((Button)root.lookup("#fiscal-attempt-retry")).isDisabled());
                table.getSelectionModel().selectLast();assertTrue(((Button)root.lookup("#fiscal-attempt-consult")).isDisabled());assertTrue(((Button)root.lookup("#fiscal-attempt-retry")).isDisabled());
                snapshot(root,"fiscal-documents-wide",1150,740);return null;
            });
        }finally{server.stop(0);}
    }
    @Test
    void evidenciaPermiteFirmaDePruebaYVerificacionSinEnviarFactura()throws Exception {
        var signed=new java.util.concurrent.atomic.AtomicBoolean();var verified=new java.util.concurrent.atomic.AtomicBoolean();
        var unexpected=new java.util.concurrent.atomic.AtomicInteger();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        String summary="\"id\":\"local-1\",\"saleId\":\"sale\",\"customerId\":\"customer\",\"provider\":\"DIAN_DIRECT\",\"purpose\":\"BORRADOR\",\"environment\":\"PRUEBAS\",\"number\":\"PRUEBA1\",\"customer\":\"Cliente de prueba\",\"total\":100,\"createdAt\":\"2026-10-30T10:00:00\"";
        server.createContext("/",exchange->{String path=exchange.getRequestURI().getPath(),body;
            String s="{"+summary+",\"state\":\""+(signed.get()?"FIRMADO_LOCAL":"BORRADOR_SIN_FIRMA")+"\"}";
            if(path.equals("/facturacion/documentos"))body="{\"success\":true,\"data\":["+s+"]}";
            else if(path.equals("/facturacion/documentos/local-1"))body="{\"success\":true,\"data\":{\"summary\":"+s+",\"format\":\""+(signed.get()?"DIAN_SIGNED_LOCAL_V1":"DIAN_INVOICE_V1")+"\",\"snapshot\":\"{}\",\"xml\":\"<Invoice/>\",\"hash\":\"huella-local\",\"events\":[]}}";
            else if(path.equals("/facturacion/documentos/local-1/firma-local")&&"POST".equals(exchange.getRequestMethod())){signed.set(true);body="{\"success\":true,\"data\":{\"id\":\"local-1\",\"number\":\"PRUEBA1\",\"state\":\"FIRMADO_LOCAL\",\"signatureValid\":true,\"trustVerified\":false,\"readyToTransmit\":false}}";}
            else if(path.endsWith("/firma-local/validacion")){verified.set(true);body="{\"success\":true,\"data\":{\"valid\":true,\"trustVerified\":false,\"readyToTransmit\":false,\"issues\":[]}}";}
            else{unexpected.incrementAndGet();body="{\"success\":false}";}
            try(exchange){byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);}
        });server.start();
        try {
            VBox root=fx(()->{VBox view=new VBox(new com.posdesktop.pos.fiscal.FiscalDocumentsPane(new PosApiClient("http://127.0.0.1:"+server.getAddress().getPort()),true));new Scene(view,1150,740);return view;});
            waitUntil(()->!((Button)root.lookup("#fiscal-documents-reload")).isDisabled());
            fx(()->{((javafx.scene.control.TableView<?>)root.lookup("#fiscal-documents-table")).getSelectionModel().selectFirst();((Button)root.lookup("#fiscal-document-view")).fire();return null;});
            waitUntil(()->javafx.stage.Window.getWindows().stream().anyMatch(w->w.getScene().lookup("#fiscal-sign-local")!=null));
            fx(()->{var window=javafx.stage.Window.getWindows().stream().filter(w->w.getScene().lookup("#fiscal-sign-local")!=null).findFirst().orElseThrow();
                Button sign=(Button)window.getScene().lookup("#fiscal-sign-local");assertTrue(sign.isVisible());assertFalse(window.getScene().lookup("#fiscal-verify-local").isVisible());
                snapshot((VBox)((javafx.scene.control.ScrollPane)window.getScene().getRoot()).getContent(),"fiscal-evidence-sign",900,680);sign.fire();return null;});
            waitUntil(()->signed.get()&&!((Button)root.lookup("#fiscal-documents-reload")).isDisabled());
            fx(()->{javafx.stage.Window.getWindows().stream().filter(w->w instanceof javafx.stage.Stage).toList().forEach(w->((javafx.stage.Stage)w).close());
                ((javafx.scene.control.TableView<?>)root.lookup("#fiscal-documents-table")).getSelectionModel().selectFirst();((Button)root.lookup("#fiscal-document-view")).fire();return null;});
            waitUntil(()->javafx.stage.Window.getWindows().stream().anyMatch(w->{var v=w.getScene().lookup("#fiscal-verify-local");return v!=null&&v.isVisible();}));
            fx(()->{var window=javafx.stage.Window.getWindows().stream().filter(w->w.getScene().lookup("#fiscal-verify-local")!=null).findFirst().orElseThrow();assertFalse(window.getScene().lookup("#fiscal-sign-local").isVisible());((Button)window.getScene().lookup("#fiscal-verify-local")).fire();return null;});
            waitUntil(verified::get);assertEquals(0,unexpected.get());
        }finally{fx(()->{javafx.stage.Window.getWindows().stream().filter(w->w instanceof javafx.stage.Stage).toList().forEach(w->((javafx.stage.Stage)w).close());return null;});server.stop(0);}
    }
    private static void waitUntil(Callable<Boolean> condition) throws Exception {for(int i=0;i<150;i++){if(fx(condition))return;Thread.sleep(30);}fail("La vista fiscal no terminó de actualizarse.");}
    @Test
    void borradorDianExigeClasificacionExplicitaYGuardaSinPedirEnvio() throws Exception {
        AtomicReference<String> sent=new AtomicReference<>(),saved=new AtomicReference<>();java.util.concurrent.atomic.AtomicInteger unexpected=new java.util.concurrent.atomic.AtomicInteger();
        String saleId="11111111-1111-4111-8111-111111111111",customerId="22222222-2222-4222-8222-222222222222";
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{String path=exchange.getRequestURI().getPath(),body;
            if(path.equals("/facturacion/dian/preparacion/PRUEBAS"))body="{\"success\":true,\"data\":{\"environment\":\"PRUEBAS\",\"configurationVersion\":1,\"prefix\":\"PRUEBA\",\"previewNumber\":1,\"validFrom\":\"2026-01-01\",\"validUntil\":\"2026-12-31\",\"issuerName\":\"EMPRESA FICTICIA\",\"secretsConfigured\":true}}";
            else if(path.equals("/facturacion/ventas/"+saleId+"/borrador")){sent.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));body="{\"success\":true,\"data\":{\"id\":\"draft-1\",\"number\":\"PRUEBA1\",\"state\":\"BORRADOR_SIN_FIRMA\",\"xml\":\"<Invoice/>\",\"hash\":\"solo-prueba-ui\",\"readyToTransmit\":false}}";}
            else {unexpected.incrementAndGet();body="{\"success\":false,\"message\":\"Ruta inesperada\"}";}
            try(exchange){byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);}});server.start();
        try{
            VBox root=fx(()->{var detail=new PosApiClient.DetalleVentaResponse("detail",1,"ARTÍCULO DE PRUEBA",java.math.BigDecimal.ONE,new java.math.BigDecimal("100"),new java.math.BigDecimal("100"),new java.math.BigDecimal("100"));
                var sale=new PosApiClient.VentaRegistradaResponse(saleId,"VTA-PRUEBA",java.time.LocalDateTime.of(2026,10,30,10,30),new java.math.BigDecimal("100"),new java.math.BigDecimal("100"),new java.math.BigDecimal("100"),java.math.BigDecimal.ZERO,1,java.util.List.of(detail));
                var pane=new com.posdesktop.pos.fiscal.DianDraftPane(new PosApiClient("http://127.0.0.1:"+server.getAddress().getPort()),sale,customerId,"Cliente de prueba",r->saved.set(r.state()));
                VBox view=new VBox(pane);Scene scene=new Scene(view,1040,840);scene.getStylesheets().add(PosDesktopFxApplication.class.getResource("/com/posdesktop/pos/mockfx/mock-theme.css").toExternalForm());view.resize(1040,840);view.applyCss();view.layout();return view;});
            waitUntil(()->!((Button)root.lookup("#dian-draft-generate")).isDisabled());
            fx(()->{((Button)root.lookup("#dian-draft-generate")).fire();assertNull(sent.get());
                ((TextField)root.lookup("#dian-customer-duties")).setText("R-99-PN");
                ((javafx.scene.control.ComboBox<?>)root.lookup("#dian-customer-tax")).getSelectionModel().selectLast();
                for(String id:new String[]{"#dian-payment-form","#dian-payment-means","#dian-line-unit"})((javafx.scene.control.ComboBox<?>)root.lookup(id)).getSelectionModel().selectFirst();
                ((javafx.scene.control.ComboBox<?>)root.lookup("#dian-line-treatment-1")).getSelectionModel().selectLast();
                snapshot(root,"dian-draft-form",1040,900);((Button)root.lookup("#dian-draft-generate")).fire();return null;});
            waitUntil(()->saved.get()!=null);assertEquals("BORRADOR_SIN_FIRMA",saved.get());assertTrue(sent.get().contains("EXCLUDED"));assertTrue(sent.get().contains(customerId));assertEquals(0,unexpected.get());
        }finally{server.stop(0);}
    }
    private static Button boton(VBox root) { return (Button) root.lookup(".electronic-invoice-filter-action"); }

    private static void esperarConsulta(VBox root) throws Exception {
        for (int i = 0; i < 100; i++) {
            if (fx(() -> !boton(root).isDisabled())) return;
            Thread.sleep(50);
        }
        fail("La consulta no terminó a tiempo");
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(15, TimeUnit.SECONDS);
    }

    private static void snapshot(VBox view, String name, double width, double height) throws Exception {
        view.resize(width, height);
        view.applyCss();
        view.layout();
        WritableImage image = view.snapshot(null, null);
        BufferedImage png = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < png.getHeight(); y++) {
            for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        }
        Path output = Path.of("target", "ui-review", name + ".png");
        Files.createDirectories(output.getParent());
        ImageIO.write(png, "png", output.toFile());
    }
}
