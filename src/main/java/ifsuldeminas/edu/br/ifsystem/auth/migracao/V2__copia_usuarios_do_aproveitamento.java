package ifsuldeminas.edu.br.ifsystem.auth.migracao;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Copia public.usuarios (aproveitamento) para o schema do auth, mantendo id, cpf, senha, perfil e status.
 * Em Java, e nao em SQL, para a condicao "se a tabela existir" funcionar no PostgreSQL e no H2 dos testes
 * (o H2 nao tem blocos DO). Roda dentro da transacao do Flyway.
 */
@Component
public class V2__copia_usuarios_do_aproveitamento extends BaseJavaMigration {
    private static final Logger log = LoggerFactory.getLogger(V2__copia_usuarios_do_aproveitamento.class);
    private static final String ORIGEM = "public";
    private static final String COLUNAS = "id, nome, cpf, email, matricula, senha, telefone, curso, periodo, "
            + "perfil, status, criado_em, atualizado_em";

    @Override
    public void migrate(Context context) throws SQLException {
        int copiados = copiar(context.getConnection(), context.getConfiguration().getDefaultSchema());
        log.info("Usuarios copiados do aproveitamento: {}", copiados);
    }

    /** Idempotente: so insere quem ainda nao existe no destino. Retorna quantos foram copiados. */
    public static int copiar(Connection c, String destino) throws SQLException {
        if (!destino.matches("[A-Za-z_][A-Za-z0-9_]*"))
            throw new IllegalArgumentException("Schema invalido: " + destino);
        if (ORIGEM.equals(destino) || !existeTabelaDeOrigem(c))
            return 0;
        try (Statement st = c.createStatement()) {
            return st.executeUpdate("INSERT INTO " + destino + ".usuarios (" + COLUNAS + ") "
                    + "SELECT " + COLUNAS + " FROM " + ORIGEM + ".usuarios o "
                    + "WHERE NOT EXISTS (SELECT 1 FROM " + destino + ".usuarios d WHERE d.id = o.id "
                    + "OR d.cpf = o.cpf OR d.email = o.email OR d.matricula = o.matricula)");
        }
    }

    private static boolean existeTabelaDeOrigem(Connection c) throws SQLException {
        try (ResultSet rs = c.getMetaData().getTables(null, ORIGEM, "usuarios", null)) {
            return rs.next();
        }
    }
}
