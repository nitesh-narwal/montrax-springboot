package in.tracking.moneymanager.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Service for auto-categorizing bank transactions based on merchant patterns.
 * Uses regex matching to suggest categories from transaction descriptions.
 *
 * Supports common Indian merchants and UPI patterns.
 */
@Service
public class MerchantCategorizationService {

    // Merchant patterns mapped to category names
    // Order matters - first match wins
    private static final Map<Pattern, String> MERCHANT_PATTERNS = new LinkedHashMap<>();

    static {
        // ==================== Food & Dining ====================
        MERCHANT_PATTERNS.put(
                Pattern.compile("(?i).*(SWIGGY|ZOMATO|DOMINOS|PIZZA|MCDONALD|KFC|BURGER|RESTAURANT|CAFE|FOOD|DINING|STARBUCKS|SUBWAY|DUNKIN|BIRYANI|CHAI|TEA|COFFEE).*"),
                "Food & Dining"
        );

        // ==================== Online Shopping ====================
        MERCHANT_PATTERNS.put(
                Pattern.compile("(?i).*(AMAZON|FLIPKART|MYNTRA|AJIO|SNAPDEAL|MEESHO|NYKAA|TATACLIQ|SHOPSY|SHOPPING|STORE).*"),
                "Shopping"
        );

        // ==================== Groceries ====================
        MERCHANT_PATTERNS.put(
                Pattern.compile("(?i).*(GROCERY|BIGBASKET|GROFERS|BLINKIT|ZEPTO|DMART|SUPERMARKET|RELIANCE|MORE|EASYDAY|SPENCER|FRESH).*"),
                "Groceries"
        );

        // ==================== Transportation ====================
        MERCHANT_PATTERNS.put(
                Pattern.compile("(?i).*(UBER|OLA|RAPIDO|METRO|IRCTC|RAILWAY|PETROL|FUEL|PARKING|TOLL|REDBUS|MAKEMYTRIP|GOIBIBO|YATRA).*"),
                "Transportation"
        );

        // ==================== Entertainment ====================
        MERCHANT_PATTERNS.put(
                Pattern.compile("(?i).*(NETFLIX|HOTSTAR|PRIME|SPOTIFY|GAANA|MOVIE|CINEMA|PVR|INOX|GAME|BOOKMYSHOW|PAYTM.*MOVIE|YOUTUBE).*"),
                "Entertainment"
        );

        // ==================== Healthcare ====================
        MERCHANT_PATTERNS.put(
                Pattern.compile("(?i).*(APOLLO|PHARMEASY|MEDPLUS|HOSPITAL|MEDICAL|PHARMACY|DOCTOR|HEALTH|NETMEDS|1MG|TATA.*HEALTH).*"),
                "Healthcare"
        );

        // ==================== Utilities & Bills ====================
        MERCHANT_PATTERNS.put(
                Pattern.compile("(?i).*(AIRTEL|JIO|VODAFONE|BSNL|ELECTRICITY|WATER|GAS|BESCOM|TATA.*POWER|BILL.*PAY|RECHARGE|DTH).*"),
                "Utilities"
        );

        // ==================== Insurance ====================
        MERCHANT_PATTERNS.put(
                Pattern.compile("(?i).*(LIC|INSURANCE|POLICY|PREMIUM|HDFC.*LIFE|ICICI.*PRU|SBI.*LIFE|MAX.*LIFE|BAJAJ.*ALLIANZ).*"),
                "Insurance"
        );

        // ==================== Education ====================
        MERCHANT_PATTERNS.put(
                Pattern.compile("(?i).*(SCHOOL|COLLEGE|UNIVERSITY|COURSE|EDUCATION|UDEMY|COURSERA|BYJU|UNACADEMY|UPGRAD|GREAT.*LEARNING).*"),
                "Education"
        );

        // ==================== Investments ====================
        MERCHANT_PATTERNS.put(
                Pattern.compile("(?i).*(ZERODHA|GROWW|UPSTOX|MUTUAL.*FUND|SIP|INVESTMENT|TRADING|DEMAT|PAYTM.*MONEY|KUVERA).*"),
                "Investments"
        );

        // ==================== EMI & Loans ====================
        MERCHANT_PATTERNS.put(
                Pattern.compile("(?i).*(EMI|LOAN|CREDIT.*CARD|BAJAJ.*FINSERV|TATA.*CAPITAL|HDFC.*BANK|ICICI.*BANK.*EMI).*"),
                "EMI & Loans"
        );

        // ==================== Rent & Housing ====================
        MERCHANT_PATTERNS.put(
                Pattern.compile("(?i).*(RENT|HOUSING|APARTMENT|FLAT|SOCIETY|MAINTENANCE|NOBROKER).*"),
                "Rent & Housing"
        );

        // ==================== Personal Care ====================
        MERCHANT_PATTERNS.put(
                Pattern.compile("(?i).*(SALON|PARLOUR|SPA|URBAN.*COMPANY|GYM|FITNESS|CULT|WELLNESS).*"),
                "Personal Care"
        );

        // ==================== ATM/Cash ====================
        MERCHANT_PATTERNS.put(
                Pattern.compile("(?i).*(ATM|CASH.*WITHDRAWAL|CASH.*WDL).*"),
                "Cash Withdrawal"
        );

        // ==================== Transfer ====================
        MERCHANT_PATTERNS.put(
                Pattern.compile("(?i).*(TRANSFER|NEFT|IMPS|RTGS|UPI.*/|FUND.*TRANSFER).*"),
                "Transfer"
        );

        // ==================== Salary (Credit) ====================
        MERCHANT_PATTERNS.put(
                Pattern.compile("(?i).*(SALARY|WAGES|PAYROLL|STIPEND).*"),
                "Salary"
        );
    }

    /**
     * Suggest a category based on transaction description.
     * Returns "Uncategorized" if no pattern matches.
     *
     * @param description Transaction description from bank
     * @return Suggested category name
     */
    public String suggestCategory(String description) {
        if (description == null || description.isEmpty()) {
            return "Uncategorized";
        }

        // Try each pattern in order
        for (Map.Entry<Pattern, String> entry : MERCHANT_PATTERNS.entrySet()) {
            if (entry.getKey().matcher(description).matches()) {
                return entry.getValue();
            }
        }

        return "Uncategorized";
    }

    /**
     * Extract merchant name from transaction description.
     * Handles common UPI formats.
     *
     * @param description Transaction description
     * @return Extracted merchant name or original description
     */
    public String extractMerchantName(String description) {
        if (description == null || description.isEmpty()) {
            return "Unknown";
        }

        // UPI format: UPI/123456/MERCHANT_NAME/PAYMENT
        if (description.toUpperCase().contains("UPI")) {
            String[] parts = description.split("[/-]");
            if (parts.length >= 3) {
                // Usually merchant name is at index 2 or 3
                for (int i = 2; i < Math.min(parts.length, 4); i++) {
                    String part = parts[i].trim();
                    if (!part.isEmpty() && !part.matches("\\d+") && part.length() > 2) {
                        return part;
                    }
                }
            }
        }

        // NEFT/IMPS format: NEFT-123456-MERCHANT NAME
        if (description.toUpperCase().contains("NEFT") ||
            description.toUpperCase().contains("IMPS")) {
            String[] parts = description.split("[-/]");
            if (parts.length >= 3) {
                return parts[parts.length - 1].trim();
            }
        }

        // Return first meaningful words
        String[] words = description.split("\\s+");
        StringBuilder merchant = new StringBuilder();
        for (String word : words) {
            if (!word.matches("\\d+") && word.length() > 2) {
                merchant.append(word).append(" ");
                if (merchant.length() > 20) break;
            }
        }

        return merchant.length() > 0 ? merchant.toString().trim() : description;
    }
}

