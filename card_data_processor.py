import re
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
    working_str_lower = raw_brand_str.lower().replace("'", "") # Normalize input

    attributes = {
        'rookie_card': False, 'autograph': False, 'game_used_material': False,
        'manufacturer_id': UNKNOWN_MANUFACTURER_ID, 'brand_id': UNKNOWN_BRAND_ID,
        'variant_id': DEFAULT_VARIANT_ID, 'theme_id': UNKNOWN_THEME_ID,
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
    identified_variant_id = DEFAULT_VARIANT_ID
    identified_theme_id = UNKNOWN_THEME_ID
    
    str_after_brand_match = working_str_lower
    found_brand_details = None

    sorted_brands_by_len = sorted(data_preparation.brand_lookup.keys(), key=len, reverse=True)
    for brand_name_key in sorted_brands_by_len: # brand_name_key is already normalized
        if re.search(r'\b' + re.escape(brand_name_key) + r'\b', working_str_lower):
            brand_data = data_preparation.brand_lookup[brand_name_key]
            found_brand_details = {'name': brand_name_key, 'id': brand_data['id'], 'manufacturer_id': brand_data.get('manufacturer_id')}
            identified_brand_id = found_brand_details['id']
            if found_brand_details['manufacturer_id']:
                identified_manufacturer_id = found_brand_details['manufacturer_id']
            str_after_brand_match = re.sub(r'\b' + re.escape(brand_name_key) + r'\b', '', working_str_lower, count=1).strip()
            str_after_brand_match = " ".join(str_after_brand_match.split())
            break

    string_for_variant_search = str_after_brand_match if found_brand_details else working_str_lower
    sorted_variants_by_len = sorted([v for v in data_preparation.variant_lookup.keys() if v != "base"], key=len, reverse=True)
    identified_v_name_str = None
    if string_for_variant_search:
        for variant_name_key in sorted_variants_by_len: # variant_name_key is normalized
            if re.search(r'\b' + re.escape(variant_name_key) + r'\b', string_for_variant_search):
                identified_variant_id = data_preparation.variant_lookup[variant_name_key]
                identified_v_name_str = variant_name_key
                if found_brand_details: # If brand was found, update str_after_brand_match
                    str_after_brand_match = re.sub(r'\b' + re.escape(variant_name_key) + r'\b', '', str_after_brand_match, count=1).strip()
                    str_after_brand_match = " ".join(str_after_brand_match.split())
                break
    
    string_for_theme_search = ""
    if found_brand_details: string_for_theme_search = str_after_brand_match
    elif identified_v_name_str: # No brand, but variant found. Remove variant from full working_str_lower for theme search.
        string_for_theme_search = re.sub(r'\b' + re.escape(identified_v_name_str) + r'\b', '', working_str_lower, count=1).strip()
        string_for_theme_search = " ".join(string_for_theme_search.split())
    elif not found_brand_details and not identified_v_name_str: string_for_theme_search = working_str_lower

    if identified_brand_id != UNKNOWN_BRAND_ID and string_for_theme_search:
        sorted_themes_for_brand = sorted([t_name for t_name, t_info in data_preparation.theme_lookup.items() if t_info['brand_id'] == identified_brand_id], key=len, reverse=True)
        for theme_name_key in sorted_themes_for_brand: # theme_name_key is normalized
            if re.search(r'\b' + re.escape(theme_name_key) + r'\b', string_for_theme_search):
                identified_theme_id = data_preparation.theme_lookup[theme_name_key]['id']
                break
    
    identified_m_name_from_fallback = None
    if identified_brand_id == UNKNOWN_BRAND_ID: # Fallback if no brand found by name
        string_after_mfr_removal_fb = working_str_lower
        sorted_mfrs_by_len = sorted(data_preparation.manufacturer_lookup.keys(), key=len, reverse=True)
        for mfr_name_key in sorted_mfrs_by_len: # mfr_name_key is normalized
            if re.search(r'\b' + re.escape(mfr_name_key) + r'\b', working_str_lower):
                if identified_manufacturer_id == UNKNOWN_MANUFACTURER_ID:
                     identified_manufacturer_id = data_preparation.manufacturer_lookup[mfr_name_key]
                identified_m_name_from_fallback = mfr_name_key
                string_after_mfr_removal_fb = re.sub(r'\b' + re.escape(mfr_name_key) + r'\b', '', working_str_lower, count=1).strip()
                string_after_mfr_removal_fb = " ".join(string_after_mfr_removal_fb.split())
                break
        
        if identified_m_name_from_fallback and identified_m_name_from_fallback in data_preparation.brand_lookup: # Mfr is also a brand
            # Variant was already searched on working_str_lower (string_for_variant_search if no brand found).
            # If string_after_mfr_removal_fb is empty or was exactly the variant name.
            if not string_after_mfr_removal_fb or \
               (identified_v_name_str and string_after_mfr_removal_fb == identified_v_name_str):
                identified_brand_id = data_preparation.brand_lookup[identified_m_name_from_fallback]['id']
                # Theme search for mfr-as-brand
                theme_search_str_fb = working_str_lower
                theme_search_str_fb = re.sub(r'\b' + re.escape(identified_m_name_from_fallback) + r'\b', '', theme_search_str_fb, count=1).strip()
                if identified_v_name_str:
                    theme_search_str_fb = re.sub(r'\b' + re.escape(identified_v_name_str) + r'\b', '', theme_search_str_fb, count=1).strip()
                theme_search_str_fb = " ".join(theme_search_str_fb.split())
                if theme_search_str_fb and identified_brand_id != UNKNOWN_BRAND_ID:
                    sorted_themes_fb = sorted([t_name for t_name, t_info in data_preparation.theme_lookup.items() if t_info['brand_id'] == identified_brand_id], key=len, reverse=True)
                    for theme_key_fb in sorted_themes_fb:
                        if re.search(r'\b' + re.escape(theme_key_fb) + r'\b', theme_search_str_fb):
                            identified_theme_id = data_preparation.theme_lookup[theme_key_fb]['id']; break
            elif string_after_mfr_removal_fb: # Mfr is brand, but remainder is not empty/variant -> unlisted brand part
                log_issue(f"Warning: Brand candidate '{string_after_mfr_removal_fb}' (after Mfr '{identified_m_name_from_fallback}' removed) not in lookup. Raw: '{raw_brand_str}'")
        elif string_after_mfr_removal_fb: # Mfr not a brand (or no mfr found), remainder is unlisted brand
            log_issue(f"Warning: Brand candidate '{string_after_mfr_removal_fb}' (after Mfr if any) not in lookup. Raw: '{raw_brand_str}'")
        elif not string_after_mfr_removal_fb and identified_m_name_from_fallback: # Only Mfr, not a brand, nothing left.
             log_issue(f"Info: Raw string seems to be only Manufacturer ('{identified_m_name_from_fallback}') not listed as brand. Raw: '{raw_brand_str}'")

    attributes.update({
        'manufacturer_id': identified_manufacturer_id, 'brand_id': identified_brand_id,
        'variant_id': identified_variant_id, 
        'theme_id': identified_theme_id if identified_theme_id != UNKNOWN_THEME_ID else None
    })

    if attributes['manufacturer_id'] == UNKNOWN_MANUFACTURER_ID and attributes['brand_id'] != UNKNOWN_BRAND_ID:
        brand_name_for_mfr_fc = [bname for bname, binfo in data_preparation.brand_lookup.items() if binfo['id'] == attributes['brand_id']]
        if brand_name_for_mfr_fc and data_preparation.brand_lookup[brand_name_for_mfr_fc[0]].get('manufacturer_id'):
            attributes['manufacturer_id'] = data_preparation.brand_lookup[brand_name_for_mfr_fc[0]]['manufacturer_id']

    if attributes['brand_id'] == UNKNOWN_BRAND_ID and raw_brand_str.strip():
        log_issue(f"FinalReport: Brand UNRESOLVED for raw_brand: '{raw_brand_str}'")
    if attributes['manufacturer_id'] == UNKNOWN_MANUFACTURER_ID and attributes['brand_id'] == UNKNOWN_BRAND_ID and raw_brand_str.strip():
        log_issue(f"FinalReport: Manufacturer UNRESOLVED for raw_brand: '{raw_brand_str}' (Brand also unknown).")
    return attributes

def process_raw_card_data(raw_cards_list: list[dict]) -> list[dict]:
    structured_cards = []
    if not data_preparation.manufacturer_lookup: log_issue("Critical: Lookups not populated in process_raw_card_data."); return []
    for raw_card in raw_cards_list:
        print_run, serial_number = parse_limited_string(raw_card['limited_string'])
        brand_parse_results = parse_brand_string(raw_card['raw_brand'], raw_card['season'])
        structured_cards.append({
            'player_id': PLAYER_ID_JUWAN_HOWARD, 'season': raw_card['season'], 
            'card_number': raw_card['card_number'], 'print_run': print_run, 'serial_number': serial_number,
            **brand_parse_results, # Includes mfr, brand, variant, theme, and attributes
            'raw_brand_for_debug': raw_card['raw_brand']
        })
    return structured_cards

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

    return structured_card_list, current_run_logs

# This exposed function will be called by sql_generator
def get_structured_card_data():
    """Public function to get structured card data. Ensures SQL content is loaded if run as part of a sequence."""
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
        
    processed_data, logs = main_processing_logic()
    # For external callers, we might not want to print all these logs directly here.
    # The caller can decide what to do with the logs.
    # For now, let sql_generator print its own summary if needed.
    return processed_data 


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
