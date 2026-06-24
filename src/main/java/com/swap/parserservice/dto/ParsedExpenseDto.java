package com.swap.parserservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ParsedExpenseDto {

    private String userEmail;
    private BigDecimal amount;
    private String merchant;
    private String category;
    private String source;
    private String rawMessage;
}