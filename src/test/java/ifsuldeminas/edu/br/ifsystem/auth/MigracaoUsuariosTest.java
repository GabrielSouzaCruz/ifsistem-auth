package ifsuldeminas.edu.br.ifsystem.auth;

import ifsuldeminas.edu.br.ifsystem.auth.migracao.V2__copia_usuarios_do_aproveitamento;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** V1 a V3 no H2 em modo PostgreSQL, com a tabela public.usuarios do aproveitamento presente ou nao. */
class MigracaoUsuariosTest {
    private static final UUID ANA = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID BIA = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
    private static final UUID CAIO = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000003");

    private String url;
    private Connection conexao;

    @BeforeEach
    void abrir() throws Exception {
        url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        conexao = DriverManager.getConnection(url, "sa", "");
    }

    @AfterEach
    void fechar() throws Exception {
        executar("shutdown");
    }

    private void executar(String sql) throws Exception {
        try (Statement st = conexao.createStatement()) {
            st.execute(sql);
        }
    }

    private String consultar(String sql) throws Exception {
        try (Statement st = conexao.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    /** Mesmo DDL da V1, mas em public (igual ao do aproveitamento). */
    private void criarOrigem() throws Exception {
        executar(Files.readString(Path.of("src/main/resources/db/migration/V1__usuarios.sql"))
                .replace("CREATE TABLE usuarios", "CREATE TABLE public.usuarios"));
    }

    private void inserir(UUID id, String cpf, String perfil, String status) throws Exception {
        executar("insert into public.usuarios values ('" + id + "', 'Nome " + cpf + "', '" + cpf + "', '" + cpf
                + "@if.br', 'M" + cpf + "', '$2a$hash" + cpf + "', null, 'ADS', 3, '" + perfil + "', '" + status
                + "', current_timestamp, current_timestamp)");
    }

    private int migrar() {
        return Flyway.configure().dataSource(url, "sa", "").schemas("auth").defaultSchema("auth")
                .javaMigrations(new V2__copia_usuarios_do_aproveitamento())
                .load().migrate().migrationsExecuted;
    }

    @Test
    void semATabelaDeOrigemMigraSemCopiarNada() throws Exception {
        assertThat(migrar()).isEqualTo(3);
        assertThat(consultar("select count(*) from auth.usuarios")).isEqualTo("0");
        assertThat(consultar("select count(*) from auth.oauth2_authorization")).isEqualTo("0");
        assertThat(consultar("select count(*) from auth.oauth2_authorization_consent")).isEqualTo("0");
    }

    @Test
    void copiaMantendoIdCpfSenhaPerfilEStatus() throws Exception {
        criarOrigem();
        inserir(ANA, "52998224725", "ALUNO", "ATIVO");
        inserir(BIA, "11144477735", "SERVIDOR", "INATIVO");
        migrar();
        assertThat(consultar("select count(*) from auth.usuarios")).isEqualTo("2");
        assertThat(consultar("select id || '|' || cpf || '|' || senha || '|' || perfil || '|' || status "
                + "from auth.usuarios where id = '" + BIA + "'"))
                .isEqualTo(BIA + "|11144477735|$2a$hash11144477735|SERVIDOR|INATIVO");
    }

    @Test
    void rodarDeNovoEIdempotenteESoCopiaOsNovos() throws Exception {
        criarOrigem();
        inserir(ANA, "52998224725", "ALUNO", "ATIVO");
        migrar();
        assertThat(migrar()).isZero(); // o Flyway nao repete

        assertThat(V2__copia_usuarios_do_aproveitamento.copiar(conexao, "auth")).isZero();

        // Mudancas feitas no auth nao sao sobrescritas; so o usuario novo entra.
        executar("update auth.usuarios set status = 'INATIVO' where id = '" + ANA + "'");
        inserir(BIA, "11144477735", "PROFESSOR", "ATIVO");
        assertThat(V2__copia_usuarios_do_aproveitamento.copiar(conexao, "auth")).isEqualTo(1);
        assertThat(V2__copia_usuarios_do_aproveitamento.copiar(conexao, "auth")).isZero();
        assertThat(consultar("select status from auth.usuarios where id = '" + ANA + "'")).isEqualTo("INATIVO");
        assertThat(consultar("select count(*) from public.usuarios")).isEqualTo("2");
    }

    @Test
    void mesmoCpfComOutroIdNaoQuebraACopia() throws Exception {
        criarOrigem();
        migrar();
        inserir(ANA, "52998224725", "ALUNO", "ATIVO");
        V2__copia_usuarios_do_aproveitamento.copiar(conexao, "auth");
        executar("delete from public.usuarios");
        inserir(CAIO, "52998224725", "ALUNO", "ATIVO");
        assertThat(V2__copia_usuarios_do_aproveitamento.copiar(conexao, "auth")).isZero();
        assertThat(consultar("select id from auth.usuarios")).isEqualTo(ANA.toString());
    }
}
