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
        <p class="sub-title">Tracking the hunt for complete parallel sets, PMG rainbows, and rare 90s inserts</p>
    </header>

    <div class="rainbow-intro-box seo-box">
        <h2><span role="img" aria-label="rainbow">&#x1F308;</span> The Quest for the Rainbow</h2>
        <p>In modern and 90s sports card collecting, a <strong>"Rainbow"</strong> represents acquiring every single parallel color, serial-numbered variant, and 1/1 Masterpiece produced for a specific card set. Explore our current completion status across key release years below.</p>
    </div>

    <section class="rainbow-sets-container">
        <#if rainbowSets?? && (rainbowSets?size > 0)>
            <#list rainbowSets as set>
                <div class="rainbow-set-card">
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

                    <div class="rainbow-items-grid">
                        <#list set.cards as card>
                            <#if card.acquired>
                                <div class="rainbow-item acquired">
                                    <a href="${card.url}?from=rainbows" title="${card.title}" class="rainbow-card-link">
                                        <figure class="rainbow-card-figure">
                                            <picture class="rainbow-card-media">
                                                <source type="image/avif" srcset="${card.imgPath}">
                                                <img src="${card.imgPath}" alt="${card.title} - ${card.variant}" loading="lazy" class="rainbow-card-img">
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
</main>
${footerHtml?no_esc}
</body>
</html>
