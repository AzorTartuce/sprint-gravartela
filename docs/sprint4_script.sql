

CREATE TABLE users (
    id             NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email          VARCHAR2(200) NOT NULL UNIQUE,
    password_hash  VARCHAR2(200) NOT NULL,
    full_name      VARCHAR2(200) NOT NULL,
    company        VARCHAR2(200),
    job_title      VARCHAR2(120),
    department     VARCHAR2(120),
    phone          VARCHAR2(40),
    city           VARCHAR2(120),
    state          VARCHAR2(2),
    linkedin_url   VARCHAR2(300),
    bio            CLOB,
    created_at     TIMESTAMP NOT NULL,
    CONSTRAINT ck_users_state CHECK (state IS NULL OR REGEXP_LIKE(state, '^[A-Z]{2}$'))
);

CREATE TABLE clients (
    id             NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        NUMBER NOT NULL,
    name           VARCHAR2(200) NOT NULL,
    name_key       VARCHAR2(200) NOT NULL,
    segment        VARCHAR2(120),
    company_size   VARCHAR2(40),
    website        VARCHAR2(300),
    city           VARCHAR2(120),
    state          VARCHAR2(2),
    contact_name   VARCHAR2(200),
    contact_role   VARCHAR2(120),
    contact_email  VARCHAR2(200),
    contact_phone  VARCHAR2(40),
    owner          VARCHAR2(200),
    status         VARCHAR2(40) DEFAULT 'prospect' NOT NULL,
    notes          CLOB,
    created_at     TIMESTAMP NOT NULL,
    CONSTRAINT uq_clients_user_namekey UNIQUE (user_id, name_key),
    CONSTRAINT ck_clients_state CHECK (state IS NULL OR REGEXP_LIKE(state, '^[A-Z]{2}$')),
    CONSTRAINT ck_clients_status CHECK (status IN ('prospect', 'ativo', 'inativo'))
);

CREATE INDEX idx_clients_user_id ON clients(user_id);

CREATE TABLE meetings (
    id                   NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id            NUMBER NOT NULL,
    source_filename      VARCHAR2(500),
    created_at           TIMESTAMP NOT NULL,
    triage               CLOB,
    selected_agents_json CLOB DEFAULT '[]' NOT NULL,
    final_report_json    CLOB DEFAULT '{}' NOT NULL,
    reports_json         CLOB DEFAULT '[]' NOT NULL,
    review_json          CLOB DEFAULT '{}' NOT NULL,
    CONSTRAINT uq_meetings_client_file_date UNIQUE (client_id, source_filename, created_at)
);

CREATE INDEX idx_meetings_client_id ON meetings(client_id);

CREATE TABLE analysis_jobs (
    id               NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          NUMBER NOT NULL,
    client_id        NUMBER NOT NULL,
    source_filename  VARCHAR2(500) NOT NULL,
    input_text       CLOB,
    status           VARCHAR2(20) DEFAULT 'queued' NOT NULL,
    error_detail     CLOB,
    meeting_id       NUMBER,
    created_at       TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP NOT NULL,
    CONSTRAINT uq_analysis_jobs_meeting UNIQUE (meeting_id),
    CONSTRAINT ck_analysis_jobs_status CHECK (status IN ('queued', 'running', 'done', 'failed'))
);

CREATE INDEX idx_analysis_jobs_user_id ON analysis_jobs(user_id);
CREATE INDEX idx_analysis_jobs_client_id ON analysis_jobs(client_id);

CREATE TABLE findings (
    id                 NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id          NUMBER NOT NULL,
    origin_meeting_id  NUMBER,
    finding_type       VARCHAR2(120) NOT NULL,
    title              VARCHAR2(300) NOT NULL,
    severity_hint      VARCHAR2(40),
    status             VARCHAR2(40) DEFAULT 'awaiting_strategy' NOT NULL,
    opened_at          TIMESTAMP,
    closed_at          TIMESTAMP,
    CONSTRAINT ck_findings_severity CHECK (severity_hint IS NULL OR severity_hint IN ('low', 'medium', 'high', 'critical')),
    CONSTRAINT ck_findings_status CHECK (status IN ('awaiting_strategy', 'in_progress', 'resolved', 'dismissed', 'recurred'))
);

CREATE INDEX idx_findings_client_id ON findings(client_id);

CREATE TABLE strategies (
    id              NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    strategy_key    VARCHAR2(120) NOT NULL UNIQUE,
    label           VARCHAR2(300) NOT NULL,
    applies_to_json CLOB DEFAULT '[]' NOT NULL,
    is_builtin      NUMBER(1) DEFAULT 1 NOT NULL,
    custom          NUMBER(1) DEFAULT 0 NOT NULL,
    CONSTRAINT ck_strategies_is_builtin CHECK (is_builtin IN (0, 1)),
    CONSTRAINT ck_strategies_custom CHECK (custom IN (0, 1))
);

CREATE TABLE finding_attempts (
    id               NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    finding_id       NUMBER NOT NULL,
    strategy_key     VARCHAR2(120) NOT NULL,
    strategy_label   VARCHAR2(300),
    notes            CLOB,
    outcome          VARCHAR2(40) DEFAULT 'in_progress' NOT NULL,
    started_at       TIMESTAMP,
    outcome_at       TIMESTAMP,
    CONSTRAINT uq_finding_attempts_finding_strategy UNIQUE (finding_id, strategy_key),
    CONSTRAINT ck_finding_attempts_outcome CHECK (outcome IN ('in_progress', 'worked', 'partial', 'failed'))
);

CREATE INDEX idx_finding_attempts_finding_id ON finding_attempts(finding_id);

CREATE TABLE suggestions (
    id                      NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    finding_id              NUMBER NOT NULL,
    triggered_by_meeting_id NUMBER,
    reason                  CLOB,
    status                  VARCHAR2(40) DEFAULT 'open' NOT NULL,
    created_at              TIMESTAMP,
    CONSTRAINT ck_suggestions_status CHECK (status IN ('open', 'accepted', 'rejected', 'recurred'))
);

CREATE INDEX idx_suggestions_finding_id ON suggestions(finding_id);

ALTER TABLE clients
    ADD CONSTRAINT fk_clients_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE meetings
    ADD CONSTRAINT fk_meetings_client
    FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;

ALTER TABLE analysis_jobs
    ADD CONSTRAINT fk_analysis_jobs_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE analysis_jobs
    ADD CONSTRAINT fk_analysis_jobs_client
    FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;

ALTER TABLE analysis_jobs
    ADD CONSTRAINT fk_analysis_jobs_meeting
    FOREIGN KEY (meeting_id) REFERENCES meetings(id) ON DELETE SET NULL;

ALTER TABLE findings
    ADD CONSTRAINT fk_findings_client
    FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE;

ALTER TABLE findings
    ADD CONSTRAINT fk_findings_meeting
    FOREIGN KEY (origin_meeting_id) REFERENCES meetings(id) ON DELETE SET NULL;

ALTER TABLE finding_attempts
    ADD CONSTRAINT fk_finding_attempts_finding
    FOREIGN KEY (finding_id) REFERENCES findings(id) ON DELETE CASCADE;

ALTER TABLE suggestions
    ADD CONSTRAINT fk_suggestions_finding
    FOREIGN KEY (finding_id) REFERENCES findings(id) ON DELETE CASCADE;

ALTER TABLE suggestions
    ADD CONSTRAINT fk_suggestions_meeting
    FOREIGN KEY (triggered_by_meeting_id) REFERENCES meetings(id) ON DELETE SET NULL;

INSERT INTO strategies (strategy_key, label, applies_to_json, is_builtin, custom) VALUES
    ('follow_up_call', 'Ligação de acompanhamento', '["*"]', 1, 0);
INSERT INTO strategies (strategy_key, label, applies_to_json, is_builtin, custom) VALUES
    ('escalate_technical', 'Escalar para time técnico', '["technical_issue","integration_gap"]', 1, 0);
INSERT INTO strategies (strategy_key, label, applies_to_json, is_builtin, custom) VALUES
    ('pricing_review', 'Revisão comercial/pricing', '["pricing_concern","churn_risk"]', 1, 0);
INSERT INTO strategies (strategy_key, label, applies_to_json, is_builtin, custom) VALUES
    ('training_session', 'Sessão de treinamento', '["adoption_gap","usability_issue"]', 1, 0);
INSERT INTO strategies (strategy_key, label, applies_to_json, is_builtin, custom) VALUES
    ('contract_renegotiation', 'Renegociação de contrato', '["churn_risk","pricing_concern"]', 1, 0);

COMMIT;

INSERT INTO users (email, password_hash, full_name, company, job_title, department, phone, city, state, linkedin_url, bio, created_at) VALUES
    ('ana.souza@consultoria.com', '$2y$10$ZyF1e8Ft9LSDKoa0dWF7/uLMXB9B9YL8ovpFj530qnCTfwPp6ye/O', 'Ana Souza', 'Consultoria Prisma', 'Consultora Sênior', 'Customer Success', '(11) 98888-1111', 'São Paulo', 'SP', 'linkedin.com/in/anasouza', 'Especialista em retenção de clientes B2B.', TIMESTAMP '2025-01-10 09:00:00');
INSERT INTO users (email, password_hash, full_name, company, job_title, department, phone, city, state, linkedin_url, bio, created_at) VALUES
    ('bruno.lima@consultoria.com', '$2y$10$ZyF1e8Ft9LSDKoa0dWF7/uLMXB9B9YL8ovpFj530qnCTfwPp6ye/O', 'Bruno Lima', 'Consultoria Prisma', 'Analista de Dados', 'Analytics', '(21) 97777-2222', 'Rio de Janeiro', 'RJ', 'linkedin.com/in/brunolima', 'Focado em análise de reuniões e mineração de insights.', TIMESTAMP '2025-01-15 10:30:00');
INSERT INTO users (email, password_hash, full_name, company, job_title, department, phone, city, state, linkedin_url, bio, created_at) VALUES
    ('carla.mendes@consultoria.com', '$2y$10$ZyF1e8Ft9LSDKoa0dWF7/uLMXB9B9YL8ovpFj530qnCTfwPp6ye/O', 'Carla Mendes', 'Consultoria Prisma', 'Gerente de Contas', 'Comercial', '(31) 96666-3333', 'Belo Horizonte', 'MG', 'linkedin.com/in/carlamendes', 'Gestão de carteira de clientes de médio porte.', TIMESTAMP '2025-02-01 08:15:00');
INSERT INTO users (email, password_hash, full_name, company, job_title, department, phone, city, state, linkedin_url, bio, created_at) VALUES
    ('diego.alves@consultoria.com', '$2y$10$ZyF1e8Ft9LSDKoa0dWF7/uLMXB9B9YL8ovpFj530qnCTfwPp6ye/O', 'Diego Alves', 'Consultoria Prisma', 'Consultor Técnico', 'Suporte Técnico', '(41) 95555-4444', 'Curitiba', 'PR', 'linkedin.com/in/diegoalves', 'Especialista em integrações e suporte técnico avançado.', TIMESTAMP '2025-02-10 14:00:00');
INSERT INTO users (email, password_hash, full_name, company, job_title, department, phone, city, state, linkedin_url, bio, created_at) VALUES
    ('elisa.rocha@consultoria.com', '$2y$10$ZyF1e8Ft9LSDKoa0dWF7/uLMXB9B9YL8ovpFj530qnCTfwPp6ye/O', 'Elisa Rocha', 'Consultoria Prisma', 'Coordenadora de CS', 'Customer Success', '(51) 94444-5555', 'Porto Alegre', 'RS', 'linkedin.com/in/elisarocha', 'Coordenação de squads de sucesso do cliente.', TIMESTAMP '2025-02-20 11:45:00');

INSERT INTO clients (user_id, name, name_key, segment, company_size, website, city, state, contact_name, contact_role, contact_email, contact_phone, owner, status, notes, created_at) VALUES
    (1, 'TechNova Sistemas', 'technova_sistemas', 'Tecnologia', 'Médio', 'technova.com.br', 'São Paulo', 'SP', 'Marcos Reis', 'CTO', 'marcos.reis@technova.com.br', '(11) 91111-0001', 'Ana Souza', 'ativo', 'Cliente estratégico, contrato anual.', TIMESTAMP '2025-01-12 09:00:00');
INSERT INTO clients (user_id, name, name_key, segment, company_size, website, city, state, contact_name, contact_role, contact_email, contact_phone, owner, status, notes, created_at) VALUES
    (1, 'Vidalux Cosméticos', 'vidalux_cosmeticos', 'Varejo', 'Pequeno', 'vidalux.com.br', 'Campinas', 'SP', 'Fernanda Dias', 'Gerente Comercial', 'fernanda.dias@vidalux.com.br', '(19) 91111-0002', 'Ana Souza', 'prospect', 'Em negociação inicial.', TIMESTAMP '2025-01-20 10:00:00');
INSERT INTO clients (user_id, name, name_key, segment, company_size, website, city, state, contact_name, contact_role, contact_email, contact_phone, owner, status, notes, created_at) VALUES
    (2, 'Grupo Fortaleza Log', 'grupo_fortaleza_log', 'Logística', 'Grande', 'fortalezalog.com.br', 'Rio de Janeiro', 'RJ', 'Paulo Nunes', 'Diretor de Operações', 'paulo.nunes@fortalezalog.com.br', '(21) 91111-0003', 'Bruno Lima', 'ativo', 'Expansão de contrato prevista para o próximo trimestre.', TIMESTAMP '2025-02-01 09:30:00');
INSERT INTO clients (user_id, name, name_key, segment, company_size, website, city, state, contact_name, contact_role, contact_email, contact_phone, owner, status, notes, created_at) VALUES
    (3, 'Minas Agro Insumos', 'minas_agro_insumos', 'Agronegócio', 'Médio', 'minasagro.com.br', 'Belo Horizonte', 'MG', 'Juliana Prado', 'Gerente de TI', 'juliana.prado@minasagro.com.br', '(31) 91111-0004', 'Carla Mendes', 'ativo', 'Boa relação, poucas ocorrências.', TIMESTAMP '2025-02-05 13:00:00');
INSERT INTO clients (user_id, name, name_key, segment, company_size, website, city, state, contact_name, contact_role, contact_email, contact_phone, owner, status, notes, created_at) VALUES
    (3, 'Ouro Preto Financeira', 'ouro_preto_financeira', 'Financeiro', 'Grande', 'opfinanceira.com.br', 'Belo Horizonte', 'MG', 'Rafael Costa', 'Head de Produto', 'rafael.costa@opfinanceira.com.br', '(31) 91111-0005', 'Carla Mendes', 'inativo', 'Contrato suspenso temporariamente.', TIMESTAMP '2025-02-08 15:20:00');
INSERT INTO clients (user_id, name, name_key, segment, company_size, website, city, state, contact_name, contact_role, contact_email, contact_phone, owner, status, notes, created_at) VALUES
    (4, 'Curitiba Health Tech', 'curitiba_health_tech', 'Saúde', 'Pequeno', 'curitibahealth.com.br', 'Curitiba', 'PR', 'Camila Fontes', 'CEO', 'camila.fontes@curitibahealth.com.br', '(41) 91111-0006', 'Diego Alves', 'ativo', 'Integração técnica em andamento.', TIMESTAMP '2025-02-12 08:45:00');
INSERT INTO clients (user_id, name, name_key, segment, company_size, website, city, state, contact_name, contact_role, contact_email, contact_phone, owner, status, notes, created_at) VALUES
    (5, 'Serra Gaúcha Alimentos', 'serra_gaucha_alimentos', 'Alimentício', 'Médio', 'serragaucha.com.br', 'Porto Alegre', 'RS', 'Eduardo Vieira', 'Gerente Comercial', 'eduardo.vieira@serragaucha.com.br', '(51) 91111-0007', 'Elisa Rocha', 'ativo', 'Cliente satisfeito, potencial de upsell.', TIMESTAMP '2025-02-18 09:10:00');
INSERT INTO clients (user_id, name, name_key, segment, company_size, website, city, state, contact_name, contact_role, contact_email, contact_phone, owner, status, notes, created_at) VALUES
    (5, 'Pampa Educação Digital', 'pampa_educacao_digital', 'Educação', 'Pequeno', 'pampaedu.com.br', 'Porto Alegre', 'RS', 'Beatriz Lopes', 'Coordenadora Pedagógica', 'beatriz.lopes@pampaedu.com.br', '(51) 91111-0008', 'Elisa Rocha', 'inativo', 'Contrato encerrado por corte de orçamento.', TIMESTAMP '2025-02-22 16:00:00');

INSERT INTO meetings (client_id, source_filename, created_at, triage, selected_agents_json, final_report_json, reports_json, review_json) VALUES
    (1, 'technova_reuniao_kickoff.txt', TIMESTAMP '2025-01-13 10:00:00', 'Reunião de kickoff, expectativas alinhadas.', '["triage_agent","tech_agent"]', '{"resumo":"Kickoff concluido com sucesso"}', '[]', '{}');
INSERT INTO meetings (client_id, source_filename, created_at, triage, selected_agents_json, final_report_json, reports_json, review_json) VALUES
    (1, 'technova_reuniao_revisao.txt', TIMESTAMP '2025-02-03 11:00:00', 'Cliente relatou lentidão na integração via API.', '["triage_agent","tech_agent"]', '{"resumo":"Identificado gargalo tecnico"}', '[]', '{}');
INSERT INTO meetings (client_id, source_filename, created_at, triage, selected_agents_json, final_report_json, reports_json, review_json) VALUES
    (2, 'vidalux_primeira_reuniao.txt', TIMESTAMP '2025-01-21 09:30:00', 'Cliente em fase de avaliação de proposta comercial.', '["triage_agent","sales_agent"]', '{"resumo":"Proposta em analise"}', '[]', '{}');
INSERT INTO meetings (client_id, source_filename, created_at, triage, selected_agents_json, final_report_json, reports_json, review_json) VALUES
    (3, 'fortaleza_log_reuniao_mensal.txt', TIMESTAMP '2025-02-02 14:00:00', 'Discussão sobre expansão de escopo do contrato.', '["triage_agent","sales_agent"]', '{"resumo":"Interesse em expansao confirmado"}', '[]', '{}');
INSERT INTO meetings (client_id, source_filename, created_at, triage, selected_agents_json, final_report_json, reports_json, review_json) VALUES
    (4, 'minas_agro_reuniao_status.txt', TIMESTAMP '2025-02-06 10:15:00', 'Status geral positivo, sem pendências críticas.', '["triage_agent"]', '{"resumo":"Sem riscos identificados"}', '[]', '{}');
INSERT INTO meetings (client_id, source_filename, created_at, triage, selected_agents_json, final_report_json, reports_json, review_json) VALUES
    (5, 'ouro_preto_reuniao_reclamacao.txt', TIMESTAMP '2025-02-09 16:00:00', 'Cliente reclamou do preço do plano atual.', '["triage_agent","sales_agent"]', '{"resumo":"Risco de churn por precificacao"}', '[]', '{}');
INSERT INTO meetings (client_id, source_filename, created_at, triage, selected_agents_json, final_report_json, reports_json, review_json) VALUES
    (6, 'curitiba_health_reuniao_integracao.txt', TIMESTAMP '2025-02-13 09:00:00', 'Dificuldades na integração com sistema legado.', '["triage_agent","tech_agent"]', '{"resumo":"Gap de integracao identificado"}', '[]', '{}');
INSERT INTO meetings (client_id, source_filename, created_at, triage, selected_agents_json, final_report_json, reports_json, review_json) VALUES
    (7, 'serra_gaucha_reuniao_upsell.txt', TIMESTAMP '2025-02-19 10:30:00', 'Cliente demonstrou interesse em módulo adicional.', '["triage_agent","sales_agent"]', '{"resumo":"Oportunidade de upsell"}', '[]', '{}');
INSERT INTO meetings (client_id, source_filename, created_at, triage, selected_agents_json, final_report_json, reports_json, review_json) VALUES
    (8, 'pampa_edu_reuniao_encerramento.txt', TIMESTAMP '2025-02-23 09:00:00', 'Cliente comunicou encerramento por corte orçamentário.', '["triage_agent","sales_agent"]', '{"resumo":"Encerramento de contrato"}', '[]', '{}');
INSERT INTO meetings (client_id, source_filename, created_at, triage, selected_agents_json, final_report_json, reports_json, review_json) VALUES
    (3, 'fortaleza_log_reuniao_treinamento.txt', TIMESTAMP '2025-02-25 13:30:00', 'Equipe do cliente relatou dificuldade de uso da plataforma.', '["triage_agent","cs_agent"]', '{"resumo":"Necessidade de treinamento"}', '[]', '{}');

INSERT INTO analysis_jobs (user_id, client_id, source_filename, input_text, status, error_detail, meeting_id, created_at, updated_at) VALUES
    (1, 1, 'technova_reuniao_kickoff.txt', 'Transcricao da reuniao de kickoff...', 'done', NULL, 1, TIMESTAMP '2025-01-13 10:05:00', TIMESTAMP '2025-01-13 10:20:00');
INSERT INTO analysis_jobs (user_id, client_id, source_filename, input_text, status, error_detail, meeting_id, created_at, updated_at) VALUES
    (1, 1, 'technova_reuniao_revisao.txt', 'Transcricao da reuniao de revisao...', 'done', NULL, 2, TIMESTAMP '2025-02-03 11:05:00', TIMESTAMP '2025-02-03 11:25:00');
INSERT INTO analysis_jobs (user_id, client_id, source_filename, input_text, status, error_detail, meeting_id, created_at, updated_at) VALUES
    (1, 2, 'vidalux_primeira_reuniao.txt', 'Transcricao da primeira reuniao...', 'done', NULL, 3, TIMESTAMP '2025-01-21 09:35:00', TIMESTAMP '2025-01-21 09:50:00');
INSERT INTO analysis_jobs (user_id, client_id, source_filename, input_text, status, error_detail, meeting_id, created_at, updated_at) VALUES
    (2, 3, 'fortaleza_log_reuniao_mensal.txt', 'Transcricao da reuniao mensal...', 'done', NULL, 4, TIMESTAMP '2025-02-02 14:05:00', TIMESTAMP '2025-02-02 14:30:00');
INSERT INTO analysis_jobs (user_id, client_id, source_filename, input_text, status, error_detail, meeting_id, created_at, updated_at) VALUES
    (3, 5, 'ouro_preto_reuniao_reclamacao.txt', 'Transcricao da reclamacao...', 'done', NULL, 6, TIMESTAMP '2025-02-09 16:05:00', TIMESTAMP '2025-02-09 16:30:00');
INSERT INTO analysis_jobs (user_id, client_id, source_filename, input_text, status, error_detail, meeting_id, created_at, updated_at) VALUES
    (4, 6, 'curitiba_health_reuniao_integracao.txt', 'Transcricao da reuniao de integracao...', 'running', NULL, NULL, TIMESTAMP '2025-02-13 09:05:00', TIMESTAMP '2025-02-13 09:10:00');
INSERT INTO analysis_jobs (user_id, client_id, source_filename, input_text, status, error_detail, meeting_id, created_at, updated_at) VALUES
    (5, 7, 'serra_gaucha_reuniao_upsell.txt', 'Transcricao da reuniao de upsell...', 'queued', NULL, NULL, TIMESTAMP '2025-02-19 10:35:00', TIMESTAMP '2025-02-19 10:35:00');
INSERT INTO analysis_jobs (user_id, client_id, source_filename, input_text, status, error_detail, meeting_id, created_at, updated_at) VALUES
    (5, 8, 'pampa_edu_arquivo_corrompido.txt', 'Arquivo de audio corrompido, sem transcricao legivel.', 'failed', 'Erro ao processar arquivo: formato invalido.', NULL, TIMESTAMP '2025-02-23 09:05:00', TIMESTAMP '2025-02-23 09:07:00');

INSERT INTO findings (client_id, origin_meeting_id, finding_type, title, severity_hint, status, opened_at, closed_at) VALUES
    (1, 2, 'technical_issue', 'Lentidão na integração via API', 'high', 'awaiting_strategy', TIMESTAMP '2025-02-03 11:30:00', NULL);
INSERT INTO findings (client_id, origin_meeting_id, finding_type, title, severity_hint, status, opened_at, closed_at) VALUES
    (3, 4, 'expansion_opportunity', 'Interesse em expandir escopo do contrato', 'medium', 'awaiting_strategy', TIMESTAMP '2025-02-02 14:35:00', NULL);
INSERT INTO findings (client_id, origin_meeting_id, finding_type, title, severity_hint, status, opened_at, closed_at) VALUES
    (5, 6, 'pricing_concern', 'Cliente insatisfeito com o valor do plano', 'critical', 'awaiting_strategy', TIMESTAMP '2025-02-09 16:35:00', NULL);
INSERT INTO findings (client_id, origin_meeting_id, finding_type, title, severity_hint, status, opened_at, closed_at) VALUES
    (6, 7, 'integration_gap', 'Falha de integração com sistema legado', 'high', 'awaiting_strategy', TIMESTAMP '2025-02-13 09:20:00', NULL);
INSERT INTO findings (client_id, origin_meeting_id, finding_type, title, severity_hint, status, opened_at, closed_at) VALUES
    (7, 8, 'upsell_opportunity', 'Interesse em módulo adicional de relatórios', 'low', 'awaiting_strategy', TIMESTAMP '2025-02-19 10:40:00', NULL);
INSERT INTO findings (client_id, origin_meeting_id, finding_type, title, severity_hint, status, opened_at, closed_at) VALUES
    (8, 9, 'churn_risk', 'Encerramento de contrato por corte orçamentário', 'critical', 'awaiting_strategy', TIMESTAMP '2025-02-23 09:20:00', NULL);
INSERT INTO findings (client_id, origin_meeting_id, finding_type, title, severity_hint, status, opened_at, closed_at) VALUES
    (3, 10, 'usability_issue', 'Equipe do cliente com dificuldade de uso da plataforma', 'medium', 'awaiting_strategy', TIMESTAMP '2025-02-25 13:40:00', NULL);
INSERT INTO findings (client_id, origin_meeting_id, finding_type, title, severity_hint, status, opened_at, closed_at) VALUES
    (1, 1, 'adoption_gap', 'Baixa adoção de funcionalidades avançadas pela equipe', 'low', 'awaiting_strategy', TIMESTAMP '2025-01-13 10:25:00', NULL);

INSERT INTO finding_attempts (finding_id, strategy_key, strategy_label, notes, outcome, started_at, outcome_at) VALUES
    (1, 'escalate_technical', 'Escalar para time técnico', 'Chamado aberto com o time de engenharia.', 'in_progress', TIMESTAMP '2025-02-04 09:00:00', NULL);
INSERT INTO finding_attempts (finding_id, strategy_key, strategy_label, notes, outcome, started_at, outcome_at) VALUES
    (3, 'pricing_review', 'Revisão comercial/pricing', 'Proposta de desconto em análise pelo comercial.', 'in_progress', TIMESTAMP '2025-02-10 10:00:00', NULL);
INSERT INTO finding_attempts (finding_id, strategy_key, strategy_label, notes, outcome, started_at, outcome_at) VALUES
    (4, 'escalate_technical', 'Escalar para time técnico', 'Chamado aberto para revisão da integração.', 'in_progress', TIMESTAMP '2025-02-14 09:00:00', NULL);
INSERT INTO finding_attempts (finding_id, strategy_key, strategy_label, notes, outcome, started_at, outcome_at) VALUES
    (6, 'contract_renegotiation', 'Renegociação de contrato', 'Reunião de renegociação agendada.', 'in_progress', TIMESTAMP '2025-02-24 09:00:00', NULL);
INSERT INTO finding_attempts (finding_id, strategy_key, strategy_label, notes, outcome, started_at, outcome_at) VALUES
    (7, 'training_session', 'Sessão de treinamento', 'Treinamento online realizado com a equipe do cliente.', 'worked', TIMESTAMP '2025-02-26 09:00:00', TIMESTAMP '2025-02-27 10:00:00');
INSERT INTO finding_attempts (finding_id, strategy_key, strategy_label, notes, outcome, started_at, outcome_at) VALUES
    (8, 'follow_up_call', 'Ligação de acompanhamento', 'Ligação realizada, cliente ainda não usa os recursos avançados.', 'failed', TIMESTAMP '2025-01-14 09:00:00', TIMESTAMP '2025-01-20 09:00:00');

INSERT INTO suggestions (finding_id, triggered_by_meeting_id, reason, status, created_at) VALUES
    (1, 2, 'Padrão recorrente de reclamação sobre performance de API em outras contas do mesmo segmento.', 'open', TIMESTAMP '2025-02-03 11:35:00');
INSERT INTO suggestions (finding_id, triggered_by_meeting_id, reason, status, created_at) VALUES
    (2, 4, 'Cliente com histórico de pagamento em dia e alta satisfação, bom candidato a expansão.', 'accepted', TIMESTAMP '2025-02-02 14:40:00');
INSERT INTO suggestions (finding_id, triggered_by_meeting_id, reason, status, created_at) VALUES
    (3, 6, 'Risco de cancelamento identificado por menção direta a concorrente mais barato.', 'accepted', TIMESTAMP '2025-02-09 16:40:00');
INSERT INTO suggestions (finding_id, triggered_by_meeting_id, reason, status, created_at) VALUES
    (5, 8, 'Cliente com uso intenso da plataforma, propenso a adquirir módulo de relatórios.', 'open', TIMESTAMP '2025-02-19 10:45:00');
INSERT INTO suggestions (finding_id, triggered_by_meeting_id, reason, status, created_at) VALUES
    (6, 9, 'Sugestão automática de retenção não se aplica: cliente já formalizou o encerramento.', 'rejected', TIMESTAMP '2025-02-23 09:25:00');
INSERT INTO suggestions (finding_id, triggered_by_meeting_id, reason, status, created_at) VALUES
    (7, 10, 'Baixa utilização de funcionalidades avançadas indica necessidade de treinamento direcionado.', 'accepted', TIMESTAMP '2025-02-25 13:45:00');

COMMIT;

UPDATE meetings
   SET final_report_json = '{"resumo":"Integracao com sistema legado mapeada"}'
 WHERE id = 7;

UPDATE analysis_jobs
   SET status = 'done',
       meeting_id = 7,
       updated_at = TIMESTAMP '2025-02-13 09:45:00'
 WHERE id = 6
   AND status = 'running';

UPDATE analysis_jobs
   SET status = 'running',
       updated_at = TIMESTAMP '2025-02-19 11:00:00'
 WHERE id = 7
   AND status = 'queued';

UPDATE findings
   SET status = 'in_progress'
 WHERE id IN (1, 3, 4, 6)
   AND status = 'awaiting_strategy';

UPDATE findings
   SET status = 'resolved',
       closed_at = TIMESTAMP '2025-02-27 10:05:00'
 WHERE id = 7;

UPDATE finding_attempts
   SET outcome = 'worked',
       outcome_at = TIMESTAMP '2025-02-15 12:00:00'
 WHERE finding_id = 4
   AND strategy_key = 'escalate_technical';

UPDATE findings
   SET status = 'resolved',
       closed_at = TIMESTAMP '2025-02-15 12:05:00'
 WHERE id = 4;

UPDATE findings
   SET status = 'dismissed',
       closed_at = TIMESTAMP '2025-02-24 09:00:00'
 WHERE id = 6;

UPDATE finding_attempts
   SET outcome = 'failed',
       outcome_at = TIMESTAMP '2025-02-24 09:00:00'
 WHERE finding_id = 6
   AND strategy_key = 'contract_renegotiation';

UPDATE clients
   SET status = 'ativo',
       notes  = 'Falha de integracao resolvida em 15/02/2025.'
 WHERE id = 6;

DELETE FROM suggestions
 WHERE finding_id = 6
   AND status = 'rejected';

DELETE FROM analysis_jobs
 WHERE id = 8
   AND status = 'failed';

COMMIT;

-- CONSULTA 1
SELECT f.id AS id_achado,
       c.name AS cliente,
       f.title AS achado,
       f.status AS status_achado,
       ROUND(
           CAST(COALESCE(f.closed_at, CAST(CURRENT_TIMESTAMP AS TIMESTAMP)) AS DATE)
           - CAST(f.opened_at AS DATE),
           2
       ) AS dias_decorridos
  FROM findings f
  INNER JOIN clients c
          ON c.id = f.client_id
 WHERE f.opened_at IS NOT NULL
 ORDER BY dias_decorridos DESC;

-- CONSULTA 2
SELECT c.id AS id_cliente,
       c.name AS cliente,
       u.full_name AS consultor_responsavel,
       c.segment AS segmento,
       c.status AS status_cliente
  FROM clients c
  INNER JOIN users u
          ON u.id = c.user_id
 ORDER BY u.full_name, c.name;

-- CONSULTA 3
SELECT c.id AS id_cliente,
       c.name AS cliente,
       c.status AS status_cliente,
       COUNT(f.id) AS total_achados
  FROM clients c
  INNER JOIN findings f
          ON f.client_id = c.id
 GROUP BY c.id, c.name, c.status
 ORDER BY total_achados DESC, c.name;

-- CONSULTA 4
SELECT c.id AS id_cliente,
       c.name AS cliente,
       c.status AS status_cliente,
       m.id AS id_reuniao,
       m.source_filename AS arquivo_reuniao,
       m.created_at AS data_reuniao
  FROM clients c
  LEFT OUTER JOIN meetings m
               ON m.client_id = c.id
 WHERE c.status = 'ativo'
 ORDER BY c.name ASC, m.created_at DESC;

-- CONSULTA 5
SELECT c.id AS id_cliente,
       c.name AS cliente,
       COUNT(f.id) AS total_achados
  FROM clients c
  INNER JOIN findings f
          ON f.client_id = c.id
 GROUP BY c.id, c.name
HAVING COUNT(f.id) >= 2
 ORDER BY total_achados DESC, c.name;

-- CONSULTA 6
SELECT COALESCE(severity_hint, 'nao_informada') AS severidade,
       COUNT(*) AS total_achados
  FROM findings
 GROUP BY COALESCE(severity_hint, 'nao_informada')
 ORDER BY total_achados DESC, severidade;

-- CONSULTA 7
SELECT status AS status_job,
       COUNT(*) AS total_jobs
  FROM analysis_jobs
 GROUP BY status
 ORDER BY total_jobs DESC, status;

-- CONSULTA 8
SELECT fa.strategy_key,
       COALESCE(fa.strategy_label, s.label) AS estrategia,
       fa.outcome AS resultado_tentativa,
       COUNT(*) AS total_tentativas
  FROM finding_attempts fa
  LEFT OUTER JOIN strategies s
               ON s.strategy_key = fa.strategy_key
 GROUP BY fa.strategy_key,
          COALESCE(fa.strategy_label, s.label),
          fa.outcome
 ORDER BY fa.strategy_key, fa.outcome;
