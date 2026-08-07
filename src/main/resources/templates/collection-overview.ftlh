<!DOCTYPE html>
<html lang="en">
<head>
    ${headHtml?no_esc}
    <#if jsonLd??>${jsonLd?no_esc}</#if>
</head>
<body id="top">
${topNavHtml?no_esc}
<main class="detail-main">
    <#if breadcrumbHtml??>${breadcrumbHtml?no_esc}</#if>
    <header class="detail-header">
        <h1>${pageTitle}</h1>
        <p class="sub-title">${subTitle}</p>
    </header>

    <section class="analytics-dashboard context-card full-width">
        <div class="dashboard-header">
            <h2>&#x1F4C8; Vault Analytics & Collection Metrics</h2>
            <span class="vault-count-badge">Total Cards: <strong>${(stats.totalCards)!"1,000+"}</strong></span>
        </div>
        <div class="metrics-grid">
            <div class="metric-card">
                <h4>&#x1F48E; Rarity Tier Breakdown</h4>
                <div class="meter-group">
                    <div class="meter-label"><span>1/1 Masterpieces</span> <strong>${(stats.count1of1)!0}</strong></div>
                    <div class="meter-bar-track"><div class="meter-bar" style="width: ${(stats.pct1of1)!0}%;"></div></div>
                </div>
                <div class="meter-group">
                    <div class="meter-label"><span>Ultra SP (&le; 10)</span> <strong>${(stats.countUltraSp)!0}</strong></div>
                    <div class="meter-bar-track"><div class="meter-bar gold" style="width: ${(stats.pctUltraSp)!0}%;"></div></div>
                </div>
                <div class="meter-group">
                    <div class="meter-label"><span>Serialized (&le; 100)</span> <strong>${(stats.countSerialized)!0}</strong></div>
                    <div class="meter-bar-track"><div class="meter-bar blue" style="width: ${(stats.pctSerialized)!0}%;"></div></div>
                </div>
            </div>

            <div class="metric-card">
                <h4>&#x270D;&#xFE0F; Attributes & Special Types</h4>
                <div class="meter-group">
                    <div class="meter-label"><span>Autographs</span> <strong>${(stats.countAutographs)!0}</strong></div>
                    <div class="meter-bar-track"><div class="meter-bar green" style="width: ${(stats.pctAutographs)!0}%;"></div></div>
                </div>
                <div class="meter-group">
                    <div class="meter-label"><span>Game-Used Patch / Mem</span> <strong>${(stats.countPatches)!0}</strong></div>
                    <div class="meter-bar-track"><div class="meter-bar purple" style="width: ${(stats.pctPatches)!0}%;"></div></div>
                </div>
                <div class="meter-group">
                    <div class="meter-label"><span>Rookie Cards (RC)</span> <strong>${(stats.countRookies)!0}</strong></div>
                    <div class="meter-bar-track"><div class="meter-bar orange" style="width: ${(stats.pctRookies)!0}%;"></div></div>
                </div>
            </div>

            <div class="metric-card">
                <h4>&#x1F3C5; Grading Census</h4>
                <div class="meter-group">
                    <div class="meter-label"><span>Gem Mint (PSA 10 / BGS 9.5)</span> <strong>${(stats.countGemMint)!0}</strong></div>
                    <div class="meter-bar-track"><div class="meter-bar emerald" style="width: ${(stats.pctGemMint)!0}%;"></div></div>
                </div>
                <div class="meter-group">
                    <div class="meter-label"><span>Graded Total (PSA, BGS)</span> <strong>${(stats.countGradedTotal)!0}</strong></div>
                    <div class="meter-bar-track"><div class="meter-bar cyan" style="width: ${(stats.pctGradedTotal)!0}%;"></div></div>
                </div>
            </div>
        </div>
    </section>

    <#if seasons?has_content>
        <div class="view-mode-bar">
            <span class="view-label">&#x1F441;&#xFE0F; Layout View:</span>
            <button id="viewGridBtn" class="view-btn active" onclick="setCollectionLayout('grid')">&#x2638; Table / Grid</button>
            <button id="viewBinderBtn" class="view-btn" onclick="setCollectionLayout('binder')">&#x1F4D2; 9-Pocket Binder</button>
        </div>

        <div class="collection-controls filter-container">
            <strong class="filter-label">Filter Collection:</strong>
            <select id="seasonFilter" class="filter-select">
                <option value="all">All Seasons</option>
                <#list seasons as season>
                    <option value="${season.id}">${season.name}</option>
                </#list>
            </select>
            <input type="text" id="textSearch" class="filter-input" list="hobby-terms"
                   placeholder="Search PMG, Auto or Print Run <25, =1...">
            <datalist id="hobby-terms">
                <option value="= 1">
                <option value="<= 10">
                <option value="<= 25">
                <option value="<= 100">
                <option value="Precious Metal Gems">
                <option value="Refractor">
                <option value="Autograph">
                <option value="Legacy Collection">
                <option value="Ruby">
                <option value="Game Used / Patch">
            </datalist>
        </div>

        <nav class="season-jump-bar" aria-label="Season Quick Navigation">
            <span class="season-jump-title">&#9889; Jump:</span>
            <#list seasons as season>
                <a href="#${season.id}" class="season-jump-pill">${season.name}</a>
            </#list>
        </nav>

    <#list seasons as season>
        <div class="season-table-wrapper" id="${season.id}" data-season="${season.id}">
            <div class="season-header">
                <h2 class="season-title">${season.name}</h2>
                <a href="#top" class="back-to-top-btn" title="Scroll back to top of the collection"
                   aria-label="Back to top of page">&#8679; Top</a>
            </div>
            <p class="season-count">
                Cards this season: <span class="visible-counter">${season.seasonCount}</span>
                / ${season.cumulativeTotal}
            </p>
            <div class="table-responsive card-container-box">
                ${season.content?no_esc}
            </div>
        </div>
    </#list>

        <script>
            document.getElementById('seasonFilter').addEventListener('change', () => {
                applyFilters();
                const seasonVal = document.getElementById('seasonFilter').value;
                if (seasonVal !== 'all') {
                    scrollToSeason(seasonVal, false);
                }
            });
            document.getElementById('textSearch').addEventListener('input', applyFilters);

            function scrollToSeason(seasonId, updateFilter = true) {
                if (!seasonId) return;
                const target = document.getElementById(seasonId);
                const filterSelect = document.getElementById('seasonFilter');

                if (updateFilter && filterSelect) {
                    if (filterSelect.value !== 'all' && filterSelect.value !== seasonId) {
                        const options = Array.from(filterSelect.options);
                        const match = options.find(o => o.value === seasonId);
                        if (match) {
                            filterSelect.value = seasonId;
                        } else {
                            filterSelect.value = 'all';
                        }
                        applyFilters();
                    }
                }

                // Update active pill state in jump bar
                document.querySelectorAll('.season-jump-pill').forEach(pill => {
                    const href = pill.getAttribute('href');
                    if (href === '#' + seasonId) {
                        pill.classList.add('active');
                    } else {
                        pill.classList.remove('active');
                    }
                });

                const targetEl = document.getElementById(seasonId);
                if (targetEl && targetEl.style.display !== 'none') {
                    setTimeout(() => {
                        targetEl.scrollIntoView({ behavior: 'smooth', block: 'start' });
                    }, 50);
                }
            }

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
                                if (!cellText || cellText.trim() === '-' || cellText.trim() === '—' || cellText.trim() === '') return NaN;
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
                                const terms = searchVal.split(/\s+/).filter(t => t.length > 0);
                                isMatch = terms.every(term => {
                                    if (term === '1/1' || term === 'masterpiece') {
                                        return text.includes('1/1') || text.includes('1 / 1') || text.includes('masterpiece');
                                    } else if (term === 'pmg') {
                                        return text.includes('pmg') || text.includes('precious metal gems');
                                    } else if (term === 'auto' || term === 'autograph') {
                                        return text.includes('auto') || text.includes('sign');
                                    } else if (term === 'patch' || term === 'jersey') {
                                        return text.includes('patch') || text.includes('jersey') || text.includes('game used');
                                    }
                                    return text.includes(term);
                                });
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
                // Attach click handlers to season jump pills
                document.querySelectorAll('.season-jump-pill').forEach(pill => {
                    pill.addEventListener('click', (e) => {
                        e.preventDefault();
                        const href = pill.getAttribute('href');
                        if (href && href.startsWith('#')) {
                            const seasonId = href.substring(1);
                            history.pushState(null, '', href);
                            scrollToSeason(seasonId, true);
                        }
                    });
                });

                window.addEventListener('hashchange', () => {
                    const hash = window.location.hash;
                    if (hash && hash.length > 1) {
                        scrollToSeason(hash.substring(1), true);
                    }
                });

                // --- Scroll Restoration Logic or Fragment Jump ---
                const savedCardId = sessionStorage.getItem('lastClickedCard');
                if (savedCardId) {
                    const targetRow = document.getElementById(savedCardId);
                    if (targetRow) {
                        setTimeout(() => {
                            targetRow.scrollIntoView({ behavior: 'smooth', block: 'center' });
                            targetRow.style.backgroundColor = '#ffffcc';
                            setTimeout(() => {
                                targetRow.style.backgroundColor = '';
                            }, 2000);
                        }, 100);
                    }
                    sessionStorage.removeItem('lastClickedCard');
                } else {
                    const hash = window.location.hash;
                    if (hash && hash.length > 1) {
                        scrollToSeason(hash.substring(1), true);
                    }
                }

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

                    // Capture click for scroll restoration
                    table.addEventListener('click', (e) => {
                        const row = e.target.closest('tr');
                        if (row && row.id) {
                            sessionStorage.setItem('lastClickedCard', row.id);
                        }
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