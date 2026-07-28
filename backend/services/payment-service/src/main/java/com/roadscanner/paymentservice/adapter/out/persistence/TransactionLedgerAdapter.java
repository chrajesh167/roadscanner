package com.roadscanner.paymentservice.adapter.out.persistence;

import com.roadscanner.paymentservice.domain.model.GatewayReference;
import com.roadscanner.paymentservice.domain.model.Money;
import com.roadscanner.paymentservice.domain.model.PaymentId;
import com.roadscanner.paymentservice.domain.model.PaymentTransaction;
import com.roadscanner.paymentservice.domain.model.RefundId;
import com.roadscanner.paymentservice.domain.model.TransactionId;
import com.roadscanner.paymentservice.domain.model.TransactionType;
import com.roadscanner.paymentservice.domain.port.out.TransactionLedger;
import org.springframework.stereotype.Repository;

import java.util.Currency;
import java.util.List;

@Repository
class TransactionLedgerAdapter implements TransactionLedger {

    private final PaymentTransactionSpringDataRepository springDataRepository;

    TransactionLedgerAdapter(PaymentTransactionSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public void append(PaymentTransaction transaction) {
        springDataRepository.save(new PaymentTransactionJpaEntity(
                transaction.id().value(),
                transaction.paymentId().value(),
                transaction.refundId() == null ? null : transaction.refundId().value(),
                transaction.type().name(),
                transaction.amount().amount(),
                transaction.amount().currency().getCurrencyCode(),
                transaction.gatewayReference() == null ? null : transaction.gatewayReference().gatewayPaymentId(),
                transaction.gatewayReference() == null ? null : transaction.gatewayReference().gatewayRefundId(),
                transaction.occurredAt()));
    }

    @Override
    public List<PaymentTransaction> findByPaymentId(PaymentId paymentId) {
        return springDataRepository.findByPaymentId(paymentId.value()).stream()
                .map(e -> new PaymentTransaction(
                        new TransactionId(e.getId()),
                        new PaymentId(e.getPaymentId()),
                        e.getRefundId() == null ? null : new RefundId(e.getRefundId()),
                        TransactionType.valueOf(e.getType()),
                        new Money(e.getAmount(), Currency.getInstance(e.getCurrency())),
                        new GatewayReference(e.getGatewayPaymentId(), null, e.getGatewayRefundId()),
                        e.getOccurredAt()))
                .toList();
    }
}
