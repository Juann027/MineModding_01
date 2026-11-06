Mod brasileiro de sátira política para **Minecraft 1.21.1** com **NeoForge 21.1.x**.

## Como instalar
1. Instale o **NeoForge 21.1.x para Minecraft 1.21.1** (instalador em https://neoforged.net → "Install client").
2. Baixe o arquivo **`tupinicraft-*.jar`** abaixo, em *Assets*, e também as bibliotecas obrigatórias
   **GeckoLib 4.9.3** e **SmartBrainLib 1.16.11** para NeoForge 1.21.1 (Modrinth ou CurseForge).
   Opcional, para as árvores de habilidades: **SkillLib 1.4.0** (que hoje exige KubeJS 2101.7.2, Rhino e Better Advanced Tooltips).
3. Coloque os jars na pasta `mods` do Minecraft:
   - Windows: `%APPDATA%\.minecraft\mods`
   - macOS: `~/Library/Application Support/minecraft/mods`
   - Linux: `~/.minecraft/mods`
4. Abra o jogo com o perfil **NeoForge**.

**Multiplayer:** o servidor precisa de NeoForge 21.1.x com os mesmos jars em `mods/`, e todos os jogadores precisam dos mesmos mods.
Comandos de administração (`/brasil admin ...`) exigem OP.

## O que tem nesta versão
- Origem regional sorteada (27 estados, com gentílico) no primeiro login.
- Cargos políticos de Desempregado a Presidente da República, com XP político e popularidade.
- HUD com cargo, popularidade e barra de XP político.
- Eleições globais com inscrição, votação na **Urna Eletrônica**, peso de voto por popularidade e apuração gradual.
- Mandatos por território (Brasil, 27 estados e 27 capitais), candidatos aldeões e configurações administrativas.
- Árvores de habilidades (pessoais, regionais, partidárias e de cargo) via SkillLib, com 4 skills de teste.
- Mob de teste "Cabo Eleitoral" com animações (GeckoLib) e IA (SmartBrainLib).
- Comandos `/brasil perfil`, `/brasil skills`, `/brasil mandatos`, `/brasil eleicao ...` e `/brasil admin ...`.

A lista completa de comandos está no README do repositório.
