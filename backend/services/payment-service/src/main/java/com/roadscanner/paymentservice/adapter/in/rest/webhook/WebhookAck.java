package com.roadscanner.paymentservice.adapter.in.rest.webhook;

/** The result surfaced to the gateway after processing a webhook. */
public record WebhookAck(String outcome) {
}
