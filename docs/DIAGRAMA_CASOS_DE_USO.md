# Diagrama de casos de uso (visão geral do MVP)

O Mermaid não tem notação UML de caso de uso; usamos `flowchart` com atores em retângulo e casos de uso
em elipse, agrupados por módulo. Detalhe por RF em [rf/README.md](rf/README.md).

```mermaid
flowchart LR
    V[Visitante]
    MP[Profissional pendente]
    MV[Médico verificado]
    G[Gestor hospitalar]
    MO[Moderador / Admin]
    D[Encarregado DPO]
    DT[Diretor-Técnico]
    F[Farmácia · externo]
    CFM[(CFM)]
    CNES[(CNES / Receita)]
    ICP[(Provedor ICP-Brasil)]

    subgraph Identidade["E1 · Identidade e acesso"]
      UC01([Cadastrar-se e confirmar e-mail])
      UC02([Entrar com senha e 2FA])
      UC03([Gerenciar sessões e segurança])
      UC04([Enviar CRM/RQE para verificação])
      UC05([Conferir selo manualmente])
    end
    subgraph Rede["E2 · Rede profissional"]
      UC06([Editar perfil verificado])
      UC07([Entrar em grupo da especialidade])
      UC08([Publicar caso anonimizado])
      UC09([Comentar / dar parecer])
      UC10([Revisar anonimização de mídia])
    end
    subgraph Com["E3 · Comunicação segura"]
      UC11([Conversar E2EE])
      UC12([Fazer chamada de vídeo E2EE])
    end
    subgraph Clin["E4 · Ato clínico"]
      UC13([Registrar nota SOAP no PEP])
      UC14([Emitir receita ICP-Brasil])
      UC15([Solicitar exame ICP-Brasil])
      UC16([Validar receita])
    end
    subgraph Priv["E5 · Privacidade"]
      UC17([Gerenciar consentimentos])
      UC18([Ver, corrigir e exportar meus dados])
      UC19([Excluir ou reativar conta])
      UC20([Atender pedido do titular])
    end
    subgraph Hosp["E6 · Gestão hospitalar"]
      UC21([Cadastrar instituição CNPJ/CNES])
      UC22([Gerir corpo clínico e papéis])
      UC23([Ver dashboard e auditoria])
      UC24([Moderar conteúdo])
    end

    V --> UC01
    MP --> UC02 & UC03 & UC04 & UC17 & UC18 & UC19
    MV --> UC02 & UC03 & UC06 & UC07 & UC08 & UC09 & UC10 & UC11 & UC12 & UC13 & UC14 & UC15 & UC17 & UC18 & UC19
    G --> UC02 & UC21 & UC22 & UC23
    MO --> UC05 & UC24 & UC23
    D --> UC20 & UC23
    DT --> UC23
    F --> UC16
    UC04 -.-> CFM
    UC21 -.-> CNES
    UC14 & UC15 -.-> ICP
    UC08 -. inclui .-> UC10
    UC09 -. estende .-> UC13
    UC11 -. estende .-> UC13
```
