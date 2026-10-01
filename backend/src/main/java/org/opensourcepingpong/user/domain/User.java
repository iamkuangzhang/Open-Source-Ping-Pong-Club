package org.opensourcepingpong.user.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "users")
public class User {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 32) private String account;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private UserStatus status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected User() {}
    public User(String account, String passwordHash) { this.id=UUID.randomUUID(); this.account=account; this.passwordHash=passwordHash; this.status=UserStatus.NORMAL; this.createdAt=Instant.now(); this.updatedAt=this.createdAt; }
    public UUID getId(){return id;} public String getAccount(){return account;} public String getPasswordHash(){return passwordHash;} public UserStatus getStatus(){return status;}
}
