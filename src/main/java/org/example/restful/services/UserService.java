package org.example.restful.services;

import org.example.restful.dtos.UserDTO;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class UserService {

    // Simule une base de données en mémoire
    private final Map<String, UserDTO> userDatabase = new HashMap<>();

    public boolean registerUser(UserDTO userDTO) {
        if (userDatabase.containsKey(userDTO.getUsername())) {
            return false; // L'utilisateur existe déjà
        }
        userDatabase.put(userDTO.getUsername(), userDTO);
        return true;
    }

    public boolean deleteUser(String userId) {
        return userDatabase.remove(userId) != null;
    }
}
