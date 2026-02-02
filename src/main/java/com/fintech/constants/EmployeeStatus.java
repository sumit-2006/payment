package com.fintech.constants;

import io.ebean.annotation.DbEnumValue;

public enum EmployeeStatus {
    ACTIVE("ACTIVE"),
    INACTIVE("INACTIVE");

    String value;
    EmployeeStatus(String value) { this.value = value; }

    @DbEnumValue
    public String getValue() { return value; }
}