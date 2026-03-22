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
${topNavHtml?no_esc}

<nav class="detail-nav no-border-bg">
    <a href="../../${overviewPage}" class="modern-button no-text-decoration">&larr; Overview</a>
    <div class="nav-button-group">
        <#if prevLink??>
            <a id="prevCardLink" href="${prevLink}" class="modern-button no-text-decoration">&laquo; Prev</a>
        </#if>
        <#if nextLink??>
            <a id="nextCardLink" href="${nextLink}" class="modern-button no-text-decoration">Next &raquo;</a>
        </#if>
    </div>
</nav>

<main class="detail-main">
    <header class="detail-header">
        <h1>${h1Title}</h1>
    </header>

    <div class="ai-summary">
        <p><strong>Card Snapshot:</strong> ${aiSnapshotText}</p>
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

    <section class="faq-section">
        <h2>Collector FAQs</h2>
        ${faqHtml?no_esc}
    </section>

    ${footerHtml?no_esc}
</main>

<div id="cardModal" class="modal" aria-hidden="true">
    <span class="close-modal" aria-label="Close zoomed image" onclick="closeModal()">&times;</span>
    <button class="flip-modal-btn" aria-label="Flip card to other side" onclick="flipCard()">
        &#8644; Flip Card
    </button>
    <img class="modal-content" id="img01" alt="Zoomed card view">
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
</body>
</html>