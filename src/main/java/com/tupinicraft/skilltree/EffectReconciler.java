package com.tupinicraft.skilltree;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** Cache transitório dos efeitos aplicados; reconcilia remoções e alterações antes de aplicar os novos. */
public final class EffectReconciler<T> {
    private Map<String, List<T>> applied = Map.of();

    public void reconcile(Map<String, List<T>> desired, BiConsumer<String, List<T>> remove,
                          BiConsumer<String, List<T>> apply) {
        Map<String, List<T>> snapshot = new LinkedHashMap<>();
        desired.forEach((id, effects) -> snapshot.put(id, List.copyOf(effects)));
        applied.forEach((id, effects) -> {
            if (!effects.equals(snapshot.get(id))) {
                remove.accept(id, effects);
            }
        });
        snapshot.forEach((id, effects) -> {
            if (!effects.equals(applied.get(id))) {
                apply.accept(id, effects);
            }
        });
        applied = Map.copyOf(snapshot);
    }
}
