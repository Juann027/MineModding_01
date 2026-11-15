# TupiniCraft 0.5.4 — Finisher de COMBATE (protótipo não explícito)

## Escopo e limitações

Este protótipo substitui o **Carry System** anterior: **não é um mod de carregar**.
Implementa mecânicas de luta e uma **animação vanilla provisória de braço**, NÃO uma cena sexual.
**Não incorpora animações explícitas, não inicia interações sexuais por perda de vida e não remove o direito de cancelar.**

O ZIP inclui a HUD antiga e o contador de derrotas persistente na cabeça do jogador.
A versão não foi compilada em nosso ambiente, pois o Gradle 9.2.1 não pôde ser baixado.

## Jogador x jogador (dois clientes)

1. Inicie um mundo/servidor de testes com dois jogadores.
2. B deve ter **estritamente menos que 25%** de HP (p.ex. 4/20).
3. A, com a mão principal vazia, **segura Shift e clica com o botão direito** em B a até 3,5 blocos.
4. B recebe convite temporário (15 segundos) e decide:
   - `/tupinitest finisher accept`
   - `/tupinitest finisher deny`
5. Depois de aceitar, B torna-se passageiro de A **temporariamente**. O jogo pode mostrar a dica vanilla "Pressione Shift para descer".
6. O protocolo visual recebe `animationKey = test_hold`, com duração de **60 ticks (3s)**. Neste ZIP a animação real do player ainda é **swing vanilla**, não um clip GeckoLib.
7. Se ambos permanecem válidos até o fim, B recebe `+1` derrota especial e fica incapacitado por 200 ticks (10s). Durante esses 10s, dano externo **é permitido**.
8. Durante a cena, um dano recebido por qualquer participante cancela o encontro e evita aquele dano. Não incrementa o contador.
9. **Em qualquer momento**, B pode pressionar Shift, ou **qualquer participante** pode usar `/stopitstopnow`. O mesmo comando remove a incapacitação pós-cena.

### Comandos

| Comando | Finalidade |
|---|---|
| `/stopitstopnow` | Emergência: interrompe o finisher e/ou a recuperação; sem OP |
| `/tupinitest finisher accept` | Aceita o convite de combate de teste atual |
| `/tupinitest finisher deny` | Recusa |
| `/tupinitest finisher mobs on` | Autoriza, para o próprio player, a demonstração de ataque de mob |
| `/tupinitest finisher mobs off` | Revoga a opção |
| `/tupinitest finisher mobtest` | Tenta iniciar demo com mob pequeno/humanoide próximo, com HP do jogador abaixo de 25% |
| `/tupinitest defeats get` | Consulta seu contador |
| `/tupinitest defeats set <player> <valor>` | Define contador, OP |
| `/tupinitest defeats add <player>` | Aumenta contador, OP |

## Mob -> jogador

* Com opção opt-in ativada, o próprio jogador vulnerável invoca `/tupinitest finisher mobtest` com mob compatível (até 3 blocos).
* Existe API para futuros mobs personalizados: `FinisherTestMechanic.tryStartFromMob(Mob, ServerPlayer)`.
* **Ainda não há IA automática para mobs**, e nenhum mob existente foi alterado. Apenas a demonstração manual utiliza essa API.
* O contador sobe se a sessão demonstrativa termina normalmente.

## Regras técnicas

* Ticks de cena: 60; incapacitação: 200; cooldown **mesma dupla**: 1.200 ticks (60s); proteção vítima: 300 ticks (15s).
* Servidor é autoridade de HP, duração, estado, vinculação por passageiros, contador e cooldowns.
* As sessões não persistem no save (não devem sobreviver a reinício). O número de derrotas e a autorização de demos de mobs persistem.
* Desconexão, morte, desmontagem, teleporte/dimensão e dano inesperado encerram a sessão e liberam participantes.
* Incapacitação é um primeiro protótipo: servidor corrige movimento e cancela interações comuns; ainda deve ser testada com itens, outros mods, packets e efeitos especiais.
* O contador é incrementado ao **concluir**, não ao iniciar; comandos administrativos de teste permanecem disponíveis.
* O player liberado após Shift/dano/cancelamento NÃO recebe +1.

## Como plugar animações Blockbench/GeckoLib no futuro

O projeto do Mowzie's Mobs tem uma implementação personalizada e mais complexa: `GeckoPlayer` implementa `GeoEntity`, `GeckoRenderPlayer` substitui renderização, e o `ClientEventHandler` decide quando trocar para o renderizador animado, inclusive primeira pessoa.

Fontes revisadas:
- https://github.com/BobMowzie/MowziesMobs-Public/blob/main/src/main/java/com/bobmowzie/mowziesmobs/client/render/entity/player/GeckoPlayer.java
- https://github.com/BobMowzie/MowziesMobs-Public/blob/main/src/main/java/com/bobmowzie/mowziesmobs/client/render/entity/player/GeckoRenderPlayer.java
- https://github.com/BobMowzie/MowziesMobs-Public/blob/main/src/main/java/com/bobmowzie/mowziesmobs/client/ClientEventHandler.java

**Nenhum código desse repositório foi copiado**; ele foi estudado como referência técnica.

Neste projeto, `FinisherVisualSyncPayload` transmite as identidades do iniciador/alvo, a chave `test_hold`, e o início/fim da cena; `FinisherVisualClientCache` guarda qual jogador usa qual papel (iniciador / alvo). É o ponto de entrada para o renderer futuro.

Ao receber animações não explícitas compatíveis com o player vanilla:
1. Configurar player-model com bones `head`, `body`, `right_arm`, `left_arm`, `right_leg`, `left_leg`, variantes slim/classic;
2. Implementar renderizador adaptador para player no **cliente** (nunca carregar classes client no servidor dedicado);
3. Rodar clips distintos para `initiator` e `target`, sincronizados pelo mesmo `animationKey`, e interromper no pacote `active = false`;
4. Restabelecer renderizador vanilla quando a sessão terminar;
5. Testar com perspectiva em primeira pessoa, armaduras, capas, skins, ferramentas e outros mods que alterem o render do player.

**O ZIP atual NÃO afirma ter o renderizador GeckoLib do Mowzie**, pois ainda faltam esses arquivos de animação e a adaptação visual.

## Compilação/testes

```bash
./gradlew compileJava
./gradlew runClient
# testes puros sem depender do Gradle (requer JDK 21):
mkdir -p /tmp/tupini-test
javac -d /tmp/tupini-test src/main/java/com/tupinicraft/testgame/FinisherRules.java tests/manual/FinisherRulesTest.java
java -cp /tmp/tupini-test FinisherRulesTest
```
