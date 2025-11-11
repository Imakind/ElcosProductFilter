package com.example.productfilter.model;

import jakarta.persistence.*;

@Entity
@Table(name="roles", uniqueConstraints=@UniqueConstraint(name="uk_role_name", columnNames="name"))
public class Role {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false)
    private String name; // "ROLE_ADMIN" или "ROLE_USER"

    public Role() {}
    public Role(String name){ this.name=name; }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }
}
