# Handoff — Consolidação da base (4.10.1 + 4.10.2 + 4.11 finisher/chefes + 4.11 avisos/caça + Calamidade 0.14.0)

Branch: `claude/hopeful-fermi-wnvzap`, a partir da `main` em `070885d` (merge do #50). Versão pública inalterada
(`0.5.7`); nenhuma release publicada, nenhum PR fechado, nada mesclado na `main`.

## 1. O que foi incorporado

Os PRs pendentes eram uma pilha linear sobre `f63a448` (merge do #46). A ponta `claude/intelligent-meitner-yfdram`
(sem PR) já tinha #49 + a atualização do Calamidade 0.14.0, então **um único merge** trouxe tudo, sem cherry-pick nem
commit duplicado:

| Origem | Commits |
|---|---|
| #47 Stage 4.10.1 (`ccr-4e7d6219-55ixjk`) | `f5c04e8` `f4b4381` `1c64a0a` `fc4fee1` `e9cefa2` `2571b1c` |
| #48 Stage 4.10.2 (`claude/ecstatic-albattani-i9j39p`) | `f57acf6` `0d4438f` `80de935` |
| #49 Stage 4.11 finisher/chefes (`claude/brave-rubin-kivvr3`) | `ec4f395` `9668e60` `0dbbca0` `24ff53e` `6e1ff1e` `f7b13e0` |
| Calamidade 0.14.0 (`claude/intelligent-meitner-yfdram`) | `3dcaadc` `8c04d8c` `1187c87` |

Commits da consolidação: `3042e09` (merge + conflitos), `2671bae` (correção da caça a recompensa), `e8cc350`
(roadmap), mais este handoff e as evidências.

## 2. Conflitos e soluções

- **Textuais:** `server-smoke-test.sh` (união dos comandos Wither/Dragão/golem + notice/bounty; conferência de efeitos
  presos com qualquer limite em vez do `0 / 64` fixo), `en_us.json` (união das chaves), `CLAUDE.md` e índice de
  handoffs (as duas etapas "4.11" distinguidas como "4.11 (finisher)" e "4.11 (avisos)").
- **Comportamental (corrigido):** o #50 cobrava a recompensa em todo `FinisherSessionEvent.Completed` jogador ×
  jogador, porque na época a cena não matava. Com a 4.10.1, `Completed` também sai com `FAILED` (totem/proteção):
  o contrato seria pago com o alvo vivo. Agora só cobra com `SUCCESS` (`BountyRules.finisherClaims`, no `SelfTest`).
  Na execução real quem cobra primeiro é o `LivingDeathEvent` (crédito do executor); o contrato sai do quadro no
  primeiro pagamento, então não há pagamento duplo.
- **Conferido sem mudança:** `BossDefeatEvent`/`HoloNotices.onBossDefeat` (API mantida; chefe sem recompensa),
  `Aborted`/servidor parando (caça não escuta), contador só com `SUCCESS` (independe da caça).

## 3. Dependências

Minecraft 1.21.1 · Java 21 · NeoForge 21.1.265 · GeckoLib 4.9.3 · RimuLib 1.3.0 · SmartBrainLib 1.16.11 ·
**Calamidade_Awakening 0.14.0** (faixa `[0.14.0,0.15.0)`, SHA-256
`02851af9f70ae404783bc8eaec985b065221828f0ecad8f35bd22d287bcb0cb7`, conferido pelo `baixarCalamidade` contra o jar
baixado do release `v0.14.0`; o `neoforge.mods.toml` do motor pede NeoForge `[21.1,21.2)` e RimuLib `[1.3.0,2.0)`).
Compila sem mudança de código Java contra a 0.14.0; `calamidade_fx.glsl` igual à do motor.

## 4. Testes

Executados nesta sessão:
- `./gradlew build` — OK. `tools/fx/check_shaders.py` — 0 falhas. `bbsync.py` — em sincronia. `test_export.py` — 0 falhas.
- `server-smoke-test.sh` (servidor dedicado) — OK, inclui `RespectCraft self-test: OK`, recusa de comuns contra
  Wither/Dragão, Wither por golem = derrota NORMAL, faróis de aviso registrados no Calamidade, 0 efeitos ativos.
- **Três clientes reais** (Xvfb/llvmpipe; Ana paga, Juca caça, Bia alvo) — `docs/evidencias/consolidacao/`:
  1. `SUCCESS`: Bia executada, "Bia was executed by Juca", contador = 1, contrato pago **uma vez** (5 diamantes),
     cartão "Bounty collected" nos clientes, 0 efeitos.
  2. Totem: `FAILED (death was prevented)`, contrato continua (hunters 1), nada pago, contador continua 1.
  3. Cancelamento (`/stopitstopnow` da vítima): `CANCELLED (player request)`, nada pago, 0 efeitos.
  4. Wither: `BOSS_REQUIRES_EXCLUSIVE` na lista e no comando de admin.
  5. `stop` no meio da cena: `CANCELLED (server stopping)` e todos os níveis salvos.

Não realizados: latência real/GPU real; clientes reais na versão do Calamidade de outro jogador (a regra continua
"mesma versão"); fluxo pela UI holográfica da aba Bounty Hunt (os pedidos foram por comando, que chama o mesmo
`BountyService`).

## 5. Limitações e decisões pendentes do dono

- Recompensa usa o crédito de morte vanilla: quem feriu o alvo pouco antes recebe mesmo se a morte final for `/kill`
  (que a 4.11 deixa passar durante a cena). Herdado do #50; registrado no `docs/ROADMAP.md`.
- Numeração "4.11" duplicada (renumerar é decisão do dono).
- `.github/release-title.txt` ainda diz o nome antigo do projeto; não alterado porque mexe no fluxo de release
  (corrigir junto da próxima release pedida).
- PRs que ficam redundantes depois do merge desta branch: #47, #48, #49 (e a branch `claude/intelligent-meitner-yfdram`).
