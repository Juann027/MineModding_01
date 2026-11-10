# Correções e validação da versão 0.5.1

## Entrega

- Código-fonte atualizado e JAR compilado em `dist/tupinicraft-0.5.1.jar`.
- Minecraft 1.21.1, NeoForge 21.1.265 e Java 21; dependências mantidas.
- Cliente e servidor precisam da versão 0.5.1 (protocolo de rede 2).
- Instalação e dependências: consulte o README. Este pacote não inclui os mods de terceiros.

## Problemas corrigidos

1. **Habilidades após `/reload`:** o servidor compara os efeitos ativos com a definição anterior. Remove modificadores de habilidades excluídas ou suspensas, atualiza valores e operações, limpa atributos antigos e efeitos retirados da lista. Compras permanecem salvas; habilidades que reaparecem podem ser restauradas. A vida é limitada ao máximo somente depois de aplicar todos os novos bônus.
2. **Mandatos vencidos e novas posses:** a expiração é processada antes da posse e no login, retornando os registros encerrados para atualizar os ocupantes. O encerramento de um registro antigo preserva outro mandato vigente do jogador. O histórico de ocupantes e as atualizações pendentes continuam persistidos.
3. **Estouro de pontuação eleitoral:** cálculos, resultados e sincronização para o cliente usam `long`. O teste com 215 votos, peso 1.000, multiplicador 10.000 e popularidade 1.000.000 preserva 2.151.000.000 pontos e o vencedor correto.

## Arte

33 ícones de habilidades do `Arte.rar` foram redimensionados para 128 × 128 e comprimidos em PNG RGBA, preservando proporção e transparência. Os arquivos passaram de 26.259.860 para 460.004 bytes (redução de 98,2%). Todos os 14 arquivos de árvores e as quatro habilidades existentes receberam ícones correspondentes. Árvores sem habilidades continuam sem aba.

O catálogo completo e a prévia estão em `ARTE_SKILL_TREES.md`. Os emblemas separados de partidos não foram atribuídos ao partido genérico de teste.

## Verificações executadas

- `./gradlew build`: **aprovado**, com compilação do mod e dos testes, empacotamento e execução de `rulesTest`.
- `RulesSelfTest`: **136 verificações aprovadas, zero falhas**, incluindo compatibilidade de dados antigos e codificação/decodificação de pontuações maiores que `int`.
- `RegressionSelfTest`: **27 verificações aprovadas**, também integrado a `rulesTest`.
- `python3 scripts/validate-skill-art.py`: **aprovado**, com 33 ícones, modelos, traduções e 18 referências do datapack.
- Integridade dos 57 JSONs e do JAR gerado: **aprovada**.
- `.github/scripts/server-smoke-test.sh`: **aprovado**. O servidor dedicado iniciou duas vezes; carregou as habilidades e o mob; persistiu uma eleição ativa e overrides; rejeitou configurações inválidas; restaurou valores do TOML; concluiu votação/apuração, posse e consulta de mandatos.

O teste do servidor foi executado em modo offline, como definido pelo script do projeto. A renderização das árvores dentro do jogo e os fluxos com dois ou mais clientes conectados ainda precisam de conferência manual. A prévia visual dos PNGs otimizados foi inspecionada.
