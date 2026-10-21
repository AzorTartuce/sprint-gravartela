package com.sprintjava.controller.dto;

import java.util.List;

public class StrategyRequest {

    private String key;
    private String label;
    private List<String> appliesTo;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public List<String> getAppliesTo() {
        return appliesTo;
    }

    public void setAppliesTo(List<String> appliesTo) {
        this.appliesTo = appliesTo;
    }
}
