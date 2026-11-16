Mod brasileiro de sátira política para **Minecraft 1.21.1** com **NeoForge 21.1.x**.

## Correções da v0.5.6

- Tela do criador de personagem sem desfoque do mundo e com botões que não saem do painel.
- **Salvar** espera a confirmação do servidor antes de abrir o menu.
- Novo menu principal da tecla **K** depois da criação (Editar aparência / Voltar ao jogo).

## Novidades da v0.5.5

- Criador de personagem inicial (tecla **K**).
- Sete penteados e seis cores de cabelo.
- Personalização e sincronização da aparência entre jogadores.
- Emotion Orb na HUD.
- Painel Body & Traits no inventário.
- Contador de derrotas acima do nickname.
- Protótipo de finalizações (comando `/stopitstopnow`).

## Observações

- Algumas funcionalidades ainda estão em fase experimental.
- Os visuais e as animações definitivas ainda não foram integrados.
- Testes multiplayer manuais ainda são recomendados.

## Como instalar
1. Instale o **NeoForge 21.1.x para Minecraft 1.21.1** (instalador em https://neoforged.net → "Install client").
2. Baixe o arquivo **`tupinicraft-*.jar`** abaixo, em *Assets*, e também os 4 mods obrigatórios para NeoForge 1.21.1
   (Modrinth ou CurseForge):
   - **GeckoLib 4.9.3** — https://modrinth.com/mod/geckolib
   - **SmartBrainLib 1.16.11** — https://modrinth.com/mod/smartbrainlib
   - **UI Lib 9.0.0** (só no cliente) — https://modrinth.com/mod/ui-lib
   - **Architectury API 13.0.x** (só no cliente; exigido pelo UI Lib) — https://modrinth.com/mod/architectury-api

   Vindo da v0.4.0: SkillLib, KubeJS e Rhino não são mais necessários.
3. Coloque os jars na pasta `mods` do Minecraft:
   - Windows: `%APPDATA%\.minecraft\mods`
   - macOS: `~/Library/Application Support/minecraft/mods`
   - Linux: `~/.minecraft/mods`
4. Abra o jogo com o perfil **NeoForge**.

**Multiplayer:** o servidor precisa de NeoForge 21.1.x com TupiniCraft, GeckoLib e SmartBrainLib em `mods/`; os jogadores precisam também do UI Lib e do Architectury.
Comandos de administração (`/brasil admin ...`) exigem OP.

## Base do projeto
- Cabo Eleitoral (GeckoLib + SmartBrainLib), Leão do Fim e pastas de bioma/dimensão/NPC como pontos de partida.
- Tigrinho, eleições, mandatos, economia e árvores de habilidades estão arquivados nesta variante.

A lista completa de comandos está no README do repositório.
