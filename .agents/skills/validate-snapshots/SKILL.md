---
name: validate-snapshots
description: Run HTML golden master snapshot tests, dead-link asset validators, and verify Freemarker template output integrity.
---

# Validate Snapshots & Template Integrity Skill

Use this skill when modifying Freemarker templates (`src/main/resources/templates/`), HTML minifiers, or asset linking logic to ensure no regression errors occur.

## Execution Steps

### 1. Run Snapshot & Link Validation Tests
```bash
mvn test -Dtest=HtmlSnapshotTest,HtmlLinkAndAssetValidatorTest
```

### 2. Full Test Suite Verification
```bash
mvn test
```

## Diagnosis & Troubleshooting
- **Snapshot Mismatch (`HtmlSnapshotTest`):**
  If intentional structural changes were made to HTML templates, inspect the diff between expected snapshot and generated HTML. Update the golden master test dataset accordingly.
- **Broken Link / Missing Asset (`HtmlLinkAndAssetValidatorTest`):**
  Check that all `<a href="...">`, `<link href="...">`, `<script src="...">`, and `<img src="...">` reference valid output files or active public assets.
