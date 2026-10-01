package org.opensourcepingpong.user.domain;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {}
