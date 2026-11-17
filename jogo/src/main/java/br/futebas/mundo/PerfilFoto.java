package br.futebas.mundo;

import java.util.function.Function;

/**
 * O "perfil" de quem não tem foto real: dados para desenhar o avatar (ui.comp.Avatar) sempre igual
 * para a mesma pessoa. Vai dentro do caminho de foto como texto ({@link #chave()}, "avatar:..."), para
 * passar por todas as telas pelo mesmo caminho que a foto real ({@code Jogo.foto} → {@code Foto.de}).
 *
 * @param semente  rosto (hash do id e do nome; nunca muda)
 * @param pais     nacionalidade (ISO3 do banco) ou null
 * @param uf       estado/região (Brasil) ou null
 * @param anoNasc  ano de nascimento (0 = desconhecido)
 * @param cor1     cor principal da camisa (clube) ou null
 * @param cor2     cor do detalhe da camisa ou null
 * @param foto     foto real preferida (relativa à raiz) ou null: se o arquivo existir, ela vence o avatar
 */
public record PerfilFoto(long semente, String pais, String uf, int anoNasc, String cor1, String cor2, String foto) {

    public static final String PREFIXO = "avatar:";

    /**
     * Cores da camisa de um time da Kings pelo id ({cor1, cor2}); registrado pelo jogo aberto. Serve aos
     * atletas da Kings que não são jogadores do mundo (só nome da fonte).
     */
    public static volatile Function<String, String[]> coresKings = id -> null;

    public static long hash(String s) {
        long h = 0xcbf29ce484222325L;
        if (s != null) for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        // mistura final (splitmix64)
        h = (h ^ (h >>> 30)) * 0xbf58476d1ce4e5b9L;
        h = (h ^ (h >>> 27)) * 0x94d049bb133111ebL;
        return h ^ (h >>> 31);
    }

    /** Perfil de um jogador do mundo (semente pelo id do banco, ou pelo id do mundo, e pelo nome). */
    public static PerfilFoto de(Jogador j, String cor1, String cor2, String foto) {
        String base = (j.dbId != null ? "db" + j.dbId : "m" + j.id) + ":" + (j.nome == null ? "" : j.nome);
        return new PerfilFoto(hash(base), j.pais, j.uf, j.anoNasc, cor1, cor2, foto);
    }

    /** Perfil de quem só tem nome (atleta da Kings fora do mundo, presidente...). */
    public static PerfilFoto deNome(String chave, String nome, String pais, int anoNasc, String cor1, String cor2) {
        return new PerfilFoto(hash("n" + (chave == null ? "" : chave) + ":" + (nome == null ? "" : nome)), pais, null, anoNasc, cor1, cor2, null);
    }

    public PerfilFoto comCores(String c1, String c2) {
        return new PerfilFoto(semente, pais, uf, anoNasc, c1, c2, foto);
    }

    public PerfilFoto comFoto(String f) {
        return new PerfilFoto(semente, pais, uf, anoNasc, cor1, cor2, f);
    }

    /** Texto que vai no lugar do caminho da foto. */
    public String chave() {
        return PREFIXO + Long.toHexString(semente) + "|" + s(pais) + "|" + s(uf) + "|" + anoNasc + "|" + s(cor1) + "|" + s(cor2) + "|" + s(foto);
    }

    public static boolean eChave(String caminho) {
        return caminho != null && caminho.startsWith(PREFIXO);
    }

    /** Lê a {@link #chave()}; null se não for uma chave de avatar. */
    public static PerfilFoto ler(String caminho) {
        if (!eChave(caminho)) return null;
        String[] p = caminho.substring(PREFIXO.length()).split("\\|", -1);
        if (p.length < 7) return null;
        try {
            return new PerfilFoto(Long.parseUnsignedLong(p[0], 16), n(p[1]), n(p[2]), Integer.parseInt(p[3]), n(p[4]), n(p[5]),
                    n(String.join("|", java.util.Arrays.copyOfRange(p, 6, p.length))));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String s(String x) {
        return x == null ? "" : x.replace('|', '/');
    }

    private static String n(String x) {
        return x == null || x.isEmpty() ? null : x;
    }
}
