package org.opensourcepingpong.organization;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface OrganizationRoleRepository extends JpaRepository<OrganizationRole,UUID>{Optional<OrganizationRole> findByCode(RoleCode code);}
