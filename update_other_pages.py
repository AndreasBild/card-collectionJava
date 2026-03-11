import os
import re

# Unified Navigation and Head based on external templates
def get_resource(path):
    with open(os.path.join("src/main/resources", path), "r", encoding="utf-8") as f:
        return f.read()

head_template = get_resource("templates/head.html")
nav_template = get_resource("templates/topnav.html")
footer_template = get_resource("templates/footer.html")
footer_nav_template = get_resource("templates/footer_nav.html")
analytics_template = get_resource("templates/analytics.html")

other_dir = "content/other"
for filename in os.listdir(other_dir):
    if filename.endswith(".html"):
        filepath = os.path.join(other_dir, filename)
        with open(filepath, "r", encoding="utf-8") as f:
            content = f.read()

        # Extract title and description from existing content if possible
        title_match = re.search(r'<title>(.*?)</title>', content, re.IGNORECASE)
        title = title_match.group(1) if title_match else "Maulmann Trading Cards"

        desc_match = re.search(r'<meta name="description" content="(.*?)">', content, re.IGNORECASE)
        description = desc_match.group(1) if desc_match else "Sports card collection."

        # Reconstruct Head
        new_head = head_template.replace("{{TITLE}}", title) \
                                .replace("{{DESCRIPTION}}", description) \
                                .replace("{{ROOT}}", "") \
                                .replace("{{ANALYTICS}}", analytics_template)

        content = re.sub(r'<head>.*?</head>', f'<head>\n{new_head}\n</head>', content, flags=re.DOTALL | re.IGNORECASE)

        # Reconstruct TopNav
        active_page = ""
        if "index" in filename: active_page = "index"
        elif "Juwan-Howard-Collection" in filename: active_page = "collection"
        elif "Baseball" in filename: active_page = "baseball"
        elif "Flawless" in filename: active_page = "flawless"
        elif "Wantlist" in filename: active_page = "wantlist"
        elif "Panini" in filename: active_page = "panini"

        new_nav = nav_template.replace("{{ROOT}}", "") \
                              .replace("{{ACTIVE_INDEX}}", 'class="active"' if active_page == "index" else "") \
                              .replace("{{ACTIVE_COLLECTION}}", 'class="active"' if active_page == "collection" else "") \
                              .replace("{{ACTIVE_BASEBALL}}", 'class="active"' if active_page == "baseball" else "") \
                              .replace("{{ACTIVE_FLAWLESS}}", 'class="active"' if active_page == "flawless" else "") \
                              .replace("{{ACTIVE_WANTLIST}}", 'class="active"' if active_page == "wantlist" else "") \
                              .replace("{{ACTIVE_PANINI}}", 'class="active"' if active_page == "panini" else "")

        # Reconstruct Footer Nav
        footer_nav = footer_nav_template.replace("{{ROOT}}", "")

        # Extract Body content
        match = re.search(r'(.*<body.*?>)(.*)(</body>.*)', content, re.DOTALL | re.IGNORECASE)
        if match:
            head_part = match.group(1)
            body_content = match.group(2)
            end_part = match.group(3)

            # Clean up body_content
            body_content = re.sub(r'<nav class="detail-nav".*?</nav>', '', body_content, flags=re.DOTALL)
            body_content = re.sub(r'<div class="topnav".*?</div>', '', body_content, flags=re.DOTALL)
            body_content = re.sub(r'<script>\s*function myFunction\(\).*?</script>', '', body_content, flags=re.DOTALL)
            body_content = re.sub(r'<main class="detail-main">', '', body_content)
            body_content = re.sub(r'</main>', '', body_content)

            # Reconstruct Body
            new_body = new_nav + '\n<main class="detail-main">\n' + body_content + footer_nav + '\n</main>'
            new_content = head_part + new_body + end_part

            with open(filepath, "w", encoding="utf-8") as f:
                f.write(new_content)
            print(f"Cleanly updated {filename}")
