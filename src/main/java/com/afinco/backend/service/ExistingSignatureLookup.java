package com.afinco.backend.service;

import com.afinco.backend.repository.TransactionRepository;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Finds which signatures are already stored, querying in chunks below SQLite's bound-parameter limit. */
@Component
public class ExistingSignatureLookup {

    static final int QUERY_BATCH_SIZE = 400;

    private final TransactionRepository transactionRepository;

    public ExistingSignatureLookup(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public Set<String> findExisting(Collection<String> signatures) {
        List<String> distinct = signatures.stream().distinct().toList();
        Set<String> matches = new HashSet<>();
        for (int offset = 0; offset < distinct.size(); offset += QUERY_BATCH_SIZE) {
            int end = Math.min(offset + QUERY_BATCH_SIZE, distinct.size());
            matches.addAll(transactionRepository.findExistingHashSignatures(distinct.subList(offset, end)));
        }
        return matches;
    }
}
