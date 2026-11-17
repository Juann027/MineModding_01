"""Biblioteca de poses-chave do jogador (reutilizadas pelos clips da biblioteca e pelas coreografias)."""
from build import P, grounded, LEGS, ARMS

NEUTRAL = P()

# ----------------------------------------------------------------------------- postura de combate
# Base ortodoxa: pe esquerdo a frente, corpo meio de lado (Root gira 20 graus), cabeca compensa para o alvo,
# punhos na altura do queixo (sem cotovelo: braco levantado e fechado para dentro).
GUARD = grounded(P(
    Root=(0, 20, 0),
    Body=(7, 6, 0),
    Head=(-4, -24, 0),
    RightArm=(-106, -18, 4),
    LeftArm=(-98, 14, -2),
    RightLeg=(14, 0, 5),
    LeftLeg=(-16, 0, -3),
))
# "Respiracao" da guarda: um pouco mais baixa e com os punhos mexendo.
GUARD_LOW = grounded(P(GUARD, Body=(10, 7, 0), RightArm=(-102, -17, 6), LeftArm=(-94, 13, -4),
                       RightLeg=(17, 0, 5), LeftLeg=(-19, 0, -3)))

# ----------------------------------------------------------------------------- soco (direto de direita)
PUNCH_WIND = grounded(P(GUARD,
    Root=(0, 26, 0),
    Body=(5, 24, 2),
    Head=(-2, -42, 0),
    RightArm=(-66, 4, 16),
    LeftArm=(-104, 18, -4),
    RightLeg=(18, 0, 6),
    LeftLeg=(-12, 0, -3),
))
PUNCH_HIT = grounded(P(GUARD,
    Root={"r": (0, 8, 0), "p": (0, 0, -4)},
    Body=(10, -26, -2),
    Head=(4, 10, 0),
    RightArm=(-92, -4, 0),
    LeftArm=(-110, 22, -8),
    RightLeg=(40, 0, 4),
    LeftLeg=(-14, 0, -3),
))
PUNCH_FOLLOW = grounded(P(PUNCH_HIT, Body=(16, -30, -3), RightArm=(-94, -2, -2), Head=(6, 13, 0)))

# ----------------------------------------------------------------------------- chute frontal (perna de tras)
KICK_CHAMBER = grounded(P(GUARD,
    Root=(0, 10, 0),
    Body=(-4, 2, -4),
    Head=(4, -12, 0),
    RightArm=(-40, 0, 38),
    LeftArm=(-70, 16, -18),
    RightLeg=(-62, 0, 2),
    LeftLeg=(4, 0, -2),
), contacts=("LeftLeg",))
KICK_HIT = grounded(P(GUARD,
    Root={"r": (0, 4, 0), "p": (0, 0, -3)},
    Body=(-20, -4, -3),
    Head=(14, -4, 0),
    RightArm=(26, 0, 30),
    LeftArm=(-62, 20, -26),
    RightLeg=(-95, 0, 0),
    LeftLeg=(16, 0, -2),
), contacts=("LeftLeg",))
KICK_RETRACT = grounded(P(KICK_CHAMBER, Body=(-2, 2, -3), RightLeg=(-48, 0, 2)), contacts=("LeftLeg",))

# ----------------------------------------------------------------------------- reacoes
# Golpe no estomago: dobra para a frente, cabeca afunda, maos vao para a barriga.
DOUBLED = grounded(P(
    Root={"r": (0, 0, 0), "p": (0, 0, 1)},
    Body=(34, 0, 2),
    Head=(22, 0, 0),
    RightArm=(-44, -32, 0),
    LeftArm=(-38, 30, 0),
    RightLeg=(10, 0, 3),
    LeftLeg=(-6, 0, -3),
))
# Defesa assustada (alvo percebe a aproximacao).
FLINCH = grounded(P(
    Root={"p": (0, 0, 0.5)},
    Body=(-4, 0, 0),
    Head=(10, 0, 0),
    RightArm=(-138, -30, 10),
    LeftArm=(-138, 30, -10),
    RightLeg=(10, 0, 4),
    LeftLeg=(-4, 0, -4),
))
WARY = grounded(P(
    Root={"p": (0, 0, 0.3)},
    Body=(-5, 0, 0),
    Head=(-6, 0, 0),
    RightArm=(-62, -22, 8),
    LeftArm=(-56, 22, -8),
    RightLeg=(12, 0, 3),
    LeftLeg=(-6, 0, -3),
))
# Desequilibrio: cambaleando para tras, bracos abertos.
STAGGER_A = grounded(P(
    Root={"r": (0, 0, 4), "p": (0, 0, 1.5)},
    Body=(14, 0, -8),
    Head=(-6, 6, 4),
    RightArm=(-36, 0, 46),
    LeftArm=(-20, 0, -26),
    RightLeg=(16, 0, 6),
    LeftLeg=(-14, 0, -2),
))
STAGGER_B = grounded(P(
    Root={"r": (0, 0, -4), "p": (0, 0, 2.5)},
    Body=(10, 0, 8),
    Head=(-4, -8, -4),
    RightArm=(-18, 0, 28),
    LeftArm=(-40, 0, -48),
    RightLeg=(-12, 0, 2),
    LeftLeg=(18, 0, -6),
))
# Chute recebido: dobra forte e e empurrado para tras.
KICKED = grounded(P(
    Root={"p": (0, 0, 3)},
    Body=(48, 0, 0),
    Head=(26, 0, 0),
    RightArm=(-78, -6, 22),
    LeftArm=(-74, 6, -22),
    RightLeg=(18, 0, 4),
    LeftLeg=(8, 0, -4),
))

# ----------------------------------------------------------------------------- chao
# Quatro apoios (pose neutra reutilizavel): coxas inclinadas para tras com o joelho (ponta da perna) no chao,
# tronco quase horizontal, bracos na vertical com as maos apoiadas, cabeca pendendo.
HANDS_KNEES = grounded(P(
    Root={"p": (0, 0, 0)},
    Body=(88, 0, 0),
    Head=(-38, 0, 0),
    RightArm=(-86, 0, 6),
    LeftArm=(-86, 0, -6),
    RightLeg=(26, 0, 3),
    LeftLeg=(26, 0, -3),
), contacts=LEGS + ARMS)
# Ajoelhado em um joelho (perna esquerda a frente, joelho direito no chao).
KNEEL = grounded(P(
    Body=(10, 0, 0),
    Head=(4, 0, 0),
    RightArm=(-6, 0, 6),
    LeftArm=(-32, 10, -4),
    RightLeg=(58, 0, 3),
    LeftLeg=(-52, 0, -4),
))
# Ajoelhado nos dois joelhos (canelas = pernas deitadas para tras), tronco ereto.
KNEEL_BOTH = grounded(P(
    Body=(4, 0, 0),
    Head=(6, 0, 0),
    RightArm=(-22, -8, 6),
    LeftArm=(-22, 8, -6),
    RightLeg=(84, 0, 4),
    LeftLeg=(84, 0, -4),
))
