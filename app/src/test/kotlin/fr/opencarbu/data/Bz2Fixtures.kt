package fr.opencarbu.data

import java.util.Base64

/** Files made by the reference `bzip2` compressor (Python's bz2 module), as text so they can live in the sources. */
internal object Bz2Fixtures {
    private fun decode(base64: String): ByteArray = Base64.getDecoder().decode(base64)

    /** A names file like the real one (many columns, quotes, accents), compressed with level 9. */
    val NAMES_CSV_BZ2: ByteArray get() = decode(
            "QlpoOTFBWSZTWZivJmwAAIl/lEAQABBQj3/wDwcVAL/n3mAQQAgAMAD7NqGppCflR5R6E9Q2oaNA0aAyaAaqflTxqYp6jIAbRqA0" +
            "BoABpqammpptRkyaNBoAAGjI0v8LbYwO/xthDmt9BiQWMCHCu8dBVpyh2B6sR7NMvt/glQZIMLoucj0ZEYjDEMKjOdCkeoZalypd" +
            "INDDO8QJoga3rdMFQZbmn4tFeCS9iq2CwkIhqFIZMY4VpAW2A6gm0zv5rmsbcQrnkafUlnLfXmZMio3KgYPyljCJ6qqEezLXf+TZ" +
            "q56ZKC9aZEnKmWErkAItYVVonzljUMDsj8Z2jLaGgrgnEOinhKNGylCh7W8vocwZ6JMAfGrTQ6IoIg1zoSGib8sEIqbDKhEhcRE5" +
            "4jBuS68v8XckU4UJCYryZsA=" +
            "",
    )

    /** `("line %d,abc\n" % (i % 97)) for i in 0 until 26000`, compressed with level 1: three blocks. */
    val MULTI_BLOCK_BZ2: ByteArray get() = decode(
            "QlpoMTFBWSZTWZkBhUUAonHZAAAQQAR/4DolUAQe8AAAAqCmE00BpiFMJpoDTEKYTTQGmIJqqg0e9VKZAApVID9UDEabElA3JKB6" +
            "UnapNak0EuCWEvgl+BL6EvYl9iX5EvYlhLCXcSwlhLCWEsJYSwl6EsJYSwl4EsJYSwlhLCWEuhLCWEsJYS8iWEsJYSwlhLyJYSwl" +
            "hLCWEuhLCWEsJYS8CWEsJaJaJaJaJeolololol3EtEtEtEtEtEtEtEvcS0S0S7CWiWiWiWiWiWiWiWiXIlkS0S0S0S0S0S0S0S8/" +
            "Yl2EuhLoS6EuhL4JfIl8iXyJcEsJYSwlhLCWiWiWiWiXjkUTCWEu4lhLCWEvUS0S0S0S9xRMJYSwl4EsJYS0S0S0S0S9RRMJYSwl" +
            "hLyJYS0S0S0S0S6iiYSwlhLCWEuhLRLRLRLRLzFE7RLCWEsJYSwlol7EtEtEu8EnBLglwS4JcEuRLkS9iXIl2oScEuCXBLhJlSYp" +
            "MUmKTEgMqTglwS4JcEuRLkS5EuJSNSSgbElA3JKBwSUDiSUDT8SUDiSUD+YoKyTKayBYipqAEXJ7IAACCACP/AdEqgCD3gAAABBT" +
            "CaaA0xCmE00BpiFMJpoDTEE1VRNqe3qpTIAFKpAaNGDTYkoG8koGlSeqk1qTIS4JYS9iX4EvoS7EvsS/Il2JYSwl5EsJYSwlhLRL" +
            "RLRLz8iWiWiWiXxEtEtEtEu4lolol0JaJaJaJaJeolololololol6EtEtEtEtEtEuololololol8CWiWiWiWEsJYS+RLCWEsJeRL" +
            "CWEsJYSwlhLCXYlhLCXgSwlhLCWEsJYSwlhLgloSwlhLCWEsJYSwl9iXQl0JdRLqJdRL3EvYl7EvYlwSwlhLRLRLRLRLCWEsJcii" +
            "YSwl5EtEtEtEsJYSwlhLulEwlhLRL4EtEtEsJYSwlhL5oSYSwlolol6EtEsJYSwlhLqhJhLCWiWiWiXQlhLCWEsJeqEngSwlhLRL" +
            "RLRLCXYlhLCXmlE4JciXIlyJciXBLgl2JcEvEUTglyJciXIlyJcEuCXBLlJVXBLkS5EuRLkS4JcEuCXAUaklA2JKBuSUDFJVRplE" +
            "qLBJQOSSgYJKB0YoKyTKayrTr5bgCO0LIAACCACP/AdEqgCDzwAAABBTCaaA0xCmE00BpiFMJpoDTEE1VQNPeqlBkAUqo0GjRg02" +
            "olA3olA0UnpSaqTspM1J8Un1SaKT8pPak/iX7iXuJaJaJd4lolololololhLCXUSwlhLCXgSwlhLCWEsJYS9RLCWEsJYS8iWEsJY" +
            "SwlhLzEsJYSwlhLCXoSwlhLCWEvESwlhLCWEsJYS6EsJYSwl3iWEsJYSwlhLCWEvYlhLCXaJYSwlhLCWEsJYSwlwS1EtEtEtEtEt" +
            "EtEtEvf6iXUS6iXQl0JdCXwS+CXBL5EuRLRLRLCWEsJYSwlhLRLlCTRLRLvEsJYSwlhLCWEtEvcEmiWiWEvESwlhLCWEsJaJdRRN" +
            "EtEsJYS8xLCWEsJYS0S9RRNEtEsJYSwl6iWEsJYS0S8xRO0S0S0SwlhLCWEvcSwlhLvFE5EvzolhLCWEsJYS5EsJdoomiXBLglwS" +
            "4JcEuCXIlyJduxCORLglwS4JcEuCXBLikzSkaklA2JKBuSUDFEoGKJQOaJQMUSgdGKCskymssNrYpQAAs2yAAAggAj/wHRKgAT4A" +
            "AAphNNAaYhTCaaA0xCmE00BpiBUqibU3v1VJMI0+82Nnbtth+GYbnxjzj6HYQ9DofA/g8j5H0POIQ7iEIQhCHviEIewhCEIQ6EIQ" +
            "h4EIQhDwIQhCHQhCEPYQhCEIe4hCHcQhCEIQ8iEOwhCEIQhDggsWLFixYsfOOh0Oh0Oh6Hoeh6xwWIQhCEIWOBkId8QhCEIWPOGQ" +
            "hD2EIQhCx7hkIQh4EIQhY6DIQhCHQhCFjwGdsQhCEIeRCHcM4ODg4ODg4PI4OwZwcHBwcHBwcHMG4ODg4ODg4OY5ttvozDfZmG/D" +
            "MN+mYb9Mw3+Mw36Zhv+LuSKcKEgHyRisAA==" +
            "",
    )

    /** Two streams one after the other: [RUNS_TEXT] then "second stream". */
    val TWO_STREAMS_BZ2: ByteArray get() = decode(
            "QlpoOTFBWSZTWZ/YGVUAAAHBgeAAPoAAQACAAAggADEA000DJpPKaaXH6Dhqy5uEYUkUd0Aym0fi7kinChIT+wMqoEJaaDExQVkm" +
            "U1nwk5f7AAAFEYBAAC4DnAAgADEA000EAaMlWw4g8kh4u5IpwoSHhJy/2A==" +
            "",
    )

    val RUNS_TEXT: ByteArray = ("a".repeat(3) + "b".repeat(4) + "c".repeat(5) + "d".repeat(258) +
        "e".repeat(259) + "x" + "g".repeat(1000)).toByteArray()

    val MULTI_BLOCK_TEXT: ByteArray = buildString {
        for (i in 0 until 26000) append("line ").append(i % 97).append(",abc\n")
    }.toByteArray()
}
