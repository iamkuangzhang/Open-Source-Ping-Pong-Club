package org.opensourcepingpong.auth;

import jakarta.validation.Valid;
import org.opensourcepingpong.common.api.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service; public AuthController(AuthService service){this.service=service;}
    @PostMapping("/register") ApiResponse<AuthDtos.TokenResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request){return ApiResponse.ok(service.register(request));}
    @PostMapping("/login") ApiResponse<AuthDtos.TokenResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request){return ApiResponse.ok(service.login(request));}
    @PostMapping("/refresh") ApiResponse<AuthDtos.TokenResponse> refresh(@Valid @RequestBody AuthDtos.RefreshRequest request){return ApiResponse.ok(service.refresh(request.refreshToken()));}
    @PostMapping("/logout") ApiResponse<Void> logout(@Valid @RequestBody AuthDtos.RefreshRequest request){service.logout(request.refreshToken());return ApiResponse.ok(null);}
}
