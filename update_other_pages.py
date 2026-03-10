import os
import re

nav_html = """
    <nav class="detail-nav" style="position: sticky; top: 0; z-index: 1000;">
        <div style="display: flex; gap: 10px; flex-wrap: wrap; justify-content: center; width: 100%;">
            <a href="index.html" class="modern-button" title="Home">Home</a>
            <a href="collection.html" class="modern-button" title="Juwan Howard Private Collection">Juwan Howard Private Collection</a>
            <a href="Baseball.html" class="modern-button" title="Upper Deck Baseball Cards">Baseball</a>
            <a href="Flawless.html" class="modern-button" title="2008 Upper Deck Flawless Basketball">Flawless</a>
            <a href="Wantlist.html" class="modern-button" title="Juwan Howard Wantlist">Wantlist</a>
            <a href="Panini.html" class="modern-button" title="2012-13 Panini Flawless Basketball">Panini</a>
        </div>
    </nav>"""

footer_nav = """
    <nav class="detail-nav" style="background: none; border: none; padding: 20px 0;">
        <div style="display: flex; gap: 10px; flex-wrap: wrap; justify-content: center;">
            <a href="index.html" class="modern-button" title="Home">Home</a>
            <a href="collection.html" class="modern-button" title="Juwan Howard Private Collection">Juwan Howard Private Collection</a>
            <a href="Baseball.html" class="modern-button" title="Upper Deck Baseball Cards">Baseball</a>
            <a href="Flawless.html" class="modern-button" title="2008 Upper Deck Flawless Basketball">Flawless</a>
            <a href="Wantlist.html" class="modern-button" title="Juwan Howard Wantlist">Wantlist</a>
            <a href="Panini.html" class="modern-button" title="2012-13 Panini Flawless Basketball">Panini</a>
        </div>
    </nav>"""

other_dir = "content/other"
for filename in os.listdir(other_dir):
    if filename.endswith(".html"):
        filepath = os.path.join(other_dir, filename)
        with open(filepath, "r", encoding="utf-8") as f:
            content = f.read()

        # Extract Head and Body
        match = re.search(r'(.*<body.*?>)(.*)(</body>.*)', content, re.DOTALL | re.IGNORECASE)
        if match:
            head_part = match.group(1)
            body_content = match.group(2)
            end_part = match.group(3)

            # Clean up body_content: remove previous navs and main wrappers
            body_content = re.sub(r'<nav class="detail-nav".*?</nav>', '', body_content, flags=re.DOTALL)
            body_content = re.sub(r'<main class="detail-main">', '', body_content)
            body_content = re.sub(r'</main>', '', body_content)

            # Also clean up any lingering Juwan Howard PC
            body_content = body_content.replace("Juwan Howard PC", "Juwan Howard Private Collection")

            # Reconstruct Body
            new_body = nav_html + '<main class="detail-main">' + body_content + footer_nav + '</main>'

            new_content = head_part + new_body + end_part

            with open(filepath, "w", encoding="utf-8") as f:
                f.write(new_content)
            print(f"Cleanly updated {filename}")
