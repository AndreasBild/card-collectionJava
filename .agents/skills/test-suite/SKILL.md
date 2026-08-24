---
name: test-suite
description: Execute JUnit 5 unit, snapshot, and integration test suite with Java 26 preview features enabled.
---

# Test Suite Skill

Use this skill to execute and diagnose the full JUnit Jupiter test suite for `card-collectionJava`.

## Execution Commands

### 1. Run Complete Test Suite
```bash
mvn clean test
```

### 2. Run Specific Test Class
```bash
mvn test -Dtest=CardSchemaGeneratorTest
```

### 3. Run Pipeline Integration Tests
```bash
mvn test -Dtest=SiteBuilderPipelineTest,LocalDevPipelineTest,FileGeneratorTest
```

### 4. Run HTML Snapshot & Asset Link Validators
```bash
mvn test -Dtest=HtmlSnapshotTest,HtmlLinkAndAssetValidatorTest
```

## Diagnosis & Troubleshooting
- **Compiler/Preview Errors:** Ensure surefire plugin args include `--enable-preview` and `--enable-native-access=ALL-UNNAMED`.
- **Snapshot Mismatch:** If Freemarker templates were updated intentionally, verify the diff and update the golden master expectation in `HtmlSnapshotTest`.
- **Missing Resource Files:** Verify that mock JSON/template files under `src/test/resources/` or `src/main/resources/` are present and valid.
