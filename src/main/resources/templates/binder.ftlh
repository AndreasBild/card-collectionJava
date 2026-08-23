<!DOCTYPE html>
<html lang="en">
<head>
    ${headHtml?no_esc}
    <#if jsonLd??>${jsonLd?no_esc}</#if>
</head>
<body class="binder-body">
{{CONSENT_BANNER}}
${topNavHtml?no_esc}

<main class="binder-main-view">
    <#if breadcrumbHtml??>${breadcrumbHtml?no_esc}</#if>

    <header class="binder-header">
        <span class="binder-badge">📖 Virtual Ultra-PRO Collection</span>
        <h1>3D 9-Pocket Collector's Binder</h1>
        <p class="sub-title">Flip through the complete Juwan Howard card vault in an authentic 9-pocket archival sleeve binder</p>
    </header>

    <!-- BINDER CONTROLS & STATUS BAR -->
    <div class="binder-toolbar">
        <div class="binder-pagination-info">
            <button type="button" class="modern-button binder-nav-btn" id="prevPageBtn" onclick="changeBinderPage(-1)" aria-label="Previous Binder Page">&laquo; Prev Page</button>
            <div class="page-indicator">
                <span>Page <strong id="currentPageNum">1</strong> of <strong id="totalPagesNum">${totalPages!1}</strong></span>
                <span class="page-sub-count">(${totalCards!0} Total Cards)</span>
            </div>
            <button type="button" class="modern-button binder-nav-btn" id="nextPageBtn" onclick="changeBinderPage(1)" aria-label="Next Binder Page">Next Page &raquo;</button>
        </div>

        <div class="binder-quick-jump">
            <label for="pageSelect" class="sr-only">Jump to page:</label>
            <select id="pageSelect" class="binder-select" onchange="jumpToPage(this.value)" aria-label="Jump directly to binder page">
                <#if binderPages??>
                    <#list binderPages as page>
                        <option value="${page?index + 1}">Page ${page?index + 1}</option>
                    </#list>
                </#if>
            </select>
            <button type="button" class="modern-button binder-tool-btn" onclick="flipAllVisibleCards()" title="Flip all cards on this page to back/front side">&#x1F504; Flip All Cards</button>
        </div>

        <div class="binder-shortcuts-hint" aria-label="Keyboard Navigation Shortcuts">
            <span class="shortcut-pill"><kbd>&larr;</kbd> / <kbd>&rarr;</kbd> Flip Page</span>
            <span class="shortcut-pill"><kbd>F</kbd> Flip Cards</span>
            <span class="shortcut-pill"><kbd>Home</kbd> / <kbd>End</kbd> First/Last</span>
        </div>
    </div>

    <!-- 3D BINDER CONTAINER -->
    <div class="binder-stage">
        <div class="binder-book" id="binderBook">
            <!-- Left Ring Spine -->
            <div class="binder-spine" aria-hidden="true">
                <div class="binder-ring ring-1"></div>
                <div class="binder-ring ring-2"></div>
                <div class="binder-ring ring-3"></div>
            </div>

            <!-- BINDER PAGES CONTAINER -->
            <div class="binder-pages-wrapper">
                <#if binderPages?? && (binderPages?size > 0)>
                    <#list binderPages as page>
                        <div class="binder-sheet <#if page?index == 0>active-sheet<#else>hidden-sheet</#if>" id="sheet-${page?index + 1}" data-page-index="${page?index + 1}">
                            <div class="pocket-grid-9">
                                <#list page as slot>
                                    <#if slot.isEmpty?? && slot.isEmpty>
                                        <div class="pocket-slot empty-pocket" aria-hidden="true">
                                            <div class="pocket-sleeve-weld"></div>
                                            <div class="empty-slot-watermark">
                                                <span class="empty-icon">🛡️</span>
                                                <span class="empty-text">Empty Sleeve</span>
                                            </div>
                                        </div>
                                    <#else>
                                        <div class="pocket-slot">
                                            <div class="pocket-sleeve-weld"></div>
                                            <div class="pocket-sleeve-glare" aria-hidden="true"></div>

                                            <!-- CARD FLIP CONTAINER INSIDE POCKET -->
                                            <div class="pocket-card-flip" onclick="togglePocketCardFlip(this)" title="Tap to flip card • Click icon to open details">
                                                <div class="pocket-card-inner">
                                                    <!-- FRONT FACE -->
                                                    <div class="pocket-card-front ${slot.frontOrientationClass!'is-portrait'}">
                                                        <picture class="pocket-picture">
                                                            <source type="image/avif" srcset="${slot.frontImg}">
                                                            <img src="${slot.frontImgFallback!slot.frontImg}" alt="${slot.title}" loading="lazy" decoding="async" class="pocket-card-img">
                                                        </picture>
                                                        <div class="pocket-card-overlay">
                                                            <#if slot.is1of1!false><span class="pocket-badge b-1of1">1/1</span></#if>
                                                            <#if slot.isAuto!false><span class="pocket-badge b-auto">AUTO</span></#if>
                                                            <#if slot.isPatch!false><span class="pocket-badge b-patch">MEM</span></#if>
                                                            <#if slot.isRookie!false><span class="pocket-badge b-rc">RC</span></#if>
                                                        </div>
                                                    </div>
                                                    <!-- BACK FACE -->
                                                    <div class="pocket-card-back ${slot.backOrientationClass!'is-portrait'}">
                                                        <picture class="pocket-picture">
                                                            <source type="image/avif" srcset="${slot.backImg}">
                                                            <img src="${slot.backImgFallback!slot.backImg}" alt="${slot.title} Back" loading="lazy" decoding="async" class="pocket-card-img">
                                                        </picture>
                                                    </div>
                                                </div>
                                            </div>

                                            <!-- ACTION BUTTONS HOVER -->
                                            <div class="pocket-actions">
                                                <a href="${slot.url}" class="pocket-detail-link" title="Open full detail view for ${slot.title}" onclick="event.stopPropagation()">
                                                    <span>🔍 Details</span>
                                                </a>
                                            </div>

                                            <div class="pocket-caption">
                                                <span class="caption-title">${slot.season} ${slot.brand}</span>
                                                <span class="caption-var">${slot.variant} <#if slot.serial?? && slot.serial != ''>&bull; ${slot.serial}</#if></span>
                                            </div>
                                        </div>
                                    </#if>
                                </#list>
                            </div>
                        </div>
                    </#list>
                <#else>
                    <p class="binder-empty-msg">No cards loaded in the binder.</p>
                </#if>
            </div>
        </div>
    </div>

    <!-- KEYBOARD & TOUCH HINT -->
    <p class="binder-help-hint">&#x2190; Use Left/Right Arrow Keys or Swipe to turn binder pages &bull; Tap cards to flip &#x2192;</p>
</main>

${footerHtml?no_esc}

<script>
    let currentBinderPage = 1;
    const totalBinderPages = ${totalPages!1};

    function updateBinderUI() {
        // Toggle visibility with smooth page transitions
        document.querySelectorAll('.binder-sheet').forEach(sheet => {
            const idx = parseInt(sheet.dataset.pageIndex, 10);
            if (idx === currentBinderPage) {
                sheet.classList.remove('hidden-sheet');
                sheet.classList.add('active-sheet');
            } else {
                sheet.classList.remove('active-sheet');
                sheet.classList.add('hidden-sheet');
            }
        });

        // Update indicators
        const currEl = document.getElementById('currentPageNum');
        if (currEl) currEl.textContent = currentBinderPage;

        const selectEl = document.getElementById('pageSelect');
        if (selectEl) selectEl.value = currentBinderPage;

        const prevBtn = document.getElementById('prevPageBtn');
        const nextBtn = document.getElementById('nextPageBtn');
        if (prevBtn) prevBtn.disabled = (currentBinderPage <= 1);
        if (nextBtn) nextBtn.disabled = (currentBinderPage >= totalBinderPages);

        // Smooth scroll to top of binder stage if scrolled far
        const stage = document.querySelector('.binder-stage');
        if (stage && window.scrollY > stage.offsetTop + 100) {
            stage.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        }
    }

    function changeBinderPage(delta) {
        const target = currentBinderPage + delta;
        if (target >= 1 && target <= totalBinderPages) {
            currentBinderPage = target;
            updateBinderUI();
        }
    }

    function jumpToPage(val) {
        const p = parseInt(val, 10);
        if (!isNaN(p) && p >= 1 && p <= totalBinderPages) {
            currentBinderPage = p;
            updateBinderUI();
        }
    }

    function togglePocketCardFlip(el) {
        el.classList.toggle('flipped');
    }

    function flipAllVisibleCards() {
        const activeSheet = document.querySelector('.binder-sheet.active-sheet');
        if (!activeSheet) return;
        const flips = activeSheet.querySelectorAll('.pocket-card-flip');
        const anyFlipped = Array.from(flips).some(f => f.classList.contains('flipped'));
        flips.forEach(f => {
            if (anyFlipped) {
                f.classList.remove('flipped');
            } else {
                f.classList.add('flipped');
            }
        });
    }

    // Keyboard Arrow Navigation
    document.addEventListener('keydown', (e) => {
        if (['INPUT', 'TEXTAREA', 'SELECT'].includes(e.target.tagName)) return;
        if (e.key === 'ArrowLeft') {
            changeBinderPage(-1);
        } else if (e.key === 'ArrowRight') {
            changeBinderPage(1);
        } else if (e.key === 'f' || e.key === 'F') {
            flipAllVisibleCards();
        } else if (e.key === 'Home') {
            currentBinderPage = 1;
            updateBinderUI();
        } else if (e.key === 'End') {
            currentBinderPage = totalBinderPages;
            updateBinderUI();
        }
    });

    // Touch Swipe Navigation for Mobile/Tablet
    let touchStartX = 0;
    let touchStartY = 0;

    const book = document.getElementById('binderBook');
    if (book) {
        book.addEventListener('touchstart', (e) => {
            if (e.touches.length > 0) {
                touchStartX = e.touches[0].clientX;
                touchStartY = e.touches[0].clientY;
            }
        }, { passive: true });

        book.addEventListener('touchend', (e) => {
            if (e.changedTouches.length > 0) {
                const diffX = e.changedTouches[0].clientX - touchStartX;
                const diffY = e.changedTouches[0].clientY - touchStartY;
                if (Math.abs(diffX) > 60 && Math.abs(diffX) > Math.abs(diffY) * 1.4) {
                    if (diffX > 0) {
                        changeBinderPage(-1); // Swipe Right -> Prev Page
                    } else {
                        changeBinderPage(1);  // Swipe Left -> Next Page
                    }
                }
            }
        }, { passive: true });
    }

    document.addEventListener('DOMContentLoaded', () => {
        updateBinderUI();
    });
</script>
</body>
</html>
