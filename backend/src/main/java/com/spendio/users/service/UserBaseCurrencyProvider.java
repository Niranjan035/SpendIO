package com.spendio.users.service;

import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.money.BaseCurrencyPort;
import com.spendio.users.repository.UserRepository;
import org.springframework.stereotype.Service;

/**
 * Adapter for the shared base-currency port (ARCHITECTURE.md B.3): the reporting
 * currency is the user's default currency. accounts may depend on the port interface in
 * sharedkernel but never on the users module, so the bean itself lives here.
 */
@Service
public class UserBaseCurrencyProvider implements BaseCurrencyPort {

    private final UserRepository userRepository;

    public UserBaseCurrencyProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public String baseCurrencyCode(Long userId) {
        return userRepository.findById(userId)
                .map(com.spendio.users.domain.User::getDefaultCurrencyCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "User not found: " + userId));
    }
}