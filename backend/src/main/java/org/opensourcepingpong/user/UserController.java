package org.opensourcepingpong.user;

import jakarta.validation.Valid;
import org.opensourcepingpong.auth.AuthPrincipal;
import org.opensourcepingpong.common.api.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/users")
public class UserController {
    private final UserService service; public UserController(UserService service){this.service=service;}
    @GetMapping("/me") ApiResponse<UserService.UserView> me(@AuthenticationPrincipal AuthPrincipal user){return ApiResponse.ok(service.me(user.userId()));}
    @PutMapping("/me") ApiResponse<UserService.UserView> update(@AuthenticationPrincipal AuthPrincipal user,@Valid @RequestBody UserService.UpdateProfileRequest r){return ApiResponse.ok(service.update(user.userId(),r));}
    @GetMapping("/{id}/public") ApiResponse<UserService.PublicUserView> publicProfile(@PathVariable java.util.UUID id){return ApiResponse.ok(service.publicProfile(id));}
}
