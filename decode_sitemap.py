import requests
import brotli
import re

url = "https://www.maulmann.de/sitemap.xml"
response = requests.get(url)
try:
    content = brotli.decompress(response.content).decode('utf-8')
    locs = re.findall(r'<loc>(.*?)</loc>', content)
    for loc in locs:
        print(loc)
except Exception as e:
    print(f"Error: {e}")
    # Try plain text
    print(response.text)
