package Helpers

import java.sql.Timestamp

case class WordsCount(
                       word: String,
                       count: Long,
                       start: Timestamp,
                       end: Timestamp,
                     )
