package com.doctorexplorer.clinical.signature;

import java.util.UUID;

/** Quem assina: conta, nome e CRM/UF (o certificado ICP-Brasil do médico carrega o CRM no campo próprio). */
public record SignerIdentity(UUID userId, String name, String crm, String uf) {}
