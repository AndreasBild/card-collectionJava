<!doctype html>
<html lang="en">
<head>
    ${headHtml?no_esc}
    <#-- PRELOAD UPDATE: Teilt dem Browser mit, welche Bildgröße er vorab laden soll -->
    <link rel="preload" as="image" href="${frontImgPath}"
          imagesrcset="${frontImgPath?replace('.webp', '-400w.webp')} 400w,
                       ${frontImgPath?replace('.webp', '-600w.webp')} 600w,
                       ${frontImgPath} 1200w"
          imagesizes="(max-width: 600px) 100vw, (max-width: 1024px) 50vw, (max-width: 1300px) 45vw, 600px"
          fetchpriority="high">
    ${jsonLd?no_esc}
</head>
<body>
{{CONSENT_BANNER}}
${topNavHtml?no_esc}
<main class="detail-main">
    <nav class="detail-nav no-border-bg">
        <a id="backToOverview" title="Back To Collection Overview" href="../../${overviewPage}"
           class="modern-button no-text-decoration">&larr; Overview</a>
        <div class="nav-button-group">
            <#if prevLink??>
                <a id="prevCardLink" title="Previous Card Details" href="${prevLink}"
                   class="modern-button no-text-decoration">&laquo; Prev</a>
            </#if>
            <#if nextLink??>
                <a id="nextCardLink" title="Next Card Details" href="${nextLink}"
                   class="modern-button no-text-decoration">Next &raquo;</a>
            </#if>
            <button id="shareBtn" title="Share this card" class="modern-button" style="display: none;">Share</button>
        </div>
    </nav>
    <#if breadcrumbHtml??>${breadcrumbHtml?no_esc}</#if>
    <header class="detail-header">
        <h1>${h1Title}</h1>
    </header>
    <div class="ai-summary">
        <p><strong>Card Snapshot:</strong> ${aiSnapshotText?no_esc}</p>
    </div>
    <div class="card-images-container">
        <div class="card-image-wrapper">
            <#-- SRCSET FÜR DAS FRONT-BILD -->
            <img src="${frontImgPath}"
                 srcset="${frontImgPath?replace('.webp', '-400w.webp')} 400w,
                         ${frontImgPath?replace('.webp', '-600w.webp')} 600w,
                         ${frontImgPath} 1200w"
                 sizes="(max-width: 600px) 100vw, (max-width: 1024px) 50vw, (max-width: 1300px) 45vw, 600px"
                 alt="${frontAlt}" title="${frontImgTitle}"
                 fetchpriority="high" class="zoomable-img"
                 onclick="openModal('${frontImgPath}', '${backImgPath}')">
            <p class="img-caption">&#x1F50D; Click to Enlarge Front</p>
        </div>
        <div class="card-image-wrapper">
            <#-- SRCSET FÜR DAS BACK-BILD -->
            <img src="${backImgPath}"
                 srcset="${backImgPath?replace('.webp', '-400w.webp')} 400w,
                         ${backImgPath?replace('.webp', '-600w.webp')} 600w,
                         ${backImgPath} 1200w"
                 sizes="(max-width: 600px) 100vw, (max-width: 1024px) 50vw, (max-width: 1300px) 45vw, 600px"
                 alt="${backAlt}" title="${backImgTitle}" loading="lazy"
                 class="zoomable-img"
                 onclick="openModal('${backImgPath}', '${frontImgPath}')">
            <p class="img-caption">&#x1F50D; Click to Enlarge Back</p>
        </div>
    </div>
    <div class="card-data">
        <table class="specs-table">
            <tr class="specs-table-header">
                <th colspan="2">Technical Specifications</th>
            </tr>
            <tr>
                <th class="specs-th">Player</th>
                <td class="specs-td">${player}</td>
            </tr>
            <tr>
                <th class="specs-th">Season</th>
                <td class="specs-td">${season}</td>
            </tr>
            <tr>
                <th class="specs-th">Team</th>
                <td class="specs-td">${team}</td>
            </tr>
            <tr>
                <th class="specs-th">Manufacturer</th>
                <td class="specs-td">${company}</td>
            </tr>
            <tr>
                <th class="specs-th">Brand</th>
                <td class="specs-td">${brand}</td>
            </tr>
            <tr>
                <th class="specs-th">Theme</th>
                <td class="specs-td">${theme}</td>
            </tr>
            <tr>
                <th class="specs-th">Variant</th>
                <td class="specs-td">${variant}</td>
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
    <#if (hobbyTrivia != "") || (techTrivia != "") || (playerHighlights != "") || (eraContext != "") || (cardBackText != "")>
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
                    <h3>&#x1F3C0; NBA Era & Pop Culture</h3>
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
        </div>
    </#if>
    <section class="vote-section">
        <h2>Collector Sentiment</h2>
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
            <div class="rating-interaction-container">
                <div class="star-rating" id="interactive-stars">
                    <input type="radio" id="star5" name="rating" value="5"/><label class="full" for="star5"
                                                                                   title="Exceptional"></label>
                    <input type="radio" id="star4half" name="rating" value="4.5"/><label class="half" for="star4half"
                                                                                         title="Very Good"></label>
                    <input type="radio" id="star4" name="rating" value="4"/><label class="full" for="star4"
                                                                                   title="Good"></label>
                    <input type="radio" id="star3half" name="rating" value="3.5"/><label class="half" for="star3half"
                                                                                         title="Nice"></label>
                    <input type="radio" id="star3" name="rating" value="3"/><label class="full" for="star3"
                                                                                   title="Average"></label>
                    <input type="radio" id="star2half" name="rating" value="2.5"/><label class="half" for="star2half"
                                                                                         title="Ok"></label>
                    <input type="radio" id="star2" name="rating" value="2"/><label class="full" for="star2"
                                                                                   title="Acceptable"></label>
                    <input type="radio" id="star1half" name="rating" value="1.5"/><label class="half" for="star1half"
                                                                                         title="Not Good"></label>
                    <input type="radio" id="star1" name="rating" value="1"/><label class="full" for="star1"
                                                                                   title="Poor"></label>
                    <input type="radio" id="star0half" name="rating" value="0.5"/><label class="half" for="star0half"
                                                                                         title="Very Poor"></label>
                </div>
                <div id="rating-value-label" class="rating-label">Rate it!</div>
            </div>
            <div id="rating-confirmation" class="rating-confirmation" style="display: none;">
                <p>Your Rating: <span id="selected-rating-text">0</span> Stars. Send it now?</p>
                <div class="confirmation-buttons">
                    <button id="submit-rating-btn" class="modern-button confirm-btn" title="Submit Rating">Submit
                        Rating
                    </button>
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
    <section class="market-section" id="market-section" style="display: none;">
        <h2>Cards on the market</h2>
        <div id="market-results">
            <div class="market-loading">Searching for active listings...</div>
        </div>
    </section>
    <section class="faq-section" id="faq-section">
        <h2>Collector FAQs</h2>
        ${faqHtml?no_esc}
    </section>
    ${footerHtml?no_esc}
</main>
<div id="cardModal" class="modal" aria-hidden="true">
    <button class="close-modal-btn" aria-label="Close zoomed image" onclick="closeModal()">&times;</button>
    <button class="flip-modal-btn" aria-label="Flip card to other side" onclick="flipCard()">
        &#8644; Flip Card
    </button>
    <img class="modal-content" id="img01" alt="Zoomed card view" title="Zoomed card view">
</div>
<script>
    var modal = document.getElementById("cardModal");
    var modalImg = document.getElementById("img01");
    var currentModalSrc = "";
    var alternateModalSrc = "";

    function openModal(src, altSrc) {
        modal.style.display = "flex";
        modal.setAttribute("aria-hidden", "false");
        modalImg.src = src;
        currentModalSrc = src;
        alternateModalSrc = altSrc;
    }

    function closeModal() {
        modal.style.display = "none";
        modal.setAttribute("aria-hidden", "true");
    }

    function flipCard() {
        var temp = currentModalSrc;
        currentModalSrc = alternateModalSrc;
        alternateModalSrc = temp;
        modalImg.src = currentModalSrc;
    }

    window.onclick = function (event) {
        if (event.target == modal) closeModal();
    }

    document.addEventListener('DOMContentLoaded', () => {
        // Extract card ID from filename
        const pathParts = window.location.pathname.split('/');
        const filename = pathParts[pathParts.length - 1];
        if (filename && filename.endsWith('.html')) {
            const cardId = filename.replace('.html', '');
            const backLink = document.getElementById('backToOverview');
            if (backLink) {
                backLink.href += '#' + cardId;
            }
        }

        // Market Search functionality
        const faqSection = document.getElementById('faq-section');
        const marketSection = document.getElementById('market-section');
        const marketResults = document.getElementById('market-results');
        let hasSearched = false;

        if (faqSection && marketSection) {
            const observer = new IntersectionObserver((entries) => {
                if (entries[0].isIntersecting && !hasSearched) {
                    hasSearched = true;
                    marketSection.style.display = 'block';
                    searchMarket();
                }
            }, {threshold: 0.1});

            observer.observe(faqSection);
        }

        function searchMarket() {
            const cardName = encodeURIComponent("${h1Title?js_string}");
            const ebaySearchUrl = "https://www.ebay.com/sch/i.html?_nkw=" + cardName;

            marketResults.innerHTML = ''; // Clear loading

            const container = document.createElement('div');
            container.className = "market-listing";

            const detailsDiv = document.createElement('div');
            detailsDiv.className = "market-details";

            const titleDiv = document.createElement('div');
            titleDiv.className = "market-title";
            titleDiv.textContent = "Search for ${h1Title?js_string}";

            const descDiv = document.createElement('div');
            descDiv.style.fontSize = '0.9rem';
            descDiv.style.color = '#555';
            descDiv.style.marginTop = '4px';
            descDiv.textContent = "Check current active listings on eBay";

            detailsDiv.appendChild(titleDiv);
            detailsDiv.appendChild(descDiv);

            const actionA = document.createElement('a');
            actionA.href = ebaySearchUrl;
            actionA.target = "_blank";
            actionA.rel = "noopener noreferrer";
            actionA.className = "market-action modern-button no-text-decoration";
            actionA.textContent = "View on eBay";

            container.appendChild(detailsDiv);
            container.appendChild(actionA);

            marketResults.appendChild(container);
        }
    });
    document.addEventListener('keydown', function (event) {
        if (event.key === "Escape") closeModal();
        if (event.key === "ArrowLeft") {
            var prevLink = document.getElementById('prevCardLink');
            if (prevLink) window.location.href = prevLink.href;
        }
        if (event.key === "ArrowRight") {
            var nextLink = document.getElementById('nextCardLink');
            if (nextLink) window.location.href = nextLink.href;
        }
    });
</script>
<script type="module">
    import {initializeApp} from "https://www.gstatic.com/firebasejs/10.7.1/firebase-app.js";
    import {
        getFirestore,
        initializeFirestore,
        persistentLocalCache,
        persistentMultipleTabManager,
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

    const app = initializeApp(firebaseConfig);
    let db;
    try {
        db = initializeFirestore(app, {
            localCache: persistentLocalCache({
                tabManager: persistentMultipleTabManager()
            })
        });
    } catch (e) {
        console.warn("Firestore persistent cache not supported in this browser mode (e.g. Private Tab), falling back to standard Firestore:", e);
        try {
            db = getFirestore(app);
        } catch (e2) {
            console.error("Firestore init fallback error:", e2);
        }
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

    // Web Share API implementation
    const shareBtn = document.getElementById('shareBtn');
    if (navigator.share) {
        shareBtn.style.display = 'inline-flex';
        shareBtn.addEventListener('click', async () => {
            try {
                await navigator.share({
                    title: document.title,
                    text: 'Check out this rare Juwan Howard card: ' + document.querySelector('h1').textContent,
                    url: window.location.href
                });
                console.log('Successfully shared');
            } catch (error) {
                console.log('Error sharing:', error);
            }
        });
    }

    if (getStorage(localStorageKey)) {
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
        ratingLabel.textContent = "Thank you for rating!";
        ratingLabel.style.color = "#3a7504";
    }

    const cardRef = doc(db, "Trading_cards", cardId);

    onSnapshot(cardRef, (docSnap) => {
        if (docSnap.exists()) {
            const data = docSnap.data();
            likeCountSpan.textContent = data.likes || 0;
            dislikeCountSpan.textContent = data.dislikes || 0;

            const sum = data.ratingSum || 0;
            const count = data.ratingCount || 0;
            const average = count > 0 ? (sum / count).toFixed(1) : "0.0";

            averageRatingSpan.textContent = average;
            ratingCountSpan.textContent = count;

            // Update visual stars
            const visualPercentage = (parseFloat(average) / 5) * 100;
            starsInner.style.width = visualPercentage + "%";
        } else {
            likeCountSpan.textContent = "0";
            dislikeCountSpan.textContent = "0";
            averageRatingSpan.textContent = "0.0";
            ratingCountSpan.textContent = "0";
            starsInner.style.width = "0%";
        }
    }, (error) => {
        console.warn("Firestore snapshot error for card " + cardId + ":", error);
    });

    function checkConsent() {
        if (getStorage('user_consent') !== 'accepted') {
            if (window.showConsentBanner) {
                window.showConsentBanner();
            }
            // Highlight the banner briefly
            const banner = document.getElementById('consent-banner');
            if (banner) {
                banner.style.backgroundColor = '#fff9c4';
                setTimeout(() => banner.style.backgroundColor = '#fff', 2000);
            }
            alert("Please accept site preferences in the banner below to rate cards.");
            return false;
        }
        return true;
    }

    async function handleVote(type) {
        if (getStorage(localStorageKey)) return;
        if (!checkConsent()) return;

        try {
            await setDoc(cardRef, {
                likes: type === 'likes' ? increment(1) : increment(0),
                dislikes: type === 'dislikes' ? increment(1) : increment(0),
                ratingCount: increment(0),
                ratingSum: increment(0)
            }, {merge: true});

            setStorage(localStorageKey, 'true');
            likeBtn.disabled = true;
            dislikeBtn.disabled = true;
            likeBtn.title = "You have already voted on this card";
            dislikeBtn.title = "You have already voted on this card";
        } catch (error) {
            console.error("Error voting:", error);
            alert("Could not submit your vote. Please check your network connection or ad-blocker settings.");
        }
    }

    async function submitRating() {
        if (!pendingRating || getStorage(localStorageRatingKey)) return;
        if (!checkConsent()) return;

        try {
            await setDoc(cardRef, {
                likes: increment(0),
                dislikes: increment(0),
                ratingCount: increment(1),
                ratingSum: increment(parseFloat(pendingRating))
            }, {merge: true});

            setStorage(localStorageRatingKey, 'true');
            disableStarInteraction();
            ratingConfirmation.style.display = 'none';
            pendingRating = null;
        } catch (error) {
            console.error("Error rating:", error);
            alert("Could not submit your rating. Please check your network connection or ad-blocker settings.");
        }
    }

    // Interaction Listeners
    likeBtn.addEventListener('click', () => handleVote('likes'));
    dislikeBtn.addEventListener('click', () => handleVote('dislikes'));

    starLabels.forEach(label => {
        label.addEventListener('mouseenter', (e) => {
            if (!localStorage.getItem(localStorageRatingKey)) {
                ratingLabel.textContent = e.target.title;
            }
        });
    });

    document.getElementById('interactive-stars').addEventListener('mouseleave', () => {
        if (!localStorage.getItem(localStorageRatingKey)) {
            ratingLabel.textContent = "Rate it!";
        }
    });

    starInputs.forEach(input => {
        input.addEventListener('change', (e) => {
            if (localStorage.getItem(localStorageRatingKey)) return;
            pendingRating = e.target.value;
            selectedRatingText.textContent = pendingRating;
            ratingConfirmation.style.display = 'block';
            ratingConfirmation.scrollIntoView({behavior: 'smooth', block: 'nearest'});
        });
    });

    submitRatingBtn.addEventListener('click', submitRating);
    cancelRatingBtn.addEventListener('click', () => {
        ratingConfirmation.style.display = 'none';
        pendingRating = null;
        starInputs.forEach(input => input.checked = false);
    });
</script>
</body>
</html>