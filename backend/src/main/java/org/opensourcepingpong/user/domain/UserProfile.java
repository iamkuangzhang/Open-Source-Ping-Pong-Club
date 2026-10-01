package org.opensourcepingpong.user.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "user_profiles")
public class UserProfile {
    @Id @Column(name="user_id") private UUID userId;
    @Column(nullable=false,length=40) private String nickname;
    @Column(name="real_name",length=40) private String realName;
    @Column(name="avatar_url",length=500) private String avatarUrl;
    @Column(length=100) private String college;
    @Column(length=100) private String major;
    @Column(length=40) private String grade;
    @Column(length=500) private String bio;
    @Column(name="student_number",length=40) private String studentNumber;
    @Column(length=30) private String phone;
    @Column(length=100) private String wechat;
    @Column(length=30) private String qq;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected UserProfile() {}
    public UserProfile(UUID id, String nickname){this.userId=id;this.nickname=nickname;this.createdAt=Instant.now();this.updatedAt=createdAt;}
    public UUID getUserId(){return userId;} public String getNickname(){return nickname;} public String getRealName(){return realName;} public String getAvatarUrl(){return avatarUrl;} public String getCollege(){return college;} public String getMajor(){return major;} public String getGrade(){return grade;} public String getBio(){return bio;}
    public void update(String nickname,String realName,String avatarUrl,String college,String major,String grade,String bio){this.nickname=nickname;this.realName=realName;this.avatarUrl=avatarUrl;this.college=college;this.major=major;this.grade=grade;this.bio=bio;this.updatedAt=Instant.now();}
}
