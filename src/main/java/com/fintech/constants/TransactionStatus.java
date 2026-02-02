package com.fintech.constants;

import io.ebean.annotation.DbEnumValue;

public enum TransactionStatus {
    PENDING("PENDING"),
    COMPLETED("COMPLETED"),
    FAILED("FAILED");

    String value;
    TransactionStatus(String value) { this.value = value; }

    @DbEnumValue
    public String getValue() { return value; }
}