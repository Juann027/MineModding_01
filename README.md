# TupiniCraft

Mod brasileiro de sátira política para **Minecraft 1.21.1** com **NeoForge 21.1.x** (Java 21).
Origem regional (27 UFs), cargos políticos, XP político, popularidade, HUD e eleições multiplayer com urna eletrônica.

## Como jogar (instalar no seu Minecraft)

1. **Pegue o jar do mod** (`tupinicraft-0.1.0.jar`), de um destes jeitos:
   - GitHub → aba **Actions** → execução mais recente do workflow **Build** → artefato **tupinicraft-jar** (é um .zip com o jar dentro; precisa estar logado no GitHub); ou
   - compile você mesmo: `./gradlew build` (Windows: `gradlew.bat build`) → o jar sai em `build/libs/`.
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
- Configurações (eleições automáticas, durações): `world/serverconfig/tupinicraft-server.toml`.

## Comandos

| Comando | Descrição |
|---|---|
| `/brasil perfil` | Origem, cargo, XP político, popularidade e próximo cargo |
| `/brasil origem` | Sua origem regional |
| `/brasil eleicao status` | Fase, cargos em disputa, candidatos e tempo restante |
| `/brasil eleicao candidatar` | Inscreve você para o seu próximo cargo (se cumprir os requisitos) |
| `/brasil eleicao votar <jogador>` | Vota (ou use a Urna Eletrônica com o botão direito) |
| `/brasil admin eleicao iniciar <cargo> [cargo2]` | Inicia uma eleição (1 ou 2 cargos) |
| `/brasil admin eleicao avancar` / `cancelar` | Pula para a próxima fase / cancela |
| `/brasil admin xp add\|set <jogador> <qtd>` | Ajusta XP político |
| `/brasil admin popularidade add\|set <jogador> <qtd>` | Ajusta popularidade |
| `/brasil admin cargo set <jogador> <cargo>` | Define o cargo (ignora requisitos; para testes) |
| `/brasil admin origem set <jogador> <uf>` | Define a origem (ex.: `MA`) |

Cargos: `desempregado`, `vereador`, `deputado_estadual`, `prefeito`, `deputado_federal`, `senador`, `governador`, `presidente`.

## Desenvolvimento

```bash
./gradlew build       # compila e roda os testes de regras
./gradlew runClient   # cliente de desenvolvimento
./gradlew runServer   # servidor de desenvolvimento
```

Convenções técnicas em [CLAUDE.md](CLAUDE.md).
