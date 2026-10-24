package com.doctorexplorer.core.institution.domain;

import com.doctorexplorer.core.common.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** RF11 · Rede, hospital ou unidade. Hierarquia por {@code parentId}; só VERIFICADA aparece no diretório. */
@Entity
@Table(name = "institution")
public class Institution {

    public enum Type { NETWORK, HOSPITAL, UNIT }

    public enum State { VERIFICANDO, VERIFICADA, REJEITADA, SUSPENSA }

    @Id
    private UUID id;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "trade_name", nullable = false)
    private String tradeName;

    @Column(nullable = false, unique = true)
    private String cnpj;

    @Column
    private String cnes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String uf;

    @Column private Double lat;
    @Column private Double lng;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state = State.VERIFICANDO;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Institution() {}

    public Institution(String legalName, String tradeName, String cnpj, String cnes, Type type, UUID parentId,
            String city, String uf, Double lat, Double lng, UUID createdBy) {
        this.id = UuidV7.generate();
        this.legalName = legalName.trim();
        this.tradeName = tradeName.trim();
        this.cnpj = cnpj.replaceAll("\\D", "");
        this.cnes = cnes == null ? null : cnes.replaceAll("\\D", "");
        this.type = type;
        this.parentId = parentId;
        this.city = city.trim();
        this.uf = uf.trim().toUpperCase(Locale.ROOT);
        this.lat = lat;
        this.lng = lng;
        this.createdBy = createdBy;
    }

    public void markVerified() { state = State.VERIFICADA; touch(); }
    public void markRejected() { state = State.REJEITADA; touch(); }
    public void suspend() { state = State.SUSPENSA; touch(); }

    private void touch() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getLegalName() { return legalName; }
    public String getTradeName() { return tradeName; }
    public String getCnpj() { return cnpj; }
    public String getCnes() { return cnes; }
    public Type getType() { return type; }
    public UUID getParentId() { return parentId; }
    public String getCity() { return city; }
    public String getUf() { return uf; }
    public Double getLat() { return lat; }
    public Double getLng() { return lng; }
    public State getState() { return state; }
    public UUID getCreatedBy() { return createdBy; }
}
