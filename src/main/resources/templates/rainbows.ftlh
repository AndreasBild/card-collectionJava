<!DOCTYPE html>
<html lang="en">
<head>
    ${headHtml?no_esc}
    <#if jsonLd??>${jsonLd?no_esc}</#if>
</head>
<body>
{{CONSENT_BANNER}}
${topNavHtml?no_esc}
<main class="detail-main">
    <#if breadcrumbHtml??>${breadcrumbHtml?no_esc}</#if>
    <header class="detail-header">
        <h1>Parallel Rainbow Tracker & Set Checklists</h1>
        <p class="sub-title">Tracking the quest for complete parallel sets, PMG rainbows, and rare 90s inserts</p>
    </header>

    <!-- MASTER RAINBOW COMPLETION DASHBOARD -->
    <section class="rainbow-master-dashboard context-card full-width">
        <div class="master-dashboard-content">
            <div class="master-ring-box">
                <div class="master-progress-circle" aria-label="Overall Rainbow Completion Meter">
                    <svg class="progress-ring-svg" viewBox="0 0 120 120">
                        <circle class="progress-ring-bg" cx="60" cy="60" r="50" fill="none" stroke-width="10"/>
                        <circle class="progress-ring-bar" cx="60" cy="60" r="50" fill="none" stroke-width="10" stroke-dasharray="314.159" stroke-dashoffset="0"/>
                    </svg>
                    <div class="progress-ring-text">
                        <span class="progress-pct-number">100%</span>
                        <span class="progress-pct-label">TRACKED</span>
                    </div>
                </div>
            </div>
            <div class="master-stats-grid">
                <div class="master-stat-card">
                    <span class="stat-icon">🌈</span>
                    <div class="stat-info">
                        <span class="stat-value">${totalRainbowSets!0}</span>
                        <span class="stat-label">Active Rainbow Sets</span>
                    </div>
                </div>
                <div class="master-stat-card">
                    <span class="stat-icon">🃏</span>
                    <div class="stat-info">
                        <span class="stat-value">${totalRainbowCards!0}</span>
                        <span class="stat-label">Cards in Rainbows</span>
                    </div>
                </div>
                <div class="master-stat-card">
                    <span class="stat-icon">👑</span>
                    <div class="stat-info">
                        <span class="stat-value">${totalRainbow1of1!0}</span>
                        <span class="stat-label">1/1 Masterpieces</span>
                    </div>
                </div>
                <div class="master-stat-card highlight">
                    <span class="stat-icon">📖</span>
                    <div class="stat-info">
                        <a href="binder.html" class="master-binder-link" title="Open 3D 9-Pocket Binder View">
                            <span class="stat-value">3D Binder</span>
                            <span class="stat-label">Browse in 9-Pocket Mode &rarr;</span>
                        </a>
                    </div>
                </div>
            </div>
        </div>
    </section>

    <div class="rainbow-intro-box seo-box">
        <h2><span role="img" aria-label="rainbow">&#x1F308;</span> The Quest for the Rainbow</h2>
        <p>In modern and 90s sports card collecting, a <strong>"Rainbow"</strong> represents acquiring every single parallel color, serial-numbered variant, and 1/1 Masterpiece produced for a specific card set. Explore our current completion status across key release years below.</p>
    </div>

    <!-- RAINBOW SEARCH & QUICK FILTER -->
    <div class="rainbow-filter-bar">
        <div class="rainbow-search-wrapper">
            <span class="search-icon">🔍</span>
            <input type="text" id="rainbowSearchInput" class="rainbow-search-input" placeholder="Search rainbow sets by season, brand, card # or variant..." oninput="filterRainbowSets()" aria-label="Filter rainbow sets">
        </div>
        <div class="rainbow-filter-chips">
            <button type="button" class="filter-chip active" onclick="filterChip(this, 'all')">All Sets (${totalRainbowSets!0})</button>
            <button type="button" class="filter-chip" onclick="filterChip(this, '1/1')">👑 With 1/1</button>
            <button type="button" class="filter-chip" onclick="filterChip(this, '90s')">🏀 90s Era</button>
            <button type="button" class="filter-chip" onclick="filterChip(this, 'modern')">✨ Modern Panini</button>
        </div>
    </div>

    <section class="rainbow-sets-container" id="rainbowSetsContainer">
        <#if rainbowSets?? && (rainbowSets?size > 0)>
            <#list rainbowSets as set>
                <div class="rainbow-set-card" data-season="${set.season!''}" data-brand="${set.brand!''}" data-theme="${set.theme!''}" data-name="${set.name!''}">
                    <div class="rainbow-set-header">
                        <div>
                            <h3 class="rainbow-set-title">${set.name}</h3>
                            <span class="rainbow-set-subtitle">Season: ${set.season} &bull; Manufacturer: ${set.company} &bull; Brand: ${set.brand} &bull; Set: ${set.theme} &bull; Card #: ${set.number}</span>
                        </div>
                        <div class="rainbow-badge-box">
                            <span class="rainbow-completion-badge">${set.acquiredCount} / ${set.totalCount} Collected (${set.percentage}%)</span>
                        </div>
                    </div>
                    
                    <div class="rainbow-progress-track">
                        <div class="rainbow-progress-bar" style="width: ${set.percentage}%;"></div>
                    </div>

                    <!-- PARALLEL CHECKLIST PILLS -->
                    <div class="rainbow-checklist-pills">
                        <#list set.cards as card>
                            <span class="checklist-pill acquired" title="${card.title} - ${card.serial!''}">
                                <span class="pill-check">&#10003;</span> ${card.variant} <#if card.serial?? && card.serial != ''><span class="pill-serial">${card.serial}</span></#if>
                            </span>
                        </#list>
                    </div>

                    <div class="rainbow-items-grid">
                        <#list set.cards as card>
                            <#if card.acquired>
                                <div class="rainbow-item acquired ${card.orientationClass!'is-portrait'}">
                                    <a href="${card.url}?from=rainbows" title="View details for ${card.title} (${card.variant})" class="rainbow-card-link">
                                        <figure class="rainbow-card-figure">
                                            <picture class="rainbow-card-media">
                                                <source type="image/avif"
                                                        srcset="${(card.imgBase)???then(card.imgBase + '-200w.avif 200w, ' + card.imgBase + '-400w.avif 400w, ' + card.imgBase + '-600w.avif 600w', card.imgPath)}"
                                                        sizes="(max-width: 480px) 140px, (max-width: 900px) 160px, 195px">
                                                <img src="${(card.imgBase)???then(card.imgBase + '-200w.avif', card.imgPath)}"
                                                     srcset="${(card.imgBase)???then(card.imgBase + '-200w.avif 200w, ' + card.imgBase + '-400w.avif 400w', card.imgPath)}"
                                                     sizes="(max-width: 480px) 140px, (max-width: 900px) 160px, 195px"
                                                     alt="${card.title} - ${card.variant}"
                                                     width="${(card.isLandscape!false)?then('195', '138')}"
                                                     height="${(card.isLandscape!false)?then('138', '195')}"
                                                     loading="lazy" decoding="async" class="rainbow-card-img">
                                            </picture>
                                            <figcaption>${card.title} - ${card.variant}</figcaption>
                                        </figure>
                                        <div class="rainbow-hover-overlay">
                                            <span class="rainbow-card-title">${card.title}</span>
                                            <span class="rainbow-item-variant">${card.variant}</span>
                                            <span class="rainbow-item-serial">${card.serial}</span>
                                            <span class="rainbow-status-badge acquired">ACQUIRED</span>
                                        </div>
                                    </a>
                                </div>
                            </#if>
                        </#list>
                    </div>
                </div>
            </#list>
        <#else>
            <p>Loading Rainbow Tracker data...</p>
        </#if>
    </section>

    <section class="faq-section seo-box" style="margin-top: 2rem;">
        <h2>Frequently Asked Questions</h2>
        <details class="faq-details">
            <summary class="faq-summary">What is a sports card Parallel Rainbow?</summary>
            <p class="faq-answer">A "Rainbow" in sports card collecting represents acquiring every single parallel color, serial-numbered variant, and 1/1 Masterpiece produced for a specific card set during a release season.</p>
        </details>
        <details class="faq-details">
            <summary class="faq-summary">How is parallel rainbow completion progress calculated?</summary>
            <p class="faq-answer">Completion progress is calculated by dividing the count of acquired parallel cards by the total known manufactured parallel variants in that specific card set.</p>
        </details>
        <details class="faq-details">
            <summary class="faq-summary">What happens if a parallel card variant is missing from the rainbow?</summary>
            <p class="faq-answer">Unacquired parallel variants are marked as "SEEKING" and actively tracked on our <a href="Wantlist.html" title="Juwan Howard Wantlist">Juwan Howard Wantlist</a> for future acquisition.</p>
        </details>
    </section>
</main>
${footerHtml?no_esc}

<script>
    let activeFilter = 'all';

    function filterChip(btn, type) {
        document.querySelectorAll('.filter-chip').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        activeFilter = type;
        filterRainbowSets();
    }

    function filterRainbowSets() {
        const query = (document.getElementById('rainbowSearchInput').value || '').toLowerCase().trim();
        const cards = document.querySelectorAll('.rainbow-set-card');

        cards.forEach(card => {
            const season = (card.dataset.season || '').toLowerCase();
            const brand = (card.dataset.brand || '').toLowerCase();
            const name = (card.dataset.name || '').toLowerCase();
            const theme = (card.dataset.theme || '').toLowerCase();
            const textContent = card.textContent.toLowerCase();

            const matchesText = !query || name.includes(query) || season.includes(query) || brand.includes(query) || theme.includes(query) || textContent.includes(query);

            let matchesChip = true;
            if (activeFilter === '1/1') {
                matchesChip = textContent.includes('1/1') || textContent.includes('1 of 1') || textContent.includes('masterpiece');
            } else if (activeFilter === '90s') {
                matchesChip = season.startsWith('199') || season.includes('1994') || season.includes('1995') || season.includes('1996') || season.includes('1997') || season.includes('1998') || season.includes('1999');
            } else if (activeFilter === 'modern') {
                matchesChip = season.startsWith('20') || brand.includes('flawless') || brand.includes('panini') || brand.includes('select') || brand.includes('prizm') || brand.includes('immaculate');
            }

            card.style.display = (matchesText && matchesChip) ? 'block' : 'none';
        });
    }
</script>
</body>
</html>
