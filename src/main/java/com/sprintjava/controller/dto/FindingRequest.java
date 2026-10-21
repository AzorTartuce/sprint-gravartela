package com.sprintjava.controller.dto;

/** Payload de criação (client_id, finding_type, title) e atualização de finding — na atualização, campos null não são alterados. */
public class FindingRequest {

    private Long clientId;
    private Long originMeetingId;
    private String findingType;
    private String title;
    private String severityHint;
    private String status;

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public Long getOriginMeetingId() {
        return originMeetingId;
    }

    public void setOriginMeetingId(Long originMeetingId) {
        this.originMeetingId = originMeetingId;
    }

    public String getFindingType() {
        return findingType;
    }

    public void setFindingType(String findingType) {
        this.findingType = findingType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSeverityHint() {
        return severityHint;
    }

    public void setSeverityHint(String severityHint) {
        this.severityHint = severityHint;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
