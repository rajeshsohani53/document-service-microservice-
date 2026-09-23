# Document Service

A standalone Spring Boot **microservice** responsible for one job: turning an uploaded document into clean, retrieval-ready text **chunks**.

This is the ingestion entry point of a larger **Retrieval-Augmented Generation (RAG)** system, rebuilt from scratch in Java to demonstrate hands-on understanding of RAG, LLM integration, and microservices architecture.

---

## Role in the System

This service is **service #1** in a five-service RAG pipeline. Each service does exactly one job (single-responsibility principle) and runs as its own process, communicating over REST.

| Port | Service | Responsibility |
|------|--------------------------|-------------------------------------------------|
| 8080 | API Gateway / Orchestrator | Single entry point; coordinates the flow |
| **8081** | **Document Service** | **Raw document → clean chunks** ← *this service* |
| 8082 | Embedding Service | Text → vector |
| 8083 | Retrieval Service | Query vector → most relevant chunks |
| 8084 | LLM / Answer Service | Chunks + question → final answer |

The Document Service is **stateless** — it produces chunks and returns them. It does not store them; downstream services (via the orchestrator) consume the output.

---

## Tech Stack

- **Java 17**
- **Spring Boot 4.1.1** (Spring Web MVC + embedded Tomcat)
- **Maven** (build & dependency management)
- **LangChain4j 1.19.0** (document splitting) — versions managed via the LangChain4j **BOM**

---

## What It Does

1. Accepts a file upload over HTTP (`multipart/form-data`).
2. Reads the file content as UTF-8 text.
3. Splits the text into overlapping chunks using LangChain4j's recursive splitter
   (`DocumentSplitters.recursive(500, 50)` — max 500 chars per chunk, 50-char overlap).
4. Returns the chunks as JSON.

**Why chunk?** LLMs have context limits, embeddings are sharper on focused text, and retrieval is more precise when the unit of retrieval is a paragraph rather than a whole document. The 50-character overlap ensures ideas that sit on a chunk boundary survive intact in at least one chunk.

---

## API

### `POST /api/documents/upload`

Uploads a document and returns its chunks.

**Request** — `multipart/form-data` with a single field named `file`.

```bash
curl -X POST http://localhost:8081/api/documents/upload \
     -F "file=@notes.txt"
```

**Response** — `200 OK`

```json
{
  "filename": "notes.txt",
  "characters": 8556,
  "totalChunks": 24,
  "chunks": [
    "first chunk text ...",
    "second chunk text ..."
  ],
  "message": "File received successfully"
}
```

> **Note:** `totalChunks` is often higher than `characters / 500` because the recursive
> splitter cuts at the nearest natural boundary (paragraph → line → sentence → word)
> rather than padding every chunk to exactly 500 characters. **500 is a ceiling, not a target.**

---

## Running Locally

**Prerequisites:** JDK 17+, Maven.

```bash
# from the project root
./mvnw spring-boot:run
```

The service starts on **http://localhost:8081**. Look for:

```
Tomcat started on port(s): 8081 (http)
Started DocumentServiceApplication in X.XXX seconds
```

### Configuration

`src/main/resources/application.properties`:

```properties
server.port=8081
```

---

## Testing the Upload

A minimal `upload.html` (plain HTML + vanilla JavaScript `fetch`) is included for manual testing — open it in a browser, choose a `.txt` file, and click **Upload**.

**CORS:** browser uploads from a `file://` page are cross-origin, so the controller is annotated
`@CrossOrigin(origins = "*")` for local development. In production this is handled centrally
at the API Gateway rather than per service.

---

## Scope & Limitations

- **Plain-text input** (`.txt`) is supported. PDF/Word parsing (via LangChain4j document loaders
  or Apache PDFBox) is a planned extension.
- **Stateless by design** — chunks are returned, not persisted. Storage/embedding is the
  responsibility of downstream services.

---

## Project Structure

```
document-service/
├── src/main/java/com/raj/documentservice/
│   ├── DocumentServiceApplication.java     # @SpringBootApplication entry point
│   ├── controller/DocumentController.java  # HTTP layer — upload endpoint
│   └── service/ChunkingService.java        # logic layer — recursive splitting
├── src/main/resources/application.properties
├── src/main/resources/static/upload.html   # manual test frontend
└── pom.xml
```
