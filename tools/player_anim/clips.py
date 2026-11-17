"""Clips da Etapa 4: biblioteca de combate/chao e a coreografia "Combo de Rua" (executor + alvo).

Uso: python3 tools/player_anim/clips.py   -> grava o .animation.json, os .bbmodel e mostra o relatorio de contato.
Tempos em ticks (20 por segundo). Cada pose-chave vem de poses.py; as defasagens (lag) fazem a cadeia cinetica:
pernas/quadril saem antes, o braco depois, cabeca e braco livre chegam um tick atrasados (follow-through).
"""
import copy

from build import P, Clip, grounded, LEGS, ARMS
import poses as K

OUT_E = "easeOutCubic"   # chegada rapida que desacelera: golpes e impactos
IN_E = "easeInQuad"      # aceleracao: quedas
S = "easeInOutSine"      # padrao


def shift(pose, dz=0.0, dx=0.0, contacts=LEGS):
    """Desloca o Root (px; z+ = para tras) e reapoia no chao."""
    pose = copy.deepcopy(pose)
    root = pose.setdefault("Root", {})
    x, y, z = root.get("p", (0, 0, 0))
    root["p"] = (x + dx, y, z + dz)
    return grounded(pose, contacts)


def lean(pose, **bones):
    return P(pose, **bones)


# ============================================================================ poses intermediarias
HK = K.HANDS_KNEES
HK_C = LEGS + ARMS
HK_BREATH = grounded(P(HK, Body=(86, 0, 0), RightArm=(-84, 0, 6), LeftArm=(-84, 0, -6), Head=(-30, 0, 0)), HK_C)
HK_LOOK = grounded(P(HK, Head=(-62, 8, 0)), HK_C)
HK_IMPACT = grounded(P(HK, Body=(92, 0, 0), RightArm=(-90, 0, 8), LeftArm=(-90, 0, -8), Head=(-20, 0, 0)), HK_C)

KNEES_LAND = grounded(P(Body=(58, 0, 0), Head=(-10, 0, 0), RightArm=(-78, -6, 10), LeftArm=(-74, 6, -10),
                        RightLeg=(26, 0, 3), LeftLeg=(26, 0, -3)))
BUCKLE = grounded(P(Root={"r": (6, 0, 0)}, Body=(44, 0, 2), Head=(14, 0, 0), RightArm=(-56, -10, 16),
                    LeftArm=(-50, 10, -14), RightLeg=(34, 0, 4), LeftLeg=(18, 0, -4)))
STUMBLE_1 = grounded(P(K.KICKED, Body=(40, 0, -4), Head=(18, 4, 0), RightArm=(-50, 0, 24), LeftArm=(-58, 0, -18),
                       RightLeg=(24, 0, 4), LeftLeg=(-8, 0, -4)))
STUMBLE_2 = grounded(P(K.KICKED, Body=(46, 0, 4), Head=(22, -4, 0), RightArm=(-64, 0, 14), LeftArm=(-46, 0, -26),
                       RightLeg=(-6, 0, 4), LeftLeg=(22, 0, -4)))
DOUBLED_DEEP = grounded(P(K.DOUBLED, Body=(40, 0, 3), Head=(30, 0, 0), RightArm=(-48, -36, 0), LeftArm=(-42, 34, 0)))
GROGGY = grounded(P(Root={"p": (0, 0, 1.5)}, Body=(12, 0, -3), Head=(4, 6, 2), RightArm=(-46, -14, 10),
                    LeftArm=(-50, 14, -10), RightLeg=(8, 0, 4), LeftLeg=(-8, 0, -4)))

# get up: empurra para tras, planta o pe esquerdo, joelho direito no chao, levanta.
GU_PUSH = grounded(P(Body=(62, 0, 0), Head=(-26, 0, 0), RightArm=(-60, 0, 8), LeftArm=(-60, 0, -8),
                     RightLeg=(34, 0, 3), LeftLeg=(34, 0, -3)), HK_C)
GU_PLANT = grounded(P(Body=(36, 0, 0), Head=(-6, 0, 0), RightArm=(-50, -6, 10), LeftArm=(-48, 18, -6),
                      RightLeg=(62, 0, 3), LeftLeg=(-46, 0, -4)), ("RightLeg", "LeftLeg", "RightArmClassic"))
GU_KNEEL = grounded(P(K.KNEEL, Body=(16, 0, 0), Head=(0, 0, 0), RightArm=(-12, 0, 8), LeftArm=(-34, 14, -4)))
GU_RISE = grounded(P(Body=(18, 0, 0), Head=(-2, 0, 0), RightArm=(-20, 0, 18), LeftArm=(-26, 0, -16),
                     RightLeg=(22, 0, 3), LeftLeg=(-24, 0, -3)))
GU_UP = grounded(P(Body=(-3, 0, 0), Head=(-4, 0, 0), RightArm=(-6, 0, 8), LeftArm=(-6, 0, -8),
                   RightLeg=(6, 0, 2), LeftLeg=(-6, 0, -2)))
GU_STEP = grounded(P(Body=(4, 0, -2), Head=(0, 0, 0), RightArm=(8, 0, 5), LeftArm=(-12, 0, -5),
                     RightLeg=(-18, 0, 1), LeftLeg=(10, 0, -1)))

# crawl: diagonais alternadas (braco direito + perna esquerda a frente).
CRAWL_A = grounded(P(HK, Root={"r": (0, 0, 3)}, Body=(88, 4, 0), Head=(-40, -6, 0),
                     RightArm=(-102, 0, 6), LeftArm=(-72, 0, -6), RightLeg=(38, 0, 3), LeftLeg=(12, 0, -3)), HK_C)
CRAWL_B = grounded(P(HK, Root={"r": (0, 0, -3)}, Body=(88, -4, 0), Head=(-40, 6, 0),
                     RightArm=(-72, 0, 6), LeftArm=(-102, 0, -6), RightLeg=(12, 0, 3), LeftLeg=(38, 0, -3)), HK_C)
CRAWL_PASS = shift(grounded(P(HK, Head=(-36, 0, 0)), HK_C), contacts=HK_C)
CRAWL_PASS["Root"]["p"] = (0.0, CRAWL_PASS["Root"]["p"][1] + 0.8, 0.0)   # sobe para o membro livre passar

GUARD_WATCH = grounded(P(K.GUARD, Head=(14, -22, 0)))
GUARD_WATCH_LOW = grounded(P(K.GUARD_LOW, Head=(16, -22, 0)))
RELAX = grounded(P(Root=(0, 8, 0), Body=(2, 0, 0), Head=(4, -6, 0), RightArm=(-18, -6, 8), LeftArm=(-16, 6, -8),
                   RightLeg=(6, 0, 3), LeftLeg=(-6, 0, -3)))
KICK_OVER = grounded(P(K.KICK_HIT, RightLeg=(-99, 0, 0), Body=(-22, -4, -3)), contacts=("LeftLeg",))
# depois do chute o executor recua meio passo (ganha distancia) e volta no encerramento
BACK = 3.5
GUARD_BACK = shift(K.GUARD, BACK)
GUARD_BACK_LOW = shift(K.GUARD_LOW, BACK)
WATCH_BACK = shift(GUARD_WATCH, BACK)
WATCH_BACK_LOW = shift(GUARD_WATCH_LOW, BACK)
STEP_IN = shift(P(K.GUARD, LeftLeg=(-30, 0, -3), RightLeg=(8, 0, 5), Body=(9, 6, 0)), BACK * 0.5)

PUNCH_CHAIN = {"Root": -2, "Body": -1, "Head": 1, "LeftArm": 1}
ARMS_LATE = {"RightArm": 2, "LeftArm": 2}
HEAD_LATE = {"Head": 1, "RightArm": 1, "LeftArm": 1}

# ============================================================================ biblioteca


def lib():
    c = {}
    x = Clip("combat_idle", 40, loop=True)
    x.key(0, K.GUARD).key(20, K.GUARD_LOW, S, {"Head": 2, "RightArm": 3, "LeftArm": 3}).key(40, K.GUARD)
    c[x.name] = x

    x = Clip("punch", 26)
    x.segment(0, [(0, K.NEUTRAL), (6, K.GUARD, S, ARMS_LATE), (10, K.PUNCH_WIND, S, {"Root": -1}),
                  (13, K.PUNCH_HIT, OUT_E, PUNCH_CHAIN), (15, K.PUNCH_FOLLOW), (20, K.GUARD, S, {"Body": -1}),
                  (26, K.NEUTRAL, S, {"Head": -1})])
    c[x.name] = x

    x = Clip("kick", 34)
    x.segment(0, [(0, K.NEUTRAL), (6, K.GUARD, S, ARMS_LATE), (11, K.KICK_CHAMBER, S, {"Body": -1, "RightArm": 1, "LeftArm": 1}),
                  (14, K.KICK_HIT, OUT_E, {"Head": 1, "RightArm": 1}), (16, KICK_OVER), (20, K.KICK_RETRACT),
                  (25, K.GUARD, IN_E, {"Body": 1, "Head": 1}), (34, K.NEUTRAL)])
    c[x.name] = x

    x = Clip("hit_reaction", 20)
    x.segment(0, [(0, K.NEUTRAL), (2, K.DOUBLED, OUT_E, HEAD_LATE), (5, DOUBLED_DEEP, S, {"Head": 1}),
                  (11, grounded(P(K.DOUBLED, Body=(14, 0, 1), Head=(6, 0, 0), Root={"p": (0, 0, 0.6)})), S, {"Head": 1}),
                  (20, K.NEUTRAL, S, {"Head": -1})])
    c[x.name] = x

    x = Clip("stagger", 34)
    x.segment(0, [(0, K.NEUTRAL), (5, K.STAGGER_A, OUT_E, HEAD_LATE), (12, K.STAGGER_B, S, {"Head": 2}),
                  (19, grounded(P(K.STAGGER_A, Root={"r": (0, 0, 2), "p": (0, 0, 1.6)}, Body=(8, 0, -4),
                                  RightArm=(-24, 0, 26))), S, {"Head": 2}),
                  (26, grounded(P(GROGGY, Root={"p": (0, 0, 0.6)})), S, {"Head": 1}), (34, K.NEUTRAL)])
    c[x.name] = x

    x = Clip("knockdown", 34)
    x.segment(0, knockdown_frames(0.0))
    c[x.name] = x

    x = Clip("kneel", 60)
    x.segment(0, [(0, K.NEUTRAL), (7, grounded(P(Body=(16, 0, 0), RightLeg=(30, 0, 3), LeftLeg=(-12, 0, -3),
                                                 RightArm=(-14, 0, 8), LeftArm=(-20, 0, -8)))),
                  (13, grounded(P(K.KNEEL_BOTH, RightLeg=(70, 0, 4), LeftLeg=(60, 0, -4), Body=(14, 0, 0))), IN_E),
                  (17, K.KNEEL_BOTH, S, {"Head": 1}),
                  (32, grounded(P(K.KNEEL_BOTH, Body=(2, 0, 0), Head=(10, 0, 0))), S),
                  (44, K.KNEEL_BOTH),
                  (50, grounded(P(K.KNEEL_BOTH, Body=(24, 0, 0), RightLeg=(50, 0, 4), LeftLeg=(-30, 0, -4)))),
                  (55, grounded(P(Body=(8, 0, 0), RightLeg=(14, 0, 2), LeftLeg=(-8, 0, -2)))), (60, K.NEUTRAL)])
    c[x.name] = x

    x = Clip("hands_and_knees", 40, loop=True)
    x.segment(0, [(0, HK), (20, HK_BREATH, S, {"Head": 3}), (40, HK)])
    c[x.name] = x

    x = Clip("crawl", 24, loop=True)
    x.segment(0, [(0, CRAWL_A), (6, CRAWL_PASS), (12, CRAWL_B), (18, CRAWL_PASS), (24, CRAWL_A)])
    c[x.name] = x

    x = Clip("get_up", 40)
    x.segment(0, get_up_frames(0.0))
    c[x.name] = x
    return c


def knockdown_frames(dz):
    """Em pe -> quatro apoios (termina exatamente em HANDS_KNEES deslocado de dz px para tras)."""
    return [(0, K.NEUTRAL),
            (3, shift(K.KICKED, dz * 0.5 - 1), OUT_E, HEAD_LATE),
            (8, shift(STUMBLE_1, dz * 0.75), S, {"Head": 1}),
            (12, shift(STUMBLE_2, dz), S, {"Head": 1, "RightArm": 1}),
            (17, shift(BUCKLE, dz), IN_E),
            (20, shift(KNEES_LAND, dz), IN_E, {"Head": 1, "RightArm": 1, "LeftArm": 1}),
            (23, shift(HK_IMPACT, dz, contacts=HK_C), IN_E, {"Head": 1}),
            (27, shift(grounded(P(HK, Body=(86, 0, 0), RightArm=(-84, 0, 6), LeftArm=(-84, 0, -6)), HK_C), dz, contacts=HK_C), S),
            (34, shift(HK, dz, contacts=HK_C), S)]


def get_up_frames(dz):
    """Quatro apoios -> em pe; o deslocamento dz volta a zero com um passo a frente no final."""
    return [(0, shift(HK, dz, contacts=HK_C)),
            (7, shift(GU_PUSH, dz, contacts=HK_C), S, {"Head": 1}),
            (13, shift(GU_PLANT, dz, contacts=("RightLeg", "LeftLeg", "RightArmClassic")), S, {"LeftLeg": -1}),
            (18, shift(GU_KNEEL, dz), S, {"Head": 1}),
            (25, shift(GU_RISE, dz), S, {"Head": 1, "RightArm": 1, "LeftArm": 1}),
            (31, shift(GU_UP, dz), S, {"Head": 1}),
            (35, shift(GU_STEP, dz * 0.4), S, {"Head": 1}),
            (40, K.NEUTRAL, S)]


# ============================================================================ coreografia (executor x alvo)
COMBO_LEN = 140
DZ = 6.5   # quanto o chute empurra o alvo para tras (px), devolvido no passo final do get_up
IMPACT_PUNCH, IMPACT_KICK, FALL_KNEES, FALL_HANDS, RECOVER_START = 25, 43, 62, 65, 98


def combo():
    a = Clip("finisher.combo_attacker", COMBO_LEN)
    a.segment(0, [
        (0, K.NEUTRAL),
        (8, K.GUARD, "easeOutSine", ARMS_LATE),          # preparacao: entra na guarda
        (13, K.GUARD_LOW, S, {"Head": 1, "RightArm": 2, "LeftArm": 2}),
        (17, K.GUARD),
        (22, K.PUNCH_WIND, S, {"Root": -1}),              # antecipacao: gira para tras
        (IMPACT_PUNCH, K.PUNCH_HIT, OUT_E, PUNCH_CHAIN),  # soco
        (IMPACT_PUNCH + 2, K.PUNCH_FOLLOW),
        (30, shift(P(K.GUARD, LeftArm=(-126, 28, -6)), -1.5), S, {"RightArm": -1}),   # recolhe o braco e sai do avanco
        (34, K.GUARD, S, {"Body": -1}),
        (39, K.KICK_CHAMBER, S, {"Body": -1, "RightArm": 1, "LeftArm": 1}),
        (IMPACT_KICK, K.KICK_HIT, OUT_E, {"Head": 1, "RightArm": 1}),
        (IMPACT_KICK + 2, KICK_OVER),
        (50, K.KICK_RETRACT),
        (56, GUARD_BACK, IN_E, {"Body": 1, "Head": 1}),   # pe volta ao chao ja recuando
        (61, GUARD_BACK_LOW, S, ARMS_LATE),
        (68, WATCH_BACK, S, {"Head": 2}),                 # acompanha a queda com o olhar
        (78, WATCH_BACK_LOW), (88, WATCH_BACK), (98, WATCH_BACK_LOW),
        (110, GUARD_BACK, S, {"Head": -2}),               # alvo levantando: volta a encarar
        (118, GUARD_BACK_LOW), (124, STEP_IN), (128, K.GUARD),
        (133, RELAX, S, {"RightArm": 1, "LeftArm": 1}),    # encerramento: baixa a guarda
        (140, K.NEUTRAL),
    ])

    v = Clip("finisher.combo_victim", COMBO_LEN)
    v.segment(0, [
        (0, K.NEUTRAL),
        (6, K.WARY, S, {"Head": -1, "RightArm": 2, "LeftArm": 2}),   # percebe a aproximacao
        (13, grounded(P(K.WARY, Body=(-3, 0, 0), Head=(-4, 4, 0))), S, {"Head": 1}),
        (21, shift(K.FLINCH, 1.2), S, {"Head": -1}),                                # encolhe com a antecipacao do soco
        (IMPACT_PUNCH + 1, shift(K.DOUBLED, 1.5), OUT_E, HEAD_LATE),    # impacto 1: dobra e recua
        (IMPACT_PUNCH + 4, shift(DOUBLED_DEEP, 2.6), S, {"Head": 1}),
        (31, shift(K.STAGGER_A, 1.5), S, {"Head": 1, "RightArm": 1}),
        (35, grounded(P(K.STAGGER_B, Root={"r": (0, 0, -2), "p": (0, 0, -1.5)}, Body=(22, 0, 4),
                        RightLeg=(14, 0, 2), LeftLeg=(-16, 0, -6))), S, {"Head": 1}),   # cambaleia para a frente
        (40, shift(GROGGY, -2.5), S, {"Head": 1}),                     # ve o chute vindo
        (IMPACT_KICK, shift(P(GROGGY, Body=(18, 0, -2), Head=(8, 0, 0)), -2.5), S),   # recebe o pe
    ])
    v.segment(IMPACT_KICK - 1, knockdown_frames(DZ)[1:])                # impacto 2 e queda
    hk_end = IMPACT_KICK - 1 + 34
    v.segment(0, [
        (hk_end + 6, shift(HK_BREATH, DZ, contacts=HK_C), S, {"Head": 3}),
        (hk_end + 13, shift(HK, DZ, contacts=HK_C)),
        (hk_end + 18, shift(HK_LOOK, DZ, contacts=HK_C), S, {"Head": 1}),   # levanta o olhar para o executor
        (RECOVER_START, shift(HK, DZ, contacts=HK_C)),
    ])
    v.segment(RECOVER_START, get_up_frames(DZ)[1:])
    return {a.name: a, v.name: v}


def fix_ground(clip, tol=0.3, rounds=24):
    """Contato: onde a interpolacao entre poses apoiadas afunda no chao, insere uma chave do Root na altura certa."""
    from build import _RIG
    from rig import sample_clip, min_y
    for _ in range(rounds):
        js = clip.to_json()
        worst, tick = 0.0, None
        for t in range(clip.length + 1):
            low = min_y(_RIG, sample_clip(js, t / 20))
            if low < worst:
                worst, tick = low, t
        if worst > -tol:
            return clip
        pose = sample_clip(js, tick / 20)["Root"]
        rx, ry, rz = pose.get("rotation", (0, 0, 0))
        px, py, pz = pose.get("position", (0, 0, 0))
        clip.key(tick, {"Root": {"r": (rx, ry, rz), "p": (px, py - worst, pz)}}, S, only=("Root",))
    return clip


def all_clips():
    clips = lib()
    clips.update(combo())
    for clip in clips.values():
        fix_ground(clip)
    return clips
