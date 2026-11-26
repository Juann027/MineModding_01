# RespectCraft Rig Kit (Blockbench Development Kit)

Kit para produzir **fora do repositório** (Blockbench Web) modelos, texturas e animações de mobs compatíveis com o
Custom Rig Framework do RespectCraft (Etapa 4.12) e entregá-los prontos para integrar. Família disponível:
**`respectcraft:fruit_humanoid_v1`** (humanoide de referência das futuras frutas antropomórficas).

```
rigkit/
├── README.md                          ← este guia (produção + integração)
├── BBFORGE_PROMPT.md                  ← prompt pronto para outra conversa do Claude com o BbForgeVersionPro
├── tools/                             ← (no ZIP) validate_rig.py + rigkit.py
└── fruit_humanoid_v1/
    ├── CONTRACT.md                    ← contrato técnico: bones, pivôs, eixos, sockets, partes, regras
    ├── fruit_humanoid_v1.rig.json     ← contrato legível por máquina (o mesmo que o mod lê)
    ├── fruit_humanoid_v1_template.bbmodel  ← TEMPLATE editável (manequim + animações compartilhadas)
    ├── reference/                     ← exportação do template (geo, png, animações) para comparar
    └── examples/banana.binding.example.json ← exemplo de manifesto de entidade
```

No repositório o validador fica em `tools/rigkit/` e o ZIP é gerado por `python3 tools/rigkit/package_kit.py`.

## Parte 1 — Produção no Blockbench Web

**Preparação (uma vez):** abra <https://web.blockbench.net>, *File → Plugins → Available* e instale
**"GeckoLib Animation Utils"** (o template usa o formato *GeckoLib Animated Model*). Sem o plugin o arquivo não abre
no formato certo.

1. *File → Open Model* → `fruit_humanoid_v1_template.bbmodel`. Salve com outro nome já no começo
   (*File → Save Model*, ex.: `banana.bbmodel`) e, em *File → Project*, troque *Model Identifier* para o nome da
   entidade (`banana`).
2. **Não mexa nos grupos obrigatórios** (veja a árvore em `CONTRACT.md` §2): nome, pai e *Pivot Point* ficam como
   estão (ou × a proporção que você escolher, §6). Para trocar a forma, **apague os cubos cinza do manequim e crie os
   seus dentro do mesmo grupo**. Pode girar/escalar cubos à vontade; não gire os grupos obrigatórios.
3. A frente é o **norte (−Z)**: o rosto do modelo olha para a câmera "North". O braço **direito** fica em **+X**.
4. Peças próprias (cabinho, coroa de folhas, cabelo, acessórios): grupos novos com prefixo **`extra_`**, dentro do
   grupo que as carrega (ex.: `extra_crown` dentro de `head`).
5. Casca/camada externa → cubos dentro de `torso_shell` / `head_shell`. Interior (polpa, núcleo) → `torso_core`
   (começa oculto no jogo). Variantes de rosto → grupos `expr_*` dentro de `face`.
6. Sockets: os grupos vazios `socket_*` marcam mãos, ponto de agarrar, ponto de impacto etc. Se a geometria mudou
   muito (mãos maiores, peito mais à frente), **arraste o marcador** para o lugar certo e anote no binding (`sockets`).
7. Textura: *Texture* do template é só referência de cores; pinte a sua (ou crie outra), mantendo o tamanho do PNG
   igual ao *UV size* do projeto.
8. Animações: as compartilhadas já vêm no template (`animation.fruit_humanoid_v1.*`) para você conferir como o seu
   modelo fica com elas — **não as edite para um modelo só**. Para um clip próprio, crie `animation.<entidade>.<chave>`
   com a **mesma duração** da cena (Takedown = 3,00 s). Troca de partes: *Animation → Effects → Timeline*
   com `rig:hide:shell;` / `rig:show:core;` / `rig:reset;`.
9. Exportar (*File → Export*):
   - *Export GeckoLib Model* → `<entidade>.geo.json`
   - *Export Animations* (só se houver clips próprios; apague os compartilhados antes, ou use um arquivo separado)
     → `<entidade>.animation.json`
   - PNG da textura → `<entidade>.png`
   - o `.bbmodel` (fonte)
10. Confira com o validador (Parte 3) e entregue: `.bbmodel`, `.geo.json`, `.png`, `.animation.json` (se houver) e o
    binding preenchido.

**A partir de imagens de referência:** use o fluxo do `BBFORGE_PROMPT.md` (blockout da silhueta sobre o manequim →
conferência das articulações → detalhes → textura → validador).

## Parte 2 — Integração no RespectCraft (desenvolvedor)

Para cada entidade nova da família:

1. Copie os arquivos para `src/main/resources/assets/respectcraft/`:
   `geo/entity/<e>.geo.json`, `textures/entity/<e>.png`, `animations/entity/<e>.animation.json` (opcional).
2. Crie o binding `rigs/entities/<e>.json` (modelo: `fruit_humanoid_v1/examples/banana.binding.example.json`):
   proporção, bones `extra_`, partes e sockets próprios, alinhamento, capacidades e `clip_overrides`.
3. Registre o tipo de entidade em `registry.ModEntities` (classe que implementa `GeoEntity` — pode estender o padrão de
   `entity.dev.RigMannequinEntity`: um controlador `main` com o `idle` da família e `triggerableAnim` para
   `CustomRigRegistry.GLOBAL.triggerables(binding)`), os atributos e, no cliente, o renderer genérico:
   `event.registerEntityRenderer(TIPO, ctx -> new RigGeoRenderer<>(ctx, id))`.
4. Associe a família em `rig.Rigs.bootstrap()`: `bind(RespectCraft.id("<e>"))`.
5. Valide: `python3 tools/rigkit/validate_rig.py --repo`, `./gradlew build`, `bash .github/scripts/server-smoke-test.sh`
   (SelfTest) e, em jogo, `/respectcraft rig clips @e[type=respectcraft:<e>,limit=1]`.

Finalizações: um clip para toda a família vai em `FinisherDef.withRigClip(new FinisherRigs.Clip(família, papel,
chave))`; requisitos de participante (família, capacidades, tipo, tag, clip animado obrigatório) em
`withRigRequirement(...)`. Ordem de resolução do clip de um mob: clip explícito da finalização para o tipo →
override do binding → clip da família → parado (que um requisito `requiresClip` recusa **antes** da cena).
Chefes continuam pela regra de sempre: finalização comum nunca executa chefe, rig ou não.

## Parte 3 — Validador

Python 3.9+, sem dependências. Só lê arquivos.

```bash
python3 tools/validate_rig.py --rig fruit_humanoid_v1/fruit_humanoid_v1.rig.json \
    --geo banana.geo.json --texture banana.png --anim banana.animation.json \
    --bbmodel banana.bbmodel --binding banana.json
```

```bash
python3 tools/validate_rig.py --rig fruit_humanoid_v1/fruit_humanoid_v1.rig.json \
    --shared-anim fruit_humanoid_v1/reference/fruit_humanoid_v1.animation.json
```

No repositório: `python3 tools/rigkit/validate_rig.py --repo` (todas as famílias e bindings) e
`python3 tools/rigkit/test_rigkit.py` (casos quebrados de propósito).

Saída por nível:

| Nível | Significa |
|---|---|
| `ERROR` | impede a integração (bone obrigatório ausente, hierarquia errada, esquerda/direita trocadas, duração errada…) |
| `WARN` | possível incompatibilidade (pivô fora da tolerância, bone sem prefixo, textura de outro tamanho…) |
| `OK` | recurso opcional válido encontrado (expressões, casca/núcleo, sockets, extras declarados) |
| `CHECK` | só a inspeção visual resolve — **o validador nunca declara compatibilidade visual** |

Exemplo (bone obrigatório ausente e pivô deslocado):

```
== banana.geo.json
  ERROR bones: required bone right_hand is missing (right wrist)
  WARN  pivots: right_forearm pivot [5.5, 21, 0] is 4.00 px from expected [5.5, 17, 0] (contract x proportion [1, 1, 1], tolerance 2.0 px): shared animations will bend at a different joint
  OK    parts: part shell present (visible by default)
  CHECK visual: play every shared animation on THIS model: joints bend where the geometry bends, ...
  -> NOT COMPATIBLE: 1 error(s), 1 warning(s). Visual compatibility is NOT verified by this tool (see CHECK items).
```

## Comandos de teste em jogo (permissão 2)

| Comando | Resultado esperado |
|---|---|
| `/respectcraft rig list` | `respectcraft:fruit_humanoid_v1 v1: 25 bones (16 required), 9 sockets, 2 parts, 3 shared animations; bound: [...]` |
| `/respectcraft rig fixtures` | invoca os dois manequins de teste à frente do jogador |
| `/respectcraft rig inspect <entidade>` | família, proporção, capacidades, estado das partes, sockets no mundo (pose de repouso); `none` para mob sem rig |
| `/respectcraft rig clips <entidade>` | por finalização e papel: `PLAYER`/`ENTITY`/`BINDING`/`FAMILY`/`HOLD` + animação, ou `REFUSED (motivo)` |
| `/respectcraft rig check <executor> <alvo>` | a mesma consulta do início da cena (chefes, PvP, rigs) para cada finalização: `ELIGIBLE` ou o motivo |
| `/respectcraft rig part <entidade> <parte> hide\|show`, `... reset` | troca visual temporária, sincronizada (`Rig parts of ...: {shell=false}`) |
| `/respectcraft rig demo <alvo>` | cena de teste das partes (fora do catálogo) contra o alvo, pelas regras do `admin finisher` |
| `/respectcraft admin finisher <executor> <alvo> respectcraft:takedown` | Takedown: o manequim A toca o override próprio, o B o clip da família |
