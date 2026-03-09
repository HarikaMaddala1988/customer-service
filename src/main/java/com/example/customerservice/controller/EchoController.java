package com.example.customerservice.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EchoController {

    private static final Logger logger = LoggerFactory.getLogger(EchoController.class);

    @GetMapping("/echo")
    public String echo() {
        logger.info("Accessed again4 /echo endpoint");
        return "success";
    }
}
