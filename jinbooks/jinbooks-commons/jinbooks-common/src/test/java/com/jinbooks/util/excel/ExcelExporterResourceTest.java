package com.jinbooks.util.excel;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertEquals;

public class ExcelExporterResourceTest {
    @Test
    public void readsTemplateInsideJarAndLocalFile() throws Exception {
        Path directory = Files.createTempDirectory("jinbooks-template-");
        Path template = directory.resolve("template.xlsx");
        Path jar = directory.resolve("templates.jar");
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try {
            try (Workbook workbook = new XSSFWorkbook(); var stream = Files.newOutputStream(template)) {
                workbook.createSheet("Sheet1").createRow(0).createCell(0).setCellValue("${name}");
                workbook.write(stream);
            }
            try (JarOutputStream stream = new JarOutputStream(Files.newOutputStream(jar))) {
                stream.putNextEntry(new JarEntry("regression-template.xlsx"));
                Files.copy(template, stream);
                stream.closeEntry();
            }
            try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, previous)) {
                Thread.currentThread().setContextClassLoader(loader);
                for (String source : new String[]{template.toString(), "classpath:regression-template.xlsx"}) {
                    ExcelExporter.export(ExcelParams.<Map<String, String>>builder()
                            .templateFilePath(source).dataModel(Map.of("name", "QA导出"))
                            .outputDirectory(directory.toString()).outputFileName("result.xlsx").build());
                    try (InputStream stream = Files.newInputStream(directory.resolve("result.xlsx"));
                         Workbook workbook = WorkbookFactory.create(stream)) {
                        assertEquals("QA导出", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
                    }
                }
            }
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
            Files.deleteIfExists(directory.resolve("result.xlsx"));
            Files.deleteIfExists(template);
            Files.deleteIfExists(jar);
            Files.deleteIfExists(directory);
        }
    }
}
