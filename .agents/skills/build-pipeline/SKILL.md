---
name: build-pipeline
description: Execute local development or full production pipeline for card-collectionJava, verify outputs, and diagnose build issues.
---

# Build Pipeline Skill

Use this skill to execute and verify the static site generation pipeline for `card-collectionJava`.

## Pipeline Execution Modes

### 1. Local Development Build (Fast, Offline)
Runs incremental site generation without external network calls to AWS, Firestore remote seeding, or IndexNow:
```bash
mvn clean compile exec:java -Dexec.mainClass="de.maulmann.LocalDevPipeline"
```

### 2. Full Production Build
Runs full generation including image optimization, minification, compression, Firestore injection, and sitemaps:
```bash
mvn clean compile exec:java -Dexec.mainClass="de.maulmann.SiteBuilderPipeline"
```

### 3. Run Test Suite
Runs the full JUnit 5 test suite:
```bash
mvn test
```

## Verification Checklist after Build:
1. Inspect `output/` directory for generated `.html` files.
2. Confirm companion `.html.gz` and `.html.br` files exist alongside generated HTML.
3. Confirm `output/generation-timestamps.properties` is updated.
4. If errors occur, inspect logs for Freemarker `TemplateException`, missing image paths, or JSON parsing issues.
