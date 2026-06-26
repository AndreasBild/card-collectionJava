<!DOCTYPE html>
<html lang="en">
<head>
    ${headHtml?no_esc}
    <#if jsonLd??>${jsonLd?no_esc}</#if>
</head>
<body>
${topNavHtml?no_esc}
<main class="detail-main">
    <header class="detail-header">
        <h1>HTML Sitemap</h1>
        <p class="sub-title">An organized overview of all pages in the Juwan Howard Private Collection</p>
    </header>

    <div class="sitemap-section">
        <h2>Core Pages & Collections</h2>
        <div class="sitemap-grid">
            <ul class="sitemap-list">
                <#list coreLinks as link>
                    <li><a href="${link.url}" class="plain" title="${link.text}">${link.text}</a></li>
                </#list>
            </ul>
        </div>
    </div>

    <div class="sitemap-section">
        <h2>Card Detail Pages by Season</h2>
        <div class="sitemap-cards-container">
            <#list cardSeasons as season>
                <div class="season-group">
                    <h3>${season.name}</h3>
                    <ul class="sitemap-list sitemap-card-list">
                        <#list season.links as link>
                            <li>
                                <a href="${link.url}" class="plain sitemap-card-link" title="${link.text}">${link.player!"Card"}</a>
                                <div class="sitemap-card-details">
                                    <#if link.company?? && link.company != ""><span class="sitemap-card-spec">${link.company}</span></#if>
                                    <#if link.brand?? && link.brand != "" && link.brand != link.company!><span class="sitemap-card-spec">${link.brand}</span></#if>
                                    <#if link.theme?? && link.theme != ""><span class="sitemap-card-spec">${link.theme}</span></#if>
                                    <#if link.variant?? && link.variant != ""><span class="sitemap-card-spec">${link.variant}</span></#if>
                                    <#if link.number?? && link.number != ""><span class="sitemap-card-spec">#${link.number}</span></#if>
                                </div>
                            </li>
                        </#list>
                    </ul>
                </div>
            </#list>
        </div>
    </div>
</main>
${footerHtml?no_esc}
</body>
</html>
