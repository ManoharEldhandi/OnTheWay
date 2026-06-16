package com.ontheway.model;

import com.ontheway.model.enums.UserRole;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.ontheway.model.enums.PaymentStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus paymentStatus;

    @Column(nullable = false)
    private String paymentMethod;

    @Column(nullable = false)
    private Double amount;

    /** Provider that processed this payment (e.g. mock, stripe, razorpay). */
    @Column(length = 20)
    private String gateway;

    /** Reference/intent id returned by the payment gateway. */
    @Column(length = 100)
    private String gatewayReference;

    @Column(nullable = false)
    private LocalDateTime paymentTime;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
