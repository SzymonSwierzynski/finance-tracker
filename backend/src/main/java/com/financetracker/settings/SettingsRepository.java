package com.financetracker.settings;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Settings are keyed by user id, so {@code findById(userId)} is already user-scoped — there is no
 * unscoped finder that could reach another user's row.
 */
public interface SettingsRepository extends JpaRepository<Settings, Long> {}
