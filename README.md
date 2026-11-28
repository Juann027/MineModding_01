# RespectCraft

RPG de humor para **Minecraft Java 1.21.1** com **NeoForge 21.1.x**. Versão atual: **0.5.7 — RPG & Animation
Preview** (pre-release: vários recursos ainda têm arte provisória).

> **Etapa 4.7.1 — migração de identidade (quebra compatibilidade).** O mod é RespectCraft também por dentro: mod id e
> namespace `respectcraft`, pacote `com.respectcraft`, jar `respectcraft-<versão>.jar`, comando `/respectcraft` e configs
> `respectcraft-*-server.toml`. Mundos, fichas e configs criados com o nome antigo **não** são carregados: itens,
> as entidades e os dados do personagem desses mundos somem. Comece um mundo novo (e faça backup dos antigos).

## Funcionalidades

- **Menu holográfico do personagem** (tecla **K**, Etapas 4.8.1, 4.8.4-A, 4.8.4-B e 4.8.4-C): placas flutuantes num arco, **paradas no
  mundo** na frente de onde o jogador olhava (ele continua andando, pulando e olhando em volta; dá para dar a volta
  nelas). **K** invoca e dispensa; **R** (*Recall*, configurável em Controles) traz as placas para a frente da mira
  atual. A posição evita paredes (em sala pequena as placas chegam mais perto, com o mesmo tamanho aparente). Seções
  Overview, Character, Body & Traits, Speciality e Skills, o próprio jogador em 3D, partículas e animações de
  abertura/fechamento. **Mire e clique** (botão esquerdo ou direito) para navegar, comprar habilidades, arrastar a
  árvore e os controles do Display; a roda sobre uma placa gira o arco. Clicar numa placa nunca ataca, minera ou usa o
  que está atrás dela, e fora das placas os controles do Minecraft continuam normais. Segurar **Alt esquerdo** (modo
  cursor, configurável) solta o mouse para apontar com o cursor sem girar a câmera. A tela antiga da 4.8.1 fica como
  reserva de desenvolvimento (`mode = "SCREEN"`). Opções em `config/respectcraft-holomenu-client.toml`.
- **Character Creator** (primeira vez na tecla **K**, depois em Appearance no menu): janela holográfica no mesmo arco
  do menu, no mundo (o jogador continua andando e olhando), com o próprio jogador em 3D como prévia ao vivo (arrastar
  gira) e só **Body**, **Hairstyle** e **Hair Color** (setas, clique no valor ou a roda do mouse sobre a opção; botão
  Random). A pele e o rosto são sempre os da skin da conta. Save só termina com a confirmação do servidor; o primeiro
  personagem segue direto para a roleta (Character Creator → Birth Roulette → Hub, tudo no mundo).
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
- **Execuções reais (Etapa 4.10.1):** a finalização concluída mata o alvo como uma morte normal do Minecraft (loot, XP,
  estatísticas, conquistas, mensagem de morte); totem, invulnerabilidade, PvP desligado e proteções do servidor
  continuam valendo. Cancelar (Shift, `/stopitstopnow`, dano, saída) nunca mata. **Chefes** (Wither, Dragão do Fim e
  qualquer `c:bosses`/`respectcraft:bosses`) morrem normalmente em combate, mas finalizações comuns são recusadas contra
  eles antes da cena: no futuro cada chefe terá a própria finalização exclusiva, desbloqueada por progressão.
- **Efeitos das finalizações (Etapa 4.10)** pelo motor **Calamidade_Awakening**: cada finalização tem um efeito
  protagonista (HERO) que acompanha a cena — linhas de foco no Takedown (discreto), arco rente ao chão na Rasteira
  (médio) e cúpula de pressão no Combo (o mais intenso) —, luz e anel no chão a cada golpe, impact frame no golpe de
  força 3, onda de poeira nas quedas e céu escurecido contra chefes. Respeitam as opções de acessibilidade do
  Calamidade (intensidade dos efeitos de tela, ocultar clarões, efeitos de movimento). A câmera continua a do RespectCraft.
- **Contador de derrotas** acima do nick, com ícones próprios 🍆💦.
- **Avisos holográficos automáticos (Etapa 4.11):** cartões no canto da tela com o visual do menu K quando um chefe
  aparece ou é derrotado, um portal do Nether é aceso, você chega ao Inverted Realm ou algo acontece na caça a
  recompensa — com distância e direção até o evento e um farol de luz do Calamidade_Awakening no local. Todos ficam na
  nova seção **Alerts** do menu K (botão ✉ com os não lidos). Chefes, portais e eventos que ainda vão chegar usam o
  mesmo caminho. Opção "Notice cards" no painel Display.
- **Caça a recompensa (Etapa 4.11):** aba **Bounty Hunt** do menu K (ou `/respectcraft bounty`): ponha um preço na
  cabeça de outro jogador online usando a pilha da mão principal como recompensa, por 1, 3 ou 7 dias de jogo. Caçadores
  aceitam o contrato, veem a distância aproximada e a direção do alvo e recebem a recompensa ao matá-lo ou finalizá-lo.
  Ao expirar (ou se for cancelado antes de alguém aceitar), os itens voltam a quem pagou.
- **Missões e janelas do sistema (Etapa 4.14):** o sistema revela missões quando as coisas certas acontecem (conquistas,
  mortes, finalizações, dimensões, outras missões) — algumas secretas. Janelas cinematográficas diante da visão
  anunciam descobertas, objetivos, conclusões e perigos sem travar os controles (tamanho, posição, opacidade, volume,
  duração, animação e efeitos no painel Display, com botão Preview). Seção **Quests** no menu K (filtros, objetivos,
  recompensas, Accept/Track/Abandon/Claim) e rastreador compacto na tela. Missões são dados de datapack: formato e
  exemplos em [`docs/QUESTS.md`](docs/QUESTS.md). Três iniciais: *A Journey Begins*, *A Fruitful Encounter* (secreta) e
  *Beyond the Unknown*.
- **Time Warp (Etapa 4.15):** uma dimensão que parece um mundo antigo do Minecraft esquecido há anos — um oceano quase
  sem fim sob neblina densa, grama de verde forte, ilhas raras e, às vezes, casas de madeira, pedregulho ou terra e até
  um raro "servidor abandonado" com caminhos, plantações e docas. Ninguém mora lá. **Segure o botão de usar com um
  relógio** (3 s) no Overworld para entrar e no Time Warp para sair; cada bloco andado lá vale **3,33 blocos** no
  Overworld. Leve um barco. Ficar mais de três minutos não é recomendado: o Sistema avisa, a neblina fecha e coisas
  estranhas começam a acontecer. Sem relógio, fique parado olhando para o céu por 15 s para voltar ao ponto de entrada.
  Admin: `/respectcraft warp` (`enter|leave`, `status`, `exposure`, `happen`, `survey`, `census`, `pieces`, `check`).
- **Inverted Realm (Etapa 4.9, MVP de teste):** dimensão de **céu roxo** em crepúsculo eterno (horizonte magenta,
  estrelas fracas, sem nuvens, luz levemente violeta), bioma Twisted Wilds com grama violeta, árvores retorcidas de copa
  carmesim, penhascos de terracota roxa, brotos de ametista que brilham, lagos violeta e ilhas flutuantes no alto.
  Entrada e saída por comando de admin: `/respectcraft realm enter` e `/respectcraft realm leave` (ainda sem portal).
- **Madeira Retorcida:** conjunto completo (tronco, madeira, tábuas, escada, laje, cerca, portão, porta, alçapão, botão,
  placa de pressão, escada de mão, placas, placa pendurada, estante, bancada, barco e barco com baú), na aba do mod.
- **Agua-vivas:** Giant Jellyfish e Baby Jellyfish flutuam no céu do Inverted Realm (passivas por enquanto), com ovos
  na aba do mod.

## Instalação

1. NeoForge **21.1.265 ou mais novo (21.1.x)** para Minecraft 1.21.1, com **Java 21**.
2. Na pasta `mods`, **cinco jars**: `respectcraft-<versão>.jar`, **GeckoLib 4.9.3** (não a 5), **SmartBrainLib 1.16.11**,
   **Rimulib 1.3.0** para NeoForge ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/rimulib/files/9676532)) e
   **Calamidade_Awakening 0.14.0** (`calamidade_awakening-0.14.0.jar`, do
   [release v0.14.0 no GitHub](https://github.com/Juann027/Calamidade_Awakening-Mod-1.21.1/releases/tag/v0.14.0);
   aceita `[0.14.0,0.15.0)`, ou seja, só 0.14.x; versões 0.10–0.13 não carregam mais).
3. Multiplayer: os mesmos cinco jars no servidor dedicado e em cada cliente, com **a mesma versão do Calamidade** nos
   dois lados (o protocolo de rede dele muda entre versões menores).

UI Lib e Architectury **não** são mais necessários.

**Leão do Fim removido (Etapa 4.9.1):** o mob de teste Leão do Fim, o ovo e o comando `/leaodofim` saíram do mod.
Leões que existiam em mundos salvos **somem** ao carregar o mundo com esta versão (e o ovo some dos inventários).

**Faça backup dos mundos antes de atualizar.** Desde a Etapa 4.7.1 o namespace mudou para `respectcraft`: mundos
anteriores perdem os itens, entidades e dados do mod. As chances da roleta ficam em
`config/respectcraft-speciality-server.toml` (gerado com os padrões aprovados; uma cópia em
`<mundo>/serverconfig/` vale só para aquele mundo) e as necessidades em `config/respectcraft-needs-server.toml`.

## Compilar e testar

```bash
./gradlew build                           # jar em build/libs/
bash .github/scripts/server-smoke-test.sh # sobe o servidor dedicado e roda /respectcraft selftest
python3 tools/player_anim/bbsync.py       # confere .bbmodel x .animation.json
python3 tools/fx/check_shaders.py         # compila os shaders fx como a Rimulib (precisa do glslangValidator)
./gradlew runClient                       # cliente de desenvolvimento
./gradlew runServer                       # servidor de desenvolvimento
```

O `./gradlew build` baixa o Calamidade_Awakening do release do GitHub para `libs/` (fora do git) e confere o SHA-256.
O CI do GitHub roda os quatro primeiros em cada push e PR. Comandos úteis no jogo (árvore única `/respectcraft`):
`/respectcraft profile`, `/respectcraft skills list`, `/respectcraft finishers` (admins: `/respectcraft finishers <alvo>`
mostra quais finalizações podem executar aquele alvo),
`/respectcraft test finisher accept|deny` e, para admins, `/respectcraft selftest`, `/respectcraft speciality list|odds|simulate <rolls>`,
`/respectcraft anim list|play|together|sequence|next|stop`, `/respectcraft realm enter|leave [players]|check [radius]`
e `/respectcraft admin profile|points|roulette|reset|skill|finisher|effect|need ...`
(ex.: `/respectcraft admin roulette <player> boner`). `/stopitstopnow` continua como está.

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
- Inverted Realm é um MVP: sem portal (só o comando de admin), chão com blocos vanilla tingidos até a arte própria
  chegar, tronco retorcido com arte provisória e agua-vivas sem ataque.
- `npc` é pasta reservada, ainda sem conteúdo.
- Avisos de portal só cobrem o portal do Nether vanilla (o mod ainda não tem portais) e eventos de mundo ainda não
  existem; a caça a recompensa só aceita alvos online na hora de abrir o contrato.

Para desenvolvedores e agentes: [`CLAUDE.md`](CLAUDE.md) e [`AGENTS.md`](AGENTS.md).
