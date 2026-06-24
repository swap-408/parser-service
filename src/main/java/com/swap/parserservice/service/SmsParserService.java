package com.swap.parserservice.service;

import com.swap.parserservice.dto.ParsedExpenseDto;

public interface SmsParserService {
    ParsedExpenseDto parse(String message);
}
