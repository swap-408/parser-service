package com.swap.parserservice.kafka;

import com.swap.parserservice.dto.ParsedExpenseDto;
import com.swap.parserservice.dto.SmsEventDto;
import com.swap.parserservice.service.SmsParserService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class SmsConsumer {

    private final SmsParserService smsParserService;
    private final ParsedExpenseProducer parsedExpenseProducer;

    public SmsConsumer(SmsParserService smsParserService,
                       ParsedExpenseProducer parsedExpenseProducer) {
        this.smsParserService = smsParserService;
        this.parsedExpenseProducer = parsedExpenseProducer;
    }

    @KafkaListener(topics = "sms.received", groupId = "parser-group")
    public void consume(SmsEventDto event) {

        ParsedExpenseDto parsed = smsParserService.parse(event.getMessage());
        parsed.setUserEmail(event.getUserEmail());
        parsed.setSource("SMS");
        parsed.setRawMessage(event.getMessage());

        parsedExpenseProducer.publish(parsed);

    }
}