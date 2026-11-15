# HUD TupiniCraft: estrutura visual inicial (Minecraft 1.21.1 / NeoForge)

## O que foi feito

- `client/hud/EmotionOrbOverlay.java`: HUD no mundo, orbe central acima da barra de XP, com cinco estados visuais e rostos desenhados em pixel art, sem PNG. Respeita F1, ausência de jogador e modo espectador.
- `client/hud/BodyStatusInventoryPanel.java`: painel **Body & Traits** ao lado do inventário **do jogador** (`InventoryScreen`), sem substituir slots. Mostra quatro barras (Coom, Bladder/Bexiga, Bowel/Intestino, Emotion/Emoção). Lado e largura adaptáveis ao espaço livre; quando o livro de receitas estiver aberto, evita sobrepor esse livro. Inclui área reservada para traços e mostra explicitamente valores de demonstração.
- `client/hud/HudMeters.java` e `HudStatusClient.java`: contrato de leitura de quatro valores com clamp de 0–100 e amostra de teste, que poderão receber dados reais posteriormente.
- `TupiniCraftClient.java`: apenas os registros de eventos do cliente, sem modificar registros de mobs/itens, IA, assets do GeckoLib ou dados salvos.
- Idiomas pt_br e en_us: títulos e barras do painel.

## O que NÃO foi feito nesta etapa

- Não há sistemas reais de necessidades, variação de emoções, cooldowns, efeitos ou XP.
- Não há persistência ou sincronização com o servidor. Os valores **72/85/43/65 são fixos e demonstrativos**.
- Não foram incluídas novas texturas, nem dependências externas.
- Nenhuma modificação em saves ou no registro de entidades/itens.

## Para substituir pelos valores verdadeiros

1. Implementar as mecânicas e guardar seus dados no lado **servidor**.
2. Sincronizar um `HudMeters` por jogador com o cliente quando os valores mudarem / após login e respawn.
3. Substituir o retorno de `HudStatusClient.current()` pela cópia local sincronizada; limpar no logout.
4. Depois substituir os `fill` de `EmotionOrbOverlay` e `BodyStatusInventoryPanel` por sprites PNG, se desejar.

## Verificação manual sugerida no jogo

1. Abra um mundo de testes (não um save importante).
2. Veja o Emotion Orb no centro da HUD acima do XP; F1 deve ocultar.
3. Aperte E no modo sobrevivência: à lateral do inventário deve aparecer o painel com quatro barras e `Valores de teste`.
4. Ajuste a escala da interface para 2, 3 e 4: o painel deve mudar de lado/largura sem invadir os slots.
5. Experimente os modos criativo e espectador: o painel foi limitado ao inventário survival, e o orbe não aparece no espectador.
