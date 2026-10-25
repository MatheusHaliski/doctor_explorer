# Imagem base do core-api em produção (RF05): JRE + Tesseract (por, eng) e libheif para a anonimização de mídia.
# O Jib empacota a aplicação por cima: mvn jib:build -Djib.from.image=ghcr.io/matheushaliski/doctor-explorer/core-api-base:<tag>
# Sem estes binários o pipeline falha fechado (a mídia vai para FALHOU e nada é publicado).
FROM eclipse-temurin:21-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-por libheif-examples libheif-plugin-libde265 \
 && rm -rf /var/lib/apt/lists/*
# /dev/shm é usado para os temporários do HEIC; o usuário sem privilégio vem do Jib (jib.container.user)
