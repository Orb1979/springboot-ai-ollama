package com.example.ollama.service;

//import org.apache.pdfbox.util.filetypedetector.FileType;
import com.example.ollama.domain.FileType;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/*
	This checks magic bytes (file signatures), not extensions — reliable even if the filename is missing or wrong.
	If you end up needing this for many more formats, consider using Apache Tika's Tika.detect()
  The filetype is considered text when it's not a pdf or one of the know image formats
*/

@Log4j2
@Service
public class FileTypeDetector {

	public FileType detect(byte[] bytes) {

		if (isPdf(bytes)) {
			log.info("Detected file type: PDF");
			return FileType.PDF;
		}

		if (isImage(bytes)) {
			log.info("Detected file type: IMAGE");
			return FileType.IMAGE;
		}

		log.info("Detected file type: TEXT");
		return FileType.TEXT;
	}

	private boolean isPdf(byte[] bytes) {
		if (bytes.length < 5) {
			return false;
		}

		String header = new String(
				bytes,
				0,
				5,
				StandardCharsets.US_ASCII
		);

		return header.equals("%PDF-");
	}

	private boolean isImage(byte[] bytes) {
		return isPng(bytes)
				       || isJpeg(bytes)
				       || isGif(bytes)
				       || isBmp(bytes)
				       || isWebp(bytes);
	}

	private boolean isPng(byte[] bytes) {
		if (bytes.length < 8) {
			return false;
		}
		// 89 50 4E 47 0D 0A 1A 0A
		return (bytes[0] & 0xFF) == 0x89
				       && bytes[1] == 0x50
				       && bytes[2] == 0x4E
				       && bytes[3] == 0x47
				       && bytes[4] == 0x0D
				       && bytes[5] == 0x0A
				       && bytes[6] == 0x1A
				       && bytes[7] == 0x0A;
	}

	private boolean isJpeg(byte[] bytes) {
		if (bytes.length < 3) {
			return false;
		}
		// FF D8 FF
		return (bytes[0] & 0xFF) == 0xFF
				       && (bytes[1] & 0xFF) == 0xD8
				       && (bytes[2] & 0xFF) == 0xFF;
	}

	private boolean isGif(byte[] bytes) {
		if (bytes.length < 6) {
			return false;
		}
		String header = new String(bytes, 0, 6, StandardCharsets.US_ASCII);
		return header.equals("GIF87a") || header.equals("GIF89a");
	}

	private boolean isBmp(byte[] bytes) {
		if (bytes.length < 2) {
			return false;
		}
		return bytes[0] == 'B' && bytes[1] == 'M';
	}

	private boolean isWebp(byte[] bytes) {
		if (bytes.length < 12) {
			return false;
		}
		String riff = new String(bytes, 0, 4, StandardCharsets.US_ASCII);
		String webp = new String(bytes, 8, 4, StandardCharsets.US_ASCII);
		return riff.equals("RIFF") && webp.equals("WEBP");
	}
}