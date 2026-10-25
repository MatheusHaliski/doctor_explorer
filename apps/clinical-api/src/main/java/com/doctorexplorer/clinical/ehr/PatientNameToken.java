package com.doctorexplorer.clinical.ehr;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

/** Linha do índice cego de nomes (ver {@code BlindIndex}). */
@Entity
@Table(name = "patient_name_index")
@IdClass(PatientNameToken.Key.class)
public class PatientNameToken {

    public static class Key implements Serializable {
        UUID patientId;
        String token;

        public Key() {}

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && k.patientId.equals(patientId) && k.token.equals(token);
        }

        @Override
        public int hashCode() {
            return patientId.hashCode() * 31 + token.hashCode();
        }
    }

    @Id
    @Column(name = "patient_id")
    private UUID patientId;

    @Id
    private String token;

    protected PatientNameToken() {}

    public PatientNameToken(UUID patientId, String token) {
        this.patientId = patientId;
        this.token = token;
    }
}
