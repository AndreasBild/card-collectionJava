<!DOCTYPE html>
<html lang="en">
<head>
    ${headHtml?no_esc}
</head>
<body>
${topNavHtml?no_esc}
<main class="detail-main">
    <header class="detail-header">
        <h1>${pageTitle}</h1>
        <p class="sub-title">${subTitle}</p>
    </header>
    <#if seasons?has_content>
        <div class="collection-controls"
             style="margin: 20px 0; padding: 15px; background: #f4f8fc; border-radius: 8px; display: flex; gap: 15px; flex-wrap: wrap; align-items: center;">
            <strong style="color: #333;">Filter Collection:</strong>
            <select id="seasonFilter"
                    style="padding: 10px; border-radius: 4px; border: 1px solid #ccc; font-size: 16px; min-width: 200px;">
                <option value="all">All Seasons</option>
                <#list seasons as season>
                    <option value="${season.id}">Season ${season.name}</option>
                </#list>
            </select>
            <input type="text" id="textSearch" placeholder="Search for Brand, Variant, Number..."
                   style="padding: 10px; border-radius: 4px; border: 1px solid #ccc; font-size: 16px; flex-grow: 1; min-width: 250px;">
        </div>

    <#list seasons as season>
        <div class="season-table-wrapper" data-season="${season.id}">
            ${season.content?no_esc}
        </div><br>
    </#list>

        <script>
            document.getElementById('seasonFilter').addEventListener('change', applyFilters);
            document.getElementById('textSearch').addEventListener('keyup', applyFilters);

            function applyFilters() {
                const seasonVal = document.getElementById('seasonFilter').value;
                const searchVal = document.getElementById('textSearch').value.toLowerCase();

                const wrappers = document.querySelectorAll('.season-table-wrapper');

                wrappers.forEach(wrapper => {
                    const wrapperSeason = wrapper.getAttribute('data-season');
                    let hasVisibleRows = false;

                    if (seasonVal !== 'all' && wrapperSeason !== seasonVal) {
                        wrapper.style.display = 'none';
                        return;
                    }

                    const rows = wrapper.querySelectorAll('tr');
                    for (let i = 0; i < rows.length; i++) {
                        const row = rows[i];
                        if (row.querySelector('th')) continue;

                        const text = row.textContent.toLowerCase();
                        if (searchVal === '' || text.includes(searchVal)) {
                            row.style.display = '';
                            hasVisibleRows = true;
                        } else {
                            row.style.display = 'none';
                        }
                    }
                    wrapper.style.display = hasVisibleRows ? '' : 'none';
                });
            }
        </script>
    <#else>
        <p>No collection data found.</p>
    </#if>
</main>
${footerHtml?no_esc}
</body>
</html>