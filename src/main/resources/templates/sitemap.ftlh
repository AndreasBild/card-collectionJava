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
                    <ul class="sitemap-list">
                        <#list season.links as link>
                            <li><a href="${link.url}" class="plain" title="${link.text}">${link.text}</a></li>
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
