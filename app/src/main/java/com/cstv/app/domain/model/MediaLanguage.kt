package com.cstv.app.domain.model

/** Stable codes persisted by Room; do not use enum names as database values. */
enum class MediaLanguage(val storageCode: String) {
    VF("vf"),
    VFQ("vfq"),
    VFF("vff"),
    VOSTFR("vostfr"),
    VOST("vost"),
    VO("vo"),
    MULTI("multi"),
    TRUEFRENCH("truefrench"),
    SUBFRENCH("subfrench")
}
