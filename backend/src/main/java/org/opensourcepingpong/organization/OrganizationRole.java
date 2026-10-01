package org.opensourcepingpong.organization;
import jakarta.persistence.*; import java.util.*;
@Entity @Table(name="organization_roles") public class OrganizationRole { @Id private UUID id; @Enumerated(EnumType.STRING) @Column(nullable=false) private RoleCode code; @Column(nullable=false) private String name; protected OrganizationRole(){} public UUID getId(){return id;} public RoleCode getCode(){return code;} }
