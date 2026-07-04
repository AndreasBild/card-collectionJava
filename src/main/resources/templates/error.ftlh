<!DOCTYPE html>
<html lang="en">
<head>
    ${headHtml?no_esc}
</head>
<body>
{{CONSENT_BANNER}}
${topNavHtml?no_esc}
<main class="detail-main error-main">
    <#if breadcrumbHtml??>${breadcrumbHtml?no_esc}</#if>
    <h1 class="error-title">Error Page</h1>
    <h2 class="error-subtitle">Requested Card Not Found</h2>
    <p class="error-text">The card or collection you are looking for has been
        traded, lost in the mail, or doesn't exist.</p>
    <a href="${root}index.html" class="modern-button modern-button-wide" title="Return to Home">Return to Base (Home)</a>
</main>
${footerHtml?no_esc}
</body>
</html>