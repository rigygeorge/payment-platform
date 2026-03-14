package com.paymentplatform.merchant.security;

import com.paymentplatform.merchant.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MerchantUserDetailsService implements UserDetailsService {

    private final MerchantRepository merchantRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return merchantRepository.findByEmail(email)
                .map(merchant -> User.builder()
                        .username(merchant.getEmail())
                        .password(merchant.getPassword())
                        .roles("MERCHANT")
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Merchant not found: " + email));
    }
}