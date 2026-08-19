<!doctype html>
<html lang="en">
<head>
    <#-- PRELOAD LCP IMAGE FIRST: Placed before CSS/JS to maximize preload scanner efficiency -->
    <link rel="preload" as="image" href="${frontImgPath?replace('.avif', '-400w.avif')}" type="image/avif"
          imagesrcset="${frontImgPath?replace('.avif', '-400w.avif')} 400w, ${frontImgPath?replace('.avif', '-600w.avif')} 600w, ${frontImgPath?replace('.avif', '-900w.avif')} 900w, ${frontImgPath} 1200w"
          imagesizes="(max-width: 600px) 380px, (max-width: 1024px) 50vw, 580px"
          fetchpriority="high">
    ${headHtml?no_esc}
    ${jsonLd?no_esc}
    <#if prevLink??><link rel="prefetch" href="${prevLink}"></#if>
    <#if nextLink??><link rel="prefetch" href="${nextLink}"></#if>
</head>
<body>
{{CONSENT_BANNER}}
${topNavHtml?no_esc}
<main class="detail-main">
    <nav class="detail-nav" aria-label="Card navigation and actions">
        <div class="detail-nav-group detail-nav-navigation">
            <a id="backToOverview" title="Back To Collection Overview" href="../../${overviewPage}"
               class="detail-nav-btn detail-nav-back">
                <svg class="detail-nav-icon" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="19" y1="12" x2="5" y2="12"></line><polyline points="12 19 5 12 12 5"></polyline></svg>
                <span>Overview</span>
            </a>
            <div class="detail-nav-segmented">
                <#if prevLink??>
                    <a id="prevCardLink" title="Previous Card Details" href="${prevLink}"
                       class="detail-nav-btn segmented-btn segmented-prev">
                        <svg class="detail-nav-icon" viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="15 18 9 12 15 6"></polyline></svg>
                        <span>Prev</span>
                    </a>
                </#if>
                <#if nextLink??>
                    <a id="nextCardLink" title="Next Card Details" href="${nextLink}"
                       class="detail-nav-btn segmented-btn segmented-next">
                        <span>Next</span>
                        <svg class="detail-nav-icon" viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="9 18 15 12 9 6"></polyline></svg>
                    </a>
                </#if>
            </div>
        </div>
        <div class="detail-nav-group detail-nav-actions">
            <button id="favBtn" type="button" title="Add to favorites" class="detail-nav-btn detail-fav-btn" onclick="toggleDetailFav()">
                <span id="favStarIcon" class="fav-icon" aria-hidden="true">☆</span>
                <span>Favorite</span>
            </button>
            <button id="shareBtn" type="button" title="Share or copy card link" class="detail-nav-btn detail-share-btn">
                <svg class="detail-nav-icon" viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="18" cy="5" r="3"></circle><circle cx="6" cy="12" r="3"></circle><circle cx="18" cy="19" r="3"></circle><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"></line><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"></line></svg>
                <span>Share</span>
            </button>
            <button id="compareBtn" type="button" title="Compare this card with others" class="detail-nav-btn compare-toggle-btn" data-card-id="${cardStableId}" data-card-title="${h1Title}" data-card-img="${frontImgPath}" data-card-url="${fullCardUrl}" data-card-player="${player!""}" data-card-serial="${serialDisplay!""}">
                <svg class="detail-nav-icon" viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>
                <span>Compare</span>
            </button>
        </div>
    </nav>
    <#if breadcrumbHtml??>${breadcrumbHtml?no_esc}</#if>
    <header class="detail-header">
        <h1>${h1Html?no_esc}</h1>
    </header>
    <div class="ai-summary">
        <p><strong>Card Snapshot:</strong> ${aiSnapshotText?no_esc}</p>
    </div>
    <div class="card-images-container">
        <div class="card-image-wrapper">
            <div class="flip-container" onclick="toggleCardFlip()">
                <div class="flip-card-inner">
                    <div class="flip-face-front">
                        <figure>
                            <picture>
                                <source type="image/avif"
                                        srcset="${frontImgPath?replace('.avif', '-400w.avif')} 400w, ${frontImgPath?replace('.avif', '-600w.avif')} 600w, ${frontImgPath?replace('.avif', '-900w.avif')} 900w, ${frontImgPath} 1200w"
                                        sizes="(max-width: 600px) 380px, (max-width: 1024px) 50vw, 580px">
                                <img src="${frontImgPath?replace('.avif', '-400w.avif')}"
                                     width="600" height="840"
                                     alt="${frontAlt}" title="${frontImgTitle}"
                                     fetchpriority="high" loading="eager" decoding="async" class="zoomable-img card-3d-interactive">
                            </picture>
                            <figcaption>${frontAlt}</figcaption>
                        </figure>
                        <div class="foil-glare-effect" aria-hidden="true"></div>
                    </div>
                    <div class="flip-face-back">
                        <figure>
                            <picture>
                                <source type="image/avif"
                                        srcset="${backImgPath?replace('.avif', '-400w.avif')} 400w, ${backImgPath?replace('.avif', '-600w.avif')} 600w, ${backImgPath?replace('.avif', '-900w.avif')} 900w, ${backImgPath} 1200w"
                                        sizes="(max-width: 600px) 380px, (max-width: 1024px) 50vw, 580px">
                                <img src="${backImgPath?replace('.avif', '-400w.avif')}"
                                     width="600" height="840"
                                     alt="${backAlt}" title="${backImgTitle}" loading="lazy" decoding="async"
                                     class="zoomable-img card-3d-interactive">
                            </picture>
                            <figcaption>${backAlt}</figcaption>
                        </figure>
                        <div class="foil-glare-effect" aria-hidden="true"></div>
                    </div>
                </div>
                <div class="flip-icon-overlay"></div>
            </div>
            <div id="cardLoupe" class="card-grading-loupe" aria-hidden="true">
                <span class="loupe-badge">2X LOUPE</span>
                <div class="loupe-crosshair"></div>
            </div>
            <div class="card-action-bar">
                <button type="button" class="modern-button card-action-btn" id="loupeBtn" onclick="toggleLoupeMode()"><span class="icon">🔬</span> 2K Loupe</button>
                <button type="button" class="modern-button card-action-btn" id="popoutBtn" onclick="toggle3DPopout()"><span class="icon">⚡</span> 3D Pop-Out</button>
            </div>
            <p class="img-caption">&#x1F504; Tap Card to Flip</p>
        </div>
    </div>
    <div class="card-data">
        <table class="specs-table">
            <tr class="specs-table-header">
                <th colspan="2">Technical Specifications</th>
            </tr>
            <tr>
                <th class="specs-th">Player</th>
                <td class="specs-td">${player?no_esc}</td>
            </tr>
            <tr>
                <th class="specs-th">Season</th>
                <td class="specs-td">${season?no_esc}</td>
            </tr>
            <tr>
                <th class="specs-th">Team</th>
                <td class="specs-td">${team?no_esc}</td>
            </tr>
            <tr>
                <th class="specs-th">Manufacturer</th>
                <td class="specs-td">${company?no_esc}</td>
            </tr>
            <tr>
                <th class="specs-th">Brand</th>
                <td class="specs-td">${brand?no_esc}</td>
            </tr>
            <tr>
                <th class="specs-th">Theme</th>
                <td class="specs-td">${theme?no_esc}</td>
            </tr>
            <tr>
                <th class="specs-th">Variant</th>
                <td class="specs-td">${variant?no_esc}</td>
            </tr>
            <tr>
                <th class="specs-th">Card Number</th>
                <td class="specs-td">${number}</td>
            </tr>
            <tr>
                <th class="specs-th">Serial / Print Run</th>
                <td class="specs-td">${serialDisplay}</td>
            </tr>
            <tr>
                <th class="specs-th">Rookie Card</th>
                <td class="specs-td">${rookie}</td>
            </tr>
            <tr>
                <th class="specs-th">Memorabilia</th>
                <td class="specs-td">${memorabilia}</td>
            </tr>
            <tr>
                <th class="specs-th">Autograph</th>
                <td class="specs-td">${autograph}</td>
            </tr>
            <#if grading != "">
                <tr>
                    <th class="specs-th">Grading</th>
                    <td class="specs-td">${grading}</td>
                </tr>
            </#if>
        </table>
    </div>
    
    <section class="share-snippets-section context-card full-width">
        <h2>&#x1F4AC; Community & Forum Sharing</h2>
        <p class="share-subtitle">Instantly copy formatted BBCode or Markdown snippets to share this card scan on Blowout Cards, Reddit (r/sportscards), or Discord.</p>
        <div class="snippet-grid">
            <div class="snippet-group">
                <label for="bbcode-input"><strong>Blowout Cards / BBCode:</strong></label>
                <div class="snippet-input-wrapper">
                    <input type="text" id="bbcode-input" readonly value="${bbCode}">
                    <button class="modern-button copy-snippet-btn" onclick="copySnippet('bbcode-input', this)">Copy BBCode</button>
                </div>
            </div>
            <div class="snippet-group">
                <label for="markdown-input"><strong>Reddit / Markdown:</strong></label>
                <div class="snippet-input-wrapper">
                    <input type="text" id="markdown-input" readonly value="${markdownCode}">
                    <button class="modern-button copy-snippet-btn" onclick="copySnippet('markdown-input', this)">Copy Markdown</button>
                </div>
            </div>
            <div class="snippet-group">
                <label for="directurl-input"><strong>Direct Image Scan URL:</strong></label>
                <div class="snippet-input-wrapper">
                    <input type="text" id="directurl-input" readonly value="${fullImageUrl}">
                    <button class="modern-button copy-snippet-btn" onclick="copySnippet('directurl-input', this)">Copy Image Link</button>
                </div>
            </div>
        </div>
    </section>

    <#if (hobbyTrivia != "") || (techTrivia != "") || (playerHighlights != "") || (eraContext != "") || (cardBackText != "")>
        <section class="context-section">
            <h2 class="context-section-title">&#x1F4CA; Collector Insights & Card Highlights</h2>
            <div class="context-engine">
                <#if hobbyTrivia != "">
                    <div class="context-card">
                        <h3>&#x1F3C6; Hobby Significance</h3>
                        <p>${hobbyTrivia?no_esc}</p>
                    </div>
                </#if>

                <#if techTrivia != "">
                    <div class="context-card">
                        <h3>&#x2692;&#xFE0F; Card Technology</h3>
                        <p>${techTrivia?no_esc}</p>
                    </div>
                </#if>

                <#if playerHighlights != "">
                    <div class="context-card">
                        <h3>&#x1F4C8; Player Performance & Team</h3>
                        <p>${playerHighlights?no_esc}</p>
                    </div>
                </#if>

                <#if eraContext != "">
                    <div class="context-card">
                        <h3><#if eraTitle??>${eraTitle?no_esc}<#else>&#x1F3C0; Era & Pop Culture</#if></h3>
                        <p>${eraContext?no_esc}</p>
                    </div>
                </#if>

                <#if cardBackText != "">
                    <div class="card-back-text-container full-width">
                        <div class="card-back-text-header">
                            <span>&#x1F4DC; Original Card Back Transcription</span>
                            <span class="verified-badge">Verified via AI-OCR</span>
                        </div>
                        <pre class="card-back-content">${cardBackText}</pre>
                    </div>
                </#if>

                <#if externalLinks?? && (externalLinks?size > 0)>
                    <div class="context-card full-width external-references-card">
                        <h3>&#x1F517; Collector Reference Profiles & History</h3>
                        <div class="external-pills-wrapper">
                            <#list externalLinks as link>
                                <a href="${link.url}" target="_blank" rel="noopener" class="external-link-pill" title="Explore ${link.name}">
                                     ${link.name?no_esc} &nearr;
                                </a>
                            </#list>
                        </div>
                    </div>
                </#if>
            </div>
        </section>
    </#if>
    <#if relatedCards?? && (relatedCards?size > 0)>
        <section class="related-cards-section">
            <h2>&#x1F48E; Related Chase Cards in Collection</h2>
            <p class="related-cards-subtitle">Explore similar parallel variants, card technology, and basketball era highlights.</p>
            <div class="related-cards-grid">
                <#list relatedCards as card>
                    <a href="${card.url}" class="related-card-item" title="${card.title}" aria-label="${card.title}">
                        <div class="related-card-thumb-wrapper">
                            <figure>
                                <picture>
                                <source type="image/avif"
                                        srcset="${(card.imgBase)???then(card.imgBase, card.thumbAvif?replace('-400w.avif', ''))}-200w.avif 200w,
                                                ${(card.imgBase)???then(card.imgBase, card.thumbAvif?replace('-400w.avif', ''))}-400w.avif 400w,
                                                ${(card.imgBase)???then(card.imgBase, card.thumbAvif?replace('-400w.avif', ''))}-600w.avif 600w"
                                        sizes="(max-width: 600px) 140px, 180px">
                                <img src="${(card.imgBase)???then(card.imgBase + '-200w.avif', card.thumbAvif?replace('-400w.avif', '-200w.avif'))}"
                                     srcset="${(card.imgBase)???then(card.imgBase, card.thumbAvif?replace('-400w.avif', ''))}-200w.avif 200w,
                                             ${(card.imgBase)???then(card.imgBase, card.thumbAvif?replace('-400w.avif', ''))}-400w.avif 400w"
                                     sizes="(max-width: 600px) 140px, 180px"
                                     alt="${card.title}" loading="lazy" decoding="async" width="180" height="252" class="related-card-thumb">
                            </picture>
                                <figcaption>${card.title}</figcaption>
                            </figure>
                        </div>
                        <div class="related-card-info">
                            <span class="related-card-tag">${card.variant?no_esc}</span>
                            <h3 class="related-card-title">${card.title?no_esc}</h3>
                            <span class="related-card-meta">${card.season} &bull; ${card.brand}</span>
                        </div>
                    </a>
                </#list>
            </div>
        </section>
    </#if>
    <section class="vote-section">
        <h2>Collector Sentiment</h2>
        <div id="consent-needed-warning" class="consent-needed-warning" style="display: none; background: rgba(212, 175, 55, 0.12); border: 1px solid var(--accent-gold); color: var(--text-primary); padding: 16px; border-radius: var(--radius-md); margin: 0 auto 20px auto; max-width: 550px; text-align: center; font-size: 0.95rem;">
            ⚠️ <strong>Zustimmung erforderlich:</strong> Bitte stimme den Website-Einstellungen zu, um Karten zu bewerten und zu liken.
            <div style="margin-top: 12px; display: flex; gap: 10px; justify-content: center; flex-wrap: wrap;">
                <button id="inline-consent-accept" type="button" onclick="if(window.setConsent){window.setConsent('accepted');}else{localStorage.setItem('user_consent','accepted');const w=document.getElementById('consent-needed-warning');if(w)w.style.display='none';window.dispatchEvent(new Event('consent-given'));}" class="modern-button confirm-btn" style="padding: 6px 18px; font-size: 0.9rem;" title="Zustimmen">Zustimmen</button>
                <button id="inline-consent-reject" type="button" onclick="if(window.setConsent){window.setConsent('rejected');}else{localStorage.setItem('user_consent','rejected');const w=document.getElementById('consent-needed-warning');if(w)w.style.display='none';}" class="modern-button cancel-btn" style="padding: 6px 18px; font-size: 0.9rem;" title="Ablehnen">Ablehnen</button>
            </div>
        </div>
        <div class="vote-container" data-card-id="${cardId}">
            <button id="like-btn" title="I like this card" class="vote-btn like">
                <span class="icon">👍</span> <span class="label">Like</span> <span id="like-count" class="count">0</span>
            </button>
            <button id="dislike-btn" title="I dislike this card" class="vote-btn dislike">
                <span class="icon">👎</span> <span class="label">Dislike</span> <span id="dislike-count"
                                                                                     class="count">0</span>
            </button>
        </div>
        <div class="rating-stars-wrapper">
            <h3>Rate this Card</h3>
            <div class="rating-scale-legend">
                <span class="scale-point min-point">← 0.5 ★ (Lowest)</span>
                <span class="scale-point max-point">5.0 ★ (Highest) →</span>
            </div>
            <div class="rating-interaction-container">
                <div class="star-rating" id="interactive-stars">
                    <input type="radio" id="star5" name="rating" value="5"/><label class="full" for="star5"
                                                                                   title="5.0 ★ (Exceptional - Highest Rating)"></label>
                    <input type="radio" id="star4half" name="rating" value="4.5"/><label class="half" for="star4half"
                                                                                         title="4.5 ★ (Very Good)"></label>
                    <input type="radio" id="star4" name="rating" value="4"/><label class="full" for="star4"
                                                                                   title="4.0 ★ (Good)"></label>
                    <input type="radio" id="star3half" name="rating" value="3.5"/><label class="half" for="star3half"
                                                                                         title="3.5 ★ (Nice)"></label>
                    <input type="radio" id="star3" name="rating" value="3"/><label class="full" for="star3"
                                                                                   title="3.0 ★ (Average)"></label>
                    <input type="radio" id="star2half" name="rating" value="2.5"/><label class="half" for="star2half"
                                                                                         title="2.5 ★ (Ok)"></label>
                    <input type="radio" id="star2" name="rating" value="2"/><label class="full" for="star2"
                                                                                   title="2.0 ★ (Acceptable)"></label>
                    <input type="radio" id="star1half" name="rating" value="1.5"/><label class="half" for="star1half"
                                                                                         title="1.5 ★ (Not Good)"></label>
                    <input type="radio" id="star1" name="rating" value="1"/><label class="full" for="star1"
                                                                                   title="1.0 ★ (Poor)"></label>
                    <input type="radio" id="star0half" name="rating" value="0.5"/><label class="half" for="star0half"
                                                                                         title="0.5 ★ (Very Poor - Lowest Rating)"></label>
                </div>
                <div id="rating-value-label" class="rating-label">Hover stars (0.5 = Lowest, 5.0 = Highest)</div>
            </div>
            <div id="rating-confirmation" class="rating-confirmation" style="display: none;">
                <p>Selected: <span id="selected-rating-text" class="highlight-rating-text">5.0</span> Stars (<span id="selected-rating-desc">Exceptional</span>). Confirm submission?</p>
                <div class="confirmation-buttons">
                    <button id="submit-rating-btn" class="modern-button confirm-btn" title="Submit Rating">Submit Rating</button>
                    <button id="cancel-rating-btn" class="cancel-btn" title="Cancel Rating">Cancel</button>
                </div>
            </div>
            <div class="aggregate-rating">
                <div class="aggregate-stars-container">
                    <div class="stars-outer">
                        <div class="stars-inner" id="stars-inner-width"></div>
                    </div>
                    <span class="rating-number"><span id="average-rating">0.0</span> / 5.0</span>
                </div>
                <p class="rating-count-text">(<span id="rating-count">0</span> Collector Ratings)</p>
            </div>
        </div>
    </section>
    <section class="faq-section" id="faq-section">
        <h2>Collector FAQs</h2>
        ${faqHtml?no_esc}
    </section>
    <#if (sameBrandCards?? && (sameBrandCards?size > 0)) || (sameCompanyCards?? && (sameCompanyCards?size > 0))>
    <section class="seo-showcase-footer" aria-label="Related Brand & Manufacturer Cards">
        <div class="seo-showcase-container">
            <div class="seo-showcase-grid">
                <#if sameBrandCards?? && (sameBrandCards?size > 0)>
                <div class="seo-showcase-col">
                    <h2 class="seo-showcase-title">${sameBrandTitle}</h2>
                    <ul class="seo-showcase-list">
                        <#list sameBrandCards as item>
                        <li class="seo-showcase-item">
                            <a href="${item.url}" title="View details for ${item.title}" class="plain seo-showcase-link">${item.title}</a>
                        </li>
                        </#list>
                    </ul>
                </div>
                </#if>
                <#if sameCompanyCards?? && (sameCompanyCards?size > 0)>
                <div class="seo-showcase-col">
                    <h2 class="seo-showcase-title">${sameCompanyTitle}</h2>
                    <ul class="seo-showcase-list">
                        <#list sameCompanyCards as item>
                        <li class="seo-showcase-item">
                            <a href="${item.url}" title="View details for ${item.title}" class="plain seo-showcase-link">${item.title}</a>
                        </li>
                        </#list>
                    </ul>
                </div>
                </#if>
            </div>
        </div>
    </section>
    </#if>
    ${footerHtml?no_esc}
</main>
<div id="toastNotification" class="toast-notification" aria-live="polite">
    <span class="toast-icon">✨</span>
    <span id="toastMessage" class="toast-text">Link copied to clipboard!</span>
</div>
<script>
    const flipInner = document.querySelector(".flip-card-inner");

    const highResFront = "${frontImgPath}";
    const highResBack = "${backImgPath}";

    function triggerHaptic(ms = 15) {
        if (typeof navigator !== 'undefined' && navigator.vibrate) {
            try { navigator.vibrate(ms); } catch (e) {}
        }
    }

    function toggleCardFlip() {
        triggerHaptic(12);
        if (flipInner) {
            flipInner.classList.toggle("flipped");
        }
    }

    function showToast(message) {
        const toast = document.getElementById("toastNotification");
        const toastMsg = document.getElementById("toastMessage");
        if (!toast) return;
        if (toastMsg) toastMsg.textContent = message;
        toast.classList.add("toast-show");
        setTimeout(() => {
            toast.classList.remove("toast-show");
        }, 2500);
    }

    const shareBtn = document.getElementById('shareBtn');
    if (shareBtn) {
        shareBtn.addEventListener('click', async () => {
            triggerHaptic(15);
            const h1 = document.querySelector('h1');
            const shareTitle = document.title;
            const shareText = 'Check out this rare Juwan Howard card: ' + (h1 ? h1.textContent : document.title);
            const shareUrl = window.location.href;

            if (navigator.share) {
                try {
                    await navigator.share({ title: shareTitle, text: shareText, url: shareUrl });
                    return;
                } catch (e) {
                    if (e.name === 'AbortError') return;
                }
            }

            try {
                await navigator.clipboard.writeText(shareUrl);
                showToast("🔗 Link copied to clipboard!");
            } catch (err) {
                showToast("Card URL: " + shareUrl);
            }
        });
    }

    // --- FAVORITES (WATCHLIST) SYNC ---
    const FAVORITES_KEY = 'maulmann_card_favorites';
    const cardStableId = "${cardStableId}";

    function getFavorites() {
        try {
            const data = localStorage.getItem(FAVORITES_KEY);
            return data ? JSON.parse(data) : [];
        } catch (e) {
            return [];
        }
    }

    function getCardIdentifiers() {
        const pathParts = window.location.pathname.split('/');
        const filename = pathParts[pathParts.length - 1];
        const filenameBase = filename.replace('.html', '');
        return [cardStableId, filenameBase];
    }

    function updateDetailFavUI() {
        const favs = getFavorites();
        const ids = getCardIdentifiers();
        const isFav = ids.some(id => favs.includes(id));
        const favBtn = document.getElementById('favBtn');
        const favIcon = document.getElementById('favStarIcon');
        if (!favBtn) return;
        if (isFav) {
            favBtn.classList.add('is-fav');
            if (favIcon) favIcon.textContent = '★';
            favBtn.title = 'Remove from favorites';
        } else {
            favBtn.classList.remove('is-fav');
            if (favIcon) favIcon.textContent = '☆';
            favBtn.title = 'Add to favorites';
        }
    }

    window.toggleDetailFav = function() {
        triggerHaptic(20);
        let favs = getFavorites();
        const ids = getCardIdentifiers();
        const isFav = ids.some(id => favs.includes(id));
        if (isFav) {
            favs = favs.filter(id => !ids.includes(id));
            showToast("Removed from favorites");
        } else {
            ids.forEach(id => {
                if (id && !favs.includes(id)) favs.push(id);
            });
            showToast("⭐ Added to favorites!");
        }
        try {
            localStorage.setItem(FAVORITES_KEY, JSON.stringify(favs));
        } catch (e) {}
        updateDetailFavUI();
        if (typeof navigator !== 'undefined' && navigator.setAppBadge) {
            try {
                if (favs.length > 0) navigator.setAppBadge(favs.length);
                else if (navigator.clearAppBadge) navigator.clearAppBadge();
            } catch (e) {}
        }
    };

    // --- HOLOGRAPHIC FOIL & 3D TILT WITH GYROSCOPE ---
    const flipContainer = document.querySelector('.flip-container');
    let isLoupeActive = false;
    let isPopoutActive = false;

    function toggle3DPopout() {
        isPopoutActive = !isPopoutActive;
        const popoutBtn = document.getElementById('popoutBtn');
        if (!flipContainer || !popoutBtn) return;

        if (isPopoutActive) {
            flipContainer.classList.add('is-3d-popout');
            popoutBtn.classList.add('active-tool');
            popoutBtn.innerHTML = '<span class="icon">⚡</span> 3D Pop-Out: On';
            showToast("⚡ 3D Pop-Out active: Card floats with 3D shadowbox depth");
            triggerHaptic(20);
        } else {
            flipContainer.classList.remove('is-3d-popout');
            popoutBtn.classList.remove('active-tool');
            popoutBtn.innerHTML = '<span class="icon">⚡</span> 3D Pop-Out';
            showToast("3D Pop-Out disabled");
            triggerHaptic(12);
        }
    }

    function updateFoilAndTilt(percentX, percentY, rotX, rotY) {
        if (isLoupeActive) return;
        document.querySelectorAll('.foil-glare-effect').forEach(foil => {
            foil.style.setProperty('--foil-x', percentX + '%');
            foil.style.setProperty('--foil-y', percentY + '%');
            foil.style.setProperty('--foil-opacity', '0.35');
        });
        if (flipContainer && flipInner && !flipInner.classList.contains('flipped')) {
            const scale = isPopoutActive ? 1.045 : 1.025;
            flipContainer.style.transform = "perspective(1000px) rotateX(" + rotX + "deg) rotateY(" + rotY + "deg) scale3d(" + scale + ", " + scale + ", " + scale + ")";
        }
    }

    function resetFoilAndTilt() {
        document.querySelectorAll('.foil-glare-effect').forEach(foil => {
            foil.style.setProperty('--foil-opacity', '0');
        });
        if (flipContainer) {
            flipContainer.style.transform = '';
        }
    }

    let motionPermissionRequested = false;
    let gyroActive = false;

    function handleDeviceOrientation(e) {
        if (isLoupeActive || e.gamma === null || e.beta === null) return;
        // Range mapping: gamma [-35, 35] & beta [10, 80] relative to 45 deg resting angle
        const clampGamma = Math.max(-35, Math.min(35, e.gamma));
        const clampBeta = Math.max(10, Math.min(80, e.beta)) - 45;
        const percentX = ((clampGamma + 35) / 70) * 100;
        const percentY = ((clampBeta + 35) / 70) * 100;
        const rotY = (clampGamma / 35) * 14;
        const rotX = -(clampBeta / 35) * 14;
        updateFoilAndTilt(percentX, percentY, rotX, rotY);
    }

    function initGyroscope() {
        if (typeof DeviceOrientationEvent !== 'undefined' && typeof DeviceOrientationEvent.requestPermission !== 'function') {
            window.addEventListener('deviceorientation', handleDeviceOrientation, { passive: true });
        }
    }

    if (flipContainer) {
        flipContainer.addEventListener('pointermove', (e) => {
            if (isLoupeActive) return;
            initGyroscope();
            const rect = flipContainer.getBoundingClientRect();
            const x = e.clientX - rect.left;
            const y = e.clientY - rect.top;
            const percentX = (x / rect.width) * 100;
            const percentY = (y / rect.height) * 100;
            const rotX = -((y - rect.height / 2) / (rect.height / 2)) * 12;
            const rotY = ((x - rect.width / 2) / (rect.width / 2)) * 12;
            updateFoilAndTilt(percentX, percentY, rotX, rotY);
        });

        flipContainer.addEventListener('touchmove', (e) => {
            if (isLoupeActive) return;
            initGyroscope();
            const rect = flipContainer.getBoundingClientRect();
            if (e.touches && e.touches.length > 0) {
                const x = e.touches[0].clientX - rect.left;
                const y = e.touches[0].clientY - rect.top;
                const percentX = Math.max(0, Math.min(100, (x / rect.width) * 100));
                const percentY = Math.max(0, Math.min(100, (y / rect.height) * 100));
                const rotX = -((y - rect.height / 2) / (rect.height / 2)) * 12;
                const rotY = ((x - rect.width / 2) / (rect.width / 2)) * 12;
                updateFoilAndTilt(percentX, percentY, rotX, rotY);
            }
        }, { passive: true });

        flipContainer.addEventListener('pointerleave', () => {
            if (!gyroActive && !motionPermissionRequested) {
                resetFoilAndTilt();
            }
        });

        flipContainer.addEventListener('click', () => {
            initGyroscope();
        }, { passive: true });
    }

    // Auto-init for Android and non-permission browsers
    if (typeof DeviceOrientationEvent !== 'undefined' && typeof DeviceOrientationEvent.requestPermission !== 'function') {
        window.addEventListener('deviceorientation', handleDeviceOrientation, { passive: true });
    }

    // --- 2K GRADING LOUPE ---
    function toggleLoupeMode() {
        isLoupeActive = !isLoupeActive;
        const loupe = document.getElementById('cardLoupe');
        const loupeBtn = document.getElementById('loupeBtn');
        if (!loupe || !loupeBtn) return;

        if (isLoupeActive) {
            resetFoilAndTilt();
            loupeBtn.classList.add('active-tool');
            loupeBtn.innerHTML = '<span class="icon">✕</span> Exit Loupe';
            if (flipContainer) flipContainer.classList.add('loupe-mode-active');
            showToast("🔬 2K Loupe: Drag on card to inspect • Tap Exit Loupe to close");
        } else {
            loupe.style.display = 'none';
            loupeBtn.classList.remove('active-tool');
            loupeBtn.innerHTML = '<span class="icon">🔬</span> 2K Loupe';
            if (flipContainer) flipContainer.classList.remove('loupe-mode-active');
            showToast("Loupe closed");
        }
    }

    function handleLoupeMove(e) {
        if (!isLoupeActive) return;
        if (e.cancelable) {
            e.preventDefault();
        }
        const loupe = document.getElementById('cardLoupe');
        const wrapper = document.querySelector('.card-image-wrapper');
        const isFlipped = flipInner && flipInner.classList.contains("flipped");
        const activeImgEl = isFlipped 
            ? document.querySelector('.flip-face-back img') 
            : document.querySelector('.flip-face-front img');

        if (!loupe || !wrapper || !activeImgEl) return;

        const imgRect = activeImgEl.getBoundingClientRect();
        const wrapperRect = wrapper.getBoundingClientRect();

        const clientX = e.touches && e.touches.length > 0 ? e.touches[0].clientX : e.clientX;
        const clientY = e.touches && e.touches.length > 0 ? e.touches[0].clientY : e.clientY;

        // Hide loupe if finger/cursor is outside card
        const margin = 30;
        if (clientX < imgRect.left - margin || clientX > imgRect.right + margin ||
            clientY < imgRect.top - margin || clientY > imgRect.bottom + margin) {
            loupe.style.display = 'none';
            return;
        }

        loupe.style.display = 'block';
        const x = clientX - imgRect.left;
        const y = clientY - imgRect.top;

        const activeImg = isFlipped ? highResBack : highResFront;
        const zoom = 2.0;
        const loupeRadius = 75; // half of 150px lens diameter

        loupe.style.left = (clientX - wrapperRect.left) + 'px';
        loupe.style.top = (clientY - wrapperRect.top) + 'px';
        loupe.style.backgroundImage = "url('" + activeImg + "')";
        loupe.style.backgroundSize = (imgRect.width * zoom) + 'px ' + (imgRect.height * zoom) + 'px';
        loupe.style.backgroundPosition = (-(x * zoom - loupeRadius)) + 'px ' + (-(y * zoom - loupeRadius)) + 'px';
    }

    if (flipContainer) {
        flipContainer.addEventListener('mousemove', (e) => {
            if (isLoupeActive) handleLoupeMove(e);
        });
        flipContainer.addEventListener('touchstart', (e) => {
            if (isLoupeActive) {
                if (e.cancelable) e.preventDefault();
                handleLoupeMove(e);
            }
        }, { passive: false });
        flipContainer.addEventListener('touchmove', (e) => {
            if (isLoupeActive) {
                if (e.cancelable) e.preventDefault();
                handleLoupeMove(e);
            }
        }, { passive: false });
        flipContainer.addEventListener('mouseleave', () => {
            if (isLoupeActive) {
                const loupe = document.getElementById('cardLoupe');
                if (loupe) loupe.style.display = 'none';
            }
        });
        flipContainer.addEventListener('touchend', () => {
            if (isLoupeActive) {
                const loupe = document.getElementById('cardLoupe');
                if (loupe) loupe.style.display = 'none';
            }
        });
    }

    document.addEventListener('DOMContentLoaded', () => {
        updateDetailFavUI();

        const pathParts = window.location.pathname.split('/');
        const filename = pathParts[pathParts.length - 1];
        if (filename && filename.endsWith('.html')) {
            const cardId = filename.replace('.html', '');
            const backLink = document.getElementById('backToOverview');
            if (backLink) {
                backLink.href += '#' + cardId;
            }
        }

        let touchStartX = 0;
        let touchEndX = 0;
        let touchStartY = 0;
        let touchEndY = 0;

        document.addEventListener('touchstart', (e) => {
            if (isLoupeActive) return;
            // Do NOT trigger swipe when touching inside the card image wrapper, loupe, or interactive buttons
            if (e.target.closest('.card-images-container') || e.target.closest('button') || e.target.closest('a') || e.target.closest('#cardLoupe')) return;
            if (e.touches.length > 0) {
                touchStartX = e.touches[0].clientX;
                touchStartY = e.touches[0].clientY;
            }
        }, { passive: true });

        document.addEventListener('touchend', (e) => {
            if (isLoupeActive) return;
            if (e.target.closest('.card-images-container') || e.target.closest('button') || e.target.closest('a') || e.target.closest('#cardLoupe')) return;
            if (e.changedTouches.length > 0) {
                touchEndX = e.changedTouches[0].clientX;
                touchEndY = e.changedTouches[0].clientY;
                const diffX = touchEndX - touchStartX;
                const diffY = touchEndY - touchStartY;
                // Only trigger if horizontal swipe is intentional and greater than vertical scroll
                if (Math.abs(diffX) > 70 && Math.abs(diffX) > Math.abs(diffY) * 1.5) {
                    if (diffX > 0) {
                        const prevLink = document.getElementById('prevCardLink');
                        if (prevLink) window.location.href = prevLink.href;
                    } else {
                        const nextLink = document.getElementById('nextCardLink');
                        if (nextLink) window.location.href = nextLink.href;
                    }
                }
            }
        }, { passive: true });
    });

    document.addEventListener('keydown', function (event) {
        if (event.target && ['INPUT', 'TEXTAREA', 'SELECT'].includes(event.target.tagName)) return;
        if (event.key === "Escape") {
            if (isLoupeActive) toggleLoupeMode();
        }
        if (event.key === "ArrowLeft") {
            const prevLink = document.getElementById('prevCardLink');
            if (prevLink) window.location.href = prevLink.href;
        }
        if (event.key === "ArrowRight") {
            const nextLink = document.getElementById('nextCardLink');
            if (nextLink) window.location.href = nextLink.href;
        }
        if (event.key === "f" || event.key === "F") {
            toggleCardFlip();
        }
    });
</script>
<script type="module">
    import { initializeApp, getApps, getApp } from "https://www.gstatic.com/firebasejs/10.7.1/firebase-app.js";
    import {
        getFirestore,
        doc,
        setDoc,
        increment,
        onSnapshot
    } from "https://www.gstatic.com/firebasejs/10.7.1/firebase-firestore.js";

    const firebaseConfig = {
        apiKey: "AIzaSyCtPPb0fIt3kaKBwDvvBFpVUkyt8-nR2NE",
        authDomain: "maulmann-3f90d.firebaseapp.com",
        projectId: "maulmann-3f90d",
        storageBucket: "maulmann-3f90d.firebasestorage.app",
        messagingSenderId: "252806218272",
        appId: "1:252806218272:web:c38637e537dc958f3eb245",
        measurementId: "G-78BKZH6R67"
    };

    let app;
    if (!getApps().length) {
        app = initializeApp(firebaseConfig);
    } else {
        app = getApp();
    }

    let db;
    try {
        db = getFirestore(app);
    } catch (e) {
        console.warn("Could not initialize Firestore:", e);
    }

    function getStorage(key) {
        try { return localStorage.getItem(key); } catch (e) { return null; }
    }

    function setStorage(key, val) {
        try { localStorage.setItem(key, val); } catch (e) {}
    }

    const cardId = "${cardId}";
    const likeBtn = document.getElementById('like-btn');
    const dislikeBtn = document.getElementById('dislike-btn');
    const likeCountSpan = document.getElementById('like-count');
    const dislikeCountSpan = document.getElementById('dislike-count');

    const averageRatingSpan = document.getElementById('average-rating');
    const ratingCountSpan = document.getElementById('rating-count');
    const starsInner = document.getElementById('stars-inner-width');
    const starInputs = document.querySelectorAll('.star-rating input');
    const starLabels = document.querySelectorAll('.star-rating label');
    const ratingLabel = document.getElementById('rating-value-label');
    const ratingConfirmation = document.getElementById('rating-confirmation');
    const selectedRatingText = document.getElementById('selected-rating-text');
    const submitRatingBtn = document.getElementById('submit-rating-btn');
    const cancelRatingBtn = document.getElementById('cancel-rating-btn');

    const localStorageKey = `voted_${cardId}`;
    const localStorageRatingKey = `voted_rating_${cardId}`;

    let pendingRating = null;

    if (likeBtn && dislikeBtn && getStorage(localStorageKey)) {
        likeBtn.disabled = true;
        dislikeBtn.disabled = true;
        likeBtn.title = "You have already voted on this card";
        dislikeBtn.title = "You have already voted on this card";
    }

    if (getStorage(localStorageRatingKey)) {
        disableStarInteraction();
    }

    function disableStarInteraction() {
        starInputs.forEach(input => input.disabled = true);
        if (ratingLabel) {
            ratingLabel.textContent = "Thank you for rating!";
            ratingLabel.style.color = "#3a7504";
        }
    }

    if (db) {
        try {
            const cardRef = doc(db, "Trading_cards", cardId);

            onSnapshot(cardRef, (docSnap) => {
                if (docSnap && docSnap.exists()) {
                    const data = docSnap.data();
                    if (likeCountSpan) likeCountSpan.textContent = data.likes || 0;
                    if (dislikeCountSpan) dislikeCountSpan.textContent = data.dislikes || 0;

                    const sum = data.ratingSum || 0;
                    const count = data.ratingCount || 0;
                    const average = count > 0 ? (sum / count).toFixed(1) : "0.0";

                    if (averageRatingSpan) averageRatingSpan.textContent = average;
                    if (ratingCountSpan) ratingCountSpan.textContent = count;
                    if (starsInner) {
                        const visualPercentage = (parseFloat(average) / 5) * 100;
                        starsInner.style.width = visualPercentage + "%";
                    }
                }
            }, () => {});
        } catch (e) {}
    }

    let pendingVoteType = null;

    window.addEventListener('consent-given', () => {
        const warningEl = document.getElementById('consent-needed-warning');
        if (warningEl) warningEl.style.display = 'none';

        if (pendingRating) {
            submitRating();
        } else if (pendingVoteType) {
            handleVote(pendingVoteType);
            pendingVoteType = null;
        }
    });

    function checkConsent() {
        if (getStorage('user_consent') !== 'accepted') {
            if (window.showConsentBanner) {
                window.showConsentBanner();
            }
            const warningEl = document.getElementById('consent-needed-warning');
            if (warningEl) {
                warningEl.style.display = 'block';
                warningEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
            }
            return false;
        }
        return true;
    }

    async function handleVote(type) {
        if (!db || getStorage(localStorageKey)) return;
        pendingVoteType = type;
        if (!checkConsent()) return;

        try {
            const cardRef = doc(db, "Trading_cards", cardId);
            await setDoc(cardRef, {
                likes: type === 'likes' ? increment(1) : increment(0),
                dislikes: type === 'dislikes' ? increment(1) : increment(0),
                ratingCount: increment(0),
                ratingSum: increment(0)
            }, {merge: true});

            setStorage(localStorageKey, 'true');
            pendingVoteType = null;
            if (likeBtn && dislikeBtn) {
                likeBtn.disabled = true;
                dislikeBtn.disabled = true;
                likeBtn.title = "You have already voted on this card";
                dislikeBtn.title = "You have already voted on this card";
            }
        } catch (error) {
            alert("Could not submit your vote. Please check your network connection or ad-blocker settings.");
        }
    }

    async function submitRating() {
        if (!db || !pendingRating || getStorage(localStorageRatingKey)) return;
        if (!checkConsent()) return;

        try {
            const cardRef = doc(db, "Trading_cards", cardId);
            await setDoc(cardRef, {
                likes: increment(0),
                dislikes: increment(0),
                ratingCount: increment(1),
                ratingSum: increment(parseFloat(pendingRating))
            }, {merge: true});

            setStorage(localStorageRatingKey, 'true');
            disableStarInteraction();
            if (ratingConfirmation) ratingConfirmation.style.display = 'none';
            pendingRating = null;
        } catch (error) {
            alert("Could not submit your rating. Please check your network connection or ad-blocker settings.");
        }
    }

    if (likeBtn) likeBtn.addEventListener('click', () => handleVote('likes'));
    if (dislikeBtn) dislikeBtn.addEventListener('click', () => handleVote('dislikes'));

    const ratingDescMap = {
        '5': 'Exceptional - Highest Rating',
        '4.5': 'Very Good',
        '4': 'Good',
        '3.5': 'Nice',
        '3': 'Average',
        '2.5': 'Ok',
        '2': 'Acceptable',
        '1.5': 'Not Good',
        '1': 'Poor',
        '0.5': 'Very Poor - LOWEST RATING'
    };

    starLabels.forEach(label => {
        label.addEventListener('mouseenter', (e) => {
            if (!localStorage.getItem(localStorageRatingKey) && ratingLabel) {
                ratingLabel.textContent = e.target.title;
            }
        });
    });

    const interactiveStars = document.getElementById('interactive-stars');
    if (interactiveStars) {
        interactiveStars.addEventListener('mouseleave', () => {
            if (!localStorage.getItem(localStorageRatingKey) && ratingLabel) {
                ratingLabel.textContent = "Hover stars (0.5 = Lowest, 5.0 = Highest)";
            }
        });
    }

    const selectedRatingDesc = document.getElementById('selected-rating-desc');

    starInputs.forEach(input => {
        input.addEventListener('change', (e) => {
            if (localStorage.getItem(localStorageRatingKey)) return;
            pendingRating = e.target.value;
            if (selectedRatingText) selectedRatingText.textContent = pendingRating;
            if (selectedRatingDesc) selectedRatingDesc.textContent = ratingDescMap[pendingRating] || "";
            if (ratingLabel) ratingLabel.textContent = e.target.title;
            if (ratingConfirmation) {
                ratingConfirmation.style.display = 'block';
                ratingConfirmation.scrollIntoView({behavior: 'smooth', block: 'nearest'});
            }
        });
    });

    if (submitRatingBtn) submitRatingBtn.addEventListener('click', submitRating);
    if (cancelRatingBtn) {
        cancelRatingBtn.addEventListener('click', () => {
            if (ratingConfirmation) ratingConfirmation.style.display = 'none';
            pendingRating = null;
            starInputs.forEach(input => input.checked = false);
            if (ratingLabel) ratingLabel.textContent = "Hover stars (0.5 = Lowest, 5.0 = Highest)";
        });
    }
</script>
<script>
    document.addEventListener('DOMContentLoaded', () => {
        const flipContainer = document.querySelector('.flip-container');
        if (!flipContainer) return;

        const frontImg = flipContainer.querySelector('.flip-face-front img');
        const backImg = flipContainer.querySelector('.flip-face-back img');

        if (!frontImg) return;

        const getOri = (img) => (img && img.naturalWidth && img.naturalHeight && img.naturalWidth > img.naturalHeight) ? 'landscape' : 'portrait';

        const updateFrontOrientation = () => {
            if (frontImg.naturalWidth) {
                const frontOri = getOri(frontImg);
                if (!flipContainer.classList.contains('is-flipped')) {
                    flipContainer.classList.remove('is-portrait', 'is-landscape');
                    flipContainer.classList.add('is-' + frontOri);
                }
            }
        };

        const updateBackOrientation = () => {
            if (backImg && backImg.naturalWidth) {
                const backOri = getOri(backImg);
                if (flipContainer.classList.contains('is-flipped')) {
                    flipContainer.classList.remove('is-portrait', 'is-landscape');
                    flipContainer.classList.add('is-' + backOri);
                }
            }
        };

        if (frontImg.complete && frontImg.naturalWidth) updateFrontOrientation();
        else frontImg.addEventListener('load', updateFrontOrientation);

        if (backImg) {
            if (backImg.complete && backImg.naturalWidth) updateBackOrientation();
            else backImg.addEventListener('load', updateBackOrientation);
        }

        flipContainer.addEventListener('click', () => {
            const isFlipped = flipContainer.classList.toggle('is-flipped');
            const frontOri = getOri(frontImg);
            const backOri = getOri(backImg);

            if (isFlipped && backImg && !backImg.naturalWidth) {
                backImg.loading = 'eager';
            }

            const targetOri = isFlipped ? backOri : frontOri;

            if (frontOri !== backOri) {
                setTimeout(() => {
                    flipContainer.classList.remove('is-portrait', 'is-landscape');
                    flipContainer.classList.add('is-' + targetOri);
                }, 220);
            } else {
                flipContainer.classList.remove('is-portrait', 'is-landscape');
                flipContainer.classList.add('is-' + targetOri);
            }
        });
    });
</script>
</body>
</html>