package com.tupinicraft.finisher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Trilha de camera de uma cena, em coordenadas relativas ao ancora da cena (ponto medio entre os participantes,
 * de frente para a direcao do executor). Pura: o cliente so amostra e posiciona a camera.
 */
public record CameraTrack(List<Key> keys) {
    /**
     * @param tick     instante da cena
     * @param orbit    angulo em graus em volta do ancora, a partir da frente do executor (90 = lado direito)
     * @param distance distancia horizontal ate o ancora (blocos)
     * @param height   altura da camera acima do ancora (blocos)
     * @param lookHeight altura do ponto observado acima do ancora (blocos)
     * @param fov      multiplicador do campo de visao (1 = normal)
     * @param roll     inclinacao da camera (graus)
     */
    public record Key(int tick, double orbit, double distance, double height, double lookHeight, double fov, double roll) {}

    public record Sample(double orbit, double distance, double height, double lookHeight, double fov, double roll) {}

    public CameraTrack {
        if (keys.isEmpty()) throw new IllegalArgumentException("Trilha de camera vazia");
        List<Key> sorted = new ArrayList<>(keys);
        sorted.sort(Comparator.comparingInt(Key::tick));
        keys = List.copyOf(sorted);
    }

    public static CameraTrack of(Key... keys) { return new CameraTrack(List.of(keys)); }

    /** Interpolacao suave (smoothstep) entre as chaves vizinhas; antes/depois das pontas, a ponta. */
    public Sample sample(double tick) {
        Key first = keys.get(0);
        if (tick <= first.tick()) return at(first);
        for (int i = 1; i < keys.size(); i++) {
            Key b = keys.get(i);
            if (tick <= b.tick()) {
                Key a = keys.get(i - 1);
                double span = Math.max(1e-6, b.tick() - a.tick());
                double t = (tick - a.tick()) / span;
                t = t * t * (3 - 2 * t);
                return new Sample(lerp(a.orbit(), b.orbit(), t), lerp(a.distance(), b.distance(), t),
                        lerp(a.height(), b.height(), t), lerp(a.lookHeight(), b.lookHeight(), t),
                        lerp(a.fov(), b.fov(), t), lerp(a.roll(), b.roll(), t));
            }
        }
        return at(keys.get(keys.size() - 1));
    }

    private static Sample at(Key k) {
        return new Sample(k.orbit(), k.distance(), k.height(), k.lookHeight(), k.fov(), k.roll());
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    /**
     * Posicao da camera relativa ao ancora (x, y, z), com o executor olhando para {@code facingYawDeg}
     * (convencao do Minecraft: yaw 0 = +Z, 90 = -X).
     */
    public static double[] offset(Sample s, double facingYawDeg) {
        double angle = Math.toRadians(facingYawDeg + s.orbit());
        double fx = -Math.sin(angle);
        double fz = Math.cos(angle);
        return new double[] {fx * s.distance(), s.height(), fz * s.distance()};
    }
}
