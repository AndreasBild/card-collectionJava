# This file will contain functions and data structures for preparing data
# for the main script.
import re

PLAYER_ID_JUWAN_HOWARD = 1

# Function to strip initial garbage characters from HTML content
GARBAGE_CHARS = b'\x1f\x8b\x08\x00\x00\x00\x00\x00\x00\xff\x00\x00\x80\xff\x7f'

def strip_initial_garbage(content: bytes) -> str:
    """
    Strips initial garbage characters from byte content and decodes to UTF-8.
    The garbage characters are \\x1f\\x8b\\x08\\x00\\x00\\x00\\x00\\x00\\x00\\xff\\x00\\x00\\x80\\xff\\x7f.
    """
    if content.startswith(GARBAGE_CHARS):
        content = content[len(GARBAGE_CHARS):]
    return content.decode('utf-8', errors='replace')

# --- SQL Parsing Logic ---

def load_sql_insert_statements_from_file(filepath: str) -> str:
    """
    Reads the content of the SQL file at filepath, extracts all INSERT INTO ... VALUES ...; statements,
    and concatenates them into a single string.
    """
    insert_statements = []
    try:
        with open(filepath, 'r', encoding='utf-8') as f: # Specify encoding
            content = f.read()
        # Regex to find INSERT INTO ... VALUES ...; statements.
        # This will find all such statements in the file.
        # Using re.IGNORECASE for `insert into` and re.DOTALL for values spanning multiple lines.
        matches = re.findall(r"INSERT INTO .*?VALUES .*?;", content, re.IGNORECASE | re.DOTALL)
        insert_statements.extend(matches)
    except FileNotFoundError:
        print(f"Error: File not found at {filepath}")
        return ""
    except Exception as e:
        print(f"An error occurred while reading {filepath}: {e}")
        return ""
    # Join all found INSERT statements. If parse_generic_sql_insert_tuples handles
    # multiple statements in one string, this is fine. Otherwise, it might need adjustment
    # or parse_generic_sql_insert_tuples needs to be called for each statement.
    # Given the current structure of parse_generic_sql_insert_tuples, it seems to expect
    # all tuples for a single table in one go, typically from one INSERT statement
    # that might look like INSERT INTO `table` VALUES (1,'a'),(2,'b');
    # If files contain multiple separate INSERT statements for the SAME table, e.g.,
    # INSERT INTO `foo` VALUES (1,2);
    # INSERT INTO `foo` VALUES (3,4);
    # Concatenating them as "INSERT INTO `foo` VALUES (1,2);\nINSERT INTO `foo` VALUES (3,4);"
    # should work if parse_generic_sql_insert_tuples iterates over matches.
    # The current parse_generic_sql_insert_tuples uses re.search, so it finds the *first* match.
    # This needs to be considered. For now, let's assume it's intended to process
    # a single (potentially large) INSERT statement per table, or the files are structured that way.
    # For safety, let's just return the whole content if it's one logical block,
    # or ensure parse_generic_sql_insert_tuples can handle multiple such blocks.
    # Re-reading parse_generic_sql_insert_tuples: it uses `insert_pattern.search(sql_content)`,
    # which finds the first match. So, if a file has multiple INSERT statements for the *same* table,
    # only the first will be processed by parse_generic_sql_insert_tuples.
    # The task description says "extract all INSERT INTO ... VALUES ...; statements" and "concatenate them".
    # This implies the parser should then be able to handle this concatenated string.
    # Let's assume parse_generic_sql_insert_tuples will be updated or is more robust.
    # For now, fulfilling the "concatenate them" part.
    return "\n".join(insert_statements)

def parse_generic_sql_insert_tuples(sql_content: str, table_name: str) -> list[tuple]:
    """
    Parses simple SQL INSERT statements for a given table and extracts value tuples.
    Example: INSERT INTO `table_name` VALUES (1,'Name'),(2,'Another Name');
    Returns a list of tuples, e.g., [(1, 'Name'), (2, 'Another Name')].
    Assumes values are simple (integers or strings).
    """
    # Regex to find INSERT INTO `table_name` VALUES (...),(...);
    # It captures the part containing the value tuples.
    # This regex is simplified and assumes a certain structure of INSERT statements.
    insert_pattern = re.compile(
        r"INSERT INTO `" + table_name + r"` VALUES\s*([^;]+);"
    )
    match = insert_pattern.search(sql_content)
    if not match:
        return []

    values_str = match.group(1)
    # Regex to extract individual tuples like (1,'Upper Deck') or (1,'Collectors Choice',1)
    # It handles numbers, quoted strings, and NULL.
    tuple_pattern = re.compile(r"\(([^)]+)\)")
    value_tuples_str = tuple_pattern.findall(values_str)

    parsed_tuples = []
    for t_str in value_tuples_str:
        elements = []
        # Split by comma, but be careful about commas inside quotes (though simple quotes are assumed here)
        # This part might need refinement if values contain escaped quotes or complex structures.
        raw_elements = t_str.split(',')
        for elem in raw_elements:
            elem = elem.strip()
            if elem.startswith("'") and elem.endswith("'"):
                elements.append(elem[1:-1]) # Strip quotes
            elif elem.lower() == 'null':
                elements.append(None)
            else:
                try:
                    elements.append(int(elem))
                except ValueError:
                    elements.append(elem) # Keep as string if not int
        parsed_tuples.append(tuple(elements))
    return parsed_tuples

# --- Lookups ---

manufacturer_lookup = {}
brand_lookup = {}
theme_lookup = {}
variant_lookup = {}

# SQL Content (to be populated by read_files in the main script or a setup phase)
# For now, these will be empty. The main script will need to pass the content.
# Or, this script could be modified to read them if run directly (for testing).

SQL_CONTENT_MANUFACTURER = ""
SQL_CONTENT_BRAND = ""
SQL_CONTENT_THEME = ""
SQL_CONTENT_VARIANT = ""


def populate_lookups(
    sql_manufacturer: str,
    sql_brand: str,
    sql_theme: str,
    sql_variant: str
):
    """
    Populates the lookup dictionaries from SQL content.
    """
    global manufacturer_lookup, brand_lookup, theme_lookup, variant_lookup

    # Populate manufacturer_lookup: {'normalized_name': id}
    manufacturer_tuples = parse_generic_sql_insert_tuples(sql_manufacturer, "card_manufacturer")
    manufacturer_lookup.clear() # Clear before populating
    for m_id, name in manufacturer_tuples:
        if name: # Ensure name is not None
            normalized_name = name.lower().replace("'", "").strip()
            manufacturer_lookup[normalized_name] = m_id

    # Populate brand_lookup: {'normalized_name': {'id': id, 'manufacturer_id': manufacturer_id, 'original_name': name}}
    brand_tuples = parse_generic_sql_insert_tuples(sql_brand, "card_brand")
    brand_lookup.clear()
    for b_id, name, manufacturer_id in brand_tuples:
        if name:
            normalized_name = name.lower().replace("'", "").replace("\t", " ").strip()
            normalized_name = " ".join(normalized_name.split()) # Normalize multiple spaces
            brand_lookup[normalized_name] = {'id': b_id, 'manufacturer_id': manufacturer_id, 'original_name': name}

    # Populate theme_lookup: {'normalized_name': {'id': id, 'brand_id': brand_id, 'original_name': name}}
    theme_tuples = parse_generic_sql_insert_tuples(sql_theme, "card_theme")
    theme_lookup.clear()
    for t_id, name, brand_id in theme_tuples:
        if name:
            normalized_name = name.lower().replace("'", "").strip()
            theme_lookup[normalized_name] = {'id': t_id, 'brand_id': brand_id, 'original_name': name}

    # Populate variant_lookup: {'normalized_name': id}
    variant_tuples = parse_generic_sql_insert_tuples(sql_variant, "variant")
    variant_lookup.clear()
    # The variant table has (id, name), name -> id
    for v_id, name in variant_tuples:
        if name:
            normalized_name = name.lower().replace("'", "").strip()
            variant_lookup[normalized_name] = v_id


if __name__ == '__main__':
    # Example usage of strip_initial_garbage
    sample_html_bytes = b'\x1f\x8b\x08\x00\x00\x00\x00\x00\x00\xff\x00\x00\x80\xff\x7f<p>Hello World</p>'
    cleaned_html = strip_initial_garbage(sample_html_bytes)
    print(f"Cleaned HTML: {cleaned_html}")
    print(f"Player ID for Juwan Howard: {PLAYER_ID_JUWAN_HOWARD}")

    # --- Example of populating lookups (requires SQL content) ---
    # This section is for demonstration. Actual population would need SQL strings.
    # Replace with actual SQL content strings for testing.
    SQL_CONTENT_MANUFACTURER_EXAMPLE = """
    INSERT INTO `card_manufacturer` VALUES (1,'Upper Deck'),(2,'Topps');
    """
    SQL_CONTENT_BRAND_EXAMPLE = """
    INSERT INTO `card_brand` VALUES (1,'Collectors Choice',1),(2,'Exquisite',1),(50,'Topps',2);
    """
    SQL_CONTENT_THEME_EXAMPLE = """
    INSERT INTO `card_theme` VALUES (1,'You Crash The Game Rookie Scoring',1),(5,'Embossed',51);
    """
    SQL_CONTENT_VARIANT_EXAMPLE = """
    INSERT INTO `variant` VALUES (1,'Base'),(2,'Refractor');
    """

    populate_lookups(
        SQL_CONTENT_MANUFACTURER_EXAMPLE,
        SQL_CONTENT_BRAND_EXAMPLE,
        SQL_CONTENT_THEME_EXAMPLE,
        SQL_CONTENT_VARIANT_EXAMPLE
    )

    print("\n--- Populated Lookups (Example) ---")
    print(f"Manufacturer Lookup: {manufacturer_lookup}")
    print(f"Brand Lookup: {brand_lookup}")
    print(f"Theme Lookup: {theme_lookup}")
    print(f"Variant Lookup: {variant_lookup}")

    # Test with a more complex name from the provided brand data
    SQL_CONTENT_BRAND_COMPLEX_NAME = """
    INSERT INTO `card_brand` VALUES (6,'SP Championship	',1);
    """ # Note the tab character in 'SP Championship	'
    populate_lookups("", SQL_CONTENT_BRAND_COMPLEX_NAME, "", "")
    # This will overwrite previous brand_lookup for this test
    print(f"Brand Lookup (Complex Name Test): {brand_lookup}")
    # Check if 'SP Championship\t' is a key
    if 'SP Championship\t' in brand_lookup:
        print("Successfully parsed brand with tab character in name.")
    else:
        print("Failed to parse brand with tab character correctly.")

    # Test with NULL foreign key
    SQL_CONTENT_THEME_NULL_FK = """
    INSERT INTO `card_theme` VALUES (7,'Rack Pack',NULL);
    """
    populate_lookups("", "", SQL_CONTENT_THEME_NULL_FK, "")
    print(f"Theme Lookup (NULL FK Test): {theme_lookup}")
    if theme_lookup.get('Rack Pack', {}).get('brand_id') is None:
        print("Successfully parsed theme with NULL brand_id.")
    else:
        print("Failed to parse theme with NULL brand_id correctly.")
