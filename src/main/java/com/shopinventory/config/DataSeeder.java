package com.shopinventory.config;

import com.shopinventory.domain.organization.Organization;
import com.shopinventory.domain.organization.OrganizationRepository;
import com.shopinventory.domain.user.Membership;
import com.shopinventory.domain.user.MembershipRepository;
import com.shopinventory.domain.user.MembershipStatus;
import com.shopinventory.domain.user.OrgRole;
import com.shopinventory.domain.user.PlatformRole;
import com.shopinventory.domain.user.User;
import com.shopinventory.domain.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    public DataSeeder(OrganizationRepository organizationRepository,
                      UserRepository userRepository,
                      MembershipRepository membershipRepository,
                      PasswordEncoder passwordEncoder,
                      AppProperties properties) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Organization org = organizationRepository.findAll().stream().findFirst()
                .orElseGet(() -> {
                    Organization created = new Organization();
                    created.setName(properties.firstOrgName());
                    created.setCurrency(properties.defaultCurrency());
                    Organization saved = organizationRepository.save(created);
                    log.info("Seeded default organization '{}' ({})", saved.getName(), saved.getId());
                    return saved;
                });

        User admin = userRepository.findByEmailIgnoreCase(properties.adminEmail()).orElseGet(() -> {
            User created = new User();
            created.setEmail(properties.adminEmail());
            created.setName("Shop Owner");
            created.setPasswordHash(passwordEncoder.encode(properties.adminPassword()));
            User savedUser = userRepository.save(created);
            log.info("Seeded admin user {}", savedUser.getEmail());
            return savedUser;
        });

        if (!membershipRepository.existsByOrgIdAndUserId(org.getId(), admin.getId())) {
            Membership membership = new Membership();
            membership.setOrg(org);
            membership.setUser(admin);
            membership.setRole(OrgRole.ADMIN);
            membership.setStatus(MembershipStatus.ACTIVE);
            membershipRepository.save(membership);
            log.info("Assigned {} as ADMIN of {}", admin.getEmail(), org.getName());
        }

        User superAdmin = userRepository.findByEmailIgnoreCase(properties.superAdminEmail()).orElseGet(() -> {
            User created = new User();
            created.setEmail(properties.superAdminEmail());
            created.setName("Super Admin");
            created.setPasswordHash(passwordEncoder.encode(properties.superAdminPassword()));
            created.setPlatformRole(PlatformRole.SUPER_ADMIN);
            User savedUser = userRepository.save(created);
            log.info("Seeded super admin {}", savedUser.getEmail());
            return savedUser;
        });

        // Platform users are NOT org members. Remove any legacy membership (old model
        // attached the super admin to the default org) and ensure the role is set.
        if (superAdmin.getPlatformRole() == null) {
            superAdmin.setPlatformRole(PlatformRole.SUPER_ADMIN);
            userRepository.save(superAdmin);
        }
        membershipRepository.findByOrgIdAndUserId(org.getId(), superAdmin.getId())
                .ifPresent(membershipRepository::delete);
    }
}