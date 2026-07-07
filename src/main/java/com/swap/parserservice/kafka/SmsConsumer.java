package com.swap.parserservice.kafka;

import com.swap.parserservice.dto.ParsedExpenseDto;
import com.swap.parserservice.dto.SmsEventDto;
import com.swap.parserservice.service.AiSmsParserService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class SmsConsumer {

    private final AiSmsParserService aiSmsParserService;
    private final ParsedExpenseProducer parsedExpenseProducer;

    public SmsConsumer(AiSmsParserService aiSmsParserService,
                       ParsedExpenseProducer parsedExpenseProducer) {
        this.aiSmsParserService = aiSmsParserService;
        this.parsedExpenseProducer = parsedExpenseProducer;
    }

    @KafkaListener(topics = "sms.received", groupId = "parser-group")
    public void consume(SmsEventDto event) {
        ParsedExpenseDto parsed = aiSmsParserService.parse(event);
        if (parsed == null) {
            return;
        }
        parsedExpenseProducer.publish(parsed);
    }
}