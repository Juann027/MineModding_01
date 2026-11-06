# TupiniCraft

Mod brasileiro de sátira política para **Minecraft 1.21.1** com **NeoForge 21.1.x** (Java 21).
Origem regional (27 UFs), cargos políticos, XP político, popularidade, HUD e eleições multiplayer com urna eletrônica.

## Como jogar (instalar no seu Minecraft)

1. **Baixe o jar do mod** na página de [Releases](https://github.com/Juann027/MineModding_01/releases/latest)
   (arquivo `tupinicraft-<versão>.jar` em *Assets*), ou compile você mesmo com `./gradlew build`
   (Windows: `gradlew.bat build`) → o jar sai em `build/libs/`.
2. **Instale o NeoForge 21.1.x para Minecraft 1.21.1**: baixe o instalador em <https://neoforged.net>, execute e escolha "Install client".
   Baixe também os 5 mods **obrigatórios**, todos para NeoForge 1.21.1 (coloque na mesma pasta `mods`).
   Estão no Modrinth (links abaixo) e também podem ser procurados pelo nome no CurseForge:

   | Mod | Versão | Para quê | Download |
   |---|---|---|---|
   | GeckoLib | 4.9.3 | animações dos mobs | <https://modrinth.com/mod/geckolib> |
   | SmartBrainLib | 1.16.11 | IA dos mobs | <https://modrinth.com/mod/smartbrainlib> |
   | SkillLib | 1.4.0 | árvores de habilidades (tecla **K**) | <https://modrinth.com/mod/skilllib> |
   | KubeJS | 2101.7.2 | exigido pelo SkillLib | <https://modrinth.com/mod/kubejs> |
   | Rhino | 2101.2.8 | exigido pelo KubeJS | <https://modrinth.com/mod/rhino> |

   Se faltar algum, o jogo mostra na tela de carregamento qual é.
3. Abra o launcher do Minecraft uma vez com o perfil **NeoForge** (isso cria a pasta `mods`).
4. Copie o jar para a pasta `mods`:
   - Windows: `%APPDATA%\.minecraft\mods`
   - macOS: `~/Library/Application Support/minecraft/mods`
   - Linux: `~/.minecraft/mods`
5. Inicie o jogo com o perfil NeoForge.

### Multiplayer

- O **servidor** precisa de NeoForge 21.1.x (use o mesmo instalador com "Install server") e dos mesmos jars em `mods/`
  (TupiniCraft e os 5 mods obrigatórios acima).
- **Todos os jogadores** precisam dos mesmos mods no cliente.
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
| `/brasil skills` | (Opcional) lista as skills no chat; o normal é usar o painel da tecla **K** |
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
| `/brasil admin teste dinheiro set\|add <jogador> <valor>` | **Teste:** muda o saldo (a economia ainda não existe) |
| `/brasil admin teste partido set <jogador> <id>` / `sair <jogador>` | **Teste:** filia/desfilia (partidos ainda não existem) |
| `/brasil admin teste pontos set <jogador> <cargo> <qtd>` | **Teste:** pontos de habilidade de um cargo |

Cargos: `desempregado`, `vereador`, `deputado_estadual`, `prefeito`, `deputado_federal`, `senador`, `governador`, `presidente`.

### Carreira política

- Não existe "subir um cargo de cada vez". Qualquer jogador, mesmo **Desempregado**, pode concorrer
  **direto a qualquer cargo**, até Presidente, desde que tenha o **XP político mínimo** do cargo.
- **Popularidade não é requisito.** Ela aumenta a sua pontuação na apuração e o peso do seu voto.
- Ao vencer, você assume o cargo. Quando o mandato termina, ou se você perde a cadeira, volta a ser Desempregado,
  mas mantém o XP, a popularidade e os pontos de habilidade daquele cargo. Eles voltam a valer se você reassumir o cargo.
- Cada jogador ocupa **uma cadeira por vez**. Ao vencer outra eleição, você renuncia à cadeira anterior.

### Árvores de habilidades (tecla K)

Aperte **K** para abrir o painel do SkillLib. Quatro árvores:
- **Pessoais:** para todos.
- **Regionais:** uma por macrorregião; vale a da sua origem.
- **Partidárias:** do partido em que você está filiado.
- **Cargos políticos:** uma por cargo.

Como funcionam:
- **Compra:** passe o mouse na skill para ver o preço e **clique nela**. Ela é comprada e já ativada; uma
  notificação no canto da tela confirma ou explica o motivo da recusa. As pessoais, regionais e partidárias custam
  **dinheiro** (reais fictícios); as de cargo custam **pontos daquele cargo**, ganhos exercendo o mandato online.
  O XP político pode ser exigido, mas nunca é gasto.
- **Saldo:** aparece no painel do canto superior direito. Cada jogador começa com **R$ 1.000** (configurável em
  `economia.saldo_inicial`).
- **Ligar/desligar:** depois de comprada, clicar de novo liga e desliga a skill, sem pagar de novo.
- **Suspensão:** se você deixar o partido, perder o mandato ou não for daquela região, a skill **fica suspensa** e volta a funcionar sozinha quando o requisito voltar. Nada do que foi comprado é perdido.

Nesta versão existem 4 skills de teste, uma por árvore. Ainda não há como ganhar dinheiro nem entrar em partidos
(isso vem com a economia e os partidos); por enquanto, além do saldo inicial, só os administradores mudam saldo e
filiação, com os comandos de teste.

### Mob de teste

O **Cabo Eleitoral (teste)** (ovo no criativo ou `/summon tupinicraft:cabo_eleitoral`) valida as bibliotecas de
animação (GeckoLib) e de IA (SmartBrainLib): passeia, olha para os jogadores e revida se for atacado.

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

O Gradle baixa todas as bibliotecas sozinho (SkillLib, KubeJS e Rhino pelo Maven do Modrinth).

Convenções técnicas em [CLAUDE.md](CLAUDE.md).
