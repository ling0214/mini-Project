package com.miniproject.backend.integrations;

import org.springframework.data.jpa.repository.JpaRepository;

interface GoogleTokenRepository extends JpaRepository<GoogleTokenEntity, String> {
}
