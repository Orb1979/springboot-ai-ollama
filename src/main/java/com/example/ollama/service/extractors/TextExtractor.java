package com.example.ollama.service.extractors;

import java.io.IOException;
import com.example.ollama.domain.FileType;

public interface TextExtractor {

	boolean supports(FileType fileType);

	String extract(byte[] fileBytes) throws IOException;
}