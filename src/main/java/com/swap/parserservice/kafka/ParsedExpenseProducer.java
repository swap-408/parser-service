package com.swap.parserservice.kafka;

import com.swap.parserservice.dto.ParsedExpenseDto;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class ParsedExpenseProducer {

    private final KafkaTemplate<String, ParsedExpenseDto> kafkaTemplate;

    public ParsedExpenseProducer(KafkaTemplate<String, ParsedExpenseDto> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(ParsedExpenseDto dto) {
        kafkaTemplate.send("expense.parsed", dto);
    }
}