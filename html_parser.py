import re
from bs4 import BeautifulSoup
from data_preparation import strip_initial_garbage # Assuming data_preparation.py is in the same directory or accessible via PYTHONPATH

def parse_seasons_from_html(html_filepath: str):
    """
    Parses an HTML file to find seasons and the number of cards (table rows) for each season.

    Args:
        html_filepath: Path to the HTML file.
    """
    try:
        with open(html_filepath, 'rb') as f:
            raw_content = f.read()
    except FileNotFoundError:
        print(f"Error: File not found at {html_filepath}")
        return
    except Exception as e:
        print(f"Error reading file {html_filepath}: {e}")
        return

    cleaned_html_str = strip_initial_garbage(raw_content)
    soup = BeautifulSoup(cleaned_html_str, 'lxml')

    season_headers = soup.find_all('h2')
    print(f"Found {len(season_headers)} h2 tags in total.")
    all_cards_data = []

    for header in season_headers:
        header_text = header.get_text(strip=True)
        header_id = header.get('id')

        # Attempt to extract season year from header text before '['
        match = re.match(r"(\d{4}-\d{2,4})", header_text) # Matches "YYYY-YY" or "YYYY-YYYY"
        if match:
            season_year_from_text = match.group(1)
        else:
            # Fallback or skip if pattern not found in text
            # We can also primarily rely on 'id' if it's consistent
            season_year_from_text = None

        # Prioritize 'id' attribute for season year if it matches the expected pattern
        if header_id and re.fullmatch(r"\d{4}-\d{2,4}", header_id):
            season_year = header_id
            # print(f"Processing season based on ID: {season_year}")
        elif season_year_from_text:
            season_year = season_year_from_text
            # print(f"Processing season based on text: {season_year}")
        else:
            # print(f"Skipping h2 (text: '{header_text}', id: '{header_id}') as it doesn't seem to be a season header.")
            continue

        # Find the next sibling table
        current_element = header.next_sibling
        table = None
        while current_element:
            if current_element.name == 'table':
                table = current_element
                break
            current_element = current_element.next_sibling

        if table:
            # num_rows = len(table.find_all('tr')) # Count all 'tr' descendants
            # print(f"Season: {season_year}, Number of card rows initially: {num_rows}")

            table_rows = table.find_all('tr')
            if not table_rows:
                print(f"Season: {season_year}, No rows (tr) found in the table.")
                continue

            # Skip header row (first tr)
            # Some tables might be empty or only have a header, so check length
            if len(table_rows) <= 1 and season_year not in ["1994-95", "1995-96", "1996-97", "1997-98", "1998-99", "1999-00", "2000-01", "2001-02", "2002-03", "2003-04", "2004-05", "2005-06", "2006-07", "2007-08", "2009-10", "2010-11", "2011-12", "2012-13", "2013-14", "2016-17", "2017-18", "2018-19", "2019-20", "2020-21", "2021-22", "2022-23", "2023-24"]:
                 # The problem description's sample output shows 0 for these seasons, implying they might be empty or only header
                 # For other seasons, if only 1 row, it's likely just a header.
                print(f"Season: {season_year}, Table has only a header row or is empty. Skipping card processing for this season.")
                # Continue to ensure an empty list is returned for seasons with no data cards.
                # This matches the previous output where these seasons had 0 card rows.

            processed_card_count_for_season = 0
            for i, row in enumerate(table_rows):
                if i == 0: # Skip header row
                    continue

                cells = row.find_all('td')
                if len(cells) == 4:
                    td_year = cells[0].get_text(strip=True)
                    td_raw_brand = cells[1].get_text(strip=True)
                    td_card_number = cells[2].get_text(strip=True)
                    td_limited = cells[3].get_text(strip=True)

                    if td_year != season_year:
                        print(f"Warning: Mismatch in season year for row. Header: {season_year}, Cell: {td_year}. Using header season: {season_year}")
                    
                    card_data = {
                        'season': season_year, # Use authoritative season_year from H2
                        'raw_brand': td_raw_brand,
                        'card_number': td_card_number,
                        'limited_string': td_limited
                    }
                    all_cards_data.append(card_data)
                    processed_card_count_for_season += 1
                else:
                    print(f"Season: {season_year}, Row {i+1}: Found {len(cells)} cells, expected 4. Skipping row: {row.get_text(strip=True)}")
            
            print(f"Season: {season_year}, Processed {processed_card_count_for_season} card data rows.")

        else:
            print(f"Season: {season_year}, No table found immediately following this header.")
    
    return all_cards_data

if __name__ == '__main__':
    html_file = 'output/index.html'
    print(f"Starting HTML parsing for {html_file}...")
    collected_cards = parse_seasons_from_html(html_file)
    
    if collected_cards:
        print(f"\nSuccessfully extracted data for {len(collected_cards)} cards in total.")
        print("First 10 cards extracted:")
        for i, card in enumerate(collected_cards[:10]):
            print(f"{i+1}: {card}")
    else:
        print("No card data was extracted.")
        
    print("\nHTML parsing finished.")
