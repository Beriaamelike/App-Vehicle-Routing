package com.example.spring.entity;



import jakarta.annotation.Nonnull;
import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(name="ROLE_DETAILS")
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long roleId;

    @Column(unique = true)
    private String roleName;
}
