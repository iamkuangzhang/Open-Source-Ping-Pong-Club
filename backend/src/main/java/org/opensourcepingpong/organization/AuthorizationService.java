package org.opensourcepingpong.organization;

import java.util.Set;
import java.util.UUID;
import org.opensourcepingpong.common.api.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {
  private final UserOrganizationRoleRepository roles;

  public AuthorizationService(UserOrganizationRoleRepository roles) {
    this.roles = roles;
  }

  public Set<RoleCode> roles(UUID user) {
    return roles.activeCodes(user);
  }

  public void require(UUID user, Permission permission) {
    Set<RoleCode> userRoles = roles(user);
    boolean manager = userRoles.stream().anyMatch(role ->
        role == RoleCode.ADMIN
            || role == RoleCode.VICE_MINISTER
            || role == RoleCode.MINISTER
            || role == RoleCode.VICE_PRESIDENT
            || role == RoleCode.PRESIDENT
            || role == RoleCode.ADVISOR);
    boolean allowed = switch (permission) {
      case ROLE_MANAGE -> userRoles.contains(RoleCode.PRESIDENT) || userRoles.contains(RoleCode.ADVISOR);
      default -> manager;
    };
    if (!allowed) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "无权执行该操作");
    }
  }
}
