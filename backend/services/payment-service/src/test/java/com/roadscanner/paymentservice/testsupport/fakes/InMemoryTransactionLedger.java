package com.roadscanner.paymentservice.testsupport.fakes;

import com.roadscanner.paymentservice.domain.model.PaymentId;
import com.roadscanner.paymentservice.domain.model.PaymentTransaction;
import com.roadscanner.paymentservice.domain.port.out.TransactionLedger;

import java.util.ArrayList;
import java.util.List;

public class InMemoryTransactionLedger implements TransactionLedger {

    public final List<PaymentTransaction> transactions = new ArrayList<>();

    @Override
    public void append(PaymentTransaction transaction) {
        transactions.add(transaction);
    }

    @Override
    public List<PaymentTransaction> findByPaymentId(PaymentId paymentId) {
        return transactions.stream().filter(t -> t.paymentId().equals(paymentId)).toList();
    }
}
