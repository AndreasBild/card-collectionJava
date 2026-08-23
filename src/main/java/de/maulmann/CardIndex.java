package de.maulmann;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * High-performance O(1) in-memory attribute index for related card lookups,
 * brand/company collections, and navigation indexing.
 */
public class CardIndex {

    private final Map<String, List<CardData>> brandMap = new HashMap<>();
    private final Map<String, List<CardData>> companyMap = new HashMap<>();
    private final Map<String, List<CardData>> seasonMap = new HashMap<>();
    private final Map<String, List<CardData>> playerMap = new HashMap<>();

    public CardIndex(List<CardData> allCards) {
        if (allCards == null) return;
        for (CardData c : allCards) {
            String brand = c.get("Brand");
            if (CardData.isValid(brand)) {
                brandMap.computeIfAbsent(brand.toLowerCase(), k -> new ArrayList<>()).add(c);
            }
            String company = c.get("Company");
            if (CardData.isValid(company)) {
                companyMap.computeIfAbsent(company.toLowerCase(), k -> new ArrayList<>()).add(c);
            }
            String season = c.get("Season");
            if (CardData.isValid(season)) {
                seasonMap.computeIfAbsent(season.toLowerCase(), k -> new ArrayList<>()).add(c);
            }
            String player = c.get("Player");
            if (CardData.isValid(player)) {
                playerMap.computeIfAbsent(player.toLowerCase(), k -> new ArrayList<>()).add(c);
            }
        }
    }

    public List<CardData> getByBrand(String brand) {
        if (brand == null) return Collections.emptyList();
        return brandMap.getOrDefault(brand.toLowerCase(), Collections.emptyList());
    }

    public List<CardData> getByCompany(String company) {
        if (company == null) return Collections.emptyList();
        return companyMap.getOrDefault(company.toLowerCase(), Collections.emptyList());
    }

    public List<CardData> getCandidatesForRelated(CardData target) {
        if (target == null) return Collections.emptyList();
        Set<CardData> candidates = new LinkedHashSet<>();
        String player = target.get("Player");
        if (CardData.isValid(player)) candidates.addAll(playerMap.getOrDefault(player.toLowerCase(), Collections.emptyList()));
        String season = target.get("Season");
        if (CardData.isValid(season)) candidates.addAll(seasonMap.getOrDefault(season.toLowerCase(), Collections.emptyList()));
        String brand = target.get("Brand");
        if (CardData.isValid(brand)) candidates.addAll(brandMap.getOrDefault(brand.toLowerCase(), Collections.emptyList()));
        return new ArrayList<>(candidates);
    }
}
