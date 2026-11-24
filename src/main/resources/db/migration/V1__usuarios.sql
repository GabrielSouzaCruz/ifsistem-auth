-- Mesmo modelo de usuarios do aproveitamento-ifsistem. O schema (padrao: auth) vem do Flyway.
CREATE TABLE usuarios (
    id            uuid         NOT NULL,
    nome          varchar(255) NOT NULL,
    cpf           varchar(11)  NOT NULL,
    email         varchar(255) NOT NULL,
    matricula     varchar(255) NOT NULL,
    senha         varchar(255) NOT NULL,
    telefone      varchar(255),
    curso         varchar(255),
    periodo       integer,
    perfil        varchar(255) NOT NULL,
    status        varchar(255) NOT NULL,
    criado_em     timestamp with time zone  NOT NULL,
    atualizado_em timestamp with time zone  NOT NULL,
    CONSTRAINT pk_usuarios PRIMARY KEY (id),
    CONSTRAINT uk_usuario_cpf UNIQUE (cpf),
    CONSTRAINT uk_usuario_email UNIQUE (email),
    CONSTRAINT uk_usuario_matricula UNIQUE (matricula)
);
