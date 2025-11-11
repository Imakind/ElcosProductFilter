package com.example.productfilter.controller;

import com.example.productfilter.model.Product;
import com.example.productfilter.util.FileNames;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

@Controller
public class ProposalController {

    @GetMapping("/proposal/pdf")
    public void downloadProposalPdf(HttpServletResponse response, HttpSession session) throws IOException {
        // Имя файла: Elcos КП <Проект> <ДД.ММ.ГГГГ> <user>.pdf
        String projectName = Optional.ofNullable((String) session.getAttribute("projectName")).orElse("Проект");
        String user = FileNames.currentUser();
        String fileName = ensurePdfExt(FileNames.kpPdf(projectName, user));

        // Данные из сессии
        @SuppressWarnings("unchecked")
        Map<Integer, Integer> cart = (Map<Integer, Integer>) session.getAttribute("proposalCart");
        @SuppressWarnings("unchecked")
        List<Product> products = (List<Product>) session.getAttribute("proposalProducts");
        @SuppressWarnings("unchecked")
        Map<Integer, Double> coeffs = (Map<Integer, Double>) session.getAttribute("proposalCoefficients");

        if (cart == null || products == null || cart.isEmpty()) {
            response.sendRedirect("/cart");
            return;
        }
        if (coeffs == null) coeffs = new HashMap<>();

        // Генерация PDF в память
        byte[] pdf = buildPdf(products, cart, coeffs);

        // Отдаём как attachment, без inline-показа
        response.reset();
        response.setContentType("application/pdf");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Content-Disposition", contentDisposition(fileName)); // filename + filename*
        response.setContentLength(pdf.length);

        try (OutputStream out = response.getOutputStream()) {
            out.write(pdf);
        }
    }

    // ---------- helpers ----------

    private static String ensurePdfExt(String name) {
        if (name == null || name.isBlank()) return "file.pdf";
        return name.toLowerCase().endsWith(".pdf") ? name : name + ".pdf";
    }

    // RFC 5987 для кириллицы + безопасный ASCII-фоллбек
    private static String contentDisposition(String filenameUtf8) {
        String ascii = filenameUtf8.replace('"', '\'').replaceAll("[^A-Za-z0-9._-]", "_");
        String encoded = URLEncoder.encode(filenameUtf8, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }

    private static byte[] buildPdf(List<Product> products,
                                   Map<Integer, Integer> cart,
                                   Map<Integer, Double> coeffs) throws IOException {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document();
            PdfWriter.getInstance(document, baos);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
            Font headerFont = new Font(Font.HELVETICA, 11, Font.BOLD);
            Font cellFont   = new Font(Font.HELVETICA, 10);
            Font totalBold  = new Font(Font.HELVETICA, 10, Font.BOLD);

            document.add(new Paragraph("Коммерческое предложение", titleFont));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1.2f, 5.5f, 3f, 2.5f, 1.5f, 2.5f, 2.5f});

            addHeader(table, headerFont, "№","Наименование","Артикул","Бренд","Кол-во","Цена","Сумма");

            double totalSum = 0.0;
            int totalQty = 0;
            int idx = 1;

            for (Product p : products) {
                int pid = p.getProductId();
                int qty = cart.getOrDefault(pid, 0);
                if (qty <= 0) continue;

                double base = p.getPrice() != null ? p.getPrice() : 0.0;
                double k = coeffs.getOrDefault(pid, 1.0);
                double price = base * k;
                double sum = price * qty;

                totalSum += sum;
                totalQty += qty;

                table.addCell(cell(String.valueOf(idx++), cellFont, Element.ALIGN_CENTER));
                table.addCell(cell(nz(p.getName()), cellFont, Element.ALIGN_LEFT));
                table.addCell(cell(nz(p.getArticleCode()), cellFont, Element.ALIGN_LEFT));
                String brand = p.getBrand() != null ? nz(p.getBrand().getBrandName()) : "—";
                table.addCell(cell(brand, cellFont, Element.ALIGN_CENTER));
                table.addCell(cell(String.valueOf(qty), cellFont, Element.ALIGN_CENTER));
                table.addCell(cell(String.format("%,.1f", price), cellFont, Element.ALIGN_CENTER));
                table.addCell(cell(String.format("%,.1f", sum),   cellFont, Element.ALIGN_CENTER));
            }

            PdfPCell lbl = new PdfPCell(new Phrase("Итого:", totalBold));
            lbl.setColspan(4);
            lbl.setHorizontalAlignment(Element.ALIGN_RIGHT);
            lbl.setVerticalAlignment(Element.ALIGN_MIDDLE);
            lbl.setPadding(5f);
            table.addCell(lbl);

            table.addCell(cell(String.valueOf(totalQty), cellFont, Element.ALIGN_CENTER));
            table.addCell(cell("", cellFont, Element.ALIGN_CENTER));
            table.addCell(cell(String.format("%,.1f", totalSum), totalBold, Element.ALIGN_CENTER));

            document.add(table);
            document.close();
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IOException("Ошибка генерации PDF", e);
        }
    }

    private static void addHeader(PdfPTable t, Font f, String... hs) {
        for (String h : hs) {
            PdfPCell c = new PdfPCell(new Phrase(h, f));
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            c.setVerticalAlignment(Element.ALIGN_MIDDLE);
            c.setPadding(5f);
            t.addCell(c);
        }
    }

    private static PdfPCell cell(String text, Font font, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text != null ? text : "", font));
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(4f);
        return c;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
