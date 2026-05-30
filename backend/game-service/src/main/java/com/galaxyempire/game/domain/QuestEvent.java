package com.galaxyempire.game.domain;

public record QuestEvent(Long playerId, String requirementType, String target, int value) {}
