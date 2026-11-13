# CLAUDE.md — TupiniCraft

Mod brasileiro de sátira política para **Minecraft 1.21.1 / NeoForge 21.1.x / Java 21**.
Base: MDK oficial `NeoForgeMDKs/MDK-1.21.1-ModDevGradle` (plugin `net.neoforged.moddev`).

## Fluxo de entrega e merge automático (política do dono do repositório)

O dono do repositório autorizou merge automático dos PRs ao concluir cada tarefa, sem aguardar aprovação manual.
Em toda implementação:

1. Crie uma branch para a funcionalidade (em sessões de nuvem, use a branch designada pela sessão; se o PR
   anterior dela já foi integrado, recomece-a a partir da `main` atualizada).
2. Implemente e execute os testes (`./gradlew build`, que inclui `rulesTest`; e o teste de fumaça do servidor
   quando a mudança tocar servidor/persistência).
3. Corrija erros de compilação, testes e regressões antes de abrir o PR.
4. Abra o Pull Request para a `main`.
5. Aguarde a conclusão do CI do PR (workflow **Build**: `./gradlew build` + teste de fumaça do servidor dedicado).
6. Se todos os testes e verificações obrigatórias passarem e o PR estiver sem conflitos, faça o merge na `main`
   (merge commit). O workflow **Release** publica a versão se `mod_version` for nova.
7. Confirme que o merge aconteceu (estado do PR e commit na `main`).
8. Informe o número do PR, o commit final (merge) e um resumo das alterações.

Regras:
- **Nunca** faça merge com testes obrigatórios falhando, CI pendente/vermelho ou erros críticos conhecidos.
- Se o merge estiver bloqueado (conflitos, testes, regras de proteção), resolva o que for possível **sem contornar
  proteções** (nada de force-push na `main`, desativar checks ou pular testes). Se não der, informe o motivo.
- Mantenha o namespace `tupinicraft` e a compatibilidade com NeoForge 1.21.1, Java 21, servidor dedicado e
  multiplayer, além dos dados salvos de versões anteriores.

## Escopo atual

Foco em **biomas, dimensões e NPCs** (ver `AGENTS.md` e `README.md`). Eleições, mandatos, economia, Tigrinho, UI cívica e
árvores de habilidades foram removidos do código ativo (cópias em `backups/`); não os reintroduza sem pedido explícito.
Não há testes de regras nem teste de fumaça: o CI roda só `./gradlew build`.

Stack: Minecraft 1.21.1, NeoForge 21.1.x, Java 21, namespace `tupinicraft`. Não usar APIs de Forge 1.20.x, Fabric ou
NeoForge 21.2+. Mantenha código comum separado de `client/`, o servidor autoritativo e nomes de registro estáveis.
