package org.opensourcepingpong.user.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface UserRepository extends JpaRepository<User, UUID> { Optional<User> findByAccount(String account); boolean existsByAccount(String account); }
