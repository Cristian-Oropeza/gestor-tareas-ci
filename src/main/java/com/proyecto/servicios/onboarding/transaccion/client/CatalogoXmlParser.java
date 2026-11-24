package com.proyecto.servicios.onboarding.transaccion.client;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser XML del getProductList.do. GestoPago/PuntoRed devuelve un documento
 * tipo:
 *   <RESPONSE>
 *     <MENSAJE CODIGO="01">OK</MENSAJE>
 *     <PRODUCTO ID_SERVICIO="TELCEL" ID_PRODUCTO="TELCEL_100" DESCRIPCION="..."
 *               TIPO_FRONT="1" PRECIO="100.00" MONTO_MIN="20.00" MONTO_MAX="500.00"
 *               URL_IMAGEN="https://..." />
 *     ...
 *   </RESPONSE>
 *
 * El formato exacto puede variar por version del proveedor; el parser acepta
 * atributos en mayusculas o minusculas y tolera que algunos campos falten.
 * XXE deshabilitado por seguridad.
 */
@Component
@Slf4j
public class CatalogoXmlParser {

    @Getter @Setter
    public static class ProductoXml {
        String idServicio, idProducto, descripcion, imagenUrl;
        Short tipoFront, idCatTipoServicio;
        BigDecimal precio, montoMin, montoMax;
    }

    public List<ProductoXml> parsear(String xml) {
        if (xml == null || xml.isBlank()) return List.of();
        List<ProductoXml> out = new ArrayList<>();
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            // Seguridad: deshabilitar XXE
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");

            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));
            while (reader.hasNext()) {
                reader.next();
                if (reader.isStartElement()
                        && reader.getLocalName().equalsIgnoreCase("PRODUCTO")) {
                    out.add(leerProducto(reader));
                }
            }
            reader.close();
            return out;
        } catch (Exception ex) {
            log.warn("Catalogo sync: fallo al parsear XML - {}", ex.getMessage());
            return List.of();
        }
    }

    private ProductoXml leerProducto(XMLStreamReader r) {
        ProductoXml p = new ProductoXml();
        for (int i = 0; i < r.getAttributeCount(); i++) {
            String name = r.getAttributeLocalName(i).toUpperCase();
            String val  = r.getAttributeValue(i);
            if (val == null || val.isBlank()) continue;
            switch (name) {
                case "ID_SERVICIO"         -> p.idServicio = val.trim();
                case "ID_PRODUCTO"         -> p.idProducto = val.trim();
                case "DESCRIPCION"         -> p.descripcion = val.trim();
                case "URL_IMAGEN", "IMAGEN_URL" -> p.imagenUrl = val.trim();
                case "TIPO_FRONT"          -> p.tipoFront = parseShort(val);
                case "ID_CAT_TIPO_SERVICIO" -> p.idCatTipoServicio = parseShort(val);
                case "PRECIO"              -> p.precio = parseBd(val);
                case "MONTO_MIN"           -> p.montoMin = parseBd(val);
                case "MONTO_MAX"           -> p.montoMax = parseBd(val);
                default -> { /* ignore unknown attrs */ }
            }
        }
        return p;
    }

    private Short parseShort(String v) { try { return Short.parseShort(v.trim()); } catch (Exception e) { return null; } }
    private BigDecimal parseBd(String v) { try { return new BigDecimal(v.trim()); } catch (Exception e) { return null; } }
}
