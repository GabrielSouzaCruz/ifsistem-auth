package ifsuldeminas.edu.br.ifsystem.auth.config;

import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChaveRsaTest {
    private static KeyPair par(int bits) throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(bits);
        return g.generateKeyPair();
    }

    private static String pem(String tipo, byte[] der) {
        return "-----BEGIN " + tipo + "-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
                + "\n-----END " + tipo + "-----\n";
    }

    private static AuthProperties.Rsa rsa(KeyPair privada, KeyPair publica) {
        return new AuthProperties.Rsa(pem("PRIVATE KEY", privada.getPrivate().getEncoded()),
                pem("PUBLIC KEY", publica.getPublic().getEncoded()), true);
    }

    @Test
    void carregaPemComKidEstavelInclusiveComQuebrasEscapadas() throws Exception {
        KeyPair par = par(2048);
        AuthProperties.Rsa normal = rsa(par, par);
        RSAKey a = ChaveRsa.carregar(normal);
        RSAKey b = ChaveRsa.carregar(new AuthProperties.Rsa(normal.privateKey().replace("\n", "\\n"),
                normal.publicKey().replace("\n", "\\n"), true));
        assertThat(a.getKeyID()).isNotBlank().isEqualTo(b.getKeyID());
        assertThat(a.isPrivate()).isTrue();
    }

    @Test
    void obrigatoriaSemChaveNaoSobe() {
        assertThatThrownBy(() -> ChaveRsa.carregar(new AuthProperties.Rsa("", "", true)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("obrigatorias");
    }

    @Test
    void emDevGeraEmMemoriaMasNaoAceitaSoMetadeDoPar() throws Exception {
        assertThat(ChaveRsa.carregar(new AuthProperties.Rsa("", "", false)).isPrivate()).isTrue();
        String publica = rsa(par(2048), par(2048)).publicKey();
        assertThatThrownBy(() -> ChaveRsa.carregar(new AuthProperties.Rsa("", publica, false)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recusaParTrocadoChaveCurtaEPemInvalido() throws Exception {
        assertThatThrownBy(() -> ChaveRsa.carregar(rsa(par(2048), par(2048)))).hasMessageContaining("par");
        KeyPair curta = par(1024);
        assertThatThrownBy(() -> ChaveRsa.carregar(rsa(curta, curta))).hasMessageContaining("bits");
        assertThatThrownBy(() -> ChaveRsa.carregar(new AuthProperties.Rsa("segredo!", "lixo", true)))
                .hasMessageContaining("PKCS#8").hasMessageNotContaining("segredo!");
    }
}
