package com.nexatech.payment.service;

import com.nexatech.payment.dto.CreatePaymentRequest;
import com.nexatech.payment.dto.PaymentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PaymentService {

    public PaymentResponse createPayment(UUID customerId, CreatePaymentRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public PaymentResponse getPayment(UUID paymentId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public PaymentResponse getPaymentByOrder(UUID orderId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public Page<PaymentResponse> getMyPayments(UUID customerId, Pageable pageable) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public PaymentResponse refund(UUID paymentId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
