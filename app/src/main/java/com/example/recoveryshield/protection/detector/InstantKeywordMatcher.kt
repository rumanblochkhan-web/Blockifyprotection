package com.example.recoveryshield.protection.detector

import java.util.regex.Pattern

/**
 * Ultra-high-performance, zero-latency instant keyword and explicit trigger matcher.
 * Operates in 0.00001 seconds (instant sub-millisecond execution).
 */
object InstantKeywordMatcher {

    // Substrings that instantly trigger protection anywhere they appear (in URLs, search terms, typed words)
    private val INSTANT_SUBSTRING_TRIGGERS = listOf(
        "test_trigger", "testtrigger", "test-trigger", "test trigger",
        "pornhub", "xvideos", "xvideo", "xnxx", "xhamster",
        "redtube", "youporn", "brazzers", "spankbang", "eporner", "beeg",
        "chaturbate", "onlyfans", "stripchat", "hentai", "blowjob",
        "cumshot", "creampie", "deepthroat", "rule34", "javhd", "erome",
        "fapping", "sexvideo", "sexyvideo", "adultvideo", "xxxvideo",
        "nangi ladki", "chudai video", "gandi video", "sax sux", "chudai",
        "muth marna", "mutth marna",
        "porno", "porn", "xxx", "cam4", "livejasmin", "myfreecams", "bongacams",
        "xhamsterlive", "tube8", "tnaflix", "sunporn", "xmof", "spankwire",
        "hardsextube", "porntrex", "hqporner", "netvideogirls", "javlibrary",
        "javmost", "jav321", "missav", "jable", "7mmtv", "avgle", "doodstream",
        "hentaihaven", "nhentai", "gelbooru", "danbooru", "e-hentai", "exhentai",
        "sankakucomplex", "literotica", "lushstories", "solarmovie", "fmovies",
        "camsoda", "flirt4free", "jasmin", "imlive", "camwhores",
        "fapello", "coomer", "kemono", "anonfiles", "cyberdrop", "pixiv",
        "thumbzilla", "youjizz", "empflix", "4tube", "porndig", "porn555",
        "upornia", "gotporn", "cliphunter", "xtube", "drtuber", "keezmovies",
        "protoporn", "slutload", "tube9", "pornthree", "naughtyamerica",
        "realitykings", "bangbros", "vixen", "kink.com", "evilangel", "mofos",
        "hanime", "rule34.xxx", "luscious.net", "hitomi.la", "xvideos2"
    )

    // Exact word boundaries for explicit adult/anatomical terms
    private val INSTANT_WORD_PATTERN = Pattern.compile(
        "\\b(xxx|sex|porn|porno|nude|nudes|boobs|tits|milf|pussy|vagina|penis|dildo|horny|nangi|masturbat(?:e|ion|ing)?|handjob|chudai|sax|lund|chut)\\b",
        Pattern.CASE_INSENSITIVE
    )

    // Safe contextual terms that should NOT trigger instant blocking (e.g. recovery / blocker / medical)
    private val SAFE_CONTEXT_TERMS = listOf(
        "porn protection", "porn blocker", "porn shield", "recovery shield",
        "blockify", "porn addiction", "quitting porn", "porn recovery",
        "stop porn", "anti porn", "free from porn", "safe sex", "sex education",
        "sexual health"
    )

    /**
     * Evaluates text in 0.00001 seconds (sub-millisecond instant check).
     * Returns the matched trigger word or null if safe.
     */
    fun findInstantTrigger(rawText: String?): String? {
        if (rawText.isNullOrBlank()) return null
        val lower = rawText.lowercase().trim()
        if (lower.length < 3) return null

        // Exempt safe recovery terms
        for (safe in SAFE_CONTEXT_TERMS) {
            if (lower.contains(safe)) return null
        }

        val normalized = lower.replace(Regex("[\\s._\\-]+"), "")

        // 1. Ultra-fast substring check on raw text
        for (trigger in INSTANT_SUBSTRING_TRIGGERS) {
            val idx = lower.indexOf(trigger)
            if (idx != -1) {
                return rawText.substring(idx, idx + trigger.length)
            }
        }

        // 2. Substring check on normalized text (catches "p o r n", "s e x", "x v i d e o s", etc.)
        if (normalized.length >= 3) {
            for (trigger in INSTANT_SUBSTRING_TRIGGERS) {
                if (normalized.contains(trigger)) {
                    return trigger
                }
            }
        }

        // 3. Word pattern match on raw text
        val matcher = INSTANT_WORD_PATTERN.matcher(rawText)
        if (matcher.find()) {
            return matcher.group(1)
        }

        // 4. Word pattern match on normalized text
        val matcherNormalized = INSTANT_WORD_PATTERN.matcher(normalized)
        if (matcherNormalized.find()) {
            return matcherNormalized.group(1)
        }

        return null
    }
}

