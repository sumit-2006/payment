package com.fintech.constants;

import io.ebean.annotation.DbEnumValue;

public enum EmployeeStatus {
    ACTIVE("ACTIVE"),
    INACTIVE("INACTIVE"); // Use this to lock out fired employees

    String value;
    EmployeeStatus(String value) { this.value = value; }

    @DbEnumValue
    public String getValue() { return value; }
}