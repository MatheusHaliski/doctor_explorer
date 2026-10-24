package com.doctorexplorer.core.identity.web;

import com.doctorexplorer.core.identity.domain.AccountStatus;
import java.io.Serializable;
import java.util.UUID;

/** O que fica na sessão (Valkey) sobre o usuário logado. Pequeno e serializável; nada de dado sensível. */
public record SessionPrincipal(UUID userId, String displayName, AccountStatus status) implements Serializable {}
