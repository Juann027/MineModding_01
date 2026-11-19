# Handoffs — índice

Documentos de continuidade de cada etapa, em ordem. São **históricos**: descrevem o código na época (o nome público
mudou para RespectCraft na Etapa 4.5; os ids internos — mod id, pacote, namespace, comandos `/tupini*` e configs —
só mudaram na Etapa 4.7.1, então os handoffs anteriores citam `tupinicraft`). Quando contradizem o código ou o [`CLAUDE.md`](../../CLAUDE.md), valem o código
e o `CLAUDE.md`.

**Não leia todos.** Abra só o handoff do sistema em que você vai mexer. Caminhos citados dentro deles (como
`docs/evidencias/...`) são relativos à raiz do repositório.

| Etapa | Sistema | Handoff | Status histórico | Limitações importantes |
|---|---|---|---|---|
| 1 | Teste do jogador com GeckoLib, teste visual em Xvfb | [HANDOFF_GECKOLIB_STAGE1.md](HANDOFF_GECKOLIB_STAGE1.md) | Concluída; renderer de teste substituído na Etapa 2 | Só render por software 854×480; skins de conta não testadas |
| 2 | Rig do jogador (`PlayerRigLayer`), Body Layers, Animation Manager, multiplayer | [HANDOFF_GECKOLIB_STAGE2.md](HANDOFF_GECKOLIB_STAGE2.md) | Concluída; base atual do rig | Quem começa a ver um clip finito no meio o vê do início |
| 3 | UI própria, Character Creator, roleta, skills, necessidades, Finisher Engine | [HANDOFF_ETAPA3.md](HANDOFF_ETAPA3.md) | Concluída | Sons usam eventos vanilla |
| 3.1 | Coom, HUD, roleta com um único novo giro, prévia 3D, consumíveis | [HANDOFF_ETAPA3_1.md](HANDOFF_ETAPA3_1.md) | Concluída | Rostos e mãos com arte provisória 8×8 |
| 4 | Biblioteca de animações de combate (keyframes) | [HANDOFF_PLAYER_ANIMATIONS_STAGE4.md](HANDOFF_PLAYER_ANIMATIONS_STAGE4.md) | Concluída | Encadeamento de clips resolvido na 4.1 |
| 4.1 | Blockbench (`bbsync`, mestre), sequências de animação | [HANDOFF_PLAYER_ANIMATIONS_STAGE4_1.md](HANDOFF_PLAYER_ANIMATIONS_STAGE4_1.md) | Concluída | Sem importação Blockbench → gerador |
| 4.2 | Contador de derrotas 🍆💦 acima do nick | [HANDOFF_DEFEAT_COUNTER_STAGE4_2.md](HANDOFF_DEFEAT_COUNTER_STAGE4_2.md) | Concluída | Finalização contra mob não conta |
| 4.4 | Remoção do legado político, migração `familia_politica` → `familia_artistica` | [HANDOFF_LEGACY_CLEANUP.md](HANDOFF_LEGACY_CLEANUP.md) | Concluída (PR #25) | Cabo Eleitoral some de mundos antigos |
| 4.5 | Rebranding RespectCraft, handoffs em `docs/handoffs/`, correção do giro infinito da roleta | [HANDOFF_STAGE4_5.md](HANDOFF_STAGE4_5.md) | Concluída | Nome novo só na marca; ids seguem `tupinicraft` |
| 4.5 rev. | Criador sem retrato/nota, paleta única de cabelo, traços de nascimento, Hidratação/Energia inativas, confirmação da roleta | [HANDOFF_STAGE4_5_REVISED.md](HANDOFF_STAGE4_5_REVISED.md) | Concluída (PR #27) | Traços sem efeito de jogo; habilidades Cantil e Rede sem efeito |
| 4.6 | Traços por corpo (universais × só masculinos), Fertile/Infertile, texto visível em inglês | [HANDOFF_BIRTH_TRAITS_STAGE4_6.md](HANDOFF_BIRTH_TRAITS_STAGE4_6.md) | Concluída | Fertile/Infertile sem efeito; sem gravidez |
| 4.7 | Specialities na roleta (15, raridades 41,5/25/20/10/3/0,5%, BONER no topo), `birth`/`current` Speciality, origens sociais legadas | [HANDOFF_SPECIALITIES_STAGE4_7.md](HANDOFF_SPECIALITIES_STAGE4_7.md) | Concluída | Specialities sem perks/efeitos; troca no fim de jogo não implementada |
| 4.7.1 | Rebrand técnico (mod id/namespace `respectcraft`, pacote `com.respectcraft`, `/respectcraft`, configs `respectcraft-*`), roda com fatias iguais e chances reais do servidor, revelação BONER | [HANDOFF_STAGE4_7_1_REBRAND_ROULETTE.md](HANDOFF_STAGE4_7_1_REBRAND_ROULETTE.md) | Concluída (PR #30) | **Mundos anteriores não são compatíveis** |

Não há handoff da Etapa 4.3 neste repositório.

## Qual ler

- Rig, render do jogador, Body Layers → Etapa 2 (e 1 para o teste visual em Xvfb).
- Animações do jogador, Blockbench → Etapas 4 e 4.1 (e `tools/player_anim/README.md`).
- Telas, criador, roleta, skills, necessidades, finalizações → Etapas 3 e 3.1; protocolo da roleta → 4.5.
- Atributos × necessidades × traços de nascimento, criador atual, Hidratação/Energia → 4.5 revisada.
- Elegibilidade de traços por corpo, Fertile/Infertile, idioma inglês → 4.6.
- Specialities (raridades, chances, catálogo, roleta, origens legadas) → 4.7.
- Identidade do mod (ids, pacote, comandos, configs) e roda da roleta (fatias, chances sincronizadas) → 4.7.1.
- Contador de derrotas → Etapa 4.2.
- Ids aposentados e compatibilidade de saves → Etapa 4.4 (antes do corte de compatibilidade da 4.7.1).
