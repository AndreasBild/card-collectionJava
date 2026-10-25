# Git Workflow & 6-Stage Automated Lifecycle Rule

## Rule: Strict Branch Isolation & Automatic PR Lifecycle

All development on `card-collectionJava` must adhere to the 6-stage lifecycle protocol:

```mermaid
flowchart LR
    S1[1. Analysis] --> S2[2. Architecture]
    S2 --> S3[3. Branch Isolation]
    S3 --> S4[4. TDD Code Gen]
    S4 --> S5[5. Quality Gate]
    S5 --> S6[6. Auto PR & Jules Review]
```

### 1. Branch Isolation (`main` Protection)
- **Invariant:** Antigravity must **NEVER** edit files or commit directly to `main`.
- Always initialize or check out a dedicated feature, fix, or chore branch (`feature/*`, `fix/*`, `chore/*`).

### 2. Local Quality Gate Verification (Pre-Commit)
Before staging or committing any code, execute the local quality gate:
```bash
# 1. Spotless formatting
mvn spotless:check

# 2. JUnit 5 test suite with Java 27 preview features
mvn clean test

# 3. Local pipeline dry-run
mvn exec:java@local
```

### 3. Stage & Semantic Commit
- Stage modified source, test, resource, and agent configuration files.
- Commit using semantic commit messages adhering to Conventional Commits:
  - `feat(...)`: New pipeline features, generators, or template enhancements.
  - `fix(...)`: Bug fixes in generators, schema, image conversion, or compression.
  - `perf(...)`: Core Web Vitals, compression, or execution speed optimizations.
  - `chore(...)`: Agent governance, CI workflows, dependency upgrades.
  - `test(...)`: Unit, snapshot, or contract test additions.

### 4. Push, Autonomous PR & Automatic Merge
- Push the branch to `origin`:
  ```bash
  git push -u origin <branch-name>
  ```
- Automatically open a Pull Request against `main` via `gh pr create` with the `.github/pull_request_template.md` checklist:
  ```bash
  gh pr create --base main --head $(git branch --show-current) --fill
  ```
- Automatically squash-merge the Pull Request into `main` and delete the remote branch:
  ```bash
  gh pr merge --squash --delete-branch
  ```
- Switch back to `main` locally and pull the latest merged state:
  ```bash
  git checkout main && git pull --rebase origin main
  ```
- **Mandatory Completion Invariant:** A task is **NOT** finished until the code has passed quality gates, changes are committed, pushed, PR created, merged via `gh pr merge`, and local `main` is updated.

