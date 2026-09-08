package com.example.ollama.service.extractors;

import com.example.ollama.domain.FileType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;

/** PDF bytes > Loader.loadPDF() > PDDocument > PDFTextStripper > String **/

@Service
public class PdfTextExtractor implements TextExtractor {

	@Override
	public boolean supports(FileType fileType) {
		return fileType == FileType.PDF;
	}

	@Override
	public String extract(byte[] fileBytes) throws IOException {

		try (PDDocument document = Loader.loadPDF(fileBytes)) {

			PDFTextStripper pdfStripper =
					new PDFTextStripper();

			return pdfStripper.getText(document);
		}
	}
}