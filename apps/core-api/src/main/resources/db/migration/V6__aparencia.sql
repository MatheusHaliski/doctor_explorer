
-- V6 · Aparência (RF01, preferências da conta): arte de fundo e intensidade. O texto da interface nunca fica
-- direto sobre a arte; fica num painel de leitura com 92 % da cor de superfície. `background_veil` é a
-- opacidade do véu sobre a arte fora do painel (20 = arte forte, 80 = arte suave). O contraste de todos os
-- tokens de texto com o véu mínimo é conferido no CI por scripts/background_contrast.py.

alter table user_preference
    add column background      text     not null default 'none',
    add column background_veil smallint not null default 45,
    add constraint user_preference_background_chk check (background in ('none', 'aurora-clara', 'neon-escuro', 'azul-noturno', 'ambar', 'neon-colorido')),
    add constraint user_preference_background_veil_chk check (background_veil between 20 and 80);
