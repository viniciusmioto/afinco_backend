package com.afinco.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.afinco.backend.domain.ExpenseType;
import com.afinco.backend.service.TransactionCategorizationService.Categorization;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TransactionCategorizationServiceTest {

    private TransactionCategorizationService service;

    @BeforeEach
    void setUp() {
        service = new TransactionCategorizationService();
    }

    // ── Payment ─────────────────────────────────────────────────────────────

    @Nested
    class PaymentTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "PAYMENT - THANK YOU",
                "PREAUTHORIZED PAYMENT",
                "REWARDS REDEMPTION TORONTO ON"
        })
        void paymentKeywordsReturnPaymentType(String description) {
            Categorization result = service.categorize(description);

            assertThat(result.expenseType()).isEqualTo(ExpenseType.PAYMENT);
            assertThat(result.categoryName()).isEqualTo("Payment");
        }
    }

    // ── Fixed / Subscriptions ───────────────────────────────────────────────

    @Nested
    class SubscriptionTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "Netflix.com 866-716-0414",
                "NETFLIX.COM 844-5052993",
                "NETFLIX.COM Vancover",
                "BELL MEDIA TORONTO",
                "OVERLEAF EDITOR OVERLEAF.COM",
                "DOORDASHDASHPASS DOWNTOWN TOR",
                "APPLE.COM/BILL 866-712-7753",
                "Uber Holdings Canada Inc. Toronto"
        })
        void subscriptionKeywordsReturnFixedSubscriptions(String description) {
            Categorization result = service.categorize(description);

            assertThat(result.expenseType()).isEqualTo(ExpenseType.FIXED);
            assertThat(result.categoryName()).isEqualTo("Subscriptions");
        }
    }

    // ── Fixed / Phone & Internet ────────────────────────────────────────────

    @Nested
    class PhoneInternetTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "VESTA *CHATR 800-485-9745",
                "FIZZ (TX. INCL.)/FIZZ MONTREAL",
                "FIZZ (TX. INCL.) MONTREAL",
                "FIZZ (TX. INCL.)/75996 MONTREAL"
        })
        void phoneInternetKeywordsReturnFixedPhoneInternet(String description) {
            Categorization result = service.categorize(description);

            assertThat(result.expenseType()).isEqualTo(ExpenseType.FIXED);
            assertThat(result.categoryName()).isEqualTo("Phone & Internet");
        }
    }

    // ── Fixed / Transport ───────────────────────────────────────────────────

    @Nested
    class TransportTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "CHRONO-RECHARGE OPUS MONTREAL",
                "STM CARTIER DIN101/848 LAVAL",
                "STM CARTIER DIN102/116 LAVAL",
                "BIXI MONTREAL 514-7892494",
                "LYFT *RIDE FRI 7PM VANCOUVER"
        })
        void transportKeywordsReturnFixedTransport(String description) {
            Categorization result = service.categorize(description);

            assertThat(result.expenseType()).isEqualTo(ExpenseType.FIXED);
            assertThat(result.categoryName()).isEqualTo("Transport");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "UBER CANADA/UBERTRIP TORONTO",
                "UBER *TRIP HELP.UBER.COM Toronto"
        })
        void uberTripReturnsFixedTransport(String description) {
            Categorization result = service.categorize(description);

            assertThat(result.expenseType()).isEqualTo(ExpenseType.FIXED);
            assertThat(result.categoryName()).isEqualTo("Transport");
        }
    }

    // ── Variable / Groceries ────────────────────────────────────────────────

    @Nested
    class GroceryTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "WALMART.CA MISSISSAUGA",
                "WAL-MART SUPERCENTER#3180 MONTREAL",
                "NF VILLE ST-LAURENT 52 SAINT-LAUREN",
                "PROVIGO LE MARCHE AVE MONTREAL",
                "PROVIGO ANDREANNE LAUR MONTREAL",
                "MAXI MONTREAL ST-JACQU MONTREAL",
                "MAXI & CIE #8906 SAINT-LAUREN",
                "SUPER C ST LAURENT 259 SAINT LAUREN",
                "METRO ETS 2416 MONTREAL",
                "ADONIS 21037 SAUVE MONTREAL",
                "ALIMENTATION KHALID AZ MONTREAL",
                "MARCHE KOREA Montreal",
                "TROTTIER FRERES MONTREAL",
                "AL-TAIB BOULANGERIE Montreal"
        })
        void groceryKeywordsReturnVariableGroceries(String description) {
            Categorization result = service.categorize(description);

            assertThat(result.expenseType()).isEqualTo(ExpenseType.VARIABLE);
            assertThat(result.categoryName()).isEqualTo("Groceries");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "DOORDASHMAXI DOWNTOWN TOR",
                "DD/DOORDASHMAXI VANCOUVER",
                "DD/DOORDASHMETRO VANCOUVER"
        })
        void groceryDeliveriesReturnVariableGroceries(String description) {
            Categorization result = service.categorize(description);

            assertThat(result.expenseType()).isEqualTo(ExpenseType.VARIABLE);
            assertThat(result.categoryName()).isEqualTo("Groceries");
        }

        @Test
        void uberCostcoReturnsVariableGroceries() {
            Categorization result = service.categorize("UBER CANADA/UBERCOSTCO TORONTO");

            assertThat(result.expenseType()).isEqualTo(ExpenseType.VARIABLE);
            assertThat(result.categoryName()).isEqualTo("Groceries");
        }
    }

    // ── Variable / Food & Leisure ───────────────────────────────────────────

    @Nested
    class FoodLeisureTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "TIM HORTONS #1436 MONTREAL",
                "MCDONALD'S #2314 Q04 SAINT-LAUREN",
                "PIZZA SOLEIL 2015 MONTREAL",
                "PIZZA PIZZA # 302 MONTREAL",
                "POUTINEVILLE BISHOP MONTREAL",
                "BOUSTAN COTE-VERTU SAINT-LAUREN",
                "POK POK MONTREAL",
                "KUNG PAO WOK MIRABEL",
                "WOK CAFE MONTREAL",
                "KOREAN FOOD MONTREAL",
                "Onigiri Shop MONTREAL",
                "DAWA CHICKEN RESTAURANT MONTREAL",
                "La Toxica St Hubert MONTREAL",
                "AMARA KING SAINT-LAUREN",
                "MEET FRESH T&T MONTREAL",
                "PATISSERIE COCOBUN-CON MONTREAL",
                "CHARTWELLS-47432 SAINT-LAUREN",
                "LS Alphabet Cafe Montreal",
                "CAFE OLIMPICO WESTMOUNT",
                "MCKIBBIN'S BISHOP MONTREAL",
                "BOTECO RESTAURANT BAR MONTREAL",
                "BRASS DOOR PUB MONTREAL",
                "BEN & JERRY'S MONTREAL",
                "LA DIPERIE DIP019 MONTREAL",
                "LS Notre Boeuf de Grac Montreal",
                "COUCHE-TARD #1257 MONTREAL",
                "TABAGIE SARA MONTREAL",
                "MLLE CATHERINE MONTREAL"
        })
        void foodLeisureKeywordsReturnVariableFoodLeisure(String description) {
            Categorization result = service.categorize(description);

            assertThat(result.expenseType()).isEqualTo(ExpenseType.VARIABLE);
            assertThat(result.categoryName()).isEqualTo("Food & Leisure");
        }

        @Test
        void uberEatsReturnsVariableFoodLeisure() {
            Categorization result = service.categorize("UBER CANADA/UBEREATS TORONTO");

            assertThat(result.expenseType()).isEqualTo(ExpenseType.VARIABLE);
            assertThat(result.categoryName()).isEqualTo("Food & Leisure");
        }
    }

    // ── Variable / Pharmacy & Health ────────────────────────────────────────

    @Nested
    class PharmacyTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "PHARMAPRIX 0052 SAINT LAUREN",
                "PHARMAPRIX 55 MONTREAL",
                "PHARMAPRIX 42 MONTREAL",
                "PHARMACIE JEAN COUTU #068 MONTREAL"
        })
        void pharmacyKeywordsReturnVariablePharmacy(String description) {
            Categorization result = service.categorize(description);

            assertThat(result.expenseType()).isEqualTo(ExpenseType.VARIABLE);
            assertThat(result.categoryName()).isEqualTo("Pharmacy & Health");
        }
    }

    // ── Occasional ──────────────────────────────────────────────────────────

    @Nested
    class OccasionalTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "WINNERS 470 MONTREAL",
                "WINNERSHOMESENSE 37004 POINTE-CLAIR",
                "LaMaisonSimons Montreal",
                "Tommy Hilfiger Mirabel",
                "LEVIS L'ENTREPOT #803 MIRABEL",
                "2317 RWCO Mirabel",
                "BOUTIQUE JAGGERS POINTE-CLAIR",
                "IKEA.CA ONLINE BURLINGTON",
                "IKEA MONTREAL SAINT-LAUREN",
                "DOLLARAMA # 8 MONTREAL",
                "LE MEME PRIX PLUS SAINT-LAUREN",
                "FAMOUS PLAYER 9406QPS MONTREAL",
                "CINEPLEX 8030 WEB QPS 416-323-6600",
                "ARCADE MTL MONTREAL",
                "SALLE DE QUILLES ROSE BOW MONTREAL",
                "SQ *LES 3 FILLES SPA BEAU Montreal",
                "CONCORDIA UNIVERSITY MONTREAL",
                "PAYPATH SERV FEE - CON MONTREAL",
                "IMMIGRATION QUEBEC MONTREAL",
                "AMZN Mktp CA*007K64453 866-216-1072",
                "SHAUNS AUTO SERVICE 514-766-9075",
                "STM STUDIO PHOTO MONTREAL",
                "BIBLIOTHEQUE VIEUX-SAI MONTREAL"
        })
        void occasionalKeywordsReturnOccasional(String description) {
            Categorization result = service.categorize(description);

            assertThat(result.expenseType()).isEqualTo(ExpenseType.OCCASIONAL);
            assertThat(result.categoryName()).isEqualTo("Occasional");
        }
    }

    // ── Fallback ────────────────────────────────────────────────────────────

    @Nested
    class FallbackTests {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "UNKNOWN MERCHANT XYZ"})
        void unmatchedDescriptionsFallToOccasional(String description) {
            Categorization result = service.categorize(description);

            assertThat(result.expenseType()).isEqualTo(ExpenseType.OCCASIONAL);
            assertThat(result.categoryName()).isEqualTo("Occasional");
        }
    }

    // ── Uber sub-routing ────────────────────────────────────────────────────

    @Nested
    class UberSubRoutingTests {

        static Stream<Arguments> uberVariants() {
            return Stream.of(
                    Arguments.of("UBER CANADA/UBEREATS TORONTO", ExpenseType.VARIABLE, "Food & Leisure"),
                    Arguments.of("UBER CANADA/UBERCOSTCO TORONTO", ExpenseType.VARIABLE, "Groceries"),
                    Arguments.of("UBER CANADA/UBERTRIP TORONTO", ExpenseType.FIXED, "Transport"),
                    Arguments.of("UBER *TRIP HELP.UBER.COM Toronto", ExpenseType.FIXED, "Transport"),
                    Arguments.of("Uber Holdings Canada Inc. Toronto", ExpenseType.FIXED, "Subscriptions")
            );
        }

        @ParameterizedTest
        @MethodSource("uberVariants")
        void uberDescriptionsRouteCorrectly(String description, ExpenseType expectedType, String expectedCategory) {
            Categorization result = service.categorize(description);

            assertThat(result.expenseType()).isEqualTo(expectedType);
            assertThat(result.categoryName()).isEqualTo(expectedCategory);
        }

        @Test
        void unknownUberVariantFallsToOccasional() {
            Categorization result = service.categorize("UBER CANADA/SOMETHING ELSE");

            assertThat(result.expenseType()).isEqualTo(ExpenseType.OCCASIONAL);
            assertThat(result.categoryName()).isEqualTo("Occasional");
        }
    }

    // ── DoorDash sub-routing ────────────────────────────────────────────────

    @Nested
    class DoorDashSubRoutingTests {

        static Stream<Arguments> doorDashVariants() {
            return Stream.of(
                    Arguments.of("DOORDASHDASHPASS DOWNTOWN TOR", ExpenseType.FIXED, "Subscriptions"),
                    Arguments.of("DOORDASHMAXI DOWNTOWN TOR", ExpenseType.VARIABLE, "Groceries"),
                    Arguments.of("DD/DOORDASHMAXI VANCOUVER", ExpenseType.VARIABLE, "Groceries"),
                    Arguments.of("DD/DOORDASHMETRO VANCOUVER", ExpenseType.VARIABLE, "Groceries")
                    // Generic DoorDash without MAXI/METRO/DASHPASS → Food & Leisure
                    // (no real example in the data yet, but the fallback works)
            );
        }

        @ParameterizedTest
        @MethodSource("doorDashVariants")
        void doorDashDescriptionsRouteCorrectly(String description, ExpenseType expectedType, String expectedCategory) {
            Categorization result = service.categorize(description);

            assertThat(result.expenseType()).isEqualTo(expectedType);
            assertThat(result.categoryName()).isEqualTo(expectedCategory);
        }

        @Test
        void genericDoorDashFallsToFoodLeisure() {
            Categorization result = service.categorize("DOORDASH SOME RESTAURANT TOR");

            assertThat(result.expenseType()).isEqualTo(ExpenseType.VARIABLE);
            assertThat(result.categoryName()).isEqualTo("Food & Leisure");
        }
    }

    // ── Case insensitivity ──────────────────────────────────────────────────

    @Nested
    class CaseInsensitivityTests {

        @Test
        void matchesRegardlessOfCase() {
            assertThat(service.categorize("netflix.com 866-716-0414").categoryName())
                    .isEqualTo("Subscriptions");
            assertThat(service.categorize("walmart.ca mississauga").categoryName())
                    .isEqualTo("Groceries");
            assertThat(service.categorize("payment - thank you").categoryName())
                    .isEqualTo("Payment");
        }
    }

    // ── ASHTON must not match CHATR ─────────────────────────────────────────

    @Test
    void ashtonDoesNotMatchChatr() {
        // ASHTON contains no Phone/Internet keyword — it's a restaurant
        Categorization result = service.categorize("ASHTON / MIRABEL MIRABEL");

        assertThat(result.expenseType()).isEqualTo(ExpenseType.VARIABLE);
        assertThat(result.categoryName()).isEqualTo("Food & Leisure");
    }
}
