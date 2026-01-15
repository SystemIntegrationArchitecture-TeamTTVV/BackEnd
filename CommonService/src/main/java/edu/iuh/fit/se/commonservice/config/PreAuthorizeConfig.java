package edu.iuh.fit.se.commonservice.config;

/**
 * Constants for role-based authorization
 * Use these constants in @PreAuthorize annotations
 */
public class PreAuthorizeConfig {
    public static final String USER_OR_ADMIN = "hasAnyRole('ADMIN', 'MODERATOR', 'USER')";
    public static final String ADMIN_ONLY = "hasRole('ADMIN')";
    public static final String ADMIN_OR_MODERATOR = "hasAnyRole('ADMIN', 'MODERATOR')";
}

