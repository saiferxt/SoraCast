package app.nebo.core;

/** Stable names are persisted and passed to the projection service, never ordinal indexes. */
public enum StreamQuality {
    AUTO("Авто", "Подстраивается под вашу сеть и телевизор", MediaProfile.BALANCED),
    ECONOMY("Экономный", "До 540p · 24 кадр/с · для слабых устройств", MediaProfile.LIGHT),
    STANDARD("720p", "До 30 кадр/с · 4 Мбит/с", MediaProfile.BALANCED),
    CLEAR("1080p", "До 30 кадр/с · 8 Мбит/с", MediaProfile.FULL_HD),
    SMOOTH("1080p / 60", "До 60 кадр/с · 12 Мбит/с · выше нагрузка", MediaProfile.SMOOTH),
    QHD("2K / 30", "До 1440p · 30 кадр/с · 14 Мбит/с", MediaProfile.QUAD_HD),
    QHD60("2K / 60", "До 1440p · 60 кадр/с · 22 Мбит/с · высокая нагрузка", MediaProfile.QUAD_HD_60);

    public final String title, detail;
    public final MediaProfile initial;
    StreamQuality(String title, String detail, MediaProfile initial) {
        this.title=title; this.detail=detail; this.initial=initial;
    }
    public static StreamQuality fromId(String id) {
        for (StreamQuality value:values()) if (value.name().equals(id)) return value;
        return AUTO;
    }
}
