# Spring AI + Ollama

This project runs an LLM locally using Ollama.

Spring Boot > Spring AI > Ollama > local LLM

```
# Download a model
ollama pull llama3.2

# set the model in application.propterties
spring.ai.ollama.chat.model=llama3.2

# check if ollama is running, if this gives some result is running correctly
ollama ps

# if ollama is not running by default
ollama serve

# run app
 ./gradlew bootRun

# test with e.g
http://localhost:8080/test?message=Explain%20Spring%20Boot
http://localhost:8080/ai?message=Explain%20Spring%20Boot
```

```
─────────────────────────────────────────
 1. Prompt structure (what invoicePrompt IS)
─────────────────────────────────────────

invoicePrompt template
        ↓
Instructions ("extract these fields", rules)
        +
Invoice text (injected via %s)
        ↓
Sent as a single user message
        ↓
Spring AI ChatClient.prompt().user(...)
        ↓
LLM (Ollama)
        ↓
AI Response mapped to InvoiceResponse

```

```
POST /ai/invoices/analyze
            ↓
      MultipartFile
            ↓
     FileTypeDetector
            ↓
         FileType
     PDF / TEXT / IMAGE
            ↓
┌─────────────────────────┐
│ Find TextExtractor      │ --> Throw error if no extractor exist for FileType (e.g IMAGE)
└───────────┬────────────┘
       ┌────┴────┐
       ▼         ▼
 PDF strategy   TXT strategy
       └────┬────┘
            ▼
       Extract text
            ↓
        Spring AI
            ↓
     InvoiceResponse
            ↓
     InvoiceResponseValidator --> Throw error on any validation failure
            ↓      
        PostgreSQL

```


```
Unstructered data to InvoiceResponse structure/schema

The AI maps it with
- The natural-language prompt
- The InvoiceResponse structure/schema
- The model's language understanding

so e.g these all will be mapped to InvoiceResponse invoiceNumber
- Invoice number: 12345
- Inv. No. 12345
- Reference: 12345
- Factuurnummer: 12345

.entity(InvoiceResponse.class) >> "Spring AI, please ask the AI for a structured response that can be represented
 by this Java type, and deserialize the resulting structured data into this type."

                LLM
                  │
                  │ understands invoice
                  ▼
Unstructured text ──────► Structured JSON
                              │
                              │
                              ▼
                     Spring AI / Jackson
                              │
                              ▼
                     InvoiceResponse

```
