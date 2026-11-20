# RespectCraft

RPG de humor para **Minecraft Java 1.21.1** com **NeoForge 21.1.x**. Versão atual: **0.5.7 — RPG & Animation
Preview** (pre-release: vários recursos ainda têm arte provisória).

> **Etapa 4.7.1 — migração de identidade (quebra compatibilidade).** O mod é RespectCraft também por dentro: mod id e
> namespace `respectcraft`, pacote `com.respectcraft`, jar `respectcraft-<versão>.jar`, comando `/respectcraft` e configs
> `respectcraft-*-server.toml`. Mundos, fichas e configs criados com o nome antigo **não** são carregados: itens,
> o Leão do Fim e os dados do personagem desses mundos somem. Comece um mundo novo (e faça backup dos antigos).

## Funcionalidades

- **Menu holográfico do personagem** (tecla **K**, Etapas 4.8.1, 4.8.4-A e 4.8.4-B): placas flutuantes num arco, **paradas no
  mundo** na frente de onde o jogador olhava (ele continua andando, pulando e olhando em volta; dá para dar a volta
  nelas). **K** invoca e dispensa; **R** (*Recall*, configurável em Controles) traz as placas para a frente da mira
  atual. A posição evita paredes (em sala pequena as placas chegam mais perto, com o mesmo tamanho aparente). Seções
  Overview, Character, Body & Traits, Speciality e Skills, o próprio jogador em 3D, partículas e animações de
  abertura/fechamento. **Mire e clique** (botão esquerdo ou direito) para navegar, comprar habilidades, arrastar a
  árvore e os controles do Display; a roda sobre uma placa gira o arco. Clicar numa placa nunca ataca, minera ou usa o
  que está atrás dela, e fora das placas os controles do Minecraft continuam normais. Segurar **Alt esquerdo** (modo
  cursor, configurável) solta o mouse para apontar com o cursor sem girar a câmera. A tela antiga da 4.8.1 fica como
  reserva de desenvolvimento (`mode = "SCREEN"`). Opções em `config/respectcraft-holomenu-client.toml`.
- **Character Creator** (primeira vez na tecla **K**, depois em Appearance no menu): prévia 3D, corpo, pele, rosto,
  cabelos e cores.
- **Roleta de nascimento:** sorteio no servidor de uma **Speciality** (15, em seis raridades: Common 41,5%,
  Uncommon 25%, Rare 20%, Epic 10%, Legendary 3%, BONER 0,5% — a raridade sai primeiro, depois a Speciality dela), dos
  6 atributos (Força, Agilidade, Vigor, Inteligência, Carisma, Sorte) e de um **traço de nascimento** (buff, debuff ou
  neutro), com **um** novo giro opcional. Continuar confirma o resultado. A roda tem seis fatias iguais (o tamanho
  não é a chance) e mostra em números as chances reais configuradas no servidor. Specialities ainda não têm perks nem efeitos.
  Personagens criados antes da 4.7 mantêm a origem social antiga (Favela, Sertão etc.) até usarem o novo giro.
- **Traços de nascimento:** características individuais separadas da Speciality e dos atributos. O sorteio usa o corpo
  escolhido no criador. **Universais** (masculino e feminino): High Endurance (buff), Limp (debuff), Fertile (buff),
  Infertile (debuff), Perfectly Average (neutro). **Só no sorteio masculino:** Heavy Loader, Loadless, Hung, Tiny Dick.
  Traço já gravado nunca é apagado ao mudar a aparência. Por enquanto são só descrição: nenhum tem efeito de jogo.
- **Idioma:** todo texto visível do mod está em inglês (`en_us.json` é o único arquivo de idioma; outros idiomas do
  jogo caem no inglês).
- **Skill Trees:** abas Combate, Sobrevivência e Malandragem, 18 habilidades compradas com pontos ganhos por nível
  de XP.
- **Necessidades:** Coom, Bexiga, Intestino e Emoção, com orbe de Emoção na tela e painel Body & Traits no
  inventário. (Hidratação e Energia foram desativadas na Etapa 4.5; os valores antigos ficam guardados no save.)
- **Consumíveis:** Água de Coco, Cafezinho, Guaraná, Pão de Queijo, Coxinha, Brigadeiro, Penico e itens fictícios
  (laxante, antidiarreico, chás, café extraforte, energético, pílula azul, afrodisíaco) com efeitos temporários.
- **Animações do jogador com GeckoLib:** o jogador é desenhado por um rig integrado ao `PlayerRenderer` vanilla
  (skins, armaduras e itens preservados), com biblioteca de combate e sequências sincronizadas pelo servidor.
- **Finalizações cinematográficas** entre dois personagens, com câmera dinâmica. Desde a Etapa 4.8 o motor aceita
  participantes com rigs GeckoLib diferentes (jogador × jogador, jogador × mob, mob × jogador, chefe × jogador),
  cada um no próprio rig.
- **Contador de derrotas** acima do nick, com ícones próprios 🍆💦.
- **Leão do Fim:** mob neutro de teste (GeckoLib + SmartBrainLib), com ovo na aba do mod.

## Instalação

1. NeoForge **21.1.265 ou mais novo (21.1.x)** para Minecraft 1.21.1, com **Java 21**.
2. Na pasta `mods`: `respectcraft-<versão>.jar`, **GeckoLib 4.9.3** (não a 5), **SmartBrainLib 1.16.11** e
   **Rimulib 1.3.0** para NeoForge ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/rimulib/files/9676532)).
3. Multiplayer: os mesmos quatro jars no servidor dedicado e em cada cliente.

UI Lib e Architectury **não** são mais necessários.

**Faça backup dos mundos antes de atualizar.** Desde a Etapa 4.7.1 o namespace mudou para `respectcraft`: mundos
anteriores perdem os itens, entidades e dados do mod. As chances da roleta ficam em
`config/respectcraft-speciality-server.toml` (gerado com os padrões aprovados; uma cópia em
`<mundo>/serverconfig/` vale só para aquele mundo) e as necessidades em `config/respectcraft-needs-server.toml`.

## Compilar e testar

```bash
./gradlew build                           # jar em build/libs/
bash .github/scripts/server-smoke-test.sh # sobe o servidor dedicado e roda /respectcraft selftest
python3 tools/player_anim/bbsync.py       # confere .bbmodel x .animation.json
./gradlew runClient                       # cliente de desenvolvimento
./gradlew runServer                       # servidor de desenvolvimento
```

O CI do GitHub roda os três primeiros em cada push e PR. Comandos úteis no jogo (árvore única `/respectcraft`):
`/respectcraft profile`, `/respectcraft skills list`, `/respectcraft finishers`,
`/respectcraft test finisher accept|deny` e, para admins, `/respectcraft selftest`, `/respectcraft speciality list|odds|simulate <rolls>`,
`/respectcraft anim list|play|together|sequence|next|stop` e `/respectcraft admin profile|points|roulette|reset|skill|finisher|effect|need ...`
(ex.: `/respectcraft admin roulette <player> boner`). `/leaodofim` e `/stopitstopnow` continuam como estão.

## Animações no Blockbench

Os projetos ficam em `model_sources/player/`. Use Blockbench 5.x com o plugin **GeckoLib Models & Animations**,
edite só keyframes e exporte o `.animation.json`. O arquivo `tools/player_anim/master.json` diz se a fonte de verdade
é o gerador (`poses.py`/`clips.py` + `export.py`) ou o Blockbench; o exportador nunca sobrescreve edições manuais
sem `--force`. Guia completo: [`tools/player_anim/README.md`](tools/player_anim/README.md).

## Estado e limitações

- Pre-release: arte provisória em rostos, mãos e ícones; sons usam eventos vanilla.
- Finalizações contra mobs só animam o jogador; o mob fica parado até ganhar clips no próprio rig (Etapa 4.8). Mob
  nunca recebe contador de derrota.
- Rimulib 1.3.0 está conectado, mas ainda não há efeito de tela que o use.
- Pernas sem joelho tornam ajoelhar e quatro apoios aproximações.
- Testado com renderização por software e até dois clientes simultâneos.
- `biome`, `dimension` e `npc` são pastas reservadas, ainda sem conteúdo.

Para desenvolvedores e agentes: [`CLAUDE.md`](CLAUDE.md) e [`AGENTS.md`](AGENTS.md).
