<!DOCTYPE html>
<html lang="en">
<head>
    ${headHtml?no_esc}
</head>
<body>
${topNavHtml?no_esc}
<main class="detail-main">
    <header class="detail-header">
        <h1>Maulmann Trading Cards</h1>
        <p class="sub-title">Welcome to our Private Sports Card Collection</p>
    </header>
    <style>
        .card-carousel {
            display: flex;
            overflow-x: auto;
            scroll-snap-type: x mandatory;
            gap: 20px;
            padding: 20px 0;
            margin-bottom: 30px;
            scrollbar-width: none; /* Firefox */
            -webkit-overflow-scrolling: touch;
        }

        .card-carousel::-webkit-scrollbar {
            display: none; /* Chrome/Safari */
        }

        .carousel-item {
            flex: 0 0 auto;
            width: 250px;
            scroll-snap-align: center;
            transition: transform 0.3s ease;
        }

        .carousel-item:hover {
            transform: translateY(-8px);
        }

        .carousel-item img {
            width: 100%;
            height: auto;
            aspect-ratio: 400 / 550;
            border-radius: 8px;
            box-shadow: 0 6px 15px rgba(0, 0, 0, 0.2);
            object-fit: contain;
            background-color: #fff;
            display: block;
        }

        @media (max-width: 600px) {
            .carousel-item {
                width: 200px;
            }
        }
    </style>
    <section class="card-carousel">
        <div class="carousel-item">
            <img src="images/1997-98/Juwan-Howard-Washington-Bullets-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Red-33-sn47-front.webp"
                 fetchpriority="high" width="400" height="550"
                 alt="Juwan Howard 1997-98 Fleer Metal Universe Precious Metal Gems Red">
        </div>
        <div class="carousel-item">
            <img src="images/1997-98/Juwan-Howard-Washington-Bullets-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Green-33-sn7-front.webp"
                 loading="lazy" width="400" height="550"
                 alt="Juwan Howard 1997-98 Fleer Metal Universe Precious Metal Gems Green">
        </div>
        <div class="carousel-item">
            <img src="images/1997-98/Juwan-Howard-Washington-Bullets-1997-98-Upper-Deck-UD3-Season-Ticket-Autograph-Base-JH-front.webp"
                 loading="lazy" width="400" height="550" alt="Juwan Howard 1997-98 UD3 Season Ticket Autograph">
        </div>
        <div class="carousel-item">
            <img src="images/1998-99/Juwan-Howard-Washington-Wizards-1998-99-Upper-Deck-SPx-Finite-Base-Set-Spectrum-76-sn291-front.webp"
                 loading="lazy" width="400" height="550" alt="Juwan Howard 1998-99 SPx Finite Base Spectrum">
        </div>
        <div class="carousel-item">
            <img src="images/1998-99/Juwan-Howard-Washington-Wizards-1998-99-Upper-Deck-SPx-Finite-Star-Power-Spectrum-92-sn223-front.webp"
                 loading="lazy" width="400" height="550" alt="JJuwan Howard 1998-99 SPx Finite Star Power Spectrum">
        </div>
        <div class="carousel-item">
            <img src="images/1994-95/Juwan-Howard-Washington-Bullets-1994-95-Fleer-Ultra-All-Rookies-Base-3-front.webp"
                 loading="lazy" width="400" height="550" alt="Juwan Howard 1994-95 Fleer Ultra All Rookies">
        </div>
    </section>
    <div class="seo-box">
        <h2>Explore the Collection</h2>
        <p>Welcome to Maulmann Trading Cards, a dedicated space showcasing a lifelong passion for sports card
            collecting. Our centerpiece is a massive Juwan Howard Private Collection, featuring over a thousand unique
            cards spanning his entire career.</p>
        <p>Beyond our primary focus, we also feature specialized collections including:</p>
        <ul>
            <li><strong>Upper Deck Baseball:</strong> A selection of premium baseball cards.</li>
            <li><strong>Flawless Basketball:</strong> Showcasing the 2008 and 2012-13 Flawless sets.</li>
            <li><strong>Wantlist:</strong> Rare cards we are currently searching for to complete our collection.</li>
        </ul>
    </div>
    <div class="ai-summary"
         style="max-width: 800px; margin: 40px auto 0; text-align: left; line-height: 1.8; background: #f9f9f9; padding: 30px; border-radius: 8px; border-left: 5px solid #3a7504;">
        <h3 style="margin-top: 0; color: #333;">About the Archive</h3>
        <p style="margin: 0; color: #555;">This digital archive represents decades of collecting, focusing on high-end
            90s inserts, rare 1-of-1s, and iconic rookie cards. Explore the technical specifications, verified OCR
            back-scans, and the historical significance behind each piece. Every card has been digitized and cataloged
            to preserve hobby history.</p>
    </div>
</main>
${footerHtml?no_esc}
</body>
</html>