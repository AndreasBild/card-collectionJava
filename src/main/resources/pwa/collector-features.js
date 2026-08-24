/* Maulmann Card Collection - Collector UX & Interactive Features */

// --- 1. COPY SNIPPET TO CLIPBOARD ---
function copySnippet(inputId, btn) {
    const input = document.getElementById(inputId);
    if (!input) return;
    input.select();
    input.setSelectionRange(0, 99999);
    
    navigator.clipboard.writeText(input.value).then(() => {
        const originalText = btn.innerText;
        btn.innerText = 'Copied!';
        btn.classList.add('copied-success');
        setTimeout(() => {
            btn.innerText = originalText;
            btn.classList.remove('copied-success');
        }, 2000);
    }).catch(err => {
        console.error('Copy failed: ', err);
    });
}

// Initialize interactive features on page load
function initAllFeatures() {
    try {
        localStorage.removeItem('collectorLayout');
    } catch (e) {}

    initCompareButtons();
    updateCompareBar();
    initTableFilter();
    init3DCardTilt();
    initRainbowOrientation();
    initBackToTop();
    initGlobalKeyboardShortcuts();
    initTouchSwipeNavigation();
    initLazyImageFade();
    initWantlistChecklist();
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initAllFeatures);
} else {
    initAllFeatures();
}

window.addEventListener('pageshow', () => {
    initCompareButtons();
    updateCompareBar();
});

// --- 2. UNIVERSAL DOM HIT HIGHLIGHTING ENGINE ---
function highlightElementMatches(el, terms) {
    if (!el) return;
    if (!terms || terms.length === 0) {
        clearElementHighlights(el);
        return;
    }

    const validTerms = terms.filter(t => t && t.trim().length > 0);
    if (validTerms.length === 0) {
        clearElementHighlights(el);
        return;
    }

    const cells = el.querySelectorAll('td, .card-title, .pocket-card-title, .rainbow-title');
    const targetNodes = (cells && cells.length > 0) ? Array.from(cells) : [el];

    const escapedTerms = validTerms.map(t => t.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
    const regex = new RegExp('(' + escapedTerms.join('|') + ')', 'gi');

    targetNodes.forEach(node => {
        if (['INPUT', 'BUTTON', 'SELECT'].includes(node.tagName)) return;

        if (!node.getAttribute('data-orig-html')) {
            node.setAttribute('data-orig-html', node.innerHTML);
        }

        const orig = node.getAttribute('data-orig-html');
        node.innerHTML = orig.replace(/(<[^>]+>)|([^<]+)/g, (match, tag, text) => {
            if (tag) return tag;
            return text.replace(regex, '<mark class="search-highlight">$1</mark>');
        });
    });
}

function clearElementHighlights(el) {
    if (!el) return;
    const highlighted = el.querySelectorAll('[data-orig-html]');
    highlighted.forEach(node => {
        node.innerHTML = node.getAttribute('data-orig-html');
        node.removeAttribute('data-orig-html');
    });
    if (el.getAttribute && el.getAttribute('data-orig-html')) {
        el.innerHTML = el.getAttribute('data-orig-html');
        el.removeAttribute('data-orig-html');
    }
}

window.highlightElementMatches = highlightElementMatches;
window.clearElementHighlights = clearElementHighlights;

// --- 2.5 REAL-TIME INSTANT TABLE SEARCH FILTER (DEBOUNCED) ---
let filterDebounceTimer = null;

function initTableFilter() {
    const searchInputs = document.querySelectorAll('.table-search-input, #cardSearchInput');
    if (!searchInputs || searchInputs.length === 0) return;

    searchInputs.forEach(input => {
        input.addEventListener('input', (e) => {
            const query = e.target.value.toLowerCase().trim();
            clearTimeout(filterDebounceTimer);
            filterDebounceTimer = setTimeout(() => {
                window.requestAnimationFrame(() => filterTables(query));
            }, 120);
        });
    });
}

function filterTables(query) {
    const rows = document.querySelectorAll('table tbody tr, table tr');
    if (!rows || rows.length === 0) return;

    const terms = query ? query.split(/\s+/).filter(t => t.length > 0) : [];

    rows.forEach(row => {
        // Skip table headers
        if (row.querySelector('th')) return;
        if (!query) {
            row.style.display = '';
            clearElementHighlights(row);
            return;
        }
        const text = row.textContent.toLowerCase();
        const matches = terms.every(term => text.includes(term));
        if (matches) {
            row.style.display = '';
            highlightElementMatches(row, terms);
        } else {
            row.style.display = 'none';
            clearElementHighlights(row);
        }
    });
}

// Global Keyboard Accessibility (Escape to close modals)
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' || e.key === 'Esc') {
        closeCompareModal();
    }
});

// --- 3. SIDE-BY-SIDE CARD COMPARISON TOOL ---
const COMPARE_KEY = 'maulmann_compare_list';

function escapeCompareHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');
}

function normalizeCardImage(img) {
    if (!img) return '';
    // Normalize relative paths so images render properly from any URL depth
    if (img.startsWith('../../images/')) {
        return '/images/' + img.substring(13);
    } else if (img.startsWith('../images/')) {
        return '/images/' + img.substring(10);
    } else if (img.startsWith('images/')) {
        return '/' + img;
    }
    return img;
}

function getCompareList() {
    try {
        const stored = localStorage.getItem(COMPARE_KEY) || sessionStorage.getItem(COMPARE_KEY);
        const parsed = stored ? JSON.parse(stored) : [];
        return Array.isArray(parsed) ? parsed : [];
    } catch (e) {
        return [];
    }
}

function saveCompareList(list) {
    const serialized = JSON.stringify(list);
    try {
        localStorage.setItem(COMPARE_KEY, serialized);
    } catch (e) {
        try { sessionStorage.setItem(COMPARE_KEY, serialized); } catch (err) {}
    }
    updateCompareBar();
    updateCompareButtonUI();
    renderCompareModalIfOpen();
}

function updateCompareButtonUI() {
    const list = getCompareList();

    const btn = document.getElementById('compareBtn');
    if (btn) {
        const id = btn.getAttribute('data-card-id');
        const isSelected = list.some(item => item.id === id);
        const textSpan = btn.querySelector('span');

        if (isSelected) {
            btn.classList.add('active-compare');
            btn.setAttribute('aria-pressed', 'true');
            if (textSpan) textSpan.innerText = 'Compared';
            btn.setAttribute('title', 'Card is in comparison list (click to remove)');
        } else {
            btn.classList.remove('active-compare');
            btn.setAttribute('aria-pressed', 'false');
            if (textSpan) textSpan.innerText = 'Compare';
            btn.setAttribute('title', 'Compare this card with others');
        }
    }

    const viewBtn = document.getElementById('viewCompareBtn');
    const navCount = document.getElementById('navCompareCount');
    if (viewBtn) {
        if (list.length > 0) {
            viewBtn.style.display = 'inline-flex';
            if (navCount) navCount.innerText = list.length;
        } else {
            viewBtn.style.display = 'none';
        }
    }
}

function showCompareToast(messageHtml) {
    let toast = document.getElementById('toastNotification');
    if (!toast) {
        toast = document.createElement('div');
        toast.id = 'toastNotification';
        toast.className = 'toast-notification';
        toast.innerHTML = `<span class="toast-icon">✨</span><span id="toastMessage" class="toast-text"></span>`;
        document.body.appendChild(toast);
    }
    const msgEl = document.getElementById('toastMessage') || toast;
    msgEl.innerHTML = messageHtml;
    toast.classList.add('toast-show');
    clearTimeout(toast._timer);
    toast._timer = setTimeout(() => {
        toast.classList.remove('toast-show');
    }, 3500);
}

let compareShowBack = false;
let compareHighlightDiffs = false;

function toggleCompareCurrentCard(btn) {
    if (!btn) btn = document.getElementById('compareBtn');
    if (!btn) return;
    const id = btn.getAttribute('data-card-id');
    const title = btn.getAttribute('data-card-title');
    const img = btn.getAttribute('data-card-img');
    const backImg = btn.getAttribute('data-card-back-img');
    const url = btn.getAttribute('data-card-url');
    const player = btn.getAttribute('data-card-player');
    const season = btn.getAttribute('data-card-season');
    const team = btn.getAttribute('data-card-team');
    const company = btn.getAttribute('data-card-company');
    const brand = btn.getAttribute('data-card-brand');
    const theme = btn.getAttribute('data-card-theme');
    const variant = btn.getAttribute('data-card-variant');
    const number = btn.getAttribute('data-card-number');
    const serial = btn.getAttribute('data-card-serial');
    const rookie = btn.getAttribute('data-card-rookie');
    const patch = btn.getAttribute('data-card-patch');
    const auto = btn.getAttribute('data-card-auto');
    const grade = btn.getAttribute('data-card-grade');

    toggleCompareCard({
        id, title, img, backImg, url, player, season, team, company, brand, theme, variant, number, serial, rookie, patch, auto, grade
    });
}

function initCompareButtons() {
    updateCompareButtonUI();
}

// Sync comparison across tabs
window.addEventListener('storage', (e) => {
    if (e.key === COMPARE_KEY) {
        updateCompareBar();
        updateCompareButtonUI();
        renderCompareModalIfOpen();
    }
});

function toggleCompareCard(card) {
    if (!card || !card.id) return;
    if (typeof navigator !== 'undefined' && navigator.vibrate) {
        try { navigator.vibrate(15); } catch(e) {}
    }
    let list = getCompareList();
    const index = list.findIndex(item => item.id === card.id);

    if (index >= 0) {
        list.splice(index, 1);
        showCompareToast("⚖️ Removed from comparison");
    } else {
        if (list.length >= 3) {
            showCompareToast('⚠️ Max 3 cards in comparison. <button type="button" onclick="openCompareModal()" class="toast-compare-action">View &rarr;</button>');
            return;
        }
        list.push({
            id: String(card.id),
            title: card.title || 'Card',
            img: normalizeCardImage(card.img),
            backImg: normalizeCardImage(card.backImg),
            url: card.url || '',
            player: card.player || '',
            season: card.season || '',
            team: card.team || '',
            company: card.company || '',
            brand: card.brand || '',
            theme: card.theme || '',
            variant: card.variant || '',
            number: card.number || '',
            serial: card.serial || '',
            rookie: card.rookie || '',
            patch: card.patch || '',
            auto: card.auto || '',
            grade: card.grade || ''
        });
        showCompareToast(`⚖️ Added to comparison (${list.length}/3) &bull; <button type="button" onclick="openCompareModal()" class="toast-compare-action">View &rarr;</button>`);
    }

    saveCompareList(list);
}

function removeCompareCard(id) {
    let list = getCompareList();
    list = list.filter(item => item.id !== id);
    saveCompareList(list);
}

function clearCompareList() {
    try {
        localStorage.removeItem(COMPARE_KEY);
        sessionStorage.removeItem(COMPARE_KEY);
    } catch (e) {}
    updateCompareButtonUI();
    updateCompareBar();
    closeCompareModal();
}

function updateCompareBar() {
    let bar = document.getElementById('compareBar');
    const list = getCompareList();

    if (!bar) {
        bar = document.createElement('div');
        bar.id = 'compareBar';
        bar.className = 'compare-sticky-bar';
        bar.innerHTML = `
            <div class="compare-bar-content">
                <span class="compare-bar-label">&#x2696;&#xFE0F; <strong>Comparison</strong> (<span id="compareCount">0</span>/3)</span>
                <div class="compare-bar-actions">
                    <button type="button" class="modern-button compare-now-btn" onclick="openCompareModal()">&#x2696;&#xFE0F; View Comparison</button>
                    <button type="button" class="modern-button secondary compare-clear-btn" onclick="clearCompareList()" title="Clear all cards">Clear</button>
                </div>
            </div>
        `;
        document.body.appendChild(bar);
    }

    const countSpan = document.getElementById('compareCount');
    if (countSpan) countSpan.innerText = list.length;

    if (list.length > 0) {
        bar.style.display = 'flex';
    } else {
        bar.style.display = 'none';
    }
}

function toggleCompareScansSide() {
    compareShowBack = !compareShowBack;
    const btn = document.getElementById('flipScansBtn');
    if (btn) {
        btn.innerHTML = compareShowBack ? '🔄 Show Front Scans' : '🔄 Show Back Scans';
    }
    const list = getCompareList();
    renderCompareModalGrid(list);
}

function toggleHighlightDifferences() {
    compareHighlightDiffs = !compareHighlightDiffs;
    const btn = document.getElementById('highlightDiffsBtn');
    if (btn) {
        btn.classList.toggle('active', compareHighlightDiffs);
        btn.setAttribute('aria-pressed', compareHighlightDiffs ? 'true' : 'false');
    }
    const table = document.getElementById('compareMatrixTable');
    if (table) {
        table.classList.toggle('highlight-diffs-active', compareHighlightDiffs);
    }
}

function copyCompareShareLink() {
    const list = getCompareList();
    if (!list || list.length === 0) return;
    const ids = list.map(c => encodeURIComponent(c.id)).join(',');
    const shareUrl = window.location.origin + window.location.pathname + '#compare=' + ids;
    
    if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(shareUrl).then(() => {
            showCompareToast("📋 Comparison link copied to clipboard!");
        }).catch(() => {
            showCompareToast("🔗 " + shareUrl);
        });
    } else {
        showCompareToast("🔗 " + shareUrl);
    }
}

function renderCompareModalGrid(list) {
    const container = document.getElementById('compareModalGrid');
    if (!container) return;

    if (!list || list.length === 0) {
        container.innerHTML = '<p style="text-align: center; color: var(--text-muted); padding: 32px 16px;">No cards currently selected for comparison. Add cards using the <strong>Compare</strong> button on any card page.</p>';
        return;
    }

    // Check attribute differences across compared cards
    const getValues = (key) => list.map(c => (c[key] || '').toString().trim());
    const isDiff = (key) => {
        if (list.length < 2) return false;
        const vals = getValues(key);
        return vals.some(v => v !== vals[0]);
    };

    const attributes = [
        { label: 'Player', key: 'player', icon: '🏀' },
        { label: 'Season', key: 'season', icon: '📅' },
        { label: 'Team', key: 'team', icon: '🏛️' },
        { label: 'Manufacturer', key: 'company', icon: '🏭' },
        { label: 'Brand', key: 'brand', icon: '🏷️' },
        { label: 'Theme / Set', key: 'theme', icon: '✨' },
        { label: 'Variant', key: 'variant', icon: '🌈' },
        { label: 'Card #', key: 'number', icon: '🔢' },
        { label: 'Serial / Print Run', key: 'serial', icon: '💎', isSerial: true },
        { label: 'Rookie Card (RC)', key: 'rookie', icon: '⭐' },
        { label: 'Memorabilia / Patch', key: 'patch', icon: '🛡️' },
        { label: 'Autograph', key: 'auto', icon: '✍️' },
        { label: 'Grading', key: 'grade', icon: '🏅' }
    ];

    let html = `
        <div class="compare-toolbar">
            <div class="compare-toolbar-left">
                <button type="button" id="flipScansBtn" class="modern-button secondary compare-tool-btn" onclick="toggleCompareScansSide()">
                    ${compareShowBack ? '🔄 Show Front Scans' : '🔄 Show Back Scans'}
                </button>
                <button type="button" id="highlightDiffsBtn" class="modern-button secondary compare-tool-btn ${compareHighlightDiffs ? 'active' : ''}" onclick="toggleHighlightDifferences()" aria-pressed="${compareHighlightDiffs}">
                    🔍 Highlight Differences
                </button>
            </div>
            <div class="compare-toolbar-right">
                <button type="button" class="modern-button secondary compare-tool-btn" onclick="copyCompareShareLink()" title="Copy shareable link to this comparison">
                    🔗 Share
                </button>
                <button type="button" class="modern-button secondary compare-tool-btn" onclick="clearCompareList()" title="Clear comparison list">
                    🗑️ Clear All
                </button>
            </div>
        </div>

        <div class="compare-matrix-scroll">
            <table id="compareMatrixTable" class="compare-matrix-table ${compareHighlightDiffs ? 'highlight-diffs-active' : ''}">
                <thead>
                    <tr class="compare-matrix-header-row">
                        <th class="matrix-attr-label-th">Attribute</th>
                        ${list.map(item => {
                            const imgSrc = compareShowBack && item.backImg ? item.backImg : (item.img || item.backImg);
                            return `
                                <th class="matrix-card-col-th" data-card-id="${escapeCompareHtml(item.id)}">
                                    <div class="matrix-card-header-box">
                                        <button type="button" class="remove-col-btn" title="Remove from comparison" aria-label="Remove card" onclick="removeCompareCard('${escapeCompareHtml(item.id)}')">&times;</button>
                                        <div class="compare-card-img-wrap">
                                            <img src="${escapeCompareHtml(normalizeCardImage(imgSrc))}" alt="${escapeCompareHtml(item.title)}" loading="lazy" decoding="async">
                                        </div>
                                        <h4 class="matrix-card-title">${escapeCompareHtml(item.title)}</h4>
                                        <a href="${escapeCompareHtml(item.url || '#')}" class="modern-button matrix-view-btn">View Specs &rarr;</a>
                                    </div>
                                </th>
                            `;
                        }).join('')}
                    </tr>
                </thead>
                <tbody>
                    ${attributes.map(attr => {
                        const diffClass = isDiff(attr.key) ? 'matrix-row-diff' : '';
                        return `
                            <tr class="matrix-attr-row ${diffClass}" data-attr="${attr.key}">
                                <td class="matrix-attr-cell-label">
                                    <span class="matrix-attr-icon">${attr.icon}</span>
                                    <strong>${attr.label}</strong>
                                    ${isDiff(attr.key) ? '<span class="matrix-diff-badge" title="Different values across compared cards">Diff</span>' : ''}
                                </td>
                                ${list.map(item => {
                                    const val = item[attr.key] || '-';
                                    let badgeHtml = escapeCompareHtml(val);
                                    if (attr.isSerial && val && (val.includes('1/1') || val.includes('/10') || val.includes('/25'))) {
                                        badgeHtml = `<span class="matrix-rare-badge">${escapeCompareHtml(val)}</span>`;
                                    } else if (attr.key === 'grade' && val && val !== '-') {
                                        badgeHtml = `<span class="matrix-grade-badge">${escapeCompareHtml(val)}</span>`;
                                    }
                                    return `<td class="matrix-attr-cell-val" data-card-id="${escapeCompareHtml(item.id)}">${badgeHtml}</td>`;
                                }).join('')}
                            </tr>
                        `;
                    }).join('')}
                </tbody>
            </table>
        </div>
    `;

    container.innerHTML = html;
}

function renderCompareModalIfOpen() {
    const modal = document.getElementById('compareModal');
    if (modal && modal.style.display !== 'none') {
        const list = getCompareList();
        if (list.length === 0) {
            closeCompareModal();
        } else {
            renderCompareModalGrid(list);
        }
    }
}

function openCompareModal() {
    const list = getCompareList();
    if (list.length === 0) return;

    let modal = document.getElementById('compareModal');
    if (!modal) {
        modal = document.createElement('div');
        modal.id = 'compareModal';
        modal.className = 'compare-modal-overlay';
        modal.setAttribute('role', 'dialog');
        modal.setAttribute('aria-modal', 'true');
        modal.setAttribute('aria-label', 'Side-by-Side Card Comparison Matrix');
        modal.addEventListener('click', (e) => {
            if (e.target === modal) closeCompareModal();
        });
        modal.innerHTML = `
            <div class="compare-modal-box">
                <div class="compare-modal-header">
                    <h3>&#x2696;&#xFE0F; Side-by-Side Card Comparison</h3>
                    <button type="button" class="modal-close-btn" aria-label="Close Comparison" onclick="closeCompareModal()">&times;</button>
                </div>
                <div id="compareModalGrid" class="compare-modal-grid"></div>
            </div>
        `;
        document.body.appendChild(modal);
    }

    renderCompareModalGrid(list);
    modal.style.display = 'flex';
}

function closeCompareModal() {
    const modal = document.getElementById('compareModal');
    if (modal) modal.style.display = 'none';
}

// Attach globally for inline handlers and modules
window.openCompareModal = openCompareModal;
window.closeCompareModal = closeCompareModal;
window.clearCompareList = clearCompareList;
window.removeCompareCard = removeCompareCard;
window.toggleCompareCard = toggleCompareCard;
window.toggleCompareCurrentCard = toggleCompareCurrentCard;
window.toggleCompareScansSide = toggleCompareScansSide;
window.toggleHighlightDifferences = toggleHighlightDifferences;
window.copyCompareShareLink = copyCompareShareLink;

// --- 5. REALISTIC 3D HOLOGRAPHIC CARD TILT & REFRACTOR SHINE EFFECT ---
function init3DCardTilt() {
    const cardContainers = document.querySelectorAll('.card-image-wrapper, .flip-container');
    if (!cardContainers || cardContainers.length === 0) return;

    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (prefersReducedMotion) return;

    cardContainers.forEach(container => {
        const targetCard = container.querySelector('.card-3d-interactive') || container.querySelector('.card-image') || container.querySelector('.flip-card-inner');
        if (!targetCard) return;

        let tiltFrame = null;
        container.addEventListener('mousemove', (e) => {
            if (tiltFrame) cancelAnimationFrame(tiltFrame);
            tiltFrame = requestAnimationFrame(() => {
                const rect = container.getBoundingClientRect();
                const x = e.clientX - rect.left;
                const y = e.clientY - rect.top;
                const centerX = rect.width / 2;
                const centerY = rect.height / 2;

                const percentX = Math.max(0, Math.min(100, (x / rect.width) * 100));
                const percentY = Math.max(0, Math.min(100, (y / rect.height) * 100));

                const deltaX = (x - centerX) / centerX;
                const deltaY = (y - centerY) / centerY;
                const rotateX = deltaY * -10;
                const rotateY = deltaX * 10;
                const distFromCenter = Math.min(1, Math.hypot(deltaX, deltaY));
                const angleDeg = (Math.atan2(deltaY, deltaX) * (180 / Math.PI) + 360) % 360;

                container.style.setProperty('--pointer-x', `${percentX.toFixed(1)}%`);
                container.style.setProperty('--pointer-y', `${percentY.toFixed(1)}%`);
                container.style.setProperty('--pointer-from-center', `${distFromCenter.toFixed(2)}`);
                container.style.setProperty('--shine-angle', `${angleDeg.toFixed(1)}deg`);

                targetCard.style.transform = `perspective(1000px) rotateX(${rotateX.toFixed(2)}deg) rotateY(${rotateY.toFixed(2)}deg) scale3d(1.025, 1.025, 1.025)`;
            });
        });

        container.addEventListener('mouseleave', () => {
            if (tiltFrame) cancelAnimationFrame(tiltFrame);
            targetCard.style.transform = 'perspective(1000px) rotateX(0deg) rotateY(0deg) scale3d(1, 1, 1)';
            container.style.setProperty('--pointer-x', '50%');
            container.style.setProperty('--pointer-y', '50%');
            container.style.setProperty('--pointer-from-center', '0');
            container.style.setProperty('--shine-angle', '135deg');
        });
    });
}

// --- 6. DYNAMIC ORIENTATION DETECTION FOR RAINBOW & BINDER SLOTS ---
function initRainbowOrientation() {
    const images = document.querySelectorAll('.rainbow-card-img, .pocket-card-img');
    if (!images || images.length === 0) return;

    images.forEach(img => {
        const updateOri = () => {
            if (img.naturalWidth && img.naturalHeight) {
                const rainbowItem = img.closest('.rainbow-item');
                if (rainbowItem) {
                    if (img.naturalWidth > img.naturalHeight) {
                        rainbowItem.classList.add('is-landscape');
                        rainbowItem.classList.remove('is-portrait');
                    } else {
                        rainbowItem.classList.add('is-portrait');
                        rainbowItem.classList.remove('is-landscape');
                    }
                }
                const pocketFace = img.closest('.pocket-card-front, .pocket-card-back');
                if (pocketFace) {
                    if (img.naturalWidth > img.naturalHeight) {
                        pocketFace.classList.add('is-landscape');
                        pocketFace.classList.remove('is-portrait');
                    } else {
                        pocketFace.classList.add('is-portrait');
                        pocketFace.classList.remove('is-landscape');
                    }
                }
            }
        };

        if (img.complete && img.naturalWidth) {
            updateOri();
        } else {
            img.addEventListener('load', updateOri, { once: true });
        }
    });
}

// --- 7. FLOATING BACK TO TOP WITH CIRCULAR PROGRESS RING ---
function initBackToTop() {
    const btn = document.getElementById('backToTopBtn');
    const circle = document.getElementById('scrollProgressCircle');
    if (!btn || !circle) return;

    const circumference = 2 * Math.PI * 18; // ~113.1px
    let scrollTicking = false;

    window.addEventListener('scroll', () => {
        if (!scrollTicking) {
            window.requestAnimationFrame(() => {
                const scrollTop = window.scrollY || document.documentElement.scrollTop;
                const docHeight = document.documentElement.scrollHeight - document.documentElement.clientHeight;

                if (scrollTop > 350) {
                    btn.classList.add('is-visible');
                } else {
                    btn.classList.remove('is-visible');
                }

                if (docHeight > 0) {
                    const scrollFraction = Math.min(1, Math.max(0, scrollTop / docHeight));
                    const offset = circumference - (scrollFraction * circumference);
                    circle.style.strokeDashoffset = offset.toFixed(1);
                }
                scrollTicking = false;
            });
            scrollTicking = true;
        }
    }, { passive: true });

    btn.addEventListener('click', () => {
        window.scrollTo({ top: 0, behavior: 'smooth' });
    });
}

// --- 8. GLOBAL ACCESSIBILITY KEYBOARD SHORTCUTS ---
function initGlobalKeyboardShortcuts() {
    document.addEventListener('keydown', (e) => {
        if (['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement.tagName)) return;

        if (e.key === 'Escape') {
            if (typeof window.closeModal === 'function') window.closeModal();
            const compareModal = document.getElementById('compareModal');
            if (compareModal && compareModal.style.display !== 'none') compareModal.style.display = 'none';
        } else if ((e.key === '/' || ((e.metaKey || e.ctrlKey) && (e.key === 'k' || e.key === 'K'))) && !['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement.tagName)) {
            e.preventDefault();
            const searchInput = document.getElementById('textSearch') || 
                                document.getElementById('cardSearchInput') || 
                                document.querySelector('.table-search-input, .rainbow-search-input, #binderSearchInput, input[type="search"], input[name="textSearch"]');
            if (searchInput) {
                searchInput.focus();
                searchInput.select();
                searchInput.scrollIntoView({ behavior: 'smooth', block: 'center' });
            }
        } else if (e.key === 'ArrowLeft') {
            const prevLink = document.querySelector('.nav-button-group a:first-child, a[title*="Prev"], a.prev-card-link');
            if (prevLink && prevLink.href) prevLink.click();
        } else if (e.key === 'ArrowRight') {
            const nextLink = document.querySelector('.nav-button-group a:last-child, a[title*="Next"], a.next-card-link');
            if (nextLink && nextLink.href) nextLink.click();
        } else if (e.key === ' ' || e.key === 'f' || e.key === 'F') {
            const flipTarget = document.querySelector('.flip-modal-btn, .flip-container');
            if (flipTarget && window.location.pathname.includes('cards/')) {
                e.preventDefault();
                flipTarget.click();
            }
        }
    });
}

// --- 9. MOBILE TOUCH SWIPE FLIP GESTURE ---
function initTouchSwipeNavigation() {
    const cardViewport = document.querySelector('.card-viewport, .flip-container');
    if (!cardViewport) return;

    let touchStartX = 0;
    cardViewport.addEventListener('touchstart', (e) => {
        if (e.changedTouches && e.changedTouches.length > 0) {
            touchStartX = e.changedTouches[0].screenX;
        }
    }, { passive: true });

    cardViewport.addEventListener('touchend', (e) => {
        if (e.changedTouches && e.changedTouches.length > 0) {
            const touchEndX = e.changedTouches[0].screenX;
            const diff = touchEndX - touchStartX;
            if (Math.abs(diff) > 45) {
                const flipContainer = document.querySelector('.flip-container');
                if (flipContainer) flipContainer.click();
            }
        }
    }, { passive: true });
}

// --- 10. SMOOTH LAZY IMAGE FADE-IN TRANSITIONS ---
function initLazyImageFade() {
    const lazyImgs = document.querySelectorAll('img[loading="lazy"]');
    lazyImgs.forEach(img => {
        if (img.complete && img.naturalWidth) {
            img.classList.add('loaded');
        } else {
            img.addEventListener('load', () => img.classList.add('loaded'), { once: true });
        }
    });
}

// --- 11. UNIVERSAL 1-CLICK COLLECTION EXPORT (CSV & JSON) ---
function extractVisibleCardData() {
    const headers = [
        "Player", "Team", "Season", "Company", "Brand",
        "Set / Theme", "Variant", "Card #", "Serial #",
        "Print Run", "Rookie", "Game Used", "Autograph", "Grade", "URL"
    ];
    const rows = [];
    const jsonList = [];

    const rowElements = document.querySelectorAll('.season-table-wrapper table tbody tr, .collection-table tbody tr, table.wantlist-table tbody tr, table tbody tr');
    
    rowElements.forEach(row => {
        if (row.querySelector('th')) return;
        if (row.style.display === 'none') return;

        const cells = row.querySelectorAll('td');
        if (cells.length >= 7) {
            const cleanText = (el) => {
                if (!el) return '';
                let clone = el.cloneNode(true);
                let btn = clone.querySelector('.card-fav-btn, .wantlist-checkbox-wrap, button');
                if (btn) btn.remove();
                return clone.textContent.trim().replace(/"/g, '""');
            };

            const link = row.querySelector('a');
            const fullUrl = link ? link.href : '';

            // Handle tables with varying column lengths dynamically
            const rowData = [
                cleanText(cells[0]),
                cleanText(cells[1]),
                cleanText(cells[2] || cells[1]),
                cleanText(cells[3] || ''),
                cleanText(cells[4] || ''),
                cleanText(cells[5] || ''),
                cleanText(cells[6] || ''),
                cleanText(cells[7] || ''),
                cleanText(cells[8] || ''),
                cleanText(cells[9] || ''),
                cleanText(cells[10] || ''),
                cleanText(cells[11] || ''),
                cleanText(cells[12] || ''),
                cleanText(cells[13] || cells[12] || ''),
                fullUrl
            ];
            rows.push(rowData);

            jsonList.push({
                player: cleanText(cells[0]),
                team: cleanText(cells[1]),
                season: cleanText(cells[2] || cells[1]),
                company: cleanText(cells[3] || ''),
                brand: cleanText(cells[4] || ''),
                theme: cleanText(cells[5] || ''),
                variant: cleanText(cells[6] || ''),
                cardNumber: cleanText(cells[7] || ''),
                serialNumber: cleanText(cells[8] || ''),
                printRun: cleanText(cells[9] || ''),
                isRookie: cleanText(cells[10] || ''),
                isPatch: cleanText(cells[11] || ''),
                isAutograph: cleanText(cells[12] || ''),
                grade: cleanText(cells[13] || ''),
                url: fullUrl
            });
        }
    });

    return { headers, rows, jsonList };
}

function exportCollectionCSV(customFilename) {
    const { headers, rows } = extractVisibleCardData();
    if (!rows || rows.length === 0) {
        alert("No cards found to export in the current view.");
        return;
    }

    let csvContent = "\uFEFF"; // UTF-8 BOM for Excel compatibility
    csvContent += headers.map(h => `"${h}"`).join(",") + "\r\n";
    rows.forEach(row => {
        csvContent += row.map(v => `"${v}"`).join(",") + "\r\n";
    });

    const timestamp = new Date().toISOString().slice(0, 10);
    const filename = customFilename || `Maulmann-Collection-${timestamp}.csv`;

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    setTimeout(() => {
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
    }, 100);
}

function exportCollectionJSON(customFilename) {
    const { jsonList } = extractVisibleCardData();
    if (!jsonList || jsonList.length === 0) {
        alert("No cards found to export in the current view.");
        return;
    }

    const timestamp = new Date().toISOString().slice(0, 10);
    const filename = customFilename || `Maulmann-Collection-${timestamp}.json`;

    const jsonStr = JSON.stringify(jsonList, null, 2);
    const blob = new Blob([jsonStr], { type: 'application/json;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    setTimeout(() => {
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
    }, 100);
}

// --- 12. INTERACTIVE WANTLIST & CARD SHOW BOUNTY CHECKLIST ---
const WANTLIST_KEY = 'maulmann_wantlist_acquired';

function getAcquiredWantlist() {
    try {
        const stored = localStorage.getItem(WANTLIST_KEY);
        return stored ? JSON.parse(stored) : [];
    } catch (e) {
        return [];
    }
}

function toggleWantlistCardAcquired(cardKey, checkbox) {
    let list = getAcquiredWantlist();
    const isChecked = checkbox ? checkbox.checked : !list.includes(cardKey);

    if (isChecked) {
        if (!list.includes(cardKey)) list.push(cardKey);
    } else {
        list = list.filter(k => k !== cardKey);
    }

    try {
        localStorage.setItem(WANTLIST_KEY, JSON.stringify(list));
    } catch (e) {}

    updateWantlistRowStyles();
}

function updateWantlistRowStyles() {
    const list = getAcquiredWantlist();
    const rows = document.querySelectorAll('.wantlist-row, .season-table-wrapper table tbody tr');
    let acquiredCount = 0;

    rows.forEach(row => {
        const key = row.getAttribute('data-wantlist-key');
        if (key) {
            const cb = row.querySelector('.wantlist-checkbox');
            const isAcquired = list.includes(key);
            if (cb) cb.checked = isAcquired;
            row.classList.toggle('wantlist-row-acquired', isAcquired);
            if (isAcquired) acquiredCount++;
        }
    });

    const badge = document.getElementById('wantlistAcquiredCount');
    if (badge) badge.innerText = `${acquiredCount}`;
}

function initWantlistChecklist() {
    const isWantlistPage = window.location.pathname.toLowerCase().includes('wantlist');
    if (!isWantlistPage) return;

    const rows = document.querySelectorAll('.season-table-wrapper table tbody tr, table tbody tr');
    if (!rows || rows.length === 0) return;

    const list = getAcquiredWantlist();

    rows.forEach((row, idx) => {
        if (row.querySelector('th')) return;
        const firstCell = row.querySelector('td:first-child');
        if (!firstCell) return;

        const cardText = row.textContent.trim().substring(0, 40).replace(/[^a-zA-Z0-9]/g, '-').toLowerCase();
        const key = `want-${idx}-${cardText}`;
        row.setAttribute('data-wantlist-key', key);

        if (!firstCell.querySelector('.wantlist-checkbox-wrap')) {
            const wrap = document.createElement('span');
            wrap.className = 'wantlist-checkbox-wrap';
            wrap.innerHTML = `<input type="checkbox" class="wantlist-checkbox" title="Mark as acquired/found" aria-label="Mark card as acquired">`;
            const cb = wrap.querySelector('input');
            cb.checked = list.includes(key);
            cb.addEventListener('change', () => toggleWantlistCardAcquired(key, cb));
            firstCell.prepend(wrap);
        }
    });

    updateWantlistRowStyles();
}

function printWantlist() {
    window.print();
}

// --- 13. EXPORT MENU DROPDOWN CONTROLLER ---
function toggleExportMenu(e) {
    if (e) {
        e.preventDefault();
        e.stopPropagation();
    }
    const menu = document.getElementById('exportMenuDropdown');
    const btn = document.getElementById('exportDropdownBtn');
    if (!menu) return;

    const isHidden = menu.style.display === 'none' || menu.style.display === '';
    menu.style.display = isHidden ? 'block' : 'none';
    if (btn) {
        btn.setAttribute('aria-expanded', isHidden ? 'true' : 'false');
    }
}

document.addEventListener('click', (e) => {
    const menu = document.getElementById('exportMenuDropdown');
    const btn = document.getElementById('exportDropdownBtn');
    if (menu && menu.style.display !== 'none' && !menu.contains(e.target) && e.target !== btn) {
        menu.style.display = 'none';
        if (btn) btn.setAttribute('aria-expanded', 'false');
    }
});

// Auto-collapse Analytics on mobile viewports (< 768px)
function initMobileAccordionState() {
    if (window.innerWidth <= 768) {
        const acc = document.getElementById('analyticsAccordion');
        if (acc && !sessionStorage.getItem('analyticsManuallyExpanded')) {
            acc.removeAttribute('open');
        }
    }
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initMobileAccordionState);
} else {
    initMobileAccordionState();
}

// Attach exports to window
window.exportCollectionCSV = exportCollectionCSV;
window.exportCollectionJSON = exportCollectionJSON;
window.printWantlist = printWantlist;
window.initWantlistChecklist = initWantlistChecklist;
window.toggleExportMenu = toggleExportMenu;

