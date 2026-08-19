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

    rows.forEach(row => {
        // Skip table headers
        if (row.querySelector('th')) return;
        if (!query) {
            row.style.display = '';
            return;
        }
        const text = row.textContent.toLowerCase();
        row.style.display = text.includes(query) ? '' : 'none';
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
    const btn = document.getElementById('compareBtn');
    if (!btn) return;

    const id = btn.getAttribute('data-card-id');
    const list = getCompareList();
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

function initCompareButtons() {
    const btn = document.getElementById('compareBtn');
    if (!btn) return;

    updateCompareButtonUI();

    if (!btn.dataset.listenerAttached) {
        btn.dataset.listenerAttached = 'true';
        btn.addEventListener('click', (e) => {
            e.preventDefault();
            const id = btn.getAttribute('data-card-id');
            const title = btn.getAttribute('data-card-title');
            const img = btn.getAttribute('data-card-img');
            const url = btn.getAttribute('data-card-url');
            const player = btn.getAttribute('data-card-player');
            const serial = btn.getAttribute('data-card-serial');

            toggleCompareCard({ id, title, img, url, player, serial });
        });
    }
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
    } else {
        if (list.length >= 3) {
            alert('You can compare up to 3 cards side-by-side.');
            return;
        }
        list.push({
            id: String(card.id),
            title: card.title || 'Card',
            img: normalizeCardImage(card.img),
            url: card.url || '',
            player: card.player || '',
            serial: card.serial || ''
        });
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
                <span>&#x2696;&#xFE0F; <strong>Card Comparison</strong> (<span id="compareCount">0</span>/3)</span>
                <div class="compare-bar-actions">
                    <button type="button" class="modern-button compare-now-btn" onclick="openCompareModal()">View Comparison</button>
                    <button type="button" class="modern-button secondary" onclick="clearCompareList()">Clear</button>
                </div>
            </div>
        `;
        document.body.appendChild(bar);
    }

    const countSpan = document.getElementById('compareCount');
    if (countSpan) countSpan.innerText = list.length;

    if (list.length > 0) {
        bar.style.display = 'block';
    } else {
        bar.style.display = 'none';
    }
}

function renderCompareModalGrid(list) {
    const grid = document.getElementById('compareModalGrid');
    if (!grid) return;

    if (!list || list.length === 0) {
        grid.innerHTML = '<p style="grid-column: 1 / -1; text-align: center; color: var(--text-muted); padding: 24px;">No cards currently selected for comparison.</p>';
        return;
    }

    grid.innerHTML = list.map(item => `
        <div class="compare-card-col" data-card-id="${escapeCompareHtml(item.id)}">
            <button type="button" class="remove-col-btn" title="Remove from comparison" aria-label="Remove card" onclick="removeCompareCard('${escapeCompareHtml(item.id)}')">&times;</button>
            <div class="compare-card-img-wrap">
                <img src="${escapeCompareHtml(normalizeCardImage(item.img))}" alt="${escapeCompareHtml(item.title)}" loading="lazy" decoding="async">
            </div>
            <h4>${escapeCompareHtml(item.title)}</h4>
            <div class="compare-card-meta">
                <p><strong>Player:</strong> ${escapeCompareHtml(item.player || '-')}</p>
                <p><strong>Serial:</strong> ${escapeCompareHtml(item.serial || '-')}</p>
            </div>
            <a href="${escapeCompareHtml(item.url || '#')}" class="modern-button view-detail-link">View Full Specs &rarr;</a>
        </div>
    `).join('');
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
        modal.setAttribute('aria-label', 'Side-by-Side Card Comparison');
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

// --- 5. REALISTIC 3D HOLOGRAPHIC CARD TILT EFFECT ---
function init3DCardTilt() {
    const cardContainers = document.querySelectorAll('.card-image-wrapper, .flip-container');
    if (!cardContainers || cardContainers.length === 0) return;

    cardContainers.forEach(container => {
        const targetCard = container.querySelector('.card-3d-interactive') || container.querySelector('.card-image');
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

                const rotateX = ((y - centerY) / centerY) * -8;
                const rotateY = ((x - centerX) / centerX) * 8;

                targetCard.style.transform = `perspective(1000px) rotateX(${rotateX.toFixed(2)}deg) rotateY(${rotateY.toFixed(2)}deg) scale3d(1.02, 1.02, 1.02)`;
            });
        });

        container.addEventListener('mouseleave', () => {
            if (tiltFrame) cancelAnimationFrame(tiltFrame);
            targetCard.style.transform = 'perspective(1000px) rotateX(0deg) rotateY(0deg) scale3d(1, 1, 1)';
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
