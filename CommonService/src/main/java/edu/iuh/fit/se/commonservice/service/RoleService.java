package edu.iuh.fit.se.commonservice.service;

import edu.iuh.fit.se.commonservice.model.Role;
import edu.iuh.fit.se.commonservice.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;

    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    public Role getRoleById(String id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Role not found with id: " + id));
    }

    public Role getRoleByName(String name) {
        return roleRepository.findByName(name)
                .orElseThrow(() -> new RuntimeException("Role not found with name: " + name));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Role createRole(Role role) {
        if (roleRepository.existsByName(role.getName())) {
            throw new RuntimeException("Role already exists: " + role.getName());
        }
        role.setCreatedAt(LocalDateTime.now());
        role.setUpdatedAt(LocalDateTime.now());
        role.setActive(true);
        return roleRepository.save(role);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Role updateRole(String id, Role role) {
        Role existingRole = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Role not found with id: " + id));
        
        existingRole.setDescription(role.getDescription());
        existingRole.setPermissions(role.getPermissions());
        existingRole.setUpdatedAt(LocalDateTime.now());
        
        return roleRepository.save(existingRole);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void deleteRole(String id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Role not found with id: " + id));
        role.setActive(false);
        roleRepository.save(role);
    }
}

