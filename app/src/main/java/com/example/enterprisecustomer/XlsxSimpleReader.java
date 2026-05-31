package com.example.enterprisecustomer;

import android.content.ContentResolver;
import android.net.Uri;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

public class XlsxSimpleReader {
    public static List<Map<String, String>> readFirstSheet(ContentResolver resolver, Uri uri) throws Exception {
        byte[] workbookBytes;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new Exception("无法打开文件");
            workbookBytes = readAll(in);
        }
        Map<String, byte[]> entries = unzip(workbookBytes);
        List<String> shared = readSharedStrings(entries.get("xl/sharedStrings.xml"));
        byte[] sheet = entries.get("xl/worksheets/sheet1.xml");
        if (sheet == null) {
            for (String k : entries.keySet()) {
                if (k.startsWith("xl/worksheets/sheet") && k.endsWith(".xml")) { sheet = entries.get(k); break; }
            }
        }
        if (sheet == null) throw new Exception("Excel中没有找到工作表");
        return readSheet(sheet, shared);
    }

    private static Map<String, byte[]> unzip(byte[] data) throws Exception {
        HashMap<String, byte[]> map = new HashMap<>();
        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data));
        ZipEntry e;
        while ((e = zis.getNextEntry()) != null) {
            if (!e.isDirectory()) map.put(e.getName(), readAll(zis));
            zis.closeEntry();
        }
        zis.close();
        return map;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static List<String> readSharedStrings(byte[] xml) throws Exception {
        ArrayList<String> list = new ArrayList<>();
        if (xml == null) return list;
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        NodeList si = doc.getElementsByTagName("si");
        for (int i = 0; i < si.getLength(); i++) {
            Element item = (Element) si.item(i);
            NodeList texts = item.getElementsByTagName("t");
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < texts.getLength(); j++) sb.append(texts.item(j).getTextContent());
            list.add(sb.toString());
        }
        return list;
    }

    private static List<Map<String, String>> readSheet(byte[] xml, List<String> shared) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        NodeList rows = doc.getElementsByTagName("row");
        ArrayList<Map<Integer, String>> rawRows = new ArrayList<>();
        int maxCol = 0;
        for (int i = 0; i < rows.getLength(); i++) {
            Element row = (Element) rows.item(i);
            LinkedHashMap<Integer, String> map = new LinkedHashMap<>();
            NodeList cells = row.getElementsByTagName("c");
            for (int j = 0; j < cells.getLength(); j++) {
                Element c = (Element) cells.item(j);
                int col = colFromRef(c.getAttribute("r"));
                String v = cellValue(c, shared);
                map.put(col, v);
                if (col > maxCol) maxCol = col;
            }
            rawRows.add(map);
        }
        if (rawRows.isEmpty()) return new ArrayList<>();
        Map<Integer, String> headerRow = rawRows.get(0);
        ArrayList<String> headers = new ArrayList<>();
        for (int c = 1; c <= maxCol; c++) {
            String h = headerRow.get(c);
            if (h == null || h.trim().isEmpty()) h = "未命名字段" + c;
            headers.add(h.trim());
        }
        ArrayList<Map<String, String>> result = new ArrayList<>();
        for (int r = 1; r < rawRows.size(); r++) {
            Map<Integer, String> raw = rawRows.get(r);
            LinkedHashMap<String, String> rowMap = new LinkedHashMap<>();
            boolean any = false;
            for (int c = 1; c <= maxCol; c++) {
                String v = raw.get(c);
                if (v == null) v = "";
                v = v.trim();
                if (!v.isEmpty()) any = true;
                rowMap.put(headers.get(c - 1), v);
            }
            if (any) result.add(rowMap);
        }
        return result;
    }

    private static String cellValue(Element c, List<String> shared) {
        String t = c.getAttribute("t");
        if ("inlineStr".equals(t)) {
            NodeList texts = c.getElementsByTagName("t");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < texts.getLength(); i++) sb.append(texts.item(i).getTextContent());
            return sb.toString();
        }
        NodeList vs = c.getElementsByTagName("v");
        if (vs.getLength() == 0) return "";
        String v = vs.item(0).getTextContent();
        if ("s".equals(t)) {
            try {
                int idx = Integer.parseInt(v.trim());
                if (idx >= 0 && idx < shared.size()) return shared.get(idx);
            } catch (Exception ignored) {}
        }
        return normalizeNumberText(v);
    }

    private static String normalizeNumberText(String v) {
        if (v == null) return "";
        String s = v.trim();
        if (s.matches("[-+]?\\d+\\.0+")) return s.substring(0, s.indexOf('.'));
        if (s.matches("[-+]?\\d+(\\.\\d+)?[Ee][-+]?\\d+")) {
            try { return new BigDecimal(s).toPlainString().replaceFirst("\\.0+$", ""); } catch (Exception ignored) {}
        }
        return s;
    }

    private static int colFromRef(String ref) {
        if (ref == null || ref.isEmpty()) return 1;
        int col = 0;
        for (int i = 0; i < ref.length(); i++) {
            char ch = ref.charAt(i);
            if (ch >= 'A' && ch <= 'Z') col = col * 26 + (ch - 'A' + 1);
            else if (ch >= 'a' && ch <= 'z') col = col * 26 + (ch - 'a' + 1);
            else break;
        }
        return col <= 0 ? 1 : col;
    }
}
