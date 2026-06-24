package com.swap.parserservice.service.impl;

import com.swap.parserservice.dto.ParsedExpenseDto;
import com.swap.parserservice.service.SmsParserService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SmsParserServiceImpl implements SmsParserService {

    @Override
    public ParsedExpenseDto parse(String message) {
        ParsedExpenseDto dto = new ParsedExpenseDto();

        // amount
        Pattern amountPattern = Pattern.compile("(?:Rs\\.?|INR\\.?|₹)\\s?(\\d+(?:\\.\\d{1,2})?)");
        Matcher amountMatcher = amountPattern.matcher(message);
        if (amountMatcher.find()) {
            dto.setAmount(new BigDecimal(amountMatcher.group(1)));
        }

        // merchant: simple heuristic after "spent on"
        Pattern merchantPattern = Pattern.compile("spent on\\s+([A-Za-z0-9&\\s]+?)(?:\\s+using|\\s+via|\\s+with|\\.|$)", Pattern.CASE_INSENSITIVE);
        Matcher merchantMatcher = merchantPattern.matcher(message);
        if (merchantMatcher.find()) {
            dto.setMerchant(merchantMatcher.group(1).trim());
        }

        // category: basic rule-based mapping
        dto.setCategory(inferCategory(message));

        return dto;
    }

    private String inferCategory(String message) {
        String text = message.toLowerCase();
        if (text.contains("swiggy") || text.contains("zomato") || text.contains("restaurant") || text.contains("food")) {
            return "Food";
        }
        if (text.contains("uber") || text.contains("ola") || text.contains("fuel") || text.contains("petrol") || text.contains("diesel")) {
            return "Transport";
        }
        if (text.contains("amazon") || text.contains("flipkart") || text.contains("shopping")) {
            return "Shopping";
        }
        if (text.contains("movie") || text.contains("netflix") || text.contains("prime")) {
            return "Entertainment";
        }
        return "Other";
    }
}