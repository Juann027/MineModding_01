# Instruções para o Claude (chat normal, Chrome + `/blockbench-web-geckolib-v2`)

Você vai animar uma **cena de finalização** do RespectCraft no Blockbench Web, no navegador do dono. Você **não** tem
terminal nem acesso ao repositório: entregue o projeto salvo e o export de animações; o Claude Code separa e integra
depois. Leia `README.md` e `CONTRACT.md` deste kit antes de começar.

## Preparação

1. Use a skill `/blockbench-web-geckolib-v2` para operar o Blockbench Web (<https://web.blockbench.net>) com o plugin
   **GeckoLib Models & Animations** instalado. **Desta vez não há modelagem**: só animação. Ignore as etapas de blockout,
   modelagem e textura da skill.
2. Abra o template pedido pelo dono (`scenes/<cena>/<cena>.bbmodel`) com File → Open Model. Confirme o formato
   *GeckoLib Animated Model* e os grupos `STAGE_ATTACKER` (rig `ATK_*`) e `STAGE_VICTIM` (rig `VIC_*`).

## Regras invioláveis

- Mexa só em **keyframes** de bones `ATK_*` e `VIC_*` e no canal *timeline* da aba Effects.
- **Nunca** renomeie, crie, apague, mova ou reparente bones, cubos ou pivôs; nunca edite UV nem textura; nunca mude o
  formato do projeto.
- **Nunca** anime `STAGE_ATTACKER`, `STAGE_VICTIM` ou `STAGE_VICTIM_SCALE` (o keyframe de escala que já existe em
  `STAGE_VICTIM_SCALE` é calibração: não apague, não mude; numa animação nova, duplique uma existente para levá-lo).
- Duração em múltiplos de 0,05 s (snapping 20); todos os keyframes em instantes de tick (0,05 s).
- Raízes (`ATK_Root`, `VIC_root`) podem se deslocar durante a cena, mas voltam a `0,0,0` no fim. A entidade não anda.
- Não use os canais *sound* e *particle*: tudo vai no canal **timeline**, uma linha por evento, com dono
  (`@attacker`, `@victim`, `@scene`). Formatos aceitos estão no `CONTRACT.md`; nada fora disso.
- `@scene execute` só no último instante da cena. `rig:hide/show` só com nomes de partes do manifesto
  (`victim_parts` do `.scene.json`; ex. morango: `face_normal`, `eyes_wide`, `eyes_closed`, `brows_panic`,
  `mouth_open`, `mouth_scream`, `damage_head`, `damage_torso`, `shell`, `core`, `leaves`, `stem`, `top`, `earrings`).
  Expressão = esconder `face_normal` + mostrar um olho, `brows_panic` e uma boca.
- Um evento tem **um** dono: som, golpe e instrução nunca nos dois participantes.
- Não exporte geometria (*Export GeckoLib Model*): só *Export Animations*.

## Fluxo

1. Selecione (ou duplique) a animação `scene.<cena>`; ajuste o comprimento pedido.
2. Blocking: poses-chave dos dois nos instantes dos golpes; use `VIC_socket_impact_point` / `VIC_socket_grab_point`
   e `ATK_RightHandAttachment` / `ATK_LeftHandAttachment` para encostar mão e alvo. Depois refine (easing/bezier).
3. Escreva os eventos na timeline no mesmo instante do contato (`@attacker hit 1..3`, câmera, sons, expressões...).
4. Toque a animação do começo ao fim e tire capturas nos golpes (vista lateral e de cima) para o dono aprovar.
5. Salve: File → Save Project (`<cena>.bbmodel`). Exporte: File → Export → Export Animations (`<cena>.animation.json`).
6. Entregue os **dois arquivos** ao dono, com: nome da animação, duração em ticks, a lista de eventos (tick, dono,
   evento) e qualquer limitação ou dúvida. Não invente ids de som/efeito: use os que o dono passar ou deixe uma linha
   `@<dono> note audio ...`/`note vfx ...` descrevendo o que falta.

## O que o Claude Code faz depois

`split_scene.py <cena>.scene.json --export <cena>.animation.json --animation scene.<anim> --out ...` separa os clips,
confere bones, duração, dono dos eventos e palco, e gera o relatório; a integração (registro do clip do jogador, do
binding/família da vítima e da finalização) é feita por ele, com aprovação do dono.
