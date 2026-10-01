package org.opensourcepingpong.user;

import java.util.UUID;
import org.opensourcepingpong.common.api.BusinessException;
import org.opensourcepingpong.user.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository users; private final UserProfileRepository profiles;
    public UserService(UserRepository users,UserProfileRepository profiles){this.users=users;this.profiles=profiles;}
    public UserView me(UUID id){User user=user(id);UserProfile profile=profiles.findById(id).orElseThrow(()->notFound());return UserView.of(user,profile);}
    @Transactional public UserView update(UUID id, UpdateProfileRequest r){User user=user(id);UserProfile p=profiles.findById(id).orElseThrow(()->notFound());p.update(r.nickname().trim(),r.realName(),r.avatarUrl(),r.college(),r.major(),r.grade(),r.bio());return UserView.of(user,p);}
    public PublicUserView publicProfile(UUID id){UserProfile p=profiles.findById(id).orElseThrow(()->notFound());return new PublicUserView(id,p.getNickname(),p.getRealName(),p.getAvatarUrl());}
    private User user(UUID id){return users.findById(id).orElseThrow(()->notFound());} private static BusinessException notFound(){return new BusinessException(HttpStatus.NOT_FOUND,"USER_NOT_FOUND","用户不存在");}
    public record UpdateProfileRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=40) String nickname,@jakarta.validation.constraints.Size(max=40) String realName,@jakarta.validation.constraints.Size(max=500) String avatarUrl,@jakarta.validation.constraints.Size(max=100) String college,@jakarta.validation.constraints.Size(max=100) String major,@jakarta.validation.constraints.Size(max=40) String grade,@jakarta.validation.constraints.Size(max=500) String bio){}
    public record UserView(UUID id,String account,String status,String nickname,String realName,String avatarUrl,String college,String major,String grade,String bio){static UserView of(User u,UserProfile p){return new UserView(u.getId(),u.getAccount(),u.getStatus().name(),p.getNickname(),p.getRealName(),p.getAvatarUrl(),p.getCollege(),p.getMajor(),p.getGrade(),p.getBio());}}
    public record PublicUserView(UUID id,String nickname,String realName,String avatarUrl){}
}
