package Helpers

import org.apache.spark.sql.functions.{col, count, current_timestamp, explode, split, window}
import org.apache.spark.sql.{DataFrame, Dataset, SaveMode, SparkSession}

object ToPostgres {

  def main(args: Array[String]): Unit = {

    val topics = "BigDataTest"
    val kafkaBroker = "localhost:9092"
    val driver = "org.postgresql.Driver"
    val url = "jdbc:postgresql://localhost:5455/postgresDB"
    val user = "postgresUser"
    val password = "postgresPW"
    val tableWords = "public.T_WORDS"


    val spark = SparkSession.builder
      .appName("SparkStreaming POC")
      .master("local[*]")
      .getOrCreate()

    spark.sparkContext.setLogLevel("ERROR")

    import spark.implicits._
    val kafkaDF = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", kafkaBroker)
      .option("subscribe", topics)
      .load()
      .select("value")

    val wordsDf = kafkaDF
      .select(split($"value", " ").as("words"))
      .select(explode($"words").as("word"),
        current_timestamp().as("timestamp"))

    val windowedWordsDF = wordsDf
      .withWatermark("timestamp", "6 seconds")
      .groupBy($"word", window($"timestamp", "4 seconds", "2 seconds"))
      .agg(
        count("*").as("count"))
      .select("word", "count", "window.start", "window.end")

    val wordsDs = windowedWordsDF.as[WordsCount]

    wordsDs
      .writeStream
      .foreachBatch { (batch: Dataset[WordsCount], _: Long) =>
        // each executor can control the batch
        // batch is a STATIC Dataset/DataFrame

        batch.write
          .format("jdbc")
          .option("driver", driver)
          .option("url", url)
          .option("user", user)
          .option("password", password)
          .option("dbtable", tableWords)
          .mode(SaveMode.Append)
          .save()

        println("Row saved")
      }
      .start()
      .awaitTermination()

    //windowedWordsDF.writeStream.format("console").start().awaitTermination()

  }
}
