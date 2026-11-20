package com.respectcraft.ui;

import com.respectcraft.network.GameplayNetworking;
import com.respectcraft.profile.BirthRoulette;
import com.respectcraft.profile.CharacterAttribute;
import com.respectcraft.profile.CharacterProfile;
import com.respectcraft.profile.RouletteLayout;
import com.respectcraft.profile.Speciality;

/**
 * Maquina de estados da roleta de nascimento, sem desenho e sem classes de cliente (Etapa 4.8.4-C): a mesma para a
 * tela antiga ({@code BirthRouletteScreen}, reserva) e a placa holografica ({@code RouletteView}). Extraida da tela
 * da Etapa 4.8.2 (PR #36) sem mudar o comportamento: pedido com {@code requestId}, so a resposta com esse id encerra
 * a espera, {@link BirthRoulette.Outcome#ERROR} na hora, timeout separado e resposta atrasada aplicada uma vez.
 *
 * <p>Fases: {@code IDLE} aguardando sorteio -> {@code WAITING} pedido enviado, girando -> {@code SPINNING} resposta
 * recebida, desacelerando ate a raridade da ficha -> {@code REVEAL} atributos um a um -> {@code DONE} resultado
 * (Continuar ou o unico novo giro). A animacao nunca decide dados: a roda so para na raridade que o servidor gravou
 * na ficha ({@link RouletteLayout#restingRotation}). Avancada por tick (20 por segundo) pela casca.
 *
 * <p>Puro e testado no {@code SelfTest} ({@code rouletteMachine}).
 */
public final class RouletteMachine {
    /** Sem resposta nenhuma em tantos ticks (10 s): aviso de timeout (rede). */
    public static final int TIMEOUT_TICKS = 200;
    /** Ticks entre a revelacao de dois atributos. */
    public static final int REVEAL_STEP = 6;

    public enum Phase { IDLE, WAITING, SPINNING, REVEAL, DONE }

    /** Aviso que a casca mostra (traduzido por ela). */
    public enum Status { NONE, ERROR, TIMEOUT, NO_REROLLS, STALE }

    /** Pedido que a casca envia ao servidor ({@link GameplayNetworking.BirthRollRequest}). */
    public record Request(boolean reroll, int expectedRollCount, int requestId) {}

    /** Momentos que a casca transforma em som, faiscas e log. */
    public interface Listener {
        /** A raridade sob o ponteiro mudou (tique). */
        default void segment(int index) {}
        /** A roda parou num resultado novo. */
        default void revealed(CharacterProfile profile) {}
        /** Passo {@code step} (1..6) da revelacao: aparece o atributo {@code step - 1}. */
        default void revealStep(int step) {}
        /** Pedido encerrado sem giro (erro do servidor ou timeout). */
        default void failed(Status status, int requestId) {}
    }

    private static final Listener SILENT = new Listener() {};

    private Phase phase;
    private float angle, prevAngle, speed, spinFrom, spinTo;
    private int spinTick, spinLength;
    private boolean revealAfterSpin;
    private int revealTick;
    /** rollCount da ficha que a roda mostra (ou para a qual esta desacelerando). */
    private int shownRollCount;
    private int pendingRequestId;
    private int waitTicks;
    private Status status = Status.NONE;
    private int statusRequestId;
    private int lastSegment = -1;

    public RouletteMachine(CharacterProfile p) {
        shownRollCount = p.rollCount();
        if (hasResult(p)) {
            phase = Phase.DONE;
            angle = prevAngle = restingAngle(p);
            revealTick = 999;
        } else {
            phase = Phase.IDLE;
        }
    }

    public static boolean hasResult(CharacterProfile p) { return p.hasBirthResult(); }

    /** Rotacao em que a fatia da raridade da Speciality da ficha fica sob o ponteiro (ficha legada: topo). */
    public static float restingAngle(CharacterProfile p) {
        Speciality speciality = p.specialityOrNull();
        return speciality == null ? 0 : RouletteLayout.restingRotation(speciality.rarity(), p.rollCount());
    }

    // ================================================================ estado para a casca

    public Phase phase() { return phase; }
    public boolean busy() { return phase == Phase.WAITING || phase == Phase.SPINNING || phase == Phase.REVEAL; }
    public boolean canSpin(CharacterProfile p) { return !busy() && (!p.born() || p.canReroll()); }
    public boolean canContinue(CharacterProfile p) { return !busy() && phase == Phase.DONE && hasResult(p); }
    /** Resultado na tela (revelando ou pronto). */
    public boolean showsResult(CharacterProfile p) { return (phase == Phase.REVEAL || phase == Phase.DONE) && hasResult(p); }
    public Status status() { return status; }
    public int statusRequestId() { return statusRequestId; }
    public int pendingRequestId() { return pendingRequestId; }
    public int revealTick() { return revealTick; }
    /** Angulo da roda interpolado no quadro (graus). */
    public float angle(float partialTick) { return prevAngle + (angle - prevAngle) * partialTick; }

    /** Quantos atributos ja aparecem (todos em DONE). */
    public int attributesShown() {
        return phase == Phase.DONE ? CharacterAttribute.values().length : phase == Phase.REVEAL ? Math.min(revealTick / REVEAL_STEP,
                CharacterAttribute.values().length) : 0;
    }

    /** Valor mostrado do atributo {@code index}: sobe de 0 ate o da ficha enquanto ele e revelado. */
    public int attributeValue(CharacterProfile p, int index, float partialTick) {
        int shown = attributesShown();
        if (index >= shown) return 0;
        int value = p.attribute(CharacterAttribute.values()[index]);
        if (phase == Phase.REVEAL && index == shown - 1) {
            value = Math.round(value * Math.min(1F, ((revealTick % REVEAL_STEP) + partialTick) / (REVEAL_STEP - 1F)));
        }
        return value;
    }

    // ================================================================ acoes

    /**
     * Comeca um giro (primeiro ou o novo giro). Devolve o pedido a enviar, ou null se nao for possivel agora (a roda
     * ja esta girando, ou nao sobra novo giro).
     */
    public Request spin(CharacterProfile p, int requestId) {
        if (!canSpin(p)) return null;
        phase = Phase.WAITING;
        speed = 0;
        waitTicks = 0;
        status = Status.NONE;
        pendingRequestId = requestId;
        return new Request(p.born(), p.rollCount(), requestId);
    }

    /**
     * Um tick. {@code reply} = a ultima resposta de giro recebida <b>desde o pedido</b> (ou null); so a que responde
     * ao pedido em andamento conta.
     */
    public void tick(CharacterProfile p, GameplayNetworking.BirthRollReply reply, Listener l) {
        if (l == null) l = SILENT;
        prevAngle = angle;
        switch (phase) {
            case WAITING -> {
                speed = Math.min(24F, speed + 2.5F);
                angle += speed;
                segmentSound(l);
                if (reply != null && reply.answers(pendingRequestId)) {
                    // Resposta do servidor ao nosso pedido (a ficha correspondente chegou antes dela).
                    boolean rolled = reply.outcome() == BirthRoulette.Outcome.ROLLED
                            && reply.rollCount() == p.rollCount() && hasResult(p);
                    if (reply.outcome() == BirthRoulette.Outcome.ERROR) {
                        // O servidor falhou e disse isso: nada mudou na ficha (o novo giro continua intacto).
                        setStatus(Status.ERROR);
                        l.failed(Status.ERROR, pendingRequestId);
                    } else if (!rolled) {
                        setStatus(reply.outcome() == BirthRoulette.Outcome.NO_REROLLS ? Status.NO_REROLLS : Status.STALE);
                    }
                    settleOn(p, rolled);
                } else if (++waitTicks > TIMEOUT_TICKS) {
                    // Nenhuma resposta (problema de rede de verdade): volta ao estado real. Uma resposta atrasada
                    // e tratada no IDLE/DONE (ficha nova); o servidor recusa um pedido repetido sobre a ficha antiga.
                    setStatus(Status.TIMEOUT);
                    l.failed(Status.TIMEOUT, pendingRequestId);
                    settleOn(p, false);
                }
            }
            case SPINNING -> {
                spinTick++;
                float t = Math.min(1F, spinTick / (float) spinLength);
                float eased = 1F - (1F - t) * (1F - t) * (1F - t);
                angle = spinFrom + (spinTo - spinFrom) * eased;
                segmentSound(l);
                if (t >= 1F) {
                    speed = 0;
                    if (revealAfterSpin) {
                        phase = Phase.REVEAL;
                        revealTick = 0;
                        l.revealed(p);
                    } else {
                        phase = hasResult(p) ? Phase.DONE : Phase.IDLE;
                        revealTick = 999;
                    }
                }
            }
            case REVEAL -> {
                revealTick++;
                int step = revealTick / REVEAL_STEP;
                if (revealTick % REVEAL_STEP == 0 && step <= CharacterAttribute.values().length) l.revealStep(step);
                if (revealTick > REVEAL_STEP * (CharacterAttribute.values().length + 1)) {
                    phase = hasResult(p) ? Phase.DONE : Phase.IDLE;
                }
            }
            default -> {
                if (p.rollCount() != shownRollCount || (phase == Phase.DONE) != hasResult(p)) {
                    // A ficha mudou fora de um pedido (resposta atrasada, giro do admin, ficha zerada).
                    settleOn(p, hasResult(p) && p.rollCount() != shownRollCount);
                }
            }
        }
    }

    private void setStatus(Status s) {
        status = s;
        statusRequestId = pendingRequestId;
    }

    /** Desacelera ate a raridade da ficha atual (com revelacao quando e um resultado novo). */
    private void settleOn(CharacterProfile p, boolean reveal) {
        shownRollCount = p.rollCount();
        if (reveal) status = Status.NONE;
        spinFrom = angle;
        spinTick = 0;
        revealAfterSpin = false;
        phase = Phase.SPINNING;
        if (!hasResult(p)) {
            // Sem nascimento: so desacelera ate parar e volta a aguardar o sorteio.
            spinTo = angle + speed * 6F;
            spinLength = 20;
            return;
        }
        float target = restingAngle(p);
        float base = angle + (reveal ? 720F : 360F);
        float r = (target - base) % 360F;
        spinTo = base + (r < 0 ? r + 360F : r);
        spinLength = reveal ? 50 : 30;
        revealAfterSpin = reveal;
    }

    private void segmentSound(Listener l) {
        int segment = RouletteLayout.rarityAtPointer(angle).ordinal();
        if (segment != lastSegment) {
            lastSegment = segment;
            l.segment(segment);
        }
    }
}
