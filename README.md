# TupiniCraft – Foco em biomas, dimensões e NPCs

## Conteúdo ativo
- **NPCs/mobs:** Cabo Eleitoral (mob de teste) e Leão do Fim, com registries, renderers GeckoLib, IA SmartBrainLib e ovos de spawn.
- **Biomas e dimensões:** ainda não existem implementações no ZIP original. Pastas `biome`, `dimension` e `npc` são apenas pontos de partida.
- **Skill trees:** ativas, tecla K, com dados em `data/tupinicraft/tupinicraft_skilltree/`.  Requisitos regionais continuam usando origem e regras antigas de pontos de cargo/filiação. **Sem eleição/mandato**, habilidades que dependem de mandato não podem ativar seus efeitos. Economia foi retirada; há somente **créditos internos legados** para permitir testar compras de skills, configuráveis por comando de OP `/skilladmin dinheiro set <jogador> <valor>`. `/skills` lista e compra as skills. Sem progressão econômica automática.

## Desativado nesta variante
Tigrinho, eleições, mandatos políticos, HUD/urna cívica e economia geral. Essas classes não ficam em `src/main/java` ativo.

## Assets e recuperação
- A pasta `src/main/resources/assets/tupinicraft/` mantém **todos os assets originais**, incluindo PNG, JSON e modelos atualmente sem uso.
- `backups/codigo-e-documentacao-original.zip` guarda uma cópia dos códigos, testes, docs e CI originais.
- `backups/datapacks-desativados.zip` guarda receitas, loot tables, tags e JSONs de datapacks retirados da versão ativa.
- O ZIP original fornecido pelo usuário é a referência integral para restauração.

## Dependências e execução
Minecraft 1.21.1, NeoForge 21.1.x, Java 21, GeckoLib 4 e SmartBrainLib. UI Lib e Architectury no cliente para a variante com skill trees.

Com Java/Gradle configurados e dependências disponíveis, execute `./gradlew build` (Linux/macOS) ou `gradlew.bat build` (Windows). O JAR ficará em `build/libs/`. Este pacote entregue contém **código-fonte, não um JAR compilado**.

## Aviso sobre saves
**Não teste esta versão diretamente em um mundo existente sem backup.** A remoção de registros de blocos/itens (Tigrinho e urna) e sistemas pode produzir remapeamentos de IDs e perda de dados. Clone o mundo ou use um mundo de desenvolvimento novo.

## Desenvolvimento futuro
1. Criar registros NeoForge de biomas e worldgen baseados em dados.
2. Criar um primeiro registro de dimensão e testes de teleporte/geração.
3. Definir NPCs em `entity/` + renderização em `client/render/`, com assets em `assets/tupinicraft/`.
4. Reativar os sistemas antigos somente quando fizer sentido.
