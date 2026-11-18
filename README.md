# RespectCraft

RPG de humor para **Minecraft Java 1.21.1** com **NeoForge 21.1.x**. Versão atual: **0.5.7 — RPG & Animation
Preview** (pre-release: vários recursos ainda têm arte provisória).

> O mod se chamava **TupiniCraft**. Só o nome público mudou: o mod id, o jar (`tupinicraft-<versão>.jar`), o pacote
> Java, o namespace de recursos, os comandos (`/tupini`, `/tupinianim`, `/tupinitest`, `/leaodofim`) e os dados
> salvos continuam `tupinicraft`. Mundos e fichas existentes seguem funcionando.

## Funcionalidades

- **Character Creator** (tecla **K**): menu do personagem com prévia 3D, corpo, pele, rosto, cabelos e cores.
- **Roleta de nascimento:** sorteio no servidor de uma entre 8 origens (Favela, Sertão, Pampa, Amazônia, Classe Média,
  Família Intelectual, Família Artística, Berço de Ouro), dos 6 atributos (Força, Agilidade, Vigor, Inteligência,
  Carisma, Sorte) e de um **traço de nascimento** (buff, debuff ou neutro), com **um** novo giro opcional. Continuar
  confirma o resultado.
- **Traços de nascimento:** características individuais separadas da origem e dos atributos. O sorteio usa o corpo
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
- **Finalizações cinematográficas** entre dois personagens, com câmera dinâmica.
- **Contador de derrotas** acima do nick, com ícones próprios 🍆💦.
- **Leão do Fim:** mob neutro de teste (GeckoLib + SmartBrainLib), com ovo na aba do mod.

## Instalação

1. NeoForge **21.1.265 ou mais novo (21.1.x)** para Minecraft 1.21.1, com **Java 21**.
2. Na pasta `mods`: `tupinicraft-<versão>.jar`, **GeckoLib 4.9.3** (não a 5) e **SmartBrainLib 1.16.11**.
3. Multiplayer: os mesmos três jars no servidor dedicado e em cada cliente.

UI Lib e Architectury **não** são mais necessários.

**Faça backup dos mundos antes de atualizar.** A partir da limpeza da Etapa 4.4, o mob de teste Cabo Eleitoral e o
seu ovo deixaram de existir: em mundos antigos eles somem ao carregar. Fichas com a antiga origem política são
convertidas automaticamente para Família Artística (mesmos atributos e habilidades).

## Compilar e testar

```bash
./gradlew build                           # jar em build/libs/
bash .github/scripts/server-smoke-test.sh # sobe o servidor dedicado e roda /tupini selftest
python3 tools/player_anim/bbsync.py       # confere .bbmodel x .animation.json
./gradlew runClient                       # cliente de desenvolvimento
./gradlew runServer                       # servidor de desenvolvimento
```

O CI do GitHub roda os três primeiros em cada push e PR. Comandos úteis no jogo: `/tupini perfil`,
`/tupini skills list`, `/tupini finishers`, `/tupinianim list` e, para admins, `/tupini selftest` e `/tupini admin ...`.

## Animações no Blockbench

Os projetos ficam em `model_sources/player/`. Use Blockbench 5.x com o plugin **GeckoLib Models & Animations**,
edite só keyframes e exporte o `.animation.json`. O arquivo `tools/player_anim/master.json` diz se a fonte de verdade
é o gerador (`poses.py`/`clips.py` + `export.py`) ou o Blockbench; o exportador nunca sobrescreve edições manuais
sem `--force`. Guia completo: [`tools/player_anim/README.md`](tools/player_anim/README.md).

## Estado e limitações

- Pre-release: arte provisória em rostos, mãos e ícones; sons usam eventos vanilla.
- Finalizações contra mobs só animam quem executa e não contam derrota.
- Pernas sem joelho tornam ajoelhar e quatro apoios aproximações.
- Testado com renderização por software e até dois clientes simultâneos.
- `biome`, `dimension` e `npc` são pastas reservadas, ainda sem conteúdo.

Para desenvolvedores e agentes: [`CLAUDE.md`](CLAUDE.md) e [`AGENTS.md`](AGENTS.md).
