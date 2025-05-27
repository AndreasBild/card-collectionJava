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

def generate_sql_updates(high_confidence_cards_list: list[dict]) -> list[str]:
    """
    Generates a list of SQL UPDATE statements from high-confidence card data.
    """
    sql_statements = []
    if not high_confidence_cards_list:
        print("Warning: No high-confidence card data received to generate SQL updates.")
        return []

    for card_data in high_confidence_cards_list:
        try:
            # Required keys for the UPDATE statement
            player_id = card_data['player_id']
            season = card_data['season']
            card_number = card_data['card_number']
            theme_id = card_data.get('theme_id') # Can be None
            variant_id = card_data['variant_id'] # Should always be present

            # Formatting values for SQL
            sql_theme_id = format_value(theme_id, can_be_null=True)
            sql_variant_id = format_value(variant_id) # Assumed to be a number
            
            # WHERE clause values
            where_season = format_value(season, is_string=True)
            where_card_number = format_value(card_number, is_string=True)
            where_player_id = format_value(player_id) # Assumed to be a number

            sql = (
                f"UPDATE card SET theme_id = {sql_theme_id}, variant_id = {sql_variant_id} "
                f"WHERE season = {where_season} AND number = {where_card_number} AND player_id = {where_player_id};"
            )
            sql_statements.append(sql)
        except KeyError as e:
            # Updated to reflect keys needed for UPDATE statement
            print(f"Error: Missing key {e} in card data: {card_data} for SQL UPDATE. Skipping this card.")
        except Exception as e:
            print(f"Error generating SQL UPDATE for card data: {card_data}. Error: {e}. Skipping this card.")
            
    return sql_statements

def get_card_update_statements(): 
    """
    Main function to orchestrate fetching data and generating SQL UPDATE statements.
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
    # Returns the list of SQL statements. # This line was part of the duplication.
    # """ # This line was part of the duplication.
    print("Fetching processed card data from card_data_processor...")
    
    # The following SQL content setup is part of a temporary inter-script calling pattern.
    # This will be simplified once card_data_processor.py's main logic is directly callable
    # with parameters or a shared configuration.
    SQL_CONTENT_MANUFACTURER_FULL = """
CREATE DATABASE  IF NOT EXISTS `cardcollection` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `cardcollection`;
INSERT INTO `card_manufacturer` VALUES (1,'Upper Deck'),(2,'Topps'),(3,'Fleer'),(4,'Leaf'),(5,'Panini'),(6,'Classic'),(7,'Score Board');
    """ 
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

    card_data_processor.SQL_CONTENT_MANUFACTURER = SQL_CONTENT_MANUFACTURER_FULL
    card_data_processor.SQL_CONTENT_BRAND = SQL_CONTENT_BRAND_FULL
    card_data_processor.SQL_CONTENT_THEME = SQL_CONTENT_THEME_FULL
    card_data_processor.SQL_CONTENT_VARIANT = SQL_CONTENT_VARIANT_FULL

    # IMPORTANT: card_data_processor.get_structured_card_data() currently returns structured_card_list, logs
    # And structured_card_list contains dicts with more fields than needed for UPDATE.
    # For this subtask, we assume it will be adapted to return what generate_sql_updates expects.
    # The current implementation of get_structured_card_data returns a tuple: (processed_data, logs)
    # where processed_data is the list of high-confidence cards if the previous steps were completed.
    # If card_data_processor.process_raw_card_data was updated to return two lists,
    # then card_data_processor.main_processing_logic and card_data_processor.get_structured_card_data
    # would need to be updated to return the high-confidence list.
    # For now, we proceed assuming structured_data_result is the high_confidence_cards_list.
    
    # card_data_processor.get_structured_card_data() now returns only the high-confidence list.
    high_confidence_cards_list = card_data_processor.get_structured_card_data() 
    
    # If structured_data_result is actually (high_confidence_cards, questionable_cards_log_entries)
    # as per card_data_processor's changes from Step 3, then we should use the first element.
    # This will be refined in Step 6. For now, this line assumes structured_data_result is the list of cards.
    # Let's assume for this step, structured_data_result IS the high_confidence_cards_list.
    # (This implies that get_structured_card_data() was modified to return just that, or we're taking the first part of a tuple)

    # card_data_processor.get_structured_card_data() now returns only the high-confidence list.
    high_confidence_cards_list = card_data_processor.get_structured_card_data() 

    print(f"Received {len(high_confidence_cards_list)} high-confidence card entries for UPDATE.")
    
    sql_update_statements = generate_sql_updates(high_confidence_cards_list)
    
    print(f"\nGenerated {len(sql_update_statements)} SQL UPDATE statements.")
    
    if sql_update_statements:
        print("\nFirst 20 generated SQL UPDATE statements:")
        for i, stmt in enumerate(sql_update_statements[:20]):
            print(f"{i+1}: {stmt}")
            
    return sql_update_statements

if __name__ == '__main__':
    sql_statements = get_card_update_statements()
    if not sql_statements:
        print("No SQL UPDATE statements were generated by direct run of sql_generator.py.")
