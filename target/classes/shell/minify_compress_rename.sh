#!/bin/bash

# Directory containing the files to compress
DIRECTORY="$1"

# Check if the directory exists
if [ ! -d "$DIRECTORY" ]; then
  echo "Directory $DIRECTORY does not exist."
  exit 1
fi

# Minify and compress all CSS and HTML files in the directory
for file in "$DIRECTORY"/*; do
  if [ -f "$file" ]; then
    case "$file" in
      *.css)
        echo "Minifying CSS file: $file"
        uglifycss "$file" > "${file}.min"
        mv "${file}.min" "$file"
        ;;
      *.html)
        echo "Minifying HTML file: $file"
        html-minifier --collapse-whitespace --remove-comments --remove-redundant-attributes --remove-empty-attributes --minify-css true --minify-js true "$file" -o "$file"
        ;;
    esac

    # Compress the file with gzip -9
    gzip -9 "$file"
  fi
done

# Rename all .gz files to remove the .gz extension
for gz_file in "$DIRECTORY"/*.gz; do
  if [ -f "$gz_file" ]; then
    mv "$gz_file" "${gz_file%.gz}"
  fi
done

echo "Minification, compression, and renaming completed."
