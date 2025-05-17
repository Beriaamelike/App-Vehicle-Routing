package com.example.spring.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Table(name = "USER_DETAILS")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long userId;
    private String name;
    @Column(unique = true, length = 20)
    private String username;
    private String password;

    @ManyToMany(fetch=FetchType.EAGER)
    @JsonIgnore
    @ToString.Exclude
    private Set<Role> roles;
}