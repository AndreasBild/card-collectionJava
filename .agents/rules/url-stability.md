# URL Stability Invariant (SEO-Critical)

Search rankings depend on **stable individual card URLs**. Once a card page URL has been published, it must never change.

## Rules
- **Never change** the URL, file name, slug, or canonical of any existing card page, including the appended hash value (the stable ID in the HTML file name).
- The hash is produced by [`CardData.generateStableId`](file:///src/main/java/de/maulmann/CardData.java): the first 4 bytes (8 hex chars) of the MD5 over the `relevantKeys` attributes (`Player`, `Team`, `Season`, `Company`, `Brand`, `Theme`, `Variant`, `Number`, `Serial`, `Print Run`, `Serial/Print Run`, `Grade`), in that order, with the exact `key:value|` formatting and trimming/first-name-comma rules.
- Do **not** modify, in any way that alters its output: the key list or order, normalization, separator, digest algorithm, or truncation length.
- Do **not** change URL construction, slug generation, canonical tags, sitemap `<loc>` entries, or internal link generation for existing cards.
- Do **not** rename generated HTML or image files (`name.avif`, `-200w/-400w/-600w/-900w`).
- Refactors, optimizations and data-field additions must not alter the hash input of any existing card. If a new attribute is added to `CardData`, it must **not** be added to `relevantKeys`.

## Verification (required before every PR touching generators, `CardData`, or card data)
- Snapshot/golden-master tests must stay green (`validate-snapshots` skill).
- The set of generated card page file names and sitemap URLs before and after the change must be identical (diff the lists).
- If a legitimate URL change is ever unavoidable, it requires explicit user approval plus 301 redirects for every old URL.
