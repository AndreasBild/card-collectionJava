import asyncio
import sys
from playwright.async_api import async_playwright

async def run_bulk_review(base_url="https://www.maulmann.de"):
    async with async_playwright() as p:
        # Launch browser
        # Set headless=False if you want to see the browser in action
        browser = await p.chromium.launch(headless=True)
        context = await browser.new_context(
            viewport={'width': 1280, 'height': 800}
        )
        page = await context.new_page()

        sitemap_url = f"{base_url}/sitemap.html"
        print(f"Navigating to sitemap: {sitemap_url}")

        try:
            await page.goto(sitemap_url)
        except Exception as e:
            print(f"Failed to load sitemap: {e}")
            await browser.close()
            return

        # Get all card detail links from the sitemap
        # The selector .sitemap-card-link matches the links in the card detail section of the sitemap
        links = await page.eval_on_selector_all(".sitemap-card-link", "elements => elements.map(e => e.href)")

        if not links:
            print("No card links found in the sitemap. Check the selector or URL.")
            await browser.close()
            return

        print(f"Found {len(links)} card links. Starting bulk review...")

        for i, link in enumerate(links):
            print(f"[{i+1}/{len(links)}] Processing: {link}")
            try:
                await page.goto(link, wait_until="networkidle")

                # 1. Accept GDPR Consent via LocalStorage
                # We set this and then we might need to trigger the logic that depends on it,
                # but since handleVote checks it on every click, it should be fine.
                await page.evaluate("localStorage.setItem('user_consent', 'accepted')")

                # 2. Click Like button
                # Selector: #like-btn
                like_btn = page.locator("#like-btn")
                # Wait for the button to be attached and visible
                try:
                    await like_btn.wait_for(state="visible", timeout=5000)
                    if not await like_btn.is_disabled():
                        await like_btn.click()
                        print(f"  - Liked")
                    else:
                        print(f"  - Like button disabled (already liked)")
                except Exception:
                    print(f"  - Like button not found or not visible")

                # 3. Rate 5 Stars
                # Selector for 5-star label: label[for='star5']
                star5_label = page.locator("label[for='star5']")
                try:
                    await star5_label.wait_for(state="visible", timeout=2000)
                    await star5_label.click()

                    # 4. Submit Rating
                    # The confirmation div appears after clicking a star
                    submit_btn = page.locator("#submit-rating-btn")
                    # Wait for the submit button to become visible
                    await submit_btn.wait_for(state="visible", timeout=2000)
                    await submit_btn.click()
                    print(f"  - Rated 5 stars")
                except Exception:
                    print(f"  - Rating interaction failed (maybe already rated?)")

                # Small sleep to avoid rate limiting and allow Firebase to process
                await asyncio.sleep(0.5)

            except Exception as e:
                print(f"  - Error processing {link}: {e}")

        print("Bulk review process completed.")
        await browser.close()

if __name__ == "__main__":
    # Allow overriding the base URL from command line
    target_url = sys.argv[1] if len(sys.argv) > 1 else "https://www.maulmann.de"
    asyncio.run(run_bulk_review(target_url))
