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
        .carousel-wrapper {
            width: 100%;
            overflow: hidden;
            padding: 40px 0;
            position: relative;
            background: radial-gradient(circle at center, #fdfdfd 0%, #f5f5f5 100%);
            margin-bottom: 40px;
            border-radius: 12px;
            box-shadow: inset 0 0 20px rgba(0, 0, 0, 0.02);
        }

        .card-carousel {
            display: flex;
            gap: 30px;
            width: max-content;
            animation: infinite-scroll 60s linear infinite;
            will-change: transform;
        }

        .carousel-wrapper:hover .card-carousel {
            animation-play-state: paused;
        }

        @keyframes infinite-scroll {
            0% {
                transform: translate3d(0, 0, 0);
            }
            100% {
                transform: translate3d(calc(-50% - 15px), 0, 0);
            }
        }

        .carousel-item {
            flex: 0 0 auto;
            width: 260px;
            transition: all 0.4s cubic-bezier(0.165, 0.84, 0.44, 1);
            position: relative;
        }

        .carousel-item:hover {
            transform: translateY(-15px) scale(1.08) rotate(1deg);
            z-index: 100;
        }

        .carousel-item img {
            width: 100%;
            height: auto;
            aspect-ratio: 400 / 550;
            border-radius: 10px;
            box-shadow: 0 8px 20px rgba(0, 0, 0, 0.15), 0 2px 5px rgba(0, 0, 0, 0.1);
            object-fit: contain;
            background-color: #fff;
            display: block;
            border: 1px solid rgba(0, 0, 0, 0.05);
        }

        /* Responsive adjustments */
        @media (max-width: 600px) {
            .carousel-item {
                width: 180px;
            }

            .carousel-wrapper {
                padding: 20px 0;
            }

            .card-carousel {
                gap: 15px;
                animation-duration: 40s;
            }

            @keyframes infinite-scroll {
                0% {
                    transform: translate3d(0, 0, 0);
                }
                100% {
                    transform: translate3d(calc(-50% - 7.5px), 0, 0);
                }
            }
        }

        /* Glass effect for the wrapper edges */
        .carousel-wrapper::before,
        .carousel-wrapper::after {
            content: "";
            position: absolute;
            top: 0;
            width: 100px;
            height: 100%;
            z-index: 2;
            pointer-events: none;
        }

        .carousel-wrapper::before {
            left: 0;
            background: linear-gradient(to right, rgba(255, 255, 255, 1) 0%, rgba(255, 255, 255, 0) 100%);
        }

        .carousel-wrapper::after {
            right: 0;
            background: linear-gradient(to left, rgba(255, 255, 255, 1) 0%, rgba(255, 255, 255, 0) 100%);
        }
    </style>
    <div class="carousel-wrapper">
        <section class="card-carousel">
            <!-- First Set -->
            <div class="carousel-item">
                <img src="images/1997-98/Juwan-Howard-Washington-Bullets-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Red-33-sn47-front-400w.webp"
                     fetchpriority="high" width="400" height="550"
                     alt="Juwan Howard 1997-98 Fleer Metal Universe Precious Metal Gems Red">
            </div>
            <div class="carousel-item">
                <img src="images/1998-99/Juwan-Howard-Washington-Wizards-1998-99-Fleer-Fleer-Vintage-61-Base-Set-Classic-5C-sn16-front-400w.webp"
                     fetchpriority="high" width="400" height="550"
                     alt="Juwan Howard 1998-99 Fleer Vintage 61 Base Classic 5C">
            </div>
            <div class="carousel-item">
                <img src="images/1998-99/Juwan-Howard-Washington-Wizards-1998-99-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-5-sn33-front-400w.webp"
                     fetchpriority="high" width="400" height="550"
                     alt="Juwan Howard 1998-99 Fleer Metal Universe Precious Metal Gems 5">
            </div>
            <div class="carousel-item">
                <img src="images/1998-99/Juwan-Howard-Washington-Wizards-1998-99-Fleer-Fleer-Brilliants-Base-Set-24-K-Gold-97TG-sn14-front-400w.webp"
                     fetchpriority="high" width="400" height="550"
                     alt="Juwan Howard 1998-99 Fleer Brilliants Base 24 K Gold 97TG">
            </div>
            <div class="carousel-item">
                <img src="images/2018-19/Juwan-Howard-Washington-Wizards-2018-19-Panini-Panini-Contenders-Optic-Contenders-Autographs-Gold-Vinyl-LC-JWH-sn1-front-400w.webp"
                     fetchpriority="high" width="400" height="550"
                     alt="Juwan Howard 2018-19 Panini Panini Contenders Optic Contenders Autographs Gold Vinyl LC JWH">
            </div>
            <div class="carousel-item">
                <img src="images/1997-98/Juwan-Howard-Washington-Bullets-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Green-33-sn7-front-400w.webp"
                     fetchpriority="high" width="400" height="550"
                     alt="Juwan Howard 1997-98 Fleer Metal Universe Precious Metal Gems Green">
            </div>
            <div class="carousel-item">
                <img src="images/1998-99/Juwan-Howard-Washington-Wizards-1998-99-Upper-Deck-SPx-Finite-Base-Set-Spectrum-76-sn291-front-400w.webp"
                     loading="lazy" width="400" height="550" alt="Juwan Howard 1998-99 SPx Finite Base Spectrum">
            </div>
            <div class="carousel-item">
                <img src="images/1997-98/Juwan-Howard-Washington-Wizards-1997-98-Fleer-Fleer-Metal-Universe-Championship-Base-Set-Precious-Metal-Gems-11-sn32-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="1997-98 Fleer Metal Universe Championship Precious Metal Gems 11">
            </div>
            <div class="carousel-item">
                <img src="images/2022-23/Juwan-Howard-Washington-Wizards-2022-23-Panini-Panini-Spectra-Icons-Autographs-Nebula-IA-JUW-sn1-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="2022-23 Panini Panini Spectra Icons Autographs Nebula IA JUW">
            </div>
            <div class="carousel-item">
                <img src="images/2018-19/Juwan-Howard-Miami-Heat-2018-19-Panini-Panini-Flawless-Legendary-Scripts-Platinum-LS-JWH-sn1-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="2018-19 Panini Panini Flawless Legendary Scripts Platinum LS JWH">
            </div>
            <div class="carousel-item">
                <img src="images/2016-17/Juwan-Howard-Miami-Heat-2016-17-Panini-Panini-Donruss-Elite-Signatures-Black-99-sn1-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="2016-17 Panini Panini Donruss Elite Signatures Black 99">
            </div>
            <div class="carousel-item">
                <img src="images/2017-18/Juwan-Howard-Dallas-Mavericks-2017-18-Panini-Panini-Donruss-Optic-Dominator-Signatures-Gold-Vinyl-18-sn1-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="2017-18 Panini Panini Donruss Optic Dominator Signatures Gold Vinyl 18">
            </div>
            <div class="carousel-item">
                <img src="images/2017-18/Juwan-Howard-Miami-Heat-2017-18-Panini-Panini-Flawless-Premium-Ink-Signatures-Platinum-PI-JH-sn1-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="2017-18 Panini Panini Flawless Premium Ink Signatures Platinum PI JH">
            </div>
            <div class="carousel-item">
                <img src="images/2013-14/Juwan-Howard-Washington-Wizards-2013-14-Panini-Panini-Immaculate-Collection-Team-Logos-Numbers-Jersey-Patch-93-sn7-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="2013-14 Panini Panini Immaculate Collection Team Logos Numbers Jersey Patch 93">
            </div>

            <!-- Second Set (Duplicate for seamless loop) -->
            <div class="carousel-item">
                <img src="images/1997-98/Juwan-Howard-Washington-Bullets-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Red-33-sn47-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="Juwan Howard 1997-98 Fleer Metal Universe Precious Metal Gems Red">
            </div>
            <div class="carousel-item">
                <img src="images/1998-99/Juwan-Howard-Washington-Wizards-1998-99-Fleer-Fleer-Vintage-61-Base-Set-Classic-5C-sn16-front-400w.webp"
                     fetchpriority="high" width="400" height="550"
                     alt="Juwan Howard 1998-99 Fleer Vintage 61 Base Classic 5C">
            </div>
            <div class="carousel-item">
                <img src="images/1998-99/Juwan-Howard-Washington-Wizards-1998-99-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-5-sn33-front-400w.webp"
                     fetchpriority="high" width="400" height="550"
                     alt="Juwan Howard 1998-99 Fleer Metal Universe Precious Metal Gems 5">
            </div>
            <div class="carousel-item">
                <img src="images/1998-99/Juwan-Howard-Washington-Wizards-1998-99-Fleer-Fleer-Brilliants-Base-Set-24-K-Gold-97TG-sn14-front-400w.webp"
                     fetchpriority="high" width="400" height="550"
                     alt="Juwan Howard 1998-99 Fleer Brilliants Base 24 K Gold 97TG">
            </div>
            <div class="carousel-item">
                <img src="images/2018-19/Juwan-Howard-Washington-Wizards-2018-19-Panini-Panini-Contenders-Optic-Contenders-Autographs-Gold-Vinyl-LC-JWH-sn1-front-400w.webp"
                     fetchpriority="high" width="400" height="550"
                     alt="Juwan Howard 2018-19 Panini Panini Contenders Optic Contenders Autographs Gold Vinyl LC JWH">
            </div>
            <div class="carousel-item">
                <img src="images/2018-19/Juwan-Howard-Washington-Wizards-2018-19-Panini-Panini-Contenders-Optic-Contenders-Autographs-Gold-Vinyl-LC-JWH-sn1-front-400w.webp"
                     fetchpriority="high" width="400" height="550"
                     alt="Juwan Howard 2018-19 Panini Panini Contenders Optic Contenders Autographs Gold Vinyl LC JWH">
            </div>
            <div class="carousel-item">
                <img src="images/1997-98/Juwan-Howard-Washington-Bullets-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Green-33-sn7-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="Juwan Howard 1997-98 Fleer Metal Universe Precious Metal Gems Green">
            </div>
            <div class="carousel-item">
                <img src="images/1998-99/Juwan-Howard-Washington-Wizards-1998-99-Upper-Deck-SPx-Finite-Base-Set-Spectrum-76-sn291-front-400w.webp"
                     loading="lazy" width="400" height="550" alt="Juwan Howard 1998-99 SPx Finite Base Spectrum">
            </div>
            <div class="carousel-item">
                <img src="images/1997-98/Juwan-Howard-Washington-Wizards-1997-98-Fleer-Fleer-Metal-Universe-Championship-Base-Set-Precious-Metal-Gems-11-sn32-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="1997-98 Fleer Metal Universe Championship Precious Metal Gems 11">
            </div>
            <div class="carousel-item">
                <img src="images/2022-23/Juwan-Howard-Washington-Wizards-2022-23-Panini-Panini-Spectra-Icons-Autographs-Nebula-IA-JUW-sn1-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="2022-23 Panini Panini Spectra Icons Autographs Nebula IA JUW">
            </div>
            <div class="carousel-item">
                <img src="images/2018-19/Juwan-Howard-Miami-Heat-2018-19-Panini-Panini-Flawless-Legendary-Scripts-Platinum-LS-JWH-sn1-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="2018-19 Panini Panini Flawless Legendary Scripts Platinum LS JWH">
            </div>
            <div class="carousel-item">
                <img src="images/2016-17/Juwan-Howard-Miami-Heat-2016-17-Panini-Panini-Donruss-Elite-Signatures-Black-99-sn1-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="2016-17 Panini Panini Donruss Elite Signatures Black 99">
            </div>
            <div class="carousel-item">
                <img src="images/2017-18/Juwan-Howard-Dallas-Mavericks-2017-18-Panini-Panini-Donruss-Optic-Dominator-Signatures-Gold-Vinyl-18-sn1-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="2017-18 Panini Panini Donruss Optic Dominator Signatures Gold Vinyl 18">
            </div>
            <div class="carousel-item">
                <img src="images/2017-18/Juwan-Howard-Miami-Heat-2017-18-Panini-Panini-Flawless-Premium-Ink-Signatures-Platinum-PI-JH-sn1-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="2017-18 Panini Panini Flawless Premium Ink Signatures Platinum PI JH">
            </div>
            <div class="carousel-item">
                <img src="images/2013-14/Juwan-Howard-Washington-Wizards-2013-14-Panini-Panini-Immaculate-Collection-Team-Logos-Numbers-Jersey-Patch-93-sn7-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="2013-14 Panini Panini Immaculate Collection Team Logos Numbers Jersey Patch 93">
            </div>
        </section>
    </div>
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