# AGENTS.md — RespectCraft

As regras completas estão no [`CLAUDE.md`](CLAUDE.md); este arquivo é o resumo. Em caso de dúvida, vale o `CLAUDE.md`.

- **Marca:** **RespectCraft** em tudo (Etapa 4.7.1): mod id e namespace `respectcraft`, pacote `com.respectcraft`,
  comando único `/respectcraft` (ramos `anim`, `test` e `realm`), configs `respectcraft-*-server.toml`. O nome antigo só fica em
  handoffs históricos; não o reintroduza nem como alias. Mundos anteriores à 4.7.1 não são compatíveis.
- **Stack:** Minecraft 1.21.1, NeoForge 21.1.x (só APIs 21.1), Java 21, GeckoLib 4.9.3, SmartBrainLib 1.16.11,
  Rimulib 1.3.0 (Etapa 4.8; CurseMaven, não embutido; `mc1211.client.api` só em `client`).
  Sem UI Lib/Architectury e sem dependências novas sem pedido.
- **Comece por** `README.md`, `CLAUDE.md` e só os arquivos do pedido. Não faça auditorias gerais. Handoffs em
  `docs/handoffs/` (índice no `README.md` de lá): leia só o do sistema que a tarefa toca.
- **Servidor autoritativo:** o cliente envia intenções; cada dado tem um único serviço dono (`ProfileService`,
  `NeedsService`, `PlayerAnimationService`, `CharacterAppearanceService`, `DefeatCounter`). Estenda o que existe.
- **Separação de lado:** `net.minecraft.client.*` só dentro de `com.respectcraft.client`.
- **Saves:** não renomeie ids persistidos (attachments, chaves de `persistentData`, ids de origens, Specialities, atributos,
  necessidades, habilidades, itens e entidades). Id aposentado → alias em `profile.LegacyOriginIds` + `SelfTest`.
- **Personagem:** atributos (`CharacterAttribute`), necessidades (`Need`; ativas = Coom, Bexiga, Intestino, Emoção;
  Hidratação/Energia são legadas e inativas) e traços de nascimento (`BirthTrait`) são sistemas separados. Origem/Speciality não
  é traço; Specialities não têm efeito (Etapa 4.7). A roda da roleta tem fatias iguais (só visual) e mostra as chances reais
  do servidor (`SpecialityOddsSync`, Etapa 4.7.1). Traço sem efeito aprovado não ganha bônus. Elegibilidade (`BirthTrait.Eligibility`) só filtra sorteios novos
  pelo corpo do criador; traço gravado nunca é apagado (Etapa 4.6).
- **Idioma:** texto visível ao jogador em inglês, só em `en_us.json` (não recrie `pt_br.json`). Código, comentários,
  docs e ids continuam como estão.
- **Tema:** eleições, política, partidos, mandatos, urna, economia e símbolos nacionais foram removidos; não
  reintroduza sem pedido explícito.
- **Finalizações (Etapa 4.8):** cada participante usa o próprio rig (`FinisherParticipants`, `EntityFinisherClip`);
  sessão sempre com um jogador; contador só de jogador; `BossDefeatEvent` é só preparação (nada concedido).
- **Menu K (Etapas 4.8.1, 4.8.4-A, 4.8.4-B e 4.8.4-C):** holográfico em `client.holo`, núcleo `HoloMenu`. Padrão = **no mundo**
  (`HoloWorld`: placas paradas no mundo, jogador anda/pula/olha; K invoca/dispensa, R chama de volta; sem `Screen`).
  Clique/arrasto/roda pela mira (Alt segurado = modo cursor) via `HoloWorldInput`: o dono do botão é decidido no aperto
  e nada atravessa uma placa para o mundo (sem ataque, mineração, uso, balanço ou troca de slot). A
  tela `HoloCharacterScreen` é só reserva temporária (`mode = "SCREEN"`). Criador (só Body, Hairstyle, Hair Color) e
  roleta são janelas holográficas do mesmo arco (`CreatorView`, `RouletteView`); no modo mundo as telas antigas nunca
  abrem. Lógica sem desenho única: `AppearanceDraft`, `RouletteSession`/`ui.RouletteMachine`. A árvore de habilidades é a seção Skills.
  Os antigos `CharacterHubScreen`/`SkillTreeScreen` não existem mais.
- **Inverted Realm (Etapa 4.9, MVP):** dimensão/bioma/features em dados (`data/respectcraft/dimension*`, `worldgen/`),
  código só em `dimension` (`/respectcraft realm enter|leave|check`) e `client.dimension` (céu). Sem portal. Madeira
  Retorcida em `block.wood` (`ModBlocks` + `ModItems`); agua-vivas passivas (`AguaVivaEntity`), sem combate inventado.
- **Animações:** dados em `PlayerAnimations`; `.bbmodel` em `model_sources/player/`; respeite
  `tools/player_anim/master.json` e nunca use `export.py --force` sobre edições do Blockbench.
- **Validação:** `./gradlew build` e `bash .github/scripts/server-smoke-test.sh`; regras puras novas no `SelfTest`.
  Nunca diga que compilou ou testou sem rodar os comandos.
- **Entrega:** branch própria, PR para a `main`, merge só com CI verde (fluxo completo no `CLAUDE.md`).
