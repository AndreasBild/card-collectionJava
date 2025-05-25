import datetime
import sql_generator # To get the list of INSERT INTO card statements

def assemble_final_sql_script():
    """
    Assembles the complete SQL import script.
    """
    current_date = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    
    sql_output_parts = []

    # 1. Initialization
    sql_output_parts.append("USE cardcollection;\n")
    sql_output_parts.append("-- SQL Import script for Juwan Howard trading card collection")
    sql_output_parts.append(f"-- Generated on {current_date}\n")
    sql_output_parts.append("-- This script assumes that card_manufacturer, card_brand, card_theme,")
    sql_output_parts.append("-- and variant tables are already populated correctly.\n")

    # 2. Fetch INSERT INTO card statements
    print("Fetching INSERT INTO card statements from sql_generator...")
    
    # This requires sql_generator.py to have a function that returns the SQL list.
    # Let's assume sql_generator.get_card_insert_statements() will be that function.
    try:
        # This function will need to be created in sql_generator.py
        card_insert_statements = sql_generator.get_card_insert_statements() 
    except AttributeError:
        print("Error: `get_card_insert_statements` function not found in `sql_generator.py`.")
        print("Please refactor `sql_generator.py` to make its output accessible.")
        card_insert_statements = [] # Fallback
    except Exception as e:
        print(f"An error occurred while trying to get data from sql_generator: {e}")
        card_insert_statements = []


    if not card_insert_statements:
        print("Warning: No card INSERT statements received from sql_generator.")
    else:
        print(f"Received {len(card_insert_statements)} card INSERT statements.")
    
    # 3. Add Card INSERT Statements
    if card_insert_statements:
        sql_output_parts.append("\n-- Inserting card data --")
        for stmt in card_insert_statements:
            sql_output_parts.append(stmt)
    
    # 4. Finalization (optional footer comments)
    sql_output_parts.append("\n-- End of Juwan Howard trading card collection import script --")

    final_sql_script_content = "\n".join(sql_output_parts)
    
    # 5. Output to file
    output_filename = "juwan_howard_collection_import.sql"
    try:
        with open(output_filename, "w") as f:
            f.write(final_sql_script_content)
        print(f"\nSuccessfully generated SQL import script: {output_filename}")
    except IOError as e:
        print(f"Error writing SQL script to file {output_filename}: {e}")
        
    return output_filename, final_sql_script_content


if __name__ == '__main__':
    assemble_final_sql_script()
