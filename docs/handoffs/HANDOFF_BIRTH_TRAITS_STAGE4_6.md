# Handoff — Etapa 4.6: traços de nascimento por corpo e texto visível em inglês

Base: `main` em `d505bf0` (PR #27). Versão continua `0.5.7` (sem release).

## O que mudou

### Elegibilidade dos traços (`profile.BirthTrait`)

| Traço | Id (persistido) | Polaridade | Peso | Elegibilidade |
|---|---|---|---|---|
| High Endurance | `high_endurance` | Buff | 10 | Universal |
| Limp | `limp` | Debuff | 10 | Universal |
| Fertile (novo) | `fertile` | Buff | 10 | Universal |
| Infertile (novo) | `infertile` | Debuff | 10 | Universal |
| Perfectly Average | `average` | Neutral | 20 | Universal |
| Heavy Loader | `heavy_loader` | Buff | 10 | Só sorteio masculino |
| Loadless | `loadless` | Debuff | 10 | Só sorteio masculino |
| Hung | `hung` | Buff | 10 | Só sorteio masculino |
| Tiny Dick | `tiny_dick` | Debuff | 10 | Só sorteio masculino |

- `BirthTrait.Eligibility` (`ALL`, `MALE_ONLY`) e `BirthTrait.Body` (`MALE`, `FEMALE`; `fromBodyType(1)` = feminino,
  qualquer outro índice, inclusive aparência ainda não criada, = masculino).
- Conjunto masculino: os 9 traços (peso total 100). Conjunto feminino: High Endurance, Limp, Fertile, Infertile,
  Perfectly Average (peso total 60). Pesos antigos preservados. Em **cada** conjunto, peso de buffs = peso de debuffs
  (checado no `SelfTest`): nenhum sexo tem vantagem de polaridade.
- Continua **um** traço por nascimento (`PER_BIRTH = 1`), no máximo um por categoria (`FERTILITY` é nova).
- Fertile/Infertile não têm efeito (`effects` vazio). Nada de gravidez, medidor, mobs, modelos ou animações.

### Sorteio no servidor

- `BirthRoulette.roll(random, body)` substitui `roll(random)`; `ProfileService.roll` usa `ProfileService.body(player)`
  (corpo da aparência salva por `CharacterAppearanceService`). O criador salva a aparência antes de abrir a roleta, e
  os pacotes chegam em ordem, então o primeiro giro já vê o corpo certo. O comando de admin usa o mesmo caminho.

### Persistido × elegível

- Elegibilidade vale **só para sorteios novos**. Nenhum traço gravado é apagado: fichas antigas e personagens que
  mudam de corpo depois de confirmar o nascimento mantêm o traço (inclusive os exclusivos masculinos).
- Troca de corpo com resultado **não confirmado** (nasceu e ainda tem o novo giro): `CharacterAppearanceService.apply`
  avisa `ProfileService.onBodyChanged`, que aplica `CharacterProfile.withTraitsCoherentWith(body)`. Traço inelegível
  vira um traço elegível **de mesma polaridade**, escolhido de forma determinística (`BirthTrait.coherentWith`, salt =
  `rollCount`). Não sorteia, não muda origem/atributos/pontos/quantidade de traços, não gasta nem devolve o novo giro.
  O substituto é universal, então voltar ao corpo anterior não muda mais nada: alternar o corpo não "pesca" traços.
- Resultado confirmado (Continue ou novo giro já usado) não muda. Ids desconhecidos (versão futura) são preservados.

### Texto visível em inglês

- `en_us.json` é a fonte única. Traduzidos: habilidades e descrições, origens e descrições, legendas de som, itens
  `cafezinho`/`guarana`. Chaves novas `trait.tupinicraft.fertile|infertile(.desc)`. `gui.tupinicraft.trait.no_effect`
  = "Effects not implemented yet." (Character Hub, roleta e painel do inventário).
- `pt_br.json` **removido**: jogadores em pt_BR veem o inglês (fallback do jogo). `tools/validate_leao.py` só lê `en_us`.
- Mensagens de chat/comando em inglês: `/tupini`, `/tupinianim`, `/tupinitest`, `/leaodofim` e o teste de finalização.
  O smoke test procura `RespectCraft self-test: OK`, `Skills (18)`, `Finishers: ...` e `Player animations: ...`.
- Descrição do mod em `neoforge.mods.toml` em inglês.
- **Não traduzidos (de propósito):** ids, nomes de subcomandos e argumentos (`resetar`, `jogador`...), código,
  comentários, logs, documentação e os rótulos de diagnóstico do `SelfTest` (só aparecem para quem roda o autoteste
  quando algo falha). Nomes próprios de comida (Coxinha, Brigadeiro) e `Favela`/`Coom` ficaram como estão.

## Testes

- `./gradlew build`: OK.
- `bash .github/scripts/server-smoke-test.sh`: OK (`RespectCraft self-test: OK`).
- `SelfTest.traitEligibility` (novos): sorteio masculino (todos os 9), sorteio feminino (exatamente os 5), Fertile e
  Infertile universais e sem efeito, exclusivos ausentes do sorteio feminino (6 000 giros), equilíbrio buff/debuff
  por corpo, ids `fertile`/`infertile` no save (`birth_traits`) e na rede, perfil antigo confirmado com
  `tiny_dick` preservado com corpo feminino, ficha sem traço não ganha traço, id desconhecido preservado, troca de
  corpo coerente/determinística/sem pesca, novo giro único mantido e não restaurado, Continue preserva o resultado.
- Teste visual com cliente real **não rodado**: a tela não mudou de layout, só os textos vindos do lang.

## Limitações e próximos passos

- Fertile/Infertile são só classificação. Gravidez (estado, medidor, visual, NPCs) fica para uma etapa futura.
- O corpo vem do índice `bodyType` da aparência (`masculino`/`feminino`); um terceiro corpo cairia no conjunto
  masculino até ganhar regra própria em `BirthTrait.Body.fromBodyType`.
- Sem `pt_br.json`, uma tradução futura precisa ser feita a partir do `en_us.json` (e não contradizê-lo).
- Recomende backup dos mundos antes de atualizar (nenhum id foi removido ou renomeado).
