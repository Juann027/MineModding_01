Mod brasileiro de sátira política para **Minecraft Java 1.21.1** com **NeoForge 21.1.x**.

> **Pre-release.** Esta versão reúne tudo o que foi desenvolvido nas etapas 1 a 4.2 depois da v0.5.6. Vários
> recursos ainda são prévias com arte provisória. **Faça backup dos seus mundos antes de atualizar.**

## Novidades

- **Animações de jogadores com GeckoLib:** o jogador é desenhado por um rig GeckoLib integrado ao renderizador
  vanilla, com gerenciador de animações sincronizado entre todos os jogadores.
- **Biblioteca de combate:** socos, chutes, reações a impacto, quedas, ajoelhar, quatro apoios, rastejar e
  levantar, além de sequências contínuas de clips sem voltar à pose vanilla entre eles.
- **Finalizações cinematográficas:** motor de finalizações com coreografia sincronizada entre dois personagens.
- **Câmera dinâmica:** câmera cinematográfica que acompanha as finalizações e restaura a perspectiva ao sair.
- **Character Creator** (tecla **K**) com prévia 3D, pele, rosto, cabelos e cores.
- **Roleta de nascimento:** sorteio inicial de origem e atributos, com **um** novo giro opcional (decidido no servidor).
- **Skill Trees:** três categorias com 18 habilidades.
- **Necessidades e consumíveis:** Coom, Bexiga, Intestino, Emoção, Hidratação e Energia; consumíveis com efeitos
  temporários e atrasados; Emotion Orb na tela de jogo e painel Body & Traits no inventário.
- **Contador de derrotas** acima do nickname, com ícones próprios em pixel art 🍆💦.

## Melhorias

- Renderização do jogador pelo `PlayerRenderer` vanilla: skins individuais preservadas (Classic/Steve e Slim/Alex),
  armaduras, itens e camadas acompanham o rig.
- Body Layers para cabelos, pele, rosto e acessórios.
- Sincronização multiplayer das animações pelo relógio do servidor.
- Transições suaves entre animações e sequências.
- Ferramentas de autoria: arquivos `.bbmodel` compatíveis com o Blockbench, exportador com proteção contra
  sobrescrita e conferência automática dos eixos.
- Interfaces customizadas com framework de UI próprio (sem desfoque vanilla).

## Correções

- Escala do rig e rig após respawn e troca de dimensão.
- Problemas de layout no Character Creator e na HUD.
- Sincronização temporal de animações entre cliente e servidor.
- Poses antigas que sobravam depois de cancelar uma animação.
- Eixos de exportação das animações do Blockbench.
- Contador de derrotas invisível, descentralizado ou zerado após troca de dimensão.

## Limitações conhecidas

- Arte provisória em rostos, mãos e ícones; sons ainda usam eventos vanilla.
- Testes feitos com renderização por software: desempenho em GPU real ainda não foi medido.
- Quem começa a ver uma animação finita no meio a vê desde o início; pernas sem joelho tornam ajoelhar e quatro
  apoios aproximações.
- Finalizações contra mobs só animam quem executa e não contam derrota.
- Testes com mais de dois clientes simultâneos ainda são recomendados.
- Tigrinho, eleições, mandatos e economia seguem arquivados nesta variante.

## Como instalar

1. Instale o **NeoForge 21.1.265 ou mais novo (21.1.x) para Minecraft 1.21.1** (instalador em https://neoforged.net).
   É necessário **Java 21** (o launcher oficial já inclui).
2. Baixe **`tupinicraft-0.5.7.jar`** abaixo, em *Assets*, e os mods obrigatórios para NeoForge 1.21.1
   (Modrinth ou CurseForge):
   - **GeckoLib 4.9.3** (4.x, não use a 5) — cliente e servidor — https://modrinth.com/mod/geckolib
   - **SmartBrainLib 1.16.11** — cliente e servidor — https://modrinth.com/mod/smartbrainlib
   - **UI Lib 9.0.0** — só no cliente — https://modrinth.com/mod/ui-lib
   - **Architectury API 13.0.x** — só no cliente (exigido pelo UI Lib) — https://modrinth.com/mod/architectury-api
3. Coloque os jars na pasta `mods` do Minecraft:
   - Windows: `%APPDATA%\.minecraft\mods`
   - macOS: `~/Library/Application Support/minecraft/mods`
   - Linux: `~/.minecraft/mods`
4. Abra o jogo com o perfil **NeoForge**. Remova o jar da versão anterior do TupiniCraft.

**Multiplayer:** instale o TupiniCraft no **cliente e no servidor**. O servidor dedicado precisa de NeoForge
21.1.x com TupiniCraft, GeckoLib e SmartBrainLib; os jogadores precisam também do UI Lib e do Architectury.

## Mundos anteriores

Nenhum formato de dado salvo foi alterado nesta release: ficha do personagem, aparência, roleta (incluindo o novo
giro já usado), habilidades, necessidades e contador de derrotas usam os mesmos identificadores. Ainda assim,
**faça backup** antes de abrir mundos antigos.
