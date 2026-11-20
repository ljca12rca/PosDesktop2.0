package com.posdesktop.pos.invoicing.infrastructure.transport;

import com.posdesktop.pos.invoicing.infrastructure.xml.DianInvoiceDraftGenerator;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.w3c.dom.*;

/** Parser cerrado, sin entidades externas; recibir ZIP no equivale a autorizar el documento. */
public final class DianSoapResponseParser {
    public static final String SOAP="http://www.w3.org/2003/05/soap-envelope",WCF="http://wcf.dian.colombia";
    public static final String UPLOAD="http://schemas.datacontract.org/2004/07/UploadDocumentResponse",RESPONSE="http://schemas.datacontract.org/2004/07/DianResponse";
    public enum State{RECEIVED,PENDING,ACCEPTED,REJECTED,UNKNOWN,FAULT}
    public record Reply(State state,String trackId,String code,String message,List<String> errors,String applicationResponse){public Reply{errors=List.copyOf(errors);}}
    public Reply upload(String xml){
        try{
            Element body=body(xml);var fault=fault(body);if(fault!=null)return fault;
            Element result=DianDocumentIdentity.child(DianDocumentIdentity.child(body,WCF,"SendTestSetAsyncResponse"),WCF,"SendTestSetAsyncResult");
            String track=text(result,UPLOAD,"ZipKey");var errors=errors(result,"ErrorMessageList");
            if(track!=null&&!track.isBlank()){
                if(track.length()>200||!track.matches("[A-Za-z0-9_-]+"))return unknown();
                return new Reply(State.RECEIVED,track,null,"ZIP recibido; consulta pendiente.",errors,null);
            }
            // Sin acuse verificable no se habilita otro POST, incluso si el servidor devolvio errores.
            return new Reply(State.UNKNOWN,null,null,"No se recibio un TrackId verificable.",errors,null);
        }catch(Exception e){return unknown();}
    }
    public Reply status(String xml,String method,String expectedKey,String expectedFile){
        if(!Set.of("GetStatusZip","GetStatus").contains(method))throw new IllegalArgumentException("Metodo no soportado.");
        try{
            Element body=body(xml);var fault=fault(body);if(fault!=null)return fault;
            Element result=DianDocumentIdentity.child(DianDocumentIdentity.child(body,WCF,method+"Response"),WCF,method+"Result");
            List<Element> replies=new ArrayList<>();
            if("GetStatus".equals(method))replies.add(result);
            else for(Node n=result.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e&&RESPONSE.equals(e.getNamespaceURI())&&"DianResponse".equals(e.getLocalName()))replies.add(e);
            if(replies.isEmpty()||replies.size()>50)return unknown();
            var matched=replies.stream().filter(e->expectedKey.equals(text(e,RESPONSE,"XmlDocumentKey"))).toList();
            if(matched.size()>1)return unknown();
            if(matched.isEmpty()){
                if(replies.size()==1&&Set.of("66","90","98").contains(Objects.toString(text(replies.getFirst(),RESPONSE,"StatusCode"),"")))return new Reply(State.PENDING,null,text(replies.getFirst(),RESPONSE,"StatusCode"),"El documento aun no tiene un resultado final.",errors(replies.getFirst(),"ErrorMessage"),null);
                return unknown();
            }
            var r=matched.getFirst();String file=text(r,RESPONSE,"XmlFileName");
            if(file!=null&&!file.isBlank()&&!withoutExtension(file).equals(withoutExtension(expectedFile)))return unknown();
            String valid=text(r,RESPONSE,"IsValid"),code=text(r,RESPONSE,"StatusCode");var errors=errors(r,"ErrorMessage");
            String description=text(r,RESPONSE,"StatusDescription"),message=text(r,RESPONSE,"StatusMessage");
            String application=decode(text(r,RESPONSE,"XmlBase64Bytes"));if(application==null)application=decode(text(r,RESPONSE,"XmlBytes"));
            State state=("true".equals(valid)||"1".equals(valid))&&"00".equals(code)&&errors.isEmpty()?State.ACCEPTED
                    :("false".equals(valid)||"0".equals(valid))&&"99".equals(code)?State.REJECTED
                    :Set.of("66","90","98").contains(Objects.toString(code,""))?State.PENDING:State.UNKNOWN;
            return new Reply(state,null,code,limit(message==null?description==null?"Respuesta recibida sin descripcion.":description:message,2000),errors,application);
        }catch(Exception e){return unknown();}
    }
    private static Element body(String xml)throws Exception{if(xml==null||xml.length()>8_000_000)throw new IllegalArgumentException();Element root=DianInvoiceDraftGenerator.parse(xml).getDocumentElement();if(!SOAP.equals(root.getNamespaceURI())||!"Envelope".equals(root.getLocalName()))throw new IllegalArgumentException();return DianDocumentIdentity.child(root,SOAP,"Body");}
    private static Reply fault(Element body){var faults=body.getElementsByTagNameNS(SOAP,"Fault");if(faults.getLength()==0)return null;if(faults.getLength()!=1)return unknown();Element f=(Element)faults.item(0);return new Reply(State.FAULT,null,null,"El servicio devolvio un SOAP Fault.",List.of(limit(f.getTextContent().strip(),2000)),null);}
    private static String text(Element p,String ns,String name){Element found=null;for(Node n=p.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element e&&ns.equals(e.getNamespaceURI())&&name.equals(e.getLocalName())){if(found!=null)throw new IllegalArgumentException();found=e;}if(found==null||"true".equals(found.getAttributeNS("http://www.w3.org/2001/XMLSchema-instance","nil")))return null;return found.getTextContent().strip();}
    private static List<String> errors(Element root,String container){var nodes=root.getElementsByTagNameNS("*",container);List<String> result=new ArrayList<>();for(int i=0;i<nodes.getLength();i++){var children=nodes.item(i).getChildNodes();for(int j=0;j<children.getLength();j++)if(children.item(j) instanceof Element e){String value=e.getTextContent().strip();if(!value.isEmpty())result.add(limit(value,2000));if(result.size()>100)throw new IllegalArgumentException();}}return List.copyOf(result);}
    private static String decode(String value)throws Exception{if(value==null||value.isBlank())return null;if(value.length()>3_000_000)throw new IllegalArgumentException();String xml=new String(Base64.getMimeDecoder().decode(value),StandardCharsets.UTF_8);DianInvoiceDraftGenerator.parse(xml);return xml;}
    private static String withoutExtension(String s){return s.endsWith(".xml")?s.substring(0,s.length()-4):s;}
    private static String limit(String text,int max){return text.length()>max?text.substring(0,max):text;}
    private static Reply unknown(){return new Reply(State.UNKNOWN,null,null,"Respuesta no correlacionada o sin resultado verificable. Consulta el intento existente.",List.of(),null);}
}
