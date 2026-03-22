package edu.iuh.fit.se.messegeservice.config;

/**
 * Tham chiếu user dùng khi seed (tránh inner class + CGLIB trên {@link DataSeeder}).
 */
public record SeedUserRef(String id, String name, String avatar) {}
