package com.bridgeai.evaluation.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Column;

@Entity
public class UserProfile {
    @Id
    private String userId;

    @Column(length = 2000)
    private String portfolioEntry;

    public UserProfile() {
    }

    public UserProfile(String userId, String portfolioEntry) {
        this.userId = userId;
        this.portfolioEntry = portfolioEntry;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPortfolioEntry() {
        return portfolioEntry;
    }

    public void setPortfolioEntry(String portfolioEntry) {
        this.portfolioEntry = portfolioEntry;
    }
}
