package com.genesis.applywise.analysis;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnalysisInputLockRepository {

    private final JdbcTemplate jdbcTemplate;

    public AnalysisInputLockRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void acquire(String inputHash) {
        jdbcTemplate.queryForObject(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                Object.class,
                inputHash
        );
    }
}
