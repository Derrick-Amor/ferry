package seb.dto;

import lombok.Data;

@Data
public class AuditDTO {
    private Long id;
    private Boolean pass;
    private String auditComment;
}
