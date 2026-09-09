package com.example.ollama;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class DebugProperties implements CommandLineRunner {
	public String openApiKey;

	public DebugProperties(@Value("${spring.ai.openai.api-key}") String openApiKey) {
		this.openApiKey = openApiKey;
	}

	@Override
	public void run(String... args) throws Exception {
		// log.info("openApiKey: {}", openApiKey);
	}
}
