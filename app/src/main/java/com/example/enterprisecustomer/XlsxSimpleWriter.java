package com.example.enterprisecustomer;

import android.content.ContentResolver;
import android.net.Uri;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class XlsxSimpleWriter {
    public static void write(ContentResolver resolver, Uri uri, List<String> headers, List<List<String>> rows) throws Exception {
        OutputStream os = resolver.openOutputStream(uri);
        if (os == null) throw new Exception("无法创建导出文件");
        ZipOutputStream zip = new ZipOutputStream(os);
        add(zip, "[Content_Types].xml", contentTypes());
        add(zip, "_rels/.rels", rels());
        add(zip, "xl/workbook.xml", workbook());
        add(zip, "xl/_rels/workbook.xml.rels", workbookRels());
        add(zip, "xl/styles.xml", styles());
        add(zip, "xl/worksheets/sheet1.xml", sheet(headers, rows));
        zip.close();
        os.close();
    }

    private static void add(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String contentTypes() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"+
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"+
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"+
                "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"+
                "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"+
                "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"+
                "</Types>";
    }
    private static String rels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"+
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"+
                "</Relationships>";
    }
    private static String workbook() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"+
                "<sheets><sheet name=\"企业客户\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"+
                "</workbook>";
    }
    private static String workbookRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"+
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"+
                "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"+
                "</Relationships>";
    }
    private static String styles() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+
                "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"+
                "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"+
                "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>"+
                "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"+
                "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/><xf numFmtId=\"49\" fontId=\"1\" fillId=\"0\" borderId=\"0\" applyNumberFormat=\"1\"/></cellXfs>"+
                "</styleSheet>";
    }

    private static String sheet(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">");
        sb.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>");
        sb.append("<cols>");
        for (int i=1;i<=headers.size();i++) sb.append("<col min=\"").append(i).append("\" max=\"").append(i).append("\" width=\"18\" customWidth=\"1\"/>");
        sb.append("</cols>");
        sb.append("<sheetData>");
        sb.append(rowXml(1, headers, true));
        int r = 2;
        for (List<String> row : rows) sb.append(rowXml(r++, row, false));
        sb.append("</sheetData>");
        sb.append("</worksheet>");
        return sb.toString();
    }

    private static String rowXml(int rowNum, List<String> values, boolean header) {
        StringBuilder sb = new StringBuilder();
        sb.append("<row r=\"").append(rowNum).append("\">");
        for (int i=0;i<values.size();i++) {
            String ref = colName(i+1) + rowNum;
            sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"");
            if (header) sb.append(" s=\"1\"");
            sb.append("><is><t>").append(esc(values.get(i))).append("</t></is></c>");
        }
        sb.append("</row>");
        return sb.toString();
    }

    private static String colName(int n) {
        StringBuilder sb = new StringBuilder();
        while (n > 0) { int rem = (n - 1) % 26; sb.insert(0, (char)('A' + rem)); n = (n - 1) / 26; }
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
