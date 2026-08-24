---
name: static-analysis
description: Run Spotless Google Java Style formatting checks, apply auto-fixes, and execute Maven compiler static analysis.
---

# Static Analysis & Formatting Skill

Use this skill to inspect and enforce code quality, Google Java Style formatting, and compiler linting.

## Execution Commands

### 1. Check Spotless Formatting
Verify that all Java source files adhere to Google Java Style:
```bash
mvn spotless:check
```

### 2. Auto-Format Code with Spotless
Automatically reformat all Java files violating style conventions:
```bash
mvn spotless:apply
```

### 3. Strict Compiler Static Analysis
Compile all sources and tests with full linter warnings enabled (`-Xlint:all`):
```bash
mvn clean test-compile
```

## Rules & Standards
- Never commit code that fails `mvn spotless:check`.
- Treat compiler warnings as errors where possible, resolving unchecked casts or deprecated usage.
