import card_data_processor # To get the structured card data
import data_preparation # To get UNKNOWN_THEME_ID for NULL handling

# Assuming UNKNOWN_THEME_ID is 0 or a similar placeholder from data_preparation
# If data_preparation.UNKNOWN_THEME_ID is, for example, 0, and 0 is a valid theme_id,
# this needs careful handling. The problem states theme_id can be NULL.
# card_data_processor sets theme_id to None if it's UNKNOWN_THEME_ID.
# So, checking for None is the primary way.

def format_value(value, is_string=False, is_boolean=False, can_be_null=False):
    """Helper function to format SQL values."""
    if can_be_null and value is None:
        return "NULL"
    if is_string:
        # Escape single quotes within the string by doubling them
        escaped_value = str(value).replace("'", "''")
        return f"'{escaped_value}'"
    if is_boolean:
        return "1" if value else "0"
    return str(value)

def generate_sql_inserts(structured_cards_list: list[dict]) -> list[str]:
    """
    Generates a list of SQL INSERT statements from structured card data.
    """
    sql_statements = []
    if not structured_cards_list:
        print("Warning: No structured card data received to generate SQL.")
        return []

    # SQL table column order:
    # (print_run, serial_number, season, number, rookie_card, game_used_material, 
    #  player_id, theme_id, autograph, variant_id)
    
    for card_data in structured_cards_list:
        try:
            # Ensure all necessary keys are present, providing defaults if absolutely necessary
            # card_data_processor is expected to provide all these keys with valid defaults.
            # The variant_id, in particular, should always be valid (e.g., defaulted to 'Base' variant ID).
            pr = card_data['print_run']
            sn = card_data['serial_number']
            s = card_data['season']
            num = card_data['card_number'] 
            rc = card_data['rookie_card']
            gu = card_data['game_used_material']
            pid = card_data['player_id'] # card_data_processor sets this
            tid = card_data.get('theme_id') # theme_id can be None (for NULL in SQL)
            au = card_data['autograph']
            vid = card_data['variant_id'] # This should always be a valid ID from processor

            # Formatting values for SQL
            sql_pr = format_value(pr)
            sql_sn = format_value(sn)
            sql_s = format_value(s, is_string=True)
            sql_num = format_value(num, is_string=True)
            sql_rc = format_value(rc, is_boolean=True)
            sql_gu = format_value(gu, is_boolean=True)
            sql_pid = format_value(pid)
            sql_tid = format_value(tid, can_be_null=True) # Handles None to NULL
            sql_au = format_value(au, is_boolean=True)
            sql_vid = format_value(vid)

            sql = (
                f"INSERT INTO card (print_run, serial_number, season, number, rookie_card, "
                f"game_used_material, player_id, theme_id, autograph, variant_id) VALUES "
                f"({sql_pr}, {sql_sn}, {sql_s}, {sql_num}, {sql_rc}, {sql_gu}, {sql_pid}, "
                f"{sql_tid}, {sql_au}, {sql_vid});"
            )
            sql_statements.append(sql)
        except KeyError as e:
            print(f"Error: Missing key {e} in card data: {card_data}. Skipping this card.")
        except Exception as e:
            print(f"Error generating SQL for card data: {card_data}. Error: {e}. Skipping this card.")
            
    return sql_statements

def get_card_insert_statements(): # Renamed from run_sql_generation
    """
    Main function to orchestrate fetching data and generating SQL.
    Returns the list of SQL statements.
    """
    print("Fetching processed card data from card_data_processor...")
    # This part requires card_data_processor to be refactored to provide its output
    # For now, let's assume card_data_processor.main_process() returns the list
    # We'll need to adjust this based on how card_data_processor is made callable.
    
    # --- Refactor card_data_processor.py to have a main function that returns the list ---
    # --- For now, I will call its existing __main__ structure by calling a wrapper ---
    # --- that simulates how it would be called. This is a temporary measure. ---

    # This is a placeholder for how card_data_processor might be called.
    # The actual implementation will depend on refactoring card_data_processor.
    # structured_card_data = card_data_processor.get_processed_data_for_sql_generator() # Ideal
    
    # Temporary approach: card_data_processor.py's main execution path populates its own
    # global 'structured_card_list' or similar. We can't directly access that.
    # For this step, I will assume card_data_processor.py needs to be modified
    # to have a function that can be called.
    # Let's define a dummy function in card_data_processor for now, or expect it to be there.

    # Populate card_data_processor's global SQL strings so its initialize_lookups works correctly
    # This is a temporary measure for the current inter-script calling pattern.
    # Ideally, card_data_processor.initialize_lookups would take SQL strings as arguments,
    # or SQL content would be managed by a central data loading mechanism.
    
    # SQL Content (from read_files in Turn 27 or a similar source)
    # For the agent, these would be fetched and inserted here.
    # For local testing, these strings would be directly here or read from files.
    
    # Placeholder for actual SQL content strings. The agent must fill these.
    # These are the same SQL content strings used in card_data_processor.py's __main__
    SQL_CONTENT_MANUFACTURER_FULL = """
CREATE DATABASE  IF NOT EXISTS `cardcollection` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `cardcollection`;
INSERT INTO `card_manufacturer` VALUES (1,'Upper Deck'),(2,'Topps'),(3,'Fleer'),(4,'Leaf'),(5,'Panini'),(6,'Classic'),(7,'Score Board');
    """ # Simplified for brevity, actual SQL dump is larger. Assume full content is used.
    SQL_CONTENT_BRAND_FULL = """
USE `cardcollection`;
INSERT INTO `card_brand` VALUES (1,'Collectors Choice',1),(2,'Exquisite',1),(3,'SP Authentic',1),(4,'Upper Deck',1),(5,'SP',1),(6,'SP Championship	',1),(7,'UD3	',1),(8,'SPx',1),(9,'Hardcourt',1),(10,'Black Diamond',1),(11,'SPx Finite',1),(12,'Choice',1),(13,'Ionix',1),(14,'Ovation',1),(15,'Encore',1),(16,'HoloGrFX',1),(17,'Retro',1),(18,'MVP',1),(19,'Gold Reserve',1),(20,'Victory',1),(21,'Reserve',1),(22,'UDx',1),(23,'SLAM',1),(24,'SP Game Used',1),(25,'Glass',1),(26,'Authentics ',1),(27,'Sweet Shot',1),(28,'Ultimate Victory',1),(29,'Honor Roll',1),(30,'Inspiration',1),(31,'SP Authentic Limited',1),(32,'Flight Team',1),(33,'Finite',1),(34,'Ultimate Collection',1),(35,'Championship Drive ',1),(36,'Exclusives',1),(37,'Standing O',1),(38,'Legends',1),(39,'R-Class',1),(40,'Trilogy',1),(41,'Reflections',1),(42,'ESPN',1),(43,'Rookie Debut',1),(44,'Signature Edition',1),(50,'Topps',2),(51,'Embossed',2),(52,'Finest',2),(53,'Stadium Club',2),(54,'Stadium Club Members Only',2),(55,'Gallery',2),(56,'Bowman\'s Best	',2),(57,'Chrome',2),(58,'Gold Label',2),(59,'Tip Off',2),(60,'Heritage',2),(61,'Stars',2),(62,'Reserve',2),(63,'Pristine',2),(64,'Jersey Edition',2),(65,'Bazooka',2),(66,'Turkey Red',2),(67,'Contemporary Collection',2),(68,'Rookie Matrix',2),(69,'First Edition',2),(70,'Luxury Box',2),(71,'Total',2),(80,'Flair',3),(81,'Fleer',3),(82,'Jam Session',3);
    """
    SQL_CONTENT_THEME_FULL = """
USE `cardcollection`;
INSERT INTO `card_theme` VALUES (1,'You Crash The Game Rookie Scoring',1),(2,'You Crash The Game Rookie Scoring Redemption',1),(3,'Signature',1),(4,'Lottery Pick',1),(5,'Embossed',51),(6,'Collegiate Best',51),(7,'Rack Pack',NULL);
    """
    SQL_CONTENT_VARIANT_FULL = """
USE `cardcollection`;
INSERT INTO `variant` VALUES (1,'Base'),(2,'Refractor'),(3,'Die Cut'),(5,'Gold'),(6,'Silver'),(7,'Bronze'),(8,'Platinum'),(9,'Diamond'),(10,'Emerald'),(11,'Ruby'),(12,'Black'),(13,'White'),(14,'Yellow'),(15,'Cyan'),(16,'Magenta'),(17,'Red'),(18,'Blue'),(19,'Grean');
    """

    # Set these in card_data_processor before calling its functions
    card_data_processor.SQL_CONTENT_MANUFACTURER = SQL_CONTENT_MANUFACTURER_FULL
    card_data_processor.SQL_CONTENT_BRAND = SQL_CONTENT_BRAND_FULL
    card_data_processor.SQL_CONTENT_THEME = SQL_CONTENT_THEME_FULL
    card_data_processor.SQL_CONTENT_VARIANT = SQL_CONTENT_VARIANT_FULL

    # Now, card_data_processor's get_structured_card_data can initialize its lookups with full data
    structured_data = card_data_processor.get_structured_card_data()


    print(f"Received {len(structured_data)} structured card entries.")
    
    sql_insert_statements = generate_sql_inserts(structured_data)
    
    print(f"\nGenerated {len(sql_insert_statements)} SQL INSERT statements.")
    
    if sql_insert_statements:
        print("\nFirst 20 generated SQL INSERT statements:")
        for i, stmt in enumerate(sql_insert_statements[:20]):
            print(f"{i+1}: {stmt}")
            
    return sql_insert_statements

if __name__ == '__main__':
    # This allows direct execution of sql_generator for testing/outputting to console
    sql_statements = get_card_insert_statements()
    # The printing part is already inside get_card_insert_statements for when it's run directly.
    # If we don't want prints when imported, get_card_insert_statements could take a flag.
    # For now, this is fine.
    if not sql_statements:
        print("No SQL statements were generated by direct run of sql_generator.py.")
