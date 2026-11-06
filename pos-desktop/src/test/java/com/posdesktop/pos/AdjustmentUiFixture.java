package com.posdesktop.pos;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.*;

/** API local simulada, sin credenciales ni servicios externos. */
final class AdjustmentUiFixture implements AutoCloseable {
    final HttpServer server;
    final AtomicInteger reviews=new AtomicInteger(),saves=new AtomicInteger(),unexpected=new AtomicInteger();
    final AtomicReference<String> request=new AtomicReference<>();
    final AtomicBoolean fail=new AtomicBoolean();
    AdjustmentUiFixture()throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",e->{String path=e.getRequestURI().getPath(),method=e.getRequestMethod(),body;
            if(path.equals("/ajustes/facturas"))body="""
                {"success":true,"data":[{"id":"source","provider":"DATAICO","number":"FE123","customer":"Cliente de prueba","environment":"PRUEBAS","total":357}]}
                """;
            else if(path.equals("/ajustes/facturas/DATAICO/source"))body=fail.get()?"{\"success\":false,\"message\":\"Revisa la conexion Dataico\"}":"""
                {"success":true,"data":{"id":"source","provider":"DATAICO","environment":"PRUEBAS","number":"FE123","customer":"Cliente de prueba","sourceHash":"hash","total":357,
                "lines":[{"order":1,"description":"Artículo de prueba con IVA","originalQuantity":3,"available":%s,"unitPrice":100,"taxes":"IVA 19%%"}],
                "numbering":{"configured":true,"version":1,"creditPrefix":"NC","creditNext":1,"debitPrefix":"ND","debitNext":1}}}
                """.formatted(saves.get()>0?"2":"3");
            else if(path.equals("/ajustes/revisar")){
                reviews.incrementAndGet();request.set(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
                boolean cancel=request.get().contains("CANCELLATION"),increase=request.get().contains("INCREASE");
                var data=new com.fasterxml.jackson.databind.ObjectMapper().readTree(request.get());var qty=data.get("lines").get(0).get("quantity").decimalValue();var net=cancel?new java.math.BigDecimal("300"):qty.multiply(new java.math.BigDecimal("100"));var tax=net.multiply(new java.math.BigDecimal("0.19"));
                body="{\"success\":true,\"data\":{\"number\":\""+(increase?"ND1":"NC1")+"\",\"plan\":{\"kind\":\""+(cancel?"CANCELLATION":increase?"INCREASE":"RETURN")+"\",\"allocations\":[],\"net\":"+net+",\"tax\":"+tax+",\"total\":"+net.add(tax)+"}}}";
            }else if(path.equals("/ajustes")&&method.equals("POST")){saves.incrementAndGet();request.set(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));body="{\"success\":true,\"data\":"+summary()+"}";}
            else if(path.equals("/ajustes"))body="{\"success\":true,\"data\":"+(saves.get()>0?"["+summary()+"]":"[]")+"}";
            else {unexpected.incrementAndGet();body="{\"success\":false,\"message\":\"Ruta no esperada en la prueba\"}";}
            try(e){byte[] bytes=body.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);}
        });server.start();
    }
    private String summary(){return "{\"id\":\"adjustment\",\"documentId\":\"evidence\",\"sourceNumber\":\"FE123\",\"number\":\"NC1\",\"provider\":\"DATAICO\",\"environment\":\"PRUEBAS\",\"kind\":\"RETURN\",\"state\":\"BORRADOR\",\"total\":119,\"createdAt\":\"2026-11-06T10:00:00\"}";}
    String url(){return "http://127.0.0.1:"+server.getAddress().getPort();}
    @Override public void close(){server.stop(0);}
}
