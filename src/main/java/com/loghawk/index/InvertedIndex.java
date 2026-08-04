package com.loghawk.index;

import com.loghawk.model.LogEntry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

public class InvertedIndex {
    private final ConcurrentHashMap<String, ConcurrentSkipListSet<Long>> index;
    private final Set<String> stopWords;

    public InvertedIndex() {
        this.index = new ConcurrentHashMap<>();
        this.stopWords = new HashSet<>(Arrays.asList(
                "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
                "of", "with", "by", "is", "are", "was", "were", "be", "been", "being",
                "have", "has", "had", "do", "does", "did", "will", "would", "could",
                "should", "may", "might", "shall", "can", "this", "that", "it", "its"
        ));
    }

    public void addEntry(LogEntry logEntry){
        String[] tokens = tokenize(logEntry.getMessage());

        for(String token : tokens){
            String lowerToken = token.toLowerCase();
            if(!stopWords.contains(lowerToken) && lowerToken.length() > 1){
                index.computeIfAbsent(lowerToken,
                        k -> new ConcurrentSkipListSet<>())
                        .add(logEntry.getId());
            }
        }
    }

    public Set<Long> searchByKeyword(String keyword){
        ConcurrentSkipListSet<Long> results = index.get(keyword.toLowerCase());
        return results != null ? new HashSet<>(results) : Collections.emptySet();
    }

    public Set<Long> searchByKeywords(List<String> keywords){
        if(keywords.isEmpty())
            return Collections.emptySet();

        Set<Long> results = null;
        for(String keyword : keywords){
            Set<Long> matches = searchByKeyword(keyword);
            if(results == null){
                results = new HashSet<>(matches);
            } else {
                results.retainAll(matches);
            }

            if(results.isEmpty())
                break;
        }

        return results != null ? results : Collections.emptySet();
    }

    private String[] tokenize(String text) {
        return text.toLowerCase()
                .replaceAll("[^a-zA-Z0-9\\s]", " ")
                .split("\\s+");
    }

    public int size(){
        return index.size();
    }

    public int getTotalTerms(){
        return index.size();
    }
}