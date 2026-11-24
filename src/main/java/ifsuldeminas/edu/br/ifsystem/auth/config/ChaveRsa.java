package ifsuldeminas.edu.br.ifsystem.auth.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Chave que assina os tokens: PEM das variaveis de ambiente ou, so em dev e testes, gerada em memoria. */
final class ChaveRsa {
    private static final Logger log = LoggerFactory.getLogger(ChaveRsa.class);
    private static final int TAMANHO = 2048;

    private ChaveRsa() {
    }

    static RSAKey carregar(AuthProperties.Rsa rsa) {
        boolean temPrivada = !rsa.privateKey().isBlank();
        boolean temPublica = !rsa.publicKey().isBlank();
        try {
            if (temPrivada && temPublica)
                return montar(publica(rsa.publicKey()), privada(rsa.privateKey()));
            if (rsa.obrigatoria() || temPrivada || temPublica)
                throw new IllegalStateException(
                        "AUTH_RSA_PRIVATE_KEY e AUTH_RSA_PUBLIC_KEY sao obrigatorias (as duas)");
            log.warn("Chave RSA gerada em memoria: os tokens deixam de valer quando a aplicacao reinicia");
            KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
            gerador.initialize(TAMANHO);
            KeyPair par = gerador.generateKeyPair();
            return montar((RSAPublicKey) par.getPublic(), (RSAPrivateKey) par.getPrivate());
        } catch (GeneralSecurityException | JOSEException | IllegalArgumentException e) {
            // Sem a causa: a mensagem original poderia carregar trechos da chave para o log.
            throw new IllegalStateException("Chave RSA invalida: use PEM PKCS#8 (BEGIN PRIVATE KEY) e "
                    + "X.509 (BEGIN PUBLIC KEY)");
        }
    }

    /** O kid e o thumbprint da chave publica: nao muda entre reinicios. */
    private static RSAKey montar(RSAPublicKey publica, RSAPrivateKey privada) throws JOSEException {
        if (!publica.getModulus().equals(privada.getModulus()))
            throw new IllegalStateException("AUTH_RSA_PRIVATE_KEY e AUTH_RSA_PUBLIC_KEY nao formam um par");
        if (publica.getModulus().bitLength() < TAMANHO)
            throw new IllegalStateException("A chave RSA deve ter ao menos " + TAMANHO + " bits");
        return new RSAKey.Builder(publica).privateKey(privada).keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256).keyIDFromThumbprint().build();
    }

    private static RSAPrivateKey privada(String pem) throws GeneralSecurityException {
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes(pem)));
    }

    private static RSAPublicKey publica(String pem) throws GeneralSecurityException {
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes(pem)));
    }

    /** Aceita PEM com quebras de linha reais ou escritas como "\n" (comum em variaveis de ambiente). */
    private static byte[] bytes(String pem) {
        return Base64.getDecoder().decode(pem.replace("\\n", "\n").replaceAll("-----[A-Z ]+-----", "")
                .replaceAll("\\s", ""));
    }
}
