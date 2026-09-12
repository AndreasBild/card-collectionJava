---
name: git-pr-workflow
description: Run the 6-stage git branching, quality gate verification, commit staging, and automated PR creation lifecycle. Trigger when committing code, opening a PR, or handing off to Jules.
---

# Git & PR Lifecycle Workflow Skill

Use this skill to execute the formal 6-stage development lifecycle, branch isolation, and automated Pull Request creation in `card-collectionJava`.

## 1. The 6-Stage Lifecycle Overview

1. **Analysis & Discovery**: Review requirements, inspect existing records (`CardData`, `CardJson`), confirm invariants.
2. **Architecture & Design**: Define data structures, CWV budget checks, and companion compression strategy.
3. **Branch Isolation**: Never commit on `main`. Create an isolated feature or chore branch:
   ```bash
   git checkout -b <type>/<short-description>
   ```
4. **TDD & Inner Loop**:
   - `mvn test-compile` (syntax/type check)
   - `mvn test -Dtest=TargetTest` (targeted single-class test)
5. **Quality Gate Verification (Outer Gate)**:
   - `mvn spotless:check` (or `mvn spotless:apply`)
   - `mvn clean test` (full JUnit 5 regression suite)
   - `mvn exec:java@local` (local static site generation dry-run)
6. **Automated PR & Review**:
   - Stage **only** modified files related to the task (never `git add .`, never touch unrelated edits in `cards.json` or `MissingImages.txt`).
   - Commit with a semantic commit message: `feat(...)`, `fix(...)`, `chore(...)`, `docs(...)`.
   - Push to `origin`:
     ```bash
     git push -u origin <branch-name>
     ```
   - Create the Pull Request via GitHub CLI:
     ```bash
     gh pr create --fill
     ```
   - Handoff to Jules (Cloud PR & Test Agent) for asynchronous CI analysis.
