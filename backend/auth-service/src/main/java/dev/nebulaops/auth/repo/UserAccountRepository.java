package dev.nebulaops.auth.repo;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import dev.nebulaops.auth.domain.UserAccount;

public interface UserAccountRepository extends MongoRepository<UserAccount, String> {
    Optional<UserAccount> findByEmail(String email);
}
