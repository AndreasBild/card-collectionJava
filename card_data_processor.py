import re
import os # Added for directory creation
import html_parser # To get raw card data
import data_preparation # For lookups and PLAYER_ID

# --- Constants & Keyword Lists ---
PLAYER_ID_JUWAN_HOWARD = data_preparation.PLAYER_ID_JUWAN_HOWARD

ROOKIE_KEYWORDS = ["rookie", "rc", "draft pick", "draft day", "collegiate", "debut"]
AUTOGRAPH_KEYWORDS = ["autograph", "signature", "ink", "signings", "auto"]
GAME_USED_KEYWORDS = ["jersey", "patch", "material", "game used", "fabric", "relic", "duds", "coverage", "shirts", "game worn", "floor"]

# Placeholder/Default IDs
UNKNOWN_MANUFACTURER_ID = 0 
UNKNOWN_BRAND_ID = 0
UNKNOWN_THEME_ID = 0 
DEFAULT_VARIANT_ID = 1 # Will be updated with actual 'Base' ID

# Global SQL content strings - to be populated by __main__ or an external caller
SQL_CONTENT_MANUFACTURER = ""
SQL_CONTENT_BRAND = ""
SQL_CONTENT_THEME = ""
SQL_CONTENT_VARIANT = ""

# Logging list for issues
processing_logs = []

def log_issue(message):
    processing_logs.append(message)

def initialize_lookups(sql_manufacturer, sql_brand, sql_theme, sql_variant):
    global DEFAULT_VARIANT_ID
    # Ensure data_preparation's global lookups are cleared and repopulated
    data_preparation.manufacturer_lookup.clear()
    data_preparation.brand_lookup.clear()
    data_preparation.theme_lookup.clear()
    data_preparation.variant_lookup.clear()
    
    data_preparation.populate_lookups(sql_manufacturer, sql_brand, sql_theme, sql_variant)
    
    base_variant_id = data_preparation.variant_lookup.get("base") # Keys are normalized now
    if base_variant_id is not None:
        DEFAULT_VARIANT_ID = base_variant_id
    else:
        # Attempt to find 'Base' if 'base' (lowercase) wasn't found, though keys should be lowercase.
        base_variant_id_cap = data_preparation.variant_lookup.get("Base") 
        if base_variant_id_cap is not None:
             DEFAULT_VARIANT_ID = base_variant_id_cap
             log_issue("Warning: 'Base' variant found with capital 'B', but keys should be normalized to lowercase. Check variant data population.")
        else:
            log_issue("Critical Warning: 'Base' or 'base' variant not found in variant_lookup. Using 1 as DEFAULT_VARIANT_ID.")
            DEFAULT_VARIANT_ID = 1 

def parse_limited_string(limited_str: str) -> tuple[int, int]:
    limited_str = limited_str.strip()
    if not limited_str or limited_str == "--":
        return 0, 0
    match_xy = re.fullmatch(r"#\s*(\d+)\s*/\s*(\d+)", limited_str)
    if match_xy:
        return int(match_xy.group(2)), int(match_xy.group(1)) # print_run, serial_number
    match_y = re.fullmatch(r"(\d+)", limited_str)
    if match_y:
        return int(match_y.group(1)), 0
    match_plain_xy = re.fullmatch(r"(\d+)\s*/\s*(\d+)", limited_str)
    if match_plain_xy:
        return int(match_plain_xy.group(2)), int(match_plain_xy.group(1))
    log_issue(f"Warning: Unparseable limited_string format: '{limited_str}'. Using defaults (0,0).")
    return 0, 0

def parse_brand_string(raw_brand_str: str, season: str) -> dict:
    original_raw_brand = raw_brand_str # Store original raw brand string
    working_str_lower = raw_brand_str.lower().replace("'", "") # Normalize input
    
    confidence = 'low' # Initialize confidence
    remaining_string_after_parsing = working_str_lower # Initialize remaining string

    attributes = {
        'rookie_card': False, 'autograph': False, 'game_used_material': False,
        'manufacturer_id': UNKNOWN_MANUFACTURER_ID, 'brand_id': UNKNOWN_BRAND_ID,
        'variant_id': DEFAULT_VARIANT_ID, 'theme_id': UNKNOWN_THEME_ID,
        # New fields to be added later
    }

    for keyword in ROOKIE_KEYWORDS:
        if keyword in working_str_lower: attributes['rookie_card'] = True; break
    if season == "1994-95" or "college" in season.lower() or "draft" in season.lower():
        attributes['rookie_card'] = True
    for keyword in AUTOGRAPH_KEYWORDS:
        if keyword in working_str_lower: attributes['autograph'] = True; break
    for keyword in GAME_USED_KEYWORDS:
        if keyword in working_str_lower: attributes['game_used_material'] = True; break

    identified_manufacturer_id = UNKNOWN_MANUFACTURER_ID
    identified_brand_id = UNKNOWN_BRAND_ID
    identified_variant_id = DEFAULT_VARIANT_ID # Base variant by default
    identified_theme_id = UNKNOWN_THEME_ID
    
    # Tentative string state after parts are removed.
    # Starts as the full normalized string, and parts are removed.
    # The final state of this will be the 'remaining_string_after_parsing'.
    current_parse_string = working_str_lower 
    
    found_brand_details = None
    
    # 1. Brand Identification
    sorted_brands_by_len = sorted(data_preparation.brand_lookup.keys(), key=len, reverse=True)
    for brand_name_key in sorted_brands_by_len:
        # Using a regex pattern that ensures brand_name_key is a whole word.
        # \b checks for word boundaries.
        pattern = r'\b' + re.escape(brand_name_key) + r'\b'
        if re.search(pattern, current_parse_string):
            brand_data = data_preparation.brand_lookup[brand_name_key]
            found_brand_details = {'name': brand_name_key, 'id': brand_data['id'], 'manufacturer_id': brand_data.get('manufacturer_id')}
            identified_brand_id = found_brand_details['id']
            if found_brand_details['manufacturer_id']:
                identified_manufacturer_id = found_brand_details['manufacturer_id']
            
            # Remove found brand from current_parse_string
            current_parse_string = re.sub(pattern, '', current_parse_string, count=1).strip()
            current_parse_string = " ".join(current_parse_string.split()) # Normalize spaces
            break 
            # Once a brand is found, we assume it's the correct one and stop searching for other brands.

    # 2. Variant Identification (searches in the remainder string after brand removal)
    # Only search for non-base variants. Base is default.
    string_for_variant_search = current_parse_string 
    identified_v_name_str = None # To store the name of the identified variant
    if string_for_variant_search: # Only search if there's something left
        # Sort variants by length, excluding 'base' which is default.
        sorted_variants_by_len = sorted([v for v in data_preparation.variant_lookup.keys() if v.lower() != "base"], key=len, reverse=True)
        for variant_name_key in sorted_variants_by_len:
            pattern = r'\b' + re.escape(variant_name_key) + r'\b'
            if re.search(pattern, string_for_variant_search):
                identified_variant_id = data_preparation.variant_lookup[variant_name_key]
                identified_v_name_str = variant_name_key
                # Remove found variant from current_parse_string
                current_parse_string = re.sub(pattern, '', string_for_variant_search, count=1).strip()
                current_parse_string = " ".join(current_parse_string.split())
                break 
                # Stop after finding the first (longest) matching variant.

    # 3. Theme Identification (searches in remainder after brand and variant)
    string_for_theme_search = current_parse_string
    # Only attempt to find a theme if a brand has been identified
    if identified_brand_id != UNKNOWN_BRAND_ID and string_for_theme_search:
        # Filter themes for the identified brand and sort by length.
        sorted_themes_for_brand = sorted(
            [t_name for t_name, t_info in data_preparation.theme_lookup.items() if t_info.get('brand_id') == identified_brand_id],
            key=len, reverse=True
        )
        for theme_name_key in sorted_themes_for_brand:
            pattern = r'\b' + re.escape(theme_name_key) + r'\b'
            if re.search(pattern, string_for_theme_search):
                # Theme name matches, and it's for the correct brand.
                identified_theme_id = data_preparation.theme_lookup[theme_name_key]['id']
                # Remove found theme from current_parse_string
                current_parse_string = re.sub(pattern, '', string_for_theme_search, count=1).strip()
                current_parse_string = " ".join(current_parse_string.split())
                break 
                # Stop after finding the first (longest) matching theme for this brand.
    
    # Update remaining_string_after_parsing with the final state of current_parse_string
    remaining_string_after_parsing = current_parse_string

    # 4. Fallback for Manufacturer if brand is still unknown
    # This part might need adjustment if the goal is to reduce its scope or integrate it
    # more cleanly with the confidence logic. For now, it's kept similar to original.
    identified_m_name_from_fallback = None
    if identified_brand_id == UNKNOWN_BRAND_ID and not found_brand_details: # Only if primary brand search failed
        temp_search_str_for_mfr_fb = working_str_lower # Start with original normalized string for mfr fallback
        
        # Try to remove already identified variant if any, from this temp string
        if identified_v_name_str:
             pattern_v_fb = r'\b' + re.escape(identified_v_name_str) + r'\b'
             temp_search_str_for_mfr_fb = re.sub(pattern_v_fb, '', temp_search_str_for_mfr_fb, count=1).strip()
             temp_search_str_for_mfr_fb = " ".join(temp_search_str_for_mfr_fb.split())

        string_after_mfr_removal_fb = temp_search_str_for_mfr_fb
        sorted_mfrs_by_len = sorted(data_preparation.manufacturer_lookup.keys(), key=len, reverse=True)
        
        for mfr_name_key in sorted_mfrs_by_len:
            pattern_mfr = r'\b' + re.escape(mfr_name_key) + r'\b'
            if re.search(pattern_mfr, temp_search_str_for_mfr_fb):
                if identified_manufacturer_id == UNKNOWN_MANUFACTURER_ID: # Only set if not already set by a brand
                     identified_manufacturer_id = data_preparation.manufacturer_lookup[mfr_name_key]
                identified_m_name_from_fallback = mfr_name_key
                # Update string_after_mfr_removal_fb by removing mfr name
                string_after_mfr_removal_fb = re.sub(pattern_mfr, '', temp_search_str_for_mfr_fb, count=1).strip()
                string_after_mfr_removal_fb = " ".join(string_after_mfr_removal_fb.split())
                break
        
        # If manufacturer was also a brand (e.g., "Topps", "Upper Deck")
        if identified_m_name_from_fallback and identified_m_name_from_fallback in data_preparation.brand_lookup:
            # Check if the remaining part of string_after_mfr_removal_fb is empty or just noise
            # This implies the mfr name itself was the brand.
            if not string_after_mfr_removal_fb: # Nothing left after mfr name removal
                brand_data_fb = data_preparation.brand_lookup[identified_m_name_from_fallback]
                identified_brand_id = brand_data_fb['id']
                if identified_manufacturer_id == UNKNOWN_MANUFACTURER_ID and brand_data_fb.get('manufacturer_id'):
                    identified_manufacturer_id = brand_data_fb['manufacturer_id']
                elif identified_manufacturer_id == UNKNOWN_MANUFACTURER_ID: # Mfr name is brand, but brand has no explicit mfr_id
                     identified_manufacturer_id = data_preparation.manufacturer_lookup[identified_m_name_from_fallback]


                remaining_string_after_parsing = string_after_mfr_removal_fb # Should be empty

                # Attempt theme search for this fallback brand
                # No need to re-search variant as it was done on working_str_lower initially if no brand was found
                # string_for_theme_search for this case would be an empty string if mfr was brand and nothing else.
                # This means if a theme existed, it should have been part of string_after_mfr_removal_fb
                # This section might need more refinement based on expected raw strings.
                # For now, if mfr is brand and string_after_mfr_removal_fb is empty, assume no theme unless it was part of a complex name.
            else: # Mfr found, and it's a brand, but there's still text left. This implies a more complex brand name not in brand_lookup.
                log_issue(f"Warning (FB-MfrAsBrand): Brand candidate '{string_after_mfr_removal_fb}' (after Mfr '{identified_m_name_from_fallback}' removed) not in lookup. Raw: '{raw_brand_str}'")
                # In this case, brand remains unknown for confidence, remaining_string is string_after_mfr_removal_fb
                remaining_string_after_parsing = string_after_mfr_removal_fb
        elif string_after_mfr_removal_fb and identified_m_name_from_fallback : # Mfr found, not a brand, but text remains
            log_issue(f"Warning (FB-MfrOnly): Brand candidate '{string_after_mfr_removal_fb}' (after Mfr '{identified_m_name_from_fallback}') not in lookup. Raw: '{raw_brand_str}'")
            remaining_string_after_parsing = string_after_mfr_removal_fb
        elif not string_after_mfr_removal_fb and identified_m_name_from_fallback: # Only Mfr, not a brand, nothing left.
             log_issue(f"Info (FB-MfrOnlyEmpty): Raw string seems to be only Manufacturer ('{identified_m_name_from_fallback}') not listed as brand. Raw: '{raw_brand_str}'")
             remaining_string_after_parsing = string_after_mfr_removal_fb # Should be empty
        elif not identified_m_name_from_fallback and string_after_mfr_removal_fb: # No Mfr found, text remains (original variant-stripped string)
            # This is the case where no brand, no mfr was found. `remaining_string_after_parsing` was set after variant attempt.
            pass # remaining_string_after_parsing is already set from before this fallback block

    # 5. Confidence Logic
    if identified_brand_id != UNKNOWN_BRAND_ID:
        # Stricter condition for 'high' confidence:
        # Brand must be known, remaining string must be empty,
        # AND either a theme or a non-default variant must have been identified.
        if not remaining_string_after_parsing.strip() and \
           (identified_theme_id != UNKNOWN_THEME_ID or identified_variant_id != DEFAULT_VARIANT_ID):
            confidence = 'high'
        else:
            # Covers:
            # 1. Brand known, but remaining string is not empty.
            # 2. Brand known, remaining string empty, but no theme and no non-default variant found.
            confidence = 'low'
    else: # No brand identified
        confidence = 'low'
        # If no brand, any theme_id found is invalid (theme must belong to a brand)
        # This was already done implicitly as theme search requires identified_brand_id.
        # To be absolutely sure, and if theme logic were different:
        identified_theme_id = UNKNOWN_THEME_ID
        # remaining_string_after_parsing was set based on initial working_str_lower minus any variant found.
        # If no brand and no variant, it's still the full working_str_lower.

    # Ensure theme_id is None if it's UNKNOWN_THEME_ID for the final attribute dictionary
    final_theme_id = identified_theme_id if identified_theme_id != UNKNOWN_THEME_ID else None
    
    # If confidence is 'low', and a theme was identified (i.e. final_theme_id is not None),
    # it should be treated as if no theme was found (set to None).
    # This covers cases where a theme was found for the correct brand, but other issues cause low confidence
    # (e.g. remaining string, or brand known but no theme/variant for high confidence).
    if confidence == 'low' and final_theme_id is not None:
        # Log if we are nullifying a theme that was initially identified.
        if identified_theme_id != UNKNOWN_THEME_ID : # Check original before it became final_theme_id
             log_issue(f"Info: Low confidence ('{confidence}') for raw: '{raw_brand_str}'. Nullifying originally identified theme_id '{identified_theme_id}'. Remaining: '{remaining_string_after_parsing.strip()}'")
        final_theme_id = None

    attributes.update({
        'manufacturer_id': identified_manufacturer_id,
        'brand_id': identified_brand_id,
        'variant_id': identified_variant_id,
        'theme_id': final_theme_id,
        'confidence': confidence,
        'original_raw_brand': original_raw_brand,
        'remaining_string_after_parsing': remaining_string_after_parsing.strip()
    })

    # Final MFR ID fixup if not set by brand but brand is known (from original logic)
    if attributes['manufacturer_id'] == UNKNOWN_MANUFACTURER_ID and attributes['brand_id'] != UNKNOWN_BRAND_ID:
        # This loop is inefficient, better to use a reverse lookup if available or build one.
        # For now, keeping structure but acknowledging inefficiency.
        brand_name_for_mfr_fc = [bname for bname, binfo in data_preparation.brand_lookup.items() if binfo['id'] == attributes['brand_id']]
        if brand_name_for_mfr_fc:
            brand_detail = data_preparation.brand_lookup.get(brand_name_for_mfr_fc[0])
            if brand_detail and brand_detail.get('manufacturer_id'):
                attributes['manufacturer_id'] = brand_detail['manufacturer_id']

    if attributes['brand_id'] == UNKNOWN_BRAND_ID and raw_brand_str.strip():
        log_issue(f"FinalReport: Brand UNRESOLVED (Confidence: {confidence}) for raw_brand: '{raw_brand_str}', Remaining: '{remaining_string_after_parsing.strip()}'")
    if attributes['manufacturer_id'] == UNKNOWN_MANUFACTURER_ID and attributes['brand_id'] == UNKNOWN_BRAND_ID and raw_brand_str.strip():
        log_issue(f"FinalReport: Manufacturer UNRESOLVED (Confidence: {confidence}) for raw_brand: '{raw_brand_str}' (Brand also unknown).")
    
    return attributes

def process_raw_card_data(raw_cards_list: list[dict]) -> tuple[list[dict], list[dict]]:
    high_confidence_cards = []
    questionable_cards_log_entries = []

    if not data_preparation.manufacturer_lookup: 
        log_issue("Critical: Lookups not populated in process_raw_card_data.")
        # Return empty lists as per expected tuple output type
        return [], [] 
        
    for raw_card in raw_cards_list:
        print_run, serial_number = parse_limited_string(raw_card['limited_string'])
        brand_parse_results = parse_brand_string(raw_card['raw_brand'], raw_card['season'])

        if brand_parse_results['confidence'] == 'high':
            high_confidence_card_dict = {
                'player_id': PLAYER_ID_JUWAN_HOWARD,
                'season': raw_card['season'],
                'card_number': raw_card['card_number'],
                'print_run': print_run,
                'serial_number': serial_number,
                'manufacturer_id': brand_parse_results['manufacturer_id'],
                'brand_id': brand_parse_results['brand_id'],
                'variant_id': brand_parse_results['variant_id'],
                'theme_id': brand_parse_results['theme_id'], # Will be None if no high-confidence theme
                'rookie_card': brand_parse_results['rookie_card'],
                'autograph': brand_parse_results['autograph'],
                'game_used_material': brand_parse_results['game_used_material']
            }
            high_confidence_cards.append(high_confidence_card_dict)
        else: # confidence == 'low'
            reason_for_low_confidence = "No brand identified" if brand_parse_results['brand_id'] == UNKNOWN_BRAND_ID else "Unparsed text remaining or ambiguous parse" # Reverted to local UNKNOWN_BRAND_ID
            questionable_card_dict = {
                'original_raw_brand': brand_parse_results['original_raw_brand'],
                'season': raw_card['season'],
                'card_number': raw_card['card_number'],
                'parsed_brand_id': brand_parse_results['brand_id'], # Key updated
                'parsed_theme_id': brand_parse_results['theme_id'], # Key updated. Value is None if low confidence nullified it.
                'parsed_variant_id': brand_parse_results['variant_id'], # Key updated
                'parsed_manufacturer_id': brand_parse_results['manufacturer_id'], # Key is correct as per prompt
                'remaining_string': brand_parse_results['remaining_string_after_parsing'], # Key updated
                'reason': reason_for_low_confidence
            }
            questionable_cards_log_entries.append(questionable_card_dict)
            
    return high_confidence_cards, questionable_cards_log_entries

def write_questionable_cards_to_file(questionable_cards_log_entries: list[dict]):
    """
    Writes low-confidence parsing results to a log file.
    """
    output_filepath = "output/questionable_parses.txt"
    
    try:
        # Ensure the output directory exists
        os.makedirs("output", exist_ok=True)
        
        with open(output_filepath, 'w', encoding='utf-8') as f:
            if not questionable_cards_log_entries:
                f.write("No questionable card parses to log.\n")
                return

            for entry in questionable_cards_log_entries:
                # Helper to convert None to "None" string for display
                def val_to_str(val):
                    return str(val) if val is not None else "None"

                f.write("----------------------------------------\n")
                f.write(f"Original Raw Brand: {entry.get('original_raw_brand', 'N/A')}\n")
                f.write(f"Season: {entry.get('season', 'N/A')}\n")
                f.write(f"Card Number: {entry.get('card_number', 'N/A')}\n")
                f.write(f"Reason for Low Confidence: {entry.get('reason', 'N/A')}\n")
                f.write("--- Parsing Details ---\n")
                f.write(f"Remaining Unparsed Text: \"{entry.get('remaining_string', '')}\"\n") # Ensure quotes for clarity
                f.write(f"Identified Brand ID: {val_to_str(entry.get('parsed_brand_id'))}\n")
                f.write(f"Identified Theme ID: {val_to_str(entry.get('parsed_theme_id'))}\n")
                f.write(f"Identified Variant ID: {val_to_str(entry.get('parsed_variant_id'))}\n")
                f.write(f"Identified Manufacturer ID: {val_to_str(entry.get('parsed_manufacturer_id'))}\n")
                f.write("----------------------------------------\n\n")
        
        print(f"Questionable card parse log written to {output_filepath}")

    except IOError as e:
        log_issue(f"Error writing questionable cards to file: {e}")
        print(f"Error: Could not write questionable cards to {output_filepath}. Check logs.")


def main_processing_logic():
    """Wraps the main processing logic of card_data_processor.py to be callable."""
    # Ensure lookups are initialized using global SQL content strings.
    # These globals must be populated before this function is called.
    if not (SQL_CONTENT_MANUFACTURER and SQL_CONTENT_BRAND and SQL_CONTENT_THEME and SQL_CONTENT_VARIANT):
        log_issue("Critical: SQL_CONTENT globals in card_data_processor are not set before main_processing_logic call.")
        # Attempt to use example SQL as a last resort if direct run of this file.
        if __name__ == "__main__": # only if this file is run directly.
             print("DEV FALLBACK: main_processing_logic called when SQL_CONTENT empty; using __main__ population.")
             # The __main__ block below will populate them.
        else: # Imported, and SQL_CONTENT not set by importer.
            print("CRITICAL ERROR: card_data_processor.main_processing_logic() called as import without SQL_CONTENT set.")
            return [], ["CRITICAL ERROR: SQL_CONTENT not set."]


    # If lookups aren't populated despite SQL_CONTENT potentially being set (e.g. first time run)
    if not data_preparation.manufacturer_lookup: 
        print("Info: card_data_processor.main_processing_logic: Lookups empty, initializing.")
        initialize_lookups(SQL_CONTENT_MANUFACTURER, SQL_CONTENT_BRAND, SQL_CONTENT_THEME, SQL_CONTENT_VARIANT)

    current_run_logs = []
    # Temporarily redirect global log_issue to a local list for this run
    # This is a simple way to isolate logs for a specific call if the module is long-lived.
    # More robust logging would use a logging instance.
    original_log_handler = log_issue.__globals__['log_issue']
    
    def local_log_issue(message):
        current_run_logs.append(message)
    log_issue.__globals__['log_issue'] = local_log_issue
    
    raw_card_data_list = html_parser.parse_seasons_from_html('output/index.html')

    if not raw_card_data_list:
        local_log_issue("Critical: No raw card data from html_parser in main_processing_logic.")
        log_issue.__globals__['log_issue'] = original_log_handler # Restore global logger
        processing_logs.extend(current_run_logs) # Add local logs to global
        return [], current_run_logs

    structured_card_list = process_raw_card_data(raw_card_data_list)
    
    log_issue.__globals__['log_issue'] = original_log_handler # Restore global logger
    processing_logs.extend(current_run_logs) # Add local logs to global list

    # structured_card_list is now a tuple: (high_confidence_cards, questionable_cards_log_entries)
    # Return this tuple along with the general current_run_logs
    return structured_card_list, current_run_logs 

# This exposed function will be called by sql_generator
def get_structured_card_data() -> list[dict]: # Explicitly list of dict for high-confidence cards
    """
    Public function to get structured card data.
    It processes raw card data, writes questionable parses to a file,
    and returns only the list of high-confidence card data.
    Ensures SQL content is loaded if run as part of a sequence.
    """
    # If this module's __main__ already ran (e.g. direct execution), SQL_CONTENTs are set.
    # If imported, the importer or a main orchestrator should ensure they are set.
    # For now, we assume if __main__ of THIS SCRIPT hasn't run, SQL_CONTENTs might be empty
    # unless set by an importer.
    if not (SQL_CONTENT_MANUFACTURER and SQL_CONTENT_BRAND and SQL_CONTENT_THEME and SQL_CONTENT_VARIANT) \
            and __name__ != "__main__": # Only try example if imported AND not set.
        print("Warning: get_structured_card_data() called (likely imported), SQL_CONTENTs not set. Using example SQL.")
        temp_initialize_with_example_sql_if_empty()

    # If lookups are still not populated (e.g. SQL_CONTENTs were set by importer but init not called)
    if not data_preparation.manufacturer_lookup:
        print("Info: get_structured_card_data: Lookups empty, initializing with current SQL_CONTENTs.")
        initialize_lookups(SQL_CONTENT_MANUFACTURER, SQL_CONTENT_BRAND, SQL_CONTENT_THEME, SQL_CONTENT_VARIANT)
        
    # main_processing_logic now returns ((high_conf_cards, questionable_entries), general_logs_from_run)
    card_data_tuple, general_logs_from_run = main_processing_logic()
    
    high_confidence_cards, questionable_cards_log_entries = card_data_tuple
    
    # Write questionable cards to file
    write_questionable_cards_to_file(questionable_cards_log_entries)
    
    # Handle general logs if necessary (e.g., print summary or add to global logs)
    # For now, the prompt implies focusing on the primary data flow.
    # These logs are already added to global processing_logs within main_processing_logic.
    if general_logs_from_run:
        print(f"Info: get_structured_card_data: main_processing_logic produced {len(general_logs_from_run)} general log entries.")

    # Return only the high-confidence cards list
    return high_confidence_cards


def temp_initialize_with_example_sql_if_empty():
    """A temporary helper to initialize with example SQL if lookups are empty.
       ONLY FOR unblocking dependent modules when card_data_processor is imported
       and its own __main__ hasn't run to set up proper SQL.
    """
    global SQL_CONTENT_MANUFACTURER, SQL_CONTENT_BRAND, SQL_CONTENT_THEME, SQL_CONTENT_VARIANT
    print("DEVELOPMENT FALLBACK in card_data_processor: Initializing lookups with EXAMPLE SQL.")
    SQL_CONTENT_MANUFACTURER = "INSERT INTO `card_manufacturer` VALUES (1,'Upper Deck'),(2,'Topps');" # Simplified
    SQL_CONTENT_BRAND = "INSERT INTO `card_brand` VALUES (1,'Collectors Choice',1),(4,'Upper Deck',1),(50,'Topps',2), (56,'Bowman''s Best	',2);" # Simplified
    SQL_CONTENT_THEME = "INSERT INTO `card_theme` VALUES (1,'You Crash The Game Rookie Scoring',1),(3,'Signature',1);" # Simplified
    SQL_CONTENT_VARIANT = "INSERT INTO `variant` VALUES (1,'Base'),(2,'Refractor'),(5,'Gold'),(6,'Silver');" # Simplified
    initialize_lookups(SQL_CONTENT_MANUFACTURER, SQL_CONTENT_BRAND, SQL_CONTENT_THEME, SQL_CONTENT_VARIANT)


if __name__ == '__main__':
    # Populate global SQL content strings for direct execution
    SQL_CONTENT_MANUFACTURER = """
CREATE DATABASE  IF NOT EXISTS `cardcollection` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `cardcollection`;
INSERT INTO `card_manufacturer` VALUES (1,'Upper Deck'),(2,'Topps'),(3,'Fleer'),(4,'Leaf'),(5,'Panini'),(6,'Classic'),(7,'Score Board');
    """
    SQL_CONTENT_BRAND = """
USE `cardcollection`;
INSERT INTO `card_brand` VALUES (1,'Collectors Choice',1),(2,'Exquisite',1),(3,'SP Authentic',1),(4,'Upper Deck',1),(5,'SP',1),(6,'SP Championship	',1),(7,'UD3	',1),(8,'SPx',1),(9,'Hardcourt',1),(10,'Black Diamond',1),(11,'SPx Finite',1),(12,'Choice',1),(13,'Ionix',1),(14,'Ovation',1),(15,'Encore',1),(16,'HoloGrFX',1),(17,'Retro',1),(18,'MVP',1),(19,'Gold Reserve',1),(20,'Victory',1),(21,'Reserve',1),(22,'UDx',1),(23,'SLAM',1),(24,'SP Game Used',1),(25,'Glass',1),(26,'Authentics ',1),(27,'Sweet Shot',1),(28,'Ultimate Victory',1),(29,'Honor Roll',1),(30,'Inspiration',1),(31,'SP Authentic Limited',1),(32,'Flight Team',1),(33,'Finite',1),(34,'Ultimate Collection',1),(35,'Championship Drive ',1),(36,'Exclusives',1),(37,'Standing O',1),(38,'Legends',1),(39,'R-Class',1),(40,'Trilogy',1),(41,'Reflections',1),(42,'ESPN',1),(43,'Rookie Debut',1),(44,'Signature Edition',1),(50,'Topps',2),(51,'Embossed',2),(52,'Finest',2),(53,'Stadium Club',2),(54,'Stadium Club Members Only',2),(55,'Gallery',2),(56,'Bowman\'s Best	',2),(57,'Chrome',2),(58,'Gold Label',2),(59,'Tip Off',2),(60,'Heritage',2),(61,'Stars',2),(62,'Reserve',2),(63,'Pristine',2),(64,'Jersey Edition',2),(65,'Bazooka',2),(66,'Turkey Red',2),(67,'Contemporary Collection',2),(68,'Rookie Matrix',2),(69,'First Edition',2),(70,'Luxury Box',2),(71,'Total',2),(80,'Flair',3),(81,'Fleer',3),(82,'Jam Session',3);
    """
    SQL_CONTENT_THEME = """
USE `cardcollection`;
INSERT INTO `card_theme` VALUES (1,'You Crash The Game Rookie Scoring',1),(2,'You Crash The Game Rookie Scoring Redemption',1),(3,'Signature',1),(4,'Lottery Pick',1),(5,'Embossed',51),(6,'Collegiate Best',51),(7,'Rack Pack',NULL);
    """
    SQL_CONTENT_VARIANT = """
USE `cardcollection`;
INSERT INTO `variant` VALUES (1,'Base'),(2,'Refractor'),(3,'Die Cut'),(5,'Gold'),(6,'Silver'),(7,'Bronze'),(8,'Platinum'),(9,'Diamond'),(10,'Emerald'),(11,'Ruby'),(12,'Black'),(13,'White'),(14,'Yellow'),(15,'Cyan'),(16,'Magenta'),(17,'Red'),(18,'Blue'),(19,'Grean');
    """
    
    # Initialize lookups using the full SQL content defined above for direct script run
    initialize_lookups(SQL_CONTENT_MANUFACTURER, SQL_CONTENT_BRAND, SQL_CONTENT_THEME, SQL_CONTENT_VARIANT)

    print("Executing card_data_processor.py as main script...")
    # Call main_processing_logic which now returns logs too
    structured_cards, logs_from_run = main_processing_logic() 
    
    print(f"Main script execution: Successfully processed {len(structured_cards)} cards.")
    if structured_cards:
        print("\nFirst 5 structured cards (from __main__):")
        for i, card in enumerate(structured_cards[:5]):
            print(f"{i+1}: {card}")
    
    # Use the returned logs for printing in __main__
    if logs_from_run:
        print("\n--- Processing Issues Logged (from __main__ run) ---")
        log_counts = {}
        for log_entry in logs_from_run: # Use logs specific to this run
            log_counts[log_entry] = log_counts.get(log_entry, 0) + 1
        sorted_log_counts = sorted(log_counts.items(), key=lambda item: item[1], reverse=True)
        print(f"Total unique issues from this run: {len(sorted_log_counts)}")
        for log_entry, count in sorted_log_counts[:20]:
            print(f"({count} times) {log_entry}")
    else:
        print("\nNo processing issues logged from this __main__ run.")
            
    print("\nCard data processing (direct script run) finished.")
