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
                <img src="images/1997-98/Juwan-Howard-Washington-Bullets-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Red-33-PMG-sn47-front-400w.webp"
                     fetchpriority="high" width="400" height="550"
                     alt="Juwan Howard 1997-98 Fleer Metal Universe Precious Metal Gems Red"
                     title="Juwan Howard Private Collection - 1997-98 PMG Red">
                <div class="hover-overlay">
                    <a href="cards/1997-98/Juwan-Howard-Washington-Bullets-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Red-33-PMG-sn47-f82795b8.html" class="view-button" title="View details for Juwan Howard 1997-98 Fleer Metal Universe Precious Metal Gems Red">View Details</a>
                </div>
            </div>
            <div class="carousel-item">
                <img src="images/1998-99/Juwan-Howard-Washington-Wizards-1998-99-Fleer-Fleer-Vintage-61-Base-Set-Classic-5C-sn16-front-400w.webp"
                     fetchpriority="high" width="400" height="550"
                     alt="Juwan Howard 1998-99 Fleer Vintage 61 Base Classic 5C"
                     title="Juwan Howard Private Collection - 1998-99 Fleer Vintage 61">
                <div class="hover-overlay">
                    <a href="cards/1998-99/Juwan-Howard-Washington-Wizards-1998-99-Fleer-Fleer-Vintage-61-Base-Set-Classic-5C-sn16-6de9ac17.html" class="view-button" title="View details for Juwan Howard 1998-99 Fleer Vintage 61 Base Classic 5C">View Details</a>
                </div>
            </div>
            <div class="carousel-item">
                <img src="images/1998-99/Juwan-Howard-Washington-Wizards-1998-99-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-5-sn33-front-400w.webp"
                     fetchpriority="high" width="400" height="550"
                     alt="Juwan Howard 1998-99 Fleer Metal Universe Precious Metal Gems 5"
                     title="Juwan Howard Private Collection - 1998-99 PMG">
                <div class="hover-overlay">
                    <a href="cards/1998-99/Juwan-Howard-Washington-Wizards-1998-99-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-5-sn33-202e3390.html" class="view-button" title="View details for Juwan Howard 1998-99 Fleer Metal Universe Precious Metal Gems 5">View Details</a>
                </div>
            </div>
            <div class="carousel-item">
                <img src="images/1998-99/Juwan-Howard-Washington-Wizards-1998-99-Fleer-Fleer-Brilliants-Base-Set-24-K-Gold-97TG-sn14-front-400w.webp"
                     fetchpriority="high" width="400" height="550"
                     alt="Juwan Howard 1998-99 Fleer Brilliants Base 24 K Gold 97TG"
                     title="Juwan Howard Private Collection - 1998-99 Fleer Brilliants 24K Gold">
                <div class="hover-overlay">
                    <a href="cards/1998-99/Juwan-Howard-Washington-Wizards-1998-99-Fleer-Fleer-Brilliants-Base-Set-24-K-Gold-97TG-sn14-d253d68b.html" class="view-button" title="View details for Juwan Howard 1998-99 Fleer Brilliants Base 24 K Gold 97TG">View Details</a>
                </div>
            </div>
            <div class="carousel-item">
                <img src="images/2018-19/Juwan-Howard-Washington-Wizards-2018-19-Panini-Panini-Contenders-Optic-Contenders-Autographs-Gold-Vinyl-LC-JWH-sn1-front-400w.webp"
                     fetchpriority="high" width="400" height="550"
                     alt="Juwan Howard 2018-19 Panini Panini Contenders Optic Contenders Autographs Gold Vinyl LC JWH"
                     title="Juwan Howard Private Collection - 2018-19 Contenders Optic Gold Vinyl 1/1">
                <div class="hover-overlay">
                    <a href="cards/2018-19/Juwan-Howard-Washington-Wizards-2018-19-Panini-Panini-Contenders-Optic-Contenders-Autographs-Gold-Vinyl-LC-JWH-sn1-e49f9113.html" class="view-button" title="View details for Juwan Howard 2018-19 Panini Panini Contenders Optic Contenders Autographs Gold Vinyl LC JWH">View Details</a>
                </div>
            </div>
            <div class="carousel-item">
                <img src="images/1997-98/Juwan-Howard-Washington-Bullets-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Green-33-PMG-sn7-front-400w.webp"
                     fetchpriority="high" width="400" height="550"
                     alt="Juwan Howard 1997-98 Fleer Metal Universe Precious Metal Gems Green"
                     title="Juwan Howard Private Collection - 1997-98 PMG Green 1/10">
                <div class="hover-overlay">
                    <a href="cards/1997-98/Juwan-Howard-Washington-Bullets-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Green-33-PMG-sn7-036109be.html" class="view-button" title="View details for Juwan Howard 1997-98 Fleer Metal Universe Precious Metal Gems Green">View Details</a>
                </div>
            </div>
            <div class="carousel-item">
                <img src="images/1998-99/Juwan-Howard-Washington-Wizards-1998-99-Upper-Deck-SPx-Finite-Base-Set-Spectrum-76-sn291-front-400w.webp"
                     loading="lazy" width="400" height="550" alt="Juwan Howard 1998-99 SPx Finite Base Spectrum"
                     title="Juwan Howard Private Collection - 1998-99 SPx Finite Spectrum">
                <div class="hover-overlay">
                    <a href="cards/1998-99/Juwan-Howard-Washington-Wizards-1998-99-Upper-Deck-SPx-Finite-Base-Set-Spectrum-76-sn291-0dd7be42.html" class="view-button" title="View details for Juwan Howard 1998-99 SPx Finite Base Spectrum">View Details</a>
                </div>
            </div>
            <div class="carousel-item">
                <img src="images/1997-98/Juwan-Howard-Washington-Wizards-1997-98-Fleer-Fleer-Metal-Universe-Championship-Base-Set-Precious-Metal-Gems-11-sn32-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="1997-98 Fleer Metal Universe Championship Precious Metal Gems 11"
                     title="Juwan Howard Private Collection - 1997-98 PMG Championship">
                <div class="hover-overlay">
                    <a href="cards/1997-98/Juwan-Howard-Washington-Wizards-1997-98-Fleer-Fleer-Metal-Universe-Championship-Base-Set-Precious-Metal-Gems-11-sn32-bec55345.html" class="view-button" title="View details for 1997-98 Fleer Metal Universe Championship Precious Metal Gems 11">View Details</a>
                </div>
            </div>
            <div class="carousel-item">
                <img src="images/2022-23/Juwan-Howard-Washington-Wizards-2022-23-Panini-Panini-Spectra-Icons-Autographs-Nebula-IA-JUW-sn1-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="2022-23 Panini Panini Spectra Icons Autographs Nebula IA JUW"
                     title="Juwan Howard Private Collection - 2022-23 Spectra Nebula 1/1">
                <div class="hover-overlay">
                    <a href="cards/2022-23/Juwan-Howard-Washington-Wizards-2022-23-Panini-Panini-Spectra-Icons-Autographs-Nebula-IA-JUW-sn1-e5137523.html" class="view-button" title="View details for 2022-23 Panini Panini Spectra Icons Autographs Nebula IA JUW">View Details</a>
                </div>
            </div>
            <div class="carousel-item">
                <img src="images/2018-19/Juwan-Howard-Miami-Heat-2018-19-Panini-Panini-Flawless-Legendary-Scripts-Platinum-LS-JWH-sn1-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="2018-19 Panini Panini Flawless Legendary Scripts Platinum LS JWH"
                     title="Juwan Howard Private Collection - 2018-19 Flawless Platinum 1/1">
                <div class="hover-overlay">
                    <a href="cards/2018-19/Juwan-Howard-Miami-Heat-2018-19-Panini-Panini-Flawless-Legendary-Scripts-Platinum-LS-JWH-sn1-0a9bf98e.html" class="view-button" title="View details for 2018-19 Panini Panini Flawless Legendary Scripts Platinum LS JWH">View Details</a>
                </div>
            </div>
            <div class="carousel-item">
                <img src="images/2016-17/Juwan-Howard-Miami-Heat-2016-17-Panini-Panini-Donruss-Elite-Signatures-Black-99-sn1-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="2016-17 Panini Panini Donruss Elite Signatures Black 99"
                     title="Juwan Howard Private Collection - 2016-17 Donruss Elite Black 1/1">
                <div class="hover-overlay">
                    <a href="cards/2016-17/Juwan-Howard-Miami-Heat-2016-17-Panini-Panini-Donruss-Elite-Signatures-Black-99-sn1-ea4c7783.html" class="view-button" title="View details for 2016-17 Panini Panini Donruss Elite Signatures Black 99">View Details</a>
                </div>
            </div>
            <div class="carousel-item">
                <img src="images/2017-18/Juwan-Howard-Dallas-Mavericks-2017-18-Panini-Panini-Donruss-Optic-Dominator-Signatures-Gold-Vinyl-18-sn1-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="2017-18 Panini Panini Donruss Optic Dominator Signatures Gold Vinyl 18"
                     title="Juwan Howard Private Collection - 2017-18 Optic Gold Vinyl 1/1">
                <div class="hover-overlay">
                    <a href="cards/2017-18/Juwan-Howard-Dallas-Mavericks-2017-18-Panini-Panini-Donruss-Optic-Dominator-Signatures-Gold-Vinyl-18-sn1-a2de02d4.html" class="view-button" title="View details for 2017-18 Panini Panini Donruss Optic Dominator Signatures Gold Vinyl 18">View Details</a>
                </div>
            </div>
            <div class="carousel-item">
                <img src="images/2017-18/Juwan-Howard-Miami-Heat-2017-18-Panini-Panini-Flawless-Premium-Ink-Signatures-Platinum-PI-JH-sn1-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="2017-18 Panini Panini Flawless Premium Ink Signatures Platinum PI JH"
                     title="Juwan Howard Private Collection - 2017-18 Flawless Premium Ink 1/1">
                <div class="hover-overlay">
                    <a href="cards/2017-18/Juwan-Howard-Miami-Heat-2017-18-Panini-Panini-Flawless-Premium-Ink-Signatures-Platinum-PI-JH-sn1-eaf6fed5.html" class="view-button" title="View details for 2017-18 Panini Panini Flawless Premium Ink Signatures Platinum PI JH">View Details</a>
                </div>
            </div>
            <div class="carousel-item">
                <img src="images/2013-14/Juwan-Howard-Washington-Wizards-2013-14-Panini-Panini-Immaculate-Collection-Team-Logos-Numbers-Jersey-Patch-93-sn7-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="2013-14 Panini Panini Immaculate Collection Team Logos Numbers Jersey Patch 93"
                     title="Juwan Howard Private Collection - 2013-14 Immaculate Patch">
                <div class="hover-overlay">
                    <a href="cards/2013-14/Juwan-Howard-Washington-Wizards-2013-14-Panini-Panini-Immaculate-Collection-Team-Logos-Numbers-Jersey-Patch-93-sn7-9a8732d2.html" class="view-button" title="View details for 2013-14 Panini Panini Immaculate Collection Team Logos Numbers Jersey Patch 93">View Details</a>
                </div>
            </div>

            <!-- Second Set (Duplicate for seamless loop) -->
            <div class="carousel-item">
                <img src="images/1997-98/Juwan-Howard-Washington-Bullets-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Red-33-PMG-sn47-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="Juwan Howard 1997-98 Fleer Metal Universe Precious Metal Gems Red"
                     title="Juwan Howard Private Collection - 1997-98 PMG Red">
                <div class="hover-overlay">
                    <a href="cards/1997-98/Juwan-Howard-Washington-Bullets-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Red-33-PMG-sn47-f82795b8.html" class="view-button" title="View details for Juwan Howard 1997-98 Fleer Metal Universe Precious Metal Gems Red">View Details</a>
                </div>
            </div>
            <div class="carousel-item">
                <img src="images/1998-99/Juwan-Howard-Washington-Wizards-1998-99-Fleer-Fleer-Vintage-61-Base-Set-Classic-5C-sn16-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="Juwan Howard 1998-99 Fleer Vintage 61 Base Classic 5C"
                     title="Juwan Howard Private Collection - 1998-99 Fleer Vintage 61">
                <div class="hover-overlay">
                    <a href="cards/1998-99/Juwan-Howard-Washington-Wizards-1998-99-Fleer-Fleer-Vintage-61-Base-Set-Classic-5C-sn16-6de9ac17.html" class="view-button" title="View details for Juwan Howard 1998-99 Fleer Vintage 61 Base Classic 5C">View Details</a>
                </div>
            </div>
            <div class="carousel-item">
                <img src="images/1998-99/Juwan-Howard-Washington-Wizards-1998-99-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-5-sn33-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="Juwan Howard 1998-99 Fleer Metal Universe Precious Metal Gems 5"
                     title="Juwan Howard Private Collection - 1998-99 PMG">
                <div class="hover-overlay">
                    <a href="cards/1998-99/Juwan-Howard-Washington-Wizards-1998-99-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-5-sn33-202e3390.html" class="view-button" title="View details for Juwan Howard 1998-99 Fleer Metal Universe Precious Metal Gems 5">View Details</a>
                </div>
            </div>
            <div class="carousel-item">
                <img src="images/1998-99/Juwan-Howard-Washington-Wizards-1998-99-Fleer-Fleer-Brilliants-Base-Set-24-K-Gold-97TG-sn14-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="Juwan Howard 1998-99 Fleer Brilliants Base 24 K Gold 97TG"
                     title="Juwan Howard Private Collection - 1998-99 Fleer Brilliants 24K Gold">
                <div class="hover-overlay">
                    <a href="cards/1998-99/Juwan-Howard-Washington-Wizards-1998-99-Fleer-Fleer-Brilliants-Base-Set-24-K-Gold-97TG-sn14-d253d68b.html" class="view-button" title="View details for Juwan Howard 1998-99 Fleer Brilliants Base 24 K Gold 97TG">View Details</a>
                </div>
            </div>
            <div class="carousel-item">
                <img src="images/2018-19/Juwan-Howard-Washington-Wizards-2018-19-Panini-Panini-Contenders-Optic-Contenders-Autographs-Gold-Vinyl-LC-JWH-sn1-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="Juwan Howard 2018-19 Panini Panini Contenders Optic Contenders Autographs Gold Vinyl LC JWH"
                     title="Juwan Howard Private Collection - 2018-19 Contenders Optic Gold Vinyl 1/1">
                <div class="hover-overlay">
                    <a href="cards/2018-19/Juwan-Howard-Washington-Wizards-2018-19-Panini-Panini-Contenders-Optic-Contenders-Autographs-Gold-Vinyl-LC-JWH-sn1-e49f9113.html" class="view-button" title="View details for Juwan Howard 2018-19 Panini Panini Contenders Optic Contenders Autographs Gold Vinyl LC JWH">View Details</a>
                </div>
            </div>
            <div class="carousel-item">
                <img src="images/2018-19/Juwan-Howard-Washington-Wizards-2018-19-Panini-Panini-Contenders-Optic-Contenders-Autographs-Gold-Vinyl-LC-JWH-sn1-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="Juwan Howard 2018-19 Panini Panini Contenders Optic Contenders Autographs Gold Vinyl LC JWH"
                     title="Juwan Howard Private Collection - 2018-19 Contenders Optic Gold Vinyl 1/1">
                <div class="hover-overlay">
                    <a href="cards/2018-19/Juwan-Howard-Washington-Wizards-2018-19-Panini-Panini-Contenders-Optic-Contenders-Autographs-Gold-Vinyl-LC-JWH-sn1-e49f9113.html" class="view-button" title="View details for Juwan Howard 2018-19 Panini Panini Contenders Optic Contenders Autographs Gold Vinyl LC JWH">View Details</a>
                </div>
            </div>
            <div class="carousel-item">
                <img src="images/1997-98/Juwan-Howard-Washington-Bullets-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Green-33-PMG-sn7-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="Juwan Howard 1997-98 Fleer Metal Universe Precious Metal Gems Green"
                     title="Juwan Howard Private Collection - 1997-98 PMG Green 1/10">
                <div class="hover-overlay">
                    <a href="cards/1997-98/Juwan-Howard-Washington-Bullets-1997-98-Fleer-Fleer-Metal-Universe-Base-Set-Precious-Metal-Gems-Green-33-PMG-sn7-036109be.html" class="view-button" title="View details for Juwan Howard 1997-98 Fleer Metal Universe Precious Metal Gems Green">View Details</a>
                </div>
            </div>
            <div class="carousel-item">
                <img src="images/1998-99/Juwan-Howard-Washington-Wizards-1998-99-Upper-Deck-SPx-Finite-Base-Set-Spectrum-76-sn291-front-400w.webp"
                     loading="lazy" width="400" height="550" alt="Juwan Howard 1998-99 SPx Finite Base Spectrum"
                     title="Juwan Howard Private Collection - 1998-99 SPx Finite Spectrum">
                <div class="hover-overlay">
                    <a href="cards/1998-99/Juwan-Howard-Washington-Wizards-1998-99-Upper-Deck-SPx-Finite-Base-Set-Spectrum-76-sn291-0dd7be42.html" class="view-button" title="View details for Juwan Howard 1998-99 SPx Finite Base Spectrum">View Details</a>
                </div>
            </div>
            <div class="carousel-item">
                <img src="images/1997-98/Juwan-Howard-Washington-Wizards-1997-98-Fleer-Fleer-Metal-Universe-Championship-Base-Set-Precious-Metal-Gems-11-sn32-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="1997-98 Fleer Metal Universe Championship Precious Metal Gems 11"
                     title="Juwan Howard Private Collection - 1997-98 PMG Championship">
                <div class="hover-overlay">
                    <a href="cards/1997-98/Juwan-Howard-Washington-Wizards-1997-98-Fleer-Fleer-Metal-Universe-Championship-Base-Set-Precious-Metal-Gems-11-sn32-bec55345.html" class="view-button" title="View details for 1997-98 Fleer Metal Universe Championship Precious Metal Gems 11">View Details</a>
                </div>
            </div>
            <div class="carousel-item">
                <img src="images/2022-23/Juwan-Howard-Washington-Wizards-2022-23-Panini-Panini-Spectra-Icons-Autographs-Nebula-IA-JUW-sn1-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="2022-23 Panini Panini Spectra Icons Autographs Nebula IA JUW"
                     title="Juwan Howard Private Collection - 2022-23 Spectra Nebula 1/1">
                <div class="hover-overlay">
                    <a href="cards/2022-23/Juwan-Howard-Washington-Wizards-2022-23-Panini-Panini-Spectra-Icons-Autographs-Nebula-IA-JUW-sn1-e5137523.html" class="view-button" title="View details for 2022-23 Panini Panini Spectra Icons Autographs Nebula IA JUW">View Details</a>
                </div>
            </div>
            <div class="carousel-item">
                <img src="images/2018-19/Juwan-Howard-Miami-Heat-2018-19-Panini-Panini-Flawless-Legendary-Scripts-Platinum-LS-JWH-sn1-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="2018-19 Panini Panini Flawless Legendary Scripts Platinum LS JWH"
                     title="Juwan Howard Private Collection - 2018-19 Flawless Platinum 1/1">
                <div class="hover-overlay">
                    <a href="cards/2018-19/Juwan-Howard-Miami-Heat-2018-19-Panini-Panini-Flawless-Legendary-Scripts-Platinum-LS-JWH-sn1-0a9bf98e.html" class="view-button" title="View details for 2018-19 Panini Panini Flawless Legendary Scripts Platinum LS JWH">View Details</a>
                </div>
            </div>
            <div class="carousel-item">
                <img src="images/2016-17/Juwan-Howard-Miami-Heat-2016-17-Panini-Panini-Donruss-Elite-Signatures-Black-99-sn1-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="2016-17 Panini Panini Donruss Elite Signatures Black 99"
                     title="Juwan Howard Private Collection - 2016-17 Donruss Elite Black 1/1">
                <div class="hover-overlay">
                    <a href="cards/2016-17/Juwan-Howard-Miami-Heat-2016-17-Panini-Panini-Donruss-Elite-Signatures-Black-99-sn1-ea4c7783.html" class="view-button" title="View details for 2016-17 Panini Panini Donruss Elite Signatures Black 99">View Details</a>
                </div>
            </div>
            <div class="carousel-item">
                <img src="images/2017-18/Juwan-Howard-Dallas-Mavericks-2017-18-Panini-Panini-Donruss-Optic-Dominator-Signatures-Gold-Vinyl-18-sn1-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="2017-18 Panini Panini Donruss Optic Dominator Signatures Gold Vinyl 18"
                     title="Juwan Howard Private Collection - 2017-18 Optic Gold Vinyl 1/1">
                <div class="hover-overlay">
                    <a href="cards/2017-18/Juwan-Howard-Dallas-Mavericks-2017-18-Panini-Panini-Donruss-Optic-Dominator-Signatures-Gold-Vinyl-18-sn1-a2de02d4.html" class="view-button" title="View details for 2017-18 Panini Panini Donruss Optic Dominator Signatures Gold Vinyl 18">View Details</a>
                </div>
            </div>
            <div class="carousel-item">
                <img src="images/2017-18/Juwan-Howard-Miami-Heat-2017-18-Panini-Panini-Flawless-Premium-Ink-Signatures-Platinum-PI-JH-sn1-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="2017-18 Panini Panini Flawless Premium Ink Signatures Platinum PI JH"
                     title="Juwan Howard Private Collection - 2017-18 Flawless Premium Ink 1/1">
                <div class="hover-overlay">
                    <a href="cards/2017-18/Juwan-Howard-Miami-Heat-2017-18-Panini-Panini-Flawless-Premium-Ink-Signatures-Platinum-PI-JH-sn1-eaf6fed5.html" class="view-button" title="View details for 2017-18 Panini Panini Flawless Premium Ink Signatures Platinum PI JH">View Details</a>
                </div>
            </div>
            <div class="carousel-item">
                <img src="images/2013-14/Juwan-Howard-Washington-Wizards-2013-14-Panini-Panini-Immaculate-Collection-Team-Logos-Numbers-Jersey-Patch-93-sn7-front-400w.webp"
                     loading="lazy" width="400" height="550"
                     alt="2013-14 Panini Panini Immaculate Collection Team Logos Numbers Jersey Patch 93"
                     title="Juwan Howard Private Collection - 2013-14 Immaculate Patch">
                <div class="hover-overlay">
                    <a href="cards/2013-14/Juwan-Howard-Washington-Wizards-2013-14-Panini-Panini-Immaculate-Collection-Team-Logos-Numbers-Jersey-Patch-93-sn7-9a8732d2.html" class="view-button" title="View details for 2013-14 Panini Panini Immaculate Collection Team Logos Numbers Jersey Patch 93">View Details</a>
                </div>
            </div>
        </section>
    </div>
    <div class="seo-box">
        <h2>Juwan Howard Super Collector | Private Collection</h2>
        <p>Welcome to Maulmann Trading Cards, the ultimate digital archive for the Juwan Howard Super Collector. This
            exclusive Private Collection features over a thousand unique Juwan Howard cards, showcasing his historic
            career from Michigan's Fab Five to his NBA championships with the Miami Heat.</p>
        <p>As a dedicated Super Collector, we specialize in high-end hobby grails, including:</p>
        <ul>
            <li><strong>1/1 Masterpieces:</strong> Unique One-of-One cards, Printing Plates, and Superfractor autographs.</li>
            <li><strong>Precious Metal Gems (PMG):</strong> Iconic SkyBox Metal Universe PMG Red and Green parallels.</li>
            <li><strong>Rare 90s Inserts:</strong> SkyBox Premium Star Rubies, Flair Showcase Legacy Collection, and E-X Credentials.</li>
            <li><strong>Specialized Galleries:</strong> Curated subsets like 2005 Upper Deck Baseball and 2012-13 Panini Flawless.</li>
        </ul>
        <p>Explore our <a href="Wantlist.html" title="Juwan Howard Card Wantlist">Wantlist</a> to see the rare 1/1s and PMGs we are still hunting for.</p>
    </div>
    <div class="ai-summary"
         style="max-width: 800px; margin: 40px auto 0; text-align: left; line-height: 1.8; background: #f9f9f9; padding: 30px; border-radius: 8px; border-left: 5px solid #3a7504;">
        <h3 style="margin-top: 0; color: #333;">The Juwan Howard Archive</h3>
        <p style="margin: 0; color: #555;">This Private Collection is optimized for hobbyists and researchers seeking
            detailed information on Juwan Howard cards. We provide high-resolution scans, technical specifications, and
            verified AI-OCR back-scans for every card. From common base cards to the most elusive 1/1 Masterpiece,
            this archive preserves the legacy of one of the 90s' most iconic players.</p>
    </div>
</main>
${footerHtml?no_esc}
</body>
</html>