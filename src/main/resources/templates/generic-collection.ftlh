<!DOCTYPE html>
<html lang="en">
<head>
    ${headHtml?no_esc}
</head>
<body>
${topNavHtml?no_esc}
<main class="detail-main">
    <div class="collection-table-wrapper"
         style="background: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.05);">
        ${pageContent?no_esc}
    </div>
</main>
${footerHtml?no_esc}
</body>
</html>