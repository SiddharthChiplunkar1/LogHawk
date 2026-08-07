package com.loghawk.search;

import com.loghawk.index.InvertedIndex;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AutocompleteService {

    private final InvertedIndex invertedIndex;

    public AutocompleteService(InvertedIndex invertedIndex) {
        this.invertedIndex = invertedIndex;
    }

    public List<String> suggest(String prefix) {
        if (prefix == null || prefix.trim().isEmpty() || prefix.length() < 2) {
            return new ArrayList<>();
        }

        String lowerPrefix = prefix.toLowerCase();
        Map<String, Set<Long>> rawIndex = invertedIndex.getRawIndex();
        
        return rawIndex.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(lowerPrefix))
                .sorted((e1, e2) -> Integer.compare(e2.getValue().size(), e1.getValue().size())) // Sort by frequency descending
                .limit(10)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}
