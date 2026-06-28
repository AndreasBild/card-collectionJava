import asyncio
import sys
from playwright.async_api import async_playwright

async def run_bulk_review(base_url="https://www.maulmann.de"):
    async with async_playwright() as p:
        # Launch browser
        browser = await p.chromium.launch(headless=True)

        # Kontext mit einem echten User-Agent erstellen, um Bot-Erkennung zu umgehen
        context = await browser.new_context(
            viewport={'width': 1280, 'height': 800},
            user_agent="Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        )
        page = await context.new_page()

        sitemap_url = f"{base_url}/sitemap.html"
        print(f"Navigating to sitemap: {sitemap_url}")

        try:
            # Auch hier auf domcontentloaded wechseln, falls die Sitemap Tracking-Skripte lädt
            await page.goto(sitemap_url, wait_until="domcontentloaded", timeout=30000)
        except Exception as e:
            print(f"Failed to load sitemap: {e}")
            await browser.close()
            return

        # Get all card detail links from the sitemap
        links = await page.eval_on_selector_all(".sitemap-card-link", "elements => elements.map(e => e.href)")

        if not links:
            print("No card links found in the sitemap. Check the selector or URL.")
            await browser.close()
            return

        print(f"Found {len(links)} card links. Starting bulk review...")

        for i, link in enumerate(links):
            print(f"[{i+1}/{len(links)}] Processing: {link}")
            try:
                # wait_until="domcontentloaded" verhindert Timeouts durch Hintergrund-Netzwerkaktivität.
                # Ein explizites Timeout von 15 Sekunden sichert den Ablauf ab.
                await page.goto(link, wait_until="domcontentloaded", timeout=15000)

                # 1. Accept GDPR Consent via LocalStorage
                await page.evaluate("localStorage.setItem('user_consent', 'accepted')")

                # 2. Click Like button
                like_btn = page.locator("#like-btn")
                try:
                    # Da wir nicht auf das Netzwerk warten, warten wir hier explizit auf die Sichtbarkeit des Buttons
                    await like_btn.wait_for(state="visible", timeout=5000)
                    if not await like_btn.is_disabled():
                        await like_btn.click()
                        print(f"  - Liked")
                    else:
                        print(f"  - Like button disabled (already liked)")
                except Exception:
                    print(f"  - Like button not found or not visible")

                # 3. Rate 5 Stars
                star5_label = page.locator("label[for='star5']")
                try:
                    await star5_label.wait_for(state="visible", timeout=3000)
                    await star5_label.click()

                    # 4. Submit Rating
                    submit_btn = page.locator("#submit-rating-btn")
                    await submit_btn.wait_for(state="visible", timeout=3000)
                    await submit_btn.click()
                    print(f"  - Rated 5 stars")
                except Exception:
                    print(f"  - Rating interaction failed (maybe already rated?)")

                # Kleiner Sleep, um Firebase-Prozesse zu erlauben und Rate-Limiting zu vermeiden
                await asyncio.sleep(0.5)

            except Exception as e:
                print(f"  - Error processing {link}: {e}")

        print("Bulk review process completed.")
        await browser.close()

if __name__ == "__main__":
    # Ermöglicht das Überschreiben der Basis-URL via Kommandozeile
    target_url = sys.argv[1] if len(sys.argv) > 1 else "https://www.maulmann.de"
    asyncio.run(run_bulk_review(target_url))