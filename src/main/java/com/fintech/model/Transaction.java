package com.fintech.model;

import com.fintech.constants.TransactionStatus;
import io.ebean.Model;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transactions")
public class Transaction extends Model {

    @Id
    public String id;


    @ManyToOne
    @JoinColumn(name = "sender_account_id")
    public Account sender;


    @ManyToOne
    @JoinColumn(name = "receiver_account_id")
    public Account receiver;

    @Column(nullable = false, columnDefinition = "DECIMAL(19,4)")
    public BigDecimal amount;

    @Enumerated(EnumType.STRING)
    public TransactionStatus status;

    @Column(unique = true)
    public String referenceId;

    public boolean emailSent = false;
    public Instant createdAt;
}