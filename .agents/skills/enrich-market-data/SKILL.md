---
name: enrich-market-data
description: Run market valuation scrapers (Point130, TCDB, SportsCardsPro), update market-data-cache.json, and apply IQR outlier filtering. Trigger when updating sports card pricing or sales comps.
---

# Market Data Enrichment Skill

Use this skill to fetch, scrape, filter, and cache real-world sports card market valuations and census data for `card-collectionJava`.

## 1. Execution Commands

### A. Point130 Completed Sales Comps
Fetches recent verified transaction data from 130point:
```bash
mvn exec:java@enrich-market-data
```

### B. SportsCardsPro Pricing & Tier Enrichment
Fetches grade-based pricing (Ungraded, PSA 8, PSA 9, PSA 10):
```bash
mvn exec:java@enrich-sportscardspro
```

### C. TCDB Checklist Verification
Cross-references checklist numbers, print runs, and parallel variations:
```bash
mvn exec:java@enrich-tcdb
```

---

## 2. Invariants & Data Safeguards

1. **IQR Outlier Filtering**:
   - Outliers are filtered using $1.5 \times \text{IQR}$ over trimmed transaction medians to prevent misattributed reprints or altered slab sales from skewing valuations.
2. **Context Protection**:
   - Never load `content/json/market-data-cache.json` (~197 KB) into prompt context in full.
   - Use `grep_search` with specific cert numbers or card IDs to inspect updates.
3. **Privacy Invariant**:
   - Private acquisition costs from internal ledgers must never be exposed in public Schema.org output or AI manifests (`llms.txt`).
4. **Subagent Offloading**:
   - Offload large batch scraping runs to an isolated background task or subagent so network wait times and raw HTTP logs do not consume main chat context.
