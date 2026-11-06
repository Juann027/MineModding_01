# TupiniCraft

Mod brasileiro de sátira política para **Minecraft 1.21.1** com **NeoForge 21.1.x** (Java 21).
Origem regional (27 UFs), cargos políticos, XP político, popularidade, HUD e eleições multiplayer com urna eletrônica.

## Como jogar (instalar no seu Minecraft)

1. **Baixe o jar do mod** na página de [Releases](https://github.com/Juann027/MineModding_01/releases/latest)
   (arquivo `tupinicraft-<versão>.jar` em *Assets*), ou compile você mesmo com `./gradlew build`
   (Windows: `gradlew.bat build`) → o jar sai em `build/libs/`.
2. **Instale o NeoForge 21.1.x para Minecraft 1.21.1**: baixe o instalador em <https://neoforged.net>, execute e escolha "Install client".
3. Abra o launcher do Minecraft uma vez com o perfil **NeoForge** (isso cria a pasta `mods`).
4. Copie o jar para a pasta `mods`:
   - Windows: `%APPDATA%\.minecraft\mods`
   - macOS: `~/Library/Application Support/minecraft/mods`
   - Linux: `~/.minecraft/mods`
5. Inicie o jogo com o perfil NeoForge.

### Multiplayer

- O **servidor** precisa de NeoForge 21.1.x (use o mesmo instalador com "Install server") e do jar em `mods/`.
- **Todos os jogadores** precisam do mesmo jar do mod no cliente.
- Comandos de administração (`/brasil admin ...`) exigem OP nível 2 (`/op <jogador>` no console).
- Configurações padrão: `config/tupinicraft-server.toml` do servidor. Administradores podem mudar valores no
  próprio mundo com `/brasil admin config set` (veja abaixo); essas mudanças ficam salvas no mundo e têm
  prioridade sobre o arquivo.

## Comandos

| Comando | Descrição |
|---|---|
| `/brasil perfil` | Origem, cargo, XP político, popularidade e próximo cargo |
| `/brasil origem` | Sua origem regional |
| `/brasil eleicao status` | Fase, cargos em disputa, candidatos e tempo restante |
| `/brasil eleicao candidatar [cargo]` | Inscreve você numa disputa do seu domicílio eleitoral (basta ter o XP político mínimo) |
| `/brasil eleicao votar <candidato>` | Vota (ou use a Urna Eletrônica com o botão direito) |
| `/brasil mandatos [território]` | Mandatos vigentes, ou todas as cadeiras de um território (ex.: `es`, `vitoria`) |
| `/brasil admin eleicao iniciar <cargo> [território] [cargo2 território2]` | Inicia uma eleição (ex.: `prefeito vitoria`) |
| `/brasil admin eleicao avancar` / `cancelar` | Pula para a próxima fase / cancela |
| `/brasil admin xp add\|set <jogador> <qtd>` | Ajusta XP político |
| `/brasil admin popularidade add\|set <jogador> <qtd>` | Ajusta popularidade |
| `/brasil admin cargo set <jogador> <cargo>` | Define o cargo (ignora requisitos; para testes) |
| `/brasil admin origem set <jogador> <uf>` | Define a origem (ex.: `MA`) |
| `/brasil admin mandato encerrar <território> <cargo>` | Encerra um mandato (cadeira fica vaga) |
| `/brasil admin config list [prefixo]` | Lista as configurações e de onde vem o valor (mundo ou arquivo) |
| `/brasil admin config get <chave>` | Mostra uma configuração |
| `/brasil admin config set <chave> <valor>` | Muda no mundo (ex.: `mandato.senador 60`, `eleicao.intervalo_gerais 45`) |
| `/brasil admin config reset <chave>` | Volta ao valor do arquivo |

Cargos: `desempregado`, `vereador`, `deputado_estadual`, `prefeito`, `deputado_federal`, `senador`, `governador`, `presidente`.

### Carreira política

- Não existe "subir um cargo de cada vez". Qualquer jogador, mesmo **Desempregado**, pode concorrer
  **direto a qualquer cargo**, até Presidente, desde que tenha o **XP político mínimo** do cargo.
- **Popularidade não é requisito.** Ela aumenta a sua pontuação na apuração e o peso do seu voto.
- Ao vencer, você assume o cargo. Quando o mandato termina, ou se você perde a cadeira, volta a ser Desempregado,
  mas mantém o XP, a popularidade e os pontos de habilidade daquele cargo. Eles voltam a valer se você reassumir o cargo.
- Cada jogador ocupa **uma cadeira por vez**. Ao vencer outra eleição, você renuncia à cadeira anterior.

### Territórios e mandatos

- Presidente governa o **Brasil**; Governador, Senador e Deputados, um **estado**; Prefeito e Vereador, um **município**
  (por enquanto, as 27 capitais).
- Cada jogador disputa no seu **domicílio eleitoral**, que vem da origem: o próprio estado e a capital dele.
- Quem vence toma posse da cadeira por um mandato (padrão 30 min, Senador 45 min). Mandatos vencidos deixam a cadeira vaga.
- Quando faltam candidatos, **aldeões** (NPCs) entram na disputa e podem ocupar cadeiras.
- Exercendo mandato, o jogador ganha **pontos de habilidade daquele cargo** pelo tempo online (padrão: 1 a cada 10 min).
- As eleições automáticas seguem dois calendários: **gerais** e **municipais**.

## Desenvolvimento

Para lançar uma nova versão: aumente `mod_version` em `gradle.properties` e faça merge na `main`;
o workflow **Release** compila e publica o jar automaticamente (se a versão ainda não tiver release).

```bash
./gradlew build       # compila e roda os testes de regras
./gradlew runClient   # cliente de desenvolvimento
./gradlew runServer   # servidor de desenvolvimento
```

Convenções técnicas em [CLAUDE.md](CLAUDE.md).
