package tz.ppra.ppraAMS.importer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class DocumentTextExtractor {

    public String extract(String ext, byte[] data) throws IOException {
        switch (ext) {
            case "pdf": {
                try (PDDocument doc = Loader.loadPDF(data)) {
                    PDFTextStripper s = new PDFTextStripper();
                    s.setSortByPosition(true);
                    return s.getText(doc);
                }
            }
            case "docx": {
                try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(data));
                     XWPFWordExtractor x = new XWPFWordExtractor(doc)) {
                    return x.getText();
                }
            }
            case "txt":
            case "csv":
                return new String(data, StandardCharsets.UTF_8);
            default:
                throw new IllegalArgumentException("Unsupported file type: ." + ext);
        }
    }
}
