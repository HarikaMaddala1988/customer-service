package com.example.customerservice.dto;

public class EchoResponse {

    private String message;

    public EchoResponse() {}

    public EchoResponse(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
