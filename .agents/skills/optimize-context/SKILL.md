---
name: optimize-context
description: Audit agent context budgets, detect rule bloat, migrate procedures into progressive skills, and adapt to newly released AI models and IDE optimizations. Trigger when auditing token usage or optimizing agent setup.
---

# Automated Context & Token Optimization Skill

Use this skill to audit repository rules, optimize prompt context budgets, and maintain peak token efficiency as new models, reasoning architectures, or IDE features are released.

## 1. Context Token Audit Procedure

When this skill is activated, run this diagnostic sequence:

### Step 1: Root Rule Budget Check
- Inspect [`AGENTS.md`](../../AGENTS.md):
  - **Invariant**: Target line count must remain $\le 60$ lines ($\le 500$ tokens).
  - If `AGENTS.md` has grown with new step-by-step procedures, commands, or documentation, extract them into a dedicated skill in `.agents/skills/<skill-name>/SKILL.md`.

### Step 2: Progressive Disclosure Verification
- Verify all skills in `.agents/skills/`:
  - Each skill must contain valid YAML frontmatter (`name` and `description`).
  - Descriptions must be concise (1–2 sentences, ~30–50 tokens) explaining *what* it does and *when* to trigger it.
  - Bulky documentation and manuals must reside in subdirectories (`references/`, `scripts/`) to avoid pre-loading.

### Step 3: Dataset Isolation Audit
- Ensure that no rule file directly embeds or references raw contents of large datasets:
  - `content/json/cards.json` (~770 KB)
  - `content/json/market-data-cache.json` (~197 KB)
  - Generated HTML files in `output/`

---

## 2. Adapting to New Models & IDE Capabilities

When newer LLM model generations (e.g., higher-capacity Flash, advanced Thinking/Reasoning models) or new IDE optimizations are released:

1. **Dynamic Model Tier Calibration**:
   - Update the mapping in [`.agents/rules/token-and-execution-efficiency.md`](../rules/token-and-execution-efficiency.md) to reflect new model capabilities:
     - **Tier 1 (Fast / Medium)**: Routine edits, Freemarker styling, single-class JUnit tests, scrapers.
     - **Tier 2 (Deep Reasoning / Pro)**: Concurrency, complex valuation math (IQR trimming), multi-system cloud sync.
2. **Subagent Delegation Scaling**:
   - When models support higher concurrency or isolated subagents, offload full Maven build passes (`mvn test`, `mvn exec:java@local`) and scraper loops to background subagents, having them return only a structured **Executive Memo** to the main chat.
3. **Automated Ingestion of `/learn`**:
   - When the user runs `/learn` to teach new patterns:
     - If it is a step-by-step procedure $\rightarrow$ route to a custom skill in `.agents/skills/`.
     - If it is an absolute invariant $\rightarrow$ add a concise 1-line rule in `AGENTS.md`.
