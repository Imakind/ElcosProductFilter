package com.example.productfilter.importer;

import com.example.productfilter.service.ExcelImportWithSmartParserService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;

import java.io.File;

@SpringBootApplication
@ComponentScan(basePackages = "com.example.productfilter")
public class ImportRunner {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("❌ Укажите путь к .xlsx файлу: java -jar productfilter.jar /path/to/file.xlsx");
            return;
        }

        File file = new File(args[0]);
        if (!file.exists() || !file.getName().endsWith(".xlsx")) {
            System.out.println("❌ Файл не найден или не .xlsx: " + args[0]);
            return;
        }

        ApplicationContext context = SpringApplication.run(ImportRunner.class);
        ExcelImportWithSmartParserService importer = context.getBean(ExcelImportWithSmartParserService.class);

        try {
            System.out.println("📥 Импорт начат: " + file.getName());
            importer.importFromExcel(file);
            System.out.println("✅ Импорт завершён успешно.");
        } catch (Exception e) {
            System.err.println("❌ Ошибка при импорте: " + e.getMessage());
            e.printStackTrace();
        }

        System.exit(0);
    }
}
