package com.fintech.model;

import com.fintech.constants.EmployeeRole;
import com.fintech.constants.EmployeeStatus;
import io.ebean.Model;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "profiles")
public class Profile extends Model {
    @Id
    public String id;

    @Column(unique = true, nullable = false)
    public String employeeId;

    public String firstName;
    public String lastName;

    @Column(unique = true, nullable = false)
    public String email;

    @Enumerated(EnumType.STRING) // Maps to MySQL ENUM
    public EmployeeRole role;

    @Enumerated(EnumType.STRING)
    public EmployeeStatus status;
    public Instant createdAt;
}