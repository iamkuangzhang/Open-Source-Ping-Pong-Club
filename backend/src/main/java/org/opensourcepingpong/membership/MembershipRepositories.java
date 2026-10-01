package org.opensourcepingpong.membership;
import org.springframework.data.jpa.repository.*; import java.util.*;
interface MembershipApplicationRepository extends JpaRepository<MembershipApplication,UUID>{boolean existsByUserIdAndStatus(UUID id,ApplicationStatus status); List<MembershipApplication> findByStatusOrderByCreatedAtAsc(ApplicationStatus status);}
interface MembershipRepository extends JpaRepository<Membership,UUID>{Optional<Membership> findByUserId(UUID userId); boolean existsByUserId(UUID userId);}
interface RatingAccountRepository extends JpaRepository<RatingAccount,UUID>{}
interface RatingLedgerRepository extends JpaRepository<RatingLedger,UUID>{}
