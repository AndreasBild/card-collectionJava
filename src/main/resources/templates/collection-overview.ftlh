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
            <div class="dashboard-header-badges">
                <#if (stats.formattedEstimatedValue)?? && (stats.totalEstimatedValue > 0)>
                    <span class="vault-valuation-badge" title="Estimated Total Collection Market Valuation">&#x1F4B0; Est. Portfolio: <strong>${stats.formattedEstimatedValue}</strong></span>
                </#if>
                <span class="vault-count-badge">Total Cards: <strong>${(stats.totalCards)!"1,000+"}</strong></span>
            </div>
        </div>
        <div class="metrics-grid">
            <div class="metric-card">
                <h3>&#x1F48E; Rarity Tier Breakdown</h3>
                <div class="meter-group clickable-metric" onclick="filterByMetric('=1')" title="Click to filter 1/1 Masterpieces">
                    <div class="meter-label"><span>1/1 Masterpieces</span> <strong>${(stats.count1of1)!0}</strong></div>
                    <div class="meter-bar-track"><div class="meter-bar" style="width: ${(stats.pct1of1)!0}%;"></div></div>
                </div>
                <div class="meter-group clickable-metric" onclick="filterByMetric('<=10')" title="Click to filter Ultra SP (&le; 10)">
                    <div class="meter-label"><span>Ultra SP (&le; 10)</span> <strong>${(stats.countUltraSp)!0}</strong></div>
                    <div class="meter-bar-track"><div class="meter-bar gold" style="width: ${(stats.pctUltraSp)!0}%;"></div></div>
                </div>
                <div class="meter-group clickable-metric" onclick="filterByMetric('<=100')" title="Click to filter Serialized (&le; 100)">
                    <div class="meter-label"><span>Serialized (&le; 100)</span> <strong>${(stats.countSerialized)!0}</strong></div>
                    <div class="meter-bar-track"><div class="meter-bar blue" style="width: ${(stats.pctSerialized)!0}%;"></div></div>
                </div>
            </div>

            <div class="metric-card">
                <h3>&#x270D;&#xFE0F; Attributes & Special Types</h3>
                <div class="meter-group clickable-metric" onclick="filterByMetric('auto')" title="Click to filter Autographs">
                    <div class="meter-label"><span>Autographs</span> <strong>${(stats.countAutographs)!0}</strong></div>
                    <div class="meter-bar-track"><div class="meter-bar green" style="width: ${(stats.pctAutographs)!0}%;"></div></div>
                </div>
                <div class="meter-group clickable-metric" onclick="filterByMetric('patch')" title="Click to filter Game-Used Patch / Mem">
                    <div class="meter-label"><span>Game-Used Patch / Mem</span> <strong>${(stats.countPatches)!0}</strong></div>
                    <div class="meter-bar-track"><div class="meter-bar purple" style="width: ${(stats.pctPatches)!0}%;"></div></div>
                </div>
                <div class="meter-group clickable-metric" onclick="filterByMetric('rookie')" title="Click to filter Rookie Cards">
                    <div class="meter-label"><span>Rookie Cards (RC)</span> <strong>${(stats.countRookies)!0}</strong></div>
                    <div class="meter-bar-track"><div class="meter-bar orange" style="width: ${(stats.pctRookies)!0}%;"></div></div>
                </div>
            </div>

            <div class="metric-card">
                <h3>&#x1F3C5; Grading Census</h3>
                <div class="meter-group clickable-metric" onclick="filterByMetric('gem mint')" title="Click to filter Gem Mint cards">
                    <div class="meter-label"><span>Gem Mint (PSA 10 / BGS 9.5)</span> <strong>${(stats.countGemMint)!0}</strong></div>
                    <div class="meter-bar-track"><div class="meter-bar emerald" style="width: ${(stats.pctGemMint)!0}%;"></div></div>
                </div>
                <div class="meter-group clickable-metric" onclick="filterByMetric('graded')" title="Click to filter Graded cards">
                    <div class="meter-label"><span>Graded Total (PSA, BGS)</span> <strong>${(stats.countGradedTotal)!0}</strong></div>
                    <div class="meter-bar-track"><div class="meter-bar cyan" style="width: ${(stats.pctGradedTotal)!0}%;"></div></div>
                </div>
            </div>

            <div class="metric-card rainbow-metric-card">
                <h3>&#x1F308; Set & Rainbow Tracker</h3>
                <div class="meter-group clickable-metric" onclick="window.location.href='rainbows.html'" title="Open Parallel Rainbow Tracker">
                    <div class="meter-label"><span>Tracked Parallel Rainbows</span> <strong>Explore &rarr;</strong></div>
                    <div class="meter-bar-track"><div class="meter-bar gold" style="width: 100%;"></div></div>
                </div>
                <div class="meter-group clickable-metric" onclick="window.location.href='binder.html'" title="Open 3D 9-Pocket Binder">
                    <div class="meter-label"><span>3D 9-Pocket Binder View</span> <strong>Open &rarr;</strong></div>
                    <div class="meter-bar-track"><div class="meter-bar cyan" style="width: 100%;"></div></div>
                </div>
            </div>
        </div>
    </section>

    <#if seasons?has_content>
        <div class="view-mode-bar">
            <span class="view-label">&#x1F441;&#xFE0F; Views & Tools:</span>
            <button id="viewBinderBtn" class="view-btn" onclick="window.location.href='binder.html'">&#x1F4D2; 3D 9-Pocket Binder View</button>
            <button id="viewRainbowBtn" class="view-btn" onclick="window.location.href='rainbows.html'">&#x1F308; Parallel Rainbow Tracker</button>
            <button id="exportCsvBtn" class="export-csv-btn" onclick="exportCollectionCSV()" title="Export visible cards to CSV for Excel / Sheets">📥 Export CSV</button>
        </div>

        <div class="quick-filter-chips" aria-label="Quick Filter Highlights">
            <span class="quick-filter-title">⚡ Highlights:</span>
            <button type="button" class="filter-chip" onclick="toggleQuickFilter('favorites')" data-filter="favorites" id="favFilterChip">⭐ My Favorites (<span id="favCount">0</span>)</button>
            <button type="button" class="filter-chip" onclick="toggleQuickFilter('masterpiece')" data-filter="masterpiece">🔥 1/1 Masterpieces</button>
            <button type="button" class="filter-chip" onclick="toggleQuickFilter('pmg')" data-filter="pmg">💎 PMG</button>
            <button type="button" class="filter-chip" onclick="toggleQuickFilter('rookie')" data-filter="rookie">⭐ Rookies</button>
            <button type="button" class="filter-chip" onclick="toggleQuickFilter('auto')" data-filter="auto">✍️ Autographs</button>
            <button type="button" class="filter-chip" onclick="toggleQuickFilter('gameused')" data-filter="gameused">🛡️ Game-Used</button>
            <button type="button" class="filter-chip" onclick="toggleQuickFilter('gemmint')" data-filter="gemmint">🏅 Gem Mint (9.5+)</button>
            <button type="button" class="filter-chip" onclick="toggleQuickFilter('ruby')" data-filter="ruby">🔴 Star Rubies</button>
        </div>

        <div class="career-era-chips" aria-label="Career Era Filter Navigation">
            <span class="era-chip-title">🏀 Career Eras:</span>
            <button type="button" class="era-chip" onclick="filterByEra('bullets')" data-era="bullets">🏛️ Bullets Era (1994–97)</button>
            <button type="button" class="era-chip" onclick="filterByEra('wizards')" data-era="wizards">🧙 Wizards Era (1997–01)</button>
            <button type="button" class="era-chip" onclick="filterByEra('mavs_rockets')" data-era="mavs_rockets">🚀 Mavs &amp; Rockets (2001–07)</button>
            <button type="button" class="era-chip" onclick="filterByEra('heat')" data-era="heat">🏆 Heat Champions (2010–13)</button>
            <button type="button" class="era-chip" onclick="filterByEra('panini')" data-era="panini">✨ Modern Panini (2014+)</button>
        </div>

        <div class="collection-controls filter-container">
            <label for="seasonFilter" class="filter-label">Filter Collection:</label>
            <select id="seasonFilter" name="seasonFilter" class="filter-select" aria-label="Filter cards by season">
                <option value="all">All Seasons</option>
                <#list seasons as season>
                    <option value="${season.id}">${season.name}</option>
                </#list>
            </select>
            <div class="search-input-wrapper">
                <input type="text" id="textSearch" name="textSearch" class="filter-input" list="hobby-terms"
                       placeholder="Search PMG, Auto or Print Run <25, =1... (Press / to focus)"
                       aria-label="Search cards by keyword, serial number, or print run">
                <span class="search-hotkey-badge" title="Press / or Cmd+K to search"><kbd>Cmd+K</kbd> <kbd>/</kbd></span>
            </div>
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

        <div id="filterResultBar" class="filter-result-bar" style="display: none;">
            <span id="filterCountBadge" class="filter-count-badge"></span>
            <button id="clearFiltersBtn" type="button" class="clear-filter-btn" onclick="clearFilters()">✕ Clear Filter</button>
        </div>

        <div id="searchEmptyState" class="search-empty-state" style="display: none;">
            <div class="empty-state-icon">🔍</div>
            <h3>No Cards Match Your Search</h3>
            <p>No cards were found matching your current filter criteria.</p>
            <button type="button" class="modern-button" onclick="clearFilters()">Reset Filters</button>
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
            let overviewFilterDebounceTimer = null;

            document.getElementById('seasonFilter').addEventListener('change', () => {
                applyFilters();
                const seasonVal = document.getElementById('seasonFilter').value;
                if (seasonVal !== 'all') {
                    scrollToSeason(seasonVal, false);
                }
            });

            const overviewSearchInput = document.getElementById('textSearch');
            if (overviewSearchInput) {
                overviewSearchInput.addEventListener('input', () => {
                    clearTimeout(overviewFilterDebounceTimer);
                    overviewFilterDebounceTimer = setTimeout(() => {
                        window.requestAnimationFrame(applyFilters);
                    }, 100);
                });
            }

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

                // Support print run queries: <=25, <25, =1, 1/1, #/25, #25, /25, printrun <=25
                const prRegex = /^(?:printrun\s*|#\/?\s*)?([<>=]+|\/)?\s*(\d+)$/;
                const prMatch = searchVal.match(prRegex);

                let prOperator = null;
                let prTarget = null;
                if (prMatch) {
                    prOperator = prMatch[1] || '<=';
                    if (prOperator === '/') prOperator = '<=';
                    prTarget = parseInt(prMatch[2], 10);
                }

                let totalVisible = 0;
                let totalCards = 0;

                // Extract query terms for highlighting and token filtering
                const terms = (searchVal !== '' && !prMatch && !['favorites', 'favorite', 'fav'].includes(searchVal)) 
                              ? searchVal.split(/\s+/).filter(t => t.length > 0) 
                              : [];

                document.querySelectorAll('.season-table-wrapper').forEach(wrapper => {
                    const wrapperSeason = wrapper.getAttribute('data-season');
                    let visibleRowsInSeason = 0;

                    const rows = wrapper.querySelectorAll('tr');
                    for (let i = 0; i < rows.length; i++) {
                        const row = rows[i];
                        if (row.querySelector('th')) continue;
                        totalCards++;

                        if (seasonVal !== 'all' && wrapperSeason !== seasonVal) {
                            row.style.display = 'none';
                            clearElementHighlights(row);
                            continue;
                        }

                        let isMatch = false;

                        // Extract cell values directly by data-label for 100% precision
                        const rookieCell = row.querySelector('td[data-label="Rookie"]');
                        const isRookie = rookieCell ? (rookieCell.textContent.trim().toLowerCase() === 'yes') : false;

                        const autoCell = row.querySelector('td[data-label="Autograph"]');
                        const isAuto = autoCell ? (autoCell.textContent.trim().toLowerCase() === 'yes') : false;

                        const patchCell = row.querySelector('td[data-label="Patch"], td[data-label="Game Used"]');
                        const isPatch = patchCell ? (patchCell.textContent.trim().toLowerCase() === 'yes') : false;

                        const prCell = row.querySelector('td[data-label="Print Run"]');
                        const serialCell = row.querySelector('td[data-label="Serial #"], td[data-label="Serial"]');
                        const gradeCell = row.querySelector('td[data-label="Grade"]');

                        let cardPrintRun = NaN;
                        const extractPR = (cell) => {
                            if (!cell) return NaN;
                            const text = cell.textContent.trim();
                            if (!text || text === '-' || text === '—' || text === '') return NaN;
                            if (text.includes('/')) {
                                const parts = text.split('/');
                                return parseInt(parts[1].replace(/\D/g, ''), 10);
                            }
                            return parseInt(text.replace(/\D/g, ''), 10);
                        };

                        if (prCell) cardPrintRun = extractPR(prCell);
                        if (isNaN(cardPrintRun) && serialCell) cardPrintRun = extractPR(serialCell);

                        const is1of1 = (cardPrintRun === 1);

                        const gradeText = gradeCell ? gradeCell.textContent.trim().toLowerCase() : '';
                        const isGraded = gradeText !== '' && gradeText !== 'no' && gradeText !== '—' && gradeText !== '-';
                        const gradeNum = parseFloat(gradeText);
                        const isGemMint = isGraded && (!isNaN(gradeNum) ? (gradeNum >= 9.5) : (gradeText.includes('10') || gradeText.includes('9.5') || gradeText.includes('gem')));

                        const rowText = row.textContent.toLowerCase();
                        const filenameBase = row.id || (row.querySelector('a.table-button') ? row.querySelector('a.table-button').getAttribute('href').split('/').pop().replace('.html', '') : '');
                        const stableId = row.getAttribute('data-card-id') || (filenameBase.includes('-') ? filenameBase.substring(filenameBase.lastIndexOf('-') + 1) : filenameBase);
                        const isFavoritesFilter = (searchVal === 'favorites' || searchVal === 'favorite' || searchVal === 'fav');

                        if (isFavoritesFilter) {
                            const favs = getFavorites();
                            isMatch = favs.includes(filenameBase) || favs.includes(stableId);
                        } else if (prMatch) {
                            if (!isNaN(cardPrintRun) && cardPrintRun > 0) {
                                if (prOperator === '<' && cardPrintRun < prTarget) isMatch = true;
                                else if (prOperator === '<=' && cardPrintRun <= prTarget) isMatch = true;
                                else if (prOperator === '>' && cardPrintRun > prTarget) isMatch = true;
                                else if (prOperator === '>=' && cardPrintRun >= prTarget) isMatch = true;
                                else if ((prOperator === '=' || prOperator === '==') && cardPrintRun === prTarget) isMatch = true;
                            }
                        } else {
                            if (searchVal === '') {
                                isMatch = true;
                            } else if (searchVal === 'gem mint' || searchVal === 'gem-mint' || searchVal === 'gem' || searchVal === 'mint') {
                                isMatch = isGemMint;
                            } else if (searchVal === 'game used' || searchVal === 'game-used') {
                                isMatch = isPatch;
                            } else {
                                isMatch = terms.every(term => {
                                    if (term === '1/1' || term === '=1' || term === 'masterpiece' || term === 'masterpieces') {
                                        return is1of1;
                                    } else if (term === 'pmg') {
                                        return rowText.includes('pmg') || rowText.includes('precious metal gems');
                                    } else if (term === 'auto' || term === 'autograph' || term === 'autographs') {
                                        return isAuto;
                                    } else if (term === 'patch' || term === 'jersey' || term === 'mem' || term === 'game' || term === 'used' || term === 'game-used') {
                                        return isPatch;
                                    } else if (term === 'rc' || term === 'rookie' || term === 'rookies') {
                                        return isRookie;
                                    } else if (term === 'gem' || term === 'mint' || term === 'gemmint') {
                                        return isGemMint;
                                    } else if (term === 'graded' || term === 'grading' || term === 'slab') {
                                        return isGraded;
                                    }
                                    return rowText.includes(term);
                                });
                            }
                        }

                        if (isMatch) {
                            row.style.display = '';
                            visibleRowsInSeason++;
                            totalVisible++;
                            if (terms.length > 0) {
                                highlightElementMatches(row, terms);
                            } else {
                                clearElementHighlights(row);
                            }
                        } else {
                            row.style.display = 'none';
                            clearElementHighlights(row);
                        }
                    }

                    wrapper.style.display = (visibleRowsInSeason > 0) ? '' : 'none';

                    const counterSpan = wrapper.querySelector('.visible-counter');
                    if (counterSpan) {
                        counterSpan.textContent = visibleRowsInSeason;
                        counterSpan.style.color = (searchVal !== '') ? '#d4af37' : '#555';
                    }
                });

                const filterResultBar = document.getElementById('filterResultBar');
                const filterCountBadge = document.getElementById('filterCountBadge');
                const searchEmptyState = document.getElementById('searchEmptyState');
                const jumpBar = document.querySelector('.season-jump-bar');

                const isFiltered = (seasonVal !== 'all' || searchVal !== '');
                if (isFiltered) {
                    if (filterResultBar) filterResultBar.style.display = 'flex';
                    if (filterCountBadge) {
                        const pct = totalCards > 0 ? ((totalVisible / totalCards) * 100).toFixed(1) : '0';
                        filterCountBadge.textContent = 'Showing ' + totalVisible.toLocaleString() + ' of ' + totalCards.toLocaleString() + ' cards (' + pct + '%)';
                    }
                } else {
                    if (filterResultBar) filterResultBar.style.display = 'none';
                }

                // Sync active states on quick filter chips
                document.querySelectorAll('.filter-chip').forEach(chip => {
                    const f = chip.getAttribute('data-filter');
                    let q = '';
                    if (f === 'favorites') q = 'favorites';
                    else if (f === 'masterpiece') q = '= 1';
                    else if (f === 'pmg') q = 'pmg';
                    else if (f === 'rookie') q = 'rookie';
                    else if (f === 'auto') q = 'auto';
                    else if (f === 'gameused') q = 'game-used';
                    else if (f === 'gemmint') q = 'gem mint';
                    else if (f === 'ruby') q = 'ruby';

                    if (searchVal !== '' && (searchVal === q || (q === '= 1' && (searchVal === '=1' || searchVal === '1/1' || searchVal === 'masterpiece')) || (q === 'game-used' && (searchVal === 'game used' || searchVal === 'patch' || searchVal === 'jersey')))) {
                        chip.classList.add('active');
                    } else {
                        chip.classList.remove('active');
                    }
                });

                if (totalVisible === 0) {
                    if (searchEmptyState) searchEmptyState.style.display = 'block';
                    if (jumpBar) jumpBar.style.display = 'none';
                } else {
                    if (searchEmptyState) searchEmptyState.style.display = 'none';
                    if (jumpBar) jumpBar.style.display = '';
                }
            }

            // --- CAREER ERA QUICK FILTER ---
            function filterByEra(eraKey) {
                const eraButtons = document.querySelectorAll('.era-chip');
                const clickedBtn = document.querySelector('.era-chip[data-era="' + eraKey + '"]');
                const isAlreadyActive = clickedBtn && clickedBtn.classList.contains('active');

                eraButtons.forEach(b => b.classList.remove('active'));

                if (isAlreadyActive) {
                    clearFilters();
                    return;
                }

                if (clickedBtn) clickedBtn.classList.add('active');

                const eraSeasonMap = {
                    'bullets': ['1994-95', '1995-96', '1996-97'],
                    'wizards': ['1997-98', '1998-99', '1999-00', '2000-01'],
                    'mavs_rockets': ['2001-02', '2002-03', '2003-04', '2004-05', '2005-06', '2006-07', '2007-08', '2008-09'],
                    'heat': ['2010-11', '2011-12', '2012-13'],
                    'panini': ['2012-13', '2013-14', '2014-15', '2015-16', '2016-17', '2017-18', '2018-19', '2019-20', '2020-21', '2021-22', '2022-23', '2023-24', '2024-25']
                };

                const targetSeasons = eraSeasonMap[eraKey] || [];
                let totalVisible = 0;
                let totalCards = 0;

                document.querySelectorAll('.season-table-wrapper').forEach(wrapper => {
                    const s = wrapper.getAttribute('data-season');
                    const rows = wrapper.querySelectorAll('tr');
                    const isSeasonInEra = targetSeasons.includes(s);
                    let visibleCount = 0;

                    for (let i = 0; i < rows.length; i++) {
                        const row = rows[i];
                        if (row.querySelector('th')) continue;
                        totalCards++;

                        if (isSeasonInEra) {
                            row.style.display = '';
                            visibleCount++;
                            totalVisible++;
                        } else {
                            row.style.display = 'none';
                        }
                    }

                    wrapper.style.display = isSeasonInEra ? '' : 'none';
                    const counter = wrapper.querySelector('.visible-counter');
                    if (counter) {
                        counter.textContent = visibleCount;
                        counter.style.color = '#d4af37';
                    }
                });

                const filterResultBar = document.getElementById('filterResultBar');
                const filterCountBadge = document.getElementById('filterCountBadge');
                if (filterResultBar) filterResultBar.style.display = 'flex';
                if (filterCountBadge) {
                    const pct = totalCards > 0 ? ((totalVisible / totalCards) * 100).toFixed(1) : '0';
                    filterCountBadge.textContent = 'Showing ' + totalVisible.toLocaleString() + ' of ' + totalCards.toLocaleString() + ' cards (' + pct + '%)';
                }

                // Scroll to first visible season
                const firstVisible = document.querySelector('.season-table-wrapper[style*="display: none"]');
                const allWrappers = Array.from(document.querySelectorAll('.season-table-wrapper'));
                const firstMatched = allWrappers.find(w => w.style.display !== 'none');
                if (firstMatched) {
                    firstMatched.scrollIntoView({ behavior: 'smooth', block: 'start' });
                }
            }

            // Keyboard navigation shortcuts for Search and Reset
            document.addEventListener('keydown', (e) => {
                if (['INPUT', 'TEXTAREA', 'SELECT'].includes(e.target.tagName)) {
                    if (e.key === 'Escape') {
                        e.target.blur();
                        clearFilters();
                    }
                    return;
                }
                if (e.key === '/' || ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k')) {
                    e.preventDefault();
                    const searchBox = document.getElementById('textSearch');
                    if (searchBox) {
                        searchBox.focus();
                        searchBox.select();
                    }
                } else if (e.key === 'Escape') {
                    clearFilters();
                }
            });


            // --- 1-CLICK CSV EXPORT (UTF-8 EXCEL READY) ---
            window.exportCollectionCSV = function() {
                const visibleRows = [];
                const headers = [
                    "Player", "Team", "Season", "Company", "Brand",
                    "Set / Theme", "Variant", "Card #", "Serial #",
                    "Print Run", "Rookie", "Game Used", "Autograph", "Grade", "URL"
                ];

                document.querySelectorAll('.season-table-wrapper table tbody tr').forEach(row => {
                    if (row.style.display !== 'none') {
                        const cells = row.querySelectorAll('td');
                        if (cells.length >= 14) {
                            const link = cells[0].querySelector('a');
                            const fullUrl = link ? link.href : '';
                            const cleanText = (el) => {
                                if (!el) return '';
                                let clone = el.cloneNode(true);
                                let favBtn = clone.querySelector('.card-fav-btn');
                                if (favBtn) favBtn.remove();
                                return clone.textContent.trim().replace(/"/g, '""');
                            };

                            const rowData = [
                                cleanText(cells[0]),
                                cleanText(cells[1]),
                                cleanText(cells[3]),
                                cleanText(cells[4]),
                                cleanText(cells[5]),
                                cleanText(cells[6]),
                                cleanText(cells[7]),
                                cleanText(cells[8]),
                                cleanText(cells[9]),
                                cleanText(cells[10]),
                                cleanText(cells[11]),
                                cleanText(cells[12]),
                                cleanText(cells[13]),
                                cleanText(cells[14] || cells[13]),
                                fullUrl
                            ];
                            visibleRows.push(rowData);
                        }
                    }
                });

                if (visibleRows.length === 0) {
                    alert("No visible cards found to export.");
                    return;
                }

                let csvContent = "\uFEFF"; // UTF-8 BOM
                csvContent += headers.map(function(h) { return '"' + h + '"'; }).join(",") + "\r\n";
                visibleRows.forEach(function(row) {
                    csvContent += row.map(function(val) { return '"' + val + '"'; }).join(",") + "\r\n";
                });

                const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
                const url = URL.createObjectURL(blob);
                const link = document.createElement("a");
                link.setAttribute("href", url);
                const timestamp = new Date().toISOString().slice(0, 10);
                link.setAttribute("download", "Juwan-Howard-Collection-" + timestamp + ".csv");
                document.body.appendChild(link);
                link.click();
                document.body.removeChild(link);
                URL.revokeObjectURL(url);
            };

            window.toggleQuickFilter = function(filterType) {
                const textSearch = document.getElementById('textSearch');
                const seasonFilter = document.getElementById('seasonFilter');
                if (!textSearch) return;

                let targetQuery = '';
                if (filterType === 'favorites') targetQuery = 'favorites';
                else if (filterType === 'masterpiece') targetQuery = '= 1';
                else if (filterType === 'pmg') targetQuery = 'PMG';
                else if (filterType === 'rookie') targetQuery = 'Rookie';
                else if (filterType === 'auto') targetQuery = 'Auto';
                else if (filterType === 'gameused') targetQuery = 'Game-Used';
                else if (filterType === 'gemmint') targetQuery = 'Gem Mint';
                else if (filterType === 'ruby') targetQuery = 'Ruby';

                const currentVal = textSearch.value.trim().toLowerCase();
                const isAlreadyActive = (currentVal === targetQuery.toLowerCase()) || 
                    (filterType === 'masterpiece' && (currentVal === '=1' || currentVal === '1/1' || currentVal === 'masterpiece'));

                if (isAlreadyActive) {
                    textSearch.value = '';
                } else {
                    if (seasonFilter) seasonFilter.value = 'all';
                    textSearch.value = targetQuery;
                }
                applyFilters();
            };

            window.clearFilters = function() {
                const s = document.getElementById('seasonFilter');
                const t = document.getElementById('textSearch');
                if (s) s.value = 'all';
                if (t) t.value = '';
                applyFilters();
            };

            window.filterByMetric = function(query) {
                const textSearch = document.getElementById('textSearch');
                const seasonFilter = document.getElementById('seasonFilter');
                if (seasonFilter) seasonFilter.value = 'all';
                if (textSearch) {
                    textSearch.value = query;
                    applyFilters();
                    const filterSection = document.querySelector('.collection-controls');
                    if (filterSection) {
                        filterSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
                    }
                }
            };

            const FAVORITES_KEY = 'maulmann_card_favorites';
            function getFavorites() {
                try {
                    const data = localStorage.getItem(FAVORITES_KEY);
                    return data ? JSON.parse(data) : [];
                } catch (e) {
                    return [];
                }
            }

            function updateFavoritesCount() {
                const favs = getFavorites();
                const countEl = document.getElementById('favCount');
                if (countEl) countEl.textContent = favs.length;
            }

            document.addEventListener('DOMContentLoaded', () => {
                updateFavoritesCount();

                // OpenSearch query parameter support (?q=...)
                const urlParams = new URLSearchParams(window.location.search);
                const queryParam = urlParams.get('q');
                if (queryParam) {
                    const textSearch = document.getElementById('textSearch');
                    if (textSearch) {
                        textSearch.value = queryParam;
                        applyFilters();
                    }
                }

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

                // Scroll Restoration Logic or Fragment Jump
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