# biome

Sem código Java. Desde a Etapa 4.9 os biomas do mod são **dados**: `data/respectcraft/worldgen/biome/` (hoje só
`twisted_wilds`, o bioma do Inverted Realm) com as features em `worldgen/configured_feature` e `worldgen/placed_feature`.
Um bioma novo entra como JSON lá; esta pasta só recebe código se um bioma precisar de lógica própria.
