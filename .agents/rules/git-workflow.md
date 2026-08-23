# Git Workflow & Automated Pull Request Invariant

## Rule: Always Create PR at the End of Work

Whenever a feature, bugfix, refactoring, or optimization task is completed:

1. **Verify Code Health First:**
   - Ensure all unit and integration tests pass cleanly (`mvn clean test`).
   - Verify local artifact generation (`mvn exec:java@local`).

2. **Stage & Commit Changes:**
   - Stage all modified and newly created source/test/resource files.
   - Craft a clean, semantic commit message (e.g. `feat(seo): add dynamic OpenGraph social share previews and enriched llms.txt`).

3. **Push & Trigger Auto-PR:**
   - Push the isolated feature branch (`feature/*` or `fix/*`) to `origin`.
   - The push automatically triggers `.github/workflows/auto-pr.yml` on GitHub to create the Pull Request and trigger Jules CI validation.
   - Always provide the PR link to the user in the final response.

4. **Never Leave Completed Work Uncommitted:**
   - Do NOT stop and ask the user if they want a PR created; execute the commit, push, and PR creation workflow automatically as the final step of each task.
