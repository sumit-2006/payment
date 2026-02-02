package com.fintech.constants;

import io.ebean.annotation.DbEnumValue;

public enum EmployeeRole {
    EMPLOYEE("EMPLOYEE"),
    ADMIN("ADMIN"),
    HR("HR"),
    FINANCE("FINANCE");

    String value;
    EmployeeRole(String value) { this.value = value; }

    @DbEnumValue
    public String getValue() { return value; }
}