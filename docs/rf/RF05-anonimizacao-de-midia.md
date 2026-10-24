---
rf: RF05
titulo: Anonimização automática de mídias de pacientes
status: especificado
cards: [US11]
paths:
  - apps/core-api/src/main/java/**/media/**
---

# RF05 · Anonimização automática de mídias de pacientes

## Critérios de aceite

1. Upload direto ao S3 por URL pré-assinada (15 min, tipo e tamanho na assinatura) num bucket de
   **quarentena**, sem leitura pública.
2. Imagens (JPEG/PNG/HEIC): formato detectado pelos bytes, limite de pixels **antes** de decodificar,
   orientação EXIF aplicada e **todos** os metadados removidos (EXIF, XMP, IPTC, GPS).
3. DICOM: desidentificação pelo *Basic Application Level Confidentiality Profile* (PS3.15 Anexo E), com
   novos UIDs. Exportado para PNG para exibição.
4. OCR procura texto gravado na imagem (nome, data, nº de prontuário) e cobre com tarja. A detecção de
   rosto cobre rostos com desfoque. **⚖️** A detecção de rosto não roda em imagens dermatológicas sem
   escolha explícita do autor.
5. O autor **revisa obrigatoriamente** a prévia, vê a lista do que foi removido e pode cobrir áreas a mais
   antes de aprovar.
6. Só a versão anonimizada sai da quarentena. O original é apagado em até 24 h e nunca vai para o grupo.
7. Vídeo fica **fora do MVP**.

## Máquina de estados

```mermaid
stateDiagram-v2
    [*] --> UploadPendente: URL pré-assinada emitida
    UploadPendente --> Expirado: 15 min sem upload
    UploadPendente --> Quarentena: objeto recebido
    Quarentena --> Rejeitado: formato/tamanho inválido ou malware
    Quarentena --> Processando
    Processando --> AguardandoRevisao: metadados + OCR + rostos tratados
    Processando --> Falhou: erro (original apagado)
    AguardandoRevisao --> AguardandoRevisao: autor cobre área extra
    AguardandoRevisao --> Aprovado: autor confirma
    AguardandoRevisao --> Descartado: autor desiste
    Aprovado --> [*]
    Descartado --> [*]
    Rejeitado --> [*]
```

## Classes

```mermaid
classDiagram
    class MediaAsset {
      +UUID id
      +UUID ownerId
      +MediaState state
      +String quarantineKey
      +String publishedKey
      +MediaKind kind
    }
    class MediaKind {
      <<enumeration>>
      IMAGE
      DICOM
    }
    class DeidentificationReport {
      +List~String~ removedMetadata
      +int textRegionsRedacted
      +int facesBlurred
      +List~Region~ manualRedactions
    }
    class DeidentificationPipeline {
      +run(MediaAsset) DeidentificationReport
    }
    class Step {
      <<interface>>
      +apply(Image) Image
    }
    class MetadataStripper
    class DicomDeidentifier
    class OcrRedactor
    class FaceBlurrer
    MediaAsset --> MediaKind
    MediaAsset "1" --> "0..1" DeidentificationReport
    DeidentificationPipeline o-- Step
    MetadataStripper ..|> Step
    DicomDeidentifier ..|> Step
    OcrRedactor ..|> Step
    FaceBlurrer ..|> Step
```

## Sequência

```mermaid
sequenceDiagram
    autonumber
    actor A as Autor
    participant W as Web
    participant M as core-api · MediaService
    participant S3Q as S3 quarentena
    participant DP as DeidentificationPipeline (worker)
    participant S3P as S3 publicado
    A->>W: escolhe arquivo
    W->>M: POST /media/uploads (tipo, tamanho)
    M-->>W: URL pré-assinada 15 min
    W->>S3Q: PUT arquivo
    S3Q-->>M: evento ObjectCreated
    M->>DP: processar(assetId)
    DP->>S3Q: lê em memória
    DP->>DP: valida → metadados → DICOM → OCR → rostos
    DP->>S3P: grava versão anonimizada (prévia privada)
    DP-->>M: relatório
    M-->>W: prévia + relatório
    A->>W: cobre área extra e aprova
    W->>M: POST /media/{id}/approve (regiões)
    M->>S3Q: apaga original
    M-->>W: 200 Aprovado
```

## Componentes

```mermaid
flowchart LR
    UI[Web: editor de prévia com tarja] --> MC[MediaController]
    MC --> MS[MediaService]
    MS --> S3Q[(S3 quarentena<br/>lifecycle 24 h)]
    S3Q -- evento --> Q[(Fila)]
    Q --> W[Worker de anonimização]
    W --> MS1[MetadataStripper · javax.imageio]
    W --> DD[DicomDeidentifier · dcm4che]
    W --> OCR[OcrRedactor · Tesseract por]
    W --> FB[FaceBlurrer]
    W --> S3P[(S3 publicado)]
```

> dcm4che e Tesseract são tecnologias novas em relação à stack de referência e estão registradas em
> ADR-012.

## Atividades

```mermaid
flowchart TD
    A([Arquivo escolhido]) --> B[Upload para quarentena]
    B --> C{Formato pelos bytes válido e tamanho ok?}
    C -- não --> R[Rejeita com motivo]
    C -- sim --> D{DICOM?}
    D -- sim --> E[Perfil PS3.15 Anexo E + UIDs novos] --> F
    D -- não --> F[Remove todos os metadados]
    F --> G[OCR: tarja em texto]
    G --> H{Imagem dermatológica marcada pelo autor?}
    H -- não --> I[Desfoca rostos] --> J
    H -- sim --> J[Prévia + relatório]
    J --> K[Autor revisa e cobre áreas extras]
    K --> L{Aprova?}
    L -- sim --> M[Publica versão anonimizada · apaga original]
    L -- não --> N[Descarta · apaga tudo]
```

## Rastreabilidade

| Camada | Artefato | Status |
|---|---|---|
| Serviço | `MediaService`, `DeidentificationPipeline` | ⏳ |
| Worker | etapas de anonimização | ⏳ |
| Infra | buckets quarentena/publicado, lifecycle | ⏳ |
| Testes | corpus de imagens sintéticas com EXIF/GPS, DICOM de teste e texto gravado | ⏳ |
