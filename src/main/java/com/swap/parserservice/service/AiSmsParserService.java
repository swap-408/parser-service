package com.swap.parserservice.service;

import com.swap.parserservice.dto.ParsedExpenseDto;
import com.swap.parserservice.dto.SmsEventDto;

public interface AiSmsParserService {
    ParsedExpenseDto parse(SmsEventDto event);
}
