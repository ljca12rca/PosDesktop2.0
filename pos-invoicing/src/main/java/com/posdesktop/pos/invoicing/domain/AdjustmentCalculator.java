package com.posdesktop.pos.invoicing.domain;

import java.math.*;
import java.util.*;

/** Politica pura de cantidades y valores. Nunca consulta entidades ni proveedores. */
public final class AdjustmentCalculator {
    public record Selection(int order,BigDecimal quantity,BigDecimal unitIncrease) { }
    public record Allocation(int order,FiscalInvoice.Line line) { }
    public record Plan(AdjustmentKind kind,List<Allocation> allocations,BigDecimal net,BigDecimal tax,BigDecimal total) {
        public Plan { allocations=List.copyOf(allocations); }
    }
    public Plan calculate(List<FiscalInvoice.Line> original,List<Allocation> reserved,boolean otherAdjustments,
            AdjustmentKind kind,List<Selection> selections) {
        Objects.requireNonNull(kind);Objects.requireNonNull(selections);
        if(original.isEmpty()||original.size()>500)throw new IllegalArgumentException("La factura no tiene un detalle compatible.");
        if(kind==AdjustmentKind.CANCELLATION&&otherAdjustments)throw new IllegalArgumentException("La factura ya tiene ajustes. Revisa el historial y devuelve solo las cantidades disponibles.");
        Map<Integer,Selection> chosen=new HashMap<>();
        for(var s:selections) {
            if(s.order()<1||s.order()>original.size()||chosen.put(s.order(),s)!=null)throw new IllegalArgumentException("Articulo repetido o inexistente.");
            quantity(s.quantity());
            if(s.unitIncrease()==null||s.unitIncrease().signum()<0||Math.max(0,s.unitIncrease().stripTrailingZeros().scale())>6)throw new IllegalArgumentException("Incremento unitario invalido.");
        }
        List<Allocation> lines=new ArrayList<>();BigDecimal net=BigDecimal.ZERO,tax=BigDecimal.ZERO;
        for(int i=0;i<original.size();i++) {
            int order=i+1;var line=original.get(i);var selected=chosen.get(order);
            BigDecimal used=reserved.stream().filter(a->a.order()==order).map(a->a.line().quantity()).reduce(BigDecimal.ZERO,BigDecimal::add);
            BigDecimal available=line.quantity().subtract(used);
            BigDecimal q=kind==AdjustmentKind.CANCELLATION?line.quantity():selected==null?BigDecimal.ZERO:selected.quantity();
            if(q.signum()==0)continue;
            if(kind.credit()&&q.compareTo(available)>0)throw new IllegalArgumentException("La cantidad de '"+line.description()+"' supera la disponible ("+available.toPlainString()+").");
            if(!kind.credit()&&q.compareTo(line.quantity())>0)throw new IllegalArgumentException("El incremento no puede usar mas unidades que la factura original.");
            BigDecimal price=kind.credit()?line.unitPrice():selected.unitIncrease();
            if(price.signum()<=0)throw new IllegalArgumentException("Ingresa un incremento unitario mayor que cero.");
            BigDecimal discount=BigDecimal.ZERO,charge=BigDecimal.ZERO;
            if(kind.credit()) {
                discount=proportion(line.discount(),q,line.quantity());charge=proportion(line.charge(),q,line.quantity());
                if(q.compareTo(available)==0) {
                    discount=line.discount().subtract(reserved.stream().filter(a->a.order()==order).map(a->a.line().discount()).reduce(BigDecimal.ZERO,BigDecimal::add));
                    charge=line.charge().subtract(reserved.stream().filter(a->a.order()==order).map(a->a.line().charge()).reduce(BigDecimal.ZERO,BigDecimal::add));
                }
            }
            List<FiscalInvoice.Tax> taxes=new ArrayList<>();
            for(var t:line.taxes()) {
                if(!kind.credit()&&t.perUnit()!=null)throw new IllegalArgumentException("El ajuste de tributos por unidad requiere una base fisica explicita; usa otro documento.");
                var u=t.perUnit();
                taxes.add(u==null?t:new FiscalInvoice.Tax(t.treatment(),t.schemeId(),t.schemeName(),t.percent(),new FiscalInvoice.UnitTax(proportion(u.quantity(),q,line.quantity()),u.unitCode(),u.referenceQuantity(),u.rate())));
            }
            var adjusted=new FiscalInvoice.Line(line.reference(),line.description(),q,line.unitCode(),price,discount,charge,taxes);
            var amounts=new InvoiceCalculator().calculateLine(adjusted);net=net.add(amounts.net());
            tax=tax.add(amounts.taxes().stream().map(InvoiceCalculator.TaxAmount::amount).reduce(BigDecimal.ZERO,BigDecimal::add));
            lines.add(new Allocation(order,adjusted));
        }
        if(lines.isEmpty())throw new IllegalArgumentException("Indica al menos una cantidad mayor que cero.");
        return new Plan(kind,lines,Amounts.money(net),Amounts.money(tax),Amounts.money(net.add(tax)));
    }
    public BigDecimal available(int order,FiscalInvoice.Line source,List<Allocation> reserved){return source.quantity().subtract(reserved.stream().filter(a->a.order()==order).map(a->a.line().quantity()).reduce(BigDecimal.ZERO,BigDecimal::add));}
    private static BigDecimal proportion(BigDecimal amount,BigDecimal q,BigDecimal original){return amount.multiply(q).divide(original,2,Amounts.ROUNDING);}
    private static void quantity(BigDecimal q){if(q==null||q.signum()<0||Math.max(0,q.stripTrailingZeros().scale())>6)throw new IllegalArgumentException("Cantidad invalida: admite hasta seis decimales.");}
}
