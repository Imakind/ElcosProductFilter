package com.example.productfilter.controller;

import com.example.productfilter.dto.ProposalParams;
import com.example.productfilter.model.Product;
import com.example.productfilter.util.FileNames;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Picture;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

@Controller
@RequestMapping("/proposal")
public class ProposalController {

    // ---------- PDF ----------
    @PostMapping(value = "/pdf", produces = "application/pdf")
    public void downloadProposalPdf(@ModelAttribute ProposalParams params,
                                    HttpServletResponse response,
                                    HttpSession session) throws IOException {
        String projectName = Optional.ofNullable((String) session.getAttribute("projectName")).orElse("Проект");
        String user = FileNames.currentUser();
        String fnamePdf = ensurePdfExt(FileNames.kpPdf(projectName, user));

        @SuppressWarnings("unchecked")
        Map<Integer, Integer> cart = (Map<Integer, Integer>) session.getAttribute("proposalCart");
        @SuppressWarnings("unchecked")
        List<Product> products = (List<Product>) session.getAttribute("proposalProducts");
        @SuppressWarnings("unchecked")
        Map<Integer, Double> coefficientMap = (Map<Integer, Double>) session.getAttribute("proposalCoefficients");
        if (coefficientMap == null) coefficientMap = new HashMap<>();
        if (cart == null || cart.isEmpty() || products == null) {
            response.sendError(404, "Корзина пуста или данные предложения отсутствуют");
            return;
        }

        @SuppressWarnings("unchecked")
        Map<Integer, Long> productSection = (Map<Integer, Long>) session.getAttribute("productSection");
        if (productSection == null) productSection = new HashMap<>();
        @SuppressWarnings("unchecked")
        Map<Long, String> sections = (Map<Long, String>) session.getAttribute("sections");
        if (sections == null || sections.isEmpty()) {
            sections = new LinkedHashMap<>();
            sections.put(1L, "Блок 1");
        }

        List<String> footerLines = buildFooterLines(params);

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", contentDisposition(fnamePdf));
        byte[] pdf = buildPdf(products, cart, coefficientMap, productSection, sections, session, footerLines);

        try (OutputStream out = response.getOutputStream()) {
            out.write(pdf);
        }
    }

    // ---------- Excel ----------
    @PostMapping("/excel-kp")
    public void downloadProposalExcelKp(@ModelAttribute ProposalParams params,
                                        HttpServletResponse response,
                                        HttpSession session) throws IOException {
        String projectName = Optional.ofNullable((String) session.getAttribute("projectName")).orElse("Проект");
        String user = FileNames.currentUser();
        String fnameXlsx = FileNames.kpXlsx(projectName, user);
        List<String> footerLines = buildFooterLines(params);

        @SuppressWarnings("unchecked")
        Map<Integer, Integer> cart = (Map<Integer, Integer>) session.getAttribute("proposalCart");
        @SuppressWarnings("unchecked")
        List<Product> products = (List<Product>) session.getAttribute("proposalProducts");
        @SuppressWarnings("unchecked")
        Map<Integer, Double> coefficientMap = (Map<Integer, Double>) session.getAttribute("proposalCoefficients");
        if (cart == null || cart.isEmpty() || products == null) {
            response.sendRedirect("/cart");
            return;
        }
        if (coefficientMap == null) coefficientMap = new HashMap<>();

        @SuppressWarnings("unchecked")
        Map<Integer, Long> productSection = (Map<Integer, Long>) session.getAttribute("productSection");
        if (productSection == null) productSection = new HashMap<>();
        @SuppressWarnings("unchecked")
        Map<Long, String> sections = (Map<Long, String>) session.getAttribute("sections");
        if (sections == null || sections.isEmpty()) {
            sections = new LinkedHashMap<>();
            sections.put(1L, "Блок 1");
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", rfc5987ContentDisposition(fnameXlsx));

        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream out = response.getOutputStream()) {
            XSSFSheet sh = wb.createSheet("Коммерческое предложение");
            sh.setDisplayGridlines(false);

            PrintSetup ps = sh.getPrintSetup();
            ps.setPaperSize(PrintSetup.A4_PAPERSIZE);
            ps.setLandscape(false);
            sh.setMargin(Sheet.LeftMargin, 0.4);
            sh.setMargin(Sheet.RightMargin, 0.4);
            sh.setMargin(Sheet.TopMargin, 0.5);
            sh.setMargin(Sheet.BottomMargin, 0.5);
            wb.setPrintArea(0, 0, 8, 0, 200);

            DataFormat df = wb.createDataFormat();

            // Базовые шрифты Inter, жирные
            Font fBase = wb.createFont();
            fBase.setFontName("Inter");
            fBase.setBold(true);
            fBase.setFontHeightInPoints((short) 10);

            Font fHead = wb.createFont();
            fHead.setFontName("Inter");
            fHead.setBold(true);
            fHead.setColor(IndexedColors.WHITE.getIndex());

            CellStyle base = wb.createCellStyle();
            base.setFont(fBase);

            // заголовок колонок
            CellStyle th = wb.createCellStyle();
            th.cloneStyleFrom(base);
            ((XSSFCellStyle) th).setFillForegroundColor(new XSSFColor(new java.awt.Color(10, 160, 160), null));
            th.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            th.setFont(fHead);
            setBorders(th, BorderStyle.THIN, IndexedColors.WHITE.getIndex());
            th.setAlignment(HorizontalAlignment.CENTER);
            th.setVerticalAlignment(VerticalAlignment.CENTER);

            // обычные ячейки
            CellStyle td = wb.createCellStyle();
            td.cloneStyleFrom(base);
            setBorders(td, BorderStyle.THIN, IndexedColors.GREY_25_PERCENT.getIndex());
            td.setVerticalAlignment(VerticalAlignment.CENTER);
            td.setWrapText(true);

            CellStyle tdNum = wb.createCellStyle();
            tdNum.cloneStyleFrom(td);
            tdNum.setDataFormat(df.getFormat("# ##0"));

            CellStyle tdMoney = wb.createCellStyle();
            tdMoney.cloneStyleFrom(td);
            tdMoney.setDataFormat(df.getFormat("# ##0"));

            // строка группы (блок) — как в PDF: голубой фон, чёрная рамка
            CellStyle grp = wb.createCellStyle();
            grp.cloneStyleFrom(base);
            ((XSSFCellStyle) grp).setFillForegroundColor(new XSSFColor(new java.awt.Color(131, 226, 255), null));
            grp.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            grp.setAlignment(HorizontalAlignment.LEFT);
            grp.setVerticalAlignment(VerticalAlignment.CENTER);
            grp.setWrapText(true);
            setBorders(grp, BorderStyle.THIN, IndexedColors.BLACK.getIndex());

            CellStyle totalLbl = wb.createCellStyle();
            totalLbl.cloneStyleFrom(base);
            totalLbl.setAlignment(HorizontalAlignment.RIGHT);

            // нижняя бирюзовая полоса "Итого, в том числе НДС 12%" — как в PDF
            CellStyle totalGrand = wb.createCellStyle();
            totalGrand.cloneStyleFrom(totalLbl);
            ((XSSFCellStyle) totalGrand).setFillForegroundColor(new XSSFColor(new java.awt.Color(10, 160, 160), null));
            totalGrand.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font fWhiteBold = wb.createFont();
            fWhiteBold.setFontName("Inter");
            fWhiteBold.setBold(true);
            fWhiteBold.setColor(IndexedColors.WHITE.getIndex());
            totalGrand.setFont(fWhiteBold);
            totalGrand.setAlignment(HorizontalAlignment.RIGHT);
            totalGrand.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorders(totalGrand, BorderStyle.THIN, IndexedColors.BLACK.getIndex());

            CellStyle totalVal = wb.createCellStyle();
            totalVal.cloneStyleFrom(totalLbl);
            totalVal.setDataFormat(df.getFormat("# ##0"));
            totalVal.setAlignment(HorizontalAlignment.RIGHT);

            int r = 4;
            Row brandBar = sh.createRow(r++);
            brandBar.setHeightInPoints(6);

            // "Кому" и шапка
            Row hdr1 = sh.createRow(r++);
            cell(hdr1, 0, "Кому:", base);
            String recipient = Optional.ofNullable((String) session.getAttribute("recipientName")).orElse("");
            cell(hdr1, 1, recipient, base);

            Row hdr2 = sh.createRow(r++);
            cell(hdr2, 0, "Мы благодарим Вас за Ваш запрос. Просим рассмотреть наше ценовое предложение", base);

            Row hdr3 = sh.createRow(r++);
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            cell(hdr3, 7, dateStr, base);
            try {
                byte[] logo = tryReadLogo(session);
                if (logo != null) {
                    int picIdx = wb.addPicture(logo, Workbook.PICTURE_TYPE_PNG);
                    CreationHelper helper = wb.getCreationHelper();
                    Drawing<?> drawing = sh.createDrawingPatriarch();
                    ClientAnchor anchor = helper.createClientAnchor();
                    anchor.setCol1(7);
                    anchor.setRow1(1);
                    Picture pict = drawing.createPicture(anchor, picIdx);
                    pict.resize(0.2, 0.2);
                }
            } catch (Exception ignore) {
            }
            r++;

            // заголовок таблицы
            Row head = sh.createRow(r++);
            String[] cols = {"№", "Наименование", "Ед.изм", "Кол-во", "Цена за ед., тг", "Сумма с учётом НДС, тг"};
            for (int c = 0; c < cols.length; c++) {
                Cell hc = head.createCell(c);
                hc.setCellValue(cols[c]);
                hc.setCellStyle(th);
            }

            sh.setColumnWidth(0, 256 * 6);
            sh.setColumnWidth(1, 256 * 48);
            sh.setColumnWidth(2, 256 * 10);
            sh.setColumnWidth(3, 256 * 10);
            sh.setColumnWidth(4, 256 * 18);
            sh.setColumnWidth(5, 256 * 22);

            Map<Long, List<Product>> bySection = new LinkedHashMap<>();
            for (Product p : products) {
                long sid = Optional.ofNullable(productSection.get(p.getProductId())).orElse(1L);
                bySection.computeIfAbsent(sid, k -> new ArrayList<>()).add(p);
            }

            int idx = 1;
            long totalQty = 0;
            double grand = 0.0;

            for (Map.Entry<Long, List<Product>> e : bySection.entrySet()) {
                String blockName = sections.getOrDefault(e.getKey(), "Блок");
                Row gr = sh.createRow(r++);
                Cell g0 = gr.createCell(0);
                g0.setCellValue(blockName);
                g0.setCellStyle(grp);
                for (int c = 1; c < 6; c++) {
                    Cell cc = gr.createCell(c);
                    cc.setCellStyle(grp);
                }
                sh.addMergedRegion(new CellRangeAddress(gr.getRowNum(), gr.getRowNum(), 0, 5));

                for (Product p : e.getValue()) {
                    int qty = cart.getOrDefault(p.getProductId(), 0);
                    if (qty <= 0) continue;

                    double basePrice = Optional.ofNullable(p.getPrice()).orElse(0.0);
                    double k = coefficientMap.getOrDefault(p.getProductId(), 1.0);
                    double price = basePrice * k;
                    double sum = price * qty;

                    Row row = sh.createRow(r++);
                    cell(row, 0, (double) idx++, tdNum);
                    cell(row, 1, nz(p.getName()), td);
                    cell(row, 2, "шт", td);
                    cell(row, 3, (double) qty, tdNum);
                    cell(row, 4, price, tdMoney);
                    cell(row, 5, sum, tdMoney);

                    totalQty += qty;
                    grand += sum;
                }
            }

            // бирюзовая полоса сразу после последней строки таблицы (без пустой строки)
            Row it = sh.createRow(r++);
            for (int c = 0; c <= 5; c++) {
                Cell cell = it.createCell(c);
                cell.setCellStyle(totalGrand);
            }
            it.getCell(0).setCellValue("Итого, в том числе НДС 12%");
            sh.addMergedRegion(new CellRangeAddress(it.getRowNum(), it.getRowNum(), 0, 4));
            it.getCell(5).setCellValue(grand);

            // текст под таблицей
            r++;
            for (String line : footerLines) {
                Row row = sh.createRow(r++);
                cell(row, 0, "• " + line, base);
                sh.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 0, 5));
            }
            Row note = sh.createRow(r++);
            sh.addMergedRegion(new CellRangeAddress(note.getRowNum(), note.getRowNum(), 0, 5));

            for (int i = 0; i <= sh.getLastRowNum(); i++) {
                Row rr = sh.getRow(i);
                if (rr != null) rr.setHeightInPoints(-1);
            }

            wb.write(out);
        }
    }

    // ---------- SVG → PNG ----------
    static byte[] svgToPngBytes(Path svgPath, Float widthPx, Float heightPx) throws IOException {
        try (InputStream in = Files.newInputStream(svgPath);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PNGTranscoder t = new PNGTranscoder();
            if (widthPx != null) t.addTranscodingHint(PNGTranscoder.KEY_WIDTH, widthPx);
            if (heightPx != null) t.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, heightPx);
            TranscoderInput input = new TranscoderInput(in);
            TranscoderOutput output = new TranscoderOutput(out);
            t.transcode(input, output);
            out.flush();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IOException("SVG→PNG conversion failed", e);
        }
    }

    // ---------- PDF renderer ----------
    private static byte[] buildPdf(List<Product> products,
                                   Map<Integer, Integer> cart,
                                   Map<Integer, Double> coeffs,
                                   Map<Integer, Long> productSection,
                                   Map<Long, String> sections,
                                   HttpSession session,
                                   List<String> footerLines) throws IOException {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Fonts fonts = Fonts.load();

            // брендовый цвет текста для содержимого таблицы
            Color brandColor = new Color(7, 124, 149);
            com.lowagie.text.Font BRAND_10 = new com.lowagie.text.Font(fonts.BOLD_BLACK_10);
            BRAND_10.setColor(brandColor);

            // логотип справа
            try {
                Path svg = Paths.get("C:\\Users\\alikt\\Downloads\\product-filter\\src\\main\\resources\\elcos_logo.svg");
                byte[] png = svgToPngBytes(svg, 500f, null);
                Image img = Image.getInstance(png);
                img.scaleToFit(200, 100);
                img.setAlignment(Image.RIGHT);
                doc.add(img);
            } catch (Exception ignore) {
            }

            // «Кому:» и дата
            PdfPTable hdr = new PdfPTable(2);
            hdr.setWidthPercentage(100);
            hdr.setWidths(new float[]{70, 10});
            PdfPCell left = new PdfPCell();
            left.setBorder(PdfPCell.NO_BORDER);
            PdfPCell right = new PdfPCell();
            right.setBorder(PdfPCell.NO_BORDER);
            right.setHorizontalAlignment(Element.ALIGN_RIGHT);

            String recipient = Optional.ofNullable((String) session.getAttribute("recipientName")).orElse("");
            Paragraph p1 = new Paragraph();
            p1.add(new Phrase("Кому: ", fonts.BOLD_BLACK_10));
            p1.add(new Phrase(recipient, fonts.BOLD_BLACK_10));
            p1.setSpacingAfter(10f);
            left.addElement(p1);
            left.addElement(new Phrase("Мы благодарим Вас за Ваш запрос. Просим рассмотреть наше ценовое предложение", fonts.BOLD_BLACK_10));

            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            right.addElement(new Phrase(dateStr, fonts.BOLD_BLACK_10));

            hdr.addCell(left);
            hdr.addCell(right);
            doc.add(hdr);
            doc.add(Chunk.NEWLINE);

            Paragraph smallGap = new Paragraph(" ");
            smallGap.setSpacingBefore(2f);
            smallGap.setSpacingAfter(0f);
            doc.add(smallGap);

            // Таблица КП
            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{6f, 48f, 10f, 10f, 18f, 22f});

            Color turquoise = new Color(10, 160, 160);
            addHeaderCell(table, "№", fonts.BOLD_WHITE_10, turquoise);
            addHeaderCell(table, "Наименование", fonts.BOLD_WHITE_10, turquoise);
            addHeaderCell(table, "Ед.изм", fonts.BOLD_WHITE_10, turquoise);
            addHeaderCell(table, "Кол-во", fonts.BOLD_WHITE_10, turquoise);
            addHeaderCell(table, "Цена за ед., тг", fonts.BOLD_WHITE_10, turquoise);
            addHeaderCell(table, "Сумма с учётом НДС, тг", fonts.BOLD_WHITE_10, turquoise);

            Map<Long, List<Product>> bySection = new LinkedHashMap<>();
            for (Product p : products) {
                long sid = Optional.ofNullable(productSection.get(p.getProductId())).orElse(1L);
                bySection.computeIfAbsent(sid, k -> new ArrayList<>()).add(p);
            }

            int idx = 1;
            long totalQty = 0;
            double grand = 0.0;

            Color groupBg = new Color(131, 226, 255);
            for (Map.Entry<Long, List<Product>> e : bySection.entrySet()) {
                String blockName = sections.getOrDefault(e.getKey(), "Блок");
                PdfPCell grpCell = new PdfPCell(new Phrase(blockName, BRAND_10));
                grpCell.setColspan(6);
                grpCell.setBackgroundColor(groupBg);
                grpCell.setPadding(6f);
                grpCell.setBorderColor(Color.BLACK);
                grpCell.setBorderWidth(0.5f);
                table.addCell(grpCell);

                for (Product p : e.getValue()) {
                    int qty = cart.getOrDefault(p.getProductId(), 0);
                    if (qty <= 0) continue;
                    double base = Optional.ofNullable(p.getPrice()).orElse(0.0);
                    double k = coeffs.getOrDefault(p.getProductId(), 1.0);
                    double price = base * k;
                    double sum = price * qty;

                    addBodyCell(table, String.valueOf(idx++), Element.ALIGN_CENTER, BRAND_10);
                    addBodyCell(table, nz(p.getName()), Element.ALIGN_LEFT, BRAND_10);
                    addBodyCell(table, "шт", Element.ALIGN_CENTER, BRAND_10);
                    addBodyCell(table, String.valueOf(qty), Element.ALIGN_RIGHT, BRAND_10);
                    addBodyCell(table, num(price), Element.ALIGN_RIGHT, BRAND_10);
                    addBodyCell(table, num(sum), Element.ALIGN_RIGHT, BRAND_10);

                    totalQty += qty;
                    grand += sum;
                }
            }

            doc.add(table);

            // нижняя бирюзовая полоса
            PdfPTable grandTbl = new PdfPTable(2);
            grandTbl.setWidthPercentage(100);
            grandTbl.setWidths(new float[]{80f, 20f});

            PdfPCell gText = new PdfPCell(new Phrase("Итого, в том числе НДС 12%", fonts.BOLD_WHITE_10));
            gText.setBackgroundColor(turquoise);
            gText.setHorizontalAlignment(Element.ALIGN_RIGHT);
            gText.setVerticalAlignment(Element.ALIGN_MIDDLE);
            gText.setPadding(6f);
            gText.setBorderColor(Color.BLACK);
            gText.setBorderWidth(0.5f);

            PdfPCell gSum = new PdfPCell(new Phrase(num(grand), fonts.BOLD_WHITE_10));
            gSum.setBackgroundColor(turquoise);
            gSum.setHorizontalAlignment(Element.ALIGN_RIGHT);
            gSum.setVerticalAlignment(Element.ALIGN_MIDDLE);
            gSum.setPadding(6f);
            gSum.setBorderColor(Color.BLACK);
            gSum.setBorderWidth(0.5f);

            grandTbl.addCell(gText);
            grandTbl.addCell(gSum);

            doc.add(grandTbl);

            doc.add(Chunk.NEWLINE);
            for (String line : footerLines) {
                Paragraph par = new Paragraph("• " + line, fonts.BOLD_BLACK_10);
                par.setSpacingBefore(2f);
                par.setSpacingAfter(2f);
                doc.add(par);
            }

            doc.close();
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IOException("Ошибка генерации PDF", e);
        }
    }

    private static void addHeaderCell(PdfPTable t, String text, com.lowagie.text.Font font, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setBackgroundColor(bg);
        c.setPadding(6f);
        c.setBorderColor(Color.BLACK);
        c.setBorderWidth(0.5f);
        t.addCell(c);
    }

    private static void addBodyCell(PdfPTable t, String text, int align, com.lowagie.text.Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text != null ? text : "", font));
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(5f);
        c.setBorderColor(Color.BLACK);
        c.setBorderWidth(0.5f);
        t.addCell(c);
    }

    // ---------- helpers ----------
    private static void setBorders(CellStyle st, BorderStyle bs, short color) {
        st.setBorderTop(bs);
        st.setTopBorderColor(color);
        st.setBorderBottom(bs);
        st.setBottomBorderColor(color);
        st.setBorderLeft(bs);
        st.setLeftBorderColor(color);
        st.setBorderRight(bs);
        st.setRightBorderColor(color);
    }

    private static Cell cell(Row r, int c, String v, CellStyle s) {
        Cell x = r.createCell(c);
        x.setCellValue(v);
        x.setCellStyle(s);
        return x;
    }

    private static Cell cell(Row r, int c, double v, CellStyle s) {
        Cell x = r.createCell(c);
        x.setCellValue(v);
        x.setCellStyle(s);
        return x;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String num(double v) {
        return String.format(Locale.forLanguageTag("ru-RU"), "%,.2f", v);
    }

    private static String ensurePdfExt(String name) {
        if (name == null || name.isBlank()) return "file.pdf";
        return name.toLowerCase().endsWith(".pdf") ? name : name + ".pdf";
    }

    private static String contentDisposition(String filenameUtf8) {
        String ascii = filenameUtf8.replace('"', '\'').replaceAll("[^A-Za-z0-9._-]", "_");
        String encoded = URLEncoder.encode(filenameUtf8, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }

    private static String rfc5987ContentDisposition(String filenameUtf8) {
        return contentDisposition(filenameUtf8);
    }

    /**
     * Читает логотип.
     * 1) logoPath из сессии (PNG/JPEG);
     * 2) /static/img/elcos.png из classpath;
     * 3) elcos_logo.svg на диске -> svgToPngBytes (тот же, что для PDF).
     */
    private static byte[] tryReadLogo(HttpSession session) {
        // 1) путь в сессии
        Object p = session.getAttribute("logoPath");
        if (p instanceof String path) {
            try {
                return Files.readAllBytes(Paths.get(path));
            } catch (Exception ignore) {
            }
        }

        // 2) PNG в resources/static/img
        try (InputStream is = ProposalController.class.getResourceAsStream("/static/img/elcos.png")) {
            if (is != null) return is.readAllBytes();
        } catch (Exception ignore) {
        }

        // 3) SVG-файл, как в PDF
        try {
            Path svg = Paths.get("C:\\Users\\alikt\\Downloads\\product-filter\\src\\main\\resources\\elcos_logo.svg");
            return svgToPngBytes(svg, 500f, null);
        } catch (Exception ignore) {
        }

        return null;
    }

    private static List<String> buildFooterLines(ProposalParams p) {
        List<String> lines = new ArrayList<>();

        boolean vat = Boolean.TRUE.equals(p.getVatIncluded());
        if (vat) {
            lines.add("Цена с учетом НДС - 12%.");
        } else {
            lines.add("Цена указана без НДС.");
        }

        Integer pre = nzInt(p.getPrepaymentPercent());
        Integer bef = nzInt(p.getBeforeShipmentPercent());
        Integer post = nzInt(p.getPostPaymentPercent());
        lines.add(String.format(
                "Порядок оплаты: %d%% предоплата, %d%% оплата перед отгрузкой, %d%% постоплата.",
                pre, bef, post
        ));

        if (!isBlank(p.getPaymentNote())) {
            lines.add("Условия оплаты (примечание): " + p.getPaymentNote().trim());
        }

        int prodDays = nzInt(p.getProductionDays());
        lines.add("Срок изготовления: " + prodDays + " рабочих дней.");

        if (!isBlank(p.getDeliveryTerms())) {
            lines.add("Условия поставки: " + p.getDeliveryTerms().trim());
        }

        if (!isBlank(p.getComponents())) {
            lines.add("Комплектующие: " + p.getComponents().trim());
        }

        int warranty = nzInt(p.getWarrantyMonths());
        if (warranty > 0) {
            lines.add("На поставленный товар действуют стандартные гарантийные условия производителя, " +
                    "которые составляют " + warranty + " месяцев с момента передачи товара Покупателю.");
        }

        BigDecimal rate = p.getUsdRate() != null ? p.getUsdRate() : BigDecimal.ZERO;
        lines.add("Настоящая цена действительна при цене за 1 $ = " +
                String.format(Locale.forLanguageTag("ru-RU"), "%,.2f", rate) +
                " тенге по курсу Нацбанка РК, при изменении курса +/- 3%, " +
                "данное коммерческое предложение является недействительным и подлежит пересчету.");

        if (!isBlank(p.getExtraConditions())) {
            lines.add("Дополнительные условия: " + p.getExtraConditions().trim());
        }

        int validDays = nzInt(p.getValidDays());
        if (validDays > 0) {
            lines.add("Коммерческое предложение действительно в течение " + validDays + " дней.");
        }

        return lines;
    }

    private static int nzInt(Integer v) {
        return v != null ? v : 0;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    // --------- Fonts loader: Inter Bold (Unicode) с фолбэком ---------
    private static final class Fonts {
        final com.lowagie.text.Font BOLD_WHITE_10;
        final com.lowagie.text.Font BOLD_BLACK_10;

        private Fonts(com.lowagie.text.Font bw10, com.lowagie.text.Font bb10) {
            this.BOLD_WHITE_10 = bw10;
            this.BOLD_BLACK_10 = bb10;
        }

        static Fonts load() {
            byte[] inter24Bold = res("/fonts/Inter_24pt-Bold.ttf");
            byte[] interBold = res("/fonts/Inter-Bold.ttf");
            byte[] interOtf = res("/fonts/Inter-Bold.otf");
            byte[] dejavuBold = res("/fonts/DejaVuSans-Bold.ttf");

            byte[] chosen = firstNonNull(inter24Bold, interBold, interOtf, dejavuBold);
            if (chosen == null) {
                throw new RuntimeException("Fonts init failed: добавь Inter_24pt-Bold.ttf или DejaVuSans-Bold.ttf в resources/fonts");
            }

            try {
                BaseFont bf = BaseFont.createFont(
                        "inter-bold.ttf",
                        BaseFont.IDENTITY_H,
                        BaseFont.EMBEDDED,
                        false,
                        chosen,
                        null
                );
                com.lowagie.text.Font BW10 = new com.lowagie.text.Font(bf, 10, com.lowagie.text.Font.NORMAL, Color.WHITE);
                com.lowagie.text.Font BB10 = new com.lowagie.text.Font(bf, 10, com.lowagie.text.Font.NORMAL, Color.BLACK);
                return new Fonts(BW10, BB10);
            } catch (Exception e) {
                throw new RuntimeException("Fonts init failed", e);
            }
        }

        private static byte[] res(String path) {
            try (InputStream is = ProposalController.class.getResourceAsStream(path)) {
                if (is == null) return null;
                return is.readAllBytes();
            } catch (IOException e) {
                return null;
            }
        }

        private static byte[] firstNonNull(byte[]... arr) {
            for (byte[] a : arr) if (a != null) return a;
            return null;
        }
    }
}
