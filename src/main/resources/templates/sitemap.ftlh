<!DOCTYPE html>
<html lang="en">
<head>
    ${headHtml?no_esc}
    <#if jsonLd??>${jsonLd?no_esc}</#if>
    <style>
        .sitemap-section {
            margin-bottom: 40px;
        }
        .sitemap-section h2 {
            border-bottom: 2px solid #3a7504;
            padding-bottom: 10px;
            margin-bottom: 20px;
            color: #333;
        }
        .sitemap-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
            gap: 20px;
        }
        .sitemap-list {
            list-style: none;
            padding: 0;
            margin: 0;
        }
        .sitemap-list li {
            margin-bottom: 8px;
        }
        .sitemap-list a {
            color: #3264af;
            text-decoration: none;
            transition: color 0.2s;
        }
        .sitemap-list a:hover {
            color: #3a7504;
            text-decoration: underline;
        }
        .sitemap-cards-container {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
            gap: 30px;
        }
        .season-group h3 {
            background: #f4f4f4;
            padding: 10px;
            border-radius: 4px;
            margin-top: 0;
        }
    </style>
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
