Mod brasileiro de sátira política para **Minecraft 1.21.1** com **NeoForge 21.1.x**.

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

## O que tem nesta versão
- Origem regional sorteada (27 estados, com gentílico) no primeiro login.
- Cargos políticos de Desempregado a Presidente da República, com XP político e popularidade.
- HUD com cargo, popularidade e barra de XP político.
- Eleições globais com inscrição, votação na **Urna Eletrônica**, peso de voto por popularidade e apuração gradual.
- Mandatos por território (Brasil, 27 estados e 27 capitais), candidatos aldeões e configurações administrativas.
- Árvores de habilidades (pessoais, regionais, partidárias e de cargo) numa tela no estilo de Progressos (tecla **K**):
  clique numa skill para comprá-la; cada skill exige a anterior da árvore. Cada jogador começa com R$ 1.000 (saldo no HUD). 4 skills de teste.
- Mob de teste "Cabo Eleitoral" com animações (GeckoLib) e IA (SmartBrainLib).
- Comandos `/brasil perfil`, `/brasil skills`, `/brasil mandatos`, `/brasil eleicao ...` e `/brasil admin ...`.

A lista completa de comandos está no README do repositório.
