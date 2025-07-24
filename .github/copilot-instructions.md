# GitHub Copilot Instructions for Simple RAG Application

## Architecture Overview

This is a **Spring Boot 3.x RAG (Retrieval-Augmented Generation) system** with PostgreSQL+pgvector for vector storage and direct Ollama API integration. The architecture follows a clean layered approach with distinct service boundaries:

- **Document Processing Pipeline**: `DocumentService` → `TextExtractionService` → `DocumentChunkingService` → `EmbeddingProvider`
- **RAG Query Pipeline**: `RagQueryService` with vector similarity search → Ollama LLM integration
- **Data Layer**: JPA entities with PostgreSQL vector columns (`vector(768)` for nomic-embed-text embeddings)

## Key Patterns & Conventions

### Service Layer Strategy Pattern
The `EmbeddingProvider` interface allows switching between `DirectOllamaEmbeddingService` (production) and `MockEmbeddingService` (testing):
```java
@Service
@ConditionalOnProperty(name = "app.embedding.provider", havingValue = "ollama", matchIfMissing = true)
public class DirectOllamaEmbeddingService implements EmbeddingProvider
```

### PostgreSQL Vector Integration
- Embeddings stored as `vector(768)` columns using pgvector extension
- Vector similarity queries use `<=>` operator with configurable thresholds
- See `DocumentChunkRepository.findSimilarChunks()` for JPQL vector query patterns

### Configuration Architecture
- `AppProperties` class maps `app.*` properties with nested static classes (`File`, `Document`, `Vector`)
- Ollama integration configured via `app.ollama.*` properties (transitioning to Spring AI in future)
- Vector search thresholds: `app.vector.similarity-threshold=0.3`

## Critical Developer Workflows

### Local Development Setup
```bash
# Start PostgreSQL with pgvector
docker-compose up -d

# Run application (auto-creates schema via Liquibase)
mvn spring-boot:run

# Alternative: Use provided scripts
./start.sh  # or start.bat on Windows
```

### Database Migrations
- **Liquibase** manages schema with `src/main/resources/db/changelog/`
- Each migration in separate SQL files: `001-create-documents-table.sql`, etc.
- pgvector extension initialized in Docker init scripts

### Document Processing Flow
1. Upload → `DocumentController.uploadDocument()`
2. Text extraction → `TextExtractionService` (PDF/DOCX/TXT support)
3. Chunking → `DocumentChunkingService` (configurable size/overlap)
4. Embedding generation → `EmbeddingProvider.generateEmbedding()`
5. Vector storage → `DocumentChunk` entity with `vector(768)` column

### RAG Query Processing
- Query embedding → Similarity search → Context assembly → Ollama LLM call
- Threshold fallback: if no chunks found with configured threshold, searches without threshold
- Response format: JSON for AJAX, HTML for form submissions

## External Dependencies & Integration

### Ollama API Integration
- **Direct HTTP calls** (not Spring AI yet) via `RestTemplate`
- Embedding model: `nomic-embed-text` (768 dimensions)
- Chat model: `qwen2.5:7b`
- Base URL: `http://localhost:11434` (configurable)

### Frontend Patterns
- **Thymeleaf** templates with Bootstrap 5
- **Markdown rendering**: Client-side using `marked.js` library
- AJAX endpoints return JSON, form submissions return model+view
- File upload: Drag & drop with progress indication

### Testing Considerations
- Mock embedding service for tests without Ollama dependency
- Test configuration in `application-test.properties`
- Vector operations require PostgreSQL testcontainer or similar

## File Organization Logic
- **Entities**: Mirror database tables with JPA annotations and vector columns
- **Repositories**: Extend `JpaRepository` with custom JPQL for vector queries  
- **Services**: Business logic layer with clear single responsibilities
- **Controllers**: Separate REST API (`/api/*`) from web form endpoints
- **Config**: Centralized in `AppProperties` with type-safe nested classes

When implementing new features, follow the established service boundary pattern and consider the embedding/vector storage implications for RAG functionality.

## Git
- Commit messages should be clear, descriptive, and not exceed 72 characters.

## Others
- No summary documentation needed