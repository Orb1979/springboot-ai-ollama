package com.example.ollama.dto;

public record ConversationRequest(String system, String question){

	public ConversationRequest(String message){
		this("", message);
	}
}

