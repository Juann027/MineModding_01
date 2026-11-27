# strawberry — estados visuais sugeridos

Complementa `strawberry.binding.json`. O schema oficial só declara partes (nome, bones, `default_visible`, `detachable`); combinações e exclusões mútuas abaixo são convenção para quem escreve as cenas. Sintaxe de timeline do contrato: `rig:hide:<parte>;rig:show:<parte>;rig:reset` (o estado anterior volta ao fim da cena).

## Visibilidade padrão
- Visíveis: `shell` (head_shell, torso_shell), `face_normal`, `leaves`, `stem`, `top`, `earrings`.
- Ocultas: `core` (torso_core), `eyes_wide`, `eyes_closed`, `brows_panic`, `mouth_open`, `mouth_scream`, `damage_head`, `damage_torso`.

## Expressões (sempre ocultar `face_normal` antes, e mostrar um olho + sobrancelha + boca)
| Estado | Timeline |
|---|---|
| Arrogante (padrão) | `rig:reset` |
| Choque | `rig:hide:face_normal;rig:show:eyes_wide;rig:show:brows_panic;rig:show:mouth_open;` |
| Grito | `rig:hide:face_normal;rig:show:eyes_wide;rig:show:brows_panic;rig:show:mouth_scream;` |
| Espremida | `rig:hide:face_normal;rig:show:eyes_closed;rig:show:brows_panic;rig:show:mouth_scream;` |

Exclusões mútuas: `eyes_wide` × `eyes_closed`; `mouth_open` × `mouth_scream`. As variantes ocupam o mesmo lugar do rosto normal; mostrar uma delas sem ocultar `face_normal` causa sobreposição. A placa `face` (pele do rosto) não faz parte de nenhuma parte e fica sempre visível; as variantes se apoiam nela.

## Dano e casca
| Estado | Timeline |
|---|---|
| Rachada | `rig:show:damage_head;rig:show:damage_torso;` (com a casca visível; as rachaduras ficam sobre a casca) |
| Descascada | `rig:hide:shell;rig:show:core;` (esconde casca da cabeça **e** do torso; a polpa da cabeça já é o interior de `head`) |
| Descascada + sem top | acrescente `rig:hide:top;` para expor o peito |
| Descascada + rachada | `rig:hide:shell;rig:show:core;rig:show:damage_torso;` (a rachadura do torso fica na altura do abdome, visível na polpa) |

Limite do schema: `shell` é uma parte única, então não dá para descascar só o torso ou só a cabeça sem uma parte nova no manifesto da família.

## Observações
- Sockets: nenhum override. Todos os marcadores seguem exatamente o contrato (pivô do bone + deslocamento) e continuam sobre a geometria.
- Não declarado de propósito: `alignment` (distância da cena é decisão de integração) e `animation_file`/`clip_overrides` (não há animações próprias).
- Validação definitiva: durante a integração ao RespectCraft (o validador do kit não está disponível no Blockbench).
