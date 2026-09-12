package com.afinco.backend.domain;

/**
 * Classifies the nature of an expense category.
 *
 * <ul>
 *   <li>{@code PAYMENT} – credit-card payments and rewards redemptions (positive magnitude, netted negatively)</li>
 *   <li>{@code FIXED} – predictable monthly charges: subscriptions, phone/internet, transport, rent</li>
 *   <li>{@code VARIABLE} – monthly necessities with fluctuating amounts: groceries, food, pharmacy</li>
 *   <li>{@code OCCASIONAL} – sporadic lifestyle expenses and the catch-all fallback</li>
 * </ul>
 */
public enum ExpenseType {
    PAYMENT,
    FIXED,
    VARIABLE,
    OCCASIONAL
}
