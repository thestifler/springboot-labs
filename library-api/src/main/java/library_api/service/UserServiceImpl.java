package library_api.service;

import library_api.dto.UserRequest;
import library_api.dto.UserResponse;
import library_api.entity.User;
import library_api.exception.UserNotFoundException;
import library_api.repository.UserRepository;
import library_api.util.mapper.UserMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserServiceImpl(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUser(Long id) {
        log.debug("Buscando usuario con id={}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("No existe usuario con id={}", id);
                    return new UserNotFoundException(id);
                });

        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse createUser(UserRequest userRequest) {
        log.debug("Creando usuario con nombre={}", userRequest.name());

        // El id no se copia desde el request: queda en null y Hibernate delega en
        // GenerationType.IDENTITY, por lo que Spring Data usa persist() (INSERT).
        User saved = userRepository.save(userMapper.toEntity(userRequest));

        log.info("Usuario creado con id={}", saved.getId());
        return userMapper.toResponse(saved);
    }
}
