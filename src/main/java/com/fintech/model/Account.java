package com.fintech.model;

import io.ebean.Model;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts")
public class Account extends Model {

    @Id
    public String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "profile_id")
    public Profile owner;

    @Column(columnDefinition = "DECIMAL(19,4) DEFAULT 0.0000")
    public BigDecimal balance;

    @Version
    public Long version;
}