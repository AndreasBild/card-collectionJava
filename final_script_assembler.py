import os
import sql_generator # To get the list of SQL UPDATE statements

def main():
    """
    Main function to orchestrate the card data processing and SQL generation.
    """
    print("Starting card data processing and SQL generation...")

    # 1. Get SQL UPDATE statements from sql_generator
    # This call triggers the entire chain:
    # sql_generator -> card_data_processor -> html_parser
    # card_data_processor will also write the questionable_parses.txt file.
    sql_update_statements = sql_generator.get_card_update_statements()

    # 2. Define output directory and file path for the SQL update script
    output_dir = "output"
    sql_output_filepath = os.path.join(output_dir, "update_script.sql")

    # 3. Ensure the output directory exists
    try:
        os.makedirs(output_dir, exist_ok=True)
    except OSError as e:
        print(f"Error creating directory {output_dir}: {e}")
        # Depending on desired behavior, might exit or try to proceed if dir already exists.
        # exist_ok=True should prevent error if dir exists. This is more for other OSErrors.
        return 

    # 4. Write the SQL UPDATE statements to the file
    try:
        with open(sql_output_filepath, "w", encoding='utf-8') as f:
            if not sql_update_statements:
                f.write("-- No SQL update statements were generated.\n")
                print("No SQL update statements were generated.")
            else:
                for stmt in sql_update_statements:
                    f.write(stmt + "\n")
                print(f"Successfully wrote {len(sql_update_statements)} SQL update statements to {sql_output_filepath}")
    except IOError as e:
        print(f"Error writing SQL update script to file {sql_output_filepath}: {e}")
        return

    # 5. Print final completion message
    print("\nProcess complete.")
    print(f"SQL update script saved to: {sql_output_filepath}")
    print(f"Questionable parses logged to: output/questionable_parses.txt")

if __name__ == "__main__":
    main()
