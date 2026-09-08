package com.example.ollama.service.extractors;

import com.example.ollama.domain.FileType;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class TxtTextExtractor implements TextExtractor {

	@Override
	public boolean supports(FileType fileType) {
		return fileType == FileType.TEXT;
	}

	@Override
	public String extract(byte[] fileBytes) {
		return new String(fileBytes, StandardCharsets.UTF_8
		);
	}
}