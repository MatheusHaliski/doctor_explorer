-- Banco e usuário próprios da clinical-api (ADR-001): o usuário da core-api não enxerga o prontuário, e o da
-- clinical-api não enxerga a rede social. Só para o ambiente local; em produção são instâncias separadas.
create role clinical login password 'clinical';
create database doctor_explorer_clinical owner clinical;
revoke all on database doctor_explorer_clinical from public;
