package org.opensourcepingpong.organization;
import org.springframework.data.jpa.repository.*; import java.util.*;
public interface UserOrganizationRoleRepository extends JpaRepository<UserOrganizationRole,UUID>{@Query("select r.code from UserOrganizationRole ur join OrganizationRole r on ur.roleId=r.id where ur.userId=:userId and ur.revokedAt is null") Set<RoleCode> activeCodes(UUID userId); boolean existsByUserIdAndRoleId(UUID userId,UUID roleId);}
