package ifsuldeminas.edu.br.ifsystem.auth.usuario.domain;

/** CPF guardado so com os digitos; valida os digitos verificadores. */
public final class Cpf {
    private Cpf() {
    }

    public static String normalizar(String cpf) {
        return cpf == null ? "" : cpf.replaceAll("\\D", "");
    }

    public static boolean valido(String cpf) {
        String d = normalizar(cpf);
        if (d.length() != 11 || d.chars().distinct().count() == 1)
            return false;
        return digito(d, 9) == d.charAt(9) - '0' && digito(d, 10) == d.charAt(10) - '0';
    }

    private static int digito(String d, int tamanho) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++)
            soma += (d.charAt(i) - '0') * (tamanho + 1 - i);
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }
}
