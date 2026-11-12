# LeãoDoFim — integração inicial

Implementação sobre TupiniCraft 0.5.3-tigrinho-dev, Minecraft 1.21.1, NeoForge 21.1.265 e GeckoLib 4.9.3. Nenhuma dependência foi adicionada.

## Estado da entrega

- Recursos exportados e verificados: 45 ossos, 127 cubos, 14 animações originais e 1.777 keyframes.
- Texturas principal e emissiva copiadas sem alteração, 128 × 128 pixels.
- Entidade, atributos, renderer, ovo, traduções e comando de teste integrados.
- Build tentado com Java 21. O Wrapper parou antes de compilar ao baixar Gradle 9.2.1: `java.net.SocketException: Network is unreachable`. Veja `docs/build-leao.log`.
- Análise sintática Java 21 aprovada nos 109 arquivos Java principais; sem resolução de tipos/dependências.
- Compilação Java e execução no Minecraft ainda NÃO confirmadas. Os testes de recursos não substituem esses passos.

## Compilar e iniciar

Abra um terminal na pasta que contém `build.gradle`, com JDK 21 e acesso à internet:

Windows PowerShell:
```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```
Linux/macOS:
```bash
bash gradlew build
bash gradlew runClient
```
O build inclui as verificações já existentes do projeto. JAR esperado: `build/libs/tupinicraft-0.5.3-tigrinho-dev.jar`.

Para verificar somente a exportação (Python 3, sem bibliotecas adicionais):
```bash
python tools/export_leao.py
python tools/validate_leao.py
```
O exportador usa `model_sources/leao_do_fim.bbmodel` e os PNGs na mesma pasta. Esses fontes ficam fora dos recursos empacotados no JAR.

## Testar no jogo

Em um mundo de teste com comandos habilitados:
```mcfunction
/summon tupinicraft:leao_do_fim ~ ~ ~
/give @s tupinicraft:leao_do_fim_spawn_egg
/leaodofim @e[type=tupinicraft:leao_do_fim,sort=nearest,limit=1] roar
/leaodofim @e[type=tupinicraft:leao_do_fim,sort=nearest,limit=1] charge_attack
/leaodofim @e[type=tupinicraft:leao_do_fim,sort=nearest,limit=1] release_attack
/leaodofim @e[type=tupinicraft:leao_do_fim,sort=nearest,limit=1] sigil_charge
/leaodofim @e[type=tupinicraft:leao_do_fim,sort=nearest,limit=1] sigil_spurt
/leaodofim @e[type=tupinicraft:leao_do_fim,sort=nearest,limit=1] aura_on
/leaodofim @e[type=tupinicraft:leao_do_fim,sort=nearest,limit=1] aura_off
/leaodofim @e[type=tupinicraft:leao_do_fim,sort=nearest,limit=1] reset
```
O comando exige permissão de operador nível 2. Também aceita `bite` e `hit`.
`charge_attack` mantém o último quadro até `release_attack`, outra ação ou `reset`.
`aura_on`/`aura_off` controlam apenas as camadas de carga; não liberam uma pose corporal mantida. Use `reset` para cancelar tudo.

O leão é neutro: anda, olha para jogadores e revida quando atacado. Para observar mordidas reais, teste em sobrevivência. Comandos de animação de ataque são apenas visuais. O dano corpo a corpo real usa o tempo padrão do MeleeAttackGoal, ainda não sincronizado com o quadro de fechamento da mandíbula.
O ovo aparece na aba TupiniCraft (a aba inclui automaticamente todos os itens registrados) e na aba de ovos. Não foi adicionado spawn natural.

Checklist manual pendente:
- Verificar UVs, orientação, cauda, transparência e glowmask à noite.
- Observar repouso, caminhada, mordida e reação a dano.
- Disparar as sete ações; alternar aura; cancelar a carga mantida.
- Confirmar que orbe/raios não são apagados pelos loops de olhos/aura.
- Testar dois leões com estados diferentes e dois clientes acompanhando os mesmos disparos.
- Iniciar servidor dedicado para confirmar separação cliente/servidor.

## Estrutura e escolhas

`LeaoDoFimEntity extends PathfinderMob implements GeoEntity` usa `AnimatableInstanceCache`, `GeckoLibUtil.createInstanceCache` e `registerControllers`.
IA básica: `FloatGoal`, `MeleeAttackGoal`, `WaterAvoidingRandomStrollGoal` (equivalente vanilla ao wander), `LookAtPlayerGoal`, `RandomLookAroundGoal` e `HurtByTargetGoal`.
Atributos provisórios: vida 100, velocidade 0,25, dano 6, alcance 24. Hitbox provisória: 1,4 × 1,7 blocos; a aura não aumenta a colisão.
`DefaultedEntityGeoModel` resolve `geo/entity`, `animations/entity` e `textures/entity`. Rotação automática da cabeça desativada para preservar a animação autoral. `AutoGlowingGeoLayer` usa o PNG `_glowmask` fornecido.

Controllers: `sigil`, `aura`, `eyes`, `body`, nessa ordem. Ações corporais têm prioridade nas partes que animam. Cinco clipes `animation.leao_do_fim.layer_*` são derivados das animações originais, mantendo apenas os ossos do efeito; removem chaves globais que ocultariam orbe, cauda ou raios. Os 14 clipes `animation.lionxx.*` permanecem exportados integralmente.
Repouso combina `idle`, `sigil`, `aura_idle` e `eye_aura_idle`; movimento troca somente o corpo por `walk`. O estado de carga da aura é sincronizado por `SynchedEntityData`; disparos de ações usam a rede do GeckoLib. Ações temporárias não são persistidas em NBT. Clientes que começam a acompanhar a entidade após um disparo não recebem replay desse disparo; teste uma nova ação após entrar no alcance.

## Mapeamento das animações

| Animação (prefixo animation.lionxx.) | Duração | Modo original | Uso |
|---|---:|---|---|
| `sigil` | 4 s | loop | Loop do sigilo (camada filtrada) |
| `idle` | 4 s | loop | Repouso corporal |
| `walk` | 1 s | loop | Movimento corporal |
| `charge_attack` | 2 s | hold | Trigger de carga mantida; ativa aura carregada |
| `release_attack` | 1.2 s | once | Trigger de liberação; termina em repouso |
| `sigil_charge` | 3 s | once | Trigger visual de carga do sigilo |
| `roar` | 2 s | once | Trigger visual de rugido |
| `bite` | 0.7 s | once | Mordida automática ao atacar ou trigger de teste |
| `hit` | 0.5 s | once | Dano recebido com sucesso ou trigger de teste |
| `aura_idle` | 4 s | loop | Camada de aura em repouso |
| `aura_charge` | 1.64 s | loop | Camada de aura carregada |
| `eye_aura_idle` | 4 s | loop | Camada de olhos em repouso |
| `eye_aura_charge` | 2 s | loop | Camada de olhos carregada |
| `sigil_spurt` | 4 s | once | Trigger visual de rajada do sigilo |

## Ossos, pivôs e locators

Não há locators no arquivo fornecido. Coordenadas abaixo são as originais do Blockbench, em unidades de modelo (16 = 1 bloco). No `.geo.json`, X do pivô é invertido e X/Y das rotações são invertidos conforme a convenção Bedrock/GeckoLib. A geometria usa UV por face, com ajuste das faces superior/inferior. O exportador preserva interpolação linear, Catmull-Rom e step (via pre/post).

| Osso | Pai | Pivô original | Rotação original | Cubos |
|---|---|---|---|---:|
| `root` | — | [0, 0, 0] | [0, 0, 0] | 0 |
| `body` | root | [0, 17, 0] | [0, 0, 0] | 1 |
| `mane` | body | [0, 20, -8] | [0, 0, 0] | 7 |
| `head` | body | [0, 21, -14] | [0, 0, 0] | 10 |
| `face_aura` | head | [0, 22, -18] | [0, 0, 0] | 12 |
| `eye_aura` | head | [0, 21, -19.3] | [0, 0, 0] | 0 |
| `eye_wisp_r1` | eye_aura | [2.85, 21, -19.3] | [0, 0, 0] | 1 |
| `eye_wisp_r2` | eye_aura | [3.1, 21.35, -19.1] | [0, 0, 0] | 1 |
| `eye_wisp_r3` | eye_aura | [3.35, 21, -18.9] | [0, 0, 0] | 1 |
| `eye_wisp_l1` | eye_aura | [-2.85, 21, -19.3] | [0, 0, 0] | 1 |
| `eye_wisp_l2` | eye_aura | [-3.1, 21.35, -19.1] | [0, 0, 0] | 1 |
| `eye_wisp_l3` | eye_aura | [-3.35, 21, -18.9] | [0, 0, 0] | 1 |
| `eye_glow_r` | eye_aura | [2, 21, -19.2] | [0, 0, 0] | 1 |
| `eye_trail_r` | eye_aura | [3, 21, -19.2] | [0, 0, 0] | 4 |
| `eye_glow_l` | eye_aura | [-2, 21, -19.2] | [0, 0, 0] | 1 |
| `eye_trail_l` | eye_aura | [-3, 21, -19.2] | [0, 0, 0] | 4 |
| `jaw` | head | [0, 16.5, -18] | [0, 0, 0] | 5 |
| `energy_orb` | head | [0, 16.5, -24.5] | [0, 0, 0] | 2 |
| `orb_particles` | head | [0, 16.5, -24.5] | [0, 0, 0] | 8 |
| `tail` | body | [0, 21, 13] | [40, 0, 0] | 1 |
| `tail_tip` | tail | [0, 21, 24] | [-20, 0, 0] | 1 |
| `tail_glow` | tail_tip | [0, 21, 25] | [0, 0, 0] | 1 |
| `tail_laser` | tail_tip | [0, 21, 27] | [0, 0, 0] | 2 |
| `leg_front_right` | root | [3.5, 12, -5.5] | [0, 0, 0] | 2 |
| `leg_front_left` | root | [-3.5, 12, -5.5] | [0, 0, 0] | 2 |
| `leg_back_right` | root | [3.5, 12, 9.5] | [0, 0, 0] | 2 |
| `leg_back_left` | root | [-3.5, 12, 9.5] | [0, 0, 0] | 2 |
| `aura` | root | [0, 35, -9] | [0, 0, 0] | 0 |
| `sigil_orbit` | aura | [0, 41, 2] | [0, 0, 0] | 0 |
| `sigil` | sigil_orbit | [0, 41, -9] | [65, 0, 0] | 0 |
| `sigil_spin` | sigil | [0, 41, -9] | [0, 0, 0] | 17 |
| `sigil_bolts` | sigil_spin | [0, 41, -9] | [0, 0, 0] | 0 |
| `sigil_bolt_0` | sigil_bolts | [0, 41, -9] | [0, 0, 0] | 2 |
| `sigil_bolt_1` | sigil_bolts | [0, 41, -9] | [0, 0, 0] | 2 |
| `sigil_bolt_2` | sigil_bolts | [0, 41, -9] | [0, 0, 0] | 2 |
| `sigil_bolt_3` | sigil_bolts | [0, 41, -9] | [0, 0, 0] | 2 |
| `sigil_bolt_4` | sigil_bolts | [0, 41, -9] | [0, 0, 0] | 2 |
| `sigil_bolt_5` | sigil_bolts | [0, 41, -9] | [0, 0, 0] | 2 |
| `sigil_bolt_6` | sigil_bolts | [0, 41, -9] | [0, 0, 0] | 2 |
| `sigil_bolt_7` | sigil_bolts | [0, 41, -9] | [0, 0, 0] | 2 |
| `sigil_bolt_8` | sigil_bolts | [0, 41, -9] | [0, 0, 0] | 2 |
| `sigil_bolt_9` | sigil_bolts | [0, 41, -9] | [0, 0, 0] | 2 |
| `sigil_bolt_10` | sigil_bolts | [0, 41, -9] | [0, 0, 0] | 2 |
| `sigil_bolt_11` | sigil_bolts | [0, 41, -9] | [0, 0, 0] | 2 |
| `sigil_ring` | sigil | [0, 41, -9] | [0, 0, 0] | 12 |

## Onde evoluir depois

- `createAttributes()` e registro do EntityType: balanceamento e colisão definitiva.
- `registerGoals()`/`doHurtTarget()`: cooldowns e aplicação de dano no instante correto da mordida.
- `playAction()`: integrar rugido, projéteis/orbe, habilidades do sigilo e interrupções de combate reais.
- Adicionar locators no Blockbench para emissão de partículas/projéteis quando houver mecânica definida.
- Adicionar barra de boss, fases, loot e spawn somente quando as regras do boss forem definidas.

## Referências técnicas consultadas

- https://github.com/bernie-g/geckolib/wiki/Triggerable-Animations-(Geckolib4)
- https://github.com/bernie-g/geckolib/wiki/Emissive-Textures-(Geckolib4)
- https://github.com/bernie-g/geckolib/blob/1.21.1/common/src/main/java/software/bernie/geckolib/animatable/GeoEntity.java
- https://github.com/JannisX11/blockbench/blob/v4.12.4/js/io/formats/bedrock.js
- https://github.com/JannisX11/blockbench/blob/master/js/animations/keyframe.js

## Arquivos criados/alterados

Veja `docs/leao_do_fim_changes.txt`, gerado comparando os arquivos com o ZIP original.
