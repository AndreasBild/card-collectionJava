<!DOCTYPE html>
<html lang="en">
<head>
    ${headHtml?no_esc}
</head>
<body id="top">
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
                    <option value="${season.id}">${season.name}</option>
                </#list>
            </select>
            <input type="text" id="textSearch" list="hobby-terms"
                   placeholder="Search PMG, Masterpiece, or type <25, =1..."
                   style="padding: 10px; border-radius: 4px; border: 1px solid #ccc; font-size: 16px; flex-grow: 1; min-width: 250px;">
            <datalist id="hobby-terms">
                <option value="< 10">
                <option value="<= 25">
                <option value="= 1">
                <option value="1/1 (Masterpiece)">
                <option value="Precious Metal Gems">
                <option value="Refractor">
                <option value="Autograph">
                <option value="Game Used / Patch">
            </datalist>
        </div>

    <#list seasons as season>
        <div class="season-table-wrapper" data-season="${season.id}" style="margin-bottom: 40px;">
            <div style="display: flex; justify-content: space-between; align-items: baseline; border-bottom: 2px solid #eaeaea; margin-bottom: 5px; padding-bottom: 5px;">
                <h2 style="margin: 0; color: #3264af;">${season.name}</h2>
                <a href="#top" class="back-to-top-btn" title="Scroll back to top of the collection"
                   aria-label="Back to top of page">&#8679; Top</a>
            </div>
            <p style="font-weight: bold; color: #555; margin-top: 5px; margin-bottom: 15px; font-size: 0.95em;">
                Cards this season: <span class="visible-counter">${season.seasonCount}</span>
                / ${season.cumulativeTotal}
            </p>
            <div style="background: #fff; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.05); overflow: hidden; overflow-x: auto;">
                ${season.content?no_esc}
            </div>
        </div>
    </#list>

        <script>
            document.getElementById('seasonFilter').addEventListener('change', applyFilters);
            document.getElementById('textSearch').addEventListener('input', applyFilters);

            function applyFilters() {
                const seasonVal = document.getElementById('seasonFilter').value;
                const searchValRaw = document.getElementById('textSearch').value.trim();
                const searchVal = searchValRaw.toLowerCase();

                const prRegex = /^(?:printrun\s*)?([<>=]+)\s*(\d+)$/;
                const prMatch = searchVal.match(prRegex);

                let prOperator = null;
                let prTarget = null;
                if (prMatch) {
                    prOperator = prMatch[1];
                    prTarget = parseInt(prMatch[2], 10);
                }

                document.querySelectorAll('.season-table-wrapper').forEach(wrapper => {
                    const wrapperSeason = wrapper.getAttribute('data-season');
                    let visibleRowsInSeason = 0;

                    if (seasonVal !== 'all' && wrapperSeason !== seasonVal) {
                        wrapper.style.display = 'none';
                        return;
                    }

                    const table = wrapper.querySelector('table');
                    let serialIdx = -1;
                    let prIdx = -1;
                    if (table) {
                        const headers = Array.from(table.querySelectorAll('th'));
                        headers.forEach((th, idx) => {
                            const thText = th.textContent.toLowerCase();
                            if (thText.includes('serial')) serialIdx = idx;
                            if (thText.includes('print')) prIdx = idx;
                        });
                    }

                    const rows = wrapper.querySelectorAll('tr');
                    for (let i = 0; i < rows.length; i++) {
                        const row = rows[i];
                        if (row.querySelector('th')) continue;

                        let isMatch = false;

                        if (prMatch) {
                            let cardPrintRun = NaN;

                            const extractPR = (cellText) => {
                                if (!cellText || cellText.trim() === '-' || cellText.trim() === '') return NaN;
                                if (cellText.includes('/')) {
                                    const parts = cellText.split('/');
                                    return parseInt(parts[1].replace(/\D/g, ''), 10);
                                }
                                return parseInt(cellText.replace(/\D/g, ''), 10);
                            };

                            let prVal1 = (serialIdx >= 0 && row.children[serialIdx]) ? extractPR(row.children[serialIdx].textContent) : NaN;
                            let prVal2 = (prIdx >= 0 && row.children[prIdx]) ? extractPR(row.children[prIdx].textContent) : NaN;

                            if (!isNaN(prVal1)) cardPrintRun = prVal1;
                            if (!isNaN(prVal2) && (isNaN(cardPrintRun) || prVal2 > cardPrintRun)) cardPrintRun = prVal2;

                            if (!isNaN(cardPrintRun) && cardPrintRun > 0) {
                                if (prOperator === '<' && cardPrintRun < prTarget) isMatch = true;
                                else if (prOperator === '<=' && cardPrintRun <= prTarget) isMatch = true;
                                else if (prOperator === '>' && cardPrintRun > prTarget) isMatch = true;
                                else if (prOperator === '>=' && cardPrintRun >= prTarget) isMatch = true;
                                else if ((prOperator === '=' || prOperator === '==') && cardPrintRun === prTarget) isMatch = true;
                            }
                        } else {
                            const text = row.textContent.toLowerCase();
                            if (searchVal === '') {
                                isMatch = true;
                            } else {
                                let searchTriggers = [searchVal];

                                if (searchVal.includes('1/1') || searchVal.includes('masterpiece') || searchVal.includes('1 of 1')) {
                                    searchTriggers = ['1/1', '1 / 1', '1 of 1', 'masterpiece'];
                                } else if (searchVal.includes('pmg') || searchVal.includes('precious metal gems')) {
                                    searchTriggers = ['pmg', 'precious metal gems'];
                                } else if (searchVal.includes('auto') || searchVal.includes('autograph')) {
                                    searchTriggers = ['auto', 'sign'];
                                } else if (searchVal.includes('game used') || searchVal.includes('patch')) {
                                    searchTriggers = ['game used', 'patch', 'jersey'];
                                }

                                isMatch = searchTriggers.some(trigger => text.includes(trigger));
                            }
                        }

                        if (isMatch) {
                            row.style.display = '';
                            visibleRowsInSeason++;
                        } else {
                            row.style.display = 'none';
                        }
                    }

                    wrapper.style.display = (visibleRowsInSeason > 0) ? '' : 'none';

                    const counterSpan = wrapper.querySelector('.visible-counter');
                    if (counterSpan) {
                        counterSpan.textContent = visibleRowsInSeason;
                        counterSpan.style.color = (searchVal !== '') ? '#e74c3c' : '#555';
                    }
                });
            }

            document.addEventListener('DOMContentLoaded', () => {
                document.querySelectorAll('.season-table-wrapper table').forEach(table => {
                    const headers = table.querySelectorAll('th');
                    headers.forEach((header, index) => {
                        header.title = 'Click to sort';
                        let icon = document.createElement('span');
                        icon.innerHTML = ' &#8693;';
                        icon.style.opacity = '0.3';
                        icon.style.fontSize = '0.85em';
                        icon.style.marginLeft = '6px';
                        header.appendChild(icon);

                        let sortAsc = true;

                        header.addEventListener('click', () => {
                            headers.forEach(h => {
                                const span = h.querySelector('span');
                                if (span) {
                                    span.innerHTML = ' &#8693;';
                                    span.style.opacity = '0.3';
                                }
                            });

                            icon.innerHTML = sortAsc ? ' &#9650;' : ' &#9660;';
                            icon.style.opacity = '1';

                            const tbody = table.querySelector('tbody') || table;
                            const rowArray = Array.from(table.querySelectorAll('tr'));
                            const headerRow = rowArray.shift();

                            rowArray.sort((a, b) => {
                                const aCell = a.children[index];
                                const bCell = b.children[index];
                                const aText = aCell ? aCell.textContent.trim() : '';
                                const bText = bCell ? bCell.textContent.trim() : '';
                                return aText.localeCompare(bText, undefined, {
                                    numeric: true,
                                    sensitivity: 'base'
                                }) * (sortAsc ? 1 : -1);
                            });

                            rowArray.forEach(row => tbody.appendChild(row));
                            sortAsc = !sortAsc;
                        });
                    });
                });
            });
        </script>
    <#else>
        <p>No collection data found.</p>
    </#if>
</main>
${footerHtml?no_esc}
</body>
</html>