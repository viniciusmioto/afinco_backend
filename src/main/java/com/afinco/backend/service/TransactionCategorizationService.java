package com.afinco.backend.service;

import com.afinco.backend.domain.ExpenseType;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Rule-based engine that assigns an {@link ExpenseType} and category name to a
 * transaction based on its description.  Rules use case-insensitive substring
 * matching and are evaluated in priority order: Payment → Fixed → Variable →
 * Occasional (fallback).
 *
 * <p>The keyword tables are derived from analysis of 7 TD credit-card statements
 * (~250 transactions, Feb–Aug 2026) cross-referenced with manually labeled data.
 * See {@code context/categorization.md} for the full model documentation.
 */
@Service
public class TransactionCategorizationService {

    /**
     * Result of auto-categorization: an expense type and a category name that
     * maps to a row in the {@code categories} table.
     */
    public record Categorization(ExpenseType expenseType, String categoryName) {
    }

    // ── Payment keywords ────────────────────────────────────────────────────

    private static final List<String> PAYMENT_KEYWORDS = List.of(
            "PAYMENT - THANK YOU",
            "PREAUTHORIZED PAYMENT",
            "REWARDS REDEMPTION"
    );

    // ── Fixed / Subscriptions ───────────────────────────────────────────────

    private static final List<String> SUBSCRIPTION_KEYWORDS = List.of(
            "NETFLIX",
            "BELL MEDIA",
            "OVERLEAF",
            "DOORDASHDASHPASS",
            "APPLE.COM/BILL",
            "WALMART DELIVERY PASS",
            "UBER HOLDINGS CANADA"
    );

    // ── Fixed / Phone & Internet ────────────────────────────────────────────

    private static final List<String> PHONE_INTERNET_KEYWORDS = List.of(
            "CHATR",
            "VESTA",
            "FIZZ"
    );

    // ── Fixed / Transport ───────────────────────────────────────────────────

    private static final List<String> TRANSPORT_KEYWORDS = List.of(
            "OPUS",
            "CHRONO-RECHARGE",
            "STM CARTIER",
            "BIXI",
            "LYFT"
    );

    // ── Variable / Groceries ────────────────────────────────────────────────
    //    Evaluated BEFORE generic Uber/DoorDash rules because compound
    //    descriptions like DOORDASHMAXI or UBERCOSTCO are grocery deliveries.

    private static final List<String> GROCERY_KEYWORDS = List.of(
            "WALMART",
            "WAL-MART",
            "NF VILLE ST-LAURENT",
            "PROVIGO",
            "MAXI",
            "SUPER C",
            "METRO ETS",
            "ADONIS",
            "DOORDASHMAXI",
            "DD/DOORDASHMAXI",
            "DD/DOORDASHMETRO",
            "UBERCOSTCO",
            "ALIMENTATION KHALID",
            "MARCHE KOREA",
            "TROTTIER FRERES",
            "AL-TAIB BOULANGERIE"
    );

    // ── Variable / Food & Leisure ───────────────────────────────────────────

    private static final List<String> FOOD_LEISURE_KEYWORDS = List.of(
            "TIM HORTONS",
            "MCDONALD",
            "PIZZA SOLEIL",
            "PIZZA PIZZA",
            "POUTINEVILLE",
            "BOUSTAN",
            "POK POK",
            "KUNG PAO WOK",
            "ASHTON",
            "WOK CAFE",
            "KOREAN FOOD",
            "ONIGIRI SHOP",
            "DAWA CHICKEN",
            "LA TOXICA",
            "AMARA KING",
            "MEET FRESH",
            "PATISSERIE COCOBUN",
            "COCOBUN",
            "CHARTWELLS",
            "ALPHABET CAFE",
            "LS ALPHABET",
            "CAFE OLIMPICO",
            "MCKIBBIN",
            "BOTECO",
            "BRASS DOOR",
            "BEN & JERRY",
            "LA DIPERIE",
            "NOTRE BOEUF DE GRAC",
            "UBEREATS",
            "COUCHE-TARD",
            "TABAGIE",
            "MLLE CATHERINE"
    );

    // ── Variable / Pharmacy & Health ────────────────────────────────────────

    private static final List<String> PHARMACY_KEYWORDS = List.of(
            "PHARMAPRIX",
            "JEAN COUTU",
            "PHARMACIE"
    );

    // ── Occasional (explicit keywords — anything unmatched also falls here) ─

    private static final List<String> OCCASIONAL_KEYWORDS = List.of(
            "WINNERS",
            "WINNERSHOMESENSE",
            "SIMONS",
            "LAMAISONSIMONS",
            "TOMMY HILFIGER",
            "LEVIS",
            "LEVI'S",
            "RWCO",
            "RW&CO",
            "BOUTIQUE JAGGERS",
            "IKEA",
            "DOLLARAMA",
            "LE MEME PRIX PLUS",
            "FAMOUS PLAYER",
            "CINEPLEX",
            "ARCADE MTL",
            "SALLE DE QUILLES",
            "LES 3 FILLES SPA",
            "CONCORDIA UNIVERSITY",
            "PAYPATH SERV FEE",
            "IMMIGRATION QUEBEC",
            "AMZN",
            "AMAZON",
            "SHAUNS AUTO SERVICE",
            "STM STUDIO PHOTO",
            "BIBLIOTHEQUE"
    );

    /**
     * Categorize a transaction by its description.
     *
     * @param description the raw merchant description from the statement
     * @return the suggested {@link Categorization}; never null
     */
    public Categorization categorize(String description) {
        if (description == null || description.isBlank()) {
            return new Categorization(ExpenseType.OCCASIONAL, "Occasional");
        }

        String upper = description.toUpperCase(Locale.ROOT);

        // ── 1. Payment ──────────────────────────────────────────────────
        if (containsAny(upper, PAYMENT_KEYWORDS)) {
            return new Categorization(ExpenseType.PAYMENT, "Payment");
        }

        // ── 2. Fixed ────────────────────────────────────────────────────

        // Uber sub-routing must happen before generic transport/subscription checks.
        // Match "UBER" only at a word boundary to avoid false positives (e.g. HUBERT).
        if (startsWithWord(upper, "UBER")) {
            return categorizeUber(upper);
        }

        // DoorDash sub-routing must happen before generic grocery checks
        if (upper.contains("DOORDASH")) {
            return categorizeDoorDash(upper);
        }

        if (containsAny(upper, SUBSCRIPTION_KEYWORDS)) {
            return new Categorization(ExpenseType.FIXED, "Subscriptions");
        }

        if (containsAny(upper, PHONE_INTERNET_KEYWORDS)) {
            return new Categorization(ExpenseType.FIXED, "Phone / Internet");
        }

        if (containsAny(upper, TRANSPORT_KEYWORDS)) {
            return new Categorization(ExpenseType.FIXED, "Transport");
        }

        // ── 3. Variable ─────────────────────────────────────────────────

        if (containsAny(upper, GROCERY_KEYWORDS)) {
            return new Categorization(ExpenseType.VARIABLE, "Groceries");
        }

        if (containsAny(upper, FOOD_LEISURE_KEYWORDS)) {
            return new Categorization(ExpenseType.VARIABLE, "Food & Leisure");
        }

        if (containsAny(upper, PHARMACY_KEYWORDS)) {
            return new Categorization(ExpenseType.VARIABLE, "Pharmacy & Health");
        }

        // ── 4. Occasional (explicit match) ──────────────────────────────

        if (containsAny(upper, OCCASIONAL_KEYWORDS)) {
            return new Categorization(ExpenseType.OCCASIONAL, "Occasional");
        }

        // ── 5. Fallback ─────────────────────────────────────────────────
        return new Categorization(ExpenseType.OCCASIONAL, "Occasional");
    }

    // ── Uber sub-routing ────────────────────────────────────────────────────

    private Categorization categorizeUber(String upper) {
        if (upper.contains("UBEREATS")) {
            return new Categorization(ExpenseType.VARIABLE, "Food & Leisure");
        }
        if (upper.contains("UBERCOSTCO")) {
            return new Categorization(ExpenseType.VARIABLE, "Groceries");
        }
        if (upper.contains("TRIP")) {
            return new Categorization(ExpenseType.FIXED, "Transport");
        }
        if (upper.contains("UBER HOLDINGS CANADA")) {
            return new Categorization(ExpenseType.FIXED, "Subscriptions");
        }
        // Unknown Uber variant → occasional
        return new Categorization(ExpenseType.OCCASIONAL, "Occasional");
    }

    // ── DoorDash sub-routing ────────────────────────────────────────────────

    private Categorization categorizeDoorDash(String upper) {
        if (upper.contains("DASHPASS")) {
            return new Categorization(ExpenseType.FIXED, "Subscriptions");
        }
        if (upper.contains("MAXI") || upper.contains("METRO")) {
            return new Categorization(ExpenseType.VARIABLE, "Groceries");
        }
        // Generic DoorDash order → food delivery
        return new Categorization(ExpenseType.VARIABLE, "Food & Leisure");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} when the uppercased text contains the given word at a
     * position where it is preceded by the start of the string, a space, or a
     * slash — preventing false positives like "HUBERT" matching "UBER".
     */
    private static boolean startsWithWord(String text, String word) {
        int idx = 0;
        while ((idx = text.indexOf(word, idx)) != -1) {
            if (idx == 0 || text.charAt(idx - 1) == ' ' || text.charAt(idx - 1) == '/') {
                return true;
            }
            idx += word.length();
        }
        return false;
    }

    private static boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
