package com.respectcraft.notice;

/**
 * Como um aviso aparece na tela (Etapa 4.14.1): cartao comum da Etapa 4.11, janela cinematografica no meio da visao,
 * ou os dois. Todo aviso entra no historico da secao Alerts, qualquer que seja a apresentacao. O pacote usa o ordinal.
 */
public enum NoticePresentation {
    TOAST,
    CINEMATIC,
    BOTH;

    public static NoticePresentation byOrdinal(int ordinal) {
        NoticePresentation[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : TOAST;
    }
}
