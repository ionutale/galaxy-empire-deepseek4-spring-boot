package com.galaxyempire.game.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "quest_definition")
public class QuestDefinition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quest_type", nullable = false, length = 20)
    private String questType;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(name = "requirement_type", nullable = false, length = 40)
    private String requirementType;

    @Column(name = "requirement_value", nullable = false)
    private int requirementValue;

    @Column(name = "reward_type", nullable = false, length = 20)
    private String rewardType;

    @Column(name = "reward_amount", nullable = false)
    private int rewardAmount;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 255)
    private String description;

    @Column(length = 40)
    private String icon;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean daily;

    public QuestDefinition() {}

    public Long getId() { return id; }
    public String getQuestType() { return questType; }
    public String getCategory() { return category; }
    public String getRequirementType() { return requirementType; }
    public int getRequirementValue() { return requirementValue; }
    public String getRewardType() { return rewardType; }
    public int getRewardAmount() { return rewardAmount; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getIcon() { return icon; }
    public int getSortOrder() { return sortOrder; }
    public boolean isDaily() { return daily; }
}
