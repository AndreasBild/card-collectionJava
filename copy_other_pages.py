import os
import shutil

source_dir = "content/other"
target_dir = "output"

if not os.path.exists(target_dir):
    os.makedirs(target_dir)

for filename in os.listdir(source_dir):
    if filename.endswith(".html"):
        shutil.copy(os.path.join(source_dir, filename), os.path.join(target_dir, filename))
        print(f"Copied {filename} to {target_dir}")
