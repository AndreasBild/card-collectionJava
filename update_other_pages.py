import os
import re

nav_html = """
    <div class="topnav" id="myTopnav">
        <a href="index.html">Home</a>
        <a href="Juwan-Howard-Collection.html">Juwan Howard PC</a>
        <a href="Baseball.html">Baseball</a>
        <a href="Flawless.html">Flawless</a>
        <a href="Wantlist.html">Wantlist</a>
        <a href="Panini.html">Panini</a>
        <a href="javascript:void(0);" class="icon" onclick="myFunction()">
            <i class="fa fa-bars"></i>
        </a>
    </div>

    <script>
    function myFunction() {
      var x = document.getElementById("myTopnav");
      if (x.className === "topnav") {
        x.className += " responsive";
      } else {
        x.className = "topnav";
      }
    }
    </script>"""

footer_nav = """
    <nav class="detail-nav" style="background: none; border: none; padding: 20px 0; justify-content: center;">
        <div style="display: flex; gap: 10px; flex-wrap: wrap; justify-content: center;">
            <a href="index.html" class="modern-button" title="Home">Home</a>
            <a href="Juwan-Howard-Collection.html" class="modern-button" title="Juwan Howard PC">Juwan Howard PC</a>
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

        # Ensure Font Awesome is in the head
        if "font-awesome" not in content.lower():
            content = content.replace("</head>", '<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.7.0/css/font-awesome.min.css">\n</head>')

        # Extract Head and Body
        match = re.search(r'(.*<body.*?>)(.*)(</body>.*)', content, re.DOTALL | re.IGNORECASE)
        if match:
            head_part = match.group(1)
            body_content = match.group(2)
            end_part = match.group(3)

            # Clean up body_content: remove previous navs and main wrappers
            body_content = re.sub(r'<nav class="detail-nav".*?</nav>', '', body_content, flags=re.DOTALL)
            body_content = re.sub(r'<div class="topnav".*?</div>', '', body_content, flags=re.DOTALL)
            body_content = re.sub(r'<script>\s*function myFunction\(\).*?</script>', '', body_content, flags=re.DOTALL)
            body_content = re.sub(r'<main class="detail-main">', '', body_content)
            body_content = re.sub(r'</main>', '', body_content)

            # Also clean up any lingering Juwan Howard PC
            body_content = body_content.replace("Juwan Howard PC", "Juwan Howard PC")

            # Reconstruct Body
            new_body = nav_html + '<main class="detail-main">' + body_content + footer_nav + '</main>'

            new_content = head_part + new_body + end_part

            with open(filepath, "w", encoding="utf-8") as f:
                f.write(new_content)
            print(f"Cleanly updated {filename}")
