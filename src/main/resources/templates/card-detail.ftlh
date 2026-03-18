<!doctype html>
<html lang="en">
<head>
    ${headHtml?no_esc}
    <link rel="preload" as="image" href="${frontImgPath}" fetchpriority="high">
    ${jsonLd?no_esc}
</head>
<body>
${topNavHtml?no_esc}
<nav class="detail-nav" style="border: none; background: transparent;">
    <a href="../../${overviewPage}" class="modern-button" style="text-decoration:none;">&larr; Overview</a>
    <div style="display: flex; gap: 10px;">
        <#if prevLink??>
            <a id="prevCardLink" href="${prevLink}" class="modern-button" style="text-decoration:none;">&laquo; Prev</a>
        </#if>
        <#if nextLink??>
            <a id="nextCardLink" href="${nextLink}" class="modern-button" style="text-decoration:none;">Next &raquo;</a>
        </#if>
    </div>
</nav>
<main class="detail-main">
    <header class="detail-header">
        <h1>${h1Title}</h1>
    </header>
    <div class="ai-summary"
         style="background: #f4f8fc; border-left: 4px solid #0056b3; padding: 15px; border-radius: 4px; margin-bottom: 25px;">
        <p style="margin:0; font-size: 0.95em;"><strong>Card Snapshot:</strong> ${aiSnapshotText}</p>
    </div>
    <div class="card-images-container" style="display: flex; gap: 20px; flex-wrap: wrap;">
        <div class="card-image-wrapper" style="display: flex; flex-direction: column; align-items: center;">
            <img src="${frontImgPath}" alt="${frontAlt}" title="${frontImgTitle}" width="400" height="550"
                 fetchpriority="high"
                 style="aspect-ratio: 400 / 550; width: 100%; max-width: 400px; height: auto; display: block; object-fit: contain; cursor: zoom-in;"
                 onclick="openModal('${frontImgPath}', '${backImgPath}')">
            <p style="margin-top: 10px; font-size: 0.85em; color: #666;">&#x1F50D; Click to Enlarge Front</p>
        </div>
        <div class="card-image-wrapper" style="display: flex; flex-direction: column; align-items: center;">
            <img src="${backImgPath}" alt="${backAlt}" title="${backImgTitle}" width="400" height="550" loading="lazy"
                 style="aspect-ratio: 400 / 550; width: 100%; max-width: 400px; height: auto; display: block; object-fit: contain; cursor: zoom-in;"
                 onclick="openModal('${backImgPath}', '${frontImgPath}')">
            <p style="margin-top: 10px; font-size: 0.85em; color: #666;">&#x1F50D; Click to Enlarge Back</p>
        </div>
    </div>
    <div class="card-data" style="margin-top: 30px;">
        <table style="width: 100%; border-collapse: collapse; margin-top: 20px;">
            <tr class="specs-table-header">
                <th colspan="2" style="padding: 10px; text-align: left;">Technical Specifications</th>
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
        <div class="context-engine"
             style="margin-top: 40px; display: grid; gap: 20px; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));">
            <#if hobbyTrivia != "">
                <div style="background: #fff; border: 1px solid #eaeaea; border-radius: 8px; padding: 20px; box-shadow: 0 4px 6px rgba(0,0,0,0.05);">
                    <h3 style="margin-top:0; color: #333; font-size: 1.1em;">&#x1F3C6; Hobby Significance</h3>
                    <p style="font-size: 0.95em; color: #555; line-height: 1.6;">${hobbyTrivia}</p>
                </div>
            </#if>

            <#if techTrivia != "">
                <div style="background: #fff; border: 1px solid #eaeaea; border-radius: 8px; padding: 20px; box-shadow: 0 4px 6px rgba(0,0,0,0.05);">
                    <h3 style="margin-top:0; color: #333; font-size: 1.1em;">&#x2692;&#xFE0F; Card Technology</h3>
                    <p style="font-size: 0.95em; color: #555; line-height: 1.6;">${techTrivia}</p>
                </div>
            </#if>

            <#if playerHighlights != "">
                <div style="background: #fff; border: 1px solid #eaeaea; border-radius: 8px; padding: 20px; box-shadow: 0 4px 6px rgba(0,0,0,0.05);">
                    <h3 style="margin-top:0; color: #333; font-size: 1.1em;">&#x1F4C8; Player Performance & Team</h3>
                    <p style="font-size: 0.95em; color: #555; line-height: 1.6;">${playerHighlights}</p>
                </div>
            </#if>

            <#if eraContext != "">
                <div style="background: #fff; border: 1px solid #eaeaea; border-radius: 8px; padding: 20px; box-shadow: 0 4px 6px rgba(0,0,0,0.05);">
                    <h3 style="margin-top:0; color: #333; font-size: 1.1em;">&#x1F3C0; NBA Era & Pop Culture</h3>
                    <p style="font-size: 0.95em; color: #555; line-height: 1.6;">${eraContext}</p>
                </div>
            </#if>

            <#if cardBackText != "">
                <div class="card-back-text-container" style="grid-column: 1 / -1;">
                    <div class="card-back-text-header">
                        <span>&#x1F4DC; Original Card Back Transcription</span>
                        <span style="margin-left: auto; font-size: 0.8em; opacity: 0.6;">Verified via AI-OCR</span>
                    </div>
                    <pre class="card-back-content">${cardBackText}</pre>
                </div>
            </#if>
        </div>
    </#if>
    <section style="margin-top: 50px;">
        <h2>Collector FAQs</h2>
        ${faqHtml?no_esc}
    </section>
    ${footerHtml?no_esc}
</main>
<div id="cardModal" class="modal" aria-hidden="true"
     style="display:none; position: fixed; z-index: 1000; left: 0; top: 0; width: 100%; height: 100%; overflow: auto; background-color: rgba(0,0,0,0.9);">
    <span class="close-modal" aria-label="Close zoomed image" onclick="closeModal()"
          style="position: absolute; top: 15px; right: 35px; color: #f1f1f1; font-size: 40px; font-weight: bold; cursor: pointer;">&times;</span>
    <button class="flip-modal-btn" aria-label="Flip card to other side" onclick="flipCard()"
            style="position: absolute; top: 20px; left: 20px; padding: 10px 20px; font-size: 16px; cursor: pointer;">
        &#8644; Flip Card
    </button>
    <img class="modal-content" id="img01" alt="Zoomed card view"
         style="margin: auto; display: block; max-width: 90%; max-height: 90vh;">
</div>
<script>
    var modal = document.getElementById("cardModal");
    var modalImg = document.getElementById("img01");
    var currentModalSrc = "";
    var alternateModalSrc = "";

    function openModal(src, altSrc) {
        modal.style.display = "flex";
        modal.style.alignItems = "center";
        modal.style.justifyContent = "center";
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